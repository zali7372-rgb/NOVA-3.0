package hu.novamobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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

    private var continuous = false
    private var novaActivated = false
    private var waitingForClarification = false

    private val requestAudio = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        status = findViewById(R.id.statusText)
        transcript = findViewById(R.id.transcriptText)
        listen = findViewById(R.id.listenButton)

        tts = TextToSpeech(this, this)

        listen.setOnClickListener {
            if (continuous) {
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
        super.onRequestPermissionsResult(requestCode, permissions, grants)

        if (requestCode == requestAudio) {
            if (grants.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                startListening()
            } else {
                status.text = "A mikrofonengedély szükséges."
                speak("Kérlek engedélyezd a mikrofon használatát.")
            }
        }
    }

    private fun startListening() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "A beszédfelismerés nem érhető el."
            speak("A beszédfelismerés nem érhető el ezen a készüléken.")
            return
        }

        continuous = true
        novaActivated = false
        waitingForClarification = false

        listen.text = "Hangvezérlés leállítása"
        status.text = "Figyelek… Mondd: Nova"

        createRecognizer()
        recognize()
    }

    private fun createRecognizer() {

        recognizer?.destroy()

        recognizer =
            SpeechRecognizer.createSpeechRecognizer(this).also { speech ->

                speech.setRecognitionListener(
                    object : RecognitionListener {

                        override fun onReadyForSpeech(params: Bundle?) {
                            if (!novaActivated) {
                                status.text = "Figyelek… Mondd: Nova"
                            } else if (!waitingForClarification) {
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

                            if (!continuous) return

                            window.decorView.postDelayed(
                                {
                                    recognize()
                                },
                                350
                            )
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

                            if (continuous) {
                                window.decorView.postDelayed(
                                    {
                                        recognize()
                                    },
                                    500
                                )
                            }
                        }

                        override fun onPartialResults(partial: Bundle?) {

                            val text =
                                partial
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
    }

    private fun recognize() {

        if (!continuous) return

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

        if (normalized.isBlank()) return

        /*
         * Első alkalommal Nova kell.
         * Utána már nincs szükség rá.
         */
        if (!novaActivated) {

            if (!containsWakeWord(normalized)) {
                status.text = "Ébresztőszóra várok: Nova"
                return
            }

            novaActivated = true

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
         * Ha már aktiválva van, simán jöhet a parancs.
         */
        if (
            normalized == "nova" ||
            normalized == "szia nova" ||
            normalized == "hej nova" ||
            normalized == "hey nova"
        ) {
            reply("Igen?")
            return
        }

        /*
         * Kikapcsolási parancsok.
         */
        if (isDeactivateCommand(normalized)) {
            novaActivated = false
            waitingForClarification = false
            reply("Rendben, visszatérek alvó módba.")
            return
        }

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
                waitingForClarification = false
                reply(result.message)
            }

            CommandRouter.ResultType.CLARIFICATION -> {

                waitingForClarification = true

                reply(
                    result.message
                )
            }

            CommandRouter.ResultType.UNKNOWN -> {
                waitingForClarification = false
                reply(result.message)
            }
        }
    }

    private fun containsWakeWord(text: String): Boolean {

        val words = text.split(" ")

        return words.any {
            fuzzySimilarity(it, "nova") >= 0.70
        }
    }

    private fun removeWakeWord(text: String): String {

        val words = text.split(" ").toMutableList()

        val index =
            words.indexOfFirst {
                fuzzySimilarity(it, "nova") >= 0.70
            }

        if (index >= 0) {
            words.removeAt(index)
        }

        return words.joinToString(" ").trim()
    }

    private fun isDeactivateCommand(text: String): Boolean {

        val commands = listOf(
            "allj le",
            "allj le nova",
            "hagyd abba",
            "ne figyelj",
            "kapcsold ki a hangvezerlest",
            "kapcsold ki a hangiranyitast",
            "hangvezerles leallitasa",
            "hangiranyitas leallitasa",
            "aludj",
            "menj aludni",
            "szunet",
            "stop",
            "leallitas"
        )

        return commands.any {
            fuzzySimilarity(text, it) >= 0.72
        }
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

    override fun onDestroy() {

        continuous = false

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

        fun fuzzySimilarity(
            a: String,
            b: String
        ): Double {

            if (a == b) return 1.0

            if (a.isBlank() || b.isBlank()) return 0.0

            val distance =
                levenshteinDistance(a, b)

            val maxLength =
                maxOf(a.length, b.length)

            return if (maxLength == 0) {
                1.0
            } else {
                1.0 -
                    distance.toDouble() /
                    maxLength.toDouble()
            }
        }

        private fun levenshteinDistance(
            a: String,
            b: String
        ): Int {

            val matrix =
                Array(a.length + 1) {
                    IntArray(b.length + 1)
                }

            for (i in 0..a.length) {
                matrix[i][0] = i
            }

            for (j in 0..b.length) {
                matrix[0][j] = j
            }

            for (i in 1..a.length) {
                for (j in 1..b.length) {

                    val cost =
                        if (a[i - 1] == b[j - 1]) {
                            0
                        } else {
                            1
                        }

                    matrix[i][j] =
                        minOf(
                            matrix[i - 1][j] + 1,
                            matrix[i][j - 1] + 1,
                            matrix[i - 1][j - 1] + cost
                        )
                }
            }

            return matrix[a.length][b.length]
        }
    }
}
