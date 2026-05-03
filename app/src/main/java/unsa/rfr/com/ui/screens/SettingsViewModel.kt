package unsa.rfr.com.ui.screens

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel : ViewModel() {
    private val _rotationAngle = MutableStateFlow(0f)
    val rotationAngle: StateFlow<Float> = _rotationAngle

    fun rotateGear() {
        _rotationAngle.value += 360f
    }
}
