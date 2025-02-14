package com.example.lottery.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.lottery.data.database.Converters

@Entity(tableName = "lottery_results")
@TypeConverters(Converters::class)
data class LotteryResult(
    @PrimaryKey
    val drawTime: Long,
    val type: LotteryType,
    val numbers: List<Int>,
    val specialNumber: Int,
    val zodiac: String? = null,
    val element: String? = null,
    val attributes: List<String> = emptyList()
)

enum class LotteryType {
    MACAU,
    HONGKONG
} 