class PredictionValidator @Inject constructor(
    private val database: LotteryDatabase
) {
    // 验证结果数据类
    data class ValidationResult(
        val hitCount: Int,                 // 命中号码数
        val specialNumberHit: Boolean,     // 特码是否命中
        val accuracy: Double,              // 准确率
        val hitNumbers: List<Int>,         // 命中的号码
        val missedNumbers: List<Int>,      // 未命中的号码
        val attributeMatchRate: Double,    // 属性匹配率
        val details: ValidationDetails     // 详细分析
    )
    
    data class ValidationDetails(
        val zodiacMatches: Int,           // 生肖匹配数
        val elementMatches: Int,           // 五行匹配数
        val colorMatches: Int,            // 颜色匹配数
        val oddEvenMatches: Int,          // 奇偶匹配数
        val bigSmallMatches: Int,         // 大小匹配数
        val sumDeviation: Double,         // 和值偏差
        val tailDeviation: Double,        // 尾数和偏差
        val consecutiveMatches: Int,      // 连号匹配数
        val distanceMatches: Int          // 距离匹配数
    )
    
    // 验证预测结果
    suspend fun validatePrediction(
        prediction: PredictionResult,
        actualResult: LotteryResult
    ): ValidationResult {
        // 基本命中统计
        val hitNumbers = prediction.numbers.filter { it in actualResult.numbers }
        val specialNumberHit = prediction.specialNumber == actualResult.specialNumber
        
        // 计算详细匹配情况
        val details = calculateValidationDetails(
            prediction.numbers + prediction.specialNumber,
            actualResult.numbers + actualResult.specialNumber
        )
        
        // 计算属性匹配率
        val attributeMatchRate = calculateAttributeMatchRate(details)
        
        return ValidationResult(
            hitCount = hitNumbers.size,
            specialNumberHit = specialNumberHit,
            accuracy = (hitNumbers.size + (if (specialNumberHit) 1 else 0)) / 7.0,
            hitNumbers = hitNumbers + if (specialNumberHit) listOf(prediction.specialNumber) else emptyList(),
            missedNumbers = prediction.numbers.filterNot { it in actualResult.numbers } +
                          if (!specialNumberHit) listOf(prediction.specialNumber) else emptyList(),
            attributeMatchRate = attributeMatchRate,
            details = details
        )
    }
    
    // 计算验证详情
    private fun calculateValidationDetails(
        predictedNumbers: List<Int>,
        actualNumbers: List<Int>
    ): ValidationDetails {
        // 生肖匹配
        val predictedZodiacs = predictedNumbers.map { getZodiacForNumber(it) }
        val actualZodiacs = actualNumbers.map { getZodiacForNumber(it) }
        val zodiacMatches = predictedZodiacs.count { it in actualZodiacs }
        
        // 五行匹配
        val predictedElements = predictedNumbers.map { getElementForNumber(it) }
        val actualElements = actualNumbers.map { getElementForNumber(it) }
        val elementMatches = predictedElements.count { it in actualElements }
        
        // 颜色匹配
        val predictedColors = predictedNumbers.map { getColorForNumber(it) }
        val actualColors = actualNumbers.map { getColorForNumber(it) }
        val colorMatches = predictedColors.count { it in actualColors }
        
        // 奇偶匹配
        val predictedOddEven = predictedNumbers.map { it % 2 }
        val actualOddEven = actualNumbers.map { it % 2 }
        val oddEvenMatches = predictedOddEven.count { it in actualOddEven }
        
        // 大小匹配
        val predictedBigSmall = predictedNumbers.map { it > 24 }
        val actualBigSmall = actualNumbers.map { it > 24 }
        val bigSmallMatches = predictedBigSmall.count { it in actualBigSmall }
        
        // 和值偏差
        val predictedSum = predictedNumbers.sum()
        val actualSum = actualNumbers.sum()
        val sumDeviation = abs(predictedSum - actualSum) / actualSum.toDouble()
        
        // 尾数和偏差
        val predictedTailSum = predictedNumbers.sumOf { it % 10 }
        val actualTailSum = actualNumbers.sumOf { it % 10 }
        val tailDeviation = abs(predictedTailSum - actualTailSum) / actualTailSum.toDouble()
        
        // 连号匹配
        val consecutiveMatches = countConsecutiveMatches(predictedNumbers, actualNumbers)
        
        // 距离匹配
        val distanceMatches = countDistanceMatches(predictedNumbers, actualNumbers)
        
        return ValidationDetails(
            zodiacMatches = zodiacMatches,
            elementMatches = elementMatches,
            colorMatches = colorMatches,
            oddEvenMatches = oddEvenMatches,
            bigSmallMatches = bigSmallMatches,
            sumDeviation = sumDeviation,
            tailDeviation = tailDeviation,
            consecutiveMatches = consecutiveMatches,
            distanceMatches = distanceMatches
        )
    }
    
    // 计算属性匹配率
    private fun calculateAttributeMatchRate(details: ValidationDetails): Double {
        val weights = mapOf(
            details.zodiacMatches to 0.2,
            details.elementMatches to 0.2,
            details.colorMatches to 0.15,
            details.oddEvenMatches to 0.1,
            details.bigSmallMatches to 0.1,
            (1.0 - details.sumDeviation).coerceAtLeast(0.0) * 7 to 0.1,
            (1.0 - details.tailDeviation).coerceAtLeast(0.0) * 7 to 0.05,
            details.consecutiveMatches to 0.05,
            details.distanceMatches to 0.05
        )
        
        return weights.entries.sumOf { (value, weight) -> 
            value.toDouble() * weight 
        } / 7.0
    }
    
    // 统计连号匹配
    private fun countConsecutiveMatches(
        predictedNumbers: List<Int>,
        actualNumbers: List<Int>
    ): Int {
        fun getConsecutiveSequences(numbers: List<Int>): List<List<Int>> {
            val sorted = numbers.sorted()
            val sequences = mutableListOf<List<Int>>()
            var currentSeq = mutableListOf<Int>()
            
            sorted.forEach { num ->
                if (currentSeq.isEmpty() || num == currentSeq.last() + 1) {
                    currentSeq.add(num)
                } else {
                    if (currentSeq.size > 1) sequences.add(currentSeq.toList())
                    currentSeq = mutableListOf(num)
                }
            }
            if (currentSeq.size > 1) sequences.add(currentSeq)
            return sequences
        }
        
        val predictedSeqs = getConsecutiveSequences(predictedNumbers)
        val actualSeqs = getConsecutiveSequences(actualNumbers)
        
        return predictedSeqs.sumOf { predicted ->
            actualSeqs.count { actual ->
                predicted.size == actual.size
            }
        }
    }
    
    // 统计距离匹配
    private fun countDistanceMatches(
        predictedNumbers: List<Int>,
        actualNumbers: List<Int>
    ): Int {
        fun getDistances(numbers: List<Int>): List<Int> {
            val sorted = numbers.sorted()
            return sorted.zipWithNext { a, b -> b - a }
        }
        
        val predictedDistances = getDistances(predictedNumbers)
        val actualDistances = getDistances(actualNumbers)
        
        return predictedDistances.count { predicted ->
            actualDistances.any { actual ->
                abs(predicted - actual) <= 1
            }
        }
    }
    
    // 生成验证报告
    fun generateValidationReport(result: ValidationResult): String {
        return buildString {
            appendLine("预测验证报告")
            appendLine("==============")
            appendLine("命中情况:")
            appendLine("- 普通号码命中: ${result.hitCount}/6")
            appendLine("- 特别号码命中: ${if (result.specialNumberHit) "是" else "否"}")
            appendLine("- 总体准确率: ${String.format("%.2f%%", result.accuracy * 100)}")
            appendLine()
            
            appendLine("命中号码: ${result.hitNumbers.sorted().joinToString(", ")}")
            appendLine("未中号码: ${result.missedNumbers.sorted().joinToString(", ")}")
            appendLine()
            
            appendLine("属性匹配分析:")
            appendLine("- 生肖匹配: ${result.details.zodiacMatches}/7")
            appendLine("- 五行匹配: ${result.details.elementMatches}/7")
            appendLine("- 颜色匹配: ${result.details.colorMatches}/7")
            appendLine("- 奇偶匹配: ${result.details.oddEvenMatches}/7")
            appendLine("- 大小匹配: ${result.details.bigSmallMatches}/7")
            appendLine()
            
            appendLine("数值分析:")
            appendLine("- 和值偏差: ${String.format("%.2f%%", result.details.sumDeviation * 100)}")
            appendLine("- 尾数偏差: ${String.format("%.2f%%", result.details.tailDeviation * 100)}")
            appendLine("- 连号匹配: ${result.details.consecutiveMatches}")
            appendLine("- 距离匹配: ${result.details.distanceMatches}")
            appendLine()
            
            appendLine("综合评分:")
            appendLine("属性匹配率: ${String.format("%.2f%%", result.attributeMatchRate * 100)}")
        }
    }
} 