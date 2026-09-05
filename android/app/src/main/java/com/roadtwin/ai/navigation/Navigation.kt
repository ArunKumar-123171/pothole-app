package com.roadtwin.ai.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.roadtwin.ai.feature.camera.CameraScreen
import com.roadtwin.ai.feature.dashboard.DashboardScreen
import com.roadtwin.ai.feature.map.MapScreen
import com.roadtwin.ai.feature.onboarding.OnboardingScreen
import com.roadtwin.ai.feature.onboarding.SplashScreen
import com.roadtwin.ai.feature.profile.AboutScreen
import com.roadtwin.ai.feature.profile.ProfileScreen
import com.roadtwin.ai.feature.reports.EditReportScreen
import com.roadtwin.ai.feature.reports.GeneratePdfScreen
import com.roadtwin.ai.feature.reports.PotholeDetailScreen
import com.roadtwin.ai.feature.reports.ReportSummaryScreen
import com.roadtwin.ai.feature.reports.ReportsScreen
import com.roadtwin.ai.feature.settings.BenchmarksScreen
import com.roadtwin.ai.feature.settings.SettingsScreen

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(Splash)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Splash> {
                SplashScreen(onNavigateToDashboard = {
                    backStack.removeLastOrNull()
                    backStack.add(Dashboard)
                })
            }
            entry<Onboarding> {
                OnboardingScreen(onNavigateToDashboard = {
                    backStack.removeLastOrNull()
                    backStack.add(Dashboard)
                })
            }
            entry<Dashboard> {
                DashboardScreen(
                    onNavigateToCamera = { backStack.add(LiveCamera) },
                    onNavigateToMap = { backStack.add(MapView) },
                    onNavigateToReports = { backStack.add(ReportsHistory) },
                    onNavigateToDetail = { sessionId -> backStack.add(ReportDetail(sessionId)) },
                    onNavigateToSettings = { backStack.add(Settings) },
                    onNavigateToProfile = { backStack.add(Profile) }
                )
            }
            entry<LiveCamera> {
                CameraScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onNavigateToSummary = { sessionId ->
                        backStack.removeLastOrNull()
                        backStack.add(ReportSummary(sessionId))
                    }
                )
            }
            entry<ReportSummary> { key ->
                ReportSummaryScreen(
                    sessionId = key.sessionId,
                    onBack = { backStack.removeLastOrNull() },
                    onNavigateToEdit = { sid -> backStack.add(EditReport(sid)) },
                    onNavigateToMap = { backStack.add(MapView) },
                    onNavigateToReports = {
                        backStack.removeLastOrNull()
                        backStack.add(ReportsHistory)
                    }
                )
            }
            entry<EditReport> { key ->
                EditReportScreen(
                    sessionId = key.sessionId,
                    onBack = { backStack.removeLastOrNull() },
                    onSaved = { backStack.removeLastOrNull() }
                )
            }
            entry<MapView> {
                MapScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onNavigateToDashboard = { backStack.add(Dashboard) },
                    onNavigateToCamera = { backStack.add(LiveCamera) },
                    onNavigateToReports = { backStack.add(ReportsHistory) },
                    onNavigateToDetail = { sid -> backStack.add(ReportDetail(sid)) },
                    onNavigateToSettings = { backStack.add(Settings) },
                    onNavigateToProfile = { backStack.add(Profile) }
                )
            }
            entry<ReportsHistory> {
                ReportsScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onNavigateToDetail = { sessionId -> backStack.add(ReportDetail(sessionId)) },
                    onNavigateToDashboard = { backStack.add(Dashboard) },
                    onNavigateToMap = { backStack.add(MapView) },
                    onNavigateToCamera = { backStack.add(LiveCamera) },
                    onNavigateToSettings = { backStack.add(Settings) },
                    onNavigateToProfile = { backStack.add(Profile) }
                )
            }
            entry<ReportDetail> { key ->
                PotholeDetailScreen(
                    sessionId = key.sessionId,
                    onBack = { backStack.removeLastOrNull() },
                    onNavigateToEdit = { sid -> backStack.add(EditReport(sid)) },
                    onNavigateToMap = { backStack.add(MapView) },
                    onNavigateToGeneratePdf = { sid -> backStack.add(GeneratePdf(sid)) }
                )
            }
            entry<GeneratePdf> { key ->
                GeneratePdfScreen(
                    sessionId = key.sessionId,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<Settings> {
                SettingsScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onNavigateToDashboard = { backStack.add(Dashboard) },
                    onNavigateToMap = { backStack.add(MapView) },
                    onNavigateToCamera = { backStack.add(LiveCamera) },
                    onNavigateToReports = { backStack.add(ReportsHistory) },
                    onNavigateToProfile = { backStack.add(Profile) }
                )
            }
            entry<Benchmarks> {
                BenchmarksScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onNavigateToDashboard = { backStack.add(Dashboard) },
                    onNavigateToMap = { backStack.add(MapView) },
                    onNavigateToCamera = { backStack.add(LiveCamera) },
                    onNavigateToReports = { backStack.add(ReportsHistory) },
                    onNavigateToSettings = { backStack.add(Settings) },
                    onNavigateToProfile = { backStack.add(Profile) }
                )
            }
            entry<Profile> {
                ProfileScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onNavigateToDashboard = { backStack.add(Dashboard) },
                    onNavigateToMap = { backStack.add(MapView) },
                    onNavigateToCamera = { backStack.add(LiveCamera) },
                    onNavigateToReports = { backStack.add(ReportsHistory) },
                    onNavigateToSettings = { backStack.add(Settings) },
                    onNavigateToBenchmarks = { backStack.add(Benchmarks) },
                    onNavigateToAbout = { backStack.add(About) }
                )
            }
            entry<About> {
                AboutScreen(
                    onBack = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}
