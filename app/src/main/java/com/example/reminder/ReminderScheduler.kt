package com.example.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.model.Customer
import java.math.BigDecimal
import java.util.Calendar

object ReminderScheduler {
    fun scheduleCustomerReminder(
        context: Context,
        customer: Customer,
        udhariAmountOverride: String? = null
    ) {
        val promiseDate = customer.promiseDate ?: return
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Compare against start of day so same-day promises stored at midnight still fire
        if (promiseDate < startOfToday) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val cleanAmountStr = udhariAmountOverride
            ?.replace("₹", "")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: customer.totalUdhari.stripTrailingZeros().toPlainString()

        val intent = Intent(context, PromiseReminderReceiver::class.java).apply {
            putExtra(PromiseReminderReceiver.EXTRA_CUSTOMER_ID, customer.id)
            putExtra(PromiseReminderReceiver.EXTRA_CUSTOMER_NAME, customer.name)
            putExtra(PromiseReminderReceiver.EXTRA_CUSTOMER_PHONE, customer.phone)
            putExtra(PromiseReminderReceiver.EXTRA_UDHARI_AMOUNT, cleanAmountStr)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            customer.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Set to 9:00:00.000 AM on the target morning
        val calendar = Calendar.getInstance().apply {
            timeInMillis = promiseDate
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val now = System.currentTimeMillis()
        val triggerTime = if (calendar.timeInMillis <= now) {
            now + 60_000L // fire shortly if already past 9:00 AM on the selected date
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
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    fun schedulePromiseReminder(
        context: Context,
        customerId: String,
        customerName: String,
        udhariAmount: String,
        promiseDateMillis: Long,
        customerPhone: String = ""
    ) {
        val parsedAmount = udhariAmount
            .replace("₹", "")
            .replace(",", "")
            .trim()
            .toBigDecimalOrNull() ?: BigDecimal.ZERO

        val tempCustomer = Customer(
            id = customerId,
            name = customerName,
            phone = customerPhone,
            totalUdhari = parsedAmount,
            promiseDate = promiseDateMillis
        )
        scheduleCustomerReminder(
            context = context,
            customer = tempCustomer,
            udhariAmountOverride = udhariAmount.replace("₹", "").trim()
        )
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
