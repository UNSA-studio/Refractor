package unsa.rfr.com.ui.screens

import android.app.Activity
import android.os.Bundle

class RoomActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 通知点击返回已起作用，直接 finish 即可
        finish()
    }
}
