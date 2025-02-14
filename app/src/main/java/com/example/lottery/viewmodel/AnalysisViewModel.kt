package com.example.lottery.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lottery.chart.ChartManager
import com.example.lottery.data.model.AnalysisData
import com.example.lottery.data.model.LotteryType
import com.example.lottery.data.repository.LotteryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val repository: LotteryRepository,
    private val chartManager: ChartManager
) : ViewModel() {

    private val _analysisData = MutableStateFlow<AnalysisData?>(null)
    val analysisData = _analysisData.asStateFlow()

    private val _selectedType = MutableStateFlow(LotteryType.MACAU)
    val selectedType = _selectedType.asStateFlow()

    init {
        loadAnalysisData()
    }

    fun setType(type: LotteryType) {
        _selectedType.value = type
        loadAnalysisData()
    }

    private fun loadAnalysisData() {
        viewModelScope.launch {
            repository.getAllResults(_selectedType.value)
                .collect { results ->
                    val trendData = chartManager.createTrendChart(results)
                    val distributionData = chartManager.createDistributionChart(results)
                    val patternData = chartManager.createPatternChart(results)

                    _analysisData.value = AnalysisData(
                        trendData = trendData,
                        distributionData = distributionData,
                        patternData = patternData,
                        statistics = calculateStatistics(results)
                    )
                }
        }
    }

    private fun calculateStatistics(results: List<LotteryResult>): Statistics {
        // TODO: 实现统计计算
        return Statistics(
            totalCount = results.size,
            oddEvenRatio = "",
            bigSmallRatio = "",
            sumRange = "",
            mostFrequentNumbers = emptyList(),
            leastFrequentNumbers = emptyList(),
            hotZodiac = "",
            hotElement = ""
        )
    }
} 