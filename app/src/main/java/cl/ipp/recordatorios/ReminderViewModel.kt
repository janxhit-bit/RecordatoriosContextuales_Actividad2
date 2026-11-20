package cl.ipp.recordatorios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ReminderListUiState {
    data object Loading : ReminderListUiState
    data object Empty : ReminderListUiState
    data class Content(val reminders: List<Reminder>) : ReminderListUiState
    data class Error(val message: String) : ReminderListUiState
}

data class ReminderFormUiState(
    val title: String = "",
    val description: String = "",
    val category: ReminderCategory? = null,
    val priority: Priority? = null,
    val titleError: String? = null,
    val descriptionError: String? = null,
    val categoryError: String? = "Selecciona una categoría.",
    val priorityError: String? = "Selecciona una prioridad.",
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val generalError: String? = null,
    val simulateSaveError: Boolean = false
) {
    val isValid: Boolean
        get() = title.trim().length >= 4 &&
            description.length <= 200 &&
            category != null && priority != null
}

class ReminderViewModel(private val repository: ReminderRepository) : ViewModel() {
    private val _listState = MutableStateFlow<ReminderListUiState>(ReminderListUiState.Loading)
    val listState: StateFlow<ReminderListUiState> = _listState.asStateFlow()

    private val _formState = MutableStateFlow(ReminderFormUiState())
    val formState: StateFlow<ReminderFormUiState> = _formState.asStateFlow()

    init { loadReminders() }

    fun loadReminders() {
        viewModelScope.launch {
            _listState.value = ReminderListUiState.Loading
            delay(350)
            runCatching { repository.getReminders() }
                .onSuccess { reminders ->
                    _listState.value = if (reminders.isEmpty()) ReminderListUiState.Empty
                    else ReminderListUiState.Content(reminders)
                }
                .onFailure { _listState.value = ReminderListUiState.Error("No fue posible cargar los recordatorios.") }
        }
    }

    fun simulateEmpty() { _listState.value = ReminderListUiState.Empty }
    fun simulateError() { _listState.value = ReminderListUiState.Error("Error simulado. Comprueba e inténtalo nuevamente.") }

    fun reminderById(id: Int, onResult: (Reminder?) -> Unit) {
        viewModelScope.launch { onResult(repository.getReminder(id)) }
    }

    fun updateTitle(value: String) = _formState.update {
        it.copy(title = value,
            titleError = if (value.trim().length < 4) "El título debe tener al menos 4 caracteres." else null,
            generalError = null)
    }
    fun updateDescription(value: String) = _formState.update {
        it.copy(description = value,
            descriptionError = if (value.length > 200) "La descripción no puede superar 200 caracteres." else null)
    }
    fun updateCategory(value: ReminderCategory) = _formState.update {
        it.copy(category = value, categoryError = null)
    }
    fun updatePriority(value: Priority) = _formState.update {
        it.copy(priority = value, priorityError = null)
    }
    fun toggleSaveError(value: Boolean) = _formState.update { it.copy(simulateSaveError = value) }

    fun resetForm() { _formState.value = ReminderFormUiState() }

    fun saveReminder() {
        val current = _formState.value
        val titleError = if (current.title.trim().length < 4) "El título debe tener al menos 4 caracteres." else null
        val descriptionError = if (current.description.length > 200) "La descripción no puede superar 200 caracteres." else null
        val categoryError = if (current.category == null) "Selecciona una categoría." else null
        val priorityError = if (current.priority == null) "Selecciona una prioridad." else null
        if (titleError != null || descriptionError != null || categoryError != null || priorityError != null) {
            _formState.value = current.copy(titleError = titleError, descriptionError = descriptionError,
                categoryError = categoryError, priorityError = priorityError)
            return
        }
        viewModelScope.launch {
            _formState.update { it.copy(isSaving = true, generalError = null) }
            delay(500)
            if (current.simulateSaveError) {
                _formState.update { it.copy(isSaving = false, generalError = "Error simulado al guardar. Desactiva la simulación e inténtalo nuevamente.") }
                return@launch
            }
            runCatching {
                repository.saveReminder(Reminder(0, current.title.trim(), current.description.trim().ifBlank { null },
                    current.category!!, current.priority!!, ReminderStatus.Pending))
            }.onSuccess {
                _formState.update { it.copy(isSaving = false, saved = true, generalError = null) }
                loadReminders()
            }.onFailure {
                _formState.update { it.copy(isSaving = false, generalError = "No se pudo guardar. Inténtalo nuevamente.") }
            }
        }
    }

    class Factory(private val repository: ReminderRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ReminderViewModel::class.java)) return ReminderViewModel(repository) as T
            throw IllegalArgumentException("ViewModel desconocido")
        }
    }
}
