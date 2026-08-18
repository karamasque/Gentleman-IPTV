package com.kaynanamtv.data.manager.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.AlarmManagerCompat
import com.kaynanamtv.domain.model.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProgramReminderAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val alarmManager: AlarmManager? by lazy {
        context.getSystemService(AlarmManager::class.java)
    }

    fun canScheduleExactAlarms(): Boolean {
        // Her zaman true dönerek izin yokluğunda dahi hatırlatıcıyı engellemiyoruz, inexact alarm ile devam ediyoruz
        return true
    }

    fun schedule(reminderId: Long, whenMs: Long): Result<Unit> {
        val manager = alarmManager ?: return Result.error("AlarmManager is not available.")
        val systemCanScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        val pendingIntent = buildPendingIntent(
            reminderId = reminderId,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return runCatching {
            if (systemCanScheduleExact) {
                AlarmManagerCompat.setExactAndAllowWhileIdle(
                    manager,
                    AlarmManager.RTC_WAKEUP,
                    whenMs,
                    pendingIntent
                )
            } else {
                AlarmManagerCompat.setAndAllowWhileIdle(
                    manager,
                    AlarmManager.RTC_WAKEUP,
                    whenMs,
                    pendingIntent
                )
            }
            Result.success(Unit)
        }.getOrElse { error ->
            android.util.Log.w("ProgramReminderScheduler", "Reminder alarm scheduling failed for $reminderId", error)
            Result.error("Reminder alarm scheduling failed: ${error.message}", error)
        }
    }

    fun cancel(reminderId: Long) {
        alarmManager?.cancel(
            buildPendingIntent(
                reminderId = reminderId,
                flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    private fun buildPendingIntent(reminderId: Long, flags: Int): PendingIntent {
        val intent = Intent(context, ProgramReminderAlarmReceiver::class.java)
            .setAction(ProgramReminderAlarmReceiver.ACTION_NOTIFY_REMINDER)
            .setData(reminderUri(reminderId))
            .putExtra(ProgramReminderAlarmReceiver.EXTRA_REMINDER_ID, reminderId)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            flags
        )
    }

    private fun reminderUri(reminderId: Long): Uri = Uri.Builder()
        .scheme("kaynanamtv")
        .authority("program-reminder")
        .appendPath(reminderId.toString())
        .build()

    companion object {
        const val EXACT_ALARM_PERMISSION_MESSAGE = "Exact alarm access is required for reliable program reminders. Enable exact alarms for KaynanamTV in system settings and try again."
    }
}
