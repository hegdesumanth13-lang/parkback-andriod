package com.parkback.pro

import android.Manifest
import android.app.*
import android.content.*
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

private const val CHANNEL = "parking_expiry"

fun ensureChannel(context: Context) {
    context
        .service<NotificationManager>()
        .createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                "Parking reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
}

fun canNotify(context: Context): Boolean {
    return (Build.VERSION.SDK_INT < 33 ||
        context.granted(Manifest.permission.POST_NOTIFICATIONS)) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        context.service<NotificationManager>().getNotificationChannel(CHANNEL)?.importance !=
            NotificationManager.IMPORTANCE_NONE
}

suspend fun scheduleReminder(context: Context, id: Long, time: Long) {
    check(canNotify(context)) { "Enable parking reminders in Android notification settings." }
    val request =
        OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(workDataOf("id" to id, "time" to time))
            .setInitialDelay(
                (time - System.currentTimeMillis()).coerceAtLeast(0),
                TimeUnit.MILLISECONDS,
            )
            .build()
    WorkManager.getInstance(context)
        .enqueueUniqueWork("parking-$id", ExistingWorkPolicy.REPLACE, request)
        .await()
}

fun cancelReminder(context: Context, id: Long) {
    WorkManager.getInstance(context).cancelUniqueWork("parking-$id")
    context.service<NotificationManager>().cancel(id.toString(), 1)
}

class ReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val id = inputData.getLong("id", -1)
        val dao = (context as ParkApp).db.spots()
        val s = dao.get(id) ?: return Result.success()
        if (s.active == null || s.reminder != inputData.getLong("time", -1)) return Result.success()
        if (canNotify(context)) {
            val intent =
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val notification =
                NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_park)
                    .setContentTitle("Check your parking time")
                    .setContentText(s.label().ifBlank { "Your parked vehicle reminder is due." })
                    .setContentIntent(intent)
                    .setAutoCancel(true)
                    .build()
            context.service<NotificationManager>().notify(id.toString(), 1, notification)
        }
        return Result.success()
    }
}
