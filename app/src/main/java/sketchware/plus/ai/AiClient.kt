package sketchware.plus.ai

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.regex.Pattern
import android.os.Handler
import android.os.Looper

object AiClient {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentCall: Call? = null

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    init {
        System.loadLibrary("aiclient")
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

    @JvmStatic
    fun isAiEnabled(context: Context): Boolean {
        val aiPref = context.getSharedPreferences(getPrefName(), Context.MODE_PRIVATE)
        val apiKey = aiPref.getString(getApiKeyPrefKey(), "") ?: ""
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
        val apiKey = aiPref.getString(getApiKeyPrefKey(), "") ?: ""
        val endpoint = aiPref.getString(getEndpointPrefKey(), "") ?: ""
        val model = if (!modelOverride.isNullOrEmpty()) modelOverride else (aiPref.getString(getModelPrefKey(), "") ?: "")

        if (apiKey.isEmpty() || endpoint.isEmpty() || model.isEmpty()) {
            callback.onError("Please set your API Key, Endpoint, and Model Name in System Settings")
            return
        }

        isCanceledByUser = false
        executor.execute { performAiRequest(context, systemPrompt, chatHistory, temperature, tools, modelOverride, callback, 0) }
    }

    private fun performAiRequest(context: Context, systemPrompt: String, chatHistory: JSONArray, temperature: Float, tools: JSONArray?, modelOverride: String?, callback: AiCallback, retryCount: Int) {
        if (isCanceledByUser) {
            callback.onError("Canceled")
            return
        }

        val aiPref = context.getSharedPreferences(getPrefName(), Context.MODE_PRIVATE)
        val apiKey = aiPref.getString(getApiKeyPrefKey(), "") ?: ""
        val endpoint = aiPref.getString(getEndpointPrefKey(), "") ?: ""
        val model = if (!modelOverride.isNullOrEmpty()) modelOverride else (aiPref.getString(getModelPrefKey(), "") ?: "")

        if (apiKey.isEmpty() || endpoint.isEmpty() || model.isEmpty()) {
            callback.onError("Please set your API Key, Endpoint, and Model Name in System Settings")
            return
        }

        try {
            val jsonBody = JSONObject().apply {
                put("model", model)
                put("temperature", temperature.toDouble())
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

            val body = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(buildRequestUrl(endpoint))
                .addHeader("Authorization", "Bearer $apiKey")
                .post(body)
                .build()

            val call = client.newCall(request)
            currentCall = call
            
            call.execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    val responseData = response.body!!.string()
                    val responseObject = JSONObject(responseData)
                    val choice = responseObject.getJSONArray("choices").getJSONObject(0)
                    val message = choice.getJSONObject("message")
                    
                    val (promptTokens, completionTokens, totalTokens) = extractTokenUsage(responseObject)

                    if (message.has("tool_calls")) {
                        val content = message.optString("content", null)
                        callback.onToolCall(message.getJSONArray("tool_calls"), content, promptTokens, completionTokens, totalTokens)
                    } else {
                        val content = message.optString("content", "").trim()
                        callback.onSuccess(content, promptTokens, completionTokens, totalTokens)
                    }
                } else if (response.code == 429 && retryCount < 5) {
                    val errorBody = response.body?.string() ?: ""
                    val delayMillis = parseRetryAfter(errorBody)
                    
                    val provider = aiPref.getString("P12_PROVIDER", "custom") ?: "custom"
                    val isGroq = "groq".equals(provider, ignoreCase = true) || endpoint.contains("groq.com", ignoreCase = true)

                    if (isGroq && delayMillis > 10000) {
                        val currentModel = aiPref.getString(getModelPrefKey(), "") ?: ""
                        val groqModels = listOf("openai/gpt-oss-20b", "openai/gpt-oss-120b", "qwen/qwen3.8-27b")
                        val currentIndex = groqModels.indexOfFirst { it.equals(currentModel, ignoreCase = true) }
                        val alternateModel = if (currentIndex != -1) {
                            groqModels[(currentIndex + 1) % groqModels.size]
                        } else {
                            groqModels[0] // Start/restart at the beginning of the list (openai/gpt-oss-20b)
                        }
                        aiPref.edit().putString(getModelPrefKey(), alternateModel).apply()

                        mainHandler.post {
                            callback.onModelSwitched(alternateModel)
                            callback.onRetry(retryCount + 1, 500)
                        }

                        executor.execute {
                            if (!isCanceledByUser) {
                                performAiRequest(context, systemPrompt, chatHistory, temperature, tools, modelOverride, callback, retryCount + 1)
                            }
                        }
                    } else {
                        mainHandler.post {
                            callback.onRetry(retryCount + 1, delayMillis)
                        }

                        executor.execute {
                            try {
                                var elapsed = 0L
                                while (elapsed < delayMillis) {
                                    if (isCanceledByUser) return@execute
                                    TimeUnit.MILLISECONDS.sleep(100)
                                    elapsed += 100
                                }
                            } catch (ignored: InterruptedException) {
                            }
                            if (!isCanceledByUser) {
                                performAiRequest(context, systemPrompt, chatHistory, temperature, tools, modelOverride, callback, retryCount + 1)
                            }
                        }
                    }
                } else {
                    val errorMsg = response.body?.string() ?: "Unknown error"
                    callback.onError("API Error (${response.code}): $errorMsg")
                }
            }
        } catch (e: Exception) {
            if (currentCall?.isCanceled() == true) {
                callback.onError("Canceled")
            } else {
                callback.onError("AI Request Failed: ${e.message}")
            }
        } finally {
            currentCall = null
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

    @Volatile
    private var isCanceledByUser = false

    @JvmStatic
    fun cancelCurrentRequest() {
        isCanceledByUser = true
        currentCall?.cancel()
    }

    private fun parseRetryAfter(errorBody: String): Long {
        try {
            val m = Pattern.compile("try again in ([0-9.]+)\\s?s").matcher(errorBody)
            if (m.find()) {
                val seconds = m.group(1)
                if (seconds != null) {
                    return (seconds.toDouble() * 1000).toLong() + 500
                }
            }
        } catch (ignored: Exception) {
        }
        return 5000
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
