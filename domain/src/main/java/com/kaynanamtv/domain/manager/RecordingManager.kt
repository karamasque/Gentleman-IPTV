package com.kaynanamtv.domain.manager

import com.kaynanamtv.domain.model.RecordingItem
import com.kaynanamtv.domain.model.RecordingRequest
import com.kaynanamtv.domain.model.RecordingStorageConfig
import com.kaynanamtv.domain.model.RecordingStorageState
import com.kaynanamtv.domain.model.Result
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface RecordingManager {
    fun observeRecordingItems(): Flow<List<RecordingItem>>
    fun observeStorageState(): Flow<RecordingStorageState>
    fun observeActiveRecordingCount(): Flow<Int> = observeRecordingItems().map { items ->
        items.count { it.status == com.kaynanamtv.domain.model.RecordingStatus.RECORDING }
    }

    suspend fun startManualRecording(request: RecordingRequest): Result<RecordingItem>
    suspend fun scheduleRecording(request: RecordingRequest): Result<RecordingItem>
    suspend fun stopRecording(recordingId: String): Result<Unit>
    suspend fun cancelRecording(recordingId: String): Result<Unit>
    suspend fun deleteRecording(recordingId: String): Result<Unit>
    suspend fun retryRecording(recordingId: String): Result<Unit> = Result.error("Retry is not supported by this recording manager.")
    suspend fun setScheduleEnabled(recordingId: String, enabled: Boolean): Result<Unit> =
        Result.error("Schedule enablement is not supported by this recording manager.")
    suspend fun updateStorageConfig(config: RecordingStorageConfig): Result<RecordingStorageState> =
        Result.error("Storage configuration is not supported by this recording manager.")
    suspend fun reconcileRecordingState(): Result<Unit> = Result.success(Unit)
    suspend fun promoteScheduledRecording(recordingId: String): Result<Unit> =
        Result.error("Scheduled promotion is not supported by this recording manager.")
    suspend fun skipOccurrence(recordingId: String): Result<Unit> =
        Result.error("Skip occurrence is not supported by this recording manager.")
    suspend fun forceScheduleRecording(request: RecordingRequest): Result<RecordingItem> =
        Result.error("Force scheduling is not supported by this recording manager.")
    suspend fun getConflictingRecordings(startMs: Long, endMs: Long, providerId: Long): List<RecordingItem> =
        emptyList()

    suspend fun prepareTeeRecordingOutput(request: RecordingRequest): Result<Pair<String, OutputStream>> =
        Result.error("Tee recording is not supported by this recording manager.")

    suspend fun finalizeTeeRecording(recordingId: String, channelName: String, programTitle: String?) {}
}

