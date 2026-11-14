package com.example.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Calendar

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val customers = AppDatabase.getInstance(context)
                    .customerDao()
                    .getCustomersWithPromiseDateInRange(startOfToday, Long.MAX_VALUE)
                customers.forEach { customer ->
                    if (!customer.isDeleted &&
                        customer.promiseDate != null &&
                        customer.totalUdhari > BigDecimal.ZERO
                    ) {
                        ReminderScheduler.scheduleCustomerReminder(context, customer)
                    }
                }
            } catch (_: Throwable) {
                // Ignore DB errors during boot receiver execution
            } finally {
                pendingResult?.finish()
            }
        }
    }
}
