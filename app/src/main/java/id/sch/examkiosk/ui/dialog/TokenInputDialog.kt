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
import androidx.lifecycle.lifecycleScope
import id.sch.examkiosk.ExamApp
import id.sch.examkiosk.R
import id.sch.examkiosk.data.crypto.CryptoEngine
import id.sch.examkiosk.data.model.ExamItem
import id.sch.examkiosk.data.sync.LocalSyncManager
import id.sch.examkiosk.databinding.DialogTokenInputBinding
import kotlinx.coroutines.launch

class TokenInputDialog : DialogFragment() {

    private var _binding: DialogTokenInputBinding? = null
    private val binding get() = _binding!!

    private var exam: ExamItem? = null
    private var onTokenConfirmed: ((String) -> Unit)? = null

    fun setExam(examItem: ExamItem, onConfirmed: (String) -> Unit) {
        this.exam = examItem
        this.onTokenConfirmed = onConfirmed
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
        _binding = DialogTokenInputBinding.inflate(inflater, container, false)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentExam = exam ?: return
        binding.tvExamSubjectSubtitle.text = "${currentExam.subjectName} (Kelas ${currentExam.gradeLevel}) • ${currentExam.durationMinutes} Menit"

        binding.btnCancelToken.setOnClickListener {
            dismiss()
        }

        binding.btnSubmitToken.setOnClickListener {
            val token = binding.etToken.text?.toString()?.trim() ?: ""
            if (token.isEmpty()) {
                binding.tvTokenError.text = "Token tidak boleh kosong"
                binding.tvTokenError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            verifyToken(currentExam, token)
        }
    }

    private fun verifyToken(examItem: ExamItem, token: String) {
        binding.pbDecrypting.visibility = View.VISIBLE
        binding.tvTokenError.visibility = View.GONE
        binding.btnSubmitToken.isEnabled = false

        lifecycleScope.launch {
            val context = requireContext()
            val syncManager = LocalSyncManager(context)
            val cryptoEngine = CryptoEngine(context)
            val encFile = syncManager.getExamFile(examItem.id)

            if (!encFile.exists()) {
                binding.pbDecrypting.visibility = View.GONE
                binding.btnSubmitToken.isEnabled = true
                binding.tvTokenError.text = "Berkas soal terenkripsi belum diunduh!"
                binding.tvTokenError.visibility = View.VISIBLE
                return@launch
            }

            val result = cryptoEngine.decryptToUnlinkedPfd(
                encryptedFile = encFile,
                token = token,
                saltHex = examItem.pbkdf2SaltHex,
                ivHex = examItem.gcmIvHex
            )

            binding.pbDecrypting.visibility = View.GONE
            binding.btnSubmitToken.isEnabled = true

            result.onSuccess { session ->
                // Tutup PFD dan hapus file uji coba
                try {
                    session.pfd.close()
                } catch (_: Exception) {
                }
                try {
                    session.tempFile.delete()
                } catch (_: Exception) {
                }
                dismiss()
                onTokenConfirmed?.invoke(token)
            }.onFailure { err ->
                val errorMsg = if (err.localizedMessage?.contains("PDF") == true) {
                    err.localizedMessage ?: getString(R.string.error_invalid_token)
                } else {
                    getString(R.string.error_invalid_token)
                }
                binding.tvTokenError.text = errorMsg
                binding.tvTokenError.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "TokenInputDialog"

        fun newInstance(exam: ExamItem, onConfirmed: (String) -> Unit): TokenInputDialog {
            return TokenInputDialog().apply {
                setExam(exam, onConfirmed)
            }
        }
    }
}
