package com.example.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.math.BigDecimal

class PromiseReminderReceiver : BroadcastReceiver() {
    companion object {
        const val CHANNEL_ID = "pashu_aahar_reminders"
        const val CHANNEL_NAME = "Payment Promise Reminders"
        const val EXTRA_CUSTOMER_ID = "extra_customer_id"
        const val EXTRA_CUSTOMER_NAME = "extra_customer_name"
        const val EXTRA_CUSTOMER_PHONE = "extra_customer_phone"
        const val EXTRA_UDHARI_AMOUNT = "extra_udhari_amount"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val customerId = intent.getStringExtra(EXTRA_CUSTOMER_ID) ?: return
        val initialName = intent.getStringExtra(EXTRA_CUSTOMER_NAME) ?: "Customer"
        val initialPhone = intent.getStringExtra(EXTRA_CUSTOMER_PHONE) ?: ""
        val initialAmount = intent.getStringExtra(EXTRA_UDHARI_AMOUNT)?.replace("₹", "")?.trim() ?: ""

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val liveCustomer = runCatching {
                    AppDatabase.getInstance(context).customerDao().getCustomerById(customerId)
                }.getOrNull()

                // Skip notification if customer already settled their udhari
                if (liveCustomer != null && liveCustomer.totalUdhari <= BigDecimal.ZERO) {
                    return@launch
                }

                val customerName = liveCustomer?.name?.takeIf { it.isNotBlank() } ?: initialName
                val customerPhone = liveCustomer?.phone?.takeIf { it.isNotBlank() } ?: initialPhone
                val udhariAmount = when {
                    liveCustomer != null && liveCustomer.totalUdhari > BigDecimal.ZERO ->
                        liveCustomer.totalUdhari.stripTrailingZeros().toPlainString()
                    initialAmount.isNotBlank() && initialAmount != "0" && initialAmount != "0.00" ->
                        initialAmount
                    else -> "0"
                }

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                createChannel(notificationManager)

                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val openPendingIntent = PendingIntent.getActivity(
                    context,
                    customerId.hashCode(),
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Payment Due Today: $customerName")
                    .setContentText("Promised payment for cattle feed. Outstanding: ₹$udhariAmount")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(openPendingIntent)

                if (customerPhone.isNotBlank()) {
                    val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$customerPhone"))
                    val callPendingIntent = PendingIntent.getActivity(
                        context,
                        customerId.hashCode() + 1,
                        callIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    builder.addAction(android.R.drawable.ic_menu_call, "Call Customer", callPendingIntent)
                }

                notificationManager.notify(customerId.hashCode(), builder.build())
                ReminderScheduler.markReminderFiredForDate(
                    context = context,
                    customerId = customerId,
                    dateMillis = liveCustomer?.promiseDate ?: System.currentTimeMillis()
                )
            } finally {
                pendingResult?.finish()
            }
        }
    }

    private fun createChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for customer promise-to-pay dates"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
