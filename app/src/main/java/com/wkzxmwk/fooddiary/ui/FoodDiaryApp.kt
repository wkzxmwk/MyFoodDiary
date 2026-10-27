package com.wkzxmwk.fooddiary.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wkzxmwk.fooddiary.ui.navigation.AppDestination
import com.wkzxmwk.fooddiary.FoodDiaryApplication
import com.wkzxmwk.fooddiary.ui.today.TodayScreen
import com.wkzxmwk.fooddiary.ui.today.TodayViewModel
import com.wkzxmwk.fooddiary.ui.today.TodayViewModelFactory
import com.wkzxmwk.fooddiary.ui.history.HistoryScreen
import com.wkzxmwk.fooddiary.ui.history.HistoryViewModel
import com.wkzxmwk.fooddiary.ui.history.HistoryViewModelFactory
import com.wkzxmwk.fooddiary.ui.detail.RecordDetailScreen
import com.wkzxmwk.fooddiary.ui.detail.RecordDetailViewModel
import com.wkzxmwk.fooddiary.ui.detail.RecordDetailViewModelFactory
import java.time.LocalDate
import com.wkzxmwk.fooddiary.ui.trend.TrendScreen
import com.wkzxmwk.fooddiary.ui.trend.TrendViewModel
import com.wkzxmwk.fooddiary.ui.trend.TrendViewModelFactory
import com.wkzxmwk.fooddiary.ui.settings.SettingsScreen
import com.wkzxmwk.fooddiary.ui.settings.SettingsViewModel
import com.wkzxmwk.fooddiary.ui.settings.SettingsViewModelFactory
import com.wkzxmwk.fooddiary.domain.model.AppSettings
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@Composable
fun FoodDiaryApp() {
    val navController = rememberNavController()
    val application = LocalContext.current.applicationContext as FoodDiaryApplication
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settings by application.container.settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = AppSettings(),
    )
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        coroutineScope.launch {
            application.container.settingsRepository.setNotificationPermissionRequested(true)
        }
    }
    LaunchedEffect(settings.remindersEnabled, settings.notificationPermissionRequested) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            settings.remindersEnabled &&
            !settings.notificationPermissionRequested &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination
    val todayViewModel: TodayViewModel = viewModel(
        factory = TodayViewModelFactory(
            application.container.foodDiaryRepository,
            application.container.settingsRepository,
            application.container.imageManager,
        ),
    )

    LaunchedEffect(currentDestination?.route) {
        if (currentDestination?.route == AppDestination.Today.route) {
            todayViewModel.syncToToday()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                AppDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true ||
                        (destination == AppDestination.History && currentDestination?.route?.startsWith("record/") == true)
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(AppDestination.Today.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Today.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(AppDestination.Today.route) {
                val state by todayViewModel.uiState.collectAsStateWithLifecycle()
                TodayScreen(
                    state = state,
                    onSelectDate = todayViewModel::selectDate,
                    onSaveWeight = todayViewModel::saveWeight,
                    onLoadCropPreview = todayViewModel::loadCropPreview,
                    onAddCroppedPhoto = todayViewModel::addCroppedPhoto,
                    onDiscardPendingImage = todayViewModel::discardPendingImage,
                    onCreateCameraUri = todayViewModel::createCameraUri,
                    onCreateDailyShareImage = todayViewModel::createDailyShareImage,
                    onDeletePhoto = todayViewModel::deletePhoto,
                )
            }
            composable(AppDestination.History.route) {
                val viewModel: HistoryViewModel = viewModel(factory = HistoryViewModelFactory(application.container.foodDiaryRepository))
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                HistoryScreen(
                    state = state,
                    selectPeriod = viewModel::selectPeriod,
                    previousPeriod = viewModel::previousPeriod,
                    nextPeriod = viewModel::nextPeriod,
                    selectDate = viewModel::selectDate,
                    selectCustomRange = viewModel::selectCustomRange,
                    openRecord = { record -> navController.navigate("record/${record.date.toEpochDay()}") },
                )
            }
            composable("record/{epochDay}") { entry ->
                val date = LocalDate.ofEpochDay(entry.arguments?.getString("epochDay")!!.toLong())
                val viewModel: RecordDetailViewModel = viewModel(
                    key = "record-${date.toEpochDay()}",
                    factory = RecordDetailViewModelFactory(date, application.container.foodDiaryRepository, application.container.settingsRepository, application.container.imageManager),
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                RecordDetailScreen(
                    state = state,
                    onBack = navController::popBackStack,
                    onDeleted = navController::popBackStack,
                    updateWeights = viewModel::updateWeights,
                    updateNote = viewModel::updateNote,
                    loadCropPreview = viewModel::loadCropPreview,
                    addCroppedPhoto = viewModel::addCroppedPhoto,
                    discardPendingImage = viewModel::discardPendingImage,
                    createCameraUri = viewModel::createCameraUri,
                    deletePhoto = viewModel::deletePhoto,
                    deleteRecord = viewModel::deleteRecord,
                )
            }
            composable(AppDestination.Trend.route) {
                val viewModel: TrendViewModel = viewModel(factory = TrendViewModelFactory(application.container.foodDiaryRepository, application.container.settingsRepository))
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                TrendScreen(state, viewModel::selectRange)
            }
            composable(AppDestination.Settings.route) {
                val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(application.container.settingsRepository, application.container.foodDiaryRepository, application.container.imageManager, application.container.backupManager))
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SettingsScreen(
                    state,
                    viewModel::setTargetWeight,
                    viewModel::setHeightCm,
                    viewModel::setGender,
                    viewModel::setRemindersEnabled,
                    viewModel::setReminderTime,
                    viewModel::setImageQuality,
                    viewModel::export,
                    viewModel::import,
                    viewModel::clearAll,
                    viewModel::consumeMessage,
                )
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
    }
}
