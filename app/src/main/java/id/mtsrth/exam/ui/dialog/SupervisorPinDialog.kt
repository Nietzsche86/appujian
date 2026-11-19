package id.mtsrth.exam.ui.dialog

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import id.mtsrth.exam.databinding.DialogSupervisorPinBinding
import id.mtsrth.exam.security.SupervisorPinHelper

class SupervisorPinDialog : DialogFragment() {

    private var _binding: DialogSupervisorPinBinding? = null
    private val binding get() = _binding!!

    private var onResetPenalty: (() -> Unit)? = null
    private var onFinishEarly: (() -> Unit)? = null

    fun setActions(onResetPenalty: () -> Unit, onFinishEarly: () -> Unit) {
        this.onResetPenalty = onResetPenalty
        this.onFinishEarly = onFinishEarly
    }

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
        _binding = DialogSupervisorPinBinding.inflate(inflater, container, false)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCancelPin.setOnClickListener {
            dismiss()
        }

        binding.btnVerifyPin.setOnClickListener {
            val pin = binding.etMasterPin.text?.toString()?.trim() ?: ""
            when (val result = SupervisorPinHelper.verifyPin(pin)) {
                is SupervisorPinHelper.PinVerifyResult.Success -> {
                    binding.tvPinError.visibility = View.GONE
                    binding.tilMasterPin.visibility = View.GONE
                    binding.layoutVerifyButtons.visibility = View.GONE
                    binding.layoutSupervisorActions.visibility = View.VISIBLE
                }
                is SupervisorPinHelper.PinVerifyResult.Failure -> {
                    binding.tvPinError.text = result.reason
                    binding.tvPinError.visibility = View.VISIBLE
                }
            }
        }

        binding.btnResetPenaltyAction.setOnClickListener {
            dismiss()
            onResetPenalty?.invoke()
        }

        binding.btnFinishEarlyAction.setOnClickListener {
            dismiss()
            onFinishEarly?.invoke()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SupervisorPinDialog"

        fun newInstance(onResetPenalty: () -> Unit, onFinishEarly: () -> Unit): SupervisorPinDialog {
            return SupervisorPinDialog().apply {
                setActions(onResetPenalty, onFinishEarly)
            }
        }
    }
}
