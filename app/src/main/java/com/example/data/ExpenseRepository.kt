package com.example.data

import kotlinx.coroutines.flow.Flow

class ExpenseRepository(private val expenseDao: ExpenseDao) {
    val allTransactions: Flow<List<Transaction>> = expenseDao.getAllTransactionsFlow()

    suspend fun insertTransaction(transaction: Transaction) {
        expenseDao.insertTransaction(transaction)
    }

    suspend fun clearTransactions() {
        expenseDao.clearAllTransactions()
    }

    suspend fun getMerchantMapping(merchantName: String): MerchantMapping? {
        return expenseDao.getMerchantMapping(merchantName)
    }

    suspend fun upsertMerchantMapping(mapping: MerchantMapping) {
        expenseDao.upsertMerchantMapping(mapping)
    }

    suspend fun clearMappings() {
        expenseDao.clearAllMappings()
    }
}
