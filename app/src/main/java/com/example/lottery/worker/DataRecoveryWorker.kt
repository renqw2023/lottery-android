class DataRecoveryWorker @WorkerInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val dataCollector: DataCollector,
    private val database: LotteryDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(dispatcher) {
        try {
            // 1. 检查数据库状态
            val dbState = checkDatabaseState()
            if (dbState.isHealthy) {
                Log.d(TAG, "数据库状态正常，无需恢复")
                return@withContext Result.success()
            }
            
            // 2. 执行数据恢复
            val recoveryResult = recoverData(dbState)
            if (!recoveryResult.isSuccessful) {
                Log.e(TAG, "数据恢复失败: ${recoveryResult.error}")
                return@withContext Result.failure()
            }
            
            // 3. 验证恢复结果
            if (!validateRecovery(recoveryResult)) {
                Log.e(TAG, "恢复数据验证失败")
                return@withContext Result.failure()
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "数据恢复过程发生错误", e)
            Result.failure()
        }
    }
    
    // 检查数据库状态
    private suspend fun checkDatabaseState(): DatabaseState {
        return withContext(dispatcher) {
            try {
                val macauResults = database.lotteryDao().getAllResults(LotteryType.MACAU).first()
                val hkResults = database.lotteryDao().getAllResults(LotteryType.HONGKONG).first()
                
                DatabaseState(
                    isHealthy = checkDataHealth(macauResults, hkResults),
                    macauLastTime = macauResults.maxOfOrNull { it.drawTime } ?: 0,
                    hkLastTime = hkResults.maxOfOrNull { it.drawTime } ?: 0,
                    corruptedRanges = findCorruptedRanges(macauResults, hkResults),
                    missingData = findMissingData(macauResults, hkResults)
                )
            } catch (e: Exception) {
                DatabaseState(isHealthy = false, error = e)
            }
        }
    }
    
    // 检查数据健康状态
    private fun checkDataHealth(
        macauResults: List<LotteryResult>,
        hkResults: List<LotteryResult>
    ): Boolean {
        // 检查数据连续性
        if (!checkDataContinuity(macauResults) || !checkDataContinuity(hkResults)) {
            return false
        }
        
        // 检查数据完整性
        if (!checkDataIntegrity(macauResults) || !checkDataIntegrity(hkResults)) {
            return false
        }
        
        // 检查数据一致性
        if (!checkDataConsistency(macauResults) || !checkDataConsistency(hkResults)) {
            return false
        }
        
        return true
    }
    
    // 查找损坏的数据范围
    private fun findCorruptedRanges(
        macauResults: List<LotteryResult>,
        hkResults: List<LotteryResult>
    ): List<TimeRange> {
        val corruptedRanges = mutableListOf<TimeRange>()
        
        // 检查数据异常的时间范围
        fun checkResultsForCorruption(results: List<LotteryResult>): List<TimeRange> {
            val ranges = mutableListOf<TimeRange>()
            var corruptionStart: Long? = null
            
            results.zipWithNext { current, next ->
                if (!isValidSequence(current, next)) {
                    if (corruptionStart == null) {
                        corruptionStart = current.drawTime
                    }
                } else if (corruptionStart != null) {
                    ranges.add(TimeRange(corruptionStart!!, current.drawTime))
                    corruptionStart = null
                }
            }
            
            corruptionStart?.let {
                ranges.add(TimeRange(it, results.last().drawTime))
            }
            
            return ranges
        }
        
        corruptedRanges.addAll(checkResultsForCorruption(macauResults))
        corruptedRanges.addAll(checkResultsForCorruption(hkResults))
        
        return corruptedRanges.mergeSortedRanges()
    }
    
    // 查找缺失的数据
    private fun findMissingData(
        macauResults: List<LotteryResult>,
        hkResults: List<LotteryResult>
    ): List<TimeRange> {
        val missingRanges = mutableListOf<TimeRange>()
        
        // 检查数据缺失的时间范围
        fun checkResultsForMissing(results: List<LotteryResult>): List<TimeRange> {
            val ranges = mutableListOf<TimeRange>()
            var lastTime = results.firstOrNull()?.drawTime ?: return ranges
            
            results.drop(1).forEach { result ->
                val expectedTime = calculateNextExpectedTime(lastTime)
                if (result.drawTime > expectedTime + MAX_TIME_GAP) {
                    ranges.add(TimeRange(lastTime, result.drawTime))
                }
                lastTime = result.drawTime
            }
            
            return ranges
        }
        
        missingRanges.addAll(checkResultsForMissing(macauResults))
        missingRanges.addAll(checkResultsForMissing(hkResults))
        
        return missingRanges.mergeSortedRanges()
    }
    
    // 执行数据恢复
    private suspend fun recoverData(dbState: DatabaseState): RecoveryResult {
        return withContext(dispatcher) {
            try {
                // 1. 备份当前数据
                backupCurrentData()
                
                // 2. 清理损坏的数据
                cleanupCorruptedData(dbState.corruptedRanges)
                
                // 3. 重新获取缺失的数据
                val recoveredData = fetchMissingData(dbState.missingData)
                
                // 4. 保存恢复的数据
                saveRecoveredData(recoveredData)
                
                RecoveryResult(
                    isSuccessful = true,
                    recoveredRanges = dbState.corruptedRanges + dbState.missingData,
                    recoveredCount = recoveredData.size
                )
            } catch (e: Exception) {
                RecoveryResult(
                    isSuccessful = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }
    
    // 备份当前数据
    private suspend fun backupCurrentData() {
        val backupFile = File(applicationContext.filesDir, "lottery_backup_${System.currentTimeMillis()}.db")
        database.backup(backupFile)
    }
    
    // 清理损坏的数据
    private suspend fun cleanupCorruptedData(ranges: List<TimeRange>) {
        database.runInTransaction {
            ranges.forEach { range ->
                database.lotteryDao().deleteInRange(range.start, range.end)
            }
        }
    }
    
    // 获取缺失的数据
    private suspend fun fetchMissingData(ranges: List<TimeRange>): List<LotteryResult> {
        return coroutineScope {
            ranges.map { range ->
                async {
                    dataCollector.fetchResultsInRange(range.start, range.end)
                }
            }.awaitAll().flatten()
        }
    }
    
    // 保存恢复的数据
    private suspend fun saveRecoveredData(data: List<LotteryResult>) {
        database.runInTransaction {
            database.lotteryDao().insertAll(data)
        }
    }
    
    // 验证恢复结果
    private suspend fun validateRecovery(result: RecoveryResult): Boolean {
        if (!result.isSuccessful) return false
        
        return checkDatabaseState().isHealthy
    }
    
    data class DatabaseState(
        val isHealthy: Boolean,
        val macauLastTime: Long = 0,
        val hkLastTime: Long = 0,
        val corruptedRanges: List<TimeRange> = emptyList(),
        val missingData: List<TimeRange> = emptyList(),
        val error: Throwable? = null
    )
    
    data class TimeRange(
        val start: Long,
        val end: Long
    )
    
    data class RecoveryResult(
        val isSuccessful: Boolean,
        val recoveredRanges: List<TimeRange> = emptyList(),
        val recoveredCount: Int = 0,
        val error: String? = null
    )
    
    companion object {
        private const val TAG = "DataRecoveryWorker"
        private const val MAX_TIME_GAP = 24 * 60 * 60 * 1000L // 1天
    }
}

// 扩展函数：合并重叠的时间范围
private fun List<DataRecoveryWorker.TimeRange>.mergeSortedRanges(): List<DataRecoveryWorker.TimeRange> {
    if (isEmpty()) return emptyList()
    
    val sorted = sortedBy { it.start }
    val merged = mutableListOf(sorted.first())
    
    sorted.drop(1).forEach { range ->
        val last = merged.last()
        if (range.start <= last.end) {
            merged[merged.lastIndex] = DataRecoveryWorker.TimeRange(
                last.start,
                maxOf(last.end, range.end)
            )
        } else {
            merged.add(range)
        }
    }
    
    return merged
} 