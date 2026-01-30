package com.oakiha.audia.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

class SleepTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Timber.tag("SleepTimerReceiver").d("Sleep timer expired. Sending intent to AudiobookService")
        val serviceIntent = Intent(context, AudiobookService::class.java).apply {
            action = AudiobookService.ACTION_SLEEP_TIMER_EXPIRED
        }
        try {
            context.startService(serviceIntent)
        } catch (e: Exception) {
            Timber.tag("SleepTimerReceiver").e(e, "Failed to start service for sleep timer")
        }
    }
}
