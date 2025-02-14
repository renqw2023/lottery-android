package com.example.lottery.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lottery.data.model.LotteryResult
import com.example.lottery.data.model.LotteryType
import com.example.lottery.data.repository.LotteryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryQueryViewModel @Inject constructor(
    private val repository: LotteryRepository
) : ViewModel() {

    // 当前选中的类型
    private val _selectedType = MutableStateFlow(LotteryType.MACAU)
    val selectedType = _selectedType.asStateFlow()

    // 历史结果
    val historyResults = _selectedType
        .flatMapLatest { type ->
            repository.getAllResults(type)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 切换类型
    fun setType(type: LotteryType) {
        _selectedType.value = type
    }

    // 导入历史数据
    fun importHistoricalData() {
        viewModelScope.launch {
            try {
                repository.importHistoricalData()
            } catch (e: Exception) {
                // TODO: 处理错误
            }
        }
    }
} 