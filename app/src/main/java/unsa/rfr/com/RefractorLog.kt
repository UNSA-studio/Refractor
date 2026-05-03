package unsa.rfr.com

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RefractorLog {
    private const val LOG_FILE = "refractor_log.txt"
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        write("========== Refractor Log Started ==========")
    }

    fun write(msg: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val line = "$timestamp  $msg\n"

        try {
            val logFile = File(appContext.cacheDir, LOG_FILE)
            FileWriter(logFile, true).use { it.append(line) }
        } catch (_: Exception) {}
    }

    fun getLogFile(): File = File(appContext.cacheDir, LOG_FILE)

    fun getLogContent(): String {
        return try {
            getLogFile().readText()
        } catch (e: Exception) {
            "无法读取日志: ${e.message}"
        }
    }
}
