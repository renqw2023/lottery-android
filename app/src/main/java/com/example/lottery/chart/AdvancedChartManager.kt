class AdvancedChartManager @Inject constructor() {
    // 添加更多图表类型
    // 实现交互式图表
    // 支持数据钻取

    // 生成趋势图数据
    suspend fun generateTrendData(results: List<LotteryResult>): ChartData.TrendData = withContext(Dispatchers.Default) {
        val entries = mutableListOf<Entry>()
        val labels = mutableListOf<String>()
        
        // 计算号码出现频率趋势
        results.forEachIndexed { index, result ->
            val numbers = result.numbers + result.specialNumber
            numbers.forEach { number ->
                entries.add(Entry(index.toFloat(), number.toFloat()))
            }
            labels.add(formatDate(result.drawTime, "MM-dd"))
        }
        
        ChartData.TrendData(
            entries = entries,
            labels = labels,
            title = "号码走势图"
        )
    }

    // 生成分布图数据
    suspend fun generateDistributionData(results: List<LotteryResult>): ChartData.DistributionData = withContext(Dispatchers.Default) {
        // 统计号码频率
        val frequencyMap = mutableMapOf<Int, Int>()
        results.forEach { result ->
            (result.numbers + result.specialNumber).forEach { number ->
                frequencyMap[number] = (frequencyMap[number] ?: 0) + 1
            }
        }
        
        // 生成饼图数据
        val entries = frequencyMap.map { (number, count) ->
            PieEntry(count.toFloat(), number.toString())
        }
        
        ChartData.DistributionData(
            entries = entries,
            title = "号码分布图",
            centerText = "总计\n${results.size}期"
        )
    }

    // 生成规律图数据
    suspend fun generatePatternData(results: List<LotteryResult>): ChartData.PatternData = withContext(Dispatchers.Default) {
        // 分析各种属性的规律
        val zodiacStats = analyzeZodiacPattern(results)
        val elementStats = analyzeElementPattern(results)
        val numberStats = analyzeNumberPattern(results)
        
        ChartData.PatternData(
            zodiacData = zodiacStats,
            elementData = elementStats,
            numberData = numberStats,
            title = "开奖规律分析"
        )
    }

    // 分析生肖规律
    private fun analyzeZodiacPattern(results: List<LotteryResult>): List<RadarEntry> {
        val zodiacCount = mutableMapOf<String, Int>()
        results.forEach { result ->
            result.zodiac?.let { zodiac ->
                zodiacCount[zodiac] = (zodiacCount[zodiac] ?: 0) + 1
            }
        }
        
        return zodiacCount.map { (zodiac, count) ->
            RadarEntry(count.toFloat(), zodiac)
        }
    }

    // 分析五行规律
    private fun analyzeElementPattern(results: List<LotteryResult>): List<BarEntry> {
        val elementCount = mutableMapOf<String, Int>()
        results.forEach { result ->
            result.element?.let { element ->
                elementCount[element] = (elementCount[element] ?: 0) + 1
            }
        }
        
        return elementCount.entries.mapIndexed { index, (_, count) ->
            BarEntry(index.toFloat(), count.toFloat())
        }
    }

    // 分析号码规律
    private fun analyzeNumberPattern(results: List<LotteryResult>): List<Entry> {
        // 计算号码间隔
        val intervals = mutableListOf<Int>()
        results.zipWithNext { current, next ->
            val currentNumbers = current.numbers.toSet()
            val nextNumbers = next.numbers.toSet()
            val commonNumbers = currentNumbers.intersect(nextNumbers)
            intervals.add(commonNumbers.size)
        }
        
        return intervals.mapIndexed { index, interval ->
            Entry(index.toFloat(), interval.toFloat())
        }
    }

    // 生成热图数据
    suspend fun generateHeatMapData(results: List<LotteryResult>): ChartData.HeatMapData = withContext(Dispatchers.Default) {
        val heatData = Array(7) { Array(7) { 0 } }
        
        // 统计号码相邻出现的频率
        results.forEach { result ->
            val numbers = result.numbers.sorted()
            numbers.zipWithNext { a, b ->
                val row = (a - 1) / 7
                val col = (b - 1) / 7
                heatData[row][col]++
            }
        }
        
        ChartData.HeatMapData(
            data = heatData,
            title = "号码相关性热图"
        )
    }

    // 生成组合分析图
    suspend fun generateCombinationData(results: List<LotteryResult>): ChartData.CombinationData = withContext(Dispatchers.Default) {
        // 分析号码组合特征
        val combinations = analyzeCombinations(results)
        val patterns = analyzePatterns(results)
        val correlations = analyzeCorrelations(results)
        
        ChartData.CombinationData(
            combinations = combinations,
            patterns = patterns,
            correlations = correlations,
            title = "号码组合分析"
        )
    }

    // 分析号码组合
    private fun analyzeCombinations(results: List<LotteryResult>): List<CombinationEntry> {
        val combinationStats = mutableMapOf<String, Int>()
        
        results.forEach { result ->
            // 分析连号
            val consecutive = findConsecutiveNumbers(result.numbers)
            // 分析重复号
            val repeated = findRepeatedNumbers(result.numbers)
            // 分析间隔号
            val intervals = findIntervalNumbers(result.numbers)
            
            combinationStats["连号"] = (combinationStats["连号"] ?: 0) + consecutive.size
            combinationStats["重复号"] = (combinationStats["重复号"] ?: 0) + repeated.size
            combinationStats["间隔号"] = (combinationStats["间隔号"] ?: 0) + intervals.size
        }
        
        return combinationStats.map { (type, count) ->
            CombinationEntry(type, count)
        }
    }

    // 查找连续号码
    private fun findConsecutiveNumbers(numbers: List<Int>): List<List<Int>> {
        val consecutive = mutableListOf<List<Int>>()
        val sorted = numbers.sorted()
        var current = mutableListOf<Int>()
        
        sorted.forEach { number ->
            if (current.isEmpty() || number == current.last() + 1) {
                current.add(number)
            } else {
                if (current.size >= 2) {
                    consecutive.add(current.toList())
                }
                current = mutableListOf(number)
            }
        }
        
        if (current.size >= 2) {
            consecutive.add(current)
        }
        
        return consecutive
    }

    // 查找重复号码模式
    private fun findRepeatedNumbers(numbers: List<Int>): List<Int> {
        val frequency = numbers.groupBy { it }
        return frequency.filter { it.value.size > 1 }.keys.toList()
    }

    // 查找间隔号码
    private fun findIntervalNumbers(numbers: List<Int>): List<Pair<Int, Int>> {
        val intervals = mutableListOf<Pair<Int, Int>>()
        val sorted = numbers.sorted()
        
        sorted.zipWithNext { a, b ->
            if (b - a > 1) {
                intervals.add(a to b)
            }
        }
        
        return intervals
    }
}

// 图表数据类
sealed class ChartData {
    data class TrendData(
        val entries: List<Entry>,
        val labels: List<String>,
        val title: String
    ) : ChartData()
    
    data class DistributionData(
        val entries: List<PieEntry>,
        val title: String,
        val centerText: String
    ) : ChartData()
    
    data class PatternData(
        val zodiacData: List<RadarEntry>,
        val elementData: List<BarEntry>,
        val numberData: List<Entry>,
        val title: String
    ) : ChartData()
    
    data class HeatMapData(
        val data: Array<Array<Int>>,
        val title: String
    ) : ChartData()
    
    data class CombinationData(
        val combinations: List<CombinationEntry>,
        val patterns: List<PatternEntry>,
        val correlations: List<CorrelationEntry>,
        val title: String
    ) : ChartData()
}

// 组合分析数据类
data class CombinationEntry(
    val type: String,
    val count: Int
)

data class PatternEntry(
    val pattern: String,
    val frequency: Int
)

data class CorrelationEntry(
    val number1: Int,
    val number2: Int,
    val correlation: Float
) 