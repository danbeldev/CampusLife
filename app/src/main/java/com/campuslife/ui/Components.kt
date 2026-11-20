package com.campuslife.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.campuslife.data.*
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

@Composable
fun CampusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF4F46E5),
            secondary = Color(0xFF0EA5E9),
            tertiary = Color(0xFFF97316),
            background = Color(0xFFF7F7FB),
            surface = Color.White,
        ),
        content = content,
    )
}

fun categoryColor(c: String) = when (c) {
    "Спорт" -> Color(0xFF16A34A)
    "Творчество" -> Color(0xFFDB2777)
    "Наука" -> Color(0xFF0284C7)
    "Конкурсы" -> Color(0xFFEA580C)
    else -> Color(0xFF6B7280)
}

fun startQrScan(ctx: Context, onResult: (String) -> Unit, onError: (String) -> Unit) {
    val opts = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    GmsBarcodeScanning.getClient(ctx, opts).startScan()
        .addOnSuccessListener { b -> b.rawValue?.let(onResult) }
        .addOnFailureListener { onError("Не удалось открыть сканер: ${it.message ?: "ошибка"}") }
}

@Composable
fun QrDialog(title: String, subtitle: String, payload: String, onDismiss: () -> Unit) {
    val bmp = remember(payload) { qrBitmap(payload).asImageBitmap() }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Image(bmp, contentDescription = "QR-код", modifier = Modifier.size(240.dp))
                Spacer(Modifier.height(8.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}

@Composable
fun CategoryBadge(c: String, modifier: Modifier = Modifier) {
    Text(
        c, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
        modifier = modifier.clip(RoundedCornerShape(50)).background(categoryColor(c)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun EventCard(u: EventUi, onClick: () -> Unit, onFav: () -> Unit, showOrganizerCount: Boolean) {
    val e = u.event
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            AsyncImage(
                model = e.imageUrl, contentDescription = e.title, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(160.dp).background(categoryColor(e.category).copy(alpha = 0.25f)),
            )
            CategoryBadge(e.category, Modifier.align(Alignment.TopStart).padding(10.dp))
            IconButton(
                onClick = onFav,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(40.dp)
                    .clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.85f)),
            ) {
                Icon(
                    if (u.isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    "Избранное", tint = if (u.isFav) Color(0xFFE11D48) else Color.DarkGray,
                )
            }
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(e.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(e.description, style = MaterialTheme.typography.bodyMedium, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis)
            InfoRow(Icons.Filled.Event, formatDateTime(e.startMillis))
            InfoRow(Icons.Filled.Place, e.location)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    InfoRow(Icons.Filled.Group, if (showOrganizerCount) "Записалось: ${u.registered} из ${e.capacity}" else "Мест: ${e.capacity - u.registered} из ${e.capacity}")
                }
                if (u.isRegistered) AssistChip(onClick = onClick, label = { Text("Вы записаны") }, leadingIcon = { Icon(Icons.Filled.CheckCircle, null, Modifier.size(16.dp)) })
                else if (u.full) Text("Мест нет", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.Gray, style = MaterialTheme.typography.bodyLarge)
    }
}
