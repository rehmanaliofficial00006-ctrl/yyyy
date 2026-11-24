package com.multi.encription.sms.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionHelper {

    const val PERMISSION_REQUEST_CODE = 1001

    /** SEND_SMS = send, READ_PHONE_STATE = detect SIM 1 / SIM 2, POST_NOTIFICATIONS = service notification. */
    private fun wanted(): List<String> = buildList {
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Sending is the only permission the app can not work without. */
    fun hasSmsPermissions(context: Context): Boolean = granted(context, Manifest.permission.SEND_SMS)

    fun missingPermissions(context: Context): List<String> = wanted().filter { !granted(context, it) }

    fun shouldShowRationale(activity: Activity, permissions: List<String>): Boolean =
        permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }

    fun request(activity: Activity, permissions: List<String>) {
        if (permissions.isEmpty()) return
        ActivityCompat.requestPermissions(activity, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
    }
}
