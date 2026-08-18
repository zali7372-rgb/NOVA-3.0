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
    private var novaActivated = false

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

        findViewById<Button>(R.id.settingsButton)
            .setOnClickListener {

                try {

                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    ).apply {

                        data =
                            android.net.Uri.parse(
                                "package:$packageName"
                            )
                    }

                    startActivity(intent)

                } catch (_: Exception) {

                    startActivity(
                        Intent(
                            Settings.ACTION_SETTINGS
                        )
                    )
                }
            }

        if (android.os.Build.VERSION.SDK_INT >= 33) {

            requestPermissions(
                arrayOf(
                    Manifest.permission.POST_NOTIFICATIONS
                ),
                requestNotifications
            )
        }
    }

    // ============================================================
    // TTS
    // ============================================================

    override fun onInit(result: Int) {

        if (result == TextToSpeech.SUCCESS) {

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

                status.text =
                    "A magyar beszédhang nem érhető el."
            }
        }
    }

    private fun speak(text: String) {

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
    // ENGEDÉLYEK
    // ============================================================

    private fun ensurePermissionAndStart() {

        if (
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {

            startListening()

        } else {

            requestPermissions(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO
                ),
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

        if (requestCode == requestAudio) {

            if (
                grants.isNotEmpty() &&
                grants[0] ==
                PackageManager.PERMISSION_GRANTED
            ) {

                startListening()

            } else {

                status.text =
                    "A mikrofonengedély szükséges a hangvezérléshez."

                speak(
                    "Kérlek engedélyezd a mikrofon használatát."
                )
            }
        }
    }

    // ============================================================
    // HALLGATÁS INDÍTÁSA
    // ============================================================

    private fun startListening() {

        if (
            !SpeechRecognizer.isRecognitionAvailable(
                this
            )
        ) {

            status.text =
                "A beszédfelismerés nem érhető el ezen a készüléken."

            speak(
                "A beszédfelismerés nem érhető el ezen a készüléken."
            )

            return
        }

        continuous = true
        novaActivated = false

        listen.text =
            "Hangvezérlés leállítása"

        status.text =
            "Figyelek... Mondd: Nova"

        createRecognizer()
        recognize()
    }

    // ============================================================
    // SPEECH RECOGNIZER
    // ============================================================

    private fun createRecognizer() {

        recognizer?.destroy()

        recognizer =
            SpeechRecognizer.createSpeechRecognizer(
                this
            )

        recognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(
                    params: Bundle?
                ) {

                    if (!continuous) {
                        return
                    }

                    status.text =
                        if (novaActivated) {
                            "Hallgatlak..."
                        } else {
                            "Figyelek... Mondd: Nova"
                        }
                }

                override fun onBeginningOfSpeech() {

                    if (continuous) {
                        status.text =
                            "Hallgatlak..."
                    }
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

                    if (continuous) {
                        status.text =
                            "Feldolgozom..."
                    }
                }

                override fun onError(
                    error: Int
                ) {

                    if (!continuous) {
                        return
                    }

                    window.decorView.postDelayed(
                        {

                            if (continuous) {
                                recognize()
                            }

                        },
                        500
                    )
                }

                override fun onResults(
                    results: Bundle?
                ) {

                    if (!continuous) {
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

                    if (heard.isNotBlank()) {

                        transcript.text =
                            heard

                        handleSpeech(heard)
                    }

                    window.decorView.postDelayed(
                        {

                            if (continuous) {
                                recognize()
                            }

                        },
                        700
                    )
                }

                override fun onPartialResults(
                    partialResults: Bundle?
                ) {

                    if (!continuous) {
                        return
                    }

                    val partial =
                        partialResults
                            ?.getStringArrayList(
                                SpeechRecognizer
                                    .RESULTS_RECOGNITION
                            )
                            ?.firstOrNull()

                    if (!partial.isNullOrBlank()) {

                        transcript.text =
                            partial
                    }
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
    // BESZÉDFELISMERÉS
    // ============================================================

    private fun recognize() {

        if (!continuous) {
            return
        }

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

            if (continuous) {

                window.decorView.postDelayed(
                    {

                        if (continuous) {
                            recognize()
                        }

                    },
                    700
                )
            }
        }
    }

    // ============================================================
    // HALLGATÁS LEÁLLÍTÁSA
    // ============================================================

    private fun stopListening() {

        continuous = false
        novaActivated = false

        recognizer?.cancel()

        listen.text =
            "Hangvezérlés indítása"

        status.text =
            "Hangvezérlés szünetel."
    }

    // ============================================================
    // BESZÉD FELDOLGOZÁSA
    // ============================================================

    private fun handleSpeech(
        raw: String
    ) {

        val normalized =
            normalize(raw)

        if (normalized.isBlank()) {
            return
        }

        val containsNova =
            Regex("\\bnova\\b")
                .containsMatchIn(normalized)

        if (!novaActivated) {

            if (!containsNova) {

                status.text =
                    "Ébresztőszóra várok: Nova"

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

                reply(
                    "Igen? Miben segíthetek?"
                )

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

            reply("Igen?")

            return
        }

        executeCommand(command)
    }

    // ============================================================
    // PARANCS VÉGREHAJTÁSA
    // ============================================================

    private fun executeCommand(
        command: String
    ) {

        try {

            val result =
                CommandRouter.execute(
                    this,
                    command
                )

            when (result.type) {

                CommandRouter.ResultType.EXECUTED -> {

                    reply(
                        result.response
                    )
                }

                CommandRouter.ResultType.UNKNOWN -> {

                    reply(
                        result.response
                    )
                }

                CommandRouter.ResultType.AMBIGUOUS -> {

                    val options =
                        result.options.joinToString(
                            separator = " vagy "
                        )

                    if (options.isBlank()) {

                        reply(
                            result.response
                        )

                    } else {

                        reply(
                            "${result.response} $options."
                        )
                    }
                }

                CommandRouter.ResultType.CLARIFICATION -> {

                    reply(
                        result.response
                    )
                }
            }

        } catch (_: Exception) {

            status.text =
                "Hiba történt a parancs feldolgozásakor."

            speak(
                "Nem sikerült végrehajtanom a parancsot."
            )
        }
    }

    // ============================================================
    // VÁLASZ
    // ============================================================

    private fun reply(
        message: String
    ) {

        if (message.isBlank()) {
            return
        }

        status.text =
            message

        speak(message)
    }

    // ============================================================
    // DESTROY
    // ============================================================

    override fun onDestroy() {

        continuous = false

        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null

        tts?.stop()
        tts?.shutdown()
        tts = null

        super.onDestroy()
    }

    // ============================================================
    // NORMALIZÁLÁS
    // ============================================================

    companion object {

        fun normalize(
            text: String
        ): String {

            return Normalizer
                .normalize(
                    text.lowercase(
                        Locale("hu", "HU")
                    ),
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
