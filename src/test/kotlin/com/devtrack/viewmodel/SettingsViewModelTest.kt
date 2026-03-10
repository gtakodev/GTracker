package com.devtrack.viewmodel

import com.devtrack.data.repository.UserSettingsRepository
import com.devtrack.domain.model.UserSettings
import com.devtrack.ui.i18n.I18n
import com.devtrack.ui.navigation.NavigationState
import com.devtrack.ui.theme.ThemeMode
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * Unit tests for SettingsViewModel (P4.6.4).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val navigationState = NavigationState()

    private lateinit var viewModel: SettingsViewModel

    @BeforeAll
    fun setupAll() {
        Dispatchers.setMain(testDispatcher)
        // Mock I18n static calls
        mockkObject(I18n)
        every { I18n.setLocale(any<String>()) } just runs
    }

    @AfterAll
    fun tearDownAll() {
        Dispatchers.resetMain()
        unmockkObject(I18n)
    }

    @BeforeEach
    fun setup() {
        clearAllMocks()
        every { I18n.setLocale(any<String>()) } just runs
    }

    private fun createViewModel(settings: UserSettings = UserSettings()): SettingsViewModel {
        coEvery { userSettingsRepository.get() } returns settings
        return SettingsViewModel(userSettingsRepository, navigationState, dispatcher = testDispatcher)
    }

    @Test
    fun `loadSettings populates UI state from repository`() = runTest {
        val settings = UserSettings(
            locale = "en",
            theme = "DARK",
            inactivityThresholdMin = 45,
            pomodoroWorkMin = 30,
            pomodoroBreakMin = 10,
            pomodoroLongBreakMin = 20,
            pomodoroSessionsBeforeLong = 6,
            hoursPerDay = 7.5,
            halfDayThreshold = 3.5,
        )
        viewModel = createViewModel(settings)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(ThemeMode.DARK, state.theme)
        assertEquals("en", state.locale)
        assertEquals(45, state.inactivityThresholdMin)
        assertEquals(30, state.pomodoroWorkMin)
        assertEquals(10, state.pomodoroBreakMin)
        assertEquals(20, state.pomodoroLongBreakMin)
        assertEquals(6, state.pomodoroSessionsBeforeLong)
        assertEquals("7.5", state.hoursPerDay)
        assertEquals("3.5", state.halfDayThreshold)
    }

    @Test
    fun `loadSettings applies theme to NavigationState`() = runTest {
        viewModel = createViewModel(UserSettings(theme = "DARK"))
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, navigationState.themeMode.value)
    }

    @Test
    fun `loadSettings applies locale via I18n`() = runTest {
        viewModel = createViewModel(UserSettings(locale = "en"))
        advanceUntilIdle()

        verify { I18n.setLocale("en") }
    }

    @Test
    fun `loadSettings defaults to SYSTEM for invalid theme`() = runTest {
        viewModel = createViewModel(UserSettings(theme = "INVALID"))
        advanceUntilIdle()

        assertEquals(ThemeMode.SYSTEM, viewModel.uiState.value.theme)
    }

    @Test
    fun `setTheme updates UI state and saves`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setTheme(ThemeMode.LIGHT)
        advanceUntilIdle()

        assertEquals(ThemeMode.LIGHT, viewModel.uiState.value.theme)
        assertEquals(ThemeMode.LIGHT, navigationState.themeMode.value)
        coVerify { userSettingsRepository.save(match { it.theme == "LIGHT" }) }
    }

    @Test
    fun `setLocale updates UI state and calls I18n`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setLocale("en")
        advanceUntilIdle()

        assertEquals("en", viewModel.uiState.value.locale)
        verify { I18n.setLocale("en") }
        coVerify { userSettingsRepository.save(match { it.locale == "en" }) }
    }

    @Test
    fun `setInactivityThreshold clamps and saves`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Within range
        viewModel.setInactivityThreshold(60)
        advanceUntilIdle()
        assertEquals(60, viewModel.uiState.value.inactivityThresholdMin)

        // Below min (5)
        viewModel.setInactivityThreshold(2)
        advanceUntilIdle()
        assertEquals(5, viewModel.uiState.value.inactivityThresholdMin)

        // Above max (120)
        viewModel.setInactivityThreshold(200)
        advanceUntilIdle()
        assertEquals(120, viewModel.uiState.value.inactivityThresholdMin)
    }

    @Test
    fun `setPomodoroWorkMin clamps to 15-60 range`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setPomodoroWorkMin(45)
        advanceUntilIdle()
        assertEquals(45, viewModel.uiState.value.pomodoroWorkMin)
        coVerify { userSettingsRepository.save(match { it.pomodoroWorkMin == 45 }) }

        // Below min
        viewModel.setPomodoroWorkMin(5)
        advanceUntilIdle()
        assertEquals(15, viewModel.uiState.value.pomodoroWorkMin)
    }

    @Test
    fun `setPomodoroBreakMin clamps to 1-15 range`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setPomodoroBreakMin(10)
        advanceUntilIdle()
        assertEquals(10, viewModel.uiState.value.pomodoroBreakMin)

        viewModel.setPomodoroBreakMin(20)
        advanceUntilIdle()
        assertEquals(15, viewModel.uiState.value.pomodoroBreakMin)
    }

    @Test
    fun `setPomodoroLongBreakMin clamps to 5-30 range`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setPomodoroLongBreakMin(25)
        advanceUntilIdle()
        assertEquals(25, viewModel.uiState.value.pomodoroLongBreakMin)

        viewModel.setPomodoroLongBreakMin(0)
        advanceUntilIdle()
        assertEquals(5, viewModel.uiState.value.pomodoroLongBreakMin)
    }

    @Test
    fun `setPomodoroSessionsBeforeLong clamps to 1-8 range`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setPomodoroSessionsBeforeLong(6)
        advanceUntilIdle()
        assertEquals(6, viewModel.uiState.value.pomodoroSessionsBeforeLong)

        viewModel.setPomodoroSessionsBeforeLong(12)
        advanceUntilIdle()
        assertEquals(8, viewModel.uiState.value.pomodoroSessionsBeforeLong)
    }

    @Test
    fun `setHoursPerDay saves valid values`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setHoursPerDay("7.5")
        advanceUntilIdle()

        assertEquals("7.5", viewModel.uiState.value.hoursPerDay)
        coVerify { userSettingsRepository.save(match { it.hoursPerDay == 7.5 }) }
    }

    @Test
    fun `setHoursPerDay does not save invalid values`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        clearAllMocks()
        every { I18n.setLocale(any<String>()) } just runs

        viewModel.setHoursPerDay("abc")
        advanceUntilIdle()

        assertEquals("abc", viewModel.uiState.value.hoursPerDay)
        coVerify(exactly = 0) { userSettingsRepository.save(any()) }
    }

    @Test
    fun `setHalfDayThreshold saves valid values`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setHalfDayThreshold("3.5")
        advanceUntilIdle()

        assertEquals("3.5", viewModel.uiState.value.halfDayThreshold)
        coVerify { userSettingsRepository.save(match { it.halfDayThreshold == 3.5 }) }
    }

    @Test
    fun `dismissError clears error`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.dismissError()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `loadSettings sets error on failure`() = runTest {
        coEvery { userSettingsRepository.get() } throws RuntimeException("DB fail")
        viewModel = SettingsViewModel(userSettingsRepository, navigationState, dispatcher = testDispatcher)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("DB fail", viewModel.uiState.value.error)
    }
}
