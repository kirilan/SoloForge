package com.kbul.spicycrab.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.util.Locale
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import com.kbul.spicycrab.BuildConfig
import com.kbul.spicycrab.R
import com.kbul.spicycrab.data.backup.AutoBackupStatus
import com.kbul.spicycrab.data.prefs.NutritionGoals
import com.kbul.spicycrab.domain.fasting.FastingMode
import com.kbul.spicycrab.domain.nutrition.AnalysisConfig
import com.kbul.spicycrab.domain.nutrition.FoodAnalysisModels

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val hasKey by viewModel.hasKey.collectAsStateWithLifecycle()
    val exportMessage by viewModel.exportMessage.collectAsStateWithLifecycle()
    val autoBackupStatus by viewModel.autoBackupStatus.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val s = settings ?: return

    LaunchedEffect(exportMessage) {
        exportMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearExportMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)

        SectionCard(stringResource(R.string.fasting_title)) {
            DefaultModeDropdown(s.defaultFastingModeName, viewModel::setDefaultFastingMode)
            SwitchRow(
                stringResource(R.string.settings_almost_there),
                s.almostThereEnabled,
                viewModel::setAlmostThereEnabled,
            )
            SwitchRow(
                stringResource(R.string.settings_eating_window_reminder),
                s.eatingWindowClosingEnabled,
                viewModel::setEatingWindowClosingEnabled,
            )
        }

        SectionCard(stringResource(R.string.settings_section_ai)) {
            SwitchRow(stringResource(R.string.settings_ai_food_analysis), s.aiFeaturesEnabled, viewModel::setAiFeaturesEnabled)
            if (s.aiFeaturesEnabled) {
                ApiKeyField(hasKey, viewModel::setApiKey, viewModel::clearApiKey)
                Text(
                    stringResource(R.string.settings_ai_chain_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AnalysisModelPicker(
                    selectedToken = s.foodAnalysisModel,
                    customModelId = s.foodAnalysisModelCustomId,
                    onSelect = viewModel::setFoodAnalysisModel,
                    onCustomIdChange = viewModel::setFoodAnalysisModelCustomId,
                )
            } else {
                Text(
                    stringResource(R.string.settings_ai_off_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(stringResource(R.string.settings_section_goals)) {
            GoalsForm(s.goals, viewModel::setGoals)
        }

        SectionCard(stringResource(R.string.settings_section_data)) {
            ExportFolderRow(s.exportFolderUri, autoBackupStatus, viewModel::setExportFolder)
            SwitchRow(stringResource(R.string.settings_save_photo), s.savePhotoLocally, viewModel::setSavePhotoLocally)
            BackupButtons(
                onExport = viewModel::exportBackup,
                onImport = viewModel::importBackup,
            )
        }

        SectionCard(stringResource(R.string.nav_weight)) {
            SwitchRow(stringResource(R.string.settings_use_kg), s.weightUnitKg, viewModel::setWeightUnitKg)
            SwitchRow(stringResource(R.string.settings_weigh_in_reminder), s.weighInEnabled, viewModel::setWeighInEnabled)
            if (s.weighInEnabled) {
                WeighInTimeRow(
                    dayOfWeek = s.weighInDayOfWeek,
                    hour = s.weighInHour,
                    minute = s.weighInMinute,
                    onChange = viewModel::setWeighInTime,
                )
            }
        }

        if (viewModel.healthConnectAvailable) {
            HealthConnectSection(s.healthImportEnabled, s.healthExportEnabled, s.healthLastSyncEpoch, viewModel)
        }

        SectionCard(stringResource(R.string.settings_section_tabs)) {
            Text(
                stringResource(R.string.settings_tabs_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SwitchRow(stringResource(R.string.settings_show_fasting_tab), s.showFastingTab, viewModel::setShowFastingTab)
            SwitchRow(stringResource(R.string.settings_show_food_tab), s.showFoodTab, viewModel::setShowFoodTab)
            SwitchRow(stringResource(R.string.settings_show_weight_tab), s.showWeightTab, viewModel::setShowWeightTab)
            SwitchRow(stringResource(R.string.settings_show_workout_tab), s.showWorkoutTab, viewModel::setShowWorkoutTab)
        }

        SectionCard(stringResource(R.string.settings_section_about)) {
            Text(
                stringResource(R.string.settings_about_line1),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(R.string.settings_about_line2),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val uriHandler = LocalUriHandler.current
            OutlinedButton(onClick = { uriHandler.openUri("https://soloforge.dimitroff.work/privacy") }) {
                Text(stringResource(R.string.settings_privacy_policy))
            }
            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            stringResource(R.string.settings_gpl_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }
}

@Composable
private fun DefaultModeDropdown(currentName: String, onSelected: (String) -> Unit) {
    val current = FastingMode.fromName(currentName)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.settings_default_mode), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FastingMode.entries.forEach { mode ->
                androidx.compose.material3.FilterChip(
                    selected = current == mode,
                    onClick = { onSelected(mode.name) },
                    label = { Text(mode.displayName) },
                )
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun ApiKeyField(hasKey: Boolean, onSet: (String) -> Unit, onClear: () -> Unit) {
    var input by remember { mutableStateOf("") }
    Text(
        stringResource(if (hasKey) R.string.settings_key_set else R.string.settings_key_none),
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedTextField(
        value = input,
        onValueChange = { input = it },
        label = { Text(stringResource(R.string.settings_api_key_label)) },
        placeholder = { Text("sk-or-…") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { onSet(input); input = "" },
            enabled = input.isNotBlank(),
            modifier = Modifier.weight(1f).height(48.dp),
        ) { Text(stringResource(if (hasKey) R.string.settings_replace else R.string.common_save)) }
        if (hasKey) {
            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.weight(1f).height(48.dp),
            ) { Text(stringResource(R.string.common_clear)) }
        }
    }
}

@Composable
private fun GoalsForm(goals: NutritionGoals, onChange: (NutritionGoals) -> Unit) {
    NumField(stringResource(R.string.label_calories_kcal), goals.kcal) { onChange(goals.copy(kcal = it)) }
    NumField(stringResource(R.string.label_protein_g), goals.proteinG) { onChange(goals.copy(proteinG = it)) }
    NumField(stringResource(R.string.label_carbs_g), goals.carbsG) { onChange(goals.copy(carbsG = it)) }
    NumField(stringResource(R.string.label_fat_g), goals.fatG) { onChange(goals.copy(fatG = it)) }
    NumField(stringResource(R.string.label_fiber_g), goals.fiberG) { onChange(goals.copy(fiberG = it)) }
}

@Composable
private fun NumField(label: String, value: Int, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { txt -> onChange(txt.filter { it.isDigit() }.toIntOrNull() ?: 0) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ExportFolderRow(
    currentUri: String?,
    status: AutoBackupStatus,
    onSet: (String?) -> Unit,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
                .onSuccess { onSet(uri.toString()) }
        }
    }

    Text(
        stringResource(if (currentUri != null) R.string.settings_export_folder_set else R.string.settings_export_folder_none),
        style = MaterialTheme.typography.bodyMedium,
    )
    if (currentUri != null && status.failed) {
        Text(
            stringResource(R.string.settings_auto_backup_failed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    } else if (currentUri != null && status.lastSuccessEpoch > 0L) {
        Text(
            stringResource(
                R.string.settings_auto_backup_last_success,
                java.text.DateFormat.getDateTimeInstance().format(java.util.Date(status.lastSuccessEpoch)),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { launcher.launch(null) },
            modifier = Modifier.weight(1f).height(48.dp),
        ) { Text(stringResource(R.string.settings_pick_folder)) }
        if (currentUri != null) {
            OutlinedButton(
                onClick = { onSet(null) },
                modifier = Modifier.weight(1f).height(48.dp),
            ) { Text(stringResource(R.string.common_clear)) }
        }
    }
}

@Composable
private fun BackupButtons(
    onExport: (android.net.Uri) -> Unit,
    onImport: (android.net.Uri, Boolean) -> Unit,
) {
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(onExport) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> pendingImportUri = uri }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { exportLauncher.launch("SoloForge-backup-${java.time.LocalDate.now()}.json") },
            modifier = Modifier.weight(1f).height(48.dp),
        ) { Text(stringResource(R.string.settings_export_backup)) }
        OutlinedButton(
            onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream")) },
            modifier = Modifier.weight(1f).height(48.dp),
        ) { Text(stringResource(R.string.settings_import_backup)) }
    }
    Text(
        stringResource(R.string.settings_backup_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    pendingImportUri?.let { uri ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text(stringResource(R.string.settings_import_backup)) },
            text = {
                Text(stringResource(R.string.settings_import_explain))
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    onImport(uri, true)
                    pendingImportUri = null
                }) { Text(stringResource(R.string.settings_merge)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    onImport(uri, false)
                    pendingImportUri = null
                }) { Text(stringResource(R.string.settings_replace_everything), color = MaterialTheme.colorScheme.error) }
            },
        )
    }
}

@Composable
private fun WeighInTimeRow(
    dayOfWeek: Int,
    hour: Int,
    minute: Int,
    onChange: (Int, Int, Int) -> Unit,
) {
    val locale = LocalLocale.current.platformLocale
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_day), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            java.time.DayOfWeek.entries.forEach { dow ->
                val day = dow.value
                val label = dow.getDisplayName(java.time.format.TextStyle.SHORT, locale)
                androidx.compose.material3.FilterChip(
                    selected = dayOfWeek == day,
                    onClick = { onChange(day, hour, minute) },
                    label = { Text(label) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = hour.toString(),
                onValueChange = { v -> v.filter { it.isDigit() }.toIntOrNull()?.coerceIn(0, 23)?.let { onChange(dayOfWeek, it, minute) } },
                label = { Text(stringResource(R.string.settings_hour)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = minute.toString().padStart(2, '0'),
                onValueChange = { v -> v.filter { it.isDigit() }.toIntOrNull()?.coerceIn(0, 59)?.let { onChange(dayOfWeek, hour, it) } },
                label = { Text(stringResource(R.string.settings_minute)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HealthConnectSection(
    importEnabled: Boolean,
    exportEnabled: Boolean,
    lastSyncEpoch: Long,
    viewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(viewModel.healthPermissionContract()) { granted ->
        viewModel.onHealthImportResult(granted)
    }
    val exportLauncher = rememberLauncherForActivityResult(viewModel.healthPermissionContract()) { granted ->
        viewModel.onHealthExportResult(granted)
    }

    SectionCard(stringResource(R.string.settings_section_health)) {
        Text(
            stringResource(R.string.settings_health_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwitchRow(stringResource(R.string.settings_health_import), importEnabled) { want ->
            if (want) importLauncher.launch(viewModel.healthImportPermissions)
            else viewModel.setHealthImportEnabled(false)
        }
        SwitchRow(stringResource(R.string.settings_health_export), exportEnabled) { want ->
            if (want) exportLauncher.launch(viewModel.healthExportPermissions)
            else viewModel.setHealthExportEnabled(false)
        }
        if (importEnabled || exportEnabled) {
            OutlinedButton(
                onClick = { viewModel.syncHealthNow() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(stringResource(R.string.settings_health_sync_now)) }
            if (lastSyncEpoch > 0) {
                Text(
                    stringResource(
                        R.string.settings_health_last_sync,
                        java.text.DateFormat.getDateTimeInstance().format(java.util.Date(lastSyncEpoch)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedButton(
            onClick = {
                runCatching {
                    context.startActivity(Intent("androidx.health.connect.action.HEALTH_CONNECT_SETTINGS"))
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text(stringResource(R.string.settings_health_manage)) }
    }
}

@Composable
private fun AnalysisModelPicker(
    selectedToken: String,
    customModelId: String,
    onSelect: (String) -> Unit,
    onCustomIdChange: (String) -> Unit,
) {
    Text(
        stringResource(R.string.settings_ai_model_title),
        style = MaterialTheme.typography.titleSmall,
    )
    FoodAnalysisModels.OFFERED.forEach { config ->
        ModelRow(
            label = stringResource(config.labelRes()),
            selected = selectedToken == config.token,
            onSelect = { onSelect(config.token) },
        ) {
            Text(
                stringResource(
                    R.string.settings_ai_model_speed,
                    formatSeconds(config.medianPhotoSeconds),
                    config.answersDirectly,
                    config.casesScored,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(
                    R.string.settings_ai_model_accuracy,
                    formatOneDecimal(config.kcalErrorPercent),
                    formatDollars(config.centsPerThousandAnalyses),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(
                    if (config.openWeight) R.string.settings_ai_model_open_note
                    else R.string.settings_ai_model_proprietary_note
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // The escape hatch. It deliberately shows a warning where the measured rows show numbers —
    // the app must never print an accuracy figure it did not measure.
    ModelRow(
        label = stringResource(R.string.settings_ai_model_custom),
        selected = selectedToken == FoodAnalysisModels.TOKEN_CUSTOM,
        onSelect = { onSelect(FoodAnalysisModels.TOKEN_CUSTOM) },
    ) {
        Text(
            stringResource(R.string.settings_ai_model_custom_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (selectedToken == FoodAnalysisModels.TOKEN_CUSTOM) {
            OutlinedTextField(
                value = customModelId,
                onValueChange = onCustomIdChange,
                label = { Text(stringResource(R.string.settings_ai_model_custom_field)) },
                placeholder = { Text("vendor/model-name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    Text(
        stringResource(R.string.settings_ai_model_measured_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ModelRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    details: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onSelect),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(
            Modifier.weight(1f).padding(top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            details()
        }
    }
}

private fun AnalysisConfig.labelRes(): Int = when (token) {
    FoodAnalysisModels.TOKEN_FAST -> R.string.settings_ai_model_fast
    FoodAnalysisModels.TOKEN_BALANCED -> R.string.settings_ai_model_balanced
    FoodAnalysisModels.TOKEN_OPEN -> R.string.settings_ai_model_open
    FoodAnalysisModels.TOKEN_ACCURATE -> R.string.settings_ai_model_accurate
    else -> R.string.settings_ai_model_custom
}

private fun formatSeconds(value: Double): String =
    if (value >= 10.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

private fun formatOneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)

private fun formatDollars(centsPerThousand: Int): String =
    String.format(Locale.US, "%.2f", centsPerThousand / 100.0)

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}
