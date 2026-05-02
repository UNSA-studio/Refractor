package unsa.rfr.com.capture

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import androidx.core.app.NotificationCompat
import io.getstream.webrtc.android.ScreenCapturerAndroid
import org.webrtc.*

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

    private var virtualDisplay: VirtualDisplay? = null
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
        val metrics = DisplayMetrics()
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        dm.getDisplay(Display.DEFAULT_DISPLAY)?.getRealMetrics(metrics)

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(resultCode, data)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val dpi = metrics.densityDpi

        screenCapturer = ScreenCapturerAndroid(
            data,
            object : MediaProjection.Callback() {
                override fun onStop() {
                    stopCapture()
                }
            },
            width, height, dpi
        )

        // 在 WebRtcManager.startAsBroadcaster 调用 initialize 时才真正初始化，这里先准备好 capturer
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
            screenCapturer?.surface,
            null, null
        )
        videoCapturer = screenCapturer as VideoCapturer
        Log.d(TAG, "Screen capture started: ${width}x${height}")
    }

    private fun stopCapture() {
        virtualDisplay?.release()
        virtualDisplay = null
        screenCapturer?.dispose()
        screenCapturer = null
        videoCapturer = null
        mediaProjection?.stop()
        mediaProjection = null
        Log.d(TAG, "Screen capture stopped")
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

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }
}
