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
import androidx.core.app.NotificationCompat
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
    }

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    private var running = false
    private var novaActivated = false
    private var recognitionStarting = false

    private val handler =
        Handler(Looper.getMainLooper())

    // ============================================================
    // CREATE
    // ============================================================

    override fun onCreate() {

        super.onCreate()

        createNotificationChannel()

        /*
         * A foreground service-nek nagyon gyorsan notificationt
         * kell kapnia, különben Android elkezdi nézegetni, hogy
         * miért is él még ez a folyamat.
         */
        startForeground(
            NOTIFICATION_ID,
            createNotification()
        )

        tts = TextToSpeech(
            applicationContext,
            this
        )

        createRecognizer()
    }

    // ============================================================
    // COMMAND
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

        /*
         * Ha a rendszer valamiért újra létrehozza a service-t,
         * próbáljon visszatérni.
         */
        return START_STICKY
    }

    // ============================================================
    // NOTIFICATION CHANNEL
    // ============================================================

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel = NotificationChannel(
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

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle("NOVA aktív")
            .setContentText(
                "Háttérben figyelek a „Nova” parancsra."
            )
            .setSmallIcon(
                android.R.drawable.ic_btn_speak_now
            )
            .setOngoing(true)
            .setContentIntent(
                pendingIntent
            )
            .setCategory(
                NotificationCompat.CATEGORY_SERVICE
            )
            .build()
    }

    // ============================================================
    // TTS
    // ============================================================

    override fun onInit(result: Int) {

        if (
            result ==
            TextToSpeech.SUCCESS
        ) {

            val languageResult =
                tts?.setLanguage(
                    Locale("hu", "HU")
                )

            tts?.setSpeechRate(1.0f)
            tts?.setPitch(1.0f)

            if (
                languageResult ==
                TextToSpeech.LANG_MISSING_DATA ||
                languageResult ==
                TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                // A készüléken nincs magyar TTS.
            }
        }
    }

    private fun speak(
        text: String
    ) {

        if (text.isBlank()) {
            return
        }

        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "nova-response"
        )
    }

    // ============================================================
    // SPEECH RECOGNIZER
    // ============================================================

    private fun createRecognizer() {

        recognizer?.destroy()

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
                    recognitionStarting = false
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

                    recognitionStarting = false

                    /*
                     * Ne próbáljuk azonnal újraindítani,
                     * mert attól könnyen összeakad a recognizer.
                     */
                    restartRecognition(700)
                }

                override fun onResults(
                    results: Bundle?
                ) {

                    if (!running) {
                        return
                    }

                    recognitionStarting = false

                    val heard =
                        results
                            ?.getStringArrayList(
                                SpeechRecognizer
                                    .RESULTS_RECOGNITION
                            )
                            ?.firstOrNull()
                            .orEmpty()

                    if (heard.isNotBlank()) {

                        handleSpeech(heard)
                    }

                    restartRecognition(500)
                }

                override fun onPartialResults(
                    partialResults: Bundle?
                ) {
                    /*
                     * Szándékosan nem indítunk itt parancsot.
                     *
                     * A Chrome-fagyás egyik lehetséges oka az,
                     * ha a partial result közben már elkezdjük
                     * végrehajtani a parancsot.
                     */
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

        if (
            !SpeechRecognizer
                .isRecognitionAvailable(
                    applicationContext
                )
        ) {
            speak(
                "A beszédfelismerés nem érhető el ezen a készüléken."
            )
            return
        }

        running = true
        novaActivated = false

        restartRecognition(300)
    }

    // ============================================================
    // RESTART
    // ============================================================

    private fun restartRecognition(
        delay: Long
    ) {

        if (!running) {
            return
        }

        if (recognitionStarting) {
            return
        }

        recognitionStarting = true

        handler.postDelayed({

            if (!running) {

                recognitionStarting = false
                return@postDelayed
            }

            try {

                recognizer?.cancel()

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
                            true
                        )

                        putExtra(
                            RecognizerIntent
                                .EXTRA_MAX_RESULTS,
                            3
                        )
                    }

                recognizer?.startListening(
                    intent
                )

            } catch (_: Exception) {

                recognitionStarting = false

                if (running) {
                    restartRecognition(1200)
                }
            }

        }, delay)
    }

    // ============================================================
    // SPEECH PROCESSING
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
                .containsMatchIn(
                    normalized
                )

        // --------------------------------------------------------
        // ÉBRESZTŐSZÓ
        // --------------------------------------------------------

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

                speak(
                    "Igen?"
                )

                return
            }

            executeCommand(command)

            return
        }

        // --------------------------------------------------------
        // MÁR AKTÍV
        // --------------------------------------------------------

        val command =
            normalized
                .replace(
                    Regex("\\bnova\\b"),
                    ""
                )
                .trim()

        if (command.isBlank()) {

            speak("Igen?")

            return
        }

        executeCommand(command)
    }

    // ============================================================
    // COMMAND EXECUTION
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
        recognitionStarting = false

        handler.removeCallbacksAndMessages(
            null
        )

        recognizer?.cancel()

        tts?.stop()
    }

    // ============================================================
    // DESTROY
    // ============================================================

    override fun onDestroy() {

        running = false
        novaActivated = false
        recognitionStarting = false

        handler.removeCallbacksAndMessages(
            null
        )

        recognizer?.cancel()
        recognizer?.destroy()
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
