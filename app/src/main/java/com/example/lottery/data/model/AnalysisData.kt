package com.example.lottery.data.model

import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.PieData

data class AnalysisData(
    val trendData: LineData,
    val distributionData: BarData,
    val patternData: PieData,
    val statistics: Statistics
)

data class Statistics(
    val totalCount: Int,
    val oddEvenRatio: String,
    val bigSmallRatio: String,
    val sumRange: String,
    val mostFrequentNumbers: List<Int>,
    val leastFrequentNumbers: List<Int>,
    val hotZodiac: String,
    val hotElement: String
) 