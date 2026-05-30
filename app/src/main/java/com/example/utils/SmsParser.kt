package com.example.utils

import java.util.Locale

data class ParsedTransaction(
    val amount: Float,
    val rawMerchant: String,
    val cleanedMerchant: String
)

object SmsParser {

    fun cleanMerchant(raw: String): String {
        var name = raw.trim()

        val lowerName = name.lowercase()
        if (lowerName.contains(" from ")) {
            name = name.substring(0, lowerName.indexOf(" from "))
        } else if (lowerName.contains(" via ")) {
            name = name.substring(0, lowerName.indexOf(" via "))
        } else if (lowerName.contains(" using ")) {
            name = name.substring(0, lowerName.indexOf(" using "))
        } else if (lowerName.contains(" on ")) {
            name = name.substring(0, lowerName.indexOf(" on "))
        }

        // If it starts with "to " or "at ", strip it
        if (name.lowercase().startsWith("to ")) {
            name = name.substring(3)
        } else if (name.lowercase().startsWith("at ")) {
            name = name.substring(3)
        }

        // If there are slashes, like UPI/.../Name, check if the last segment contains the merchant name
        if (name.contains("/")) {
            val lastPart = name.substringAfterLast("/").trim()
            if (lastPart.isNotEmpty() && !lastPart.matches(Regex("\\d+"))) {
                name = lastPart
            } else {
                // Check other segments
                val parts = name.split("/")
                for (part in parts.reversed()) {
                    val p = part.trim()
                    if (p.isNotEmpty() && !p.matches(Regex("\\d+")) && !p.lowercase().contains("upi")) {
                        name = p
                        break
                    }
                }
            }
        }

        // Strip UPI handles like @paytm, @ybl, @okhdfcbank
        if (name.contains("@")) {
            val firstPart = name.substringBefore("@").trim()
            // If first part is just digits (like a mobile number), use the part after @ or if there's text after slashes
            if (!firstPart.matches(Regex("\\d+")) && firstPart.isNotEmpty()) {
                name = firstPart
            } else {
                val secondPart = name.substringAfter("@").trim()
                // Take up to first spacer/slash
                name = secondPart.split(Regex("[/\\s]"))[0]
            }
        }

        // Strip phrase-based fluff like "RefNo...", "Txn ID...", etc.
        name = name.replace(Regex("(?i)\\bref\\s*(?:no)?\\.?\\s*\\w+"), "")
        name = name.replace(Regex("(?i)\\btxn\\s*(?:id)?\\.?\\s*\\w+"), "")
        name = name.replace(Regex("(?i)\\bupi\\s*(?:id)?\\.?\\s*\\w+"), "")
        name = name.replace(Regex("(?i)\\bdebited\\b"), "")
        name = name.replace(Regex("(?i)\\bcredited\\b"), "")

        // Clean double spaces and punctuation at the ends
        name = name.trim().replace(Regex("\\s+"), " ")
        name = name.trim { it <= ' ' || it == ',' || it == '.' || it == '-' || it == '/' || it == ':' }

        return name.ifEmpty { "Unknown Merchant" }
    }

    fun parseSms(message: String): ParsedTransaction? {
        if (message.isBlank()) return null

        val lowerMessage = message.lowercase()
        // Check if there are any transactional keywords
        val keywords = listOf("debited", "spent", "paid", "payment of", "sent", "transaction", "alert", "pymt", "credited", "transfer")
        val hasKeyword = keywords.any { lowerMessage.contains(it) } || message.contains("₹") || message.contains("Rs") || message.contains("Rs.")
        if (!hasKeyword) return null

        // 1. Extract Amount
        val amountRegexes = listOf(
            Regex("(?i)(?:INR|Rs\\.?|₹)\\s*([0-9,]+\\.[0-9]{1,2})"),
            Regex("(?i)(?:INR|Rs\\.?|₹)\\s*([0-9,]+)"),
            Regex("(?i)(?:debited|spent|paid|payment of|pymt of|sent)\\s+(?:by|of)?\\s*(?:INR|Rs\\.?|₹)?\\s*([0-9,]+\\.[0-9]{1,2})"),
            Regex("(?i)(?:debited|spent|paid|payment of|pymt of|sent)\\s+(?:by|of)?\\s*(?:INR|Rs\\.?|₹)?\\s*([0-9,]+)"),
            Regex("\\b([0-9]+\\.[0-9]{2})\\b")
        )

        var amount = 0.0f
        for (regex in amountRegexes) {
            val match = regex.find(message)
            if (match != null) {
                val cleanVal = match.groupValues[1].replace(",", "")
                val parsed = cleanVal.toFloatOrNull()
                if (parsed != null && parsed > 0.0f) {
                    amount = parsed
                    break
                }
            }
        }

        // 2. Extract Merchant
        var merchant = ""

        // Try UPI slash patterns first: e.g. "UPI/9876543210@paytm/Arun Dabha"
        if (message.contains("/")) {
            val parts = message.split("/")
            if (parts.size > 1) {
                val lastPart = parts.last().trim()
                // Ensure it's not purely numeric digit fluff
                if (lastPart.isNotEmpty() && !lastPart.matches(Regex("\\d+")) && !lastPart.lowercase().contains("upi")) {
                    merchant = lastPart
                }
            }
        }

        if (merchant.isEmpty()) {
            val patternPhrases = listOf(
                Regex("(?i)\\bto\\s+vendor\\s+([^\\s,.;]+(?:\\s+[^\\s,.;]+){0,3})"),
                Regex("(?i)@\\s*UPI:?\\s*([^\\s,.;]+(?:\\s+[^\\s,.;]+){0,3})"),
                Regex("(?i)@\\s*([^\\s,.;]+(?:\\s+[^\\s,.;]+){0,3})"),
                Regex("(?i)\\bto\\s+([^\\s,.;]+(?:\\s+[^\\s,.;]+){0,3})"),
                Regex("(?i)\\bat\\s+([^\\s,.;]+(?:\\s+[^\\s,.;]+){0,3})"),
                Regex("(?i)\\bpaying\\s+([^\\s,.;]+(?:\\s+[^\\s,.;]+){0,3})")
            )

            for (regex in patternPhrases) {
                val match = regex.find(message)
                if (match != null) {
                    val candidate = match.groupValues[1].trim()
                    if (candidate.isNotEmpty() && !candidate.lowercase().contains("acct") && !candidate.lowercase().contains("bank")) {
                        merchant = candidate
                        break
                    }
                }
            }
        }

        if (merchant.isEmpty()) {
            merchant = "Unknown Merchant"
        }

        val cleaned = cleanMerchant(merchant)

        return ParsedTransaction(
            amount = amount,
            rawMerchant = merchant,
            cleanedMerchant = cleaned
        )
    }

    fun mapItemToCategory(item: String): String {
        val text = item.lowercase(Locale.ROOT)
        return when {
            // Food & Drinks: tea, coffee, samosa, lunch, dinner, maggie, chai, snacks, cafe (+ original extensions)
            text.contains("tea") || text.contains("coffee") || text.contains("samosa") || 
            text.contains("lunch") || text.contains("dinner") || text.contains("maggie") || 
            text.contains("maggi") || text.contains("chai") || text.contains("snacks") || 
            text.contains("snack") || text.contains("cafe") || text.contains("breakfast") || 
            text.contains("pizza") || text.contains("burger") || text.contains("food") || 
            text.contains("drinks") || text.contains("restaurant") || text.contains("hotel") ||
            text.contains("roll") || text.contains("sweets") || text.contains("biryani") || 
            text.contains("starbucks") || text.contains("mcd") -> "Food & Drinks"

            // Travel & Transport: uber, ola, auto, metro, petrol, fuel, rapido, train, cab (+ original extensions)
            text.contains("uber") || text.contains("ola") || text.contains("auto") || 
            text.contains("metro") || text.contains("petrol") || text.contains("fuel") || 
            text.contains("rapido") || text.contains("train") || text.contains("cab") || 
            text.contains("diesel") || text.contains("taxi") || text.contains("bus") || 
            text.contains("ticket") || text.contains("flight") -> "Travel & Transport"

            // Groceries & Shopping: dmart, blinkit, instamart, milk, vegetables, clothes, amazon (+ original extensions)
            text.contains("dmart") || text.contains("d-mart") || text.contains("blinkit") || 
            text.contains("instamart") || text.contains("milk") || text.contains("vegetables") || 
            text.contains("clothes") || text.contains("amazon") || text.contains("grocery") || 
            text.contains("groceries") || text.contains("shopping") || text.contains("mart") || 
            text.contains("supermarket") || text.contains("zepto") || text.contains("bigbasket") || 
            text.contains("bread") || text.contains("butter") || text.contains("shirt") || 
            text.contains("pant") || text.contains("jeans") || text.contains("shoes") || 
            text.contains("pantaloons") || text.contains("dress") || text.contains("flipkart") || 
            text.contains("myntra") || text.contains("mall") || text.contains("gift") || 
            text.contains("bag") || text.contains("watch") -> "Groceries & Shopping"

            // Bills & Utilities
            text.contains("recharge") || text.contains("electricity") || text.contains("water") || 
            text.contains("wifi") || text.contains("internet") || text.contains("netflix") || 
            text.contains("spotify") || text.contains("youtube") || text.contains("rent") || 
            text.contains("bill") || text.contains("subscription") || text.contains("postpaid") ||
            text.contains("gas") -> "Bills & Utilities"

            // Medical & Healthcare
            text.contains("medicine") || text.contains("hospital") || text.contains("doctor") || 
            text.contains("pharmacy") || text.contains("clinic") || text.contains("medical") || 
            text.contains("tablet") || text.contains("health") || text.contains("syrup") -> "Medical & Healthcare"

            // Defaults to Other
            else -> "Other"
        }
    }
}
