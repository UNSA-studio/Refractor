package unsa.rfr.com

import android.app.Application
import android.os.Environment
import android.widget.Toast
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        RefractorLog.init(this)

        // 全局崩溃处理
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // 手动写一份日志到下载目录
            try {
                val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val crashFile = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "refractor_crash_$dateStr.txt"
                )
                FileWriter(crashFile, false).use { writer ->
                    writer.append("Crash Report -- ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())}\n")
                    writer.append("Thread: ${thread.name}\n")
                    writer.append("\n--- Exception ---\n")
                    writer.append(throwable.stackTraceToString())
                    writer.append("\n\n--- Last logs ---\n")
                    writer.append(RefractorLog.getLogContent())
                }
            } catch (_: Exception) {}
            // 交给系统默认处理
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
