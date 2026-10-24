package id.mtsrth.exam.ui.dialog

import android.app.ActivityManager
import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import id.mtsrth.exam.R
import id.mtsrth.exam.databinding.DialogDeviceRequirementBinding
import java.util.Locale

class DeviceRequirementDialog : DialogFragment() {

    private var _binding: DialogDeviceRequirementBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.requestFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogDeviceRequirementBinding.inflate(inflater, container, false)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnOpenSettings.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {
            }
        }

        binding.btnUnderstand.setOnClickListener {
            dismiss()
        }

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        updateStatus()
    }

    private fun updateStatus() {
        val ctx = context ?: return

        // 1. Mode Pesawat (Wajib AKTIF)
        val isAirplaneOn = Settings.Global.getInt(
            ctx.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0
        ) != 0

        // 2. Wi-Fi (Wajib MATI)
        val wifiManager = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val isWifiOff = wifiManager?.isWifiEnabled != true

        // 3. Bluetooth (Wajib MATI)
        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        val isBluetoothOff = bluetoothAdapter == null || !bluetoothAdapter.isEnabled

        // 4. Mode Suara (Wajib HENING/GETAR)
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val isRingerSilentOrVibrate = audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL

        val okBg = ContextCompat.getColor(ctx, R.color.card_sensor_ok_bg)
        val okBorder = ContextCompat.getColor(ctx, R.color.card_sensor_ok_border)
        val okText = ContextCompat.getColor(ctx, R.color.badge_ready_text)

        val badBg = ContextCompat.getColor(ctx, R.color.card_sensor_bad_bg)
        val badBorder = ContextCompat.getColor(ctx, R.color.card_sensor_bad_border)
        val badText = ContextCompat.getColor(ctx, R.color.badge_danger_text)

        // 1. Airplane
        if (isAirplaneOn) {
            binding.cardDialogAirplane.setCardBackgroundColor(okBg)
            binding.cardDialogAirplane.strokeColor = okBorder
            binding.ivDialogAirplaneIcon.setColorFilter(okText)
            binding.tvDialogAirplaneStatus.setTextColor(okText)
            binding.tvDialogAirplaneStatus.text = "Mode Pesawat: Aktif (Aman)"
            binding.ivDialogAirplaneCheck.setImageResource(R.drawable.ic_check_circle)
            binding.ivDialogAirplaneCheck.setColorFilter(okText)
        } else {
            binding.cardDialogAirplane.setCardBackgroundColor(badBg)
            binding.cardDialogAirplane.strokeColor = badBorder
            binding.ivDialogAirplaneIcon.setColorFilter(badText)
            binding.tvDialogAirplaneStatus.setTextColor(badText)
            binding.tvDialogAirplaneStatus.text = "Mode Pesawat: Nonaktif (Wajib ON!)"
            binding.ivDialogAirplaneCheck.setImageResource(R.drawable.ic_cancel)
            binding.ivDialogAirplaneCheck.setColorFilter(badText)
        }

        // 2. Wi-Fi
        if (isWifiOff) {
            binding.cardDialogWifi.setCardBackgroundColor(okBg)
            binding.cardDialogWifi.strokeColor = okBorder
            binding.ivDialogWifiIcon.setColorFilter(okText)
            binding.tvDialogWifiStatus.setTextColor(okText)
            binding.tvDialogWifiStatus.text = "Wi-Fi: Nonaktif (Aman)"
            binding.ivDialogWifiCheck.setImageResource(R.drawable.ic_check_circle)
            binding.ivDialogWifiCheck.setColorFilter(okText)
        } else {
            binding.cardDialogWifi.setCardBackgroundColor(badBg)
            binding.cardDialogWifi.strokeColor = badBorder
            binding.ivDialogWifiIcon.setColorFilter(badText)
            binding.tvDialogWifiStatus.setTextColor(badText)
            binding.tvDialogWifiStatus.text = "Wi-Fi: Aktif (Wajib Dimatikan!)"
            binding.ivDialogWifiCheck.setImageResource(R.drawable.ic_cancel)
            binding.ivDialogWifiCheck.setColorFilter(badText)
        }

        // 3. Bluetooth
        if (isBluetoothOff) {
            binding.cardDialogBluetooth.setCardBackgroundColor(okBg)
            binding.cardDialogBluetooth.strokeColor = okBorder
            binding.ivDialogBluetoothIcon.setColorFilter(okText)
            binding.tvDialogBluetoothStatus.setTextColor(okText)
            binding.tvDialogBluetoothStatus.text = "Bluetooth: Nonaktif (Aman)"
            binding.ivDialogBluetoothCheck.setImageResource(R.drawable.ic_check_circle)
            binding.ivDialogBluetoothCheck.setColorFilter(okText)
        } else {
            binding.cardDialogBluetooth.setCardBackgroundColor(badBg)
            binding.cardDialogBluetooth.strokeColor = badBorder
            binding.ivDialogBluetoothIcon.setColorFilter(badText)
            binding.tvDialogBluetoothStatus.setTextColor(badText)
            binding.tvDialogBluetoothStatus.text = "Bluetooth: Aktif (Wajib Dimatikan!)"
            binding.ivDialogBluetoothCheck.setImageResource(R.drawable.ic_cancel)
            binding.ivDialogBluetoothCheck.setColorFilter(badText)
        }

        // 4. Mode Suara
        if (isRingerSilentOrVibrate) {
            binding.cardDialogRinger.setCardBackgroundColor(okBg)
            binding.cardDialogRinger.strokeColor = okBorder
            binding.ivDialogRingerIcon.setColorFilter(okText)
            binding.tvDialogRingerStatus.setTextColor(okText)
            binding.tvDialogRingerStatus.text = "Mode Suara: Hening/Getar (Aman)"
            binding.ivDialogRingerCheck.setImageResource(R.drawable.ic_check_circle)
            binding.ivDialogRingerCheck.setColorFilter(okText)
        } else {
            binding.cardDialogRinger.setCardBackgroundColor(badBg)
            binding.cardDialogRinger.strokeColor = badBorder
            binding.ivDialogRingerIcon.setColorFilter(badText)
            binding.tvDialogRingerStatus.setTextColor(badText)
            binding.tvDialogRingerStatus.text = "Mode Suara: Berdering (Wajib Hening!)"
            binding.ivDialogRingerCheck.setImageResource(R.drawable.ic_cancel)
            binding.ivDialogRingerCheck.setColorFilter(badText)
        }

        // 5. Versi Android (Minimal Android 8 Oreo - API 26)
        val osRelease = Build.VERSION.RELEASE ?: "Unknown"
        val isAndroidVersionOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

        if (isAndroidVersionOk) {
            binding.cardDialogAndroid.setCardBackgroundColor(okBg)
            binding.cardDialogAndroid.strokeColor = okBorder
            binding.ivDialogAndroidIcon.setColorFilter(okText)
            binding.tvDialogAndroidStatus.setTextColor(okText)
            binding.tvDialogAndroidStatus.text = "Versi: Android $osRelease (Memenuhi Standar)"
            binding.ivDialogAndroidCheck.setImageResource(R.drawable.ic_check_circle)
            binding.ivDialogAndroidCheck.setColorFilter(okText)
        } else {
            binding.cardDialogAndroid.setCardBackgroundColor(badBg)
            binding.cardDialogAndroid.strokeColor = badBorder
            binding.ivDialogAndroidIcon.setColorFilter(badText)
            binding.tvDialogAndroidStatus.setTextColor(badText)
            binding.tvDialogAndroidStatus.text = "Versi: Android $osRelease (Wajib Minimal Android 8 Oreo)"
            binding.ivDialogAndroidCheck.setImageResource(R.drawable.ic_cancel)
            binding.ivDialogAndroidCheck.setColorFilter(badText)
        }

        // 6. RAM (Minimal 2 GB)
        val actManager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val totalBytes = memInfo.totalMem
        val totalGb = totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        val formattedRam = String.format(Locale.US, "%.1f", totalGb).removeSuffix(".0")
        val isRamOk = totalBytes >= (1.75 * 1024 * 1024 * 1024)

        if (isRamOk) {
            binding.cardDialogRam.setCardBackgroundColor(okBg)
            binding.cardDialogRam.strokeColor = okBorder
            binding.ivDialogRamIcon.setColorFilter(okText)
            binding.tvDialogRamStatus.setTextColor(okText)
            binding.tvDialogRamStatus.text = "RAM: $formattedRam GB (Memenuhi Standar)"
            binding.ivDialogRamCheck.setImageResource(R.drawable.ic_check_circle)
            binding.ivDialogRamCheck.setColorFilter(okText)
        } else {
            binding.cardDialogRam.setCardBackgroundColor(badBg)
            binding.cardDialogRam.strokeColor = badBorder
            binding.ivDialogRamIcon.setColorFilter(badText)
            binding.tvDialogRamStatus.setTextColor(badText)
            binding.tvDialogRamStatus.text = "RAM: $formattedRam GB (Wajib Minimal 2 GB)"
            binding.ivDialogRamCheck.setImageResource(R.drawable.ic_cancel)
            binding.ivDialogRamCheck.setColorFilter(badText)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "DeviceRequirementDialog"

        fun newInstance(): DeviceRequirementDialog {
            return DeviceRequirementDialog()
        }
    }
}
