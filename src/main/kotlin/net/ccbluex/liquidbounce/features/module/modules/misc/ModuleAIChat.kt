package net.ccbluex.liquidbounce.features.module.modules.misc

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.markAsError
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.network.protocol.game.ServerboundChatPacket
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit


//使用智谱的glm的api调用聊天 在聊天框使用ai+信息即可调用
object ModuleAIChat : ClientModule("AIChat", ModuleCategories.MISC) {

    val apiKey by text("APIKey", "")
    val model by text("Model", "glm-4-flash")
    val systemPrompt by text("SystemPrompt", "你是一个有帮助的AI助手，请用中文简洁回答。")
    val timeout by int("Timeout", 30, 5..120)
    val showThinking by boolean("ShowThinking", true)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(timeout.toLong(), TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val conversationHistory = mutableListOf<Message>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Suppress("unused")
    val packetHandler = handler<PacketEvent> { event ->
        val packet = event.packet
        if (packet !is ServerboundChatPacket) return@handler

        val message = packet.message
        if (!message.startsWith("AI ", ignoreCase = true)) return@handler

        event.cancelEvent()

        val userInput = message.substring(3).trim()
        if (userInput.isEmpty()) {
            chat(markAsError("§c[AIChat] 请输入内容，用法: AI <你的问题>"))
            return@handler
        }

        if (apiKey.isBlank()) {
            chat(markAsError("§c[AIChat] 请先设置智谱 API Key！在 ClickGUI -> AIChat -> APIKey 中配置"))
            return@handler
        }

        if (showThinking) {
            chat("§b[AIChat] §7思考中...")
        }

        scope.launch {
            try {
                val response = callGLMAPI(userInput)
                mc.execute {
                    displayAIResponse(response)
                }
            } catch (e: SocketTimeoutException) {
                mc.execute {
                    chat(markAsError("§c[AIChat] 请求超时，请检查网络或增加 Timeout 设置"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mc.execute {
                    chat(markAsError("§c[AIChat] 请求失败: ${e.message} (详见控制台)"))
                }
            }
        }
    }

    private suspend fun callGLMAPI(userMessage: String): String = withContext(Dispatchers.IO) {
        val messages = mutableListOf(
            Message("system", systemPrompt),
            Message("user", userMessage)
        )
        conversationHistory.takeLast(10).forEach { messages.add(it) }

        val requestBody = JsonObject().apply {
            addProperty("model", model)
            add("messages", gson.toJsonTree(messages))
            addProperty("stream", false)
            addProperty("max_tokens", 2048)
        }

        val request = Request.Builder()
            .url("https://open.bigmodel.cn/api/paas/v4/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody(jsonMediaType))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                throw Exception("HTTP ${response.code}: $errorBody")
            }

            val responseBody = response.body?.string() ?: throw Exception("Empty response")
            val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
            val choices = jsonResponse.getAsJsonArray("choices")
                ?: throw Exception("Invalid response format: no 'choices'")
            if (choices.size() == 0) throw Exception("No response from AI")

            val content = choices[0].asJsonObject
                .getAsJsonObject("message")
                .get("content").asString

            conversationHistory.add(Message("user", userMessage))
            conversationHistory.add(Message("assistant", content))
            while (conversationHistory.size > 20) conversationHistory.removeAt(0)

            content
        }
    }

    private fun displayAIResponse(message: String) {
        val maxLength = 256
        if (message.length <= maxLength) {
            chat("§b[AI] §f$message")
        } else {
            val parts = message.chunked(maxLength - 6)
            parts.forEachIndexed { index, part ->
                val prefix = if (index == 0) "§b[AI] §f" else "§b[AI] ... §f"
                chat("$prefix$part")
            }
        }
    }

    override fun onEnabled() {
        if (apiKey.isBlank()) {
            chat(markAsError("§e[AIChat] 警告: 未设置 API Key，请在模块设置中配置"))
        }
    }

    private data class Message(val role: String, val content: String)
}
