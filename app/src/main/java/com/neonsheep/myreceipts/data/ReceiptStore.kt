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
)

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

    @Query("UPDATE captured_receipts SET pdfPath = :pdfPath WHERE clientId = :clientId")
    suspend fun updatePdfPath(clientId: String, pdfPath: String)

    @Query("SELECT * FROM captured_receipts WHERE clientId = :clientId LIMIT 1")
    suspend fun getByClientId(clientId: String): CapturedReceiptEntity?
}

@Database(entities = [CapturedReceiptEntity::class], version = 3, exportSchema = false)
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
                ).fallbackToDestructiveMigration(dropAllTables = true).build().also { INSTANCE = it }
            }
        }
    }
}
