package com.multi.encription.sms.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

/** Helps pick which SIM card an SMS is sent from. */
object SimHelper {

    const val AUTO = "AUTO"
    const val SIM1 = "SIM1"
    const val SIM2 = "SIM2"

    data class SimInfo(val slot: Int, val subId: Int, val name: String)

    fun hasPhoneStatePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    /** Accepts "sim1", "SIM 2", "1", "auto" ... and returns AUTO, SIM1 or SIM2. */
    fun normalize(mode: String?): String {
        return when (mode?.trim()?.uppercase()?.replace(" ", "")?.replace("_", "")) {
            "SIM1", "1" -> SIM1
            "SIM2", "2" -> SIM2
            else -> AUTO
        }
    }

    @Suppress("MissingPermission")
    fun getActiveSims(context: Context): List<SimInfo> {
        if (!hasPhoneStatePermission(context)) return emptyList()
        return try {
            val manager = context.getSystemService(SubscriptionManager::class.java)
            val list: List<SubscriptionInfo> = manager?.activeSubscriptionInfoList ?: emptyList()
            list.map { info ->
                val label = (info.carrierName ?: info.displayName)?.toString().orEmpty()
                SimInfo(
                    slot = info.simSlotIndex,
                    subId = info.subscriptionId,
                    name = label.ifBlank { "SIM ${info.simSlotIndex + 1}" }
                )
            }.sortedBy { it.slot }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Returns the Android SmsManager for the chosen SIM, or throws a readable error. */
    fun resolve(context: Context, mode: String): SmsManager {
        val normalized = normalize(mode)
        if (normalized == AUTO) return defaultManager(context)

        val slot = if (normalized == SIM1) 0 else 1
        if (!hasPhoneStatePermission(context)) {
            throw IllegalStateException("Phone permission is needed to choose ${normalized.replace("SIM", "SIM ")}")
        }
        val sim = getActiveSims(context).firstOrNull { it.slot == slot }
            ?: throw IllegalStateException("SIM ${slot + 1} is not available (no active SIM in that slot)")
        return forSubscription(context, sim.subId)
    }

    private fun defaultManager(context: Context): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    private fun forSubscription(context: Context, subId: Int): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getSmsManagerForSubscriptionId(subId)
        }
    }
}
