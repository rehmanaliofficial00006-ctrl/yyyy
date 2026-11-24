package com.multi.encription.sms.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.multi.encription.sms.service.SmsGatewayService
import com.multi.encription.sms.utils.ConfigManager

/** Starts the gateway service after the phone reboots or the app is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val config = ConfigManager(context)
        if (config.isServerEnabled || config.isRelayEnabled) {
            try {
                SmsGatewayService.startService(context)
                Log.i("BootReceiver", "Gateway service started after $action")
            } catch (e: Exception) {
                Log.e("BootReceiver", "Could not start service", e)
            }
        }
    }
}
