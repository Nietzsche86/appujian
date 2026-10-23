package id.sch.examkiosk.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.sch.examkiosk.R
import id.sch.examkiosk.data.model.ExamItemUiModel
import id.sch.examkiosk.data.model.ExamStatus
import id.sch.examkiosk.databinding.ItemExamCardBinding

class ExamAdapter(
    private val onActionClick: (ExamItemUiModel) -> Unit
) : ListAdapter<ExamItemUiModel, ExamAdapter.ExamViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExamViewHolder {
        val binding = ItemExamCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ExamViewHolder(binding, onActionClick)
    }

    override fun onBindViewHolder(holder: ExamViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ExamViewHolder(
        private val binding: ItemExamCardBinding,
        private val onActionClick: (ExamItemUiModel) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ExamItemUiModel) {
            val exam = item.exam
            val context = binding.root.context

            binding.tvSubjectName.text = exam.subjectName
            binding.tvGradeLevel.text = context.getString(R.string.filter_all).let {
                "Kelas ${exam.gradeLevel}"
            }
            binding.tvDuration.text = context.getString(R.string.label_duration, exam.durationMinutes)

            when (item.status) {
                ExamStatus.SELESAI -> {
                    binding.tvExamStatusBadge.text = context.getString(R.string.status_finished)
                    binding.tvExamStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.badge_finished_text))
                    binding.btnExamAction.text = context.getString(R.string.status_finished)
                    binding.btnExamAction.isEnabled = false
                    binding.btnExamAction.alpha = 0.5f
                }
                ExamStatus.TERSIMPAN, ExamStatus.SEDANG_UJIAN -> {
                    val statusText = if (item.status == ExamStatus.SEDANG_UJIAN) "Sedang Berjalan" else context.getString(R.string.status_saved)
                    binding.tvExamStatusBadge.text = statusText
                    binding.tvExamStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.badge_ready_text))
                    binding.btnExamAction.text = context.getString(R.string.btn_start_exam)
                    binding.btnExamAction.isEnabled = true
                    binding.btnExamAction.alpha = 1.0f
                }
                ExamStatus.BELUM_DIUNDUH -> {
                    binding.tvExamStatusBadge.text = context.getString(R.string.status_not_downloaded)
                    binding.tvExamStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                    binding.btnExamAction.text = context.getString(R.string.btn_download)
                    binding.btnExamAction.isEnabled = true
                    binding.btnExamAction.alpha = 1.0f
                }
            }

            binding.btnExamAction.setOnClickListener {
                onActionClick(item)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<ExamItemUiModel>() {
        override fun areItemsTheSame(oldItem: ExamItemUiModel, newItem: ExamItemUiModel): Boolean {
            return oldItem.exam.id == newItem.exam.id
        }

        override fun areContentsTheSame(oldItem: ExamItemUiModel, newItem: ExamItemUiModel): Boolean {
            return oldItem == newItem
        }
    }
}
