package com.neonsheep.myreceipts.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "captured_receipts",
    indices = [Index(value = ["clientId"], unique = true)],
)
data class CapturedReceiptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val link: String,
    val senderLabel: String,
    val timestamp: Long,
    @ColumnInfo(name = "clientId") val clientId: String,
    val capturedAt: Long,
    val totalAmount: Double? = null,
    val pdfPath: String? = null,
    /** Why there is no PDF, when the backend said so: see [PdfStatus]. Null otherwise. */
    val pdfStatus: String? = null,
)

/** What the backend said about a receipt it couldn't turn into a PDF. */
object PdfStatus {
    /** The link's site isn't one the backend serves yet (HTTP 400). */
    const val UNSUPPORTED = "unsupported"

    /** The store no longer has the receipt, e.g. an expired Shufersal link (HTTP 410). */
    const val GONE = "gone"

    /** The background catch-up tried once and the backend couldn't make a PDF. Not retried on its own. */
    const val FAILED = "failed"
}

@Dao
interface ReceiptDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(receipt: CapturedReceiptEntity): Long

    @Query("SELECT * FROM captured_receipts ORDER BY timestamp DESC")
    fun getAll(): Flow<List<CapturedReceiptEntity>>

    @Query("SELECT * FROM captured_receipts ORDER BY timestamp DESC")
    suspend fun getAllOnce(): List<CapturedReceiptEntity>

    @Query("DELETE FROM captured_receipts WHERE clientId = :clientId")
    suspend fun deleteByClientId(clientId: String)

    @Query("UPDATE captured_receipts SET pdfPath = :pdfPath, pdfStatus = NULL WHERE clientId = :clientId")
    suspend fun updatePdfPath(clientId: String, pdfPath: String)

    @Query("UPDATE captured_receipts SET pdfStatus = :status WHERE clientId = :clientId")
    suspend fun updatePdfStatus(clientId: String, status: String?)

    /** Receipts the background catch-up may try: no PDF and no verdict yet, newest first. */
    @Query("SELECT * FROM captured_receipts WHERE pdfPath IS NULL AND pdfStatus IS NULL ORDER BY timestamp DESC LIMIT :limit")
    suspend fun backfillCandidates(limit: Int): List<CapturedReceiptEntity>

    @Query("SELECT * FROM captured_receipts WHERE clientId = :clientId LIMIT 1")
    suspend fun getByClientId(clientId: String): CapturedReceiptEntity?
}

@Database(entities = [CapturedReceiptEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun receiptDao(): ReceiptDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "receipts.db",
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

/** v4 adds pdfStatus. A real migration, so an update never wipes the owner's receipts. */
val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE captured_receipts ADD COLUMN pdfStatus TEXT")
    }
}
