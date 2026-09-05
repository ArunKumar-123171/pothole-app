package com.roadtwin.ai.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object Splash : NavKey

@Serializable
data object Onboarding : NavKey

@Serializable
data object Dashboard : NavKey

@Serializable
data object LiveCamera : NavKey

@Serializable
data class ReportSummary(
    val sessionId: String
) : NavKey

@Serializable
data class EditReport(
    val sessionId: String
) : NavKey

@Serializable
data object MapView : NavKey

@Serializable
data object ReportsHistory : NavKey

@Serializable
data class ReportDetail(
    val sessionId: String
) : NavKey

@Serializable
data class GeneratePdf(
    val sessionId: String
) : NavKey

@Serializable
data object Settings : NavKey

@Serializable
data object Benchmarks : NavKey

@Serializable
data object Profile : NavKey

@Serializable
data object About : NavKey
