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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class NovaVoiceService :
    Service(),
    TextToSpeech.OnInitListener {

    companion object {

        const val ACTION_START =
            "hu.novamobile.START_VOICE"

        const val ACTION_STOP =
            "hu.novamobile.STOP_VOICE"

        private const val CHANNEL_ID =
            "nova_voice_channel"

        private const val NOTIFICATION_ID =
            1001

        private val RECOGNITION_TOKEN =
            Any()
    }

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    private var running = false
    private var novaActivated = false
    private var recognitionInProgress = false
    private var speaking = false

    private val handler =
        Handler(
            Looper.getMainLooper()
        )

    // ============================================================
    // CREATE
    // ============================================================

    override fun onCreate() {

        super.onCreate()

        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            createNotification()
        )

        tts =
            TextToSpeech(
                applicationContext,
                this
            )

        createRecognizer()
    }

    // ============================================================
    // START / STOP
    // ============================================================

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_START -> {

                if (!running) {
                    startListening()
                }
            }

            ACTION_STOP -> {

                stopListening()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    // ============================================================
    // NOTIFICATION CHANNEL
    // ============================================================

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "NOVA hangvezérlés",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {

                    description =
                        "NOVA háttérben futó hangvezérlése"

                    setShowBadge(false)
                }

            val manager =
                getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

            manager.createNotificationChannel(
                channel
            )
        }
    }

    // ============================================================
    // NOTIFICATION
    // ============================================================

    private fun createNotification(): Notification {

        val openIntent =
            Intent(
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

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            Notification.Builder(
                this,
                CHANNEL_ID
            )
                .setContentTitle(
                    "NOVA aktív"
                )
                .setContentText(
                    "A NOVA hangvezérlés fut."
                )
                .setSmallIcon(
                    android.R.drawable.ic_btn_speak_now
                )
                .setOngoing(true)
                .setContentIntent(
                    pendingIntent
                )
                .setCategory(
                    Notification.CATEGORY_SERVICE
                )
                .build()

        } else {

            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(
                    "NOVA aktív"
                )
                .setContentText(
                    "A NOVA hangvezérlés fut."
                )
                .setSmallIcon(
                    android.R.drawable.ic_btn_speak_now
                )
                .setOngoing(true)
                .setContentIntent(
                    pendingIntent
                )
                .setCategory(
                    Notification.CATEGORY_SERVICE
                )
                .build()
        }
    }

    // ============================================================
    // TTS
    // ============================================================

    override fun onInit(
        result: Int
    ) {

        if (
            result ==
            TextToSpeech.SUCCESS
        ) {

            tts?.setLanguage(
                Locale(
                    "hu",
                    "HU"
                )
            )

            tts?.setSpeechRate(
                1.0f
            )

            tts?.setPitch(
                1.0f
            )
        }
    }

    private fun speak(
        text: String
    ) {

        if (
            text.isBlank() ||
            !running
        ) {
            return
        }

        speaking = true

        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "nova-response"
        )

        /*
         * Fontos:
         * nem kezdünk azonnal új hallgatást.
         * Így a NOVA nem hallja vissza a saját hangját.
         */

        handler.removeCallbacksAndMessages(
            RECOGNITION_TOKEN
        )

        handler.postDelayed(
            {

                speaking = false

                if (running) {
                    scheduleRecognition(500)
                }

            },
            RECOGNITION_TOKEN,
            1200
        )
    }

    // ============================================================
    // SPEECH RECOGNIZER
    // ============================================================

    private fun createRecognizer() {

        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {
        }

        recognizer =
            SpeechRecognizer
                .createSpeechRecognizer(
                    applicationContext
                )

        recognizer?.setRecognitionListener(

            object : RecognitionListener {

                override fun onReadyForSpeech(
                    params: Bundle?
                ) {

                    recognitionInProgress =
                        true
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

                    recognitionInProgress =
                        false

                    if (
                        running &&
                        !speaking
                    ) {

                        scheduleRecognition(
                            1000
                        )
                    }
                }

                override fun onResults(
                    results: Bundle?
                ) {

                    recognitionInProgress =
                        false

                    if (
                        !running ||
                        speaking
                    ) {
                        return
                    }

                    val heard =
                        results
                            ?.getStringArrayList(
                                SpeechRecognizer
                                    .RESULTS_RECOGNITION
                            )
                            ?.firstOrNull()
                            .orEmpty()

                    if (
                        heard.isNotBlank()
                    ) {

                        handleSpeech(
                            heard
                        )

                    } else {

                        scheduleRecognition(
                            500
                        )
                    }
                }

                override fun onPartialResults(
                    partialResults: Bundle?
                ) {
                    // Nincs parancsvégrehajtás partial resultból.
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
    // START LISTENING
    // ============================================================

    private fun startListening() {

        if (running) {
            return
        }

        if (
            !SpeechRecognizer
                .isRecognitionAvailable(
                    applicationContext
                )
        ) {

            return
        }

        running = true
        novaActivated = false
        recognitionInProgress = false
        speaking = false

        scheduleRecognition(
            500
        )
    }

    // ============================================================
    // SCHEDULE
    // ============================================================

    private fun scheduleRecognition(
        delay: Long
    ) {

        if (
            !running ||
            speaking
        ) {
            return
        }

        handler.removeCallbacksAndMessages(
            RECOGNITION_TOKEN
        )

        handler.postDelayed(
            {

                if (
                    running &&
                    !speaking &&
                    !recognitionInProgress
                ) {

                    startRecognition()
                }

            },
            RECOGNITION_TOKEN,
            delay
        )
    }

    // ============================================================
    // ACTUAL RECOGNITION
    // ============================================================

    private fun startRecognition() {

        if (
            !running ||
            speaking ||
            recognitionInProgress
        ) {
            return
        }

        try {

            if (recognizer == null) {
                createRecognizer()
            }

            val intent =
                Intent(
                    RecognizerIntent
                        .ACTION_RECOGNIZE_SPEECH
                ).apply {

                    putExtra(
                        RecognizerIntent
                            .EXTRA_LANGUAGE,
                        "hu-HU"
                    )

                    putExtra(
                        RecognizerIntent
                            .EXTRA_LANGUAGE_PREFERENCE,
                        "hu-HU"
                    )

                    putExtra(
                        RecognizerIntent
                            .EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent
                            .LANGUAGE_MODEL_FREE_FORM
                    )

                    putExtra(
                        RecognizerIntent
                            .EXTRA_PARTIAL_RESULTS,
                        false
                    )

                    putExtra(
                        RecognizerIntent
                            .EXTRA_MAX_RESULTS,
                        3
                    )
                }

            recognitionInProgress =
                true

            recognizer?.startListening(
                intent
            )

        } catch (_: Exception) {

            recognitionInProgress =
                false

            if (running) {

                scheduleRecognition(
                    1500
                )
            }
        }
    }

    // ============================================================
    // SPEECH PROCESSING
    // ============================================================

    private fun handleSpeech(
        raw: String
    ) {

        val normalized =
            MainActivity.normalize(
                raw
            )

        if (normalized.isBlank()) {

            scheduleRecognition(
                300
            )

            return
        }

        val containsNova =
            Regex(
                "\\bnova\\b"
            ).containsMatchIn(
                normalized
            )

        // ========================================================
        // WAKE WORD
        // ========================================================

        if (!novaActivated) {

            if (!containsNova) {

                scheduleRecognition(
                    300
                )

                return
            }

            novaActivated = true

            val command =
                normalized
                    .replace(
                        Regex(
                            "\\bnova\\b"
                        ),
                        ""
                    )
                    .trim()

            if (command.isBlank()) {

                speak("Igen?")

                return
            }

            executeCommand(
                command
            )

            return
        }

        // ========================================================
        // ACTIVE
        // ========================================================

        val command =
            normalized
                .replace(
                    Regex(
                        "\\bnova\\b"
                    ),
                    ""
                )
                .trim()

        if (command.isBlank()) {

            speak("Igen?")

            return
        }

        executeCommand(
            command
        )
    }

    // ============================================================
    // COMMAND EXECUTION
    // ============================================================

    private fun executeCommand(
        command: String
    ) {

        recognitionInProgress =
            false

        try {
            recognizer?.cancel()
        } catch (_: Exception) {
        }

        try {

            val result =
                CommandRouter.execute(
                    applicationContext,
                    command
                )

            when (result.type) {

                CommandRouter.ResultType.EXECUTED -> {

                    speak(
                        result.response
                    )
                }

                CommandRouter.ResultType.UNKNOWN -> {

                    speak(
                        result.response
                    )
                }

                CommandRouter.ResultType.AMBIGUOUS -> {

                    val options =
                        result.options
                            .joinToString(
                                separator =
                                    " vagy "
                            )

                    speak(
                        if (
                            options.isBlank()
                        ) {

                            result.response

                        } else {

                            "${result.response} $options."
                        }
                    )
                }

                CommandRouter.ResultType.CLARIFICATION -> {

                    speak(
                        result.response
                    )
                }
            }

        } catch (_: Exception) {

            speak(
                "Nem sikerült végrehajtanom a parancsot."
            )
        }
    }

    // ============================================================
    // STOP
    // ============================================================

    private fun stopListening() {

        running = false
        novaActivated = false
        recognitionInProgress = false
        speaking = false

        handler.removeCallbacksAndMessages(
            null
        )

        try {
            recognizer?.cancel()
        } catch (_: Exception) {
        }

        tts?.stop()
    }

    // ============================================================
    // DESTROY
    // ============================================================

    override fun onDestroy() {

        running = false
        novaActivated = false
        recognitionInProgress = false
        speaking = false

        handler.removeCallbacksAndMessages(
            null
        )

        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {
        }

        recognizer = null

        tts?.stop()
        tts?.shutdown()
        tts = null

        super.onDestroy()
    }

    // ============================================================
    // BIND
    // ============================================================

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }
}
