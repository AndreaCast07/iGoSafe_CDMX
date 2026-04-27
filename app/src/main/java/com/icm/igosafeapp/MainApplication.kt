package com.icm.igosafeapp

import android.app.Application
import android.util.Log

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize RiskManager
        RiskManager.initialize(this)
        
        // Note: Flogger configuration on Android usually requires a backend.
        // If the warning persists, it might be due to a library using Flogger 
        // before the application class is even fully initialized or 
        // due to missing configuration in some Google SDKs.
    }
}
