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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import com.kbul.spicycrab.data.prefs.NutritionGoals
import com.kbul.spicycrab.domain.fasting.FastingMode

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val hasKey by viewModel.hasKey.collectAsStateWithLifecycle()
    val exportMessage by viewModel.exportMessage.collectAsStateWithLifecycle()
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
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        SectionCard("Fasting") {
            DefaultModeDropdown(s.defaultFastingModeName, viewModel::setDefaultFastingMode)
            SwitchRow(
                "“Almost there” encouragement",
                s.almostThereEnabled,
                viewModel::setAlmostThereEnabled,
            )
            SwitchRow(
                "Eating-window-closing reminder",
                s.eatingWindowClosingEnabled,
                viewModel::setEatingWindowClosingEnabled,
            )
        }

        SectionCard("AI / API") {
            ApiKeyField(hasKey, viewModel::setApiKey, viewModel::clearApiKey)
            Text(
                "Food photos use an automatic model chain: fast default analysis first, stronger review when confidence is low, and a premium check only when uncertainty remains.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("Daily nutrition goals") {
            GoalsForm(s.goals, viewModel::setGoals)
        }

        SectionCard("Data") {
            ExportFolderRow(s.exportFolderUri, viewModel::setExportFolder)
            SwitchRow("Save photo locally with each entry", s.savePhotoLocally, viewModel::setSavePhotoLocally)
            Button(
                onClick = viewModel::exportAll,
                enabled = s.exportFolderUri != null,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Export all data") }
            Text(
                "Rewrites food_log.csv and weight_log.csv from the database (single row per entry).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("Weight") {
            SwitchRow("Use kilograms", s.weightUnitKg, viewModel::setWeightUnitKg)
            SwitchRow("Weekly weigh-in reminder", s.weighInEnabled, viewModel::setWeighInEnabled)
            if (s.weighInEnabled) {
                WeighInTimeRow(
                    dayOfWeek = s.weighInDayOfWeek,
                    hour = s.weighInHour,
                    minute = s.weighInMinute,
                    onChange = viewModel::setWeighInTime,
                )
            }
        }

        SectionCard("Tabs") {
            Text(
                "Hide tabs you don't use. Home and Settings are always shown.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SwitchRow("Show Fasting tab", s.showFastingTab, viewModel::setShowFastingTab)
            SwitchRow("Show Food tab", s.showFoodTab, viewModel::setShowFoodTab)
            SwitchRow("Show Weight tab", s.showWeightTab, viewModel::setShowWeightTab)
            SwitchRow("Show Workout tab", s.showWorkoutTab, viewModel::setShowWorkoutTab)
        }

        SectionCard("About") {
            Text(
                "All data stays on this device. Food images are sent only to OpenRouter when you start meal analysis.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
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
        Text("Default fasting mode", style = MaterialTheme.typography.bodyMedium)
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
        if (hasKey) "An OpenRouter key is set." else "No API key set.",
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedTextField(
        value = input,
        onValueChange = { input = it },
        label = { Text("OpenRouter API key") },
        placeholder = { Text("sk-or-…") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { onSet(input); input = "" },
            enabled = input.isNotBlank(),
            modifier = Modifier.weight(1f).height(48.dp),
        ) { Text(if (hasKey) "Replace" else "Save") }
        if (hasKey) {
            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.weight(1f).height(48.dp),
            ) { Text("Clear") }
        }
    }
}

@Composable
private fun GoalsForm(goals: NutritionGoals, onChange: (NutritionGoals) -> Unit) {
    NumField("Calories (kcal)", goals.kcal) { onChange(goals.copy(kcal = it)) }
    NumField("Protein (g)", goals.proteinG) { onChange(goals.copy(proteinG = it)) }
    NumField("Carbs (g)", goals.carbsG) { onChange(goals.copy(carbsG = it)) }
    NumField("Fat (g)", goals.fatG) { onChange(goals.copy(fatG = it)) }
    NumField("Fiber (g)", goals.fiberG) { onChange(goals.copy(fiberG = it)) }
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
private fun ExportFolderRow(currentUri: String?, onSet: (String?) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            onSet(uri.toString())
        }
    }

    Text(
        currentUri?.let { "Export folder set." } ?: "No export folder selected.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { launcher.launch(null) },
            modifier = Modifier.weight(1f).height(48.dp),
        ) { Text("Pick folder") }
        if (currentUri != null) {
            OutlinedButton(
                onClick = { onSet(null) },
                modifier = Modifier.weight(1f).height(48.dp),
            ) { Text("Clear") }
        }
    }
}

@Composable
private fun WeighInTimeRow(
    dayOfWeek: Int,
    hour: Int,
    minute: Int,
    onChange: (Int, Int, Int) -> Unit,
) {
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Day", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            days.forEachIndexed { idx, label ->
                val day = idx + 1
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
                label = { Text("Hour") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = minute.toString().padStart(2, '0'),
                onValueChange = { v -> v.filter { it.isDigit() }.toIntOrNull()?.coerceIn(0, 59)?.let { onChange(dayOfWeek, hour, it) } },
                label = { Text("Minute") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

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
