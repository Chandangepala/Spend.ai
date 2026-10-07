package com.basic.spendai.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class Category(val label: String, val color: Color, val icon: ImageVector) {
    Food("Food", Color(0xFFE53935), Icons.Rounded.Restaurant),
    Transport("Transport", Color(0xFF1E88E5), Icons.Rounded.DirectionsCar),
    Shopping("Shopping", Color(0xFF8E24AA), Icons.Rounded.ShoppingBag),
    Bills("Bills", Color(0xFFFB8C00), Icons.Rounded.Receipt),
    Entertainment("Entertainment", Color(0xFFD81B60), Icons.Rounded.Movie),
    Health("Health", Color(0xFF00897B), Icons.Rounded.LocalHospital),
    Other("Other", Color(0xFF757575), Icons.Rounded.Category),
}
