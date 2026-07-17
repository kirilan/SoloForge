package com.kbul.spicycrab.ui.food

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.kbul.spicycrab.R

@Composable
fun ScaleByGramsButton(
    currentGrams: Double,
    onApply: (newGrams: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    OutlinedButton(
        onClick = {
            input = if (currentGrams > 0) "%.0f".format(currentGrams) else ""
            dialogOpen = true
        },
        enabled = currentGrams > 0,
        modifier = modifier,
    ) { Text(stringResource(R.string.scale_adjust)) }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(stringResource(R.string.scale_title)) },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text(stringResource(R.string.scale_new_grams)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = input.replace(',', '.').toDoubleOrNull()?.let { it > 0 } == true,
                    onClick = {
                        val newGrams = input.replace(',', '.').toDouble()
                        onApply(newGrams)
                        dialogOpen = false
                    },
                ) { Text(stringResource(R.string.scale_apply)) }
            },
            dismissButton = {
                TextButton(onClick = { dialogOpen = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}
