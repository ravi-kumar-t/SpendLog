package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ExpenseRepository
import com.example.data.MerchantMapping
import com.example.data.Transaction
import com.example.utils.SmsParser
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class MainViewModel(private val repository: ExpenseRepository) : ViewModel() {

    // Observe transactions from Room
    val transactions: StateFlow<List<Transaction>> = repository.allTransactions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // SMS simulation input
    var smsInput by mutableStateOf("Debited: INR 120.00 on UPI/9876543210@paytm/Arun Dabha")
        private set

    // Overlay state
    var isOverlayVisible by mutableStateOf(false)
        private set

    var currentAmount by mutableStateOf(0.0f)
        private set

    var currentMerchant by mutableStateOf("")
        private set

    var currentRawMerchant by mutableStateOf("")
        private set

    // State for Text Field in overlay to programmatically manage cursor and selection
    var itemDescriptionValue by mutableStateOf(TextFieldValue(""))

    var errorMessage by mutableStateOf("")
        private set

    fun updateSmsInput(newSms: String) {
        smsInput = newSms
    }

    fun onSimulateUpiPayment() {
        val parsed = SmsParser.parseSms(smsInput) ?: return
        
        viewModelScope.launch {
            // Find if merchant already mapped in Database
            val mapping = repository.getMerchantMapping(parsed.cleanedMerchant)
            
            currentAmount = parsed.amount
            currentMerchant = parsed.cleanedMerchant
            currentRawMerchant = parsed.rawMerchant
            errorMessage = ""
            
            if (mapping != null) {
                // Pre-fill and select all text to allow auto-overwriting and typing
                val defaultItem = mapping.default_item
                itemDescriptionValue = TextFieldValue(
                    text = defaultItem,
                    selection = TextRange(0, defaultItem.length)
                )
            } else {
                // Ensure field starts empty
                itemDescriptionValue = TextFieldValue("")
            }
            
            isOverlayVisible = true
        }
    }

    fun onConfirmExpense() {
        val itemText = itemDescriptionValue.text.trim()
        if (itemText.isEmpty()) {
            errorMessage = "Please describe what did you buy!"
            return
        }

        val category = SmsParser.mapItemToCategory(itemText)
        val finalMerchant = if (category == "Personal Transfers") {
            SmsParser.extractRecipientName(itemText, currentMerchant)
        } else {
            currentMerchant
        }

        viewModelScope.launch {
            // 1. Upsert merchant mapping
            val mapping = MerchantMapping(
                merchant_name = currentMerchant,
                default_item = itemText,
                default_category = category
            )
            repository.upsertMerchantMapping(mapping)

            // 2. Insert transaction
            val transaction = Transaction(
                amount = currentAmount,
                merchant = finalMerchant,
                item_description = itemText,
                category = category,
                timestamp = System.currentTimeMillis()
            )
            repository.insertTransaction(transaction)

            // 3. Reset and dismiss overlay
            isOverlayVisible = false
            itemDescriptionValue = TextFieldValue("")
            errorMessage = ""
        }
    }

    fun onDismissOverlay() {
        isOverlayVisible = false
        itemDescriptionValue = TextFieldValue("")
        errorMessage = ""
    }

    fun onClearDatabase() {
        viewModelScope.launch {
            repository.clearTransactions()
            repository.clearMappings()
        }
    }

    // Helper functions for UI
    fun getFormattedMonthName(): String {
        val cal = Calendar.getInstance()
        return cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()) ?: "this Month"
    }
}

class MainViewModelFactory(private val repository: ExpenseRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
