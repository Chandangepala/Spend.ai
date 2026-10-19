package com.basic.spendai.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.basic.spendai.data.Category

/** What a row shows next to its title: an emoji after it, and the icon on its tile. */
data class TitleDecor(val emoji: String?, val icon: ImageVector)

private class Motif(val emoji: String, val icon: ImageVector, vararg words: String) {
    val words = words.toSet()
}

/**
 * Whole-word keywords for common spends; the first motif with a word in the title wins.
 * Kept small on purpose: an unknown title simply shows no emoji.
 */
private val Motifs = listOf(
    Motif("🔌", Icons.Rounded.Bolt, "electricity", "electric", "power", "bescom", "tneb", "msedcl"),
    Motif("💧", Icons.Rounded.WaterDrop, "water"),
    Motif("🔥", Icons.Rounded.LocalFireDepartment, "lpg", "cylinder", "gas"),
    Motif("📶", Icons.Rounded.Wifi, "wifi", "broadband", "internet", "fiber", "fibre"),
    Motif("📱", Icons.Rounded.PhoneAndroid, "recharge", "mobile", "prepaid", "postpaid"),
    Motif("☁️", Icons.Rounded.Cloud, "cloud", "aws", "gcp", "azure", "icloud", "dropbox", "hosting"),
    Motif("🍔", Icons.Rounded.Fastfood, "swiggy", "zomato", "burger", "pizza", "kfc", "mcdonalds", "dominos"),
    Motif("☕", Icons.Rounded.LocalCafe, "coffee", "chai", "tea", "starbucks", "cafe"),
    Motif("🛒", Icons.Rounded.ShoppingCart, "blinkit", "zepto", "bigbasket", "instamart", "dmart", "grocery", "groceries"),
    Motif("🚕", Icons.Rounded.LocalTaxi, "uber", "ola", "rapido", "cab", "taxi"),
    Motif("🚇", Icons.Rounded.Train, "metro", "train", "irctc"),
    Motif("⛽", Icons.Rounded.LocalGasStation, "petrol", "diesel", "fuel"),
    Motif("🅿️", Icons.Rounded.LocalParking, "parking"),
    Motif("🎬", Icons.Rounded.Movie, "netflix", "hotstar", "movie", "movies", "pvr", "inox", "bookmyshow"),
    Motif("🎵", Icons.Rounded.MusicNote, "spotify", "gaana", "music"),
    Motif("🏠", Icons.Rounded.Home, "rent"),
    Motif("💊", Icons.Rounded.Medication, "pharmacy", "medicine", "medicines", "pharmeasy"),
    Motif("🩺", Icons.Rounded.LocalHospital, "doctor", "hospital", "clinic", "dentist"),
    Motif("🛍️", Icons.Rounded.ShoppingBag, "amazon", "flipkart", "myntra", "ajio", "meesho"),
    Motif("✈️", Icons.Rounded.Flight, "flight", "flights", "airline", "makemytrip"),
    Motif("🏨", Icons.Rounded.Hotel, "hotel", "oyo", "airbnb"),
    Motif("🎁", Icons.Rounded.CardGiftcard, "gift", "gifts", "birthday"),
    Motif("💪", Icons.Rounded.FitnessCenter, "gym", "cult", "yoga"),
    Motif("📚", Icons.Rounded.MenuBook, "book", "books", "course", "udemy", "tuition"),
    Motif("🛡️", Icons.Rounded.Shield, "insurance", "policybazaar", "lic"),
    Motif("💳", Icons.Rounded.CreditCard, "emi", "loan"),
    Motif("✂️", Icons.Rounded.ContentCut, "salon", "haircut", "barber"),
)

/**
 * The emoji and tile icon for a row. The tile keeps the category's own icon (so a category always
 * looks the same) except for [Category.Other], whose generic icon says nothing: a matching
 * motif's icon is more useful there ("Google Cloud" shows a cloud).
 */
fun titleDecor(title: String, category: Category): TitleDecor {
    // Letters only, so SMS merchants like "Paytm*blinkit4412" still give "blinkit".
    val words = title.lowercase().split(Regex("[^\\p{L}]+")).toSet()
    val motif = Motifs.firstOrNull { m -> m.words.any(words::contains) }
    val icon = if (category == Category.Other && motif != null) motif.icon else category.icon
    // Titles the user already decorated ("Electricity 🔌") keep their own emoji, not a second one.
    return TitleDecor(motif?.emoji?.takeUnless { hasEmoji(title) }, icon)
}

private fun hasEmoji(text: String): Boolean =
    text.codePoints().anyMatch { Character.getType(it) == Character.OTHER_SYMBOL.toInt() || it >= 0x1F000 }
