package com.basic.spendai.sms

import com.basic.spendai.data.Category

/** Guesses a category from merchant and SMS text using keyword rules. */
object CategoryGuesser {
    // Checked in order, so "uber eats" lands in Food before "uber" matches Transport.
    private val keywords: List<Pair<Category, List<String>>> = listOf(
        Category.Food to listOf(
            "swiggy", "zomato", "uber eats", "eatsure", "restaurant", "cafe", "coffee", "starbucks", "dominos",
            "domino's", "pizza", "mcdonald", "kfc", "burger", "subway", "bakery", "food", "dine", "dhaba",
            "chai", "zepto cafe", "blinkit", "bigbasket", "instamart", "grocer", "grocery",
        ),
        Category.Transport to listOf(
            "uber", "ola", "rapido", "irctc", "railway", "metro", "fuel", "petrol", "diesel", "hpcl", "bpcl",
            "indian oil", "iocl", "shell", "fastag", "parking", "redbus", "indigo", "air india", "vistara",
            "spicejet", "akasa", "makemytrip", "goibibo", "cab", "taxi", "toll",
        ),
        Category.Shopping to listOf(
            "amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "dmart", "reliance", "trends",
            "lifestyle", "westside", "decathlon", "ikea", "croma", "tata cliq", "snapdeal", "mall", "store",
            "mart", "retail", "fashion",
        ),
        Category.Bills to listOf(
            "electricity", "recharge", "airtel", "jio", "vodafone", "vi ", "bsnl", "broadband", "act fibernet",
            "gas", "water", "bill", "billpay", "insurance", "lic", "emi", "loan", "rent", "postpaid", "dth",
            "tata play", "bescom", "tneb", "mseb", "adani", "maintenance", "society",
        ),
        Category.Entertainment to listOf(
            "netflix", "spotify", "prime video", "hotstar", "disney", "jiocinema", "zee5", "sonyliv",
            "youtube", "bookmyshow", "pvr", "inox", "cinema", "movie", "steam", "playstation", "gaming",
        ),
        Category.Health to listOf(
            "pharmacy", "pharma", "medical", "medic", "apollo", "1mg", "netmeds", "pharmeasy", "hospital",
            "clinic", "diagnostic", "lab", "healthkart", "cult.fit", "cultfit", "gym", "dental", "doctor",
        ),
    )

    private val rules: List<Pair<Category, Regex>> = keywords.map { (category, words) ->
        category to Regex(words.joinToString("|", "\\b(", ")\\b") { Regex.escape(it.trim()) }, RegexOption.IGNORE_CASE)
    }

    // Words people type in quick notes ("450 auto", "samosa") but that would misfire on bank SMS
    // ("auto-debit"), so they're only used for typed or spoken entries.
    private val casualKeywords: List<Pair<Category, List<String>>> = listOf(
        Category.Food to listOf(
            "samosa", "lunch", "dinner", "breakfast", "snacks", "snack", "tea", "juice", "biryani", "thali",
            "dosa", "idli", "vada pav", "momos", "maggi", "ice cream", "sweets", "fruits", "vegetables", "milk", "groceries",
        ),
        Category.Transport to listOf("auto", "rickshaw", "bus", "train", "flight", "ride", "bike", "cab fare"),
        Category.Shopping to listOf("clothes", "shoes", "shirt", "gift"),
        Category.Bills to listOf("wifi", "phone bill", "mobile recharge"),
        Category.Entertainment to listOf("movie", "concert", "game", "outing"),
        Category.Health to listOf("medicine", "medicines", "tablets", "checkup", "doctor fee"),
    )
    private val casualRules = casualKeywords.map { (category, words) ->
        category to Regex(words.joinToString("|", "\\b(", ")\\b") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
    }

    /** For typed or spoken notes: casual words first, then the regular merchant keywords. */
    fun guessCasual(text: String): Category =
        casualRules.firstOrNull { (_, regex) -> regex.containsMatchIn(text) }?.first ?: guess(text, "")

    /** Key used to remember the user's choice for a merchant. */
    fun merchantKey(merchant: String): String = merchant.trim().lowercase()

    /** [learned] is the category the user chose for this merchant before, if any; it always wins. */
    fun guess(merchant: String?, body: String, learned: Category? = null): Category {
        if (learned != null) return learned
        merchant?.let { name -> match(name)?.let { return it } }
        return match(body) ?: Category.Other
    }

    private fun match(text: String): Category? = rules.firstOrNull { (_, regex) -> regex.containsMatchIn(text) }?.first
}
