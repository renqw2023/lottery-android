package com.example.lottery

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LotteryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        setupWorkManager()
    }
    
    private fun setupWorkManager() {
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
        
        WorkManager.initialize(this, config)
    }
} 