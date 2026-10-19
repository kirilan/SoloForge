package com.kbul.spicycrab.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kbul.spicycrab.R
import com.kbul.spicycrab.data.prefs.BodyProfile
import com.kbul.spicycrab.domain.profile.ActivityLevel
import com.kbul.spicycrab.domain.profile.GoalCalculator
import com.kbul.spicycrab.domain.profile.GoalSuggestion
import com.kbul.spicycrab.domain.profile.GoalWarning
import com.kbul.spicycrab.domain.profile.Sex
import com.kbul.spicycrab.domain.weight.LB_PER_KG
import java.time.LocalDate
import java.time.Year
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

private const val CM_PER_INCH = 2.54

/** First-run page after the trust intro. Skipping keeps the default goals and saves nothing. */
@Composable
fun ProfileSetupScreen(onDone: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state ?: return
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.profile_optional_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = s.useKg,
                    onClick = { viewModel.setUseKg(true) },
                    label = { Text(stringResource(R.string.profile_units_metric)) },
                )
                FilterChip(
                    selected = !s.useKg,
                    onClick = { viewModel.setUseKg(false) },
                    label = { Text(stringResource(R.string.profile_units_imperial)) },
                )
            }
            ProfileForm(
                initial = s.profile,
                useKg = s.useKg,
                latestWeightKg = s.latestWeightKg,
                askCurrentWeight = true,
            ) { draft, enteredWeightKg ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDone, modifier = Modifier.weight(1f).height(52.dp)) {
                        Text(stringResource(R.string.profile_skip))
                    }
                    Button(
                        onClick = { viewModel.save(draft, enteredWeightKg, applyGoals = true, onSaved = onDone) },
                        modifier = Modifier.weight(2f).height(52.dp),
                    ) { Text(stringResource(R.string.profile_save_continue)) }
                }
            }
        }
    }
}

/** Settings entry point: same form, no weight field — the latest weigh-in is used instead. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(onDismiss: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state ?: return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.settings_section_profile), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.profile_optional_short),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ProfileForm(
                initial = s.profile,
                useKg = s.useKg,
                latestWeightKg = s.latestWeightKg,
                askCurrentWeight = false,
            ) { draft, _ ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s.latestWeightKg != null) {
                        Button(
                            onClick = { viewModel.save(draft, null, applyGoals = true, onSaved = onDismiss) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) { Text(stringResource(R.string.profile_save_apply)) }
                    }
                    OutlinedButton(
                        onClick = { viewModel.save(draft, null, applyGoals = false, onSaved = onDismiss) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(stringResource(R.string.profile_save)) }
                }
            }
        }
    }
}

@Composable
private fun ProfileForm(
    initial: BodyProfile,
    useKg: Boolean,
    latestWeightKg: Double?,
    askCurrentWeight: Boolean,
    actions: @Composable (draft: BodyProfile, enteredWeightKg: Double?) -> Unit,
) {
    val year = Year.now().value
    val weightUnit = if (useKg) "kg" else "lb"
    var heightCm by remember { mutableStateOf(initial.heightCm) }
    var weightKg by remember { mutableStateOf<Double?>(null) }
    var sex by remember { mutableStateOf(Sex.fromName(initial.sex)) }
    var birthYear by remember { mutableStateOf(initial.birthYear) }
    var activity by remember { mutableStateOf(ActivityLevel.fromName(initial.activity)) }
    var goalKg by remember { mutableStateOf(initial.goalWeightKg) }
    var pace by remember { mutableDoubleStateOf(initial.paceKgPerWeek) }

    if (useKg) {
        NumberField(
            label = stringResource(R.string.profile_height_cm),
            initial = heightCm?.toString().orEmpty(),
            resetKey = useKg,
            parse = { it.toIntOrNull()?.takeIf { cm -> cm in 100..250 } },
            onValue = { heightCm = it },
        )
    } else {
        val totalInches = heightCm?.let { (it / CM_PER_INCH).roundToInt() }
        var feet by remember(useKg) { mutableStateOf(totalInches?.div(12)) }
        var inches by remember(useKg) { mutableStateOf(totalInches?.rem(12)) }
        fun update() {
            val f = feet
            heightCm = if (f == null) null else ((f * 12 + (inches ?: 0)) * CM_PER_INCH).roundToInt()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(
                label = stringResource(R.string.profile_height_ft),
                initial = feet?.toString().orEmpty(),
                resetKey = useKg,
                parse = { it.toIntOrNull()?.takeIf { ft -> ft in 3..8 } },
                onValue = { feet = it; update() },
                modifier = Modifier.weight(1f),
            )
            NumberField(
                label = stringResource(R.string.profile_height_in),
                initial = inches?.toString().orEmpty(),
                resetKey = useKg,
                parse = { it.toIntOrNull()?.takeIf { inch -> inch in 0..11 } },
                onValue = { inches = it; update() },
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (askCurrentWeight) {
        WeightField(stringResource(R.string.profile_current_weight, weightUnit), weightKg, useKg) { weightKg = it }
    }

    Text(stringResource(R.string.profile_sex), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Sex.entries.forEach { option ->
            FilterChip(
                selected = sex == option,
                onClick = { sex = option },
                label = { Text(stringResource(option.labelRes)) },
            )
        }
    }
    Text(
        stringResource(R.string.profile_sex_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    NumberField(
        label = stringResource(R.string.profile_birth_year),
        initial = birthYear?.toString().orEmpty(),
        resetKey = Unit,
        parse = { it.toIntOrNull()?.takeIf { y -> y in (year - 110)..(year - 5) } },
        onValue = { birthYear = it },
    )

    Text(stringResource(R.string.profile_activity), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActivityLevel.entries.forEach { option ->
            FilterChip(
                selected = activity == option,
                onClick = { activity = option },
                label = { Text(stringResource(option.labelRes)) },
            )
        }
    }
    Text(
        stringResource(activity.descriptionRes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    WeightField(stringResource(R.string.profile_goal_weight, weightUnit), goalKg, useKg) { goalKg = it }
    if (goalKg != null) {
        Text(stringResource(R.string.profile_pace), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoalCalculator.PACES.forEachIndexed { i, option ->
                FilterChip(
                    selected = pace == option,
                    onClick = { pace = option },
                    label = { Text(stringResource(PACE_LABELS[i])) },
                )
            }
        }
        Text(
            stringResource(R.string.profile_pace_per_week, formatWeight(pace, useKg, decimals = 2), weightUnit),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    val draft = BodyProfile(
        heightCm = heightCm,
        sex = sex.name,
        birthYear = birthYear,
        activity = activity.name,
        goalWeightKg = goalKg,
        paceKgPerWeek = pace,
    )
    val basisKg = weightKg ?: latestWeightKg
    SuggestionCard(
        suggestion = basisKg?.let { GoalCalculator.suggest(it, draft, year) },
        heightCm = heightCm,
        useKg = useKg,
        noWeightRes = if (askCurrentWeight) R.string.profile_need_weight else R.string.profile_need_logged_weight,
    )
    actions(draft, weightKg)
}

@Composable
private fun SuggestionCard(suggestion: GoalSuggestion?, heightCm: Int?, useKg: Boolean, noWeightRes: Int) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (suggestion == null) {
                Text(stringResource(noWeightRes), style = MaterialTheme.typography.bodyMedium)
            } else {
                SuggestionDetails(suggestion, heightCm, useKg)
            }
        }
    }
}

@Composable
private fun SuggestionDetails(suggestion: GoalSuggestion, heightCm: Int?, useKg: Boolean) {
    val g = suggestion.goals
    Text(stringResource(R.string.profile_suggestion_title), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(R.string.profile_suggestion_kcal, g.kcal),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Text(stringResource(R.string.profile_suggestion_macros, g.proteinG, g.carbsG, g.fatG, g.fiberG))
    Text(
        stringResource(R.string.profile_maintenance, suggestion.maintenanceKcal),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    suggestion.weeksToGoal?.let { weeks ->
        Text(stringResource(R.string.profile_eta, formatEta(weeks)), style = MaterialTheme.typography.bodyMedium)
    }
    suggestion.warnings.forEach { warning ->
        val text = when (warning) {
            GoalWarning.MINOR -> stringResource(R.string.profile_warning_minor)
            GoalWarning.FLOOR_APPLIED -> stringResource(R.string.profile_warning_floor)
            GoalWarning.GOAL_BELOW_HEALTHY -> {
                val range = GoalCalculator.healthyWeightRangeKg(heightCm ?: return@forEach)
                stringResource(
                    R.string.profile_warning_goal_low,
                    formatWeight(range.start, useKg),
                    formatWeight(range.endInclusive, useKg),
                    if (useKg) "kg" else "lb",
                )
            }
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
    Text(
        stringResource(R.string.profile_disclaimer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun WeightField(label: String, valueKg: Double?, useKg: Boolean, onValue: (Double?) -> Unit) {
    NumberField(
        label = label,
        initial = valueKg?.let { formatWeight(it, useKg) }.orEmpty(),
        resetKey = useKg,
        parse = { text ->
            text.replace(',', '.').toDoubleOrNull()
                ?.let { if (useKg) it else it / LB_PER_KG }
                ?.takeIf { it in 20.0..400.0 }
        },
        onValue = onValue,
    )
}

/** A field whose text survives typing; it re-seeds from [initial] only when [resetKey] changes (unit switch). */
@Composable
private fun <T> NumberField(
    label: String,
    initial: String,
    resetKey: Any,
    parse: (String) -> T?,
    onValue: (T?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(resetKey) { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onValue(parse(it.trim()))
        },
        label = { Text(label) },
        isError = text.isNotBlank() && parse(text.trim()) == null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
    )
}

internal fun formatWeight(kg: Double, useKg: Boolean, decimals: Int = 1): String =
    "%.${decimals}f".format(if (useKg) kg else kg * LB_PER_KG)

@Composable
internal fun formatEta(weeks: Int): String =
    LocalDate.now().plusWeeks(weeks.toLong())
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(LocalLocale.current.platformLocale))

private val PACE_LABELS = listOf(R.string.profile_pace_slow, R.string.profile_pace_medium, R.string.profile_pace_fast)

private val Sex.labelRes: Int
    get() = when (this) {
        Sex.MALE -> R.string.profile_sex_male
        Sex.FEMALE -> R.string.profile_sex_female
        Sex.UNSPECIFIED -> R.string.profile_sex_unspecified
    }

private val ActivityLevel.labelRes: Int
    get() = when (this) {
        ActivityLevel.SEDENTARY -> R.string.profile_activity_sedentary
        ActivityLevel.LIGHT -> R.string.profile_activity_light
        ActivityLevel.MODERATE -> R.string.profile_activity_moderate
        ActivityLevel.ACTIVE -> R.string.profile_activity_active
        ActivityLevel.VERY_ACTIVE -> R.string.profile_activity_very_active
    }

private val ActivityLevel.descriptionRes: Int
    get() = when (this) {
        ActivityLevel.SEDENTARY -> R.string.profile_activity_sedentary_desc
        ActivityLevel.LIGHT -> R.string.profile_activity_light_desc
        ActivityLevel.MODERATE -> R.string.profile_activity_moderate_desc
        ActivityLevel.ACTIVE -> R.string.profile_activity_active_desc
        ActivityLevel.VERY_ACTIVE -> R.string.profile_activity_very_active_desc
    }
