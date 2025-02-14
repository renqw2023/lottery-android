package com.example.lottery.data.repository

import com.example.lottery.data.DataCollector
import com.example.lottery.data.dao.LotteryDao
import com.example.lottery.data.model.LotteryResult
import com.example.lottery.data.model.LotteryType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LotteryRepository @Inject constructor(
    private val lotteryDao: LotteryDao,
    private val dataCollector: DataCollector
) {
    // 获取所有结果
    fun getAllResults(type: LotteryType): Flow<List<LotteryResult>> = 
        lotteryDao.getAllResults(type)

    // 获取最新结果
    suspend fun getLatestResult(type: LotteryType): LotteryResult? =
        lotteryDao.getLatestResult(type)

    // 获取指定开奖结果
    suspend fun getResultByDrawTime(drawTime: Long): LotteryResult? =
        lotteryDao.getResultByDrawTime(drawTime)

    // 刷新数据
    suspend fun refreshData() {
        dataCollector.collectData()
    }

    // 导入历史数据
    suspend fun importHistoricalData() {
        dataCollector.importHistoricalData()
    }
} 