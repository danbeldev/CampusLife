package com.campuslife.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.campuslife.MainActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val RU = Locale("ru")

fun formatDateTime(millis: Long): String = SimpleDateFormat("d MMMM, HH:mm", RU).format(Date(millis))
fun formatDate(millis: Long): String = SimpleDateFormat("d MMMM, EEE", RU).format(Date(millis))
fun formatTime(millis: Long): String = SimpleDateFormat("HH:mm", RU).format(Date(millis))

fun reminderLabel(min: Int) = when (min) {
    15 -> "за 15 минут"
    60 -> "за 1 час"
    1440 -> "за 1 день"
    else -> "за $min мин"
}

// ---------- QR ----------

fun qrBitmap(text: String, size: Int = 720): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) for (y in 0 until size) {
        bmp.setPixel(x, y, if (m[x, y]) Color.BLACK else Color.WHITE)
    }
    return bmp
}

/** QR мероприятия: участник сканирует и записывается на месте. */
fun eventQr(eventId: Long) = "campuslife://event?id=$eventId"

/** QR билета участника: организатор сканирует и отмечает приход. */
fun ticketQr(eventId: Long, attendeeId: String, name: String) =
    "campuslife://ticket?e=$eventId&id=${android.net.Uri.encode(attendeeId)}&n=${android.net.Uri.encode(name)}"

// ---------- Reminders ----------

const val CHANNEL_ID = "reminders"

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val title = inputData.getString("title") ?: return Result.failure()
        val whenText = inputData.getString("when") ?: ""
        val id = inputData.getLong("id", 0)
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Напоминания о мероприятиях", NotificationManager.IMPORTANCE_HIGH)
        )
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return Result.success()
        val pi = PendingIntent.getActivity(
            applicationContext, 0, Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Скоро: $title")
            .setContentText(whenText)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify(id.toInt(), n)
        return Result.success()
    }
}

fun scheduleReminder(ctx: Context, e: EventEntity, minutesBefore: Int) {
    val delay = (e.startMillis - minutesBefore * 60_000L - System.currentTimeMillis()).coerceAtLeast(0)
    val req = OneTimeWorkRequestBuilder<ReminderWorker>()
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .setInputData(workDataOf("title" to e.title, "when" to "${formatDateTime(e.startMillis)} · ${e.location}", "id" to e.id))
        .build()
    WorkManager.getInstance(ctx).enqueueUniqueWork("reminder_${e.id}", ExistingWorkPolicy.REPLACE, req)
}

fun cancelReminder(ctx: Context, eventId: Long) {
    WorkManager.getInstance(ctx).cancelUniqueWork("reminder_$eventId")
}
