package com.devtrack.viewmodel

import com.devtrack.data.repository.SessionEventRepository
import com.devtrack.data.repository.TaskRepository
import com.devtrack.data.repository.WorkSessionRepository
import com.devtrack.domain.model.*
import com.devtrack.domain.service.TaskService
import com.devtrack.domain.service.TimeCalculator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDate
import java.util.UUID
import kotlin.coroutines.CoroutineContext

/**
 * Sort options for the backlog view.
 */
enum class BacklogSortOption {
    CREATED_DESC,
    CREATED_ASC,
    TITLE_ASC,
    TITLE_DESC,
    CATEGORY,
}

/**
 * UI state for the Backlog screen (P2.1).
 */
data class BacklogUiState(
    val tasks: List<TaskWithTime> = emptyList(),
    val filteredTasks: List<TaskWithTime> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val selectedCategories: Set<TaskCategory> = emptySet(),
    val selectedStatuses: Set<TaskStatus> = setOf(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.PAUSED),
    val sortOption: BacklogSortOption = BacklogSortOption.CREATED_DESC,
    val selectedTaskIds: Set<UUID> = emptySet(),
    val isMultiSelectMode: Boolean = false,
    val quickCreateText: String = "",
    val selectedTask: Task? = null,
    val showTaskDetail: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val snackbarMessage: String? = null,
    val subTasks: List<Task> = emptyList(),
)

/**
 * ViewModel for the Backlog screen (P2.1.1).
 * Manages unplanned tasks with filtering, sorting, multi-select, and batch actions.
 */
class BacklogViewModel(
    private val taskService: TaskService,
    private val taskRepository: TaskRepository,
    private val sessionRepository: WorkSessionRepository,
    private val eventRepository: SessionEventRepository,
    private val timeCalculator: TimeCalculator,
    dispatcher: CoroutineContext = Dispatchers.Main,
) {
    private val logger = LoggerFactory.getLogger(BacklogViewModel::class.java)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(BacklogUiState(isLoading = true))
    val uiState: StateFlow<BacklogUiState> = _uiState.asStateFlow()

    init {
        loadTasks()
    }

    /**
     * Load all backlog tasks (unplanned, non-archived).
     */
    fun loadTasks() {
        scope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }

                val tasks = taskRepository.findBacklog()

                // Build TaskWithTime for each task
                val tasksWithTime = tasks.map { task ->
                    val sessions = sessionRepository.findByTaskId(task.id)
                    val eventsMap = sessions.associate { session ->
                        session.id to eventRepository.findBySessionId(session.id)
                    }
                    val totalDuration = timeCalculator.calculateTotalForTask(sessions, eventsMap)
                    val subTasks = taskRepository.findByParentId(task.id)

                    TaskWithTime(
                        task = task,
                        totalDuration = totalDuration,
                        sessionCount = sessions.size,
                        subTaskCount = subTasks.size,
                        completedSubTaskCount = subTasks.count { it.status == TaskStatus.DONE },
                        level = TaskLevel.BACKLOG,
                    )
                }

                _uiState.update {
                    it.copy(
                        tasks = tasksWithTime,
                        isLoading = false,
                    )
                }
                applyFiltersAndSort()
            } catch (e: Exception) {
                logger.error("Failed to load backlog tasks", e)
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Unknown error") }
            }
        }
    }

    // -- Filtering & Sorting --

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFiltersAndSort()
    }

    fun toggleCategoryFilter(category: TaskCategory) {
        _uiState.update { state ->
            val newCategories = if (category in state.selectedCategories) {
                state.selectedCategories - category
            } else {
                state.selectedCategories + category
            }
            state.copy(selectedCategories = newCategories)
        }
        applyFiltersAndSort()
    }

    fun toggleStatusFilter(status: TaskStatus) {
        _uiState.update { state ->
            val newStatuses = if (status in state.selectedStatuses) {
                state.selectedStatuses - status
            } else {
                state.selectedStatuses + status
            }
            state.copy(selectedStatuses = newStatuses)
        }
        applyFiltersAndSort()
    }

    fun clearFilters() {
        _uiState.update {
            it.copy(
                searchQuery = "",
                selectedCategories = emptySet(),
                selectedStatuses = setOf(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.PAUSED),
            )
        }
        applyFiltersAndSort()
    }

    fun setSortOption(option: BacklogSortOption) {
        _uiState.update { it.copy(sortOption = option) }
        applyFiltersAndSort()
    }

    private fun applyFiltersAndSort() {
        _uiState.update { state ->
            var filtered = state.tasks

            // Filter by search query
            if (state.searchQuery.isNotBlank()) {
                val query = state.searchQuery.lowercase()
                filtered = filtered.filter { twt ->
                    twt.task.title.lowercase().contains(query) ||
                        twt.task.jiraTickets.any { it.lowercase().contains(query) } ||
                        twt.task.description?.lowercase()?.contains(query) == true
                }
            }

            // Filter by categories (if any selected)
            if (state.selectedCategories.isNotEmpty()) {
                filtered = filtered.filter { it.task.category in state.selectedCategories }
            }

            // Filter by statuses
            if (state.selectedStatuses.isNotEmpty()) {
                filtered = filtered.filter { it.task.status in state.selectedStatuses }
            }

            // Sort
            val sorted = when (state.sortOption) {
                BacklogSortOption.CREATED_DESC -> filtered.sortedByDescending { it.task.createdAt }
                BacklogSortOption.CREATED_ASC -> filtered.sortedBy { it.task.createdAt }
                BacklogSortOption.TITLE_ASC -> filtered.sortedBy { it.task.title.lowercase() }
                BacklogSortOption.TITLE_DESC -> filtered.sortedByDescending { it.task.title.lowercase() }
                BacklogSortOption.CATEGORY -> filtered.sortedBy { it.task.category.name }
            }

            state.copy(filteredTasks = sorted)
        }
    }

    // -- Multi-select --

    fun toggleMultiSelectMode() {
        _uiState.update { state ->
            if (state.isMultiSelectMode) {
                state.copy(isMultiSelectMode = false, selectedTaskIds = emptySet())
            } else {
                state.copy(isMultiSelectMode = true)
            }
        }
    }

    fun toggleTaskSelection(taskId: UUID) {
        _uiState.update { state ->
            val newSelection = if (taskId in state.selectedTaskIds) {
                state.selectedTaskIds - taskId
            } else {
                state.selectedTaskIds + taskId
            }
            state.copy(selectedTaskIds = newSelection)
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(selectedTaskIds = state.filteredTasks.map { it.task.id }.toSet())
        }
    }

    fun deselectAll() {
        _uiState.update { it.copy(selectedTaskIds = emptySet()) }
    }

    // -- Batch Actions --

    /**
     * Plan all selected tasks for today.
     */
    fun batchPlanToday() {
        scope.launch {
            try {
                val ids = _uiState.value.selectedTaskIds
                ids.forEach { taskService.planTask(it, LocalDate.now()) }
                _uiState.update {
                    it.copy(
                        selectedTaskIds = emptySet(),
                        isMultiSelectMode = false,
                        snackbarMessage = "backlog.batch_planned",
                    )
                }
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to batch plan tasks", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Plan all selected tasks for a specific date.
     */
    fun batchPlanForDate(date: LocalDate) {
        scope.launch {
            try {
                val ids = _uiState.value.selectedTaskIds
                ids.forEach { taskService.planTask(it, date) }
                _uiState.update {
                    it.copy(
                        selectedTaskIds = emptySet(),
                        isMultiSelectMode = false,
                        snackbarMessage = "backlog.batch_planned",
                    )
                }
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to batch plan tasks", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Archive all selected tasks.
     */
    fun batchArchive() {
        scope.launch {
            try {
                val ids = _uiState.value.selectedTaskIds
                ids.forEach { taskService.changeStatus(it, TaskStatus.ARCHIVED) }
                _uiState.update {
                    it.copy(
                        selectedTaskIds = emptySet(),
                        isMultiSelectMode = false,
                        snackbarMessage = "backlog.batch_archived",
                    )
                }
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to batch archive tasks", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Delete all selected tasks.
     */
    fun batchDelete() {
        scope.launch {
            try {
                val ids = _uiState.value.selectedTaskIds
                ids.forEach { taskService.deleteTask(it) }
                _uiState.update {
                    it.copy(
                        selectedTaskIds = emptySet(),
                        isMultiSelectMode = false,
                        snackbarMessage = "backlog.batch_deleted",
                    )
                }
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to batch delete tasks", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // -- Single Task Actions --

    /**
     * Plan a single task for today.
     */
    fun planTaskToday(taskId: UUID) {
        scope.launch {
            try {
                taskService.planTask(taskId, LocalDate.now())
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to plan task", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Send a task to backlog (remove planned date).
     */
    fun sendToBacklog(taskId: UUID) {
        scope.launch {
            try {
                taskService.unplanTask(taskId)
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to send task to backlog", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Quick-create a task in the backlog (no planned date).
     */
    fun quickCreateTask() {
        scope.launch {
            try {
                val title = _uiState.value.quickCreateText.trim()
                if (title.isBlank()) return@launch

                taskService.createTask(title, plannedDate = null)
                _uiState.update { it.copy(quickCreateText = "") }
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to create backlog task", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun updateQuickCreateText(text: String) {
        _uiState.update { it.copy(quickCreateText = text) }
    }

    // -- Task Detail --

    fun openTaskDetail(task: Task) {
        scope.launch {
            val subTasks = taskService.getSubTasks(task.id)
            _uiState.update { it.copy(selectedTask = task, showTaskDetail = true, subTasks = subTasks) }
        }
    }

    fun closeTaskDetail() {
        _uiState.update { it.copy(selectedTask = null, showTaskDetail = false, showDeleteConfirmation = false, subTasks = emptyList()) }
    }

    fun saveTask(task: Task) {
        scope.launch {
            try {
                taskService.updateTask(task)
                closeTaskDetail()
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to save task", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun requestDeleteTask() {
        _uiState.update { it.copy(showDeleteConfirmation = true) }
    }

    fun confirmDeleteTask() {
        scope.launch {
            try {
                val taskId = _uiState.value.selectedTask?.id ?: return@launch
                taskService.deleteTask(taskId)
                closeTaskDetail()
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to delete task", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // -- Sub-task operations (P2.5) --

    fun createSubTask(title: String) {
        scope.launch {
            try {
                val parentId = _uiState.value.selectedTask?.id ?: return@launch
                taskService.createSubTask(parentId, title)
                val subTasks = taskService.getSubTasks(parentId)
                _uiState.update { it.copy(subTasks = subTasks) }
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to create sub-task", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun deleteSubTask(subTaskId: UUID) {
        scope.launch {
            try {
                taskService.deleteTask(subTaskId)
                val parentId = _uiState.value.selectedTask?.id ?: return@launch
                val subTasks = taskService.getSubTasks(parentId)
                _uiState.update { it.copy(subTasks = subTasks) }
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to delete sub-task", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun toggleSubTaskDone(subTask: Task) {
        scope.launch {
            try {
                val newStatus = if (subTask.status == TaskStatus.DONE) TaskStatus.TODO else TaskStatus.DONE
                taskService.changeStatus(subTask.id, newStatus)
                val parentId = _uiState.value.selectedTask?.id ?: return@launch
                val subTasks = taskService.getSubTasks(parentId)
                _uiState.update { it.copy(subTasks = subTasks) }
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to toggle sub-task status", e)
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
