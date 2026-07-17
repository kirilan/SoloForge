package com.kbul.spicycrab.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kbul.spicycrab.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeField(
    epochMillis: Long,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }

    val zone = ZoneId.systemDefault()
    val current = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm") }

    OutlinedButton(
        onClick = { showDate = true },
        modifier = modifier.fillMaxWidth().height(48.dp),
    ) { Text(formatter.format(current)) }

    if (showDate) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = current.toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= System.currentTimeMillis()
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dateState.selectedDateMillis?.let { utc ->
                            pickedDate = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                            showTime = true
                        }
                        showDate = false
                    },
                ) { Text(stringResource(R.string.common_next)) }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        ) { DatePicker(dateState) }
    }

    if (showTime) {
        val timeState = rememberTimePickerState(
            initialHour = current.hour,
            initialMinute = current.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            text = { TimePicker(timeState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val date = pickedDate ?: current.toLocalDate()
                        val result = date.atTime(timeState.hour, timeState.minute)
                            .atZone(zone).toInstant().toEpochMilli()
                            .coerceAtMost(System.currentTimeMillis())
                        onChange(result)
                        showTime = false
                    },
                ) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTime = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}
