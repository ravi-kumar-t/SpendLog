package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "merchant_mapping")
data class MerchantMapping(
    @PrimaryKey val merchant_name: String,
    val default_item: String,
    val default_category: String
)

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val amount: Float,
    val merchant: String,
    val item_description: String,
    val category: String,
    val timestamp: Long,
    val type: String = "TYPE_EXPENSE"
)
