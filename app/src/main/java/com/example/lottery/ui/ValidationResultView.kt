class ValidationResultView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding = ViewValidationResultBinding.inflate(LayoutInflater.from(context), this, true)
    
    // 更新验证结果
    fun updateValidationResult(result: PredictionValidator.ValidationResult) {
        setupAccuracyChart(result)
        setupAttributeMatchChart(result)
        setupDeviationChart(result)
        updateSummaryText(result)
    }
    
    // 设置准确率饼图
    private fun setupAccuracyChart(result: PredictionValidator.ValidationResult) {
        val entries = listOf(
            PieEntry(result.hitCount.toFloat(), "命中"),
            PieEntry((7 - result.hitCount).toFloat(), "未中")
        )
        
        val dataSet = PieDataSet(entries, "命中率").apply {
            colors = listOf(
                Color.parseColor("#4CAF50"),  // 命中-绿色
                Color.parseColor("#F44336")   // 未中-红色
            )
            valueTextSize = 12f
            valueFormatter = PercentFormatter()
        }
        
        binding.accuracyChart.apply {
            data = PieData(dataSet)
            description.isEnabled = false
            legend.isEnabled = true
            setUsePercentValues(true)
            centerText = "命中率\n${String.format("%.1f%%", result.accuracy * 100)}"
            setCenterTextSize(14f)
            
            animateY(1000)
            invalidate()
        }
    }
    
    // 设置属性匹配雷达图
    private fun setupAttributeMatchChart(result: PredictionValidator.ValidationResult) {
        val entries = listOf(
            RadarEntry(result.details.zodiacMatches.toFloat()),
            RadarEntry(result.details.elementMatches.toFloat()),
            RadarEntry(result.details.colorMatches.toFloat()),
            RadarEntry(result.details.oddEvenMatches.toFloat()),
            RadarEntry(result.details.bigSmallMatches.toFloat())
        )
        
        val dataSet = RadarDataSet(entries, "属性匹配").apply {
            color = Color.parseColor("#2196F3")
            fillColor = Color.parseColor("#2196F3")
            fillAlpha = 100
            lineWidth = 2f
            valueTextSize = 12f
        }
        
        binding.attributeMatchChart.apply {
            data = RadarData(dataSet)
            description.isEnabled = false
            
            xAxis.valueFormatter = IndexAxisValueFormatter(listOf(
                "生肖", "五行", "颜色", "奇偶", "大小"
            ))
            
            yAxis.apply {
                axisMinimum = 0f
                axisMaximum = 7f
                setDrawLabels(false)
            }
            
            legend.isEnabled = true
            
            animateXY(1000, 1000)
            invalidate()
        }
    }
    
    // 设置偏差柱状图
    private fun setupDeviationChart(result: PredictionValidator.ValidationResult) {
        val entries = listOf(
            BarEntry(0f, (1 - result.details.sumDeviation).toFloat() * 100),
            BarEntry(1f, (1 - result.details.tailDeviation).toFloat() * 100),
            BarEntry(2f, result.details.consecutiveMatches.toFloat() / 7 * 100),
            BarEntry(3f, result.details.distanceMatches.toFloat() / 7 * 100)
        )
        
        val dataSet = BarDataSet(entries, "数值分析").apply {
            colors = listOf(
                Color.parseColor("#FF9800"),  // 和值
                Color.parseColor("#9C27B0"),  // 尾数
                Color.parseColor("#3F51B5"),  // 连号
                Color.parseColor("#009688")   // 距离
            )
            valueTextSize = 12f
            valueFormatter = PercentFormatter()
        }
        
        binding.deviationChart.apply {
            data = BarData(dataSet)
            description.isEnabled = false
            
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(listOf(
                    "和值", "尾数", "连号", "距离"
                ))
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
            }
            
            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 100f
                valueFormatter = PercentFormatter()
            }
            axisRight.isEnabled = false
            
            legend.isEnabled = true
            
            animateY(1000)
            invalidate()
        }
    }
    
    // 更新总结文本
    private fun updateSummaryText(result: PredictionValidator.ValidationResult) {
        binding.summaryText.text = buildString {
            append("预测结果总结\n\n")
            
            append("命中号码: ")
            append(result.hitNumbers.sorted().joinToString(", "))
            append("\n\n")
            
            append("属性匹配率: ")
            append(String.format("%.1f%%", result.attributeMatchRate * 100))
            append("\n\n")
            
            // 添加建议
            append("分析建议:\n")
            if (result.accuracy >= 0.5) {
                append("• 当前预测模型表现良好，建议保持现有策略\n")
            } else {
                append("• 建议调整预测权重，增加")
                when {
                    result.details.zodiacMatches >= 4 -> append("生肖")
                    result.details.elementMatches >= 4 -> append("五行")
                    result.details.colorMatches >= 4 -> append("颜色")
                    else -> append("基础属性")
                }
                append("维度的权重\n")
            }
            
            // 添加特征分析
            if (result.details.consecutiveMatches > 0) {
                append("• 连号特征明显，可以增加连号权重\n")
            }
            if (result.details.sumDeviation < 0.1) {
                append("• 和值预测准确，可以增加和值权重\n")
            }
        }
    }
} 