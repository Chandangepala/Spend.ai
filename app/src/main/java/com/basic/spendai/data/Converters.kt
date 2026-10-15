package com.basic.spendai.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromCategory(category: Category): String = category.name

    @TypeConverter
    fun toCategory(name: String): Category =
        Category.entries.firstOrNull { it.name == name } ?: Category.Other

    @TypeConverter
    fun fromStatus(status: DetectionStatus): String = status.name

    @TypeConverter
    fun toStatus(name: String): DetectionStatus =
        DetectionStatus.entries.firstOrNull { it.name == name } ?: DetectionStatus.PENDING

    @TypeConverter
    fun fromAiState(state: AiState): String = state.name

    @TypeConverter
    fun toAiState(name: String): AiState =
        AiState.entries.firstOrNull { it.name == name } ?: AiState.PENDING_AI
}
