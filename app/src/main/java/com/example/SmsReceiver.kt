package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.utils.SmsParser

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (message in messages) {
                val body = message.messageBody ?: continue
                
                // Real transactional SMS keyword guard
                val isTransactional = body.contains("Debited", ignoreCase = true) || 
                                      body.contains("Sent INR", ignoreCase = true) || 
                                      body.contains("Rs.", ignoreCase = true) ||
                                      body.contains("Rs ", ignoreCase = true) ||
                                      body.contains("INR", ignoreCase = true) ||
                                      body.contains("₹", ignoreCase = true) ||
                                      body.contains("spent", ignoreCase = true) ||
                                      body.contains("paid", ignoreCase = true)

                if (isTransactional) {
                    val parsed = SmsParser.parseSms(body)
                    val amount = parsed?.amount ?: 0.0f
                    val merchant = if (parsed != null && parsed.cleanedMerchant != "Unknown Merchant") parsed.cleanedMerchant else ""
                    
                    try {
                        val serviceIntent = Intent(context, OverlayService::class.java).apply {
                            putExtra("extracted_amount", amount)
                            putExtra("extracted_merchant", merchant)
                            putExtra("amount", amount)
                            putExtra("merchant", merchant)
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
