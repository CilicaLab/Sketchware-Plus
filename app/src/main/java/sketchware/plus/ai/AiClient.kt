package sketchware.plus.ai

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

object AiClient {

    // ------------------------------------------------------------------
    // Rotation configuration
    // ------------------------------------------------------------------

    /** Groq models, in rotation order. After the last one the rotation wraps to the first. */
    private val GROQ_MODELS = listOf(
        "openai/gpt-oss-20b",
        "openai/gpt-oss-120b",
        "qwen/qwen3.8-27b"
    )

    /** Only part of the chain when "separate Groq and Gemini settings" is on and both keys are set. */
    private const val GEMINI_MODEL = "gemini-3.8-flash"

    private const val GROQ_ENDPOINT = "https://api.groq.com/openai/v1"
    private const val GEMINI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/openai/"

    /** A rate-limit wait longer than this switches to the next model instead of waiting. */
    private const val SWITCH_THRESHOLD_MS = 10_000L

    /** Short waits (<= threshold) are retried on the same model this many times before switching. */
    private const val MAX_SAME_MODEL_RETRIES = 3

    /** If every model is cooling down, wait for the soonest one only if it is within this time. */
    private const val MAX_FULL_CYCLE_WAIT_MS = 60_000L

    /** Hard safety cap so a bad situation can never loop forever. */
    private const val MAX_TOTAL_ATTEMPTS = 15

    private const val DEFAULT_RETRY_MS = 5_000L
    private const val RETRY_BUFFER_MS = 500L
    private const val SWITCH_DELAY_MS = 500L
    private const val NOT_FOUND_COOLDOWN_MS = 5 * 60_000L

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var currentCall: Call? = null

    @Volatile
    private var isCanceledByUser = false

    /** target id -> epoch millis until which that model is considered rate limited. */
    private val cooldowns = ConcurrentHashMap<String, Long>()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private enum class Kind { GROQ, GOOGLE, OTHER }

    private data class Target(
        val kind: Kind,
        val provider: String,
        val endpoint: String,
        val model: String
    ) {
        val id: String get() = kind.name + "|" + model.lowercase()
    }

    private data class HttpResult(
        val successful: Boolean,
        val code: Int,
        val body: String,
        val retryAfterHeader: String?
    )

    private val RETRY_TEXT_PATTERN: Pattern = Pattern.compile(
        "(?:try again|retry) in\\s+((?:\\d+(?:\\.\\d+)?\\s*(?:ms|h|m|s)(?![a-zA-Z])\\s*)+)",
        Pattern.CASE_INSENSITIVE
    )
    private val DURATION_PART_PATTERN: Pattern = Pattern.compile(
        "(\\d+(?:\\.\\d+)?)\\s*(ms|h|m|s)(?![a-zA-Z])",
        Pattern.CASE_INSENSITIVE
    )
    private val RETRY_DELAY_JSON_PATTERN: Pattern = Pattern.compile(
        "\"retryDelay\"\\s*:\\s*\"([0-9.]+)s\""
    )

    init {
        System.loadLibrary("aiclient")
    }

    @JvmStatic
    fun sanitizeToolName(rawName: String?): String {
        if (rawName.isNullOrEmpty()) return ""
        var clean = rawName.replace(Regex("<\\|[a-zA-Z0-9_|-]+\\|>[a-zA-Z0-9_]*"), "")
        clean = clean.replace(Regex("<[^>]+>"), "")
        clean = clean.replace(Regex("[^a-zA-Z0-9_-]"), "").trim()
        return if (clean.isNotEmpty()) clean else rawName
    }

    @JvmStatic
    private external fun getPrefName(): String
    @JvmStatic
    private external fun getApiKeyPrefKey(): String
    @JvmStatic
    private external fun getEndpointPrefKey(): String
    @JvmStatic
    private external fun getModelPrefKey(): String
    @JvmStatic
    private external fun getTempKeyError(): String
    @JvmStatic
    private external fun getTempKeyGen(): String
    @JvmStatic
    private external fun getTempKeyAssistant(): String
    @JvmStatic
    private external fun buildRequestUrl(endpoint: String): String

    // ------------------------------------------------------------------
    // Provider / key helpers
    // ------------------------------------------------------------------

    private fun kindOf(provider: String, endpoint: String): Kind = when {
        provider.equals("groq", true) ||
                provider.equals("grog", true) ||
                endpoint.contains("groq", true) -> Kind.GROQ
        provider.equals("google", true) ||
                endpoint.contains("generativelanguage", true) -> Kind.GOOGLE
        else -> Kind.OTHER
    }

    private fun makeTarget(provider: String, endpoint: String, model: String): Target =
        Target(kindOf(provider, endpoint), provider, endpoint, model)

    /**
     * Resolves the API key. [providerOverride] lets the rotation ask for the key of a specific
     * provider without having to change the saved provider setting first.
     */
    private fun resolveApiKey(aiPref: SharedPreferences, providerOverride: String? = null): String {
        val separateActive = aiPref.getBoolean("P12_SEPARATE_API_KEYS", false)
        val defaultKey = aiPref.getString(getApiKeyPrefKey(), "") ?: ""
        if (!separateActive) {
            return defaultKey
        }
        val provider = providerOverride ?: (aiPref.getString("P12_PROVIDER", "custom") ?: "custom")
        return when {
            provider.equals("groq", true) || provider.equals("grog", true) -> {
                val groqKey = aiPref.getString("P12_GROQ_API_KEY", "")?.trim() ?: ""
                if (groqKey.isNotEmpty()) groqKey else defaultKey
            }
            provider.equals("google", true) -> {
                val geminiKey = aiPref.getString("P12_GEMINI_API_KEY", "")?.trim() ?: ""
                if (geminiKey.isNotEmpty()) geminiKey else defaultKey
            }
            else -> defaultKey
        }
    }

    @JvmStatic
    fun isAiEnabled(context: Context): Boolean {
        val aiPref = context.getSharedPreferences(getPrefName(), Context.MODE_PRIVATE)
        val apiKey = resolveApiKey(aiPref)
        val endpoint = aiPref.getString(getEndpointPrefKey(), "") ?: ""
        val model = aiPref.getString(getModelPrefKey(), "") ?: ""
        return apiKey.isNotEmpty() && endpoint.isNotEmpty() && model.isNotEmpty()
    }

    enum class AiTemperatureType(val key: String) {
        ERROR_EXPLANATION(getTempKeyError()),
        CODE_GENERATION(getTempKeyGen()),
        ASSISTANT_MODE(getTempKeyAssistant());
    }

    interface AiCallback {
        fun onSuccess(response: String)
        fun onSuccess(response: String, promptTokens: Int, completionTokens: Int, totalTokens: Int) {
            onSuccess(response)
        }
        fun onToolCall(toolCalls: JSONArray, content: String?) {}
        fun onToolCall(toolCalls: JSONArray, content: String?, promptTokens: Int, completionTokens: Int, totalTokens: Int) {
            onToolCall(toolCalls, content)
        }
        fun onError(error: String)
        fun onRetry(retryCount: Int, delayMillis: Long) {}
        fun onModelSwitched(newModel: String) {}
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    @JvmStatic
    @JvmOverloads
    fun askAi(context: Context, systemPrompt: String, userPrompt: String, temperature: Float, tools: JSONArray? = null, modelOverride: String? = null, callback: AiCallback) {
        askAi(context, systemPrompt, wrapUserPrompt(userPrompt), temperature, tools, modelOverride, callback)
    }

    @JvmStatic
    @JvmOverloads
    fun askAi(context: Context, systemPrompt: String, userPrompt: String, type: AiTemperatureType, tools: JSONArray? = null, modelOverride: String? = null, callback: AiCallback) {
        askAi(context, systemPrompt, wrapUserPrompt(userPrompt), type, tools, modelOverride, callback)
    }

    @JvmStatic
    @JvmOverloads
    fun askAi(context: Context, systemPrompt: String, chatHistory: JSONArray, type: AiTemperatureType, tools: JSONArray? = null, modelOverride: String? = null, callback: AiCallback) {
        val aiPref = context.getSharedPreferences(getPrefName(), Context.MODE_PRIVATE)
        val tempValue = aiPref.all[type.key]
        val temperature = when (tempValue) {
            is Int -> tempValue / 100f
            is String -> (tempValue.toIntOrNull() ?: 20) / 100f
            else -> 0.2f
        }
        askAi(context, systemPrompt, chatHistory, temperature, tools, modelOverride, callback)
    }

    @JvmStatic
    @JvmOverloads
    fun askAi(context: Context, systemPrompt: String, chatHistory: JSONArray, temperature: Float, tools: JSONArray? = null, modelOverride: String? = null, callback: AiCallback) {
        val aiPref = context.getSharedPreferences(getPrefName(), Context.MODE_PRIVATE)
        val apiKey = resolveApiKey(aiPref)
        val endpoint = aiPref.getString(getEndpointPrefKey(), "") ?: ""
        val model = if (!modelOverride.isNullOrEmpty()) modelOverride else (aiPref.getString(getModelPrefKey(), "") ?: "")

        if (apiKey.isEmpty() || endpoint.isEmpty() || model.isEmpty()) {
            callback.onError("Please set your API Key, Endpoint, and Model Name in System Settings")
            return
        }

        isCanceledByUser = false
        executor.execute {
            performAiRequest(context, systemPrompt, chatHistory, temperature, tools, modelOverride, callback)
        }
    }

    @JvmStatic
    fun cancelCurrentRequest() {
        isCanceledByUser = true
        currentCall?.cancel()
    }

    // ------------------------------------------------------------------
    // Request loop
    // ------------------------------------------------------------------

    private fun performAiRequest(
        context: Context,
        systemPrompt: String,
        chatHistory: JSONArray,
        temperature: Float,
        tools: JSONArray?,
        modelOverride: String?,
        callback: AiCallback
    ) {
        val aiPref = context.getSharedPreferences(getPrefName(), Context.MODE_PRIVATE)

        val configuredProvider = aiPref.getString("P12_PROVIDER", "custom") ?: "custom"
        val configuredEndpoint = aiPref.getString(getEndpointPrefKey(), "") ?: ""
        val configuredModel = if (!modelOverride.isNullOrEmpty()) modelOverride
        else (aiPref.getString(getModelPrefKey(), "") ?: "")

        var target = makeTarget(configuredProvider, configuredEndpoint, configuredModel)

        if (resolveApiKey(aiPref, target.provider).isEmpty() ||
            target.endpoint.isEmpty() ||
            target.model.isEmpty()
        ) {
            callback.onError("Please set your API Key, Endpoint, and Model Name in System Settings")
            return
        }

        // Gemini only joins the chain when separate keys are on and BOTH keys exist.
        val separateActive = aiPref.getBoolean("P12_SEPARATE_API_KEYS", false)
        val groqKey = aiPref.getString("P12_GROQ_API_KEY", "")?.trim() ?: ""
        val geminiKey = aiPref.getString("P12_GEMINI_API_KEY", "")?.trim() ?: ""
        val geminiInChain = separateActive && groqKey.isNotEmpty() && geminiKey.isNotEmpty()

        val chain = buildChain(target, geminiInChain)

        // A request that uses an explicit model override must not overwrite the user's saved model.
        val persistSwitches = modelOverride.isNullOrEmpty()

        var currentTemp = temperature
        var sameModelRetries = 0
        var retryNumber = 0
        var parseRetries = 0
        var attempts = 0

        try {
            while (true) {
                if (isCanceledByUser) {
                    callback.onError("Canceled")
                    return
                }
                attempts++

                val apiKey = resolveApiKey(aiPref, target.provider)
                val jsonBody = buildRequestBody(target.model, systemPrompt, chatHistory, currentTemp, tools)

                val request = Request.Builder()
                    .url(buildRequestUrl(target.endpoint))
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val call = client.newCall(request)
                currentCall = call

                val result = call.execute().use { response ->
                    HttpResult(
                        response.isSuccessful,
                        response.code,
                        response.body?.string() ?: "",
                        response.header("retry-after")
                    )
                }

                // ---------------- success ----------------
                if (result.successful) {
                    deliverSuccess(result.body, callback)
                    return
                }

                val code = result.code

                // ---------------- rate limit / unavailable ----------------
                if (code == 429 || code == 413 || code == 404 || code == 503) {
                    val hintedMs = parseRetryDelayMillis(result.retryAfterHeader, result.body)
                    val waitMs = hintedMs ?: DEFAULT_RETRY_MS
                    val modelGone = code == 404
                    val tooLong = modelGone || waitMs > SWITCH_THRESHOLD_MS

                    if (attempts >= MAX_TOTAL_ATTEMPTS) {
                        callback.onError("API Error ($code): ${result.body.ifEmpty { "Too many retries" }}")
                        return
                    }

                    val rotationAvailable = chain.any { it.id != target.id }
                    val shouldSwitch = rotationAvailable &&
                            (tooLong || sameModelRetries >= MAX_SAME_MODEL_RETRIES)

                    if (!shouldSwitch) {
                        // Short wait (or no other model to go to): wait and retry the same model.
                        if (!modelGone && sameModelRetries < MAX_SAME_MODEL_RETRIES) {
                            sameModelRetries++
                            retryNumber++
                            val pause = waitMs + RETRY_BUFFER_MS
                            postRetry(callback, retryNumber, pause)
                            if (!sleepInterruptibly(pause)) {
                                callback.onError("Canceled")
                                return
                            }
                            continue
                        }
                        callback.onError("API Error ($code): ${result.body.ifEmpty { "Unknown error" }}")
                        return
                    }

                    // ---- switch to the next model in the chain ----
                    val blockMs = if (modelGone) NOT_FOUND_COOLDOWN_MS else waitMs + RETRY_BUFFER_MS
                    cooldowns[target.id] = System.currentTimeMillis() + blockMs

                    var next = pickNext(chain, target)
                    var pause = SWITCH_DELAY_MS

                    if (next == null) {
                        // Every model is cooling down. Wait for the soonest one if it is close enough.
                        val soonest = soonestAvailable(chain)
                        if (soonest != null && soonest.second <= MAX_FULL_CYCLE_WAIT_MS) {
                            next = soonest.first
                            pause = soonest.second + RETRY_BUFFER_MS
                        } else {
                            val secs = (soonest?.second ?: waitMs) / 1000
                            callback.onError(
                                "All models are rate limited (HTTP $code). Try again in about ${secs}s.\n" +
                                        result.body.ifEmpty { "Unknown error" }
                            )
                            return
                        }
                    }

                    val previous = target
                    target = next
                    sameModelRetries = 0
                    retryNumber++

                    if (target.id != previous.id) {
                        if (persistSwitches) persistSwitch(aiPref, previous, target)
                        postSwitch(callback, describeSwitch(previous, target), retryNumber, pause)
                    } else {
                        postRetry(callback, retryNumber, pause)
                    }

                    if (!sleepInterruptibly(pause)) {
                        callback.onError("Canceled")
                        return
                    }
                    continue
                }

                // ---------------- 400 output_parse_failed: retry once or twice at temperature 0 ----------------
                if (code == 400 && parseRetries < 2 && result.body.contains("output_parse_failed")) {
                    parseRetries++
                    retryNumber++
                    currentTemp = 0.0f
                    postRetry(callback, retryNumber, 300)
                    if (!sleepInterruptibly(300)) {
                        callback.onError("Canceled")
                        return
                    }
                    continue
                }

                // ---------------- anything else ----------------
                callback.onError("API Error ($code): ${result.body.ifEmpty { "Unknown error" }}")
                return
            }
        } catch (e: Exception) {
            if (currentCall?.isCanceled() == true || isCanceledByUser) {
                callback.onError("Canceled")
            } else {
                callback.onError("AI Request Failed: ${e.message}")
            }
        } finally {
            currentCall = null
        }
    }

    // ------------------------------------------------------------------
    // Rotation helpers
    // ------------------------------------------------------------------

    /**
     * Builds the rotation order once per request:
     *   Groq model 1 -> Groq model 2 -> Groq model 3 -> (Gemini, if enabled) -> back to Groq model 1 ...
     * Returns an empty list when rotation does not apply (other providers, or Gemini without
     * the separate-keys setup).
     */
    private fun buildChain(current: Target, includeGemini: Boolean): List<Target> {
        if (current.kind == Kind.OTHER) return emptyList()
        if (current.kind == Kind.GOOGLE && !includeGemini) return emptyList()

        val groqProvider = if (current.kind == Kind.GROQ) current.provider else "groq"
        val groqEndpoint = if (current.kind == Kind.GROQ) current.endpoint else GROQ_ENDPOINT

        val groqTargets = GROQ_MODELS.map { makeTarget(groqProvider, groqEndpoint, it) }
        return if (includeGemini) {
            groqTargets + makeTarget("google", GEMINI_ENDPOINT, GEMINI_MODEL)
        } else {
            groqTargets
        }
    }

    /** Next model after [current] (wrapping around) that is not still cooling down, or null. */
    private fun pickNext(chain: List<Target>, current: Target): Target? {
        if (chain.isEmpty()) return null
        val now = System.currentTimeMillis()
        val idx = chain.indexOfFirst { it.id == current.id }
        val start = if (idx == -1) 0 else idx + 1
        val count = if (idx == -1) chain.size else chain.size - 1
        for (i in 0 until count) {
            val candidate = chain[(start + i) % chain.size]
            val blockedUntil = cooldowns[candidate.id] ?: 0L
            if (blockedUntil <= now) return candidate
        }
        return null
    }

    /** The chain entry that becomes available first, with the remaining wait in millis. */
    private fun soonestAvailable(chain: List<Target>): Pair<Target, Long>? {
        val now = System.currentTimeMillis()
        return chain
            .map { it to maxOf(0L, (cooldowns[it.id] ?: 0L) - now) }
            .minByOrNull { it.second }
    }

    private fun persistSwitch(aiPref: SharedPreferences, from: Target, to: Target) {
        val editor = aiPref.edit().putString(getModelPrefKey(), to.model)
        if (from.kind != to.kind) {
            editor.putString("P12_PROVIDER", if (to.kind == Kind.GOOGLE) "google" else "groq")
            editor.putString(getEndpointPrefKey(), to.endpoint)
        }
        editor.apply()
    }

    private fun describeSwitch(from: Target, to: Target): String {
        return when {
            from.kind == Kind.GROQ && to.kind == Kind.GOOGLE ->
                "${to.model} (Groq exhausted -> Gemini failover)"
            from.kind == Kind.GOOGLE && to.kind == Kind.GROQ ->
                "${to.model} (Gemini rate limited -> Groq failback)"
            else -> to.model
        }
    }

    private fun postRetry(callback: AiCallback, retryNumber: Int, delayMillis: Long) {
        mainHandler.post { callback.onRetry(retryNumber, delayMillis) }
    }

    private fun postSwitch(callback: AiCallback, label: String, retryNumber: Int, delayMillis: Long) {
        mainHandler.post {
            callback.onModelSwitched(label)
            callback.onRetry(retryNumber, delayMillis)
        }
    }

    /** Sleeps in small steps so cancel works. Returns false if canceled or interrupted. */
    private fun sleepInterruptibly(millis: Long): Boolean {
        var elapsed = 0L
        try {
            while (elapsed < millis) {
                if (isCanceledByUser) return false
                val step = minOf(100L, millis - elapsed)
                TimeUnit.MILLISECONDS.sleep(step)
                elapsed += step
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return false
        }
        return !isCanceledByUser
    }

    // ------------------------------------------------------------------
    // Retry delay parsing
    // ------------------------------------------------------------------

    /**
     * Reads how long the API wants us to wait. Understands:
     *  - the Retry-After header (seconds)
     *  - Groq style text: "try again in 5.4s", "7m12.288s", "230ms", "1h2m3s"
     *  - Gemini style: "retry in 33s" and "retryDelay": "33s"
     * If several hints are present, the longest wins. Returns null when nothing was found.
     */
    private fun parseRetryDelayMillis(headerValue: String?, errorBody: String): Long? {
        val candidates = mutableListOf<Long>()

        headerValue?.trim()?.toDoubleOrNull()?.let {
            if (it >= 0) candidates.add((it * 1000).toLong())
        }

        val textMatcher = RETRY_TEXT_PATTERN.matcher(errorBody)
        if (textMatcher.find()) {
            parseDurationMillis(textMatcher.group(1) ?: "")?.let { candidates.add(it) }
        }

        val jsonMatcher = RETRY_DELAY_JSON_PATTERN.matcher(errorBody)
        if (jsonMatcher.find()) {
            jsonMatcher.group(1)?.toDoubleOrNull()?.let { candidates.add((it * 1000).toLong()) }
        }

        return candidates.maxOrNull()
    }

    private fun parseDurationMillis(text: String): Long? {
        val m = DURATION_PART_PATTERN.matcher(text)
        var total = 0.0
        var found = false
        while (m.find()) {
            val value = m.group(1)?.toDoubleOrNull() ?: continue
            found = true
            total += when (m.group(2)?.lowercase()) {
                "ms" -> value
                "s" -> value * 1000.0
                "m" -> value * 60_000.0
                "h" -> value * 3_600_000.0
                else -> 0.0
            }
        }
        return if (found) total.toLong() else null
    }

    // ------------------------------------------------------------------
    // Request / response helpers
    // ------------------------------------------------------------------

    private fun buildRequestBody(
        model: String,
        systemPrompt: String,
        chatHistory: JSONArray,
        temperature: Float,
        tools: JSONArray?
    ): JSONObject {
        return JSONObject().apply {
            put("model", model)
            val effectiveTemp = if (tools != null && tools.length() > 0) {
                temperature.coerceAtMost(0.1f)
            } else {
                temperature
            }
            put("temperature", effectiveTemp.toDouble())
            if (tools != null && tools.length() > 0) {
                put("tools", tools)
                put("tool_choice", "auto")
            }
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                for (i in 0 until chatHistory.length()) {
                    put(chatHistory.getJSONObject(i))
                }
            }
            put("messages", messages)
        }
    }

    private fun deliverSuccess(responseData: String, callback: AiCallback) {
        val responseObject = JSONObject(responseData)
        val choice = responseObject.getJSONArray("choices").getJSONObject(0)
        val message = choice.getJSONObject("message")

        val (promptTokens, completionTokens, totalTokens) = extractTokenUsage(responseObject)

        if (message.has("tool_calls")) {
            val toolCalls = message.getJSONArray("tool_calls")
            for (i in 0 until toolCalls.length()) {
                val tc = toolCalls.getJSONObject(i)
                if (tc.has("function")) {
                    val fn = tc.getJSONObject("function")
                    val fnName = fn.optString("name", "")
                    if (fnName.isNotEmpty()) {
                        fn.put("name", sanitizeToolName(fnName))
                    }
                }
            }
            val content = if (message.has("content") && !message.isNull("content")) message.getString("content") else null
            callback.onToolCall(toolCalls, content, promptTokens, completionTokens, totalTokens)
        } else {
            val content = message.optString("content", "").trim()
            callback.onSuccess(content, promptTokens, completionTokens, totalTokens)
        }
    }

    private fun extractTokenUsage(responseObject: JSONObject): Triple<Int, Int, Int> {
        val usage = responseObject.optJSONObject("usage")
            ?: responseObject.optJSONObject("usageMetadata")
            ?: responseObject.optJSONObject("usage_metadata")

        if (usage == null) return Triple(0, 0, 0)

        val promptTokens = usage.optInt("prompt_tokens", -1).let {
            if (it != -1) it else usage.optInt("promptTokenCount", -1).let { p2 ->
                if (p2 != -1) p2 else usage.optInt("input_tokens", 0)
            }
        }

        val completionTokens = usage.optInt("completion_tokens", -1).let {
            if (it != -1) it else usage.optInt("candidatesTokenCount", -1).let { c2 ->
                if (c2 != -1) c2 else usage.optInt("completionTokenCount", -1).let { c3 ->
                    if (c3 != -1) c3 else usage.optInt("output_tokens", 0)
                }
            }
        }

        var totalTokens = usage.optInt("total_tokens", -1).let {
            if (it != -1) it else usage.optInt("totalTokenCount", -1).let { t2 ->
                if (t2 != -1) t2 else (promptTokens + completionTokens)
            }
        }

        if (totalTokens <= 0) {
            totalTokens = promptTokens + completionTokens
        }

        return Triple(promptTokens, completionTokens, totalTokens)
    }

    private fun wrapUserPrompt(userPrompt: String): JSONArray {
        return JSONArray().apply {
            try {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            } catch (ignored: Exception) {
            }
        }
    }
}