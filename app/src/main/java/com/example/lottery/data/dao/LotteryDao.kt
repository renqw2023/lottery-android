package com.example.lottery.data.dao

import androidx.room.*
import com.example.lottery.data.model.LotteryResult
import com.example.lottery.data.model.LotteryType
import kotlinx.coroutines.flow.Flow

@Dao
interface LotteryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: LotteryResult)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(results: List<LotteryResult>)

    @Query("SELECT * FROM lottery_results WHERE type = :type ORDER BY drawTime DESC")
    fun getAllResults(type: LotteryType): Flow<List<LotteryResult>>

    @Query("SELECT * FROM lottery_results WHERE type = :type ORDER BY drawTime DESC LIMIT 1")
    suspend fun getLatestResult(type: LotteryType): LotteryResult?

    @Query("SELECT * FROM lottery_results WHERE drawTime = :drawTime")
    suspend fun getResultByDrawTime(drawTime: Long): LotteryResult?

    @Query("SELECT * FROM lottery_results WHERE type = :type AND drawTime > :since ORDER BY drawTime ASC")
    suspend fun getResultsSince(type: LotteryType, since: Long): List<LotteryResult>

    @Query("DELETE FROM lottery_results WHERE type = :type")
    suspend fun deleteAllResults(type: LotteryType)

    @Query("""
        SELECT * FROM lottery_results 
        WHERE drawTime BETWEEN :startDate AND :endDate
        AND (:zodiac IS NULL OR zodiac = :zodiac)
        AND (:element IS NULL OR element = :element)
        ORDER BY drawTime DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun queryResults(
        startDate: Long,
        endDate: Long,
        numberRange: Pair<Int, Int>,
        specialNumbers: List<Int>,
        zodiac: String?,
        element: String?,
        attributes: List<String>,
        offset: Int,
        limit: Int
    ): List<LotteryResult>

    @Query("""
        SELECT number, COUNT(*) as frequency
        FROM (
            SELECT number
            FROM lottery_results
            WHERE drawTime BETWEEN :startDate AND :endDate
            CROSS JOIN json_each(numbers) AS numbers(number)
            UNION ALL
            SELECT specialNumber as number
            FROM lottery_results
            WHERE drawTime BETWEEN :startDate AND :endDate
        )
        GROUP BY number
        ORDER BY frequency DESC
    """)
    suspend fun getNumberFrequency(startDate: Long, endDate: Long): List<NumberFrequency>

    @Query("""
        SELECT attribute, COUNT(*) as count
        FROM lottery_results
        WHERE drawTime BETWEEN :startDate AND :endDate
        GROUP BY attribute
    """)
    suspend fun getAttributeDistribution(
        startDate: Long,
        endDate: Long,
        attributeType: String
    ): List<AttributeCount>
}

data class NumberFrequency(
    val number: Int,
    val frequency: Int
)

data class AttributeCount(
    val attribute: String,
    val count: Int
) 