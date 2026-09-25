package io.github.rsclub22.tireservations.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.savedstate.read
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.rsclub22.tireservations.data.ReservationRepository
import io.github.rsclub22.tireservations.data.SettingsStore
import io.github.rsclub22.tireservations.data.UpdateChecker
import io.github.rsclub22.tireservations.ui.components.LoadingBox
import io.github.rsclub22.tireservations.ui.annahme.TelefonannahmeScreen
import io.github.rsclub22.tireservations.ui.detail.ReservationDetailScreen
import io.github.rsclub22.tireservations.ui.edit.ReservationEditScreen
import io.github.rsclub22.tireservations.ui.list.ReservationListScreen
import io.github.rsclub22.tireservations.ui.login.LoginScreen
import io.github.rsclub22.tireservations.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

private object Routes {
    const val LOGIN = "login"
    const val LIST = "list"
    const val DETAIL = "detail/{id}"
    const val EDIT = "edit?id={id}&date={date}"
    const val ANNAHME = "annahme"
    const val SETTINGS = "settings"

    fun detail(id: Long) = "detail/$id"
    fun edit(id: Long? = null, date: LocalDate? = null) = "edit?id=${id ?: -1}&date=${date ?: ""}"
}

@Composable
fun AppNavigation(
    repository: ReservationRepository,
    settingsStore: SettingsStore,
    updateChecker: UpdateChecker,
    /** Wird unter Einstellungen angezeigt; jede Plattform baut es selbst. */
    versionLabel: String,
) {
    // Decided once from the stored settings; afterwards navigation handles login/logout.
    var start by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        start = if (settingsStore.settings.first().isLoggedIn) Routes.LIST else Routes.LOGIN
    }

    when (val destination = start) {
        null -> LoadingBox()
        else -> AppNavHost(repository, settingsStore, updateChecker, versionLabel, destination)
    }
}

@Composable
private fun AppNavHost(
    repository: ReservationRepository,
    settingsStore: SettingsStore,
    updateChecker: UpdateChecker,
    versionLabel: String,
    startDestination: String,
    nav: NavHostController = rememberNavController(),
) {
    val scope = rememberCoroutineScope()
    val toLogin: () -> Unit = {
        scope.launch {
            // Also reached on HTTP 401: drop the invalid token so the next start shows the login.
            repository.logout()
            nav.navigate(Routes.LOGIN) { popUpTo(nav.graph.id) { inclusive = true } }
        }
    }

    NavHost(navController = nav, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(repository, settingsStore, onLoggedIn = {
                nav.navigate(Routes.LIST) { popUpTo(Routes.LOGIN) { inclusive = true } }
            })
        }
        composable(Routes.LIST) {
            ReservationListScreen(
                repository = repository,
                settingsStore = settingsStore,
                onOpen = { nav.navigate(Routes.detail(it)) },
                onCreate = { nav.navigate(Routes.edit(date = it)) },
                onAnnahme = { nav.navigate(Routes.ANNAHME) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onUnauthorized = toLogin,
            )
        }
        composable(Routes.ANNAHME) {
            TelefonannahmeScreen(
                repository = repository,
                onBack = { nav.popBackStack() },
                onUnauthorized = toLogin,
            )
        }
        composable(Routes.DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.read { getLongOrNull("id") } ?: return@composable
            ReservationDetailScreen(
                repository = repository,
                reservationId = id,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.edit(id = it)) },
                onUnauthorized = toLogin,
            )
        }
        composable(
            Routes.EDIT,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                navArgument("date") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val id = entry.arguments?.read { getLongOrNull("id") }?.takeIf { it > 0 }
            val date = entry.arguments?.read { getStringOrNull("date") }?.takeIf { it.isNotBlank() }
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ReservationEditScreen(
                repository = repository,
                settingsStore = settingsStore,
                reservationId = id,
                initialDate = date,
                onBack = { nav.popBackStack() },
                onSaved = { savedId, isNew ->
                    if (isNew) {
                        nav.navigate(Routes.detail(savedId)) { popUpTo(Routes.LIST) }
                    } else {
                        nav.popBackStack()
                    }
                },
                onUnauthorized = toLogin,
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                repository = repository,
                settingsStore = settingsStore,
                updateChecker = updateChecker,
                versionLabel = versionLabel,
                onBack = { nav.popBackStack() },
                onLoggedOut = {
                    nav.navigate(Routes.LOGIN) { popUpTo(nav.graph.id) { inclusive = true } }
                },
            )
        }
    }
}
