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
                if (hasMicrophonePermission()) {
                    startNovaService()
                } else {
                    requestPermissions(
                        arrayOf(Manifest.permission.RECORD_AUDIO),
                        requestAudio
                    )
                }
            }
        }

        findViewById<Button>(R.id.settingsButton)
            .setOnClickListener {

                try {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    ).apply {
                        data = Uri.parse("package:$packageName")
                    }

                    startActivity(intent)

                } catch (_: Exception) {

                    startActivity(
                        Intent(Settings.ACTION_SETTINGS)
                    )
                }
            }

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

        status.text = "NOVA készen áll."
        listen.text = "NOVA indítása"
    }

    private fun hasMicrophonePermission(): Boolean {
        return checkSelfPermission(
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startNovaService() {

        try {

            val intent = Intent(
                this,
                NovaVoiceService::class.java
            ).apply {
                action = NovaVoiceService.ACTION_START
            }

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            novaRunning = true

            listen.text = "NOVA leállítása"
            status.text = "NOVA aktív."

        } catch (e: Exception) {

            novaRunning = false

            listen.text = "NOVA indítása"
            status.text =
                "Nem sikerült elindítani a NOVA-t."

            e.printStackTrace()
        }
    }

    private fun stopNovaService() {

        try {

            val intent = Intent(
                this,
                NovaVoiceService::class.java
            ).apply {
                action = NovaVoiceService.ACTION_STOP
            }

            startService(intent)

        } catch (e: Exception) {
            e.printStackTrace()
        }

        novaRunning = false

        listen.text = "NOVA indítása"
        status.text = "NOVA leállítva."
    }

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

        if (requestCode == requestAudio) {

            if (
                grants.isNotEmpty() &&
                grants[0] ==
                PackageManager.PERMISSION_GRANTED
            ) {
                startNovaService()
            } else {
                status.text =
                    "A mikrofonengedély szükséges a NOVA használatához."
            }
        }
    }

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
                    Regex("\\p{M}"),
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
