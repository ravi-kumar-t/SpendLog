package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactionsFlow(): Flow<List<Transaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions")
    suspend fun clearAllTransactions()

    @Query("SELECT * FROM merchant_mapping WHERE merchant_name = :merchantName LIMIT 1")
    suspend fun getMerchantMapping(merchantName: String): MerchantMapping?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMerchantMapping(mapping: MerchantMapping)

    @Query("DELETE FROM merchant_mapping")
    suspend fun clearAllMappings()
}
