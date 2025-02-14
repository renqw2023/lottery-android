package com.example.lottery.ui.analysis

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.lottery.R
import com.example.lottery.databinding.FragmentAnalysisBinding
import com.example.lottery.viewmodel.AnalysisViewModel
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AnalysisFragment : Fragment() {

    private var _binding: FragmentAnalysisBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AnalysisViewModel by viewModels()
    private lateinit var pagerAdapter: AnalysisPagerAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalysisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        observeData()
    }

    private fun setupViews() {
        // 设置工具栏
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }

        // 设置ViewPager和TabLayout
        pagerAdapter = AnalysisPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_trend)
                1 -> getString(R.string.tab_distribution)
                else -> getString(R.string.tab_pattern)
            }
        }.attach()

        // 设置过滤按钮
        binding.filterFab.setOnClickListener {
            showFilterDialog()
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.analysisData.collectLatest { data ->
                pagerAdapter.updateData(data)
            }
        }
    }

    private fun showFilterDialog() {
        AnalysisFilterDialog().show(
            childFragmentManager,
            "AnalysisFilterDialog"
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 