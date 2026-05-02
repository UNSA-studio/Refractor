package unsa.rfr.com.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import org.webrtc.audio.JavaAudioDeviceModule

class AudioCaptureManager(private val context: Context) {
    companion object {
        private const val TAG = "AudioCaptureManager"
        const val SAMPLE_RATE = 48000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    enum class AudioMode { MIC_ONLY, INTERNAL_ONLY, BOTH }

    private var audioRecord: AudioRecord? = null
    private var internalRecord: AudioRecord? = null
    private var mediaProjection: MediaProjection? = null
    private var currentMode = AudioMode.MIC_ONLY

    fun setMediaProjection(projection: MediaProjection) {
        this.mediaProjection = projection
    }

    fun createAudioSource(mode: AudioMode): org.webrtc.AudioSource? {
        currentMode = mode
        return when (mode) {
            AudioMode.MIC_ONLY -> createMicSource()
            AudioMode.INTERNAL_ONLY -> createInternalSource()
            AudioMode.BOTH -> createMixedSource()
        }
    }

    private fun createMicSource(): org.webrtc.AudioSource? {
        val factory = JavaAudioDeviceModule.builder(context)
            .setSampleRate(SAMPLE_RATE)
            .setUseHardwareAcousticEchoCanceler(false)
            .setUseHardwareNoiseSuppressor(false)
            .createAudioDeviceModule()
        return factory.javaClass.getDeclaredMethod("getNativeAudioSource")
            .invoke(factory) as? org.webrtc.AudioSource
    }

    private fun createInternalSource(): org.webrtc.AudioSource? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.w(TAG, "Internal audio capture requires Android 10+")
            return null
        }
        val projection = mediaProjection ?: return null

        try {
            val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .build()

            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            internalRecord = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AUDIO_FORMAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG)
                    .build())
                .setBufferSizeInBytes(bufferSize)
                .build()
            internalRecord?.startRecording()

            // 通过 WebRTC 自定义音频源（这里简化为返回 null，实际需要在 WebRTC 层面绑定）
            Log.d(TAG, "Internal audio capture started")
            return null // 需要 WebRTC 自定义音频轨道绑定，后续补全
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start internal capture", e)
            return null
        }
    }

    private fun createMixedSource(): org.webrtc.AudioSource? {
        // 同时采集麦克风和内部音频，在 PCM 层混音后发送
        return null // 需要 PCM 混音逻辑，后续补全
    }

    fun stop() {
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        internalRecord?.stop()
        internalRecord?.release()
        internalRecord = null
    }
}
