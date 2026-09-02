package com.roadtwin.ai.data.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.roadtwin.ai.RoadTwinApplication
import java.util.concurrent.TimeUnit

private const val TAG = "SyncWorker"
private const val UNIQUE_ONE_TIME_SYNC = "roadtwin_one_time_sync"
private const val UNIQUE_PERIODIC_SYNC = "roadtwin_periodic_sync"

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "SyncWorker started execution in background.")
        return try {
            val app = applicationContext as? RoadTwinApplication
            if (app != null) {
                val syncManager = app.syncManager
                val result = syncManager.syncPendingReports()
                Log.d(TAG, "SyncWorker finished with result: ${result.message}")
                if (result.success) {
                    Result.success()
                } else {
                    Result.retry()
                }
            } else {
                Log.w(TAG, "Unable to access RoadTwinApplication context")
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing SyncWorker", e)
            Result.retry()
        }
    }

    companion object {
        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * Enqueues an immediate one-time sync task when network is connected.
         */
        fun enqueueOneTimeSync(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(networkConstraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONE_TIME_SYNC,
                ExistingWorkPolicy.REPLACE,
                request
            )
            Log.d(TAG, "Enqueued unique one-time sync request.")
        }

        /**
         * Schedules recurring periodic sync work (1 hour interval) with network constraint.
         */
        fun schedulePeriodicSync(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(networkConstraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_SYNC,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Scheduled recurring periodic sync work.")
        }
    }
}
