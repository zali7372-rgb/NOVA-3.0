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
    private var ttsReady = false

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
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:$packageName")
                    )
                )
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
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
            val resultLanguage = tts?.setLanguage(Locale("hu", "HU"))

            ttsReady =
                resultLanguage != TextToSpeech.LANG_MISSING_DATA &&
                resultLanguage != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    private fun ensurePermissionAndStart() {
        if (
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
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
            if (
                grants.firstOrNull() ==
                PackageManager.PERMISSION_GRANTED
            ) {
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

        listen.text = "Hangvezérlés leállítása"
        status.text = "Figyelek…"

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
                    status.text = "Figyelek… Mondd: Nova"
                }

                override fun onBeginningOfSpeech() {
                    status.text = "Hallgatlak…"
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    status.text = "Feldolgozom…"
                }

                override fun onError(error: Int) {

                    if (continuous) {
                        window.decorView.postDelayed(
                            {
                                recognize()
                            },
                            600
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

                    transcript.text = heard

                    if (heard.isNotBlank()) {
                        handleSpeech(heard)
                    }

                    if (continuous) {
                        window.decorView.postDelayed(
                            {
                                recognize()
                            },
                            900
                        )
                    }
                }

                override fun onPartialResults(
                    partialResults: Bundle?
                ) {
                    val text =
                        partialResults
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

                putExtra(
                    RecognizerIntent.EXTRA_PROMPT,
                    "Mondd: Nova..."
                )
            }

        try {
            recognizer?.startListening(intent)
        } catch (_: Exception) {
        }
    }

    private fun stopListening() {

        continuous = false

        recognizer?.cancel()

        listen.text = "Hangvezérlés indítása"
        status.text = "Hangvezérlés szünetel."
    }

    private fun handleSpeech(raw: String) {

        val normalized = normalize(raw)

        if (normalized.isBlank()) return

        /*
         * Nova megszólítások:
         *
         * nova
         * nóva
         * hey nova
         * he nova
         * hé nova
         * hallo nova
         * halló nova
         * szia nova
         * figyelj nova
         * figyelj nóva
         * nova kérlek
         * nova légy szíves
         * stb.
         */

        val wakeWords = listOf(
            "nova",
            "he nova",
            "hey nova",
            "helo nova",
            "hello nova",
            "hallo nova",
            "hallo no va",
            "szia nova",
            "figyelj nova",
            "figyelj no va",
            "nova kerlek",
            "nova legyszi",
            "nova legyel szives"
        )

        val containsWakeWord =
            wakeWords.any {
                normalized.contains(it)
            } || normalized.startsWith("nova")

        if (!containsWakeWord) {
            status.text = "Ébresztőszóra várok: Nova"
            return
        }

        /*
         * A Nova előtti és utáni megszólításokat eltávolítjuk.
         */
        var command = normalized

        wakeWords.forEach {
            command = command.replace(it, " ")
        }

        command =
            command
                .replace(Regex("\\s+"), " ")
                .trim()

        /*
         * Tipikus udvariassági szavak eltávolítása.
         */
        val fillerWords = listOf(
            "kerlek",
            "legyszi",
            "legyel szives",
            "szeretnem",
            "tudnal",
            "tudnad",
            "kerlek szepen",
            "jo lenne ha"
        )

        fillerWords.forEach {
            command = command.replace(it, " ")
        }

        command =
            command
                .replace(Regex("\\s+"), " ")
                .trim()

        if (command.isBlank()) {
            reply(
                "Igen? Miben segíthetek?"
            )
            return
        }

        val response =
            CommandRouter.execute(
                this,
                command
            )

        reply(response)
    }

    private fun reply(message: String) {

        status.text = message
        speak(message)
    }

    private fun speak(text: String) {

        if (!ttsReady) return

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
        recognizer = null

        tts?.stop()
        tts?.shutdown()
        tts = null

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
