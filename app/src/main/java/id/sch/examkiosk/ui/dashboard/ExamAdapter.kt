package id.sch.examkiosk.ui.dashboard

import android.content.res.ColorStateList
import android.graphics.Color
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
import id.sch.examkiosk.util.ExamScheduleHelper

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

            // 1. Hari & Jadwal Kalender / Jam (Tanpa Karakter Emoticon, Ikon Ditangani Vector Drawable)
            val dateLabel = ExamScheduleHelper.formatDisplayDate(exam.examDate, exam.examDay)
            binding.tvExamDayBadge.text = dateLabel

            val timeSchedule = if (exam.startTime.isNotBlank() && exam.endTime.isNotBlank()) {
                "${exam.startTime} - ${exam.endTime} WIB"
            } else if (exam.startTime.isNotBlank()) {
                "Mulai ${exam.startTime} WIB"
            } else {
                "Waktu Bebas"
            }
            binding.tvExamTimeSchedule.text = timeSchedule

            // 2. Tingkat Kelas
            binding.tvGradeBadge.text = "Kelas ${exam.gradeLevel}"

            // 3. Singkatan / Kode Mapel
            val shortCode = if (exam.shortName.isNotBlank()) {
                exam.shortName.uppercase()
            } else {
                exam.subjectName.filter { it.isLetterOrDigit() }.take(4).uppercase().ifEmpty { "MAPEL" }
            }
            binding.tvShortNameBadge.text = shortCode

            // 4. Nama Mapel Lengkap & Durasi
            binding.tvSubjectName.text = exam.subjectName
            binding.tvDuration.text = "${exam.durationMinutes} Menit"

            // 5. Status Aksi & Jadwal Jam Nyata HP
            val primaryColor = ContextCompat.getColor(context, R.color.primary)

            if (item.status == ExamStatus.SELESAI) {
                binding.btnExamAction.text = "Selesai"
                binding.btnExamAction.setIconResource(R.drawable.ic_check_circle)
                binding.btnExamAction.iconTint = ColorStateList.valueOf(Color.parseColor("#64748B"))
                binding.btnExamAction.isEnabled = false
                binding.btnExamAction.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E2E8F0"))
                binding.btnExamAction.setTextColor(Color.parseColor("#64748B"))
            } else {
                binding.btnExamAction.icon = null
                val scheduleState = ExamScheduleHelper.getScheduleState(exam)

                when (scheduleState) {
                    ExamScheduleHelper.ScheduleState.NOT_STARTED_YET -> {
                        val dateCompare = ExamScheduleHelper.compareExamDateWithToday(exam.examDate)
                        binding.btnExamAction.text = if (dateCompare != null && dateCompare < 0) {
                            "Belum Buka"
                        } else if (exam.startTime.isNotBlank()) {
                            "Jam ${exam.startTime}"
                        } else {
                            "Belum Buka"
                        }
                        binding.btnExamAction.isEnabled = false
                        binding.btnExamAction.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E2E8F0"))
                        binding.btnExamAction.setTextColor(Color.parseColor("#94A3B8"))
                    }
                    ExamScheduleHelper.ScheduleState.EXPIRED -> {
                        binding.btnExamAction.text = "Waktu Habis"
                        binding.btnExamAction.isEnabled = false
                        binding.btnExamAction.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEE2E2"))
                        binding.btnExamAction.setTextColor(Color.parseColor("#DC2626"))
                    }
                    ExamScheduleHelper.ScheduleState.ACTIVE -> {
                        if (item.status == ExamStatus.BELUM_DIUNDUH) {
                            binding.btnExamAction.text = "Unduh Soal"
                        } else {
                            binding.btnExamAction.text = "Mulai Ujian"
                        }
                        binding.btnExamAction.isEnabled = true
                        binding.btnExamAction.backgroundTintList = ColorStateList.valueOf(primaryColor)
                        binding.btnExamAction.setTextColor(Color.WHITE)
                    }
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
