package hu.novamobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var transcript: TextView
    private lateinit var listen: Button

    private val requestAudio = 100
    private val requestNotifications = 101

    private var novaRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        status = findViewById(R.id.statusText)
        transcript = findViewById(R.id.transcriptText)
        listen = findViewById(R.id.listenButton)

        listen.setOnClickListener {

            if (novaRunning) {
                stopNovaService()
            } else {
                startNova()
            }
        }

        findViewById<Button>(R.id.settingsButton)
            .setOnClickListener {
                openAppSettings()
            }

        requestNotificationPermissionIfNeeded()

        updateUi(false)
    }

    // ============================================================
    // NOVA INDÍTÁSA
    // ============================================================

    private fun startNova() {

        if (!hasMicrophonePermission()) {

            requestPermissions(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO
                ),
                requestAudio
            )

            return
        }

        startNovaService()
    }

    private fun startNovaService() {

        try {

            val intent = Intent(
                this,
                NovaVoiceService::class.java
            ).apply {
                action = NovaVoiceService.ACTION_START
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            novaRunning = true
            updateUi(true)

        } catch (e: Exception) {

            novaRunning = false
            updateUi(false)

            status.text =
                "Nem sikerült elindítani a NOVA-t."
        }
    }

    // ============================================================
    // NOVA LEÁLLÍTÁSA
    // ============================================================

    private fun stopNovaService() {

        try {

            stopService(
                Intent(
                    this,
                    NovaVoiceService::class.java
                )
            )

            novaRunning = false
            updateUi(false)

        } catch (_: Exception) {

            status.text =
                "Nem sikerült leállítani a NOVA-t."
        }
    }

    // ============================================================
    // UI
    // ============================================================

    private fun updateUi(active: Boolean) {

        if (active) {

            listen.text = "NOVA leállítása"

            status.text =
                "NOVA aktív. Mondd: Nova..."

        } else {

            listen.text = "NOVA indítása"

            status.text =
                "NOVA készen áll."
        }
    }

    // ============================================================
    // MIKROFON ENGEDÉLY
    // ============================================================

    private fun hasMicrophonePermission(): Boolean {

        return checkSelfPermission(
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    // ============================================================
    // ÉRTESÍTÉSI ENGEDÉLY
    // ============================================================

    private fun requestNotificationPermissionIfNeeded() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            if (
                checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {

                requestPermissions(
                    arrayOf(
                        Manifest.permission.POST_NOTIFICATIONS
                    ),
                    requestNotifications
                )
            }
        }
    }

    // ============================================================
    // APP BEÁLLÍTÁSOK
    // ============================================================

    private fun openAppSettings() {

        try {

            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            ).apply {

                data = Uri.parse(
                    "package:$packageName"
                )
            }

            startActivity(intent)

        } catch (_: Exception) {

            try {

                startActivity(
                    Intent(
                        Settings.ACTION_SETTINGS
                    )
                )

            } catch (_: Exception) {

                status.text =
                    "Nem sikerült megnyitni a beállításokat."
            }
        }
    }

    // ============================================================
    // ENGEDÉLY VÁLASZ
    // ============================================================

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grants: IntArray
    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grants
        )

        when (requestCode) {

            requestAudio -> {

                val granted =
                    grants.isNotEmpty() &&
                            grants[0] ==
                            PackageManager.PERMISSION_GRANTED

                if (granted) {

                    startNovaService()

                } else {

                    novaRunning = false

                    listen.text =
                        "NOVA indítása"

                    status.text =
                        "A mikrofonengedély szükséges a NOVA használatához."
                }
            }

            requestNotifications -> {
                // Nem kell külön kezelni.
                // A NOVA ettől még használható.
            }
        }
    }

    // ============================================================
    // NORMALIZÁLÁS
    // ============================================================

    companion object {

        fun normalize(text: String): String {

            return java.text.Normalizer
                .normalize(
                    text.lowercase(
                        java.util.Locale("hu", "HU")
                    ),
                    java.text.Normalizer.Form.NFD
                )
                .replace(
                    Regex("\\p{M}+"),
                    ""
                )
                .replace(
                    Regex("[^a-z0-9 ]"),
                    " "
                )
                .replace(
                    Regex("\\s+"),
                    " "
                )
                .trim()
        }
    }
}
