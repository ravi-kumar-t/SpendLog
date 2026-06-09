package com.example.utils

import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ParsedTransaction(
    val amount: Float,
    val rawMerchant: String,
    val cleanedMerchant: String,
    val type: String = "TYPE_EXPENSE"
)

object SmsParser {

    val CAMPUS_VOCABULARY = mapOf(
        "Lassi" to "Food & Drinks",
        "Milk" to "Food & Drinks",
        "Ice cream" to "Food & Drinks",
        "Icecream" to "Food & Drinks",
        "Juice" to "Food & Drinks",
        "Food" to "Food & Drinks",
        "Fruits" to "Food & Drinks",
        "Fruits Salad" to "Food & Drinks",
        "Water" to "Food & Drinks",
        "Tea" to "Food & Drinks",
        "Coffee" to "Food & Drinks",
        "Maggie" to "Food & Drinks",
        "Biscuit" to "Food & Drinks",
        "Chips" to "Food & Drinks",
        "Lays" to "Food & Drinks",
        "Cool Drinks" to "Food & Drinks",
        "Pepsi" to "Food & Drinks",
        "Thumps Up" to "Food & Drinks",
        "Coke" to "Food & Drinks",
        "Limka" to "Food & Drinks",
        "Fanta" to "Food & Drinks",
        "Chicken" to "Food & Drinks",
        "Mandi" to "Food & Drinks",
        "Noodles" to "Food & Drinks",
        "Roti" to "Food & Drinks",
        "Rice" to "Food & Drinks",
        "Panneer" to "Food & Drinks",
        "Tiffins" to "Food & Drinks",
        "Idly" to "Food & Drinks",
        "Dosa" to "Food & Drinks",
        "Butter Milk" to "Food & Drinks",
        "Bubble Gum" to "Food & Drinks",
        "Biriyani" to "Food & Drinks",
        "Mango" to "Food & Drinks",
        "Watermelon" to "Food & Drinks",
        "Muskmelon" to "Food & Drinks",
        "Litchi" to "Food & Drinks",
        "Jamun" to "Food & Drinks",
        "Papaya" to "Food & Drinks",
        "Pineapple" to "Food & Drinks",
        "Coconut" to "Food & Drinks",
        "Plum" to "Food & Drinks",
        "Pear" to "Food & Drinks",
        "Apple" to "Food & Drinks",
        "Pomegranate" to "Food & Drinks",
        "Guava" to "Food & Drinks",
        "Grapes" to "Food & Drinks",
        "Cherry" to "Food & Drinks",
        "Dragon Fruit" to "Food & Drinks",
        "Kiwi" to "Food & Drinks",
        "Orange" to "Food & Drinks",
        "Sweet Lime" to "Food & Drinks",
        "Jackfruit" to "Food & Drinks",
        "Soap" to "Groceries & Shopping",
        "Shampoo" to "Groceries & Shopping",
        "Toothpaste" to "Groceries & Shopping",
        "Brush" to "Groceries & Shopping",
        "Body Wash" to "Groceries & Shopping",
        "Scrub" to "Groceries & Shopping",
        "Towel" to "Groceries & Shopping",
        "Laundry detergent" to "Groceries & Shopping",
        "Surf" to "Groceries & Shopping",
        "Bucket" to "Groceries & Shopping",
        "Mug" to "Groceries & Shopping",
        "Vegetables" to "Groceries & Shopping",
        "Pens" to "Stationaries & Education",
        "Notebooks" to "Stationaries & Education",
        "Books" to "Stationaries & Education",
        "Pen" to "Stationaries & Education",
        "Notebook" to "Stationaries & Education",
        "Book" to "Stationaries & Education",
        "Calculators" to "Stationaries & Education",
        "Project materials" to "Stationaries & Education",
        "Assignment Printouts" to "Stationaries & Education",
        "Eraser" to "Stationaries & Education",
        "Certification exam" to "Stationaries & Education",
        "Xerox" to "Stationaries & Education",
        "Auto" to "Travel & Transport",
        "Ekart" to "Travel & Transport",
        "Rapido" to "Travel & Transport",
        "Uber" to "Travel & Transport",
        "Bus" to "Travel & Transport",
        "Train" to "Travel & Transport",
        "Petrol" to "Travel & Transport",
        "Parking fees" to "Travel & Transport",
        "Mobile recharge" to "Bills & Utilities",
        "Recharge" to "Bills & Utilities",
        "Rent" to "Bills & Utilities",
        "Medicines" to "Medical & Healthcare",
        "Clinic visits" to "Medical & Healthcare",
        "Doctor fees" to "Medical & Healthcare",
        "Tablets" to "Medical & Healthcare",
        "Sent to friend" to "Personal Transfers",
        "Split" to "Personal Transfers",
        "Borrowed money returned" to "Personal Transfers",
        "Haircut" to "Other",
        "Movie" to "Other"
    )

    fun getSingularForm(input: String): String? {
        val lower = input.lowercase(Locale.ROOT)
        // Exempt explicit master entries ending in 's' (such as "Lays", "Chips", "Tiffins")
        val exemptKeys = CAMPUS_VOCABULARY.keys.map { it.lowercase(Locale.ROOT) }.filter { it.endsWith("s") }
        if (exemptKeys.contains(lower)) {
            return null
        }
        
        if (lower.endsWith("es") && lower.length > 2) {
            return input.substring(0, input.length - 2)
        } else if (lower.endsWith("s") && lower.length > 1) {
            return input.substring(0, input.length - 1)
        }
        return null
    }

    fun matchVocabulary(text: String): Map.Entry<String, String>? {
        val cleanedText = text.trim()
        if (cleanedText.isEmpty()) return null
        
        val lowerText = cleanedText.lowercase(Locale.ROOT)
        val spaceAgnosticInput = lowerText.replace(" ", "")
        
        // Exact match with space-agnostic fallback
        val exactMatch = CAMPUS_VOCABULARY.entries.firstOrNull { 
            it.key.lowercase(Locale.ROOT) == lowerText || 
            it.key.lowercase(Locale.ROOT).replace(" ", "") == spaceAgnosticInput
        }
        if (exactMatch != null) return exactMatch
        
        // Prefix match with space-agnostic fallback
        val prefixMatch = CAMPUS_VOCABULARY.entries.firstOrNull { 
            it.key.lowercase(Locale.ROOT).startsWith(lowerText) || 
            it.key.lowercase(Locale.ROOT).replace(" ", "").startsWith(spaceAgnosticInput)
        }
        if (prefixMatch != null) return prefixMatch
        
        // Try singular clone checks
        val singularForm = getSingularForm(lowerText)
        if (singularForm != null) {
            val spaceAgnosticSingular = singularForm.replace(" ", "")
            val exactSingularMatch = CAMPUS_VOCABULARY.entries.firstOrNull { 
                it.key.lowercase(Locale.ROOT) == singularForm || 
                it.key.lowercase(Locale.ROOT).replace(" ", "") == spaceAgnosticSingular
            }
            if (exactSingularMatch != null) return exactSingularMatch
            
            val prefixSingularMatch = CAMPUS_VOCABULARY.entries.firstOrNull { 
                it.key.lowercase(Locale.ROOT).startsWith(singularForm) || 
                it.key.lowercase(Locale.ROOT).replace(" ", "").startsWith(spaceAgnosticSingular)
            }
            if (prefixSingularMatch != null) return prefixSingularMatch
        }
        
        return null
    }

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

        val cleaned = name.ifEmpty { "Unknown Merchant" }
        return resolveAlias(cleaned)
    }

    fun resolveAlias(merchant: String): String {
        val upper = merchant.uppercase(Locale.getDefault())
        return when {
            upper.contains("AURIGANS") || upper.contains("LE BROC") || upper.contains("LEEBROC") -> "Le Broc"
            upper.contains("ARUN") -> "Arun Dabha"
            else -> merchant
        }
    }

    fun parseSms(message: String): ParsedTransaction? {
        if (message.isBlank()) return null

        val lowerMessage = message.lowercase(Locale.ROOT)

        // 0. Hard-Block Promotional / Recharge Reminders
        val blockedKeywords = listOf(
            "recharge", "due by", "will be deducted", "reminder", "prepaid", 
            "validity", "subscribe", "plan", "eligible to claim", "unclaimed"
        )
        if (blockedKeywords.any { lowerMessage.contains(it) }) {
            return null
        }

        // 1. Check for Strict Credit Transaction (Expected HDFC credit format and general credit patterns)
        val hdfcCreditRegex = Regex("Credit Alert! Rs\\.([0-9,.]+) credited to HDFC Bank A/c", RegexOption.IGNORE_CASE)
        val hdfcCreditMatch = hdfcCreditRegex.find(message)
        if (hdfcCreditMatch != null) {
            val amountStr = hdfcCreditMatch.groupValues[1].replace(",", "")
            val parsedAmount = amountStr.toFloatOrNull() ?: 0.0f
            return ParsedTransaction(
                amount = parsedAmount,
                rawMerchant = "HDFC Bank",
                cleanedMerchant = "HDFC Bank",
                type = "TYPE_INCOME"
            )
        }

        val isCreditMatch = lowerMessage.contains("credit alert!") || 
                            lowerMessage.contains("credited to") || 
                            lowerMessage.contains("received") || 
                            lowerMessage.contains("deposited")

        // 2. Check for Strict Debit Transaction (Expected HDFC debit format and general debit patterns)
        val hdfcDebitRegex = Regex("Sent Rs\\.([0-9,.]+) From HDFC Bank A/C \\*7925 To ([A-Za-z0-9 ]+)", RegexOption.IGNORE_CASE)
        val hdfcDebitMatch = hdfcDebitRegex.find(message)
        if (hdfcDebitMatch != null) {
            val amountStr = hdfcDebitMatch.groupValues[1].replace(",", "")
            val parsedAmount = amountStr.toFloatOrNull() ?: 0.0f
            val rawMerchant = hdfcDebitMatch.groupValues[2].trim()
            val cleanedMerchant = cleanMerchant(rawMerchant)
            return ParsedTransaction(
                amount = parsedAmount,
                rawMerchant = rawMerchant,
                cleanedMerchant = cleanedMerchant,
                type = "TYPE_EXPENSE"
            )
        }

        val isDebitMatch = message.startsWith("Sent Rs.", ignoreCase = true) ||
                           lowerMessage.contains("debited by") ||
                           lowerMessage.contains("paid to") ||
                           lowerMessage.contains("sent to") ||
                           lowerMessage.contains("spent") ||
                           lowerMessage.contains("pymt of")

        // Classify Type
        val txType = if (isCreditMatch && !isDebitMatch) {
            "TYPE_INCOME"
        } else {
            "TYPE_EXPENSE"
        }

        // Return early if no transactional trigger keywords are present
        if (!isCreditMatch && !isDebitMatch) {
            val keywords = listOf("debited", "spent", "paid", "payment of", "sent", "transaction", "alert", "pymt", "credited", "transfer", "received", "deposited")
            val hasKeyword = keywords.any { lowerMessage.contains(it) } || message.contains("₹") || message.contains("Rs") || message.contains("Rs.")
            if (!hasKeyword) return null
        }

        // 3. Extract Amount using standard regexes
        val amountRegexes = listOf(
            Regex("(?i)(?:INR|Rs\\.?|₹)\\s*([0-9,]+\\.[0-9]{1,2})"),
            Regex("(?i)(?:INR|Rs\\.?|₹)\\s*([0-9,]+)"),
            Regex("(?i)(?:debited|spent|paid|payment of|pymt of|sent|credited|received|deposited)\\s+(?:by|of|to|from)?\\s*(?:INR|Rs\\.?|₹)?\\s*([0-9,]+\\.[0-9]{1,2})"),
            Regex("(?i)(?:debited|spent|paid|payment of|pymt of|sent|credited|received|deposited)\\s+(?:by|of|to|from)?\\s*(?:INR|Rs\\.?|₹)?\\s*([0-9,]+)"),
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

        // 4. Extract Merchant using standard rules
        var merchant = ""

        // Try UPI slash patterns first: e.g. "UPI/9876543210@paytm/Arun Dabha"
        if (message.contains("/")) {
            val parts = message.split("/")
            if (parts.size > 1) {
                val lastPart = parts.last().trim()
                if (lastPart.isNotEmpty() && !lastPart.matches(Regex("\\d+")) && !lastPart.lowercase().contains("upi")) {
                    merchant = lastPart
                }
            }
        }

        if (merchant.isEmpty()) {
            val patternPhrases = mutableListOf<Regex>()
            if (txType == "TYPE_INCOME") {
                patternPhrases.add(Regex("(?i)\\bfrom\\s+([^\\s,.;]+(?:\\s+[^\\s,.;]+){0,3})"))
                patternPhrases.add(Regex("(?i)\\bby\\s+([^\\s,.;]+(?:\\s+[^\\s,.;]+){0,3})"))
            }
            patternPhrases.addAll(listOf(
                Regex("(?i)\\bto\\s+vendor\\s+([^\\s,.;]+(?:\\s+[^\\s,.;]+){0,3})"),
                Regex("(?i)@\\s*UPI:?\\s*([^\\s,.;]+(?:\\s+[^\\s,.;]+){0,3})"),
                Regex("(?i)@\\s*([^\\s,.;]+(?:\\s+[^\\s,.;]+){0,3})"),
                Regex("(?i)\\bto\\s+([^\\s,.;]+(?:\\s+[^\\s,.;]+){0,3})"),
                Regex("(?i)\\bat\\s+([^\\s,.;]+(?:\\s+[^\\s,.;]+){0,3})"),
                Regex("(?i)\\bpaying\\s+([^\\s,.;]+(?:\\s+[^\\s,.;]+){0,3})")
            ))

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
            merchant = if (txType == "TYPE_INCOME") "Bank/Deposit" else "Unknown Merchant"
        }

        val cleaned = cleanMerchant(merchant)

        return ParsedTransaction(
            amount = amount,
            rawMerchant = merchant,
            cleanedMerchant = cleaned,
            type = txType
        )
    }

    fun isCommonIndianName(text: String): Boolean {
        val nameLower = text.trim().lowercase()
        val commonNames = setOf(
            "pavan", "amit", "rahul", "pooja", "rajesh", "suresh", "neha", "arjun", "karan", "vijay", 
            "anjali", "priya", "deepak", "anil", "sunil", "ajay", "sanjay", "rohit", "vikas", "manoj", 
            "abhishek", "ravi", "sharma", "singh", "kumar", "verma", "patel", "prakash", "gandhi",
            "kiran", "tanmay", "aniket", "pranav", "nikhil", "aditya", "gaurav", "saurabh", "simran",
            "sneha", "sapna", "shreya", "divya", "rita", "gita", "harish", "mahesh", "ramesh", "dinesh"
        )
        return commonNames.contains(nameLower)
    }

    fun extractRecipientName(description: String, defaultMerchant: String): String {
        val text = description.trim()
        val textLower = text.lowercase()
        val prefixes = listOf("send to", "paid to", "transfer to", "sent to")
        for (prefix in prefixes) {
            if (textLower.startsWith(prefix)) {
                val candidate = text.substring(prefix.length).trim()
                if (candidate.isNotEmpty()) {
                    return capitalizeName(candidate)
                }
            }
        }
        if (textLower.startsWith("to ")) {
            val candidate = text.substring(3).trim()
            if (candidate.isNotEmpty()) {
                return capitalizeName(candidate)
            }
        }
        if (isCommonIndianName(text)) {
            return capitalizeName(text)
        }
        val words = text.split(" ")
        if (words.size <= 2) {
            val nameWord = words.find { isCommonIndianName(it) }
            if (nameWord != null) {
                return capitalizeName(nameWord)
            }
        }
        return capitalizeName(defaultMerchant)
    }

    private fun capitalizeName(name: String): String {
        val trimmed = name.trim().replace(Regex("\\s+"), " ")
        if (trimmed.isEmpty()) return "Unknown"
        var cleanName = trimmed
        val lower = cleanName.lowercase()
        val fluffTrailing = listOf(" via u", " using", " from")
        for (fluff in fluffTrailing) {
            if (lower.contains(fluff)) {
                cleanName = cleanName.substring(0, lower.indexOf(fluff)).trim()
            }
        }
        return cleanName.split(" ").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }

    fun mapItemToCategory(item: String): String {
        val text = item.lowercase(Locale.ROOT).trim()
        val vMatch = matchVocabulary(text)
        if (vMatch != null) return vMatch.value
 
        val textWords = text.split(" ")
        val containsSenderPrefix = text.contains("send to") || text.contains("paid to") || 
                text.contains("transfer to") || text.contains("sent to") || 
                text.contains("transfer of") || text.startsWith("to ") || text.startsWith("transfer ")
        
        val isNameMatch = isCommonIndianName(text) || (textWords.isNotEmpty() && textWords.size <= 2 && textWords.any { isCommonIndianName(it) })

        return when {
            containsSenderPrefix || isNameMatch -> "Personal Transfers"

            // Food & Drinks: milk, lassi, chicken, tea, coffee, samosa, lunch, dinner, maggie, chai, snacks, cafe (+ original extensions)
            text.contains("milk") || text.contains("lassi") || text.contains("chicken") ||
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

            // Groceries & Shopping: dmart, blinkit, instamart, book, vegetables, clothes, amazon (+ original extensions)
            text.contains("book") || text.contains("dmart") || text.contains("d-mart") || text.contains("blinkit") || 
            text.contains("instamart") || text.contains("vegetables") || 
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

class OnDeviceLLM {
    suspend fun generateContent(prompt: String): String {
        // Simulate local CPU/NPU processing latency matching "target window under 200ms"
        delay(180)
        
        val promptClean = prompt.lowercase(Locale.ROOT)
        
        // 1. Intent Verification Prompt
        if (promptClean.contains("intent") || promptClean.contains("money actually left")) {
            // Checks if the SMS text contains keywords suggesting actual debit intent
            val hasDebitKeywords = promptClean.contains("sent") || 
                                   promptClean.contains("debited") || 
                                   promptClean.contains("paid") || 
                                   promptClean.contains("spent") || 
                                   promptClean.contains("pymt") ||
                                   promptClean.contains("tx") ||
                                   promptClean.contains("₹") ||
                                   promptClean.contains("rs")
            
            // To be ultra clean, also make sure it is not promotional recharge spam
            val isPromo = promptClean.contains("recharge") || 
                          promptClean.contains("due") || 
                          promptClean.contains("reminder") || 
                          promptClean.contains("validity")
            
            val actuallyLeft = hasDebitKeywords && !isPromo
            return actuallyLeft.toString()
        }
        
        // 2. Auto-Categorization Prompt
        if (promptClean.contains("category") || promptClean.contains("auto-categorization")) {
            var payload = when {
                promptClean.contains("of:") -> promptClean.substringAfter("of:").trim()
                promptClean.contains("sms:") -> promptClean.substringAfter("sms:").trim()
                promptClean.contains("of sms:") -> promptClean.substringAfter("of sms:").trim()
                promptClean.contains("of ") -> promptClean.substringAfter("of ").trim()
                else -> promptClean
            }
            if (payload.contains("(system instructions:")) {
                payload = payload.substringBefore("(system instructions:").trim()
            } else if (payload.contains("system instructions:")) {
                payload = payload.substringBefore("system instructions:").trim()
            }
            return predictCategorySemantically(payload)
        }
        
        return "Other"
    }

    private fun predictCategorySemantically(prompt: String): String {
        return when {
            prompt.contains("milk") || prompt.contains("lassi") || prompt.contains("chicken") ||
            prompt.contains("chai") || prompt.contains("tea") || prompt.contains("coffee") || 
            prompt.contains("juice") || prompt.contains("cafe") || prompt.contains("food") || 
            prompt.contains("restaurant") || prompt.contains("dining") || prompt.contains("samosa") || 
            prompt.contains("mcd") || prompt.contains("starbucks") || prompt.contains("biryani") ||
            prompt.contains("hotel") || prompt.contains("maggi") ||
            prompt.contains("banana") || prompt.contains("apple") || prompt.contains("orange") ||
            prompt.contains("mango") || prompt.contains("fruit") || prompt.contains("fruits") ||
            prompt.contains("vegetable") || prompt.contains("vegetables") || prompt.contains("tomato") ||
            prompt.contains("potato") || prompt.contains("onion") || prompt.contains("snack") ||
            prompt.contains("snacks") || prompt.contains("meat") || prompt.contains("meats") ||
            prompt.contains("mutton") || prompt.contains("fish") || prompt.contains("egg") ||
            prompt.contains("eggs") || prompt.contains("rice") || prompt.contains("wheat") ||
            prompt.contains("flour") || prompt.contains("curd") || prompt.contains("butter") ||
            prompt.contains("cheese") || prompt.contains("ingredient") || prompt.contains("ingredients") -> "Food & Drinks"
            
            prompt.contains("t-shirt") || prompt.contains("shirt") || prompt.contains("clothing") ||
            prompt.contains("apparel") || prompt.contains("jeans") || prompt.contains("dress") ||
            prompt.contains("footwear") || prompt.contains("bags") || prompt.contains("bag") ||
            prompt.contains("shoe") || prompt.contains("shoes") -> "Apparel & Clothing"

            prompt.contains("book") ||
            prompt.contains("dmart") || prompt.contains("blinkit") || prompt.contains("zepto") || 
            prompt.contains("instamart") || prompt.contains("grocery") || prompt.contains("shopping") || 
            prompt.contains("amazon") || prompt.contains("flipkart") || prompt.contains("clothes") ||
            prompt.contains("supermarket") || prompt.contains("mart") || prompt.contains("mall") -> "Groceries & Shopping"
            
            prompt.contains("uber") || prompt.contains("ola") || prompt.contains("auto") || 
            prompt.contains("metropolitan") || prompt.contains("fuel") || prompt.contains("petrol") || 
            prompt.contains("rapido") || prompt.contains("cab") || prompt.contains("transport") ||
            prompt.contains("train") || prompt.contains("metro") || prompt.contains("diesel") -> "Travel & Transport"
            
            prompt.contains("recharge") || prompt.contains("wifi") || prompt.contains("bill") || 
            prompt.contains("electricity") || prompt.contains("netflix") || prompt.contains("spotify") || 
            prompt.contains("rent") || prompt.contains("subscription") || prompt.contains("internet") -> "Bills & Utilities"
            
            prompt.contains("medicine") || prompt.contains("pharmacy") || prompt.contains("doctor") || 
            prompt.contains("hospital") || prompt.contains("healthcare") || prompt.contains("clinic") ||
            prompt.contains("medical") -> "Medical & Healthcare"
            
            prompt.contains("send to") || prompt.contains("paid to") || prompt.contains("transfer") || 
            prompt.contains("pavan") || prompt.contains("amit") || prompt.contains("rahul") ||
            prompt.contains("remittance") || prompt.contains("upi to") -> "Personal Transfers"

            // Dynamic Category Creation (generate brand-new, highly relevant 2-word names on-the-fly)
            prompt.contains("haircut") || prompt.contains("salon") || prompt.contains("spa") || 
            prompt.contains("shampoo") || prompt.contains("trimmer") || prompt.contains("makeup") || 
            prompt.contains("barber") -> "Personal Care"
            
            prompt.contains("laptop") || prompt.contains("iphone") || prompt.contains("phone") || 
            prompt.contains("headphone") || prompt.contains("mouse") || prompt.contains("keyboard") || 
            prompt.contains("monitor") || prompt.contains("charger") || prompt.contains("electronics") || 
            prompt.contains("tv") || prompt.contains("gadget") || prompt.contains("appliance") ||
            prompt.contains("electronic") -> "Electronics"
            
            prompt.contains("gym") || prompt.contains("football") || prompt.contains("cricket") || 
            prompt.contains("badminton") || prompt.contains("shoes") || prompt.contains("adidas") || 
            prompt.contains("nike") || prompt.contains("fitbit") || prompt.contains("sports") || 
            prompt.contains("workout") -> "Sports & Fitness"
            
            prompt.contains("movie") || prompt.contains("cinema") || prompt.contains("theatre") || 
            prompt.contains("bookmyshow") || prompt.contains("ticket") || prompt.contains("game") || 
            prompt.contains("arcade") || prompt.contains("concert") || prompt.contains("show") -> "Entertainment"
            
            prompt.contains("book") || prompt.contains("course") || prompt.contains("college") || 
            prompt.contains("school") || prompt.contains("udemy") || prompt.contains("tuition") || 
            prompt.contains("stationery") || prompt.contains("exam") || prompt.contains("class") ||
            prompt.contains("pen") || prompt.contains("notebook") || prompt.contains("textbook") ||
            prompt.contains("calculator") || prompt.contains("pencil") -> "Education"
            
            prompt.contains("tool") || prompt.contains("hardware") || prompt.contains("paint") || 
            prompt.contains("screw") || prompt.contains("plumber") || prompt.contains("drill") -> "Hardware & Tools"
            
            prompt.contains("stock") || prompt.contains("mutual fund") || prompt.contains("sip") || 
            prompt.contains("groww") || prompt.contains("zerodha") || prompt.contains("invest") || 
            prompt.contains("gold") -> "Investments"
            
            prompt.contains("gift") || prompt.contains("delivery") || prompt.contains("donation") || 
            prompt.contains("charity") || prompt.contains("tip") || prompt.contains("gifting") -> "Gifts & Donations"
            
            prompt.contains("laundry") || prompt.contains("dry clean") || prompt.contains("washing") -> "Laundry Services"
            
            else -> {
                // If it doesn't match any existing category, dynamically derive dry 2-word title representation
                // from the prompt words to avoid "Other" fallback.
                val cleanWords = prompt.split(Regex("[^a-zA-Z]")).filter { 
                    it.length > 2 && it != "sms" && it != "item" && it != "items" && it != "description" && 
                    !it.equals("shopping", ignoreCase = true) && !it.equals("expenses", ignoreCase = true)
                }
                if (cleanWords.size >= 2) {
                    val w1 = cleanWords[0].replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                    val w2 = cleanWords[1].replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                    "$w1 $w2"
                } else if (cleanWords.size == 1) {
                    val w1 = cleanWords[0].replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                    w1
                } else {
                    "Generic Care"
                }
            }
        }
    }
}
