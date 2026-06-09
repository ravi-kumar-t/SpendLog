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

    // On-Device LLM Instance initialized once
    private val onDeviceLlm = com.example.utils.OnDeviceLLM()

    // Explicit state variable to track predicted category
    var predictedCategory by mutableStateOf("")
        private set

    var isManuallySelected by mutableStateOf(false)
        private set

    private var predictionJob: kotlinx.coroutines.Job? = null

    private fun toTitleCase(s: String): String {
        return s.split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word ->
                if (word.equals("&", ignoreCase = true)) {
                    "&"
                } else {
                    word.lowercase(Locale.getDefault())
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
            }
    }

    fun predictCategoryForInput(description: String) {
        android.util.Log.d("SPENDLOG_AI", "➡️ Function triggered with input: '$description' (Length: ${description.length})")
        android.util.Log.d("SPENDLOG_AI", "ℹ️ current state - isManuallySelected: $isManuallySelected, current predictedCategory: '$predictedCategory'")
        predictionJob?.cancel() // Explicitly called immediately to kill stale background loops
        val trimmed = description.trim()
        if (trimmed.isEmpty()) {
            predictedCategory = ""
            isManuallySelected = false
            return
        }

        // If user already manually selected a chip, do not let typing/recomposition overwrite it
        if (isManuallySelected) {
            return
        }

        // Tier 1: 0ms Instant Local-First Vocabulary Match
        val matchedEntry = SmsParser.matchVocabulary(trimmed)
        if (matchedEntry != null) {
            android.util.Log.d("SPENDLOG_AI", "⚡ Instant vocabulary match, AI sleeps: '${matchedEntry.key}' -> '${matchedEntry.value}'")
            predictedCategory = matchedEntry.value
            return
        }

        // Tier 2: Local AI Fallback Execution Gate (Trigger only after 500ms pause and no vocabulary entry match)
        android.util.Log.d("SPENDLOG_AI", "🌀 Launching background coroutine thread on Dispatchers.Default with 500ms debounce...")
        predictionJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            kotlinx.coroutines.delay(500)
            try {
                val predicted = kotlinx.coroutines.withTimeoutOrNull(800) {
                    onDeviceLlm.generateContent(
                        "auto-categorization of: $trimmed (System Instructions: You must aggressively prioritize matching item inputs to our baseline categories first. " +
                        "For example, any fruits, vegetables, snacks, meats, groceries, or raw ingredients MUST map strictly to 'Food & Drinks' or 'Groceries & Shopping'. " +
                        "If the item is a pen, notebook, textbook, calculator, or pencil, it must classify into a category called 'Education' or 'Stationery'. " +
                        "If the item is an article of clothing, footwear, or bags, it must classify into a category called 'Apparel & Clothing'. " +
                        "If the item is an appliance or electronic hardware (like a charger or mouse), it must classify into 'Electronics'. " +
                        "CRITICAL RULE: You are strictly forbidden from ever appending the word 'Items', 'Shopping', or 'Expenses' to an input noun to create a category (e.g., 'pen' -> 'Pen Items' is completely banned). You must perform genuine semantic classification. " +
                        "ONLY generate a brand-new 2-word title-cased category if the item completely breaks our standard baseline context (e.g., 'trimmer' -> 'Personal Care').)"
                    )
                }
                // Double-check lock state before applying
                if (!isManuallySelected) {
                    android.util.Log.d("SPENDLOG_AI", "🤖 RAW AI Model Output: '$predicted'")
                    if (predicted != null && predicted.isNotBlank()) {
                        val cleaned = predicted.replace("\"", "").trim()
                        android.util.Log.d("SPENDLOG_AI", "🧼 Cleaned/Sanitized string value: '$cleaned'")
                        if (cleaned.isNotBlank()) {
                            val baseCategories = listOf(
                                "Food & Drinks",
                                "Groceries & Shopping",
                                "Travel & Transport",
                                "Bills & Utilities",
                                "Medical & Healthcare",
                                "Personal Transfers",
                                "Other"
                            )
                            val matched = baseCategories.firstOrNull { it.equals(cleaned, ignoreCase = true) }
                            android.util.Log.d("SPENDLOG_AI", "🎯 Case-insensitive list match yield: '$matched'")
                            predictedCategory = if (matched != null) {
                                matched
                            } else {
                                toTitleCase(cleaned)
                            }
                            android.util.Log.d("SPENDLOG_AI", "✅ Setting state variable predictedCategory to: '$predictedCategory'")
                        } else if (predictedCategory.isEmpty()) {
                            android.util.Log.d("SPENDLOG_AI", "⚠️ Cleaned result is blank, running fallback to SmsParser...")
                            predictedCategory = SmsParser.mapItemToCategory(trimmed)
                            android.util.Log.d("SPENDLOG_AI", "✅ Fallback set variable predictedCategory to: '$predictedCategory'")
                        }
                    } else if (predictedCategory.isEmpty()) {
                        android.util.Log.d("SPENDLOG_AI", "⚠️ RAW prediction null or blank, running fallback to SmsParser...")
                        predictedCategory = SmsParser.mapItemToCategory(trimmed)
                        android.util.Log.d("SPENDLOG_AI", "✅ Fallback set variable predictedCategory to: '$predictedCategory'")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SPENDLOG_AI", "❌ CRITICAL: Coroutine thread caught execution crash!", e)
                if (!isManuallySelected && predictedCategory.isEmpty()) {
                    android.util.Log.d("SPENDLOG_AI", "⚠️ Catch fallback, running fallback to SmsParser...")
                    predictedCategory = SmsParser.mapItemToCategory(trimmed)
                    android.util.Log.d("SPENDLOG_AI", "✅ Fallback set variable predictedCategory to: '$predictedCategory'")
                }
            }
        }
    }

    fun setPredictedCategoryManually(category: String) {
        predictedCategory = category
        isManuallySelected = true
    }

    fun clearPrediction() {
        predictedCategory = ""
        isManuallySelected = false
        predictionJob?.cancel()
    }

    fun clearCategorySelection() {
        // Clear any model prediction or state as necessary
        clearPrediction()
    }

    // Overlay state
    var isOverlayVisible by mutableStateOf(false)
        private set

    var showClearConfirmationDialog by mutableStateOf(false)
        private set

    fun triggerClearConfirmation() {
        showClearConfirmationDialog = true
    }

    fun dismissClearConfirmation() {
        showClearConfirmationDialog = false
    }

    fun confirmClearDatabase() {
        showClearConfirmationDialog = false
        onClearDatabase()
    }

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
        
        if (parsed.type == "TYPE_INCOME") {
            // It is an income, do not show overlay, but save to database directly!
            viewModelScope.launch {
                repository.insertTransaction(
                    Transaction(
                        amount = parsed.amount,
                        merchant = if (parsed.cleanedMerchant.isNotEmpty() && parsed.cleanedMerchant != "Unknown Merchant") parsed.cleanedMerchant else "Self",
                        item_description = "Received UPI payment",
                        category = "Other",
                        timestamp = System.currentTimeMillis(),
                        type = "TYPE_INCOME"
                    )
                )
            }
            return
        }

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

        val category = if (predictedCategory.isNotBlank()) predictedCategory else "Other"
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

    fun insertManualTransaction(
        itemDescription: String,
        amount: Float,
        merchantName: String,
        category: String
    ) {
        viewModelScope.launch {
            val cleanedMerchant = SmsParser.cleanMerchant(merchantName)
            val finalMerchant = if (category == "Personal Transfers") {
                SmsParser.extractRecipientName(itemDescription, cleanedMerchant)
            } else {
                cleanedMerchant
            }

            // 1. Upsert merchant mapping
            val mapping = MerchantMapping(
                merchant_name = cleanedMerchant,
                default_item = itemDescription,
                default_category = category
            )
            repository.upsertMerchantMapping(mapping)

            // 2. Insert transaction
            val transaction = Transaction(
                amount = amount,
                merchant = finalMerchant,
                item_description = itemDescription,
                category = category,
                timestamp = System.currentTimeMillis(),
                type = "TYPE_EXPENSE"
            )
            repository.insertTransaction(transaction)
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
