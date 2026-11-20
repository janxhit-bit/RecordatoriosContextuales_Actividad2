package cl.ipp.recordatorios

data class Reminder(
    val id: Int,
    val title: String,
    val description: String? = null,
    val category: ReminderCategory,
    val priority: Priority,
    val status: ReminderStatus,
    val location: String? = null
)

enum class ReminderCategory(val label: String) {
    PERSONAL("Personal"), STUDY("Estudio"), WORK("Trabajo"), HEALTH("Salud")
}

enum class Priority(val label: String) {
    LOW("Baja"), MEDIUM("Media"), HIGH("Alta")
}

sealed class ReminderStatus(val label: String) {
    data object Pending : ReminderStatus("Pendiente")
    data object InProgress : ReminderStatus("En progreso")
    data object Completed : ReminderStatus("Completado")
}
