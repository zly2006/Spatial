package com.github.zly2006.spatial

import android.app.Application
import android.content.Intent
import android.os.Looper

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val intent = Intent(this, ErrorReportActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("error_message", throwable.message ?: "No message")
                putExtra("error_stack", throwable.stackTraceToString())
            }
            startActivity(intent)
            // 终止主线程
            if (Looper.getMainLooper().thread == thread) {
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(10)
            }
        }
    }
}

