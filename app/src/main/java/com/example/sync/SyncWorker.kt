package com.example.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

import com.example.PashuAaharApp

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val firestoreEngine = PashuAaharApp.instance.firestoreSyncEngine
        return when (firestoreEngine.syncNow()) {
            is FirestoreSyncState.Success -> Result.success()
            is FirestoreSyncState.Unauthenticated -> Result.success()
            is FirestoreSyncState.Error -> Result.retry()
            else -> Result.success()
        }
    }
}
