package com.multi.encription.sms

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.LinearLayout
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.multi.encription.sms.core.SmsManager
import com.multi.encription.sms.database.SmsDatabase
import com.multi.encription.sms.database.SmsEntity
import com.multi.encription.sms.service.SmsGatewayService
import com.multi.encription.sms.ui.SmsHistoryAdapter
import com.multi.encription.sms.utils.ConfigManager
import com.multi.encription.sms.utils.PermissionHelper
import com.multi.encription.sms.utils.SimHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var configManager: ConfigManager
    private lateinit var smsManager: SmsManager
    private lateinit var database: SmsDatabase
    private lateinit var smsHistoryAdapter: SmsHistoryAdapter

    // Views
    private lateinit var statusDot: View
    private lateinit var serverStatusText: TextView
    private lateinit var serverToggle: MaterialSwitch
    private lateinit var baseUrlText: TextView
    private lateinit var apiKeyText: TextView
    private lateinit var portText: TextView
    private lateinit var copyUrlButton: MaterialButton
    private lateinit var copyKeyButton: MaterialButton
    private lateinit var endpointsButton: MaterialButton
    private lateinit var simToggleGroup: MaterialButtonToggleGroup
    private lateinit var simInfoText: TextView
    private lateinit var rateLimitSwitch: MaterialSwitch
    private lateinit var perMinuteEdit: TextInputEditText
    private lateinit var perHourEdit: TextInputEditText
    private lateinit var saveRateLimitButton: MaterialButton
    private lateinit var testApiButton: MaterialButton
    private lateinit var externalDomainButton: MaterialButton
    private lateinit var relayButton: MaterialButton
    private lateinit var tabGroup: MaterialButtonToggleGroup
    private lateinit var dashboardScroll: View
    private lateinit var historyContainer: View
    private lateinit var emptyHistoryText: TextView
    private lateinit var smsHistoryRecyclerView: RecyclerView
    private lateinit var sendSmsFab: ExtendedFloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initializeComponents()
        setupUI()
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateSimInfo()
    }

    private fun initializeComponents() {
        configManager = ConfigManager(this)
        smsManager = SmsManager(this)
        database = SmsDatabase.getDatabase(this)

        statusDot = findViewById(R.id.statusDot)
        serverStatusText = findViewById(R.id.serverStatusText)
        serverToggle = findViewById(R.id.serverToggle)
        baseUrlText = findViewById(R.id.baseUrlText)
        apiKeyText = findViewById(R.id.apiKeyText)
        portText = findViewById(R.id.portText)
        copyUrlButton = findViewById(R.id.copyUrlButton)
        copyKeyButton = findViewById(R.id.copyKeyButton)
        endpointsButton = findViewById(R.id.endpointsButton)
        simToggleGroup = findViewById(R.id.simToggleGroup)
        simInfoText = findViewById(R.id.simInfoText)
        rateLimitSwitch = findViewById(R.id.rateLimitSwitch)
        perMinuteEdit = findViewById(R.id.perMinuteEdit)
        perHourEdit = findViewById(R.id.perHourEdit)
        saveRateLimitButton = findViewById(R.id.saveRateLimitButton)
        testApiButton = findViewById(R.id.testApiButton)
        externalDomainButton = findViewById(R.id.externalDomainButton)
        relayButton = findViewById(R.id.relayButton)
        tabGroup = findViewById(R.id.tabGroup)
        dashboardScroll = findViewById(R.id.dashboardScroll)
        historyContainer = findViewById(R.id.historyContainer)
        emptyHistoryText = findViewById(R.id.emptyHistoryText)
        smsHistoryRecyclerView = findViewById(R.id.smsHistoryRecyclerView)
        sendSmsFab = findViewById(R.id.sendSmsFab)
    }

    private fun setupUI() {
        setupTabs()
        setupHistory()
        setupServer()
        setupConnection()
        setupSim()
        setupRateLimit()

        testApiButton.setOnClickListener { showTestApiDialog() }
        externalDomainButton.setOnClickListener { showExternalDomainDialog() }
        relayButton.setOnClickListener { showRelayDialog() }
        updateRelayButton()
        sendSmsFab.setOnClickListener { showSendSmsDialog() }
        updateExternalDomainButton()
    }

    // ---------------------------------------------------------------- tabs / history

    private fun setupTabs() {
        tabGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val showDashboard = checkedId == R.id.tabDashboard
            dashboardScroll.visibility = if (showDashboard) View.VISIBLE else View.GONE
            historyContainer.visibility = if (showDashboard) View.GONE else View.VISIBLE
        }
        tabGroup.check(R.id.tabDashboard)
    }

    private fun setupHistory() {
        smsHistoryAdapter = SmsHistoryAdapter { sms -> showSmsDetails(sms) }
        smsHistoryRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = smsHistoryAdapter
        }
        database.smsDao().getAllSmsLiveData().observe(this) { smsList ->
            smsHistoryAdapter.submitList(smsList)
            emptyHistoryText.visibility = if (smsList.isNullOrEmpty()) View.VISIBLE else View.GONE
        }
    }

    // ---------------------------------------------------------------- server

    private fun setupServer() {
        serverToggle.isChecked = configManager.isServerEnabled
        serverToggle.setOnCheckedChangeListener { _, isChecked ->
            configManager.isServerEnabled = isChecked
            if (isChecked) {
                SmsGatewayService.startService(this)
            } else {
                SmsGatewayService.stopService(this)
            }
            updateServerStatus()
            updateConnection()
        }
        updateServerStatus()
    }

    private fun updateServerStatus() {
        val running = configManager.isServerEnabled
        serverStatusText.text = if (running) "Running on port ${configManager.serverPort}" else "Stopped"
        statusDot.setBackgroundResource(if (running) R.drawable.dot_on else R.drawable.dot_off)
    }

    // ---------------------------------------------------------------- connection

    private fun setupConnection() {
        apiKeyText.setOnClickListener { showApiKeyDialog() }
        portText.setOnClickListener { showPortDialog() }
        baseUrlText.setOnClickListener { copyToClipboard("Base URL", currentBaseUrl()) }
        copyUrlButton.setOnClickListener { copyToClipboard("Base URL", currentBaseUrl()) }
        copyKeyButton.setOnClickListener { copyToClipboard("API Key", configManager.apiKey) }
        endpointsButton.setOnClickListener { showApiUrlsDialog() }
        updateConnection()
    }

    private fun currentBaseUrl(): String = configManager.getApiBaseUrl(getDeviceIpAddress())

    private fun updateConnection() {
        baseUrlText.text = "URL: ${currentBaseUrl()}"
        apiKeyText.text = "API key: ${configManager.apiKey.take(10)}••••••••"
        portText.text = "Port: ${configManager.serverPort}"
    }

    // ---------------------------------------------------------------- SIM

    private fun setupSim() {
        val initial = when (configManager.simMode) {
            SimHelper.SIM1 -> R.id.sim1Button
            SimHelper.SIM2 -> R.id.sim2Button
            else -> R.id.simAutoButton
        }
        simToggleGroup.check(initial)
        simToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            configManager.simMode = when (checkedId) {
                R.id.sim1Button -> SimHelper.SIM1
                R.id.sim2Button -> SimHelper.SIM2
                else -> SimHelper.AUTO
            }
            updateSimInfo()
        }
        updateSimInfo()
    }

    private fun updateSimInfo() {
        val sims = SimHelper.getActiveSims(this)
        val detected = when {
            !SimHelper.hasPhoneStatePermission(this) -> "Allow the Phone permission so the app can detect your SIMs."
            sims.isEmpty() -> "No active SIM detected."
            else -> sims.joinToString("\n") { "SIM ${it.slot + 1}: ${it.name}" }
        }
        val hint = when (configManager.simMode) {
            SimHelper.SIM1 -> "All messages are sent from SIM 1."
            SimHelper.SIM2 -> "All messages are sent from SIM 2."
            else -> "Auto uses your phone's default SMS SIM."
        }
        simInfoText.text = "$detected\n\n$hint API requests can override this with \"sim\": \"SIM1\" / \"SIM2\" / \"AUTO\"."
    }

    // ---------------------------------------------------------------- rate limit

    private fun setupRateLimit() {
        rateLimitSwitch.isChecked = configManager.isRateLimitEnabled
        perMinuteEdit.setText(configManager.rateLimitPerMinute.toString())
        perHourEdit.setText(configManager.rateLimitPerHour.toString())
        setRateFieldsEnabled(configManager.isRateLimitEnabled)

        rateLimitSwitch.setOnCheckedChangeListener { _, isChecked ->
            configManager.isRateLimitEnabled = isChecked
            setRateFieldsEnabled(isChecked)
        }
        saveRateLimitButton.setOnClickListener { saveRateLimit() }
    }

    private fun setRateFieldsEnabled(enabled: Boolean) {
        perMinuteEdit.isEnabled = enabled
        perHourEdit.isEnabled = enabled
        saveRateLimitButton.isEnabled = enabled
    }

    private fun saveRateLimit() {
        val perMinute = perMinuteEdit.text.toString().trim().toIntOrNull()
        val perHour = perHourEdit.text.toString().trim().toIntOrNull()

        when {
            perMinute == null || perMinute !in 1..100000 ->
                perMinuteEdit.error = "Enter 1 - 100000"
            perHour == null || perHour !in 1..100000 ->
                perHourEdit.error = "Enter 1 - 100000"
            perHour < perMinute ->
                perHourEdit.error = "Must be at least the per-minute value"
            else -> {
                configManager.rateLimitPerMinute = perMinute
                configManager.rateLimitPerHour = perHour
                currentFocus?.let { view ->
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(view.windowToken, 0)
                    view.clearFocus()
                }
                Toast.makeText(this, "Saved: $perMinute / minute, $perHour / hour", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------------------------------------------------------------- permissions

    private fun checkPermissions() {
        val missing = PermissionHelper.missingPermissions(this)
        if (missing.isEmpty()) return
        if (PermissionHelper.shouldShowRationale(this, missing)) {
            AlertDialog.Builder(this)
                .setTitle("Permissions needed")
                .setMessage(
                    "• SMS – to send messages\n" +
                        "• Phone – to detect SIM 1 / SIM 2\n" +
                        "• Notifications – to show the running server\n\n" +
                        "The app never reads your messages or contacts."
                )
                .setPositiveButton("Allow") { _, _ -> PermissionHelper.request(this, missing) }
                .setNegativeButton("Not now", null)
                .show()
        } else {
            PermissionHelper.request(this, missing)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionHelper.PERMISSION_REQUEST_CODE) {
            updateSimInfo()
            if (!PermissionHelper.hasSmsPermissions(this)) {
                Toast.makeText(this, "SMS permission is required to send messages", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun getDeviceIpAddress(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (!networkInterface.isLoopback && networkInterface.isUp) {
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (!address.isLoopbackAddress && address.hostAddress?.contains(':') == false) {
                            return address.hostAddress ?: "192.168.1.100"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // use fallback
        }
        return "192.168.1.100"
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "$label copied", Toast.LENGTH_SHORT).show()
    }

    private fun formatTime(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))

    // ---------------------------------------------------------------- send SMS

    private fun showSendSmsDialog() {
        if (!PermissionHelper.hasSmsPermissions(this)) {
            Toast.makeText(this, "SMS permission required", Toast.LENGTH_SHORT).show()
            checkPermissions()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_send_sms, null)
        val phoneEditText = dialogView.findViewById<EditText>(R.id.phoneEditText)
        val messageEditText = dialogView.findViewById<EditText>(R.id.messageEditText)

        AlertDialog.Builder(this)
            .setTitle("Send SMS (${configManager.simMode.replace("SIM", "SIM ")})")
            .setView(dialogView)
            .setPositiveButton("Send") { _, _ ->
                val phone = phoneEditText.text.toString().trim()
                val message = messageEditText.text.toString().trim()
                if (phone.isBlank() || message.isBlank()) {
                    Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                } else {
                    sendSms(phone, message)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendSms(phoneNumber: String, message: String) {
        lifecycleScope.launch {
            try {
                val result = smsManager.sendSms(phoneNumber, message)
                if (result.isSuccess) {
                    Toast.makeText(this@MainActivity, "SMS queued for sending", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Failed: ${result.exceptionOrNull()?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showSmsDetails(sms: SmsEntity) {
        val lines = mutableListOf(
            "Phone: ${sms.phoneNumber}",
            "Message: ${sms.message}",
            "Status: ${sms.status}",
            "Time: ${formatTime(sms.timestamp)}"
        )
        sms.deliveryTimestamp?.let { lines.add("Delivered: ${formatTime(it)}") }
        sms.errorMessage?.let { lines.add("Error: $it") }
        sms.requestId?.let { lines.add("Request ID: $it") }

        AlertDialog.Builder(this)
            .setTitle("SMS Details")
            .setMessage(lines.joinToString("\n\n"))
            .setPositiveButton("OK", null)
            .show()
    }

    // ---------------------------------------------------------------- dialogs

    private fun showApiKeyDialog() {
        val editText = EditText(this).apply {
            setText(configManager.apiKey)
            setTextIsSelectable(true)
            setPadding(48, 32, 48, 16)
        }

        AlertDialog.Builder(this)
            .setTitle("API Key")
            .setView(editText)
            .setPositiveButton("Copy") { _, _ -> copyToClipboard("API Key", configManager.apiKey) }
            .setNeutralButton("Generate new") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Generate a new key?")
                    .setMessage("The old key stops working immediately. Update every app that uses it.")
                    .setPositiveButton("Generate") { _, _ ->
                        configManager.regenerateApiKey()
                        updateConnection()
                        Toast.makeText(this, "New API key generated", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showPortDialog() {
        val editText = EditText(this).apply {
            setText(configManager.serverPort.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(48, 32, 48, 16)
        }

        AlertDialog.Builder(this)
            .setTitle("Server Port")
            .setMessage("Enter a port between 1024 and 65535:")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val port = editText.text.toString().toIntOrNull()
                if (port != null && port in 1024..65535) {
                    configManager.serverPort = port
                    updateConnection()
                    updateServerStatus()
                    if (configManager.isServerEnabled) {
                        SmsGatewayService.stopService(this)
                        SmsGatewayService.startService(this)
                    }
                    Toast.makeText(this, "Port updated to $port", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Enter a valid port (1024-65535)", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showApiUrlsDialog() {
        val baseUrl = currentBaseUrl()
        val apiKey = configManager.apiKey

        val message = """
            Base URL: $baseUrl
            API Key: $apiKey

            1. Send SMS
            POST $baseUrl/api/send
            X-API-Key: $apiKey
            {"phone_number": "+911234567890", "message": "Hello!", "sim": "SIM1"}

            ("sim" is optional: SIM1, SIM2 or AUTO)

            2. Bulk SMS (max 100)
            POST $baseUrl/api/send-bulk
            {"messages": [{"phone_number": "+911234567890", "message": "Hi"}]}

            3. Status
            GET $baseUrl/api/status?sms_id=123

            4. History
            GET $baseUrl/api/history?limit=10

            5. Info (no key needed)
            GET $baseUrl/api/info
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("API Endpoints")
            .setMessage(message)
            .setPositiveButton("Copy URL") { _, _ -> copyToClipboard("Base URL", baseUrl) }
            .setNeutralButton("Copy key") { _, _ -> copyToClipboard("API Key", apiKey) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showTestApiDialog() {
        if (!configManager.isServerEnabled) {
            Toast.makeText(this, "Turn on the API server first", Toast.LENGTH_SHORT).show()
            return
        }
        if (!PermissionHelper.hasSmsPermissions(this)) {
            Toast.makeText(this, "SMS permission required", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_test_api, null)
        val phoneEditText = dialogView.findViewById<EditText>(R.id.testPhoneEditText)
        val messageEditText = dialogView.findViewById<EditText>(R.id.testMessageEditText)
        messageEditText.setText("Test message from SMS Gateway")

        AlertDialog.Builder(this)
            .setTitle("Test: send an SMS")
            .setView(dialogView)
            .setPositiveButton("Send test") { _, _ ->
                val phone = phoneEditText.text.toString().trim()
                val message = messageEditText.text.toString().trim()
                if (phone.isBlank() || message.isBlank()) {
                    Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                } else {
                    testApiSendSms(phone, message)
                }
            }
            .setNeutralButton("Server info URL") { _, _ ->
                showTestResult(
                    "Server info",
                    "Open this in a browser on the same network:\n\n${currentBaseUrl()}/api/info"
                )
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun testApiSendSms(phoneNumber: String, message: String) {
        lifecycleScope.launch {
            try {
                val result = smsManager.sendSms(
                    phoneNumber, message, configManager.apiKey, "test-${System.currentTimeMillis()}"
                )
                if (result.isSuccess) {
                    showTestResult("Test queued", "SMS ID: ${result.getOrThrow()}\nTo: $phoneNumber\n\nSee the History tab for delivery status.")
                } else {
                    showTestResult("Test failed", result.exceptionOrNull()?.message ?: "Unknown error")
                }
            } catch (e: Exception) {
                showTestResult("Test failed", e.message ?: "Unknown error")
            }
        }
    }

    private fun showTestResult(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun updateExternalDomainButton() {
        externalDomainButton.text =
            if (configManager.useExternalDomain) "Domain: on" else "External domain"
    }

    private fun showExternalDomainDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_external_domain, null)
        val domainEditText = dialogView.findViewById<EditText>(R.id.domainEditText)
        val enableSwitch = dialogView.findViewById<CompoundButton>(R.id.enableExternalDomainSwitch)
        val instructionsText = dialogView.findViewById<TextView>(R.id.instructionsText)

        domainEditText.setText(configManager.externalDomain)
        enableSwitch.isChecked = configManager.useExternalDomain

        instructionsText.text = """
            The app itself speaks plain HTTP. For internet access put HTTPS in front of it:

            • Cloudflare Tunnel or Tailscale (free, no port forwarding)
            • or a VPS with Nginx + Let's Encrypt

            Forward to: ${getDeviceIpAddress()}:${configManager.serverPort}
            Then enter your domain below (e.g. sms.yourdomain.com).
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("External domain")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val domain = domainEditText.text.toString().trim()
                val enabled = enableSwitch.isChecked
                if (enabled && domain.isBlank()) {
                    Toast.makeText(this, "Please enter a domain", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                configManager.externalDomain = domain
                configManager.useExternalDomain = enabled
                updateConnection()
                updateExternalDomainButton()
                Toast.makeText(
                    this,
                    if (enabled) "External domain enabled" else "Using local network",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------------------------------------------------------- relay mode

    private fun updateRelayButton() {
        relayButton.text = if (configManager.isRelayEnabled) "Relay: on" else "Relay mode"
    }

    private fun showRelayDialog() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
        }
        val info = TextView(this).apply {
            text = "Relay mode: the phone fetches SMS from your cloud relay (e.g. Vercel). " +
                "No tunnel, no Termux. Keep the app's notification running."
            textSize = 12f
        }
        val enable = MaterialSwitch(this).apply {
            text = "Enable relay mode"
            isChecked = configManager.isRelayEnabled
        }
        val urlEdit = EditText(this).apply {
            hint = "Relay URL, e.g. https://my-site.vercel.app"
            setText(configManager.relayUrl)
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
        }
        val keyEdit = EditText(this).apply {
            hint = "Relay key (same as RELAY_KEY on Vercel)"
            setText(configManager.relayKey)
            setSingleLine()
        }
        val intervalEdit = EditText(this).apply {
            hint = "Check every N seconds (2-120)"
            setText(configManager.relayIntervalSec.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSingleLine()
        }
        val capEdit = EditText(this).apply {
            hint = "Max SMS per hour (safety cap)"
            setText(configManager.relayMaxPerHour.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSingleLine()
        }
        val battery = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Allow running in background (battery)"
            setOnClickListener { requestIgnoreBatteryOptimizations() }
        }
        listOf(info, enable, urlEdit, keyEdit, intervalEdit, capEdit, battery).forEach { box.addView(it) }

        AlertDialog.Builder(this)
            .setTitle("Relay mode")
            .setView(android.widget.ScrollView(this).apply { addView(box) })
            .setPositiveButton("Save") { _, _ ->
                val url = urlEdit.text.toString().trim()
                val key = keyEdit.text.toString().trim()
                if (enable.isChecked && (!url.startsWith("https://") || key.length < 12)) {
                    Toast.makeText(
                        this,
                        "Need an https:// relay URL and a key of 12+ characters",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }
                configManager.relayUrl = url
                configManager.relayKey = key
                configManager.relayIntervalSec =
                    intervalEdit.text.toString().toIntOrNull() ?: ConfigManager.DEFAULT_RELAY_INTERVAL_SEC
                configManager.relayMaxPerHour =
                    capEdit.text.toString().toIntOrNull() ?: ConfigManager.DEFAULT_RELAY_MAX_PER_HOUR
                configManager.isRelayEnabled = enable.isChecked
                updateRelayButton()
                // restart so the service picks up the new settings
                SmsGatewayService.stopService(this)
                if (configManager.isRelayEnabled || configManager.isServerEnabled) {
                    SmsGatewayService.startService(this)
                }
                Toast.makeText(
                    this,
                    if (enable.isChecked) "Relay mode on" else "Relay mode off",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestIgnoreBatteryOptimizations() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                Toast.makeText(this, "Already allowed", Toast.LENGTH_SHORT).show()
                return
            }
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}
