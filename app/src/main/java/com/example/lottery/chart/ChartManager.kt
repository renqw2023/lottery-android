package com.example.lottery.chart

import android.graphics.Color
import com.example.lottery.data.model.LotteryResult
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.utils.ColorTemplate
import javax.inject.Inject
import javax.inject.Singleton
import java.text.SimpleDateFormat
import java.util.*

@Singleton
class ChartManager @Inject constructor() {
    
    // 创建趋势图
    fun createTrendChart(results: List<LotteryResult>): LineData {
        val entries = mutableListOf<Entry>()
        val labels = mutableListOf<String>()
        
        results.forEachIndexed { index, result ->
            // 添加普通号码
            result.numbers.forEach { number ->
                entries.add(Entry(index.toFloat(), number.toFloat()))
            }
            // 添加特别号
            entries.add(Entry(index.toFloat(), result.specialNumber.toFloat(), true))
            
            // 添加日期标签
            labels.add(formatDate(result.drawTime, "MM-dd"))
        }
        
        val dataSet = LineDataSet(entries, "号码走势").apply {
            color = Color.BLUE
            setCircleColor(Color.BLUE)
            setDrawCircles(true)
            setDrawValues(false)
            lineWidth = 1.5f
            circleRadius = 3f
            
            // 特别号标记
            setDrawIcons(true)
            iconsOffset = MPPointF(0f, -5f)
            
            // 高亮
            highLightColor = Color.RED
            highlightLineWidth = 1f
            setDrawHorizontalHighlightIndicator(true)
            setDrawVerticalHighlightIndicator(true)
            
            // 动画
            mode = LineDataSet.Mode.LINEAR
        }
        
        return LineData(dataSet).apply {
            setValueTextSize(9f)
            setDrawValues(false)
        }
    }

    private fun formatDate(timestamp: Long, pattern: String): String {
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
    }

    // 创建分布图
    fun createDistributionChart(results: List<LotteryResult>): BarData {
        val entries = mutableListOf<BarEntry>()
        val numberFrequency = IntArray(49) { 0 }
        
        // 统计每个号码出现的频率
        results.forEach { result ->
            result.numbers.forEach { number ->
                numberFrequency[number - 1]++
            }
        }
        
        // 创建柱状图数据
        numberFrequency.forEachIndexed { index, frequency ->
            entries.add(BarEntry((index + 1).toFloat(), frequency.toFloat()))
        }
        
        val dataSet = BarDataSet(entries, "号码分布").apply {
            colors = ColorTemplate.MATERIAL_COLORS.toList()
            valueTextSize = 10f
        }
        
        return BarData(dataSet)
    }

    // 创建规律图
    fun createPatternChart(results: List<LotteryResult>): PieData {
        val entries = mutableListOf<PieEntry>()
        val patterns = analyzePatterns(results)
        
        patterns.forEach { (pattern, count) ->
            entries.add(PieEntry(count.toFloat(), pattern))
        }
        
        val dataSet = PieDataSet(entries, "号码规律").apply {
            colors = ColorTemplate.MATERIAL_COLORS.toList()
            valueTextSize = 12f
            valueTextColor = Color.WHITE
        }
        
        return PieData(dataSet)
    }

    private fun analyzePatterns(results: List<LotteryResult>): Map<String, Int> {
        // TODO: 实现规律分析逻辑
        return emptyMap()
    }
} 