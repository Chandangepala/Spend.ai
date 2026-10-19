package com.basic.spendai.sms

import com.basic.spendai.data.Category

/** Guesses a category from merchant and SMS text using keyword rules. */
object CategoryGuesser {
    // Checked in order: earlier categories win, so "uber eats" is Dining before "uber" is Transport,
    // and "zepto cafe" is Dining before "zepto" is Groceries.
    private val keywords: List<Pair<Category, List<String>>> = listOf(
        Category.Food to listOf(
            "swiggy", "zomato", "uber eats", "eatsure", "zepto cafe", "restaurant", "cafe", "coffee", "starbucks",
            "dominos", "domino's", "pizza", "mcdonald", "kfc", "burger", "subway", "bakery", "dine", "dhaba",
            "chai", "bar", "pub", "brewery", "food court",
        ),
        Category.Groceries to listOf(
            "blinkit", "zepto", "bigbasket", "instamart", "jiomart", "dmart", "more supermarket", "reliance fresh",
            "spencer", "nature's basket", "grocer", "grocery", "supermarket", "kirana", "dairy", "milk", "fruits",
            "vegetables", "country delight",
        ),
        Category.Travel to listOf(
            "irctc", "railway", "indigo", "air india", "vistara", "spicejet", "akasa", "makemytrip", "goibibo",
            "cleartrip", "ixigo", "yatra", "redbus", "oyo", "airbnb", "hotel", "resort", "booking.com", "agoda",
        ),
        Category.Transport to listOf(
            "uber", "ola", "rapido", "metro", "fuel", "petrol", "diesel", "hpcl", "bpcl", "indian oil", "iocl",
            "shell", "fastag", "parking", "cab", "taxi", "toll", "namma yatri", "blusmart",
        ),
        Category.Home to listOf(
            "rent", "nobroker", "society", "maintenance", "housing", "urban company", "plumber", "electrician",
            "carpenter", "maid", "furniture", "pepperfry",
        ),
        Category.Finance to listOf(
            "emi", "loan", "insurance", "lic", "policybazaar", "premium", "sip", "mutual fund", "zerodha", "groww",
            "upstox", "credit card bill", "cred", "income tax", "bajaj finance",
        ),
        Category.Bills to listOf(
            "electricity", "recharge", "airtel", "jio", "vodafone", "vi ", "bsnl", "broadband", "act fibernet",
            "gas", "water", "bill", "billpay", "postpaid", "dth", "tata play", "bescom", "tneb", "mseb", "adani",
        ),
        Category.Entertainment to listOf(
            "netflix", "spotify", "prime video", "hotstar", "disney", "jiocinema", "zee5", "sonyliv",
            "youtube", "bookmyshow", "pvr", "inox", "cinema", "movie", "steam", "playstation", "gaming",
        ),
        Category.PersonalCare to listOf(
            "salon", "spa", "barber", "haircut", "parlour", "parlor", "grooming", "nykaa", "laundry", "dry clean",
            "cosmetics",
        ),
        Category.Health to listOf(
            "pharmacy", "pharma", "medical", "medic", "apollo", "1mg", "netmeds", "pharmeasy", "hospital",
            "clinic", "diagnostic", "lab", "healthkart", "cult.fit", "cultfit", "gym", "dental", "doctor",
        ),
        Category.Education to listOf(
            "udemy", "coursera", "byju", "unacademy", "upgrad", "school", "college", "university", "tuition",
            "coaching", "books", "kindle", "stationery", "linkedin", "notion", "github",
        ),
        Category.Shopping to listOf(
            "amazon", "flipkart", "myntra", "ajio", "meesho", "reliance digital", "trends", "lifestyle",
            "westside", "decathlon", "ikea", "croma", "tata cliq", "snapdeal", "mall", "store", "mart", "retail",
            "fashion",
        ),
        Category.Gifts to listOf("gift", "donation", "charity", "temple", "ferns n petals", "igp", "pet"),
    )

    private val rules: List<Pair<Category, Regex>> = keywords.map { (category, words) ->
        category to Regex(words.joinToString("|", "\\b(", ")\\b") { Regex.escape(it.trim()) }, RegexOption.IGNORE_CASE)
    }

    // Words people type in quick notes ("450 auto", "samosa") but that would misfire on bank SMS
    // ("auto-debit"), so they're only used for typed or spoken entries.
    private val casualKeywords: List<Pair<Category, List<String>>> = listOf(
        Category.Food to listOf(
            "samosa", "lunch", "dinner", "breakfast", "snacks", "snack", "tea", "juice", "biryani", "thali",
            "dosa", "idli", "vada pav", "momos", "maggi", "ice cream", "sweets", "drinks", "beer", "party",
        ),
        Category.Groceries to listOf("groceries", "veggies", "bread", "eggs", "atta", "rice", "dal", "oil", "sabzi"),
        Category.Transport to listOf("auto", "rickshaw", "bus", "ride", "bike", "cab fare"),
        Category.Travel to listOf("train", "flight", "trip", "stay", "hostel", "vacation"),
        Category.Shopping to listOf("clothes", "shoes", "shirt", "jeans", "phone", "headphones"),
        Category.Bills to listOf("wifi", "phone bill", "mobile recharge"),
        Category.Home to listOf("house rent", "repairs", "cook", "cleaning"),
        Category.Entertainment to listOf("concert", "game", "outing"),
        Category.Health to listOf("medicine", "medicines", "tablets", "checkup", "doctor fee", "physio"),
        Category.PersonalCare to listOf("hair", "shave", "facial", "manicure", "pedicure", "skincare"),
        Category.Education to listOf("course", "book", "notebook", "pens", "exam fee", "fees"),
        Category.Gifts to listOf("birthday", "wedding", "sent to mom", "sent to dad", "family"),
    )
    private val casualRules = casualKeywords.map { (category, words) ->
        category to Regex(words.joinToString("|", "\\b(", ")\\b") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
    }

    /** The built-in model, asked only when no keyword matched. Tests swap it; returning null means rules only. */
    internal var model: () -> CategoryModel? = { CategoryModel.bundled }

    /** How sure the model must be to be used; below it, the rules' answer (often "Other") stands. */
    internal const val MODEL_MIN_CONFIDENCE = 0.6f

    /** For typed or spoken notes: casual words first, then the regular merchant keywords, then the model. */
    fun guessCasual(text: String): Category =
        casualRules.firstOrNull { (_, regex) -> regex.containsMatchIn(text) }?.first ?: guess(text, "")

    /** Key used to remember the user's choice for a merchant. */
    fun merchantKey(merchant: String): String = merchant.trim().lowercase()

    /** [learned] is the category the user chose for this merchant before, if any; it always wins. */
    fun guess(merchant: String?, body: String, learned: Category? = null): Category {
        if (learned != null) return learned
        merchant?.let { name -> match(name)?.let { return it } }
        match(body)?.let { return it }
        // A merchant or title no keyword knows ("Mg Grocery Store", "popcorn"): ask the model.
        val text = merchant ?: body.takeIf { it.length <= MAX_MODEL_TEXT } ?: return Category.Other
        return modelGuess(text) ?: Category.Other
    }

    /** The model's category when it's confident and has an opinion; null otherwise. */
    private fun modelGuess(text: String): Category? =
        model()?.predict(text)
            ?.takeIf { it.confidence >= MODEL_MIN_CONFIDENCE && it.category != Category.Other }
            ?.category

    /** Whole SMS bodies are for the keywords; the model was trained on short names and phrases. */
    private const val MAX_MODEL_TEXT = 60

    private fun match(text: String): Category? = rules.firstOrNull { (_, regex) -> regex.containsMatchIn(text) }?.first
}
