package com.icm.igosafeapp

import android.app.Application
import android.util.Log

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize RiskManager
        RiskManager.initialize(this)
    }
}
