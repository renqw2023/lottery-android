package com.example.lottery.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lottery.chart.ChartManager
import com.example.lottery.data.model.LotteryResult
import com.example.lottery.data.repository.LotteryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResultDetailViewModel @Inject constructor(
    private val repository: LotteryRepository,
    private val chartManager: ChartManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val drawTime: Long = checkNotNull(savedStateHandle["drawTime"])

    // 开奖结果
    private val _result = MutableStateFlow<LotteryResult?>(null)
    val result = _result.asStateFlow()

    // 图表数据
    private val _chartType = MutableStateFlow(ChartType.TREND)
    val chartType = _chartType.asStateFlow()

    init {
        loadResult()
    }

    private fun loadResult() {
        viewModelScope.launch {
            try {
                val lotteryResult = repository.getResultByDrawTime(drawTime)
                _result.value = lotteryResult
            } catch (e: Exception) {
                // TODO: 处理错误
            }
        }
    }

    // 切换图表类型
    fun setChartType(type: ChartType) {
        _chartType.value = type
    }

    // 分析数据
    fun analyzeResult() {
        viewModelScope.launch {
            // TODO: 实现数据分析
        }
    }

    enum class ChartType {
        TREND,
        DISTRIBUTION,
        PATTERN
    }
} 