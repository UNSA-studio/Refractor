package unsa.rfr.com.capture

import android.app.*
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.VideoCapturer

class ScreenCaptureService : Service() {

    companion object {
        const val TAG = "ScreenCaptureService"
        const val CHANNEL_ID = "screen_capture_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "unsa.rfr.com.action.STOP_SCREEN_CAPTURE"

        var videoCapturer: VideoCapturer? = null
            private set
        var mediaProjection: MediaProjection? = null
            private set
    }

    private var screenCapturer: ScreenCapturerAndroid? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        val resultCode = intent?.getIntExtra("resultCode", -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY

        if (resultCode == Activity.RESULT_OK) {
            startScreenCapture(resultCode, data)
        }

        return START_STICKY
    }

    private fun startScreenCapture(resultCode: Int, data: Intent) {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(resultCode, data)

        // 使用官方 ScreenCapturerAndroid，它会自动处理 VirtualDisplay
        screenCapturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
            override fun onStop() {
                stopCapture()
            }
        })
        videoCapturer = screenCapturer
    }

    private fun stopCapture() {
        screenCapturer?.dispose()
        screenCapturer = null
        videoCapturer = null
        mediaProjection?.stop()
        mediaProjection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "屏幕录制", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Refractor 直播中")
            .setContentText("正在分享屏幕…")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止直播",
                PendingIntent.getService(
                    this, 0, Intent(this, ScreenCaptureService::class.java)
                        .setAction(ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE
                ))
            .build()
    }
}
