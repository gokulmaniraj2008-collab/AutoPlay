package com.gokul.autoplay

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Single source of truth for Tommy speech recognition.
 * Chat mic and the floating Tommy service must use this controller instead
 * of creating their own SpeechRecognizer instances.
 */
object TommyVoiceController {
    enum class Source { MIC, WAKE_WORD, BUBBLE }

    enum class State { IDLE, LISTENING, PROCESSING, ERROR }

    interface Listener {
        fun onStateChanged(state: State, message: String)
        fun onPartialText(text: String)
        fun onFinalText(text: String, source: Source)
    }

    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var currentSource = Source.MIC
    private val listeners = linkedSetOf<Listener>()

    @Synchronized
    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    @Synchronized
    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    @Synchronized
    fun isListening(): Boolean = listening

    @Synchronized
    fun start(context: Context, source: Source): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            notifyState(State.ERROR, "Speech recognition is unavailable on this phone")
            return false
        }
        if (listening) return false

        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).also { speech ->
                speech.setRecognitionListener(recognitionListener)
            }
        }

        currentSource = source
        listening = true
        notifyState(State.LISTENING, "Tommy is listening…")

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
        }

        return try {
            recognizer?.startListening(intent)
            true
        } catch (_: Exception) {
            listening = false
            notifyState(State.ERROR, "Voice input could not start")
            false
        }
    }

    @Synchronized
    fun stop() {
        if (!listening) return
        listening = false
        try { recognizer?.stopListening() } catch (_: Exception) { }
        notifyState(State.PROCESSING, "Processing…")
    }

    @Synchronized
    fun cancel() {
        listening = false
        try { recognizer?.cancel() } catch (_: Exception) { }
        notifyState(State.IDLE, "Tommy is ready")
    }

    @Synchronized
    fun release() {
        listening = false
        try { recognizer?.cancel() } catch (_: Exception) { }
        try { recognizer?.destroy() } catch (_: Exception) { }
        recognizer = null
        listeners.clear()
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            notifyState(State.LISTENING, "Tommy is listening…")
        }

        override fun onBeginningOfSpeech() {
            notifyState(State.LISTENING, "Tommy is listening…")
        }

        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            listening = false
            notifyState(State.PROCESSING, "Processing…")
        }

        override fun onError(error: Int) {
            listening = false
            val message = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that — try again"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Tommy voice is already busy"
                else -> "Voice input stopped — try again"
            }
            notifyState(State.ERROR, message)
        }

        override fun onResults(results: Bundle?) {
            listening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val text = matches.firstOrNull()?.trim().orEmpty()
            if (text.isBlank()) {
                notifyState(State.ERROR, "No speech detected")
            } else {
                notifyState(State.PROCESSING, "Tommy heard: $text")
                notifyFinalText(text, currentSource)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.trim().orEmpty()
            if (text.isNotBlank()) notifyPartialText(text)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun notifyState(state: State, message: String) {
        val snapshot = synchronized(this) { listeners.toList() }
        snapshot.forEach { it.onStateChanged(state, message) }
    }

    private fun notifyPartialText(text: String) {
        val snapshot = synchronized(this) { listeners.toList() }
        snapshot.forEach { it.onPartialText(text) }
    }

    private fun notifyFinalText(text: String, source: Source) {
        val snapshot = synchronized(this) { listeners.toList() }
        snapshot.forEach { it.onFinalText(text, source) }
    }
}
