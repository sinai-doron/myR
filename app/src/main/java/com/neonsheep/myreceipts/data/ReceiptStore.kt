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
import androidx.room.Transaction
import androidx.room.Update
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
    /** The amount paid, in shekels, when known. See [totalSource] for where it came from. */
    val totalAmount: Double? = null,
    val pdfPath: String? = null,
    /** Why there is no PDF, when the backend said so: see [PdfStatus]. Null otherwise. */
    val pdfStatus: String? = null,
    /** Where [totalAmount] came from: see [TotalSource]. Null while nobody has looked. */
    val totalSource: String? = null,
    /** The store's name as the user typed it. Null: [senderLabel]. */
    val storeName: String? = null,
    /** When the purchase was made, as the user set it (epoch millis). Null: [timestamp], when the text arrived. */
    val purchasedAt: Long? = null,
    /** A [Category] name for this receipt only. Null: its store's category. */
    val category: String? = null,
    /** The last day to return it (epoch millis, end of that day). Null: none set. */
    val returnBy: Long? = null,
    /** The last day of its warranty (epoch millis, end of that day). Null: none set. */
    val warrantyUntil: Long? = null,
    /** The user said this isn't the same receipt as a similar one, so it isn't flagged again. */
    @ColumnInfo(defaultValue = "0") val notDuplicate: Boolean = false,
    /** How it came in: see [Origin]. A file or scan has no [link]: its PDF is the receipt. */
    @ColumnInfo(defaultValue = "sms") val origin: String = Origin.SMS,
    /** The receipt's or invoice's own number, as printed. Null: not known. */
    val invoiceNumber: String? = null,
    /** The business's 9-digit Israeli tax ID (ח.פ. / עוסק מורשה). Null: not known. */
    val businessId: String? = null,
    /** The VAT included in [totalAmount], in shekels. Null: not known. */
    val vatAmount: Double? = null,
    /** The branch's street address, as printed. Null: not known. */
    val storeAddress: String? = null,
    /** The store's phone number, digits and dashes. Null: not known. */
    val storePhone: String? = null,
    /** The branch's name or number, as printed. Null: not known. */
    val branch: String? = null,
    /**
     * The ISO 4217 code of the currency the receipt is in, when not shekels. Its [vatAmount], items and
     * [originalTotal] are in it; [totalAmount] is always shekels, converted at [ilsRate].
     */
    val currency: String? = null,
    /** The total as printed, in [currency]. Null: not known, or a shekel receipt. */
    val originalTotal: Double? = null,
    /** Shekels per unit of [currency] that [totalAmount] was converted at. Null: not converted (or typed by the user). */
    val ilsRate: Double? = null,
    /** The day [ilsRate] is from (YYYY-MM-DD), the ECB's. */
    val rateDate: String? = null,
) {
    /** The store, as the user named it or as the text said. */
    val store: String get() = storeName ?: senderLabel

    /** When it was bought: the user's date, else when the text arrived. */
    val boughtAt: Long get() = purchasedAt ?: timestamp
}

/** How a receipt came in. */
object Origin {
    /** A receipt link in a text message. */
    const val SMS = "sms"

    /** A receipt link shared to the app (WhatsApp, email, the browser): fetched like an SMS link. */
    const val LINK = "link"

    /** A PDF, picked in the app or shared to it. */
    const val FILE = "file"

    /** A photo, picked in the app or shared to it, saved as a one-page PDF. */
    const val PHOTO = "photo"

    /** A paper receipt, scanned with the camera. */
    const val SCAN = "scan"

    /** Whether it came with a link, rather than as a file the phone already has. */
    fun hasLink(origin: String): Boolean = origin == SMS || origin == LINK

    /** Whether its PDF is a picture of the paper we made or the scanner made, which may need turning the right way up. */
    fun isPicture(origin: String): Boolean = origin == SCAN || origin == PHOTO
}

/** Where a receipt's amount came from. */
object TotalSource {
    /** The receipt page, read by the backend when it made the PDF (X-Receipt-Total). */
    const val PAGE = "page"

    /** The PDF's text, read on the phone. */
    const val PDF = "pdf"

    /** Typed in by the user. Never replaced by a read amount. */
    const val USER = "user"

    /** Looked for in the PDF and not found: not looked for again, but a page amount may still come. */
    const val NONE = "none"

    /** A made-up receipt's amount ([SampleReceipts]). */
    const val SAMPLE = "sample"
}

/** The category a store's receipts count in, as the user set it. Stores not here use [Category.guessFor]. */
@Entity(tableName = "store_categories")
data class StoreCategoryEntity(
    @PrimaryKey val senderLabel: String,
    /** A [Category] name. */
    val category: String,
)

/** A monthly budget, in shekels: for one [Category] (by name), or for everything ([ALL]). */
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val category: String,
    val monthlyLimit: Double,
) {
    companion object {
        const val ALL = "ALL"
    }
}

@Dao
interface SpendingDao {
    @Query("SELECT * FROM store_categories")
    fun storeCategories(): Flow<List<StoreCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setStoreCategory(entity: StoreCategoryEntity)

    @Query("SELECT * FROM budgets")
    fun budgets(): Flow<List<BudgetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setBudget(entity: BudgetEntity)

    @Query("DELETE FROM budgets WHERE category = :category")
    suspend fun removeBudget(category: String)
}

/** What the backend said about a receipt it couldn't turn into a PDF. */
object PdfStatus {
    /** The link's site isn't one the backend serves yet (HTTP 400). */
    const val UNSUPPORTED = "unsupported"

    /** The store no longer has the receipt, e.g. an expired Shufersal link (HTTP 410). */
    const val GONE = "gone"

    /** The background catch-up tried once and the backend couldn't make a PDF. Not retried on its own. */
    const val FAILED = "failed"

    /** A made-up receipt ([SampleReceipts]): there is no PDF to get. */
    const val SAMPLE = "sample"
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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(receipts: List<CapturedReceiptEntity>)

    /** Removes every made-up receipt ([SampleReceipts]), and nothing else. */
    @Query("DELETE FROM captured_receipts WHERE clientId LIKE 'sample-%'")
    suspend fun deleteSamples()

    @Query("UPDATE captured_receipts SET pdfPath = :pdfPath, pdfStatus = NULL WHERE clientId = :clientId")
    suspend fun updatePdfPath(clientId: String, pdfPath: String)

    @Query("UPDATE captured_receipts SET pdfStatus = :status WHERE clientId = :clientId")
    suspend fun updatePdfStatus(clientId: String, status: String?)

    /** Receipts the background catch-up may try: a link, no PDF and no verdict yet, newest first. */
    @Query("SELECT * FROM captured_receipts WHERE link != '' AND pdfPath IS NULL AND pdfStatus IS NULL ORDER BY timestamp DESC LIMIT :limit")
    suspend fun backfillCandidates(limit: Int): List<CapturedReceiptEntity>

    @Query("SELECT * FROM captured_receipts WHERE clientId = :clientId LIMIT 1")
    suspend fun getByClientId(clientId: String): CapturedReceiptEntity?

    /** A receipt already saved for this link, by SMS or shared: so sharing it doesn't add a second. */
    @Query("SELECT * FROM captured_receipts WHERE link = :link AND link != '' LIMIT 1")
    suspend fun getByLink(link: String): CapturedReceiptEntity?

    /** An amount read from the receipt page: replaces any read amount, never one the user typed. */
    @Query(
        "UPDATE captured_receipts SET totalAmount = :amount, totalSource = 'page' " +
            "WHERE clientId = :clientId AND (totalSource IS NULL OR totalSource != 'user')",
    )
    suspend fun setPageTotal(clientId: String, amount: Double)

    /** What reading the PDF found ([amount] null: nothing), only where no amount is known yet. */
    @Query(
        "UPDATE captured_receipts SET totalAmount = :amount, totalSource = :source " +
            "WHERE clientId = :clientId AND totalAmount IS NULL AND (totalSource IS NULL OR totalSource = 'none')",
    )
    suspend fun setPdfTotal(clientId: String, amount: Double?, source: String)

    /**
     * The user's own amount, or none ([amount] null), which no read amount replaces. In shekels: for a
     * receipt in another currency, what they were charged, so it is no longer a conversion.
     */
    @Query(
        "UPDATE captured_receipts SET totalAmount = :amount, totalSource = 'user', ilsRate = NULL, rateDate = NULL " +
            "WHERE clientId = :clientId",
    )
    suspend fun setUserTotal(clientId: String, amount: Double?)

    /** A receipt's currency as read ([currency] null: shekels), its printed total, and the rate it was converted at. */
    @Query(
        "UPDATE captured_receipts SET currency = :currency, originalTotal = :originalTotal, ilsRate = :ilsRate, " +
            "rateDate = :rateDate WHERE clientId = :clientId",
    )
    suspend fun setCurrency(clientId: String, currency: String?, originalTotal: Double?, ilsRate: Double?, rateDate: String?)

    /** The user's store name ([name] null: back to the text's sender). */
    @Query("UPDATE captured_receipts SET storeName = :name WHERE clientId = :clientId")
    suspend fun setStoreName(clientId: String, name: String?)

    /** The user's purchase date ([at] null: back to when the text arrived). */
    @Query("UPDATE captured_receipts SET purchasedAt = :at WHERE clientId = :clientId")
    suspend fun setPurchasedAt(clientId: String, at: Long?)

    /** This receipt's own category ([category] null: back to its store's). */
    @Query("UPDATE captured_receipts SET category = :category WHERE clientId = :clientId")
    suspend fun setCategory(clientId: String, category: String?)

    /** The branch the user confirmed: its name or number, address and phone. */
    @Query(
        "UPDATE captured_receipts SET branch = :branch, storeAddress = :address, storePhone = :phone " +
            "WHERE clientId = :clientId",
    )
    suspend fun setBranch(clientId: String, branch: String?, address: String?, phone: String?)

    /** The printed details the user confirmed: invoice number, tax ID and VAT. */
    @Query(
        "UPDATE captured_receipts SET invoiceNumber = :invoiceNumber, businessId = :businessId, vatAmount = :vat " +
            "WHERE clientId = :clientId",
    )
    suspend fun setDetails(clientId: String, invoiceNumber: String?, businessId: String?, vat: Double?)

    @Query("UPDATE captured_receipts SET returnBy = :returnBy, warrantyUntil = :warrantyUntil WHERE clientId = :clientId")
    suspend fun setDeadlines(clientId: String, returnBy: Long?, warrantyUntil: Long?)

    /**
     * Everything the edit screen saves, at once. The amount becomes the user's only when they changed it, so
     * opening and saving the screen never turns a read amount into a typed one.
     */
    @Transaction
    suspend fun applyEdit(
        clientId: String,
        storeName: String?,
        purchasedAt: Long?,
        category: String?,
        returnBy: Long?,
        warrantyUntil: Long?,
        amount: Double?,
        amountChanged: Boolean,
    ) {
        setStoreName(clientId, storeName)
        setPurchasedAt(clientId, purchasedAt)
        setCategory(clientId, category)
        setDeadlines(clientId, returnBy, warrantyUntil)
        if (amountChanged) setUserTotal(clientId, amount)
    }

    /** Receipts with a deadline still ahead of [now]: the reminders to schedule. */
    @Query("SELECT * FROM captured_receipts WHERE returnBy >= :now OR warrantyUntil >= :now")
    suspend fun withDeadlinesAfter(now: Long): List<CapturedReceiptEntity>

    @Query("UPDATE captured_receipts SET notDuplicate = 1 WHERE clientId IN (:clientIds)")
    suspend fun markNotDuplicate(clientIds: List<String>)

    @Update
    suspend fun update(receipt: CapturedReceiptEntity)

    @Query("DELETE FROM captured_receipts WHERE clientId IN (:clientIds)")
    suspend fun deleteByClientIds(clientIds: List<String>)

    /** Receipts the user said are the same, as one: [kept] saved, the others deleted, at once. */
    @Transaction
    suspend fun merge(kept: CapturedReceiptEntity, removed: List<String>) {
        update(kept)
        deleteByClientIds(removed)
    }

    /** Receipts with a PDF on the phone whose amount nobody has looked for yet. */
    @Query("SELECT * FROM captured_receipts WHERE pdfPath IS NOT NULL AND totalAmount IS NULL AND totalSource IS NULL")
    suspend fun withoutTotalLookedFor(): List<CapturedReceiptEntity>
}

/**
 * One line of what a receipt says was bought, read with AI and kept by the user. Its receipt's [receiptClientId];
 * a trigger deletes a receipt's items with it ([ITEMS_GO_WITH_RECEIPT]).
 */
@Entity(tableName = "receipt_items", indices = [Index(value = ["receiptClientId"])])
data class ReceiptItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptClientId: String,
    /** Its place on the receipt, from 0. */
    val position: Int,
    val name: String,
    /** The barcode or the store's product code. */
    val code: String? = null,
    val quantity: Double? = null,
    /** [ItemUnit]: per unit, or by weight. */
    val unit: String? = null,
    val unitPrice: Double? = null,
    /** The line's price before [discount]. */
    val price: Double? = null,
    /** The discount printed for this line, as a positive amount. */
    val discount: Double? = null,
)

object ItemUnit {
    const val UNIT = "unit"
    const val KG = "kg"
}

@Dao
interface ItemDao {
    @Query("SELECT * FROM receipt_items WHERE receiptClientId = :clientId ORDER BY position")
    fun itemsFor(clientId: String): Flow<List<ReceiptItemEntity>>

    @Query("SELECT * FROM receipt_items WHERE receiptClientId = :clientId ORDER BY position")
    suspend fun itemsForOnce(clientId: String): List<ReceiptItemEntity>

    @Query("DELETE FROM receipt_items WHERE receiptClientId = :clientId")
    suspend fun deleteFor(clientId: String)

    @Insert
    suspend fun insertAll(items: List<ReceiptItemEntity>)

    /** A receipt's items, all at once: the old ones go. */
    @Transaction
    suspend fun replace(clientId: String, items: List<ReceiptItemEntity>) {
        deleteFor(clientId)
        insertAll(items.mapIndexed { i, item -> item.copy(id = 0, receiptClientId = clientId, position = i) })
    }
}

/**
 * Deletes a receipt's items whenever the receipt is deleted, by any of the DAO's delete queries. A trigger rather
 * than a foreign key: it works whatever PRAGMA foreign_keys says, and an INSERT OR REPLACE of a receipt (none
 * today) would not silently drop its items, as ON DELETE CASCADE would; SQLite fires no delete trigger for a
 * REPLACE while recursive_triggers is off.
 */
const val ITEMS_GO_WITH_RECEIPT =
    "CREATE TRIGGER IF NOT EXISTS receipt_items_go_with_receipt AFTER DELETE ON captured_receipts " +
        "BEGIN DELETE FROM receipt_items WHERE receiptClientId = OLD.clientId; END"

@Database(
    entities = [CapturedReceiptEntity::class, StoreCategoryEntity::class, BudgetEntity::class, ReceiptItemEntity::class],
    version = DB_VERSION,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun receiptDao(): ReceiptDao

    abstract fun spendingDao(): SpendingDao

    abstract fun itemDao(): ItemDao

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
                    .addMigrations(*MIGRATIONS)
                    // Only the two development versions that predate real migrations may be wiped. Any later
                    // version without a migration must fail loudly in tests, never erase the owner's receipts.
                    .fallbackToDestructiveMigrationFrom(true, *WIPED_VERSIONS)
                    .addCallback(
                        object : Callback() {
                            override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                                db.execSQL(ITEMS_GO_WITH_RECEIPT)
                            }
                        },
                    )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

/** The database version. A bump needs a migration in [MIGRATIONS]. */
const val DB_VERSION = 11

/** Versions 1 and 2 were development builds with no migrations: the only ones an update may wipe. */
val WIPED_VERSIONS = intArrayOf(1, 2)

/**
 * Every migration, oldest first. Each version bump adds one here; MigrationsTest fails when the chain
 * doesn't reach [AppDatabase]'s version.
 */
val MIGRATIONS: Array<androidx.room.migration.Migration>
    get() = arrayOf(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)

/** v4 adds pdfStatus. A real migration, so an update never wipes the owner's receipts. */
val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE captured_receipts ADD COLUMN pdfStatus TEXT")
    }
}

/** v5 adds totalSource: where a receipt's amount came from. */
val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE captured_receipts ADD COLUMN totalSource TEXT")
    }
}

/** v6 adds each store's category and the monthly budgets. */
val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `store_categories` (`senderLabel` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                "PRIMARY KEY(`senderLabel`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `budgets` (`category` TEXT NOT NULL, `monthlyLimit` REAL NOT NULL, " +
                "PRIMARY KEY(`category`))",
        )
    }
}

/** v7 lets the user correct a receipt (store, date, category), set return and warranty deadlines, and dismiss a duplicate flag. */
val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE captured_receipts ADD COLUMN storeName TEXT")
        db.execSQL("ALTER TABLE captured_receipts ADD COLUMN purchasedAt INTEGER")
        db.execSQL("ALTER TABLE captured_receipts ADD COLUMN category TEXT")
        db.execSQL("ALTER TABLE captured_receipts ADD COLUMN returnBy INTEGER")
        db.execSQL("ALTER TABLE captured_receipts ADD COLUMN warrantyUntil INTEGER")
        db.execSQL("ALTER TABLE captured_receipts ADD COLUMN notDuplicate INTEGER NOT NULL DEFAULT 0")
    }
}

/** v8 keeps how each receipt came in, for files and scans that have no link. Every older receipt came by SMS. */
val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE captured_receipts ADD COLUMN origin TEXT NOT NULL DEFAULT 'sms'")
    }
}

/** v9 keeps a receipt's printed details, read with AI and confirmed by the user. */
val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE captured_receipts ADD COLUMN invoiceNumber TEXT")
        db.execSQL("ALTER TABLE captured_receipts ADD COLUMN businessId TEXT")
        db.execSQL("ALTER TABLE captured_receipts ADD COLUMN vatAmount REAL")
    }
}

/** v10 keeps the branch (address, phone, name) and the items bought, read with AI and kept by the user. */
val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE captured_receipts ADD COLUMN storeAddress TEXT")
        db.execSQL("ALTER TABLE captured_receipts ADD COLUMN storePhone TEXT")
        db.execSQL("ALTER TABLE captured_receipts ADD COLUMN branch TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `receipt_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`receiptClientId` TEXT NOT NULL, `position` INTEGER NOT NULL, `name` TEXT NOT NULL, `code` TEXT, " +
                "`quantity` REAL, `unit` TEXT, `unitPrice` REAL, `price` REAL, `discount` REAL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_receipt_items_receiptClientId` ON `receipt_items` (`receiptClientId`)")
        db.execSQL(ITEMS_GO_WITH_RECEIPT)
    }
}

/** v11 keeps a receipt's own currency, its printed total, and the shekel rate it was converted at. */
val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE captured_receipts ADD COLUMN currency TEXT")
        db.execSQL("ALTER TABLE captured_receipts ADD COLUMN originalTotal REAL")
        db.execSQL("ALTER TABLE captured_receipts ADD COLUMN ilsRate REAL")
        db.execSQL("ALTER TABLE captured_receipts ADD COLUMN rateDate TEXT")
    }
}
