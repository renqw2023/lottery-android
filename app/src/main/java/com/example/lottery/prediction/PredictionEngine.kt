import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

class PredictionEngine @Inject constructor(
    private val database: LotteryDatabase,
    private val chartManager: ChartManager,
    @Inject lateinit var validator: PredictionValidator
) {
    // 权重配置数据类
    data class WeightConfig(
        val periodicityWeight: Double = 0.20,    // 周期性权重
        val zodiacWeight: Double = 0.15,         // 生肖权重
        val elementWeight: Double = 0.15,        // 五行权重
        val attributeWeight: Double = 0.15,      // 特征匹配权重
        val sumWeight: Double = 0.10,            // 数字和权重
        val tailWeight: Double = 0.10,           // 尾数权重
        val consecutiveWeight: Double = 0.10,    // 连号权重
        val distanceWeight: Double = 0.05        // 距离权重
    ) {
        init {
            require(listOf(
                periodicityWeight, zodiacWeight, elementWeight, attributeWeight,
                sumWeight, tailWeight, consecutiveWeight, distanceWeight
            ).sum() == 1.0) { "权重总和必须等于1.0" }
        }
    }

    private var weightConfig = WeightConfig()

    // 动态权重优化
    private suspend fun optimizeWeights(type: LotteryType) {
        val historicalData = database.lotteryDao().getAllResults(type).first()
        if (historicalData.size < 10) return // 数据太少，不进行优化
        
        // 计算每个维度的预测准确率
        val periodicityAccuracy = calculateDimensionAccuracy(historicalData) { numbers, target ->
            numbers.filter { number ->
                val periodicity = chartManager.analyzeNumberPeriodicity(type).periodicityData[number]
                periodicity?.standardDeviation ?: Double.MAX_VALUE < 2.0
            }.any { it in target }
        }
        
        val zodiacAccuracy = calculateDimensionAccuracy(historicalData) { numbers, target ->
            val zodiacPattern = chartManager.analyzeZodiacPeriodicity(type)
            numbers.filter { number ->
                val zodiac = getZodiacName(getZodiacForNumber(number))
                zodiacPattern.transitionPatterns.any { it.key.second == zodiac && it.value > 5 }
            }.any { it in target }
        }
        
        // 计算其他维度的准确率...
        
        // 根据准确率调整权重
        val totalAccuracy = periodicityAccuracy + zodiacAccuracy // + 其他维度准确率
        weightConfig = WeightConfig(
            periodicityWeight = (periodicityAccuracy / totalAccuracy).coerceIn(0.1, 0.3),
            zodiacWeight = (zodiacAccuracy / totalAccuracy).coerceIn(0.1, 0.3),
            // 其他权重调整...
        )
    }

    // 计算单个维度的预测准确率
    private fun calculateDimensionAccuracy(
        historicalData: List<LotteryResult>,
        predictionCheck: (List<Int>, List<Int>) -> Boolean
    ): Double {
        var correctPredictions = 0
        var totalPredictions = 0
        
        for (i in 0 until historicalData.size - 1) {
            val currentResult = historicalData[i]
            val nextResult = historicalData[i + 1]
            val predictedNumbers = (1..49).toList() // 所有可能的号码
            
            if (predictionCheck(predictedNumbers, nextResult.numbers + nextResult.specialNumber)) {
                correctPredictions++
            }
            totalPredictions++
        }
        
        return correctPredictions.toDouble() / totalPredictions
    }

    // 预测下一期号码
    suspend fun predictNextDraw(type: LotteryType): PredictionResult = withContext(Dispatchers.Default) {
        // 优化权重
        optimizeWeights(type)
        
        val historicalData = database.lotteryDao().getAllResults(type).first()
        
        // 获取各种分析结果
        val numberPeriodicity = chartManager.analyzeNumberPeriodicity(type)
        val zodiacPeriodicity = chartManager.analyzeZodiacPeriodicity(type)
        val elementPeriodicity = chartManager.analyzeElementPeriodicity(type)
        val specialNumberPatterns = chartManager.analyzeSpecialNumberPatterns(type)
        
        // 基于周期性分析选择候选号码
        val candidates = selectCandidateNumbers(
            numberPeriodicity,
            zodiacPeriodicity,
            elementPeriodicity,
            specialNumberPatterns,
            historicalData
        )
        
        // 计算每个候选号码的综合得分
        val scoredNumbers = candidates.map { number ->
            val score = calculateNumberScore(
                number,
                numberPeriodicity,
                zodiacPeriodicity,
                elementPeriodicity,
                specialNumberPatterns,
                historicalData
            )
            number to score
        }.sortedByDescending { it.second }
        
        // 选择得分最高的号码作为预测结果
        val selectedNumbers = scoredNumbers.take(7)
        
        PredictionResult(
            numbers = selectedNumbers.take(6).map { it.first },
            specialNumber = selectedNumbers.last().first,
            confidence = calculateConfidence(selectedNumbers, historicalData)
        )
    }
    
    // 选择候选号码
    private fun selectCandidateNumbers(
        numberPeriodicity: ChartManager.NumberPeriodicityResult,
        zodiacPeriodicity: ChartManager.ZodiacPeriodicityResult,
        elementPeriodicity: ChartManager.ElementPeriodicityResult,
        specialNumberPatterns: ChartManager.SpecialNumberResult,
        historicalData: List<LotteryResult>
    ): List<Int> {
        val candidates = mutableSetOf<Int>()
        
        // 1. 基于号码周期性选择候选号码
        candidates.addAll(selectNumbersByPeriodicity(numberPeriodicity))
        
        // 2. 基于生肖转换模式选择候选号码
        candidates.addAll(selectNumbersByZodiacPattern(zodiacPeriodicity))
        
        // 3. 基于五行组合选择候选号码
        candidates.addAll(selectNumbersByElementPattern(elementPeriodicity))
        
        // 4. 基于特码规律选择候选号码
        candidates.addAll(selectNumbersBySpecialPattern(specialNumberPatterns))
        
        // 新增维度的选择逻辑
        candidates.addAll(selectNumbersBySumPattern(historicalData))
        candidates.addAll(selectNumbersByTailPattern(historicalData))
        candidates.addAll(selectNumbersByConsecutivePattern(historicalData))
        candidates.addAll(selectNumbersByDistancePattern(historicalData))
        
        return candidates.toList()
    }
    
    // 基于号码周期性选择候选号码
    private fun selectNumbersByPeriodicity(
        periodicity: ChartManager.NumberPeriodicityResult
    ): List<Int> {
        // 选择周期性稳定的号码（标准差较小）
        return periodicity.periodicityData
            .filter { it.value.standardDeviation < 2.0 }
            .map { it.key }
    }
    
    // 基于生肖转换模式选择候选号码
    private fun selectNumbersByZodiacPattern(
        zodiacPeriodicity: ChartManager.ZodiacPeriodicityResult
    ): List<Int> {
        // 找出最常见的生肖转换模式
        val commonPatterns = zodiacPeriodicity.transitionPatterns
            .filter { it.value > 5 }
            .keys.map { it.second }
            
        // 返回属于这些生肖的号码
        return (1..49).filter { number ->
            getZodiacName(getZodiacForNumber(number)) in commonPatterns
        }
    }
    
    // 基于五行组合选择候选号码
    private fun selectNumbersByElementPattern(
        elementPeriodicity: ChartManager.ElementPeriodicityResult
    ): List<Int> {
        // 找出最常见的五行组合
        val commonCombination = elementPeriodicity.combinationPatterns
            .maxByOrNull { it.value }?.key
            
        // 返回符合这个五行组合的号码
        return (1..49).filter { number ->
            commonCombination?.containsKey(getElementForNumber(number)) == true
        }
    }
    
    // 基于特码规律选择候选号码
    private fun selectNumbersBySpecialPattern(
        specialNumberPatterns: ChartManager.SpecialNumberResult
    ): List<Int> {
        val candidates = mutableListOf<Int>()
        
        // 根据奇偶分布选择
        val preferOdd = specialNumberPatterns.oddEvenDistribution[true] ?: 0 >
                       specialNumberPatterns.oddEvenDistribution[false] ?: 0
                       
        // 根据大小分布选择
        val preferBig = specialNumberPatterns.bigSmallDistribution[true] ?: 0 >
                       specialNumberPatterns.bigSmallDistribution[false] ?: 0
                       
        // 选择符合条件的号码
        candidates.addAll((1..49).filter { number ->
            val isOdd = number % 2 == 1
            val isBig = number > 24
            (isOdd == preferOdd) && (isBig == preferBig)
        })
        
        return candidates
    }
    
    // 基于数字和选择候选号码
    private fun selectNumbersBySumPattern(historicalData: List<LotteryResult>): List<Int> {
        // 计算历史数据中的数字和分布
        val sumDistribution = historicalData.map { result ->
            result.numbers.sum() + result.specialNumber
        }.groupingBy { it }.eachCount()
        
        // 找出最常见的数字和范围
        val avgSum = sumDistribution.entries.maxByOrNull { it.value }?.key ?: 0
        val tolerance = 10
        
        // 选择符合数字和范围的号码组合
        return (1..49).filter { number ->
            val remainingSum = avgSum - number
            remainingSum in (6 * 1)..(6 * 49) // 确保剩余和在合理范围内
        }
    }
    
    // 基于尾数和选择候选号码
    private fun selectNumbersByTailPattern(historicalData: List<LotteryResult>): List<Int> {
        // 计算历史数据中的尾数和分布
        val tailSumDistribution = historicalData.map { result ->
            (result.numbers.map { it % 10 }.sum() + result.specialNumber % 10)
        }.groupingBy { it }.eachCount()
        
        // 找出最常见的尾数和范围
        val avgTailSum = tailSumDistribution.entries.maxByOrNull { it.value }?.key ?: 0
        val tolerance = 3
        
        // 选择符合尾数和范围的号码
        return (1..49).filter { number ->
            val tail = number % 10
            val remainingTailSum = avgTailSum - tail
            remainingTailSum in 0..54 // 6个数字的尾数和最大可能值
        }
    }
    
    // 基于连号模式选择候选号码
    private fun selectNumbersByConsecutivePattern(historicalData: List<LotteryResult>): List<Int> {
        // 分析历史数据中连号的出现情况
        val consecutivePatterns = mutableMapOf<Int, Int>() // 连号长度的分布
        
        historicalData.forEach { result ->
            val numbers = (result.numbers + result.specialNumber).sorted()
            var currentLength = 1
            
            for (i in 1 until numbers.size) {
                if (numbers[i] == numbers[i-1] + 1) {
                    currentLength++
                } else {
                    if (currentLength > 1) {
                        consecutivePatterns[currentLength] = 
                            (consecutivePatterns[currentLength] ?: 0) + 1
                    }
                    currentLength = 1
                }
            }
        }
        
        // 找出最常见的连号长度
        val commonLength = consecutivePatterns.maxByOrNull { it.value }?.key ?: 0
        
        // 选择可能形成连号的号码
        return (1..49).filter { number ->
            val sequence = (number until (number + commonLength)).toList()
            sequence.all { it <= 49 }
        }
    }
    
    // 基于号码间距选择候选号码
    private fun selectNumbersByDistancePattern(historicalData: List<LotteryResult>): List<Int> {
        // 计算历史数据中号码间的距离分布
        val distanceDistribution = mutableMapOf<Int, Int>()
        
        historicalData.forEach { result ->
            val numbers = (result.numbers + result.specialNumber).sorted()
            for (i in 1 until numbers.size) {
                val distance = numbers[i] - numbers[i-1]
                distanceDistribution[distance] = (distanceDistribution[distance] ?: 0) + 1
            }
        }
        
        // 找出最常见的距离
        val commonDistances = distanceDistribution.entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }
        
        // 选择符合常见距离的号码
        return (1..49).filter { number ->
            commonDistances.any { distance ->
                (number + distance <= 49) || (number - distance >= 1)
            }
        }
    }
    
    // 计算号码得分
    private fun calculateNumberScore(
        number: Int,
        numberPeriodicity: ChartManager.NumberPeriodicityResult,
        zodiacPeriodicity: ChartManager.ZodiacPeriodicityResult,
        elementPeriodicity: ChartManager.ElementPeriodicityResult,
        specialNumberPatterns: ChartManager.SpecialNumberResult,
        historicalData: List<LotteryResult>
    ): Double {
        var score = 0.0
        
        // 使用动态权重计算得分
        score += calculatePeriodicityScore(number, numberPeriodicity) * weightConfig.periodicityWeight
        score += calculateZodiacScore(number, zodiacPeriodicity) * weightConfig.zodiacWeight
        score += calculateElementScore(number, elementPeriodicity) * weightConfig.elementWeight
        score += calculateAttributeMatchScore(number, specialNumberPatterns) * weightConfig.attributeWeight
        score += calculateSumScore(number, historicalData) * weightConfig.sumWeight
        score += calculateTailScore(number, historicalData) * weightConfig.tailWeight
        score += calculateConsecutiveScore(number, historicalData) * weightConfig.consecutiveWeight
        score += calculateDistanceScore(number, historicalData) * weightConfig.distanceWeight
        
        return score
    }
    
    // 计算特征匹配得分
    private fun calculateAttributeMatchScore(
        attributes: ChartManager.SpecialNumberAttributes,
        patterns: ChartManager.SpecialNumberResult
    ): Double {
        var matchScore = 0.0
        
        // 奇偶匹配
        val oddCount = patterns.oddEvenDistribution[true] ?: 0
        val evenCount = patterns.oddEvenDistribution[false] ?: 0
        if ((attributes.isOdd && oddCount > evenCount) || 
            (!attributes.isOdd && evenCount > oddCount)) {
            matchScore += 0.25
        }
        
        // 大小匹配
        val bigCount = patterns.bigSmallDistribution[true] ?: 0
        val smallCount = patterns.bigSmallDistribution[false] ?: 0
        if ((attributes.isBig && bigCount > smallCount) || 
            (!attributes.isBig && smallCount > bigCount)) {
            matchScore += 0.25
        }
        
        // 生肖匹配
        val zodiacCount = patterns.zodiacDistribution[getZodiacName(attributes.zodiac)] ?: 0
        matchScore += zodiacCount.toDouble() / 100.0 * 0.25
        
        // 五行匹配
        val elementCount = patterns.elementDistribution[attributes.element] ?: 0
        matchScore += elementCount.toDouble() / 100.0 * 0.25
        
        return matchScore
    }
    
    // 计算数字和得分
    private fun calculateSumScore(number: Int, historicalData: List<LotteryResult>): Double {
        val historicalSums = historicalData.map { result ->
            result.numbers.sum() + result.specialNumber
        }
        val avgSum = historicalSums.average()
        val maxDeviation = historicalSums.maxOf { abs(it - avgSum) }
        
        return 1.0 - (abs(number - avgSum) / maxDeviation).coerceAtMost(1.0)
    }
    
    // 计算尾数得分
    private fun calculateTailScore(number: Int, historicalData: List<LotteryResult>): Double {
        val tail = number % 10
        val tailCounts = historicalData.flatMap { result ->
            result.numbers.map { it % 10 } + listOf(result.specialNumber % 10)
        }.groupingBy { it }.eachCount()
        
        val maxCount = tailCounts.values.maxOrNull() ?: 1
        return (tailCounts[tail] ?: 0).toDouble() / maxCount
    }
    
    // 计算连号得分
    private fun calculateConsecutiveScore(number: Int, historicalData: List<LotteryResult>): Double {
        val consecutiveCounts = mutableMapOf<Int, Int>()
        historicalData.forEach { result ->
            val numbers = (result.numbers + result.specialNumber).sorted()
            numbers.forEachIndexed { index, n ->
                if (index > 0 && n == numbers[index-1] + 1) {
                    consecutiveCounts[n] = (consecutiveCounts[n] ?: 0) + 1
                }
            }
        }
        
        return if (consecutiveCounts.containsKey(number)) {
            consecutiveCounts[number]!!.toDouble() / (consecutiveCounts.values.maxOrNull() ?: 1)
        } else {
            0.0
        }
    }
    
    // 计算距离得分
    private fun calculateDistanceScore(number: Int, historicalData: List<LotteryResult>): Double {
        val distanceCounts = mutableMapOf<Int, Int>()
        historicalData.forEach { result ->
            val numbers = (result.numbers + result.specialNumber).sorted()
            for (i in 1 until numbers.size) {
                val distance = numbers[i] - numbers[i-1]
                distanceCounts[distance] = (distanceCounts[distance] ?: 0) + 1
            }
        }
        
        val nearNumbers = historicalData.flatMap { result ->
            result.numbers + result.specialNumber
        }.filter { abs(it - number) <= 3 }
        
        return nearNumbers.size.toDouble() / historicalData.size
    }
    
    // 计算预测可信度
    private fun calculateConfidence(
        predictedNumbers: List<Int>,
        history: List<LotteryResult>
    ): Double {
        if (history.isEmpty()) return 0.0
        
        // 1. 历史准确率分析
        val historicalAccuracy = calculateHistoricalAccuracy(history)
        
        // 2. 号码分布合理性
        val numberDistributionScore = analyzeNumberDistribution(predictedNumbers)
        
        // 3. 生肖组合合理性
        val zodiacCombinationScore = analyzeZodiacCombination(predictedNumbers)
        
        // 4. 五行平衡性
        val elementBalanceScore = analyzeElementBalance(predictedNumbers)
        
        // 5. 颜色分布合理性
        val colorDistributionScore = analyzeColorDistribution(predictedNumbers)
        
        // 综合各维度得分，计算最终可信度
        return (historicalAccuracy * 0.3 +
                numberDistributionScore * 0.2 +
                zodiacCombinationScore * 0.2 +
                elementBalanceScore * 0.15 +
                colorDistributionScore * 0.15)
            .coerceIn(0.0, 1.0)
    }
    
    // 计算历史预测准确率
    private fun calculateHistoricalAccuracy(history: List<LotteryResult>): Double {
        if (history.size < 2) return 0.0
        
        var totalHits = 0
        var totalPredictions = 0
        
        // 使用每期结果预测下一期，统计命中率
        for (i in 0 until history.size - 1) {
            val currentResult = history[i]
            val nextResult = history[i + 1]
            
            // 模拟预测
            val prediction = generatePrediction(
                mapOf(currentResult.specialNumber to 1.0),
                mapOf(getZodiacForNumber(currentResult.specialNumber) to 1.0),
                mapOf(getElementForNumber(currentResult.specialNumber) to 1.0),
                mapOf(getColorForNumber(currentResult.specialNumber) to 1.0)
            )
            
            // 统计命中数
            val hits = prediction.count { it in nextResult.numbers + nextResult.specialNumber }
            totalHits += hits
            totalPredictions += 7
        }
        
        return totalHits.toDouble() / totalPredictions
    }
    
    // 分析号码分布合理性
    private fun analyzeNumberDistribution(numbers: List<Int>): Double {
        var score = 1.0
        
        // 检查数字间隔
        val gaps = numbers.sorted().zipWithNext { a, b -> b - a }
        val avgGap = gaps.average()
        if (avgGap < 3 || avgGap > 10) {
            score *= 0.8
        }
        
        // 检查奇偶比例
        val oddCount = numbers.count { it % 2 == 1 }
        val evenCount = numbers.size - oddCount
        if (abs(oddCount - evenCount) > 2) {
            score *= 0.8
        }
        
        // 检查大小数字比例（1-24为小数，25-49为大数）
        val smallCount = numbers.count { it <= 24 }
        val largeCount = numbers.size - smallCount
        if (abs(smallCount - largeCount) > 2) {
            score *= 0.8
        }
        
        return score
    }
    
    // 分析生肖组合合理性
    private fun analyzeZodiacCombination(numbers: List<Int>): Double {
        var score = 1.0
        
        // 获取生肖组合
        val zodiacs = numbers.map { getZodiacForNumber(it) }.toSet()
        
        // 检查生肖多样性
        if (zodiacs.size < 4) {
            score *= 0.7  // 生肖太少，降低可信度
        }
        
        // 检查阴阳平衡
        val yinCount = zodiacs.count { it.yinYang == YinYang.YIN }
        val yangCount = zodiacs.size - yinCount
        if (abs(yinCount - yangCount) > 2) {
            score *= 0.8
        }
        
        // 检查天地生肖比例
        val skyCount = zodiacs.count { it.celestialType == CelestialType.SKY }
        val earthCount = zodiacs.size - skyCount
        if (abs(skyCount - earthCount) > 2) {
            score *= 0.8
        }
        
        return score
    }
    
    // 分析五行平衡性
    private fun analyzeElementBalance(numbers: List<Int>): Double {
        var score = 1.0
        
        // 统计五行分布
        val elements = numbers.map { getElementForNumber(it) }
        val elementCounts = elements.groupingBy { it }.eachCount()
        
        // 检查五行是否齐全
        if (elementCounts.size < 4) {
            score *= 0.8  // 缺少某些五行，降低可信度
        }
        
        // 检查五行分布是否均衡
        val avgCount = elements.size / 5.0
        elementCounts.values.forEach { count ->
            if (abs(count - avgCount) > 1) {
                score *= 0.9
            }
        }
        
        return score
    }
    
    // 分析颜色分布合理性
    private fun analyzeColorDistribution(numbers: List<Int>): Double {
        var score = 1.0
        
        // 统计颜色分布
        val colors = numbers.map { getColorForNumber(it) }
        val colorCounts = colors.groupingBy { it }.eachCount()
        
        // 检查颜色是否齐全
        if (colorCounts.size < 3) {
            score *= 0.7  // 缺少某些颜色，降低可信度
        }
        
        // 检查颜色分布是否均衡
        val avgCount = colors.size / 3.0
        colorCounts.values.forEach { count ->
            if (abs(count - avgCount) > 1) {
                score *= 0.9
            }
        }
        
        return score
    }
    
    // 辅助方法：根据号码获取对应生肖
    private fun getZodiacForNumber(number: Int): Zodiac {
        return when (number) {
            in listOf(6, 18, 30, 42) -> Zodiac.RAT(
                celestialType = CelestialType.EARTH,
                yinYang = YinYang.YIN,
                season = Season.WINTER,
                direction = Direction.NORTH,
                gender = Gender.MALE,
                luck = Luck.BAD
            )
            in listOf(5, 17, 29, 41) -> Zodiac.OX(
                celestialType = CelestialType.EARTH,
                yinYang = YinYang.YANG,
                season = Season.WINTER,
                direction = Direction.NORTH,
                gender = Gender.MALE,
                luck = Luck.BAD
            )
            in listOf(4, 16, 28, 40) -> Zodiac.TIGER(
                celestialType = CelestialType.EARTH,
                yinYang = YinYang.YANG,
                season = Season.SPRING,
                direction = Direction.EAST,
                gender = Gender.MALE,
                luck = Luck.BAD
            )
            in listOf(3, 15, 27, 39) -> Zodiac.RABBIT(
                celestialType = CelestialType.SKY,
                yinYang = YinYang.YANG,
                season = Season.SPRING,
                direction = Direction.EAST,
                gender = Gender.FEMALE,
                luck = Luck.GOOD
            )
            in listOf(2, 14, 26, 38) -> Zodiac.DRAGON(
                celestialType = CelestialType.SKY,
                yinYang = YinYang.YIN,
                season = Season.SPRING,
                direction = Direction.EAST,
                gender = Gender.MALE,
                luck = Luck.GOOD
            )
            in listOf(1, 13, 25, 37, 49) -> Zodiac.SNAKE(
                celestialType = CelestialType.EARTH,
                yinYang = YinYang.YIN,
                season = Season.SUMMER,
                direction = Direction.SOUTH,
                gender = Gender.FEMALE,
                luck = Luck.GOOD
            )
            in listOf(12, 24, 36, 48) -> Zodiac.HORSE(
                celestialType = CelestialType.SKY,
                yinYang = YinYang.YIN,
                season = Season.SUMMER,
                direction = Direction.SOUTH,
                gender = Gender.MALE,
                luck = Luck.GOOD
            )
            in listOf(11, 23, 35, 47) -> Zodiac.GOAT(
                celestialType = CelestialType.EARTH,
                yinYang = YinYang.YANG,
                season = Season.SUMMER,
                direction = Direction.SOUTH,
                gender = Gender.FEMALE,
                luck = Luck.GOOD
            )
            in listOf(10, 22, 34, 46) -> Zodiac.MONKEY(
                celestialType = CelestialType.SKY,
                yinYang = YinYang.YANG,
                season = Season.AUTUMN,
                direction = Direction.WEST,
                gender = Gender.MALE,
                luck = Luck.BAD
            )
            in listOf(9, 21, 33, 45) -> Zodiac.ROOSTER(
                celestialType = CelestialType.EARTH,
                yinYang = YinYang.YANG,
                season = Season.AUTUMN,
                direction = Direction.WEST,
                gender = Gender.FEMALE,
                luck = Luck.GOOD
            )
            in listOf(8, 20, 32, 44) -> Zodiac.DOG(
                celestialType = CelestialType.EARTH,
                yinYang = YinYang.YIN,
                season = Season.AUTUMN,
                direction = Direction.WEST,
                gender = Gender.MALE,
                luck = Luck.BAD
            )
            in listOf(7, 19, 31, 43) -> Zodiac.PIG(
                celestialType = CelestialType.SKY,
                yinYang = YinYang.YIN,
                season = Season.WINTER,
                direction = Direction.NORTH,
                gender = Gender.FEMALE,
                luck = Luck.BAD
            )
            else -> throw IllegalArgumentException("Invalid number: $number")
        }
    }
    
    // 根据号码获取对应五行
    private fun getElementForNumber(number: Int): Element {
        return when (number) {
            in listOf(3, 4, 11, 12, 25, 26, 33, 34, 41, 42) -> Element.GOLD
            in listOf(7, 8, 15, 16, 23, 24, 37, 38, 45, 46) -> Element.WOOD
            in listOf(13, 14, 21, 22, 29, 30, 43, 44) -> Element.WATER
            in listOf(1, 2, 9, 10, 17, 18, 31, 32, 39, 40, 47, 48) -> Element.FIRE
            in listOf(5, 6, 19, 20, 27, 28, 35, 36, 49) -> Element.EARTH
            else -> throw IllegalArgumentException("Invalid number: $number")
        }
    }
    
    // 根据号码获取对应颜色
    private fun getColorForNumber(number: Int): Color {
        return when {
            // 单数红波
            number in listOf(1, 7, 13, 19, 23, 29, 35, 45) -> Color.RED
            // 双数红波
            number in listOf(2, 8, 12, 18, 24, 30, 34, 40, 46) -> Color.RED
            // 单数蓝波
            number in listOf(3, 9, 15, 25, 31, 37, 41, 47) -> Color.BLUE
            // 双数蓝波
            number in listOf(4, 10, 14, 20, 26, 36, 42, 48) -> Color.BLUE
            // 单数绿波
            number in listOf(5, 11, 17, 21, 27, 33, 39, 43, 49) -> Color.GREEN
            // 双数绿波
            number in listOf(6, 16, 22, 28, 32, 38, 44) -> Color.GREEN
            else -> throw IllegalArgumentException("Invalid number: $number")
        }
    }

    // 验证预测结果并更新权重
    suspend fun validateAndUpdateWeights(prediction: PredictionResult, actualResult: LotteryResult) {
        val validationResult = validator.validatePrediction(prediction, actualResult)
        
        // 根据验证结果调整权重
        adjustWeights(validationResult)
        
        // 记录验证结果
        logValidationResult(validationResult)
    }

    // 根据验证结果调整权重
    private fun adjustWeights(validationResult: PredictionValidator.ValidationResult) {
        val adjustmentFactor = when {
            validationResult.accuracy >= 0.5 -> 1.1  // 准确率较高，增加权重
            validationResult.accuracy >= 0.3 -> 1.0  // 准确率一般，保持权重
            else -> 0.9                              // 准确率较低，降低权重
        }
        
        // 根据属性匹配率调整具体维度的权重
        val details = validationResult.details
        
        weightConfig = weightConfig.copy(
            zodiacWeight = (weightConfig.zodiacWeight * 
                (if (details.zodiacMatches >= 4) 1.1 else 0.9)).coerceIn(0.1, 0.3),
            elementWeight = (weightConfig.elementWeight * 
                (if (details.elementMatches >= 4) 1.1 else 0.9)).coerceIn(0.1, 0.3),
            // ... 其他权重调整
        )
        
        // 归一化权重
        normalizeWeights()
    }

    // 记录验证结果
    private fun logValidationResult(result: PredictionValidator.ValidationResult) {
        Log.d(TAG, validator.generateValidationReport(result))
    }
}

// 预测结果数据类
data class PredictionResult(
    val numbers: List<Int>,        // 预测的普通号码
    val specialNumber: Int,        // 预测的特别号码
    val confidence: Double         // 预测可信度(0-1)
) 