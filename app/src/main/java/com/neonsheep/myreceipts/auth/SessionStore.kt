package com.neonsheep.myreceipts.auth

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.datastore.tink.AeadSerializer
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/** What survives an app restart: the refresh token and who it belongs to. */
@Serializable
data class StoredSession(
    val refreshToken: String,
    val userId: String,
    val email: String,
    val displayName: String? = null,
)

/** Where the signed-in session is kept. The access token is never stored (auth design §6.5). */
interface SessionStore {
    val session: Flow<StoredSession?>
    suspend fun current(): StoredSession? = session.first()
    suspend fun save(session: StoredSession)
    suspend fun clear()
}

/**
 * The session in DataStore, encrypted with Tink AES-256-GCM under a key held in the Android
 * Keystore (auth design §6.5). The file is excluded from backup and device transfer
 * (`res/xml/data_extraction_rules.xml`), so a restored phone starts signed out.
 */
class EncryptedSessionStore(context: Context) : SessionStore {

    private val store: DataStore<StoredSession?> = DataStoreFactory.create(
        serializer = AeadSerializer(
            aead = aead(context.applicationContext),
            wrappedSerializer = JsonSessionSerializer,
            associatedData = FILE_NAME.toByteArray(),
        ),
        // A file that can't be decrypted (a new Keystore key after a restore, say) means signed out.
        corruptionHandler = ReplaceFileCorruptionHandler { null },
        produceFile = { context.applicationContext.dataStoreFile(FILE_NAME) },
    )

    override val session: Flow<StoredSession?> = store.data.map { it }

    override suspend fun save(session: StoredSession) {
        store.updateData { session }
    }

    override suspend fun clear() {
        store.updateData { null }
    }

    private object JsonSessionSerializer : Serializer<StoredSession?> {
        private val json = Json { ignoreUnknownKeys = true }
        override val defaultValue: StoredSession? = null

        override suspend fun readFrom(input: InputStream): StoredSession? {
            val bytes = input.readBytes()
            if (bytes.isEmpty()) return null
            return try {
                json.decodeFromString(StoredSession.serializer(), bytes.decodeToString())
            } catch (e: SerializationException) {
                throw CorruptionException("unreadable session", e)
            } catch (e: IllegalArgumentException) {
                throw CorruptionException("unreadable session", e)
            }
        }

        override suspend fun writeTo(t: StoredSession?, output: OutputStream) {
            if (t != null) output.write(json.encodeToString(StoredSession.serializer(), t).toByteArray())
        }
    }

    private companion object {
        const val FILE_NAME = "session.enc"
        const val KEYSET_NAME = "session_keyset"
        const val KEYSET_PREFS = "session_keyset_prefs"
        const val MASTER_KEY_URI = "android-keystore://myr_session_key"

        fun aead(context: Context): Aead {
            AeadConfig.register()
            return AndroidKeysetManager.Builder()
                .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle
                .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        }
    }
}

