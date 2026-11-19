package id.mtsrth.exam.security

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.provider.Settings
import androidx.core.content.ContextCompat

enum class ViolationReason(val description: String) {
    AIRPLANE_MODE_DISABLED("Mode Pesawat Dinonaktifkan"),
    WIFI_ENABLED("Wi-Fi Terdeteksi Aktif Saat Ujian"),
    BLUETOOTH_ENABLED("Bluetooth Terdeteksi Aktif"),
    USB_CONNECTED("Kabel USB Terhubung"),
    MULTI_WINDOW_DETECTED("Mode Layar Belah (Split-Screen / Floating Window) Terdeteksi"),
    CLOCK_TAMPERED("Jam / Tanggal Perangkat Terdeteksi Dimanipulasi"),
    WINDOW_FOCUS_LOST("Kehilangan Fokus Layar (Mencoba Membuka Aplikasi Lain)"),
    DEVICE_REBOOT_DETECTED("Perangkat Direstart di Tengah Ujian"),
    UNAUTHORIZED_EXIT_ATTEMPT("Upaya Menutup / Melepas Kunci Layar Ujian Tanpa Izin")
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
                WifiManager.WIFI_STATE_CHANGED_ACTION -> checkWifiDisabled()
                BluetoothAdapter.ACTION_STATE_CHANGED -> checkBluetooth()
                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                "android.hardware.usb.action.USB_STATE" -> checkUsbBroadcast(intent)
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED -> {
                    onViolationDetected(ViolationReason.CLOCK_TAMPERED)
                }
            }
        }
    }

    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction("android.hardware.usb.action.USB_STATE")
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
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
        if (!checkWifiDisabled()) return false
        if (!checkBluetooth()) return false
        if (!checkUsbNotConnectedImmediate()) return false
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

    private fun checkWifiDisabled(): Boolean {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val isWifiOn = wifiManager?.isWifiEnabled == true
        if (isWifiOn) {
            onViolationDetected(ViolationReason.WIFI_ENABLED)
            return false
        }
        return true
    }

    private fun checkBluetooth(): Boolean {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
        val isBtOn = adapter != null && adapter.isEnabled

        if (isBtOn) {
            onViolationDetected(ViolationReason.BLUETOOTH_ENABLED)
            return false
        }
        return true
    }

    private fun checkUsbNotConnectedImmediate(): Boolean {
        val batteryIntent = runCatching {
            ContextCompat.registerReceiver(
                context,
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }.getOrNull()
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val isUsbDataOrPower = (plugged and BatteryManager.BATTERY_PLUGGED_USB) != 0

        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        val hasUsbAccessoryOrDevice = runCatching {
            !usbManager?.deviceList.isNullOrEmpty()
        }.getOrDefault(false)

        if (isUsbDataOrPower || hasUsbAccessoryOrDevice) {
            onViolationDetected(ViolationReason.USB_CONNECTED)
            return false
        }
        return true
    }

    private fun checkUsbBroadcast(intent: Intent?) {
        val action = intent?.action
        val isConnected = if (action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            true
        } else {
            intent?.getBooleanExtra("connected", false) ?: false
        }
        if (isConnected) {
            onViolationDetected(ViolationReason.USB_CONNECTED)
        }
    }
}
