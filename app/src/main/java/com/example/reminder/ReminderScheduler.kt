package com.example.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.model.Customer
import java.util.Calendar

object ReminderScheduler {
    fun scheduleCustomerReminder(context: Context, customer: Customer) {
        val promiseDate = customer.promiseDate ?: return
        if (promiseDate < System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, PromiseReminderReceiver::class.java).apply {
            putExtra(PromiseReminderReceiver.EXTRA_CUSTOMER_ID, customer.id)
            putExtra(PromiseReminderReceiver.EXTRA_CUSTOMER_NAME, customer.name)
            putExtra(PromiseReminderReceiver.EXTRA_CUSTOMER_PHONE, customer.phone)
            putExtra(PromiseReminderReceiver.EXTRA_UDHARI_AMOUNT, customer.totalUdhari.toPlainString())
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            customer.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Set to 9:00 AM on the target morning
        val calendar = Calendar.getInstance().apply {
            timeInMillis = promiseDate
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }

        val triggerTime = if (calendar.timeInMillis <= System.currentTimeMillis()) {
            System.currentTimeMillis() + 60_000 // fire shortly if already past 9am on selected date
        } else {
            calendar.timeInMillis
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    // Fallback to inexact alarm as specified in doc
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // Fallback for safety
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    fun schedulePromiseReminder(
        context: Context,
        customerId: String,
        customerName: String,
        udhariAmount: String,
        promiseDateMillis: Long
    ) {
        val tempCustomer = Customer(
            id = customerId,
            name = customerName,
            promiseDate = promiseDateMillis
        )
        scheduleCustomerReminder(context, tempCustomer)
    }

    fun cancelReminder(context: Context, customerId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, PromiseReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            customerId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }
}
