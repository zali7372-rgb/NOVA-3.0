package hu.novamobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import java.util.Locale

class NovaVoiceService : Service() {

    companion object {
        const val ACTION_START = "hu.novamobile.START_VOICE"
        const val ACTION_STOP = "hu.novamobile.STOP_VOICE"

        private const val CHANNEL_ID = "nova_voice_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private var recognizer: SpeechRecognizer? = null

    private var running = false
    private var novaActivated = false
    private var restarting = false

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            createNotification()
        )

        createRecognizer()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_START -> {
                startVoiceRecognition()
            }

            ACTION_STOP -> {
                stopVoiceRecognition()
                stopSelf()
            }
        }

        return START_STICKY
    }

    // ============================================================
    // NOTIFICATION
    // ============================================================

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "NOVA hangvezérlés",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "NOVA háttérben futó hangvezérlése"
                setShowBadge(false)
            }

            val manager =
                getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {

        val openIntent = Intent(
            this,
            MainActivity::class.java
        )

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle("NOVA aktív")
            .setContentText("Háttérben figyelek a „Nova” ébresztőszóra.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    // ============================================================
    // RECOGNIZER
    // ============================================================

    private fun createRecognizer() {

        recognizer?.destroy()

        recognizer =
            SpeechRecognizer.createSpeechRecognizer(this)

        recognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(
                    params: Bundle?
                ) {
                    restarting = false
                }

                override fun onBeginningOfSpeech() {
                }

                override fun onRmsChanged(
                    rmsdB: Float
                ) {
                }

                override fun onBufferReceived(
                    buffer: ByteArray?
                ) {
                }

                override fun onEndOfSpeech() {
                }

                override fun onError(
                    error: Int
                ) {
                    if (!running) {
                        return
                    }

                    restartRecognition(500)
                }

                override fun onResults(
                    results: Bundle?
                ) {

                    if (!running) {
                        return
                    }

                    val heard =
                        results
                            ?.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                            )
                            ?.firstOrNull()
                            .orEmpty()

                    if (heard.isNotBlank()) {

                        handleSpeech(heard)
                    }

                    restartRecognition(400)
                }

                override fun onPartialResults(
                    partialResults: Bundle?
                ) {
                }

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {
                }
            }
        )
    }

    // ============================================================
    // START
    // ============================================================

    private fun startVoiceRecognition() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            return
        }

        running = true
        novaActivated = false

        restartRecognition(200)
    }

    private fun restartRecognition(
        delay: Long
    ) {

        if (!running || restarting) {
            return
        }

        restarting = true

        android.os.Handler(
            mainLooper
        ).postDelayed(
            {

                if (!running) {
                    restarting = false
                    return@postDelayed
                }

                try {

                    recognizer?.cancel()

                    val intent =
                        Intent(
                            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
                        ).apply {

                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE,
                                "hu-HU"
                            )

                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                                "hu-HU"
                            )

                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )

                            putExtra(
                                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                                true
                            )

                            putExtra(
                                RecognizerIntent.EXTRA_MAX_RESULTS,
                                3
                            )

                            putExtra(
                                RecognizerIntent.EXTRA_CALLING_PACKAGE,
                                packageName
                            )
                        }

                    recognizer?.startListening(intent)

                } catch (_: Exception) {

                    restarting = false

                    if (running) {
                        restartRecognition(1000)
                    }

                    return@postDelayed
                }

                restarting = false
            },
            delay
        )
    }

    // ============================================================
    // SPEECH
    // ============================================================

    private fun handleSpeech(
        raw: String
    ) {

        val normalized =
            MainActivity.normalize(raw)

        if (normalized.isBlank()) {
            return
        }

        val containsNova =
            Regex("\\bnova\\b")
                .containsMatchIn(normalized)

        if (!novaActivated) {

            if (!containsNova) {
                return
            }

            novaActivated = true

            val command =
                normalized
                    .replace(
                        Regex("\\bnova\\b"),
                        ""
                    )
                    .trim()

            if (command.isBlank()) {
                return
            }

            executeCommand(command)

            return
        }

        val command =
            normalized
                .replace(
                    Regex("\\bnova\\b"),
                    ""
                )
                .trim()

        if (command.isBlank()) {
            return
        }

        executeCommand(command)
    }

    // ============================================================
    // COMMAND
    // ============================================================

    private fun executeCommand(
        command: String
    ) {

        try {

            val result =
                CommandRouter.execute(
                    applicationContext,
                    command
                )

            when (result.type) {

                CommandRouter.ResultType.EXECUTED -> {
                    speak(result.response)
                }

                CommandRouter.ResultType.UNKNOWN -> {
                    speak(result.response)
                }

                CommandRouter.ResultType.AMBIGUOUS -> {

                    val options =
                        result.options.joinToString(
                            separator = " vagy "
                        )

                    speak(
                        if (options.isBlank()) {
                            result.response
                        } else {
                            "${result.response} $options."
                        }
                    )
                }

                CommandRouter.ResultType.CLARIFICATION -> {
                    speak(result.response)
                }
            }

        } catch (_: Exception) {

            speak(
                "Nem sikerült végrehajtanom a parancsot."
            )
        }
    }

    // ============================================================
    // TTS
    // ============================================================

    private fun speak(
        text: String
    ) {

        if (text.isBlank()) {
            return
        }

        val intent =
            Intent(
                this,
                NovaTtsService::class.java
            ).apply {
                putExtra(
                    "text",
                    text
                )
            }

        try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                startService(intent)

            } else {

                startService(intent)
            }

        } catch (_: Exception) {
        }
    }

    // ============================================================
    // STOP
    // ============================================================

    private fun stopVoiceRecognition() {

        running = false
        novaActivated = false
        restarting = false

        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    override fun onDestroy() {

        stopVoiceRecognition()

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }
}
