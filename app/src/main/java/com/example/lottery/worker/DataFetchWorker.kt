class DataFetchWorker @WorkerInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val dataCollector: DataCollector,
    private val predictionEngine: PredictionEngine,
    private val database: LotteryDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : CoroutineWorker(context, workerParams) {

    // 缓存管理器
    private val cache = LRUCache<String, Any>(100)
    
    // 批处理大小
    private val BATCH_SIZE = 50

    override suspend fun doWork(): Result = withContext(dispatcher) {
        try {
            // 使用协程作用域进行并行处理
            coroutineScope {
                // 并行检查同步需求
                val shouldSyncDeferred = async { shouldSync() }
                
                // 同时预加载一些必要数据
                val preloadDeferred = async { preloadData() }
                
                if (!shouldSyncDeferred.await()) {
                    Log.d(TAG, "数据已是最新，无需同步")
                    return@coroutineScope Result.success()
                }
                
                // 等待预加载完成
                preloadDeferred.await()
                
                // 执行同步
                val syncResult = synchronizeData()
                if (!syncResult.isSuccessful) {
                    return@coroutineScope handleError(syncResult.error!!, runAttemptCount)
                }
                
                // 并行执行验证和更新
                val validationJob = async { validateDataConsistency() }
                val predictionJob = async { updatePredictions() }
                
                // 等待所有任务完成
                if (!validationJob.await()) {
                    return@coroutineScope handleError(
                        SyncError.ValidationError("数据一致性检查失败"),
                        runAttemptCount
                    )
                }
                
                // 发送通知
                if (syncResult.hasNewData) {
                    withContext(Dispatchers.Main) {
                        notifyNewResults(syncResult.macauResult, syncResult.hkResult)
                    }
                }
                
                Result.success()
            }
        } catch (e: Exception) {
            handleError(SyncError.UnknownError(e.message ?: "未知错误"), runAttemptCount)
        }
    }
    
    // 预加载数据
    private suspend fun preloadData() {
        withContext(dispatcher) {
            launch { preloadLatestResults() }
            launch { preloadPredictionModels() }
        }
    }
    
    // 预加载最新结果
    private suspend fun preloadLatestResults() {
        val key = "latest_results"
        if (!cache.contains(key)) {
            val results = database.lotteryDao().getLatestResults()
            cache.put(key, results)
        }
    }
    
    // 预加载预测模型
    private suspend fun preloadPredictionModels() {
        val key = "prediction_models"
        if (!cache.contains(key)) {
            val models = predictionEngine.loadModels()
            cache.put(key, models)
        }
    }
    
    // 优化的数据同步方法
    private suspend fun synchronizeData(): SyncResult = withContext(dispatcher) {
        try {
            // 并行获取澳门和香港数据
            val (macauResults, hkResults) = coroutineScope {
                val macauDeferred = async { fetchMacauData() }
                val hkDeferred = async { fetchHKData() }
                Pair(macauDeferred.await(), hkDeferred.await())
            }
            
            // 批量保存数据
            saveBatchData(macauResults, hkResults)
            
            SyncResult(
                isSuccessful = true,
                hasNewData = macauResults?.isNotEmpty() == true || hkResults?.isNotEmpty() == true,
                macauResult = macauResults?.lastOrNull(),
                hkResult = hkResults?.lastOrNull()
            )
        } catch (e: Exception) {
            SyncResult(
                isSuccessful = false,
                error = SyncError.UnknownError("同步过程发生未知错误: ${e.message}")
            )
        }
    }
    
    // 批量保存数据
    private suspend fun saveBatchData(
        macauResults: List<LotteryResult>?,
        hkResults: List<LotteryResult>?
    ) {
        withContext(dispatcher) {
            database.runInTransaction {
                macauResults?.chunked(BATCH_SIZE)?.forEach { batch ->
                    database.lotteryDao().insertAll(batch)
                }
                
                hkResults?.chunked(BATCH_SIZE)?.forEach { batch ->
                    database.lotteryDao().insertAll(batch)
                }
            }
        }
    }
    
    // 优化的数据验证方法
    private suspend fun validateDataConsistency(): Boolean = withContext(dispatcher) {
        coroutineScope {
            // 并行执行各项验证
            val continuityCheck = async { checkDataContinuity() }
            val integrityCheck = async { checkDataIntegrity() }
            val validityCheck = async { checkDataValidity() }
            
            // 等待所有验证完成
            continuityCheck.await() && integrityCheck.await() && validityCheck.await()
        }
    }
    
    // LRU缓存实现
    private class LRUCache<K, V>(private val maxSize: Int) {
        private val cache = LinkedHashMap<K, V>(maxSize, 0.75f, true)
        
        @Synchronized
        fun put(key: K, value: V) {
            if (cache.size >= maxSize) {
                cache.remove(cache.keys.first())
            }
            cache[key] = value
        }
        
        @Synchronized
        fun get(key: K): V? = cache[key]
        
        @Synchronized
        fun contains(key: K): Boolean = cache.containsKey(key)
        
        @Synchronized
        fun clear() = cache.clear()
    }
    
    // 检查是否需要同步
    private suspend fun shouldSync(): Boolean {
        // 获取本地最新数据时间
        val localMacauTime = database.lotteryDao().getLatestResult(LotteryType.MACAU)?.drawTime ?: 0
        val localHKTime = database.lotteryDao().getLatestResult(LotteryType.HONGKONG)?.drawTime ?: 0
        
        // 获取远程最新数据时间
        val remoteMacauTime = dataCollector.getLatestMacauDrawTime()
        val remoteHKTime = dataCollector.getLatestHKDrawTime()
        
        // 检查是否有新数据
        return remoteMacauTime > localMacauTime || remoteHKTime > localHKTime
    }
    
    // 检查数据连续性
    private suspend fun checkDataContinuity(): Boolean {
        fun checkContinuity(results: List<LotteryResult>): Boolean {
            if (results.size < 2) return true
            
            // 检查时间间隔是否合理
            for (i in 1 until results.size) {
                val timeDiff = results[i].drawTime - results[i-1].drawTime
                if (timeDiff > MAX_TIME_GAP) {
                    Log.e(TAG, "发现数据间隔异常: ${results[i-1].drawTime} -> ${results[i].drawTime}")
                    return false
                }
            }
            return true
        }
        
        val macauResults = database.lotteryDao().getAllResults(LotteryType.MACAU).first()
        val hkResults = database.lotteryDao().getAllResults(LotteryType.HONGKONG).first()
        
        return checkContinuity(macauResults) && checkContinuity(hkResults)
    }
    
    // 检查数据完整性
    private suspend fun checkDataIntegrity(): Boolean {
        fun checkResultIntegrity(result: LotteryResult): Boolean {
            // 检查号码数量
            if (result.numbers.size != 6) return false
            
            // 检查号码范围
            if (result.numbers.any { it !in 1..49 }) return false
            if (result.specialNumber !in 1..49) return false
            
            // 检查号码唯一性
            val allNumbers = result.numbers + result.specialNumber
            return allNumbers.toSet().size == 7
        }
        
        val macauResults = database.lotteryDao().getAllResults(LotteryType.MACAU).first()
        val hkResults = database.lotteryDao().getAllResults(LotteryType.HONGKONG).first()
        
        return macauResults.all { checkResultIntegrity(it) } && 
               hkResults.all { checkResultIntegrity(it) }
    }
    
    // 检查数据有效性
    private suspend fun checkDataValidity(): Boolean {
        fun checkResultValidity(result: LotteryResult): Boolean {
            // 检查开奖时间是否合理
            val now = System.currentTimeMillis()
            if (result.drawTime > now) return false
            
            // 检查开奖时间是否在合理范围内
            if (now - result.drawTime > MAX_RESULT_AGE) return false
            
            return true
        }
        
        val macauResults = database.lotteryDao().getAllResults(LotteryType.MACAU).first()
        val hkResults = database.lotteryDao().getAllResults(LotteryType.HONGKONG).first()
        
        return macauResults.all { checkResultValidity(it) } && 
               hkResults.all { checkResultValidity(it) }
    }
    
    // 更新预测
    private suspend fun updatePredictions() {
        // 1. 验证上一次预测
        validatePreviousPrediction()
        
        // 2. 生成新的预测
        generateNewPrediction()
        
        // 3. 清理过期预测
        cleanupOldPredictions()
    }
    
    // 清理过期预测
    private suspend fun cleanupOldPredictions() {
        val cutoffTime = System.currentTimeMillis() - PREDICTION_RETENTION_PERIOD
        database.predictionDao().deleteOldPredictions(cutoffTime)
    }
    
    private suspend fun validatePreviousPrediction() {
        // 获取上一次的预测结果
        val previousPredictions = database.predictionDao().getLatestPredictions()
        
        // 获取实际开奖结果
        val macauResult = database.lotteryDao().getLatestResult(LotteryType.MACAU)
        val hkResult = database.lotteryDao().getLatestResult(LotteryType.HONGKONG)
        
        // 验证预测结果
        previousPredictions.forEach { prediction ->
            val actualResult = when (prediction.type) {
                LotteryType.MACAU -> macauResult
                LotteryType.HONGKONG -> hkResult
            }
            
            actualResult?.let {
                predictionEngine.validateAndUpdateWeights(prediction, it)
            }
        }
    }
    
    private suspend fun generateNewPrediction() {
        // 生成新的预测
        val macauPrediction = predictionEngine.predictNextDraw(LotteryType.MACAU)
        val hkPrediction = predictionEngine.predictNextDraw(LotteryType.HONGKONG)
        
        // 保存预测结果
        database.predictionDao().apply {
            insert(macauPrediction)
            insert(hkPrediction)
        }
    }
    
    private fun notifyNewResults(macauResult: LotteryResult?, hkResult: LotteryResult?) {
        val context = applicationContext
        
        // 创建通知渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "开奖结果通知",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "显示最新开奖结果和预测"
            }
            
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
        
        // 构建通知内容
        val contentText = buildString {
            macauResult?.let {
                append("澳门最新开奖: ")
                append(it.numbers.joinToString(","))
                append(" + ")
                append(it.specialNumber)
                append("\n")
            }
            
            hkResult?.let {
                append("香港最新开奖: ")
                append(it.numbers.joinToString(","))
                append(" + ")
                append(it.specialNumber)
            }
        }
        
        // 创建通知
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("开奖结果更新")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        // 显示通知
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
    
    // 错误类型枚举
    sealed class SyncError(val message: String) {
        class NetworkError(message: String) : SyncError(message)
        class DataError(message: String) : SyncError(message)
        class DatabaseError(message: String) : SyncError(message)
        class ValidationError(message: String) : SyncError(message)
        class UnknownError(message: String) : SyncError(message)
    }
    
    // 更新SyncResult类
    data class SyncResult(
        val isSuccessful: Boolean,
        val hasNewData: Boolean = false,
        val macauResult: LotteryResult? = null,
        val hkResult: LotteryResult? = null,
        val error: SyncError? = null,
        val retryAttempt: Int = 0
    )
    
    // 重试策略配置
    private object RetryConfig {
        const val MAX_RETRIES = 3
        val BACKOFF_TIMES = listOf(30_000L, 60_000L, 300_000L) // 30秒, 1分钟, 5分钟
        
        fun getBackoffTime(attempt: Int): Long {
            return BACKOFF_TIMES.getOrElse(attempt - 1) { BACKOFF_TIMES.last() }
        }
    }
    
    // 错误处理方法
    private fun handleError(error: SyncError, retryCount: Int): Result {
        Log.e(TAG, "同步错误: ${error.message}, 重试次数: $retryCount")
        
        // 记录错误日志
        logError(error, retryCount)
        
        return when (error) {
            is SyncError.NetworkError -> {
                if (retryCount < RetryConfig.MAX_RETRIES) {
                    Result.retry()
                } else {
                    handleDataError(error)
                }
            }
            is SyncError.DataError -> {
                if (retryCount < RetryConfig.MAX_RETRIES) {
                    Result.retry()
                } else {
                    handleDataError(error)
                }
            }
            is SyncError.DatabaseError -> {
                notifyDatabaseError(error)
                handleDataError(error)
            }
            is SyncError.ValidationError -> {
                if (retryCount < RetryConfig.MAX_RETRIES) {
                    Result.retry()
                } else {
                    handleDataError(error)
                }
            }
            is SyncError.UnknownError -> {
                notifyUnknownError(error)
                handleDataError(error)
            }
        }
    }
    
    // 记录错误日志
    private fun logError(error: SyncError, retryCount: Int) {
        val errorLog = ErrorLog(
            timestamp = System.currentTimeMillis(),
            errorType = error::class.java.simpleName,
            message = error.message,
            retryCount = retryCount
        )
        
        lifecycleScope.launch {
            try {
                database.errorLogDao().insert(errorLog)
            } catch (e: Exception) {
                Log.e(TAG, "错误日志记录失败", e)
            }
        }
    }
    
    // 通知数据库错误
    private fun notifyDatabaseError(error: SyncError.DatabaseError) {
        val notification = NotificationCompat.Builder(applicationContext, ERROR_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_error)
            .setContentTitle("数据库错误")
            .setContentText(error.message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(applicationContext)
            .notify(ERROR_NOTIFICATION_ID, notification)
    }
    
    // 通知未知错误
    private fun notifyUnknownError(error: SyncError.UnknownError) {
        val notification = NotificationCompat.Builder(applicationContext, ERROR_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_error)
            .setContentTitle("同步错误")
            .setContentText(error.message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(applicationContext)
            .notify(ERROR_NOTIFICATION_ID, notification)
    }
    
    private suspend fun handleDataError(error: SyncError): Result {
        // 启动数据恢复工作
        val recoveryRequest = OneTimeWorkRequestBuilder<DataRecoveryWorker>()
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        
        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork(
                "DataRecovery",
                ExistingWorkPolicy.REPLACE,
                recoveryRequest
            )
        
        return Result.failure()
    }
    
    companion object {
        private const val TAG = "DataFetchWorker"
        private const val CHANNEL_ID = "lottery_results"
        private const val ERROR_CHANNEL_ID = "lottery_errors"
        private const val NOTIFICATION_ID = 1
        private const val ERROR_NOTIFICATION_ID = 2
        
        // 配置常量
        private const val MAX_TIME_GAP = 7 * 24 * 60 * 60 * 1000L // 7天
        private const val MAX_RESULT_AGE = 365 * 24 * 60 * 60 * 1000L // 1年
        private const val PREDICTION_RETENTION_PERIOD = 30 * 24 * 60 * 60 * 1000L // 30天
        
        // 性能监控相关常量
        private const val PERFORMANCE_THRESHOLD = 1000L // 1秒
        private const val MEMORY_THRESHOLD = 50 * 1024 * 1024L // 50MB
    }
} 