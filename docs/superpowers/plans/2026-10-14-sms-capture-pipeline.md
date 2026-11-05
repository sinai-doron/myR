# SMS Capture Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the core SMS capture pipeline — MerchantConfig, ReceiptFilter, Room store, and BroadcastReceiver — so the app catches receipt SMS, extracts links, stores them locally, and displays them in the list.

**Architecture:** SMS arrives → `SmsReceiver` (BroadcastReceiver) reassembles multipart → `DomainMatchFilter` checks links against `MerchantConfig` domain allowlist → matched links written to Room via `ReceiptDao` → `MainActivity` observes the DAO's Flow and renders the list. No network, no heavy work in the receiver.

**Tech Stack:** Room 2.6.1 + KSP 2.0.0-1.0.21, kotlinx-serialization-json 1.7.0, Kotlin coroutines (via lifecycle-runtime-ktx already present)

**Spec:** `docs/superpowers/specs/2026-10-14-android-sms-receipt-capture-design.md`

## Global Constraints

- `minSdk 26`, `compileSdk 36`, `targetSdk 36`
- Package `com.neonsheep.myreceipts`, single `:app` module
- No `INTERNET` permission — nothing leaves the device
- Raw message body is NEVER stored — only extracted links
- Only on-list links stored (not off-list links from the same message)
- Sender label is a routing hint, not a gate (alphanumeric senders are spoofable)
- `ReceiptFilter` is an interface (seam for future heuristic detection)
- Build with `gradle assembleDebug` / `gradle test` (system Gradle 9.8.1, not wrapper)
- `ANDROID_HOME=/opt/homebrew/share/android-commandlinetools`, Java 17

## Review Focus

1. **SMS with no links at all** (OTP like "Your code is 123456") — must be silently ignored, never stored. _Test added to Task 2, `noLinks_otpMessage_returnsEmpty`._
2. **SMS with only off-list links** (e.g. bit.ly shortener, bank URL) — must be ignored, never stored. _Test added to Task 2, `offListLinksOnly_returnsEmpty`._
3. **Duplicate SMS** (same link + timestamp delivered twice by Android) — `clientId` (UUID v3 from link+timestamp) + `OnConflictStrategy.IGNORE` on a unique index must silently deduplicate. _Test added to Task 3, `duplicateClientId_isIgnored`._
4. **Hebrew UCS-2 multipart SMS** — receiver must reassemble all parts before link extraction, not process parts individually. _Test added to Task 2, `hebrewMultipart_extractsLink`._
5. **Receiver killed before insert completes** — `goAsync()` + background thread keeps the process alive for the insert. Not fully testable without process death simulation, but the pattern is verified by the integration test in Task 4.

---

## File Structure

```
app/
├── build.gradle.kts                             (modify: add Room, KSP, serialization)
├── src/
│   ├── main/
│   │   ├── AndroidManifest.xml                   (modify: add RECEIVE_SMS, receiver)
│   │   ├── assets/
│   │   │   └── merchants.json                    (create: domain allowlist)
│   │   └── java/com/neonsheep/myreceipts/
│   │       ├── data/
│   │       │   ├── MerchantConfig.kt             (create: config data + JSON loading)
│   │       │   ├── ReceiptFilter.kt              (create: interface + DomainMatchFilter)
│   │       │   ├── ReceiptStore.kt               (create: Room entity, DAO, database)
│   │       │   └── ClientId.kt                   (create: deterministic UUID generation)
│   │       ├── receiver/
│   │       │   └── SmsReceiver.kt                (create: BroadcastReceiver + handler)
│   │       ├── MainActivity.kt                   (modify: observe Room Flow)
│   │       └── ui/screens/
│   │           └── CapturedListScreen.kt         (unchanged)
│   ├── test/java/com/neonsheep/myreceipts/data/
│   │   ├── MerchantConfigTest.kt                 (create: JVM unit test)
│   │   ├── ReceiptFilterTest.kt                  (create: JVM unit test)
│   │   └── ClientIdTest.kt                       (create: JVM unit test)
│   └── androidTest/java/com/neonsheep/myreceipts/
│       └── data/
│           └── ReceiptCaptureIntegrationTest.kt   (create: Room + handler test)
```

Also modify:
- `gradle/libs.versions.toml` — add Room, KSP, serialization versions + libraries
- `build.gradle.kts` (root) — add KSP and serialization plugins

---

### Task 1: Build Dependencies and MerchantConfig

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts` (root)
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/assets/merchants.json`
- Create: `app/src/main/java/com/neonsheep/myreceipts/data/MerchantConfig.kt`
- Create: `app/src/test/java/com/neonsheep/myreceipts/data/MerchantConfigTest.kt`

**Interfaces:**
- Consumes: existing Gradle build from skeleton plan
- Produces: `MerchantConfig` data class with `fromJson(json: String): MerchantConfig`, `load(context: Context): MerchantConfig`, and `getAllDomains(): Set<String>`. `MerchantConfig.Merchant` has `name: String`, `domains: List<String>`, `senderHints: List<String>`.

- [ ] **Step 1: Write the failing MerchantConfig unit test**

File: `app/src/test/java/com/neonsheep/myreceipts/data/MerchantConfigTest.kt`

```kotlin
package com.neonsheep.myreceipts.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantConfigTest {

    private val testJson = """
        {
          "merchants": [
            {
              "name": "Shufersal",
              "domains": ["shufersal.co.il"],
              "senderHints": ["Shufersal"]
            },
            {
              "name": "Pairzon",
              "domains": ["pairzon.com"],
              "senderHints": ["OSHERAD", "Pairzon"]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun fromJson_parsesMerchants() {
        val config = MerchantConfig.fromJson(testJson)
        assertEquals(2, config.merchants.size)
        assertEquals("Shufersal", config.merchants[0].name)
        assertEquals(listOf("shufersal.co.il"), config.merchants[0].domains)
    }

    @Test
    fun getAllDomains_returnsFlatSet() {
        val config = MerchantConfig.fromJson(testJson)
        val domains = config.getAllDomains()
        assertEquals(setOf("shufersal.co.il", "pairzon.com"), domains)
    }

    @Test
    fun fromJson_emptySenderHints_defaultsToEmptyList() {
        val json = """{"merchants": [{"name": "Test", "domains": ["test.com"]}]}"""
        val config = MerchantConfig.fromJson(json)
        assertTrue(config.merchants[0].senderHints.isEmpty())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
gradle test --tests "com.neonsheep.myreceipts.data.MerchantConfigTest" 2>&1 | tail -10
```

Expected: compilation error — `MerchantConfig` not defined.

- [ ] **Step 3: Add dependencies to version catalog**

Append to `[versions]` in `gradle/libs.versions.toml`:
```toml
room = "2.6.1"
ksp = "2.0.0-1.0.21"
kotlinx-serialization-json = "1.7.0"
```

Append to `[libraries]`:
```toml
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinx-serialization-json" }
```

Append to `[plugins]`:
```toml
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 4: Update root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
```

- [ ] **Step 5: Update `app/build.gradle.kts`**

Add plugins:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}
```

Add dependencies (append to the `dependencies` block):
```kotlin
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.serialization.json)
```

- [ ] **Step 6: Create `merchants.json`**

File: `app/src/main/assets/merchants.json`

```json
{
  "merchants": [
    {
      "name": "Shufersal",
      "domains": ["shufersal.co.il"],
      "senderHints": ["Shufersal"]
    },
    {
      "name": "Pairzon",
      "domains": ["pairzon.com"],
      "senderHints": ["OSHERAD", "Pairzon"]
    }
  ]
}
```

- [ ] **Step 7: Implement `MerchantConfig.kt`**

File: `app/src/main/java/com/neonsheep/myreceipts/data/MerchantConfig.kt`

```kotlin
package com.neonsheep.myreceipts.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class MerchantConfig(
    val merchants: List<Merchant>,
) {
    @Serializable
    data class Merchant(
        val name: String,
        val domains: List<String>,
        val senderHints: List<String> = emptyList(),
    )

    fun getAllDomains(): Set<String> = merchants.flatMap { it.domains }.toSet()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(jsonString: String): MerchantConfig = json.decodeFromString(jsonString)

        fun load(context: Context): MerchantConfig = fromJson(
            context.assets.open("merchants.json").bufferedReader().use { it.readText() }
        )
    }
}
```

- [ ] **Step 8: Run tests to verify they pass**

```bash
gradle test --tests "com.neonsheep.myreceipts.data.MerchantConfigTest" 2>&1 | tail -10
```

Expected: 3 tests PASSED.

- [ ] **Step 9: Verify full build**

```bash
gradle assembleDebug 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts \
  app/src/main/assets/merchants.json \
  app/src/main/java/com/neonsheep/myreceipts/data/MerchantConfig.kt \
  app/src/test/java/com/neonsheep/myreceipts/data/MerchantConfigTest.kt
git commit -m "feat: add MerchantConfig with bundled domain allowlist and build deps"
```

---

### Task 2: ReceiptFilter

**Files:**
- Create: `app/src/main/java/com/neonsheep/myreceipts/data/ReceiptFilter.kt`
- Create: `app/src/test/java/com/neonsheep/myreceipts/data/ReceiptFilterTest.kt`

**Interfaces:**
- Consumes: `MerchantConfig` from Task 1 — `MerchantConfig(merchants)`, `getAllDomains(): Set<String>`
- Produces: `ReceiptFilter` interface with `fun filter(messageBody: String, sender: String): FilterResult`. `FilterResult` data class with `matchedLinks: List<String>`, `senderLabel: String`. `DomainMatchFilter(config: MerchantConfig)` implements `ReceiptFilter`.

- [ ] **Step 1: Write the failing ReceiptFilter unit tests**

File: `app/src/test/java/com/neonsheep/myreceipts/data/ReceiptFilterTest.kt`

```kotlin
package com.neonsheep.myreceipts.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReceiptFilterTest {

    private lateinit var filter: ReceiptFilter

    @Before
    fun setup() {
        val config = MerchantConfig(
            merchants = listOf(
                MerchantConfig.Merchant(
                    name = "Shufersal",
                    domains = listOf("shufersal.co.il"),
                    senderHints = listOf("Shufersal"),
                ),
                MerchantConfig.Merchant(
                    name = "Pairzon",
                    domains = listOf("pairzon.com"),
                    senderHints = listOf("OSHERAD", "Pairzon"),
                ),
            ),
        )
        filter = DomainMatchFilter(config)
    }

    @Test
    fun shufersal_singleLink_extracted() {
        val body = "קבלה דיגיטלית שלך מסופרסל: https://shufersal.co.il/receipt/abc123"
        val result = filter.filter(body, "Shufersal")

        assertEquals(1, result.matchedLinks.size)
        assertEquals("https://shufersal.co.il/receipt/abc123", result.matchedLinks[0])
        assertEquals("Shufersal", result.senderLabel)
    }

    @Test
    fun osherad_twoLinks_onlyOnListExtracted() {
        val body = "הקבלה שלך: https://pairzon.com/r/456 לפרטים: https://bit.ly/xyz"
        val result = filter.filter(body, "OSHERAD")

        assertEquals(1, result.matchedLinks.size)
        assertEquals("https://pairzon.com/r/456", result.matchedLinks[0])
    }

    @Test
    fun noLinks_otpMessage_returnsEmpty() {
        val body = "Your verification code is 123456"
        val result = filter.filter(body, "Google")

        assertTrue(result.matchedLinks.isEmpty())
    }

    @Test
    fun noLinks_bankAlert_returnsEmpty() {
        val body = "חיוב בסך 150.00 ש\"ח בכרטיס *1234. יתרה: 5,000 ש\"ח"
        val result = filter.filter(body, "Leumi")

        assertTrue(result.matchedLinks.isEmpty())
    }

    @Test
    fun offListLinksOnly_returnsEmpty() {
        val body = "Click here: https://bit.ly/abc123 to view your statement"
        val result = filter.filter(body, "SomeBank")

        assertTrue(result.matchedLinks.isEmpty())
    }

    @Test
    fun hebrewMultipart_extractsLink() {
        val body = "שלום, תודה שקנית בסופרסל! " +
            "הקבלה הדיגיטלית שלך: https://shufersal.co.il/receipt/long123456789"
        val result = filter.filter(body, "Shufersal")

        assertEquals(1, result.matchedLinks.size)
        assertTrue(result.matchedLinks[0].contains("shufersal.co.il"))
    }

    @Test
    fun subdomain_matches() {
        val body = "Receipt: https://receipts.shufersal.co.il/view/123"
        val result = filter.filter(body, "Shufersal")

        assertEquals(1, result.matchedLinks.size)
    }

    @Test
    fun httpWithoutS_alsoMatches() {
        val body = "Receipt: http://shufersal.co.il/receipt/123"
        val result = filter.filter(body, "Shufersal")

        assertEquals(1, result.matchedLinks.size)
    }

    @Test
    fun multipleOnListLinks_allExtracted() {
        val body = "https://pairzon.com/a https://pairzon.com/b"
        val result = filter.filter(body, "OSHERAD")

        assertEquals(2, result.matchedLinks.size)
    }

    @Test
    fun senderPassedThrough_asLabel() {
        val body = "https://shufersal.co.il/receipt/1"
        val result = filter.filter(body, "UnknownSender")

        assertEquals("UnknownSender", result.senderLabel)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
gradle test --tests "com.neonsheep.myreceipts.data.ReceiptFilterTest" 2>&1 | tail -10
```

Expected: compilation error — `ReceiptFilter`, `DomainMatchFilter`, `FilterResult` not defined.

- [ ] **Step 3: Implement `ReceiptFilter.kt`**

File: `app/src/main/java/com/neonsheep/myreceipts/data/ReceiptFilter.kt`

```kotlin
package com.neonsheep.myreceipts.data

import java.net.URI

data class FilterResult(
    val matchedLinks: List<String>,
    val senderLabel: String,
)

interface ReceiptFilter {
    fun filter(messageBody: String, sender: String): FilterResult
}

class DomainMatchFilter(config: MerchantConfig) : ReceiptFilter {

    private val allowedDomains: Set<String> = config.getAllDomains()
    private val urlPattern = Regex("""https?://[^\s]+""")

    override fun filter(messageBody: String, sender: String): FilterResult {
        val matchedLinks = urlPattern.findAll(messageBody)
            .map { it.value }
            .filter { url -> isOnList(url) }
            .toList()

        return FilterResult(
            matchedLinks = matchedLinks,
            senderLabel = sender,
        )
    }

    private fun isOnList(url: String): Boolean {
        val host = try {
            URI(url).host?.lowercase()
        } catch (_: Exception) {
            null
        } ?: return false

        return allowedDomains.any { domain ->
            host == domain || host.endsWith(".$domain")
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
gradle test --tests "com.neonsheep.myreceipts.data.ReceiptFilterTest" 2>&1 | tail -15
```

Expected: 10 tests PASSED.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/neonsheep/myreceipts/data/ReceiptFilter.kt \
  app/src/test/java/com/neonsheep/myreceipts/data/ReceiptFilterTest.kt
git commit -m "feat: add ReceiptFilter interface with domain-match implementation"
```

---

### Task 3: ReceiptStore (Room) and ClientId

**Files:**
- Create: `app/src/main/java/com/neonsheep/myreceipts/data/ClientId.kt`
- Create: `app/src/main/java/com/neonsheep/myreceipts/data/ReceiptStore.kt`
- Create: `app/src/test/java/com/neonsheep/myreceipts/data/ClientIdTest.kt`
- Create: `app/src/androidTest/java/com/neonsheep/myreceipts/data/ReceiptStoreTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks (standalone persistence layer)
- Produces: `CapturedReceiptEntity` (Room entity with fields: `id: Long`, `link: String`, `senderLabel: String`, `timestamp: Long`, `clientId: String`, `capturedAt: Long`). `ReceiptDao` with `suspend fun insert(receipt: CapturedReceiptEntity): Long`, `fun getAll(): Flow<List<CapturedReceiptEntity>>`, `suspend fun getAllOnce(): List<CapturedReceiptEntity>`. `AppDatabase.getInstance(context: Context): AppDatabase`. `generateClientId(link: String, timestamp: Long): String`.

- [ ] **Step 1: Write the failing ClientId unit test**

File: `app/src/test/java/com/neonsheep/myreceipts/data/ClientIdTest.kt`

```kotlin
package com.neonsheep.myreceipts.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ClientIdTest {

    @Test
    fun sameInputs_produceSameId() {
        val id1 = generateClientId("https://shufersal.co.il/receipt/123", 1728900000000L)
        val id2 = generateClientId("https://shufersal.co.il/receipt/123", 1728900000000L)
        assertEquals(id1, id2)
    }

    @Test
    fun differentLink_producesDifferentId() {
        val id1 = generateClientId("https://shufersal.co.il/receipt/123", 1728900000000L)
        val id2 = generateClientId("https://shufersal.co.il/receipt/456", 1728900000000L)
        assertNotEquals(id1, id2)
    }

    @Test
    fun differentTimestamp_producesDifferentId() {
        val id1 = generateClientId("https://shufersal.co.il/receipt/123", 1728900000000L)
        val id2 = generateClientId("https://shufersal.co.il/receipt/123", 1728903600000L)
        assertNotEquals(id1, id2)
    }

    @Test
    fun format_isUuid() {
        val id = generateClientId("https://shufersal.co.il/receipt/123", 1728900000000L)
        val uuidRegex = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        assert(uuidRegex.matches(id)) { "Expected UUID format, got: $id" }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
gradle test --tests "com.neonsheep.myreceipts.data.ClientIdTest" 2>&1 | tail -10
```

Expected: compilation error — `generateClientId` not defined.

- [ ] **Step 3: Implement `ClientId.kt`**

File: `app/src/main/java/com/neonsheep/myreceipts/data/ClientId.kt`

```kotlin
package com.neonsheep.myreceipts.data

import java.util.UUID

fun generateClientId(link: String, timestamp: Long): String {
    return UUID.nameUUIDFromBytes("$link|$timestamp".toByteArray()).toString()
}
```

- [ ] **Step 4: Run ClientId tests to verify they pass**

```bash
gradle test --tests "com.neonsheep.myreceipts.data.ClientIdTest" 2>&1 | tail -10
```

Expected: 4 tests PASSED.

- [ ] **Step 5: Write the Room store instrumented test**

File: `app/src/androidTest/java/com/neonsheep/myreceipts/data/ReceiptStoreTest.kt`

```kotlin
package com.neonsheep.myreceipts.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceiptStoreTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ReceiptDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.receiptDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insert_and_retrieve() = runBlocking {
        val entity = CapturedReceiptEntity(
            link = "https://shufersal.co.il/receipt/123",
            senderLabel = "Shufersal",
            timestamp = 1728900000000L,
            clientId = "test-client-id",
            capturedAt = 1728900001000L,
        )

        dao.insert(entity)
        val all = dao.getAllOnce()

        assertEquals(1, all.size)
        assertEquals("https://shufersal.co.il/receipt/123", all[0].link)
        assertEquals("Shufersal", all[0].senderLabel)
        assertEquals(1728900000000L, all[0].timestamp)
        assertEquals("test-client-id", all[0].clientId)
    }

    @Test
    fun duplicateClientId_isIgnored() = runBlocking {
        val entity1 = CapturedReceiptEntity(
            link = "https://shufersal.co.il/receipt/123",
            senderLabel = "Shufersal",
            timestamp = 1728900000000L,
            clientId = "same-id",
            capturedAt = 1728900001000L,
        )
        val entity2 = CapturedReceiptEntity(
            link = "https://shufersal.co.il/receipt/123",
            senderLabel = "Shufersal",
            timestamp = 1728900000000L,
            clientId = "same-id",
            capturedAt = 1728900002000L,
        )

        dao.insert(entity1)
        dao.insert(entity2)
        val all = dao.getAllOnce()

        assertEquals(1, all.size)
    }

    @Test
    fun getAllOnce_orderedByTimestampDesc() = runBlocking {
        dao.insert(CapturedReceiptEntity(
            link = "https://shufersal.co.il/old",
            senderLabel = "Shufersal",
            timestamp = 1000L,
            clientId = "id-old",
            capturedAt = 2000L,
        ))
        dao.insert(CapturedReceiptEntity(
            link = "https://shufersal.co.il/new",
            senderLabel = "Shufersal",
            timestamp = 3000L,
            clientId = "id-new",
            capturedAt = 4000L,
        ))

        val all = dao.getAllOnce()
        assertEquals(2, all.size)
        assertTrue(all[0].timestamp > all[1].timestamp)
    }
}
```

- [ ] **Step 6: Verify instrumented test does not compile**

```bash
gradle compileDebugAndroidTestKotlin 2>&1 | grep "error:" | head -5
```

Expected: compilation error — `AppDatabase`, `ReceiptDao`, `CapturedReceiptEntity` not defined.

- [ ] **Step 7: Implement `ReceiptStore.kt`**

File: `app/src/main/java/com/neonsheep/myreceipts/data/ReceiptStore.kt`

```kotlin
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
)

@Dao
interface ReceiptDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(receipt: CapturedReceiptEntity): Long

    @Query("SELECT * FROM captured_receipts ORDER BY timestamp DESC")
    fun getAll(): Flow<List<CapturedReceiptEntity>>

    @Query("SELECT * FROM captured_receipts ORDER BY timestamp DESC")
    suspend fun getAllOnce(): List<CapturedReceiptEntity>
}

@Database(entities = [CapturedReceiptEntity::class], version = 1, exportSchema = false)
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
                ).build().also { INSTANCE = it }
            }
        }
    }
}
```

- [ ] **Step 8: Run JVM tests (ClientId)**

```bash
gradle test --tests "com.neonsheep.myreceipts.data.ClientIdTest" 2>&1 | tail -10
```

Expected: 4 tests PASSED.

- [ ] **Step 9: Run instrumented tests (requires emulator)**

```bash
gradle connectedDebugAndroidTest --tests "com.neonsheep.myreceipts.data.ReceiptStoreTest" 2>&1 | tail -15
```

If no emulator is running, verify compilation:
```bash
gradle compileDebugAndroidTestKotlin 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL (compilation). If emulator available: 3 tests PASSED.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/neonsheep/myreceipts/data/ClientId.kt \
  app/src/main/java/com/neonsheep/myreceipts/data/ReceiptStore.kt \
  app/src/test/java/com/neonsheep/myreceipts/data/ClientIdTest.kt \
  app/src/androidTest/java/com/neonsheep/myreceipts/data/ReceiptStoreTest.kt
git commit -m "feat: add Room store with CapturedReceiptEntity, DAO, and clientId generation"
```

---

### Task 4: SmsReceiver and Manifest

**Files:**
- Create: `app/src/main/java/com/neonsheep/myreceipts/receiver/SmsReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/androidTest/java/com/neonsheep/myreceipts/data/ReceiptCaptureIntegrationTest.kt`

**Interfaces:**
- Consumes: `MerchantConfig.load(context)` from Task 1, `DomainMatchFilter(config)` and `FilterResult` from Task 2, `AppDatabase.getInstance(context)`, `ReceiptDao.insert(entity)`, `CapturedReceiptEntity`, `generateClientId(link, timestamp)` from Task 3.
- Produces: `SmsReceiver` (BroadcastReceiver) registered in the manifest for `SMS_RECEIVED`. `ReceiptCaptureHandler.processMessage(body: String, sender: String, timestamp: Long, config: MerchantConfig, dao: ReceiptDao)` — the testable core logic.

- [ ] **Step 1: Write the failing integration test**

File: `app/src/androidTest/java/com/neonsheep/myreceipts/data/ReceiptCaptureIntegrationTest.kt`

```kotlin
package com.neonsheep.myreceipts.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neonsheep.myreceipts.receiver.ReceiptCaptureHandler
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceiptCaptureIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ReceiptDao
    private lateinit var config: MerchantConfig

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.receiptDao()
        config = MerchantConfig(
            merchants = listOf(
                MerchantConfig.Merchant(
                    name = "Shufersal",
                    domains = listOf("shufersal.co.il"),
                ),
                MerchantConfig.Merchant(
                    name = "Pairzon",
                    domains = listOf("pairzon.com"),
                ),
            ),
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun receiptSms_storedInDatabase() = runBlocking {
        ReceiptCaptureHandler.processMessage(
            body = "הקבלה שלך: https://shufersal.co.il/receipt/123",
            sender = "Shufersal",
            timestamp = 1728900000000L,
            config = config,
            dao = dao,
        )

        val receipts = dao.getAllOnce()
        assertEquals(1, receipts.size)
        assertEquals("https://shufersal.co.il/receipt/123", receipts[0].link)
        assertEquals("Shufersal", receipts[0].senderLabel)
    }

    @Test
    fun nonReceiptSms_notStored() = runBlocking {
        ReceiptCaptureHandler.processMessage(
            body = "Your verification code is 123456",
            sender = "Google",
            timestamp = 1728900000000L,
            config = config,
            dao = dao,
        )

        val receipts = dao.getAllOnce()
        assertTrue(receipts.isEmpty())
    }

    @Test
    fun duplicateMessage_storedOnce() = runBlocking {
        repeat(2) {
            ReceiptCaptureHandler.processMessage(
                body = "הקבלה שלך: https://shufersal.co.il/receipt/123",
                sender = "Shufersal",
                timestamp = 1728900000000L,
                config = config,
                dao = dao,
            )
        }

        val receipts = dao.getAllOnce()
        assertEquals(1, receipts.size)
    }

    @Test
    fun mixedLinks_onlyOnListStored() = runBlocking {
        ReceiptCaptureHandler.processMessage(
            body = "הקבלה שלך: https://pairzon.com/r/456 לפרטים: https://bit.ly/xyz",
            sender = "OSHERAD",
            timestamp = 1728900000000L,
            config = config,
            dao = dao,
        )

        val receipts = dao.getAllOnce()
        assertEquals(1, receipts.size)
        assertEquals("https://pairzon.com/r/456", receipts[0].link)
    }
}
```

- [ ] **Step 2: Verify integration test does not compile**

```bash
gradle compileDebugAndroidTestKotlin 2>&1 | grep "error:" | head -5
```

Expected: compilation error — `ReceiptCaptureHandler` not defined.

- [ ] **Step 3: Implement `SmsReceiver.kt`**

File: `app/src/main/java/com/neonsheep/myreceipts/receiver/SmsReceiver.kt`

```kotlin
package com.neonsheep.myreceipts.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.neonsheep.myreceipts.data.AppDatabase
import com.neonsheep.myreceipts.data.CapturedReceiptEntity
import com.neonsheep.myreceipts.data.DomainMatchFilter
import com.neonsheep.myreceipts.data.MerchantConfig
import com.neonsheep.myreceipts.data.ReceiptDao
import com.neonsheep.myreceipts.data.generateClientId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return

        val sender = messages[0].displayOriginatingAddress ?: return
        val body = messages.joinToString("") { it.displayMessageBody ?: "" }
        val timestamp = messages[0].timestampMillis

        val config = MerchantConfig.load(context)
        val filter = DomainMatchFilter(config)
        val result = filter.filter(body, sender)
        if (result.matchedLinks.isEmpty()) return

        val pendingResult = goAsync()
        val dao = AppDatabase.getInstance(context).receiptDao()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReceiptCaptureHandler.processMessage(
                    body = body,
                    sender = sender,
                    timestamp = timestamp,
                    config = config,
                    dao = dao,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}

object ReceiptCaptureHandler {
    suspend fun processMessage(
        body: String,
        sender: String,
        timestamp: Long,
        config: MerchantConfig,
        dao: ReceiptDao,
    ) {
        val filter = DomainMatchFilter(config)
        val result = filter.filter(body, sender)
        if (result.matchedLinks.isEmpty()) return

        val now = System.currentTimeMillis()
        for (link in result.matchedLinks) {
            dao.insert(
                CapturedReceiptEntity(
                    link = link,
                    senderLabel = result.senderLabel,
                    timestamp = timestamp,
                    clientId = generateClientId(link, timestamp),
                    capturedAt = now,
                )
            )
        }
    }
}
```

- [ ] **Step 4: Update `AndroidManifest.xml`**

Replace the contents of `app/src/main/AndroidManifest.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.RECEIVE_SMS" />

    <application
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.MyReceipts.Splash">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.MyReceipts.Splash">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <receiver
            android:name=".receiver.SmsReceiver"
            android:exported="true"
            android:permission="android.permission.BROADCAST_SMS">
            <intent-filter>
                <action android:name="android.provider.Telephony.SMS_RECEIVED" />
            </intent-filter>
        </receiver>
    </application>

</manifest>
```

- [ ] **Step 5: Build and run JVM tests**

```bash
gradle assembleDebug 2>&1 | tail -5
gradle test 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL and all JVM tests pass.

- [ ] **Step 6: Run integration tests on emulator**

```bash
gradle connectedDebugAndroidTest --tests "com.neonsheep.myreceipts.data.ReceiptCaptureIntegrationTest" 2>&1 | tail -15
```

If no emulator available, verify compilation:
```bash
gradle compileDebugAndroidTestKotlin 2>&1 | tail -5
```

Expected: 4 integration tests PASSED (or compilation success).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/neonsheep/myreceipts/receiver/SmsReceiver.kt \
  app/src/main/AndroidManifest.xml \
  app/src/androidTest/java/com/neonsheep/myreceipts/data/ReceiptCaptureIntegrationTest.kt
git commit -m "feat: add SmsReceiver with RECEIVE_SMS permission and capture handler"
```

---

### Task 5: Wire Store to UI

**Files:**
- Modify: `app/src/main/java/com/neonsheep/myreceipts/MainActivity.kt`

**Interfaces:**
- Consumes: `AppDatabase.getInstance(context)` and `ReceiptDao.getAll(): Flow<List<CapturedReceiptEntity>>` from Task 3. `CapturedReceipt` data class from `ui/screens/CapturedListScreen.kt` (existing).
- Produces: `MainActivity` that observes the Room database and passes live data to `CapturedListScreen`. `CapturedReceiptEntity.toUiModel(): CapturedReceipt` extension function.

- [ ] **Step 1: Update `MainActivity.kt`**

Replace `app/src/main/java/com/neonsheep/myreceipts/MainActivity.kt` with:

```kotlin
package com.neonsheep.myreceipts

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.neonsheep.myreceipts.data.AppDatabase
import com.neonsheep.myreceipts.data.CapturedReceiptEntity
import com.neonsheep.myreceipts.ui.screens.CapturedListScreen
import com.neonsheep.myreceipts.ui.screens.CapturedReceipt
import com.neonsheep.myreceipts.ui.theme.MyReceiptsTheme
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestSmsPermissionIfNeeded()

        val dao = AppDatabase.getInstance(this).receiptDao()
        val receiptsFlow = dao.getAll().map { entities ->
            entities.map { it.toUiModel() }
        }

        setContent {
            val receipts by receiptsFlow.collectAsState(initial = emptyList())
            MyReceiptsTheme {
                CapturedListScreen(receipts = receipts)
            }
        }
    }

    private fun requestSmsPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECEIVE_SMS),
                SMS_PERMISSION_REQUEST,
            )
        }
    }

    companion object {
        private const val SMS_PERMISSION_REQUEST = 1001
    }
}

fun CapturedReceiptEntity.toUiModel(): CapturedReceipt = CapturedReceipt(
    link = link,
    senderLabel = senderLabel,
    timestamp = timestamp,
    clientId = clientId,
)
```

- [ ] **Step 2: Build and verify**

```bash
gradle assembleDebug 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run all JVM tests**

```bash
gradle test 2>&1 | tail -10
```

Expected: All tests pass (MerchantConfig, ReceiptFilter, ClientId).

- [ ] **Step 4: Install on emulator and test with SMS**

```bash
gradle installDebug 2>&1 | tail -5
```

Then grant SMS permission and send a test receipt:
```bash
adb shell pm grant com.neonsheep.myreceipts android.permission.RECEIVE_SMS
adb shell am start -n com.neonsheep.myreceipts/.MainActivity
adb emu sms send "Shufersal" "קבלה דיגיטלית שלך מסופרסל: https://shufersal.co.il/receipt/abc123"
```

Expected: The receipt card appears in the list within seconds. Take a screenshot to confirm:
```bash
adb exec-out screencap -p > /tmp/myreceipts-capture.png
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/neonsheep/myreceipts/MainActivity.kt
git commit -m "feat: wire Room store to UI with live Flow observation and SMS permission request"
```
