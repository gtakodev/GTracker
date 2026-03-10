package com.devtrack.viewmodel

import com.devtrack.domain.model.TaskCategory
import com.devtrack.domain.model.TemplateTask
import com.devtrack.domain.service.TemplateService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID
import kotlin.coroutines.CoroutineContext

/**
 * UI state for the Templates screen (P4.2.1).
 */
data class TemplatesUiState(
    val templates: List<TemplateTask> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val snackbarMessage: String? = null,
    // Edit/Create dialog
    val showEditDialog: Boolean = false,
    val editingTemplate: TemplateTask? = null,
    val editTitle: String = "",
    val editCategory: TaskCategory = TaskCategory.DEVELOPMENT,
    val editDurationMin: String = "",
    // Delete confirmation
    val showDeleteConfirmation: Boolean = false,
    val deletingTemplate: TemplateTask? = null,
)

/**
 * ViewModel for the Templates screen (P4.2.1).
 * CRUD for templates and instantiation for a given day.
 */
class TemplatesViewModel(
    private val templateService: TemplateService,
    dispatcher: CoroutineContext = Dispatchers.Main,
) {
    private val logger = LoggerFactory.getLogger(TemplatesViewModel::class.java)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(TemplatesUiState(isLoading = true))
    val uiState: StateFlow<TemplatesUiState> = _uiState.asStateFlow()

    init {
        loadTemplates()
    }

    /** Load all templates from the repository. */
    fun loadTemplates() {
        scope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val templates = templateService.getAllTemplates()
                _uiState.update { it.copy(templates = templates, isLoading = false) }
            } catch (e: Exception) {
                logger.error("Failed to load templates", e)
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Unknown error") }
            }
        }
    }

    // -- Edit/Create Dialog --

    /** Open dialog to create a new template. */
    fun openCreateDialog() {
        _uiState.update {
            it.copy(
                showEditDialog = true,
                editingTemplate = null,
                editTitle = "",
                editCategory = TaskCategory.DEVELOPMENT,
                editDurationMin = "",
            )
        }
    }

    /** Open dialog to edit an existing template. */
    fun openEditDialog(template: TemplateTask) {
        _uiState.update {
            it.copy(
                showEditDialog = true,
                editingTemplate = template,
                editTitle = template.title,
                editCategory = template.category,
                editDurationMin = template.defaultDurationMin?.toString() ?: "",
            )
        }
    }

    fun closeEditDialog() {
        _uiState.update {
            it.copy(showEditDialog = false, editingTemplate = null)
        }
    }

    fun updateEditTitle(title: String) {
        _uiState.update { it.copy(editTitle = title) }
    }

    fun updateEditCategory(category: TaskCategory) {
        _uiState.update { it.copy(editCategory = category) }
    }

    fun updateEditDuration(duration: String) {
        // Only allow digits
        if (duration.isEmpty() || duration.all { it.isDigit() }) {
            _uiState.update { it.copy(editDurationMin = duration) }
        }
    }

    /** Save the template (create or update). */
    fun saveTemplate() {
        scope.launch {
            try {
                val state = _uiState.value
                val title = state.editTitle.trim()
                if (title.isBlank()) return@launch

                val durationMin = state.editDurationMin.toIntOrNull()

                if (state.editingTemplate != null) {
                    // Update existing
                    val updated = state.editingTemplate.copy(
                        title = title,
                        category = state.editCategory,
                        defaultDurationMin = durationMin,
                    )
                    templateService.updateTemplate(updated)
                    _uiState.update { it.copy(snackbarMessage = "templates.updated") }
                } else {
                    // Create new
                    templateService.createTemplate(title, state.editCategory, durationMin)
                    _uiState.update { it.copy(snackbarMessage = "templates.created") }
                }

                closeEditDialog()
                loadTemplates()
            } catch (e: Exception) {
                logger.error("Failed to save template", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // -- Delete --

    fun requestDelete(template: TemplateTask) {
        _uiState.update { it.copy(showDeleteConfirmation = true, deletingTemplate = template) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(showDeleteConfirmation = false, deletingTemplate = null) }
    }

    fun confirmDelete() {
        scope.launch {
            try {
                val template = _uiState.value.deletingTemplate ?: return@launch
                templateService.deleteTemplate(template.id)
                _uiState.update {
                    it.copy(
                        showDeleteConfirmation = false,
                        deletingTemplate = null,
                        snackbarMessage = "templates.deleted",
                    )
                }
                loadTemplates()
            } catch (e: Exception) {
                logger.error("Failed to delete template", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // -- Instantiation --

    /** Instantiate a template for today, creating a real planned task. */
    fun instantiateForToday(template: TemplateTask) {
        scope.launch {
            try {
                templateService.instantiate(template, LocalDate.now())
                _uiState.update { it.copy(snackbarMessage = "templates.instantiated") }
            } catch (e: Exception) {
                logger.error("Failed to instantiate template", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // -- Utility --

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun dispose() {
        scope.cancel()
    }
}
