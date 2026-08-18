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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        status = findViewById(R.id.statusText)
        transcript = findViewById(R.id.transcriptText)
        listen = findViewById(R.id.listenButton)

        listen.setOnClickListener {

            if (NovaService.isRunning) {
                stopNovaService()
            } else {
                ensurePermissionsAndStart()
            }
        }

        findViewById<Button>(R.id.settingsButton)
            .setOnClickListener {

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

                    startActivity(
                        Intent(Settings.ACTION_SETTINGS)
                    )
                }
            }

        requestRequiredPermissions()

        updateUi()
    }

    // ============================================================
    // ENGEDÉLYEK
    // ============================================================

    private fun requestRequiredPermissions() {

        if (
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            requestPermissions(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO
                ),
                requestAudio
            )

            return
        }

        if (
            Build.VERSION.SDK_INT >= 33 &&
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

    private fun ensurePermissionsAndStart() {

        if (
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {

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

                if (
                    Build.VERSION.SDK_INT >= 33 &&
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

                } else {

                    startNovaService()
                }

            } else {

                status.text =
                    "A mikrofonengedély szükséges."
            }
        }

        if (requestCode == requestNotifications) {

            if (
                checkSelfPermission(
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startNovaService()
            }
        }
    }

    // ============================================================
    // NOVA SERVICE
    // ============================================================

    private fun startNovaService() {

        val intent =
            Intent(
                this,
                NovaService::class.java
            ).apply {
                action = NovaService.ACTION_START
            }

        try {

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {

                startForegroundService(intent)

            } else {

                startService(intent)
            }

            status.text =
                "NOVA elindult."

            listen.text =
                "Hangvezérlés leállítása"

        } catch (_: Exception) {

            status.text =
                "Nem sikerült elindítani a NOVA-t."
        }
    }

    private fun stopNovaService() {

        val intent =
            Intent(
                this,
                NovaService::class.java
            ).apply {
                action = NovaService.ACTION_STOP
            }

        try {

            startService(intent)

        } catch (_: Exception) {

            stopService(
                Intent(
                    this,
                    NovaService::class.java
                )
            )
        }

        status.text =
            "Hangvezérlés leállítva."

        listen.text =
            "Hangvezérlés indítása"
    }

    // ============================================================
    // UI
    // ============================================================

    private fun updateUi() {

        if (NovaService.isRunning) {

            listen.text =
                "Hangvezérlés leállítása"

            status.text =
                "NOVA aktív."

        } else {

            listen.text =
                "Hangvezérlés indítása"

            status.text =
                "NOVA készen áll."
        }
    }

    override fun onResume() {

        super.onResume()

        updateUi()

        transcript.text =
            NovaService.lastTranscript
    }

    // ============================================================
    // NORMALIZÁLÁS
    // ============================================================

    companion object {

        fun normalize(
            text: String
        ): String {

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
