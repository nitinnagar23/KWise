package com.example

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.AppDatabase
import com.example.data.repository.CattleFeedRepository
import com.example.reminder.PromiseReminderReceiver
import com.example.sync.SyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

class KhataWiseApp : Application(), Application.ActivityLifecycleCallbacks {
    lateinit var repository: CattleFeedRepository
        private set

    val firestoreSyncEngine: com.example.sync.FirestoreSyncEngine by lazy {
        com.example.sync.FirestoreSyncEngine(this)
    }

    private var startedActivityCount = 0

    companion object {
        lateinit var instance: KhataWiseApp
            private set

        // App-level security state: locked whenever app goes to background
        private val _isPurchasesUnlocked = MutableStateFlow(false)
        val isPurchasesUnlocked = _isPurchasesUnlocked.asStateFlow()

        fun setPurchasesUnlocked(unlocked: Boolean) {
            _isPurchasesUnlocked.value = unlocked
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        try {
            com.google.firebase.FirebaseApp.initializeApp(this)
        } catch (_: Throwable) {}
        val db = AppDatabase.getInstance(this)
        repository = CattleFeedRepository(this)
        try {
            // Initialize FirestoreSyncEngine so AuthStateListener and realtime listeners attach immediately
            firestoreSyncEngine
        } catch (_: Throwable) {}

        registerActivityLifecycleCallbacks(this)
        createNotificationChannels()
        schedulePeriodicSync()
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount++
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount--
        if (startedActivityCount <= 0) {
            // App went to background: Immediately lock protected screen as required
            _isPurchasesUnlocked.value = false
            startedActivityCount = 0
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PromiseReminderReceiver.CHANNEL_ID,
                PromiseReminderReceiver.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Customer payment promise reminders"
                enableVibration(true)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun schedulePeriodicSync() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "khatawise_periodic_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        } catch (e: Exception) {
            // WorkManager fallback
        }
    }
}

typealias PashuAaharApp = KhataWiseApp

