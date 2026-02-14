package com.scanx.qrscanner

import android.app.Application
import android.content.Intent
import android.os.Process
import android.os.StrictMode
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class QrScannerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        if (BuildConfig.DEBUG) {
            enableStrictMode()
        }

        // Apply saved theme
        SettingsManager(this).applyTheme()

        // Set global exception handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleUncaughtException(thread, throwable, defaultHandler)
        }
    }

    private fun handleUncaughtException(
        thread: Thread, 
        throwable: Throwable, 
        defaultHandler: Thread.UncaughtExceptionHandler?
    ) {
        try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val stackTrace = sw.toString()

            val intent = Intent(this, ErrorActivity::class.java).apply {
                putExtra(ErrorActivity.EXTRA_STACK_TRACE, stackTrace)
                putExtra(ErrorActivity.EXTRA_ERROR_MESSAGE, throwable.message ?: "Unknown error")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
            
            // Kill process to ensure clean restart state
            Process.killProcess(Process.myPid())
            exitProcess(1)
        } catch (e: Exception) {
            // If custom handling fails, fall back to default
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )
    }

    /**
     * Simple check for common root binaries.
     * Note: This is not exhaustive but provides a basic level of awareness.
     */
    fun isDeviceRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        return false
    }
}
