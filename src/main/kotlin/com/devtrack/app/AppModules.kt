package com.devtrack.app

import com.devtrack.data.database.DatabaseFactory
import com.devtrack.data.database.MigrationManager
import com.devtrack.infrastructure.security.KeyStore
import com.devtrack.infrastructure.security.KeyStoreFactory
import com.devtrack.data.repository.*
import com.devtrack.data.repository.impl.*
import com.devtrack.domain.service.*
import com.devtrack.infrastructure.export.DailyReportGenerator
import com.devtrack.infrastructure.export.MonthlyReportGenerator
import com.devtrack.infrastructure.export.StandupGenerator
import com.devtrack.infrastructure.export.WeeklyReportGenerator
import com.devtrack.infrastructure.backup.BackupService
import com.devtrack.infrastructure.logging.AuditLogger
import com.devtrack.infrastructure.notification.NotificationService
import com.devtrack.infrastructure.systray.SystemTrayService
import com.devtrack.ui.i18n.I18n
import com.devtrack.ui.navigation.NavigationState
import com.devtrack.viewmodel.BacklogViewModel
import com.devtrack.viewmodel.CalendarViewModel
import com.devtrack.viewmodel.CommandPaletteViewModel
import com.devtrack.viewmodel.ReportsViewModel
import com.devtrack.viewmodel.SettingsViewModel
import com.devtrack.viewmodel.TemplatesViewModel
import com.devtrack.viewmodel.TimelineViewModel
import com.devtrack.viewmodel.TodayViewModel
import org.koin.dsl.module

fun appModules() = listOf(
    databaseModule,
    repositoryModule,
    domainModule,
    viewModelModule,
    infrastructureModule,
)

val databaseModule = module {
    single<KeyStore> { KeyStoreFactory.create() }
    single { DatabaseFactory(get()) }
    single { MigrationManager(get()) }
}

val repositoryModule = module {
    single<TaskRepository> { TaskRepositoryImpl(get()) }
    single<WorkSessionRepository> { WorkSessionRepositoryImpl(get()) }
    single<SessionEventRepository> { SessionEventRepositoryImpl(get()) }
    single<TemplateTaskRepository> { TemplateTaskRepositoryImpl(get()) }
    single<UserSettingsRepository> { UserSettingsRepositoryImpl(get()) }
}

val domainModule = module {
    single { JiraTicketParser() }
    single { TimeCalculator() }
    single { SessionService(get(), get(), get(), get(), get()) }
    single { TaskService(get(), get(), get(), get()) }
    single { CommandPaletteService(get()) }
    // Phase 3: Report data aggregation and ticket aggregation
    single { ReportDataService(get(), get(), get(), get(), get()) }
    single { JiraAggregationService(get(), get(), get(), get(), get()) }
    // Phase 4: Template service
    single { TemplateService(get(), get(), get()) }
    // Phase 4: Pomodoro service
    single { PomodoroService(get(), get()) }
}

val viewModelModule = module {
    single { NavigationState() }
    single { TodayViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { BacklogViewModel(get(), get(), get(), get(), get()) }
    single { CommandPaletteViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    // Phase 3: Reports and Timeline ViewModels
    single { ReportsViewModel(get(), get(), get(), get(), get(), get()) }
    single { TimelineViewModel(get(), get(), get(), get()) }
    // Phase 4: Templates ViewModel
    single { TemplatesViewModel(get()) }
    // Phase 4: Settings ViewModel
    single { SettingsViewModel(get(), get(), get()) }
    // Phase 4: Calendar ViewModel
    single { CalendarViewModel(get(), get(), get(), get()) }
}

val infrastructureModule = module {
    single { AuditLogger() }
    single { I18n }
    single { DailyReportGenerator(get(), get(), get(), get(), get()) }
    // Phase 3: Report generators
    single { MonthlyReportGenerator(get(), get(), get()) }
    single { WeeklyReportGenerator(get(), get(), get()) }
    single { StandupGenerator(get(), get(), get(), get(), get()) }
    // Phase 4: System tray
    single { SystemTrayService() }
    // Phase 4: Backup service
    single { BackupService(get()) }
    // Phase 4: Notification service
    single { NotificationService(get()) }
}
