package hu.novamobile

import android.Manifest
import android.content.*
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

class MainActivity : android.app.Activity(), TextToSpeech.OnInitListener {
    private lateinit var status: TextView; private lateinit var transcript: TextView; private lateinit var listen: Button
    private var recognizer: SpeechRecognizer? = null; private var tts: TextToSpeech? = null; private var continuous = false
    private val requestAudio = 100

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContentView(R.layout.activity_main)
        status = findViewById(R.id.statusText); transcript = findViewById(R.id.transcriptText); listen = findViewById(R.id.listenButton)
        tts = TextToSpeech(this, this)
        listen.setOnClickListener { if (continuous) stopListening() else ensurePermissionAndStart() }
        findViewById<Button>(R.id.settingsButton).setOnClickListener { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName"))) }
        if (android.os.Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
    }
    override fun onInit(result: Int) { if (result == TextToSpeech.SUCCESS) { tts?.language = Locale("hu", "HU") } }
    private fun ensurePermissionAndStart() { if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startListening() else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), requestAudio) }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grants: IntArray) { super.onRequestPermissionsResult(requestCode, permissions, grants); if (requestCode == requestAudio && grants.firstOrNull() == PackageManager.PERMISSION_GRANTED) startListening() else if (requestCode == requestAudio) { status.text = "A mikrofonengedély szükséges a hangvezérléshez."; speak("Kérlek engedélyezd a mikrofon használatát.") } }
    private fun startListening() { if (!SpeechRecognizer.isRecognitionAvailable(this)) { status.text="A beszédfelismerés nem érhető el ezen a készüléken."; return }; continuous=true; listen.text="Hangvezérlés leállítása"; status.text="Figyelek… Mondd: Nova"; createRecognizer(); recognize() }
    private fun createRecognizer() { recognizer?.destroy(); recognizer=SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(object: RecognitionListener {
        override fun onReadyForSpeech(p: Bundle?) { status.text="Figyelek… Mondd: Nova" }; override fun onBeginningOfSpeech() { status.text="Hallgatlak…" }
        override fun onRmsChanged(v: Float) {}; override fun onBufferReceived(b: ByteArray?) {}; override fun onEndOfSpeech() {}
        override fun onError(e: Int) { if (continuous) window.decorView.postDelayed({ recognize() }, 500) }
        override fun onResults(r: Bundle?) { val heard=r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty(); transcript.text=heard; handleSpeech(heard); if(continuous) window.decorView.postDelayed({recognize()}, 900) }
        override fun onPartialResults(p: Bundle?) { val s=p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull(); if(!s.isNullOrBlank()) transcript.text=s }; override fun onEvent(t:Int,p:Bundle?) {}
    }) }
    private fun recognize() { if (!continuous) return; val i=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"hu-HU").putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true); try { recognizer?.startListening(i) } catch(_:Exception){} }
    private fun stopListening() { continuous=false; recognizer?.cancel(); listen.text="Hangvezérlés indítása"; status.text="Hangvezérlés szünetel." }
    private fun handleSpeech(raw:String) { val normalized=normalize(raw); val command=normalized.replace(Regex("^nova[ ,.!?]*"), "").trim(); if (!normalized.contains("nova")) { status.text="Ébresztőszóra várok: Nova"; return }; if(command.isBlank()) { reply("Igen? Miben segíthetek?"); return }; val response=CommandRouter.execute(this, command); reply(response) }
    private fun reply(message:String) { status.text=message; speak(message) }; private fun speak(text:String) { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nova-response") }
    override fun onDestroy() { recognizer?.destroy(); tts?.shutdown(); super.onDestroy() }
    companion object { fun normalize(text:String):String = Normalizer.normalize(text.lowercase(Locale("hu","HU")), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim() }
}
