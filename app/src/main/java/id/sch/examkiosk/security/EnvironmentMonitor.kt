package id.sch.examkiosk.security

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.provider.Settings
import androidx.core.content.ContextCompat

enum class ViolationReason(val description: String) {
    AIRPLANE_MODE_DISABLED("Mode Pesawat Dinonaktifkan"),
    BLUETOOTH_ENABLED("Bluetooth Terdeteksi Aktif"),
    USB_CONNECTED("Kabel USB Terhubung"),
    WINDOW_FOCUS_LOST("Kehilangan Fokus Layar (Mencoba Membuka Aplikasi Lain)"),
    DEVICE_REBOOT_DETECTED("Perangkat Direstart di Tengah Ujian")
}

class EnvironmentMonitor(
    private val context: Context,
    private val onViolationDetected: (ViolationReason) -> Unit
) {

    private var isMonitoring = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (!isMonitoring) return
            when (intent?.action) {
                Intent.ACTION_AIRPLANE_MODE_CHANGED -> checkAirplaneMode()
                BluetoothAdapter.ACTION_STATE_CHANGED -> checkBluetooth()
                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                "android.hardware.usb.action.USB_STATE" -> checkUsbConnection(intent)
            }
        }
    }

    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction("android.hardware.usb.action.USB_STATE")
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Verifikasi kondisi awal saat monitoring dimulai
        checkAllImmediate()
    }

    fun stopMonitoring() {
        if (!isMonitoring) return
        isMonitoring = false
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
    }

    fun onWindowFocusChanged(hasFocus: Boolean, isKioskGracePeriod: Boolean) {
        if (!isMonitoring || isKioskGracePeriod) return
        if (!hasFocus) {
            onViolationDetected(ViolationReason.WINDOW_FOCUS_LOST)
        }
    }

    fun checkAllImmediate(): Boolean {
        if (!checkAirplaneMode()) return false
        if (!checkBluetooth()) return false
        return true
    }

    private fun checkAirplaneMode(): Boolean {
        val isAirplaneOn = Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0
        ) != 0

        if (!isAirplaneOn) {
            onViolationDetected(ViolationReason.AIRPLANE_MODE_DISABLED)
            return false
        }
        return true
    }

    private fun checkBluetooth(): Boolean {
        val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        val isBtOn = adapter != null && adapter.isEnabled

        if (isBtOn) {
            onViolationDetected(ViolationReason.BLUETOOTH_ENABLED)
            return false
        }
        return true
    }

    private fun checkUsbConnection(intent: Intent?) {
        val isConnected = intent?.getBooleanExtra("connected", false) ?: false
        if (isConnected) {
            onViolationDetected(ViolationReason.USB_CONNECTED)
        }
    }
}
