package unsa.rfr.com.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.AudioRecordDataCallback
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 音频采集管理。
 *
 * 三种模式：
 * - [AudioMode.MIC_ONLY]：仅麦克风（默认），走标准 JavaAudioDeviceModule，无自定义处理。
 * - [AudioMode.INTERNAL_ONLY]：仅内部音频（系统播放的声音），通过 [AudioRecordDataCallback]
 *   把麦克风采集到的数据替换为 AudioPlaybackCapture 捕获的系统音频（需 Android 10+ 且已授权
 *   MediaProjection）。
 * - [AudioMode.BOTH]：麦克风 + 内部音频混音（PCM16 加法混音）。
 *
 * 说明：WebRTC 底层 AudioRecord 始终在录麦克风（ADM 要求），但 INTERNAL_ONLY / BOTH 通过
 * GetStream fork 提供的回调在数据进入 native 前替换或混音，实现"仅内部音频 / 都接收"。
 */
class AudioCaptureManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioCaptureManager"
        const val SAMPLE_RATE = 48000
        /** 内部音频 10ms 帧字节数：48kHz * 2声道 * 2字节 * 0.01s */
        private const val INTERNAL_FRAME_BYTES = SAMPLE_RATE * 2 * 2 / 100
    }

    enum class AudioMode { MIC_ONLY, INTERNAL_ONLY, BOTH }

    private val frameLock = Any()
    private var latestInternalFrame: ByteArray? = null

    private var internalRecord: AudioRecord? = null
    private var internalThread: Thread? = null
    @Volatile private var internalRunning = false

    /** 创建对应的 AudioDeviceModule（传入 WebRtcManager 构建 PeerConnectionFactory 前）。 */
    fun createAudioDeviceModule(mode: AudioMode, projection: MediaProjection?): AudioDeviceModule {
        return when (mode) {
            AudioMode.MIC_ONLY -> JavaAudioDeviceModule.builder(context)
                .setSampleRate(SAMPLE_RATE)
                .setUseHardwareAcousticEchoCanceler(false)
                .setUseHardwareNoiseSuppressor(false)
                .createAudioDeviceModule()

            AudioMode.INTERNAL_ONLY, AudioMode.BOTH -> {
                startInternalCapture(projection)
                JavaAudioDeviceModule.builder(context)
                    .setSampleRate(SAMPLE_RATE)
                    .setUseHardwareAcousticEchoCanceler(false)
                    .setUseHardwareNoiseSuppressor(false)
                    .setAudioRecordDataCallback(AudioRecordDataCallback { audioFormat, channelCount, sampleRate, audioBuffer ->
                        onAudioDataRecorded(audioFormat, channelCount, sampleRate, audioBuffer, mode)
                    })
                    .createAudioDeviceModule()
            }
        }
    }

    /** 启动系统内部音频（AudioPlaybackCapture）采集，独立线程持续读取最新帧。 */
    private fun startInternalCapture(projection: MediaProjection?) {
        stopInternalCapture()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.w(TAG, "内部音频采集需要 Android 10+，已忽略")
            return
        }
        if (projection == null) {
            Log.w(TAG, "缺少 MediaProjection（屏幕捕获未授权），内部音频不可用")
            return
        }
        try {
            val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf == AudioRecord.ERROR || minBuf == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "内部音频缓冲大小无效")
                return
            }
            val record = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuf * 2, INTERNAL_FRAME_BYTES * 4))
                .build()
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "内部音频 AudioRecord 初始化失败")
                record.release()
                return
            }
            internalRecord = record
            record.startRecording()
            internalRunning = true
            Log.d(TAG, "内部音频采集已启动")

            internalThread = Thread({
                val buf = ByteArray(INTERNAL_FRAME_BYTES)
                while (internalRunning) {
                    try {
                        val n = record.read(buf, 0, buf.size)
                        if (n > 0) {
                            synchronized(frameLock) { latestInternalFrame = buf.copyOf(n) }
                        }
                    } catch (e: Exception) {
                        if (internalRunning) Log.e(TAG, "内部音频读取失败: ${e.message}")
                    }
                }
            }, "InternalAudioCaptureThread").apply { start() }
        } catch (e: Exception) {
            Log.e(TAG, "启动内部音频采集失败: ${e.message}")
        }
    }

    private fun stopInternalCapture() {
        internalRunning = false
        internalThread?.join(1000)
        internalThread = null
        internalRecord?.stop()
        internalRecord?.release()
        internalRecord = null
        synchronized(frameLock) { latestInternalFrame = null }
    }

    /**
     * WebRTC 录音线程回调（每 ~10ms 一次）。audioBuffer 为 PCM16 小端，
     * 内容为麦克风数据；此处按模式替换或混入内部音频后写回。
     */
    private fun onAudioDataRecorded(
        audioFormat: Int,
        channelCount: Int,
        sampleRate: Int,
        audioBuffer: ByteBuffer,
        mode: AudioMode
    ) {
        if (audioFormat != AudioFormat.ENCODING_PCM_16BIT) return

        val internal = synchronized(frameLock) { latestInternalFrame }
        val frames = audioBuffer.capacity() / 2 / channelCount
        if (frames <= 0) return

        // 先拷贝麦克风原数据（buffer 需要同时读和写）
        val micData = ByteArray(audioBuffer.capacity())
        audioBuffer.rewind()
        audioBuffer.get(micData)
        audioBuffer.rewind()
        audioBuffer.order(ByteOrder.LITTLE_ENDIAN)

        if (mode == AudioMode.INTERNAL_ONLY && internal == null) {
            // 仅内部音频但暂未捕获到：输出静音
            while (audioBuffer.hasRemaining()) audioBuffer.put(0)
            return
        }

        val internalShorts = (internal?.size ?: 0) / 2 // 内部帧为 stereo，short 数
        for (i in 0 until frames) {
            val micL = leShort(micData, i * 2 * channelCount)
            val micR = if (channelCount == 2) leShort(micData, i * 2 * channelCount + 2) else micL

            // 内部 stereo 帧按帧索引取左右声道（与采样率一致时逐帧对齐）
            var intL = 0
            var intR = 0
            if (internal != null && internalShorts > 0) {
                val idx = i * 2
                if (idx < internalShorts) intL = leShort(internal, idx * 2)
                if (idx + 1 < internalShorts) intR = leShort(internal, idx * 2 + 2)
            }

            val outL: Int
            val outR: Int
            when (mode) {
                AudioMode.MIC_ONLY -> { outL = micL; outR = micR }
                AudioMode.INTERNAL_ONLY -> { outL = intL; outR = intR }
                AudioMode.BOTH -> { outL = clampMix(micL + intL); outR = clampMix(micR + intR) }
            }

            audioBuffer.putShort(outL.toShort())
            if (channelCount == 2) audioBuffer.putShort(outR.toShort())
        }
    }

    private fun leShort(data: ByteArray, offset: Int): Int {
        // PCM16 小端
        return (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun clampMix(v: Int): Int = v.coerceIn(-32768, 32767)

    fun stop() {
        stopInternalCapture()
    }
}
