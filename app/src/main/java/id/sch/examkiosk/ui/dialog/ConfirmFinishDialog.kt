package id.sch.examkiosk.ui.dialog

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import id.sch.examkiosk.databinding.DialogConfirmFinishBinding

class ConfirmFinishDialog : DialogFragment() {

    private var _binding: DialogConfirmFinishBinding? = null
    private val binding get() = _binding!!

    private var onConfirmAction: (() -> Unit)? = null

    fun setOnConfirm(action: () -> Unit) {
        this.onConfirmAction = action
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
        _binding = DialogConfirmFinishBinding.inflate(inflater, container, false)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCancelFinish.setOnClickListener {
            dismiss()
        }

        binding.btnConfirmFinish.setOnClickListener {
            dismiss()
            onConfirmAction?.invoke()
        }
    }

    override fun onResume() {
        super.onResume()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ConfirmFinishDialog"

        fun newInstance(onConfirm: () -> Unit): ConfirmFinishDialog {
            return ConfirmFinishDialog().apply {
                setOnConfirm(onConfirm)
            }
        }
    }
}
