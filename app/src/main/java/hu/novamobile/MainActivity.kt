package hu.novamobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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

    private val requestAudio = 100
    private val requestNotifications = 101

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

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                requestNotifications
            )
        }
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            val languageResult = tts?.setLanguage(Locale("hu", "HU"))

            if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                languageResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                tts?.language = Locale.getDefault()
            }
        }
    }

    private fun ensurePermissionAndStart() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
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

        recognizer = SpeechRecognizer.createSpeechRecognizer(this)

        recognizer?.setRecognitionListener(object : RecognitionListener {

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
                        { recognize() },
                        600
                    )
                }
            }

            override fun onResults(results: Bundle?) {
                val heard = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()

                if (heard.isNotBlank()) {
                    transcript.text = heard
                    handleSpeech(heard)
                }

                if (continuous) {
                    window.decorView.postDelayed(
                        { recognize() },
                        900
                    )
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()

                if (!text.isNullOrBlank()) {
                    transcript.text = text
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun recognize() {
        if (!continuous) return

        val intent = Intent(
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

    private fun stopListening() {
        continuous = false

        try {
            recognizer?.cancel()
        } catch (_: Exception) {
        }

        listen.text = "Hangvezérlés indítása"
        status.text = "Hangvezérlés szünetel."
    }

    private fun handleSpeech(raw: String) {
        val normalized = normalize(raw)

        val command = removeWakeWord(normalized)

        if (command == null) {
            status.text = "Ébresztőszóra várok: Nova"
            return
        }

        if (command.isBlank()) {
            reply("Igen? Miben segíthetek?")
            return
        }

        val response = CommandRouter.execute(
            this,
            command
        )

        reply(response)
    }

    private fun removeWakeWord(text: String): String? {
        var value = normalize(text)

        if (value.isBlank()) {
            return null
        }

        /*
         * A normalize() után az ékezetes formákból:
         *
         * Nova -> nova
         * Nóva -> nova
         * NÓVA -> nova
         *
         * lesz.
         */

        val wakeWords = listOf(
            "nova",
            "hey nova",
            "he nova",
            "hej nova",
            "helo nova",
            "hello nova",
            "ok nova",
            "oke nova",
            "okay nova",
            "okey nova",
            "hallo nova",
            "hallod nova",
            "figyelj nova",
            "figyel nova",
            "nova figyelj",
            "nova hallod",
            "nova hallasz",
            "nova ebreszto",
            "nova ebredj",
            "nova segits",
            "nova kerlek",
            "nova legyszives",
            "nova legy szives"
        )

        val sorted = wakeWords.sortedByDescending { it.length }

        for (wakeWord in sorted) {
            if (value == wakeWord) {
                return ""
            }

            if (value.startsWith("$wakeWord ")) {
                return value.removePrefix(wakeWord).trim()
            }

            if (value.startsWith("$wakeWord,")) {
                return value.removePrefix(wakeWord).trim(' ', ',')
            }
        }

        /*
         * Speech-to-Text néha furcsa dolgokat ír.
         * Ha a mondat első néhány szavában szerepel a Nova,
         * akkor megszólításként kezeljük.
         */

        val words = value.split(" ")

        val novaIndex = words.indexOfFirst {
            it == "nova"
        }

        if (novaIndex in 0..3) {
            return words
                .drop(novaIndex + 1)
                .joinToString(" ")
                .trim()
        }

        return null
    }

    private fun reply(message: String) {
        status.text = message
        speak(message)
    }

    private fun speak(text: String) {
        try {
            tts?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "nova-response"
            )
        } catch (_: Exception) {
        }
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
