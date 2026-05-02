package unsa.rfr.com.webrtc

import android.content.Context
import io.getstream.webrtc.android.ui.SurfaceViewRenderer

class VideoRenderView(context: Context) : SurfaceViewRenderer(context) {
    fun init() {
        setEnableHardwareScaler(true)
        setZOrderMediaOverlay(true)
    }
}
