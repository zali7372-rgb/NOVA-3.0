package hu.novamobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.TextView
import java.text.Normalizer
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var status: TextView
    private lateinit var transcript: TextView
    private lateinit var listen: Button

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    private var listening = false
    private var novaActive = false

    private val handler = Handler(Looper.getMainLooper())

    private val activeTimeout = Runnable {
        novaActive = false
        status.text = "Nova alvó mód."
        speak("Rendben.")
    }

    private val requestAudio = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        status = findViewById(R.id.statusText)
        transcript = findViewById(R.id.transcriptText)
        listen = findViewById(R.id.listenButton)

        tts = TextToSpeech(this, this)

        listen.setOnClickListener {
            if (listening) {
                stopListening()
            } else {
                ensurePermissionAndStart()
            }
        }

        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:$packageName")
                )
            )
        }

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                101
            )
        }
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hu", "HU")
            tts?.setSpeechRate(1.0f)
        }
    }

    private fun ensurePermissionAndStart() {
        if (
            checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                requestAudio
            )
        }
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

        if (
            requestCode == requestAudio &&
            grants.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        } else if (requestCode == requestAudio) {
            status.text = "A mikrofonengedély szükséges."
            speak("Kérlek engedélyezd a mikrofon használatát.")
        }
    }

    private fun startListening() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "A beszédfelismerés nem érhető el."
            return
        }

        listening = true

        listen.text = "Hangvezérlés leállítása"

        status.text = "Figyelek… Mondd: Nova"

        createRecognizer()
        recognize()
    }

    private fun createRecognizer() {

        recognizer?.destroy()

        recognizer =
            SpeechRecognizer.createSpeechRecognizer(this)

        recognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {
                    if (!novaActive) {
                        status.text = "Figyelek… Mondd: Nova"
                    } else {
                        status.text = "Hallgatlak…"
                    }
                }

                override fun onBeginningOfSpeech() {
                    status.text = "Hallgatlak…"
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {

                    if (listening) {
                        handler.postDelayed(
                            { recognize() },
                            400
                        )
                    }
                }

                override fun onResults(results: Bundle?) {

                    val heard =
                        results
                            ?.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                            )
                            ?.firstOrNull()
                            .orEmpty()

                    if (heard.isNotBlank()) {
                        transcript.text = heard
                        handleSpeech(heard)
                    }

                    if (listening) {
                        handler.postDelayed(
                            { recognize() },
                            500
                        )
                    }
                }

                override fun onPartialResults(results: Bundle?) {

                    val text =
                        results
                            ?.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                            )
                            ?.firstOrNull()

                    if (!text.isNullOrBlank()) {
                        transcript.text = text
                    }
                }

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {}
            }
        )
    }

    private fun recognize() {

        if (!listening) return

        val intent =
            Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
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
                    5
                )
            }

        try {
            recognizer?.startListening(intent)
        } catch (_: Exception) {
        }
    }

    private fun handleSpeech(raw: String) {

        val normalized = normalize(raw)

        /*
         * Ha Nova nincs aktív módban:
         * csak olyan mondatra reagálunk,
         * amelyben szerepel az ébresztőszó.
         */

        if (!novaActive) {

            if (!containsWakeWord(normalized)) {
                status.text = "Ébresztőszóra várok: Nova"
                return
            }

            novaActive = true

            restartActiveTimer()

            val command =
                removeWakeWord(normalized)

            if (command.isBlank()) {
                reply("Igen? Miben segíthetek?")
                return
            }

            executeCommand(command)

            return
        }

        /*
         * Nova már aktív.
         * Innen nem kell többé kimondani.
         */

        restartActiveTimer()

        executeCommand(normalized)
    }

    private fun executeCommand(command: String) {

        val result =
            CommandRouter.execute(
                this,
                command
            )

        when (result.type) {

            CommandRouter.ResultType.EXECUTED -> {
                reply(result.message)
            }

            CommandRouter.ResultType.AMBIGUOUS -> {

                val options =
                    result.options.joinToString(
                        " vagy "
                    )

                reply(
                    "Ezek közül melyikre gondoltál: $options?"
                )
            }

            CommandRouter.ResultType.UNKNOWN -> {
                reply(result.message)
            }
        }
    }

    private fun restartActiveTimer() {

        handler.removeCallbacks(activeTimeout)

        handler.postDelayed(
            activeTimeout,
            10_000
        )
    }

    private fun containsWakeWord(text: String): Boolean {

        val wakeWords = listOf(
            "nova",
            "novah",
            "nóva",
            "novaa",
            "noba",
            "novaa",
            "novi",
            "novi a",
            "nova ai",
            "novae"
        )

        return wakeWords.any {
            text.contains(normalize(it))
        }
    }

    private fun removeWakeWord(text: String): String {

        var result = text

        val wakeWords = listOf(
            "nova ai",
            "novae",
            "novi a",
            "novaa",
            "nóva",
            "novah",
            "noba",
            "novi",
            "nova"
        )

        for (word in wakeWords) {
            result =
                result.replace(
                    normalize(word),
                    " "
                )
        }

        return normalize(result)
    }

    private fun reply(message: String) {

        status.text = message

        speak(message)
    }

    private fun speak(text: String) {

        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "nova-response"
        )
    }

    private fun stopListening() {

        listening = false
        novaActive = false

        handler.removeCallbacks(activeTimeout)

        recognizer?.cancel()

        listen.text = "Hangvezérlés indítása"

        status.text = "Hangvezérlés szünetel."
    }

    override fun onDestroy() {

        handler.removeCallbacksAndMessages(null)

        recognizer?.destroy()
        tts?.shutdown()

        super.onDestroy()
    }

    companion object {

        fun normalize(text: String): String {

            return Normalizer
                .normalize(
                    text.lowercase(Locale("hu", "HU")),
                    Normalizer.Form.NFD
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
