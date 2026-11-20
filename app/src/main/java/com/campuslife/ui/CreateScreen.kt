@file:OptIn(ExperimentalMaterial3Api::class)

package com.campuslife.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.campuslife.data.*
import java.time.*

@Composable
fun CreateScreen(vm: AppViewModel, onBack: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(CATEGORIES.first()) }
    var location by remember { mutableStateOf("") }
    var capacity by remember { mutableStateOf("30") }
    var date by remember { mutableStateOf(LocalDate.now().plusDays(1)) }
    var time by remember { mutableStateOf(LocalTime.of(15, 0)) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var tried by remember { mutableStateOf(false) }

    val cap = capacity.toIntOrNull()
    val valid = title.isNotBlank() && desc.isNotBlank() && location.isNotBlank() && cap != null && cap > 0

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Новое событие") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Название") }, isError = tried && title.isBlank(), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(desc, { desc = it }, label = { Text("Описание") }, isError = tried && desc.isBlank(), minLines = 3, modifier = Modifier.fillMaxWidth())

            ExposedDropdownMenuBox(expanded = menu, onExpandedChange = { menu = it }) {
                OutlinedTextField(
                    value = category, onValueChange = {}, readOnly = true, label = { Text("Категория") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menu) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    CATEGORIES.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { category = c; menu = false }) }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1f)) {
                    Text(formatDate(date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()))
                }
                OutlinedButton(onClick = { showTime = true }, modifier = Modifier.weight(1f)) {
                    Text("%02d:%02d".format(time.hour, time.minute))
                }
            }

            OutlinedTextField(location, { location = it }, label = { Text("Место проведения") }, isError = tried && location.isBlank(), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                capacity, { capacity = it.filter(Char::isDigit).take(4) }, label = { Text("Количество мест") },
                isError = tried && (cap == null || cap <= 0), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    tried = true
                    if (valid) {
                        val start = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        vm.createEvent(title, desc, category, start, location, cap!!)
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Опубликовать") }
        }
    }

    if (showDate) {
        val st = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate() }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Отмена") } },
        ) { DatePicker(st) }
    }
    if (showTime) {
        val st = rememberTimePickerState(time.hour, time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = { TextButton(onClick = { time = LocalTime.of(st.hour, st.minute); showTime = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Отмена") } },
            text = { TimePicker(st) },
        )
    }
}
