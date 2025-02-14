package com.example.lottery.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.lottery.R
import com.example.lottery.databinding.FragmentMainBinding
import com.example.lottery.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainFragment : Fragment() {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        observeData()
    }

    private fun setupViews() {
        // 设置下拉刷新
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshData()
        }

        // 设置底部导航
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_history -> {
                    findNavController().navigate(R.id.action_main_to_history)
                    true
                }
                R.id.navigation_analysis -> {
                    findNavController().navigate(R.id.action_main_to_analysis)
                    true
                }
                else -> true
            }
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            // 观察UI状态
            viewModel.uiState.collectLatest { state ->
                binding.swipeRefresh.isRefreshing = state is MainViewModel.UiState.Loading
                when (state) {
                    is MainViewModel.UiState.Error -> {
                        // TODO: 显示错误信息
                    }
                    else -> {}
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // 观察最新结果
            viewModel.latestResults.collectLatest { results ->
                updateMacauResult(results[LotteryType.MACAU])
                updateHKResult(results[LotteryType.HONGKONG])
            }
        }
    }

    private fun updateMacauResult(result: LotteryResult?) {
        result?.let {
            binding.macauDrawTime.text = formatDate(it.drawTime)
            binding.macauNumbersList.removeAllViews()
            it.numbers.forEach { number ->
                addNumberBall(binding.macauNumbersList, number, false)
            }
            addNumberBall(binding.macauNumbersList, it.specialNumber, true)
        }
    }

    private fun updateHKResult(result: LotteryResult?) {
        result?.let {
            binding.hkDrawTime.text = formatDate(it.drawTime)
            binding.hkNumbersList.removeAllViews()
            it.numbers.forEach { number ->
                addNumberBall(binding.hkNumbersList, number, false)
            }
            addNumberBall(binding.hkNumbersList, it.specialNumber, true)
        }
    }

    private fun addNumberBall(container: ViewGroup, number: Int, isSpecial: Boolean) {
        val ball = NumberBallView(requireContext()).apply {
            setNumber(number)
            setSpecial(isSpecial)
            layoutParams = ViewGroup.MarginLayoutParams(
                resources.getDimensionPixelSize(R.dimen.ball_size),
                resources.getDimensionPixelSize(R.dimen.ball_size)
            ).apply {
                marginStart = resources.getDimensionPixelSize(R.dimen.ball_margin)
            }
        }
        container.addView(ball)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 