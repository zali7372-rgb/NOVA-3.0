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

    companion object {
        private const val REQUEST_AUDIO = 100
        private const val REQUEST_NOTIFICATIONS = 101

        fun normalize(text: String): String {
            return java.text.Normalizer
                .normalize(
                    text.lowercase(java.util.Locale("hu", "HU")),
                    java.text.Normalizer.Form.NFD
                )
                .replace(Regex("\\p{M}"), "")
                .replace(Regex("[^a-z0-9 ]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        status = findViewById(R.id.statusText)
        transcript = findViewById(R.id.transcriptText)
        listen = findViewById(R.id.listenButton)

        status.text = "NOVA készen áll."

        listen.setOnClickListener {
            if (hasMicrophonePermission()) {
                startNovaService()
            } else {
                requestPermissions(
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    REQUEST_AUDIO
                )
            }
        }

        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            openAppSettings()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (
                checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATIONS
                )
            }
        }
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            listen.text = "NOVA aktív"
            status.text = "NOVA aktív. Mondd: Nova..."

        } catch (e: Exception) {
            status.text =
                "Nem sikerült elindítani a NOVA szolgáltatást."
        }
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            ).apply {
                data = Uri.parse("package:$packageName")
            }

            startActivity(intent)

        } catch (_: Exception) {
            try {
                startActivity(
                    Intent(Settings.ACTION_SETTINGS)
                )
            } catch (_: Exception) {
                status.text =
                    "Nem sikerült megnyitni a beállításokat."
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        when (requestCode) {

            REQUEST_AUDIO -> {
                if (
                    grantResults.isNotEmpty() &&
                    grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    startNovaService()
                } else {
                    status.text =
                        "A mikrofonengedély szükséges a NOVA használatához."
                }
            }

            REQUEST_NOTIFICATIONS -> {
                // Nem kritikus, ha a felhasználó nem engedélyezi.
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
