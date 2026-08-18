package com.kaynanamtv.domain.manager

import com.kaynanamtv.domain.model.Program
import com.kaynanamtv.domain.model.ProgramReminder
import com.kaynanamtv.domain.model.Result
import kotlinx.coroutines.flow.Flow

interface ProgramReminderManager {
    fun observeUpcomingReminders(): Flow<List<ProgramReminder>>

    suspend fun isReminderScheduled(
        providerId: Long,
        channelId: String,
        programTitle: String,
        programStartTime: Long
    ): Boolean

    suspend fun scheduleReminder(
        providerId: Long,
        channelId: String,
        channelName: String,
        program: Program,
        leadTimeMinutes: Int = 5
    ): Result<Unit>

    suspend fun cancelReminder(
        providerId: Long,
        channelId: String,
        programTitle: String,
        programStartTime: Long
    ): Result<Unit>

    suspend fun restoreScheduledReminders()
}
