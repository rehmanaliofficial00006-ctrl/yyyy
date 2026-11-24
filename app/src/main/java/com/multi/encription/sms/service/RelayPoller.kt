package com.multi.encription.sms.service

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.multi.encription.sms.core.SmsManager
import com.multi.encription.sms.utils.ConfigManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

/**
 * Relay mode: instead of waiting for inbound connections (which needs a tunnel),
 * the phone makes OUTBOUND requests to a cloud relay and sends whatever SMS are queued.
 *
 * The relay hands each message out exactly once (it pops it from the queue), so a
 * message can never be sent twice by this poller.
 */
class RelayPoller(private val context: Context) {

    private data class RelayMessage(
        @SerializedName("id") val id: String?,
        @SerializedName("phone_number") val phoneNumber: String?,
        @SerializedName("message") val message: String?
    )

    private data class PullResponse(
        @SerializedName("messages") val messages: List<RelayMessage>?
    )

    private val gson = Gson()
    private val config = ConfigManager(context)
    private val smsManager = SmsManager(context)
    private var job: Job? = null

    // timestamps (ms) of relay SMS sent in the last hour - used for the safety cap
    private val sentTimes = ArrayDeque<Long>()

    companion object {
        private const val TAG = "RelayPoller"
        private const val MAX_PER_PULL = 10
        private const val MAX_MESSAGE_LENGTH = 480
    }

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            Log.i(TAG, "Relay polling started")
            var failures = 0
            while (isActive) {
                val enabled = config.isRelayEnabled &&
                    config.relayUrl.isNotBlank() && config.relayKey.isNotBlank()
                if (!enabled) {
                    delay(5_000)
                    continue
                }
                try {
                    pullAndSend()
                    failures = 0
                    delay(config.relayIntervalSec * 1000L)
                } catch (e: Exception) {
                    failures++
                    Log.w(TAG, "Relay poll failed ($failures): ${e.message}")
                    // exponential back-off, max 60 s
                    delay((config.relayIntervalSec * 1000L * (1L shl minOf(failures, 4))).coerceAtMost(60_000L))
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        Log.i(TAG, "Relay polling stopped")
    }

    private suspend fun pullAndSend() {
        val conn = (URL("${config.relayUrl}/api/relay-pull?max=$MAX_PER_PULL").openConnection() as HttpURLConnection)
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("X-Relay-Key", config.relayKey)
            conn.setRequestProperty("Accept", "application/json")

            val code = conn.responseCode
            if (code == 401 || code == 403) throw IllegalStateException("Relay rejected the key (HTTP $code)")
            if (code !in 200..299) throw IllegalStateException("Relay HTTP $code")

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val messages = gson.fromJson(body, PullResponse::class.java)?.messages.orEmpty()

            for (m in messages) {
                val phone = m.phoneNumber?.trim().orEmpty()
                val text = m.message.orEmpty()
                if (phone.isBlank() || text.isBlank() || text.length > MAX_MESSAGE_LENGTH) {
                    Log.w(TAG, "Skipping invalid relay message ${m.id}")
                    continue
                }
                if (!underHourlyCap()) {
                    Log.w(TAG, "Hourly relay cap reached, dropping message ${m.id}")
                    continue
                }
                val result = smsManager.sendSms(
                    phoneNumber = phone,
                    message = text,
                    apiKey = "relay",
                    requestId = m.id
                )
                if (result.isSuccess) {
                    sentTimes.addLast(System.currentTimeMillis())
                    Log.d(TAG, "Relay SMS sent: ${m.id}")
                } else {
                    Log.w(TAG, "Relay SMS failed ${m.id}: ${result.exceptionOrNull()?.message}")
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun underHourlyCap(): Boolean {
        val cutoff = System.currentTimeMillis() - 3_600_000L
        while (sentTimes.isNotEmpty() && sentTimes.first() < cutoff) sentTimes.removeFirst()
        return sentTimes.size < config.relayMaxPerHour
    }
}
