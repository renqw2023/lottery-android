package com.example.lottery.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextExtractor
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.system.measureTimeMillis
import com.example.lottery.data.model.LotteryResult
import com.example.lottery.data.database.LotteryDatabase
import javax.inject.Inject
import javax.inject.Singleton
import com.example.lottery.data.model.LotteryType

@Singleton
class DataCollector @Inject constructor(
    private val database: LotteryDatabase,
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    // 添加缓存
    private val cache = LRUCache<String, Any>(100)
    
    // 添加并发支持
    suspend fun fetchLatestResults() = withContext(dispatcher) {
        coroutineScope {
            val macauDeferred = async { fetchMacauData() }
            val hkDeferred = async { fetchHKData() }
            
            try {
                val macauResult = macauDeferred.await()
                val hkResult = hkDeferred.await()
                
                // 批量保存
                database.runInTransaction {
                    macauResult?.let { saveMacaoResult(it) }
                    hkResult?.let { saveHKResult(it) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching lottery results", e)
                throw e
            }
        }
    }
    
    // 优化数据解析
    private fun parseMacaoResult(doc: Document): LotteryResult {
        return measureTimeMillis("parse_macau") {
            // 使用CSS选择器优化选择
            val numbers = doc.select(".lottery-numbers .number")
                .mapNotNull { it.text().toIntOrNull() }
            
            val drawTime = doc.select(".draw-time").firstOrNull()
                ?.text()
                ?.let { parseDrawTime(it) }
                ?: System.currentTimeMillis()
                
            LotteryResult(
                type = LotteryType.MACAU,
                drawTime = drawTime,
                numbers = numbers.take(6),
                specialNumber = numbers.lastOrNull() ?: 0
            )
        }
    }
    
    // 性能监控工具
    private inline fun <T> measureTimeMillis(
        tag: String,
        block: () -> T
    ): T {
        val start = System.nanoTime()
        val result = block()
        val end = System.nanoTime()
        val duration = (end - start) / 1_000_000 // 转换为毫秒
        
        if (duration > PERFORMANCE_THRESHOLD) {
            Log.w(TAG, "Performance warning: $tag took $duration ms")
        }
        
        return result
    }

    // 解析澳门开奖结果
    private fun parseMacaoResult(doc: Document): LotteryResult {
        val numbers = doc.select(".lottery-numbers .number").map { 
            it.text().toInt() 
        }
        val drawTime = doc.select(".draw-time").first()?.text()?.let {
            parseDrawTime(it)
        } ?: System.currentTimeMillis()

        return LotteryResult(
            type = LotteryType.MACAU,
            drawTime = drawTime,
            numbers = numbers.take(6),
            specialNumber = numbers.last()
        )
    }

    // 解析香港开奖结果
    private fun parseHKResult(doc: Document): LotteryResult {
        val numbers = doc.select(".mark-six-number").map {
            it.text().toInt()
        }
        val drawTime = doc.select(".draw-date").first()?.text()?.let {
            parseDrawTime(it)
        } ?: System.currentTimeMillis()

        return LotteryResult(
            type = LotteryType.HONGKONG,
            drawTime = drawTime,
            numbers = numbers.take(6),
            specialNumber = numbers.last()
        )
    }

    // 导入历史数据
    suspend fun importHistoricalData() = withContext(Dispatchers.IO) {
        try {
            // 导入澳门历史数据
            val macaoHistory = readMacaoExcel()
            saveMacaoHistory(macaoHistory)

            // 导入香港历史数据
            val hkHistory = readHKPdf()
            saveHKHistory(hkHistory)
        } catch (e: Exception) {
            Log.e(TAG, "Error importing historical data", e)
            throw e
        }
    }

    // 读取澳门历史Excel文件
    private fun readMacaoExcel(): List<LotteryResult> {
        val inputStream = context.assets.open("澳门历史开奖数据.xlsx")
        val workbook = WorkbookFactory.create(inputStream)
        val sheet = workbook.getSheetAt(0)
        
        return sheet.map { row ->
            LotteryResult(
                type = LotteryType.MACAU,
                drawTime = row.getCell(0).dateCellValue.time,
                numbers = (1..6).map { 
                    row.getCell(it).numericCellValue.toInt() 
                },
                specialNumber = row.getCell(7).numericCellValue.toInt()
            )
        }
    }

    // 读取香港历史PDF文件
    private fun readHKPdf(): List<LotteryResult> {
        val inputStream = context.assets.open("香港历史开奖数据.pdf")
        val reader = PdfReader(inputStream)
        val results = mutableListOf<LotteryResult>()

        for (i in 1..reader.numberOfPages) {
            val page = reader.getPage(i)
            val text = PdfTextExtractor.getTextFromPage(page)
            
            // 使用正则表达式解析每行数据
            val pattern = """(\d{2}/\d{2}/\d{4})\s+(\d{2})\s+(\d{2})\s+(\d{2})\s+(\d{2})\s+(\d{2})\s+(\d{2})\s+\[(\d{2})\]""".toRegex()
            
            pattern.findAll(text).forEach { match ->
                val drawTime = SimpleDateFormat("dd/MM/yyyy", Locale.US)
                    .parse(match.groupValues[1])?.time ?: 0L
                    
                results.add(LotteryResult(
                    type = LotteryType.HONGKONG,
                    drawTime = drawTime,
                    numbers = (2..7).map { 
                        match.groupValues[it].toInt() 
                    },
                    specialNumber = match.groupValues[8].toInt()
                ))
            }
        }
        return results
    }

    // 保存结果到数据库
    private suspend fun saveMacaoResult(result: LotteryResult) {
        database.lotteryDao().insert(result)
    }

    private suspend fun saveHKResult(result: LotteryResult) {
        database.lotteryDao().insert(result)
    }

    private suspend fun saveMacaoHistory(results: List<LotteryResult>) {
        database.lotteryDao().insertAll(results)
    }

    private suspend fun saveHKHistory(results: List<LotteryResult>) {
        database.lotteryDao().insertAll(results)
    }

    private fun parseDrawTime(timeStr: String): Long {
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .parse(timeStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    // 获取最新开奖时间
    suspend fun getLatestMacauDrawTime(): Long {
        return withContext(Dispatchers.IO) {
            try {
                // 获取最新开奖时间的逻辑
                // ...
            } catch (e: Exception) {
                Log.e(TAG, "获取澳门最新开奖时间失败", e)
                0L
            }
        }
    }
    
    suspend fun getLatestHKDrawTime(): Long {
        return withContext(Dispatchers.IO) {
            try {
                // 获取最新开奖时间的逻辑
                // ...
            } catch (e: Exception) {
                Log.e(TAG, "获取香港最新开奖时间失败", e)
                0L
            }
        }
    }
    
    // 获取增量数据
    suspend fun fetchMacauResults(since: Long): List<LotteryResult>? {
        return withContext(Dispatchers.IO) {
            try {
                // 获取指定时间之后的澳门开奖结果
                // ...
            } catch (e: Exception) {
                Log.e(TAG, "获取澳门开奖结果失败", e)
                null
            }
        }
    }
    
    suspend fun fetchHKResults(since: Long): List<LotteryResult>? {
        return withContext(Dispatchers.IO) {
            try {
                // 获取指定时间之后的香港开奖结果
                // ...
            } catch (e: Exception) {
                Log.e(TAG, "获取香港开奖结果失败", e)
                null
            }
        }
    }

    suspend fun collectData() = withContext(Dispatchers.IO) {
        try {
            val results = fetchLatestResults()
            saveResults(results)
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting data", e)
        }
    }

    private suspend fun saveResults(results: List<LotteryResult>) {
        try {
            database.lotteryDao().insertAll(results)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving results", e)
        }
    }

    companion object {
        private const val TAG = "DataCollector"
        private const val PERFORMANCE_THRESHOLD = 500L // 500毫秒
    }
} 