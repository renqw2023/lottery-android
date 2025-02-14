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
class MainViewModel @Inject constructor(
    private val repository: LotteryRepository
) : ViewModel() {

    // UI状态
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState = _uiState.asStateFlow()

    // 最新开奖结果
    private val _latestResults = MutableStateFlow<Map<LotteryType, LotteryResult?>>(emptyMap())
    val latestResults = _latestResults.asStateFlow()

    init {
        loadLatestResults()
    }

    // 加载最新结果
    private fun loadLatestResults() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val macauResult = repository.getLatestResult(LotteryType.MACAU)
                val hkResult = repository.getLatestResult(LotteryType.HONGKONG)
                
                _latestResults.value = mapOf(
                    LotteryType.MACAU to macauResult,
                    LotteryType.HONGKONG to hkResult
                )
                _uiState.value = UiState.Success
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    // 刷新数据
    fun refreshData() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                repository.refreshData()
                loadLatestResults()
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Refresh failed")
            }
        }
    }

    sealed class UiState {
        object Loading : UiState()
        object Success : UiState()
        data class Error(val message: String) : UiState()
    }
} 