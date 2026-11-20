package cl.ipp.recordatorios

interface ReminderRepository {
    suspend fun getReminders(): List<Reminder>
    suspend fun getReminder(id: Int): Reminder?
    suspend fun saveReminder(reminder: Reminder): Reminder
}

class InMemoryReminderRepository : ReminderRepository {
    private val items = mutableListOf(
        Reminder(1, "Entregar actividad de Kotlin", "Revisar el proyecto y subir el PDF.",
            ReminderCategory.STUDY, Priority.HIGH, ReminderStatus.Pending),
        Reminder(2, "Comprar alimentos", "Preparar la lista para la semana.",
            ReminderCategory.PERSONAL, Priority.MEDIUM, ReminderStatus.InProgress),
        Reminder(3, "Repasar Jetpack Compose", "Practicar componentes y estados.",
            ReminderCategory.STUDY, Priority.HIGH, ReminderStatus.Pending),
        Reminder(4, "Caminar por la tarde", "Realizar una caminata.",
            ReminderCategory.HEALTH, Priority.LOW, ReminderStatus.Completed),
        Reminder(5, "Revisar correo", "Responder mensajes importantes.",
            ReminderCategory.WORK, Priority.MEDIUM, ReminderStatus.Pending)
    )

    override suspend fun getReminders(): List<Reminder> = items.toList()
    override suspend fun getReminder(id: Int): Reminder? = items.firstOrNull { it.id == id }

    override suspend fun saveReminder(reminder: Reminder): Reminder {
        val next = reminder.copy(id = (items.maxOfOrNull { it.id } ?: 0) + 1)
        items.add(next)
        return next
    }
}
