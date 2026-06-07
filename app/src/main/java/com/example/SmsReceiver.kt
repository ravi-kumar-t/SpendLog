package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.utils.SmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (message in messages) {
                val body = message.messageBody ?: continue
                
                // Real transactional SMS keyword guard and promotional filters run within parser.
                // Let's parse the SMS using the strict logic.
                val parsed = SmsParser.parseSms(body) ?: continue
                val amount = parsed.amount
                val merchant = if (parsed.cleanedMerchant != "Unknown Merchant") parsed.cleanedMerchant else ""
                val type = parsed.type
                
                if (type == "TYPE_INCOME") {
                    Log.d("SmsReceiver", "Credit Transaction detected (TYPE_INCOME). Saving directly and aborting overlay execution.")
                    if (amount > 0.0f) {
                        val db = com.example.data.AppDatabase.getDatabase(context)
                        val repo = com.example.data.ExpenseRepository(db.expenseDao())
                        CoroutineScope(Dispatchers.IO).launch {
                            repo.insertTransaction(
                                com.example.data.Transaction(
                                    amount = amount,
                                    merchant = if (merchant.isNotEmpty()) merchant else "Self",
                                    item_description = "Received UPI payment",
                                    category = "Other",
                                    timestamp = System.currentTimeMillis(),
                                    type = "TYPE_INCOME"
                                )
                            )
                        }
                    }
                    continue // Abort execution immediately inside the BroadcastReceiver. Do NOT launch the OverlayService.
                }

                if (amount > 0.0f) {
                    try {
                        val serviceIntent = Intent(context, OverlayService::class.java).apply {
                            putExtra("extracted_amount", amount)
                            putExtra("extracted_merchant", merchant)
                            putExtra("amount", amount)
                            putExtra("merchant", merchant)
                            putExtra("sms_body", body)
                        }
                        context.startService(serviceIntent)
                    } catch (e: Exception) {
                        Log.e("SmsReceiver", "Error starting OverlayService: ${e.message}")
                    }
                }
            }
        }
    }
}
