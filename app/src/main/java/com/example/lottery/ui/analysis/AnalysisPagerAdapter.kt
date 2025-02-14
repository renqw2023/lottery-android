package com.example.lottery.ui.analysis

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.lottery.data.model.AnalysisData

class AnalysisPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    private var data: AnalysisData? = null

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> TrendAnalysisFragment()
            1 -> DistributionAnalysisFragment()
            else -> PatternAnalysisFragment()
        }
    }

    fun updateData(newData: AnalysisData) {
        data = newData
        notifyDataSetChanged()
    }
} 