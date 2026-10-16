package com.basic.spendai.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Stored in Room by [name], so existing names never change: `Food` is shown as "Dining & Drinks",
 * `Bills` as "Bills & Utilities" and `Health` as "Health & Fitness". Unknown names load as [Other].
 */
enum class Category(val label: String, val color: Color, val icon: ImageVector) {
    Food("Dining & Drinks", Color(0xFFE53935), Icons.Rounded.Restaurant),
    Groceries("Groceries", Color(0xFF43A047), Icons.Rounded.LocalGroceryStore),
    Transport("Transport", Color(0xFF1E88E5), Icons.Rounded.DirectionsCar),
    Shopping("Shopping", Color(0xFF8E24AA), Icons.Rounded.ShoppingBag),
    Bills("Bills & Utilities", Color(0xFFFB8C00), Icons.Rounded.Receipt),
    Home("Home & Rent", Color(0xFF6D4C41), Icons.Rounded.Home),
    Entertainment("Entertainment", Color(0xFFD81B60), Icons.Rounded.Movie),
    Health("Health & Fitness", Color(0xFF00897B), Icons.Rounded.LocalHospital),
    PersonalCare("Personal Care", Color(0xFFEC407A), Icons.Rounded.ContentCut),
    Travel("Travel & Stays", Color(0xFF00ACC1), Icons.Rounded.Flight),
    Education("Education & Work", Color(0xFF3949AB), Icons.Rounded.School),
    Finance("EMI & Finance", Color(0xFF546E7A), Icons.Rounded.AccountBalance),
    Gifts("Gifts & Family", Color(0xFFF4511E), Icons.Rounded.CardGiftcard),
    Other("Other", Color(0xFF757575), Icons.Rounded.Category),
}
