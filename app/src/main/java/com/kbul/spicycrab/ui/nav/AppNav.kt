package com.kbul.spicycrab.ui.nav

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kbul.spicycrab.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kbul.spicycrab.ui.fasting.FastingScreen
import com.kbul.spicycrab.ui.food.FoodScreen
import com.kbul.spicycrab.ui.home.HomeScreen
import com.kbul.spicycrab.ui.onboarding.OpenSourceIntroScreen
import com.kbul.spicycrab.ui.settings.SettingsScreen
import com.kbul.spicycrab.ui.weight.WeightScreen
import com.kbul.spicycrab.ui.workout.WorkoutScreen
import androidx.core.content.ContextCompat

enum class TopLevelDest(val route: String, @StringRes val labelRes: Int, val icon: ImageVector) {
    Home("home", R.string.nav_home, Icons.Outlined.Home),
    Fasting("fasting", R.string.nav_fast, Icons.Outlined.AccessTime),
    Food("food", R.string.nav_food, Icons.Outlined.Restaurant),
    Weight("weight", R.string.nav_weight, Icons.Outlined.MonitorWeight),
    Workout("workout", R.string.nav_workout, Icons.Outlined.FitnessCenter),
    Settings("settings", R.string.nav_settings, Icons.Outlined.Settings),
}

@Composable
fun AppNav(viewModel: AppNavViewModel = hiltViewModel()) {
    val visibility by viewModel.visibility.collectAsStateWithLifecycle()
    val onboardingComplete by viewModel.onboardingComplete.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}

    LaunchedEffect(onboardingComplete) {
        if (
            onboardingComplete == true &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (onboardingComplete != true) {
        Surface {
            OpenSourceIntroScreen(
                loading = onboardingComplete == null,
                onContinue = viewModel::completeOnboarding,
            )
        }
        return
    }

    val visibleTabs = TopLevelDest.entries.filter {
        when (it) {
            TopLevelDest.Home, TopLevelDest.Settings -> true
            TopLevelDest.Fasting -> visibility.fasting
            TopLevelDest.Food -> visibility.food
            TopLevelDest.Weight -> visibility.weight
            TopLevelDest.Workout -> visibility.workout
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                visibleTabs.forEach { dest ->
                    val selected = backStack?.destination?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigateTo(navController, dest, currentRoute) },
                        icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
                        label = { Text(stringResource(dest.labelRes)) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDest.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(TopLevelDest.Home.route) {
                HomeScreen(
                    onNavigate = { dest -> navigateTo(navController, dest, currentRoute) }
                )
            }
            composable(TopLevelDest.Fasting.route) { FastingScreen() }
            composable(TopLevelDest.Food.route) { FoodScreen() }
            composable(TopLevelDest.Weight.route) { WeightScreen() }
            composable(TopLevelDest.Workout.route) { WorkoutScreen() }
            composable(TopLevelDest.Settings.route) { SettingsScreen() }
        }
    }
}

private fun navigateTo(navController: NavHostController, dest: TopLevelDest, currentRoute: String?) {
    if (currentRoute != dest.route) {
        navController.navigate(dest.route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}
