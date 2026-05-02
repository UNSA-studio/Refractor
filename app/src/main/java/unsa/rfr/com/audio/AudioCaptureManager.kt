package unsa.rfr.com.audio

import android.content.Context
import android.media.AudioAttributes
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

    fun setMediaProjection(projection: MediaProjection) {
        this.mediaProjection = projection
    }

    fun createAudioSource(mode: AudioMode): org.webrtc.AudioSource? {
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

            Log.d(TAG, "Internal audio capture started")
            return null // 实际使用需要自定义 AudioTrack，暂时返回 null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start internal capture", e)
            return null
        }
    }

    private fun createMixedSource(): org.webrtc.AudioSource? {
        return null // 混音逻辑后续补完
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
