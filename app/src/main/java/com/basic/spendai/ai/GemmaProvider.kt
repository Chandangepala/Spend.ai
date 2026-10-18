package com.basic.spendai.ai

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Gemma 3 1B through LiteRT-LM, for phones without Gemini Nano. The model is a file the user
 * chose to install ([GemmaModel]). It runs inside our process, so unlike Nano it also works from
 * background workers. One engine is shared; it loads on first use (a few seconds) and is closed
 * after a quiet spell or when Android asks for memory back.
 */
class GemmaProvider(context: Context) : AiProvider {
    override val tier = AiTier.GEMMA

    private val app = context.applicationContext
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var engine: Engine? = null
    /** The backend that worked last time, so later loads skip a failing GPU attempt. */
    private var workingBackend: String? = null
    private var idleClose: Job? = null

    init {
        app.registerComponentCallbacks(object : ComponentCallbacks2 {
            @Suppress("DEPRECATION")
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) closeWhenIdle()
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = closeWhenIdle()
        })
    }

    override suspend fun status(): AiStatus =
        if (GemmaModel.isInstalled(app)) AiStatus.Available else AiStatus.Unavailable

    override suspend fun generate(prompt: String, maxTokens: Int): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            idleClose?.cancel()
            try {
                val loaded = engine ?: open() ?: return@withLock null
                val config = ConversationConfig(
                    // Low temperature, like Nano: the same SMS should give the same JSON.
                    samplerConfig = SamplerConfig(topK = 10, topP = 0.95, temperature = 0.1, seed = 0),
                    maxOutputToken = maxTokens,
                )
                loaded.createConversation(config).use { conversation ->
                    // A stuck generation would block every later AI call; stop it after a while.
                    val watchdog = scope.launch {
                        delay(GENERATE_TIMEOUT_MS)
                        Log.w(TAG, "generation took too long; cancelling")
                        conversation.cancelProcess()
                    }
                    try {
                        conversation.sendMessage(prompt).contents.contents
                            .filterIsInstance<Content.Text>()
                            .joinToString("") { it.text }
                            .takeIf { it.isNotBlank() }
                    } finally {
                        watchdog.cancel()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Native errors and OutOfMemoryError included: the caller falls back to rules.
                Log.w(TAG, "generate failed", e)
                closeEngine()
                null
            } finally {
                scheduleIdleClose()
            }
        }
    }

    /** Loads the model, trying the GPU first (faster) and then the CPU. Null if neither works. */
    private fun open(): Engine? {
        val file = GemmaModel.file(app)
        if (!GemmaModel.isInstalled(app)) return null
        val backends = listOf("gpu" to { Backend.GPU() }, "cpu" to { Backend.CPU() })
            .sortedBy { it.first != workingBackend }
        var lastError: Throwable? = null
        for ((name, backend) in backends) {
            val candidate = Engine(EngineConfig(modelPath = file.path, backend = backend(), cacheDir = app.cacheDir.path))
            try {
                candidate.initialize()
                Log.i(TAG, "Gemma loaded on $name")
                workingBackend = name
                engine = candidate
                GemmaModel.clearLoadError(app)
                return candidate
            } catch (e: Throwable) {
                Log.w(TAG, "Gemma failed to load on $name", e)
                lastError = e
                runCatching { candidate.close() }
            }
        }
        GemmaModel.reportLoadError(app, "Couldn't start the model: ${lastError?.message ?: "unknown error"}")
        return null
    }

    private fun scheduleIdleClose() {
        idleClose = scope.launch {
            delay(IDLE_CLOSE_MS)
            mutex.withLock { closeEngine() }
        }
    }

    /** Frees the loaded model now (e.g. after the file is deleted); waits for a running answer. */
    fun unload() {
        scope.launch { mutex.withLock { idleClose?.cancel(); closeEngine() } }
    }

    private fun closeWhenIdle() {
        scope.launch { if (mutex.tryLock()) try { closeEngine() } finally { mutex.unlock() } }
    }

    private fun closeEngine() {
        engine?.let { runCatching { it.close() } }
        engine = null
    }

    private companion object {
        const val TAG = "SpendAI-Gemma"
        const val IDLE_CLOSE_MS = 2 * 60_000L
        const val GENERATE_TIMEOUT_MS = 60_000L
    }
}
