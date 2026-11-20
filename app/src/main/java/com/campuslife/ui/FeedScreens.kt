@file:OptIn(ExperimentalMaterial3Api::class)

package com.campuslife.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campuslife.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private fun dayStart(d: LocalDate) = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

@Composable
fun FeedScreen(vm: AppViewModel, onOpen: (Long) -> Unit, onCreate: () -> Unit) {
    val feed by vm.feed.collectAsStateWithLifecycle()
    val f by vm.filters.collectAsStateWithLifecycle()
    val organizer by vm.isOrganizer.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("CampusLife", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary) },
                actions = {
                    IconButton(onClick = {
                        startQrScan(ctx, { vm.handleQr(it) }, { vm.message.value = it })
                    }) { Icon(Icons.Filled.QrCodeScanner, "Сканировать QR") }
                },
            )
        },
        floatingActionButton = {
            if (organizer) ExtendedFloatingActionButton(onClick = onCreate, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Событие") })
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            OutlinedTextField(
                value = f.query, onValueChange = vm::setQuery,
                placeholder = { Text("Поиск по названию") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (f.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Close, "Очистить") } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = f.category == null, onClick = { vm.setCategory(null) }, label = { Text("Все") }) }
                items(CATEGORIES) { c ->
                    FilterChip(selected = f.category == c, onClick = { vm.setCategory(if (f.category == c) null else c) }, label = { Text(c) })
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(selected = f.dateFrom == null, onClick = { vm.setDateRange(null, null, "Все даты") }, label = { Text("Все даты") })
                }
                item {
                    FilterChip(selected = f.dateLabel == "Сегодня", onClick = {
                        val t = LocalDate.now(); vm.setDateRange(dayStart(t), dayStart(t.plusDays(1)), "Сегодня")
                    }, label = { Text("Сегодня") })
                }
                item {
                    FilterChip(selected = f.dateLabel == "7 дней", onClick = {
                        val t = LocalDate.now(); vm.setDateRange(dayStart(t), dayStart(t.plusDays(7)), "7 дней")
                    }, label = { Text("7 дней") })
                }
                item {
                    val custom = f.dateLabel !in listOf("Все даты", "Сегодня", "7 дней")
                    FilterChip(
                        selected = custom, onClick = { showPicker = true },
                        leadingIcon = { Icon(Icons.Filled.CalendarMonth, null, Modifier.size(18.dp)) },
                        label = { Text(if (custom) f.dateLabel else "Выбрать дату") },
                    )
                }
            }
            if (feed.isEmpty()) {
                EmptyState("Ничего не найдено. Измените запрос или фильтры.")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(feed, key = { it.event.id }) { u ->
                        EventCard(u, onClick = { onOpen(u.event.id) }, onFav = { vm.toggleFav(u.event.id) }, showOrganizerCount = organizer)
                    }
                }
            }
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneId.of("UTC")).toLocalDate()
                        vm.setDateRange(dayStart(d), dayStart(d.plusDays(1)), formatDate(dayStart(d)))
                    }
                    showPicker = false
                }) { Text("Применить") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Отмена") } },
        ) { DatePicker(state) }
    }
}

@Composable
fun ListScreen(title: String, items: List<EventUi>, empty: String, onOpen: (Long) -> Unit, vm: AppViewModel, organizerCounts: Boolean = false) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { TopAppBar(title = { Text(title, fontWeight = FontWeight.Bold) }) }) { pad ->
        if (items.isEmpty()) EmptyState(empty, Modifier.padding(pad))
        else LazyColumn(
            Modifier.padding(pad), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(items, key = { it.event.id }) { u ->
                EventCard(u, { onOpen(u.event.id) }, { vm.toggleFav(u.event.id) }, organizerCounts)
            }
        }
    }
}

@Composable
fun MineScreen(vm: AppViewModel, onOpen: (Long) -> Unit) {
    val regs by vm.myRegistrations.collectAsStateWithLifecycle()
    val mine by vm.myEvents.collectAsStateWithLifecycle()
    val organizer by vm.isOrganizer.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    Column {
        Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { TopAppBar(title = { Text("Мои записи", fontWeight = FontWeight.Bold) }) }) { pad ->
            Column(Modifier.padding(pad)) {
                if (organizer) {
                    TabRow(selectedTabIndex = tab) {
                        Tab(tab == 0, { tab = 0 }, text = { Text("Я иду") })
                        Tab(tab == 1, { tab = 1 }, text = { Text("Мои события") })
                    }
                }
                val show = if (organizer && tab == 1) mine else regs
                if (show.isEmpty()) {
                    EmptyState(if (organizer && tab == 1) "Вы ещё не создавали событий" else "Вы пока никуда не записаны. Загляните в ленту!")
                } else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(show, key = { it.event.id }) { u ->
                        EventCard(u, { onOpen(u.event.id) }, { vm.toggleFav(u.event.id) }, organizer && tab == 1)
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(vm: AppViewModel) {
    val name by vm.userName.collectAsStateWithLifecycle()
    val organizer by vm.isOrganizer.collectAsStateWithLifecycle()
    var text by remember(name) { mutableStateOf(name) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { TopAppBar(title = { Text("Профиль", fontWeight = FontWeight.Bold) }) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, label = { Text("Ваше имя") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { if (text.isNotBlank()) vm.setName(text.trim()) }, enabled = text.isNotBlank() && text.trim() != name) { Text("Сохранить имя") }
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Режим организатора", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Создание событий, список записавшихся и сканирование билетов участников",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = organizer, onCheckedChange = vm::setOrganizer)
                }
            }
        }
    }
}
