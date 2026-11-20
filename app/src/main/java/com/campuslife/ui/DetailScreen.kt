@file:OptIn(ExperimentalMaterial3Api::class)

package com.campuslife.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.campuslife.data.*

@Composable
fun DetailScreen(vm: AppViewModel, eventId: Long, onBack: () -> Unit) {
    val u by vm.find(eventId).collectAsStateWithLifecycle(initialValue = null)
    val regs by vm.registrations.collectAsStateWithLifecycle()
    val organizer by vm.isOrganizer.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var showTicket by remember { mutableStateOf(false) }
    var showEventQr by remember { mutableStateOf(false) }
    var showReminder by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun ensureNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val cur = u
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Мероприятие") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                actions = {
                    if (cur != null) IconButton(onClick = { vm.toggleFav(eventId) }) {
                        Icon(
                            if (cur.isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "Избранное",
                            tint = if (cur.isFav) Color(0xFFE11D48) else LocalContentColor.current,
                        )
                    }
                },
            )
        },
    ) { pad ->
        if (cur == null) { EmptyState("Мероприятие не найдено", Modifier.padding(pad)); return@Scaffold }
        val e = cur.event
        val attendees = regs.filter { it.eventId == eventId }.sortedBy { it.createdAt }
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {
            Box {
                AsyncImage(
                    model = e.imageUrl, contentDescription = e.title, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(220.dp).background(categoryColor(e.category).copy(alpha = 0.25f)),
                )
                CategoryBadge(e.category, Modifier.align(Alignment.BottomStart).padding(12.dp))
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(e.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                InfoRow(Icons.Filled.Event, "${formatDate(e.startMillis)}, ${formatTime(e.startMillis)}")
                InfoRow(Icons.Filled.Place, e.location)
                InfoRow(Icons.Filled.Person, "Организатор: ${e.organizer}")
                InfoRow(Icons.Filled.Group, "Записались: ${cur.registered} из ${e.capacity}")
                LinearProgressIndicator(progress = { (cur.registered / e.capacity.toFloat()).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text(e.description, style = MaterialTheme.typography.bodyLarge)

                if (cur.isRegistered) {
                    Button(onClick = { showTicket = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.QrCode2, null); Spacer(Modifier.width(8.dp)); Text("Мой билет (QR)")
                    }
                    OutlinedButton(onClick = { vm.unregister(eventId) }, modifier = Modifier.fillMaxWidth()) { Text("Отменить запись") }
                } else {
                    Button(onClick = { vm.register(eventId) }, enabled = !cur.full, modifier = Modifier.fillMaxWidth()) {
                        Text(if (cur.full) "Мест нет" else "Записаться")
                    }
                }

                OutlinedButton(onClick = { showReminder = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(if (cur.reminderMin != null) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsNone, null)
                    Spacer(Modifier.width(8.dp))
                    Text(cur.reminderMin?.let { "Напоминание: ${reminderLabel(it)}" } ?: "Напомнить о событии")
                }

                if (organizer) {
                    HorizontalDivider()
                    Text("Для организатора", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showEventQr = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.QrCode2, null); Spacer(Modifier.width(6.dp)); Text("QR события")
                        }
                        OutlinedButton(onClick = {
                            startQrScan(ctx, { vm.handleQr(it) }, { vm.message.value = it })
                        }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.QrCodeScanner, null); Spacer(Modifier.width(6.dp)); Text("Сканер")
                        }
                    }
                    val checked = attendees.count { it.checkedIn }
                    Text("Записавшиеся: ${attendees.size} · пришли: $checked", style = MaterialTheme.typography.bodyMedium)
                    attendees.forEach { r ->
                        Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surface).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(r.attendeeName, Modifier.weight(1f))
                            if (r.checkedIn) Icon(Icons.Filled.CheckCircle, "Пришёл", tint = Color(0xFF16A34A))
                        }
                    }
                    if (e.createdByMe) TextButton(onClick = { confirmDelete = true }) { Text("Удалить событие", color = MaterialTheme.colorScheme.error) }
                }
            }
        }

        if (showTicket) {
            QrDialog("Ваш билет", "Покажите этот код организатору на входе", ticketQr(eventId, ME, vm.userName.value)) { showTicket = false }
        }
        if (showEventQr) {
            QrDialog("QR события", "Участники сканируют код в приложении и записываются на месте", eventQr(eventId)) { showEventQr = false }
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Удалить событие?") },
                text = { Text("Все записи участников тоже будут удалены.") },
                confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteEvent(eventId); onBack() }) { Text("Удалить") } },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
            )
        }
        if (showReminder) {
            AlertDialog(
                onDismissRequest = { showReminder = false },
                title = { Text("Напомнить") },
                text = {
                    Column {
                        listOf(15, 60, 1440).forEach { m ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = cur.reminderMin == m, onClick = {
                                    ensureNotifPermission(); vm.setReminder(eventId, m); showReminder = false
                                })
                                Text(reminderLabel(m))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = cur.reminderMin == null, onClick = { vm.setReminder(eventId, null); showReminder = false })
                            Text("Не напоминать")
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showReminder = false }) { Text("Закрыть") } },
            )
        }
    }
}
