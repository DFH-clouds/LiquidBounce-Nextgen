package net.ccbluex.liquidbounce.features.module.modules.misc

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.mojang.blaze3d.platform.InputConstants
import kotlinx.coroutines.*
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.markAsError
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.input.InputBind
import net.ccbluex.liquidbounce.utils.input.bind
import net.ccbluex.liquidbounce.utils.input.inputByName
import net.ccbluex.liquidbounce.utils.input.unbind
import net.minecraft.network.protocol.game.ServerboundChatPacket
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit


/**
 * AIChat - 与ai聊天和控制模块开关 绑定按键 查询模块信息等
 *
 * 在聊天框使用以下前缀调用（不区分大小写都能执行）：
 * - AI <消息>     → 普通AI对话
 * - AI! <消息>    → 模块管理命令（开关模块、设置按键绑定、查询模块信息等）
 *
 * 支持的模块管理指令：
 * - "开启 KillAura" / "打开 Scaffold" / "启用 Speed"
 * - "关闭 Fly" / "禁用 NoFall"
 * - "绑定 KillAura 到 R 键" / "给 Scaffold 设置按键 G"
 * - "解绑 Fly" / "移除 Speed 的按键绑定"
 * - "列出所有模块" / "显示 Combat 分类的模块"
 * - "查询 KillAura 信息" / "Scaffold 有什么设置"
 * - "搜索 自动" / "查找 auto 相关的模块"
 */

//新用户注册智谱账号免费送Token 智谱网站：https://open.bigmodel.cn/apikey/platform
object ModuleAIChat : ClientModule("AIChat", ModuleCategories.MISC) {

    val apiKey by text("APIKey", "")
    val model by text("Model", "glm-4-flash")
    val systemPrompt by text("SystemPrompt", DEFAULT_SYSTEM_PROMPT)
    val timeout by int("Timeout", 30, 5..120)
    val showThinking by boolean("ShowThinking", true)
    val maxHistory by int("MaxHistory", 20, 5..50)
    val enableModuleManager by boolean("EnableModuleManager", true)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(timeout.toLong(), TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val conversationHistory = mutableListOf<Message>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 模块管理相关的系统提示词
    private val moduleManagerSystemPrompt = """
你是一位 LiquidBounce 客户端的智能助手，可以帮助用户管理模块（Hacks）。

你可以执行以下操作：
1. **开关模块**：开启/关闭指定模块
2. **按键绑定**：为模块设置或移除按键绑定
3. **查询信息**：查看模块列表、模块详情、按键绑定状态
4. **搜索模块**：按名称搜索模块

当你需要执行操作时，请使用工具调用（Function Calling）。

可用工具：
- `list_modules`：列出模块，可指定分类过滤
- `toggle_module`：开关指定模块
- `set_keybind`：设置模块按键绑定
- `remove_keybind`：移除模块按键绑定
- `get_module_info`：获取模块详细信息
- `search_modules`：搜索模块

模块分类：Combat, Movement, Player, Render, World, Exploit, Misc, Fun

按键名称格式：支持字母（如 R, G, X）、功能键（如 LEFT_SHIFT, RIGHT_CONTROL, SPACE, TAB）、鼠标键（如 MOUSE_3, MOUSE_4, MOUSE_5）等。
""".trimIndent()

    @Suppress("unused")
    val packetHandler = handler<PacketEvent> { event ->
        val packet = event.packet
        if (packet !is ServerboundChatPacket) return@handler

        val message = packet.message
        val isNormalAI = message.startsWith("AI ", ignoreCase = true)
        val isModuleManager = message.startsWith("AI! ", ignoreCase = true)

        if (!isNormalAI && !isModuleManager) return@handler

        event.cancelEvent()

        val userInput = if (isNormalAI) message.substring(3).trim() else message.substring(4).trim()
        if (userInput.isEmpty()) {
            chat(markAsError("§c[AIChat] 请输入内容，用法: AI <你的问题> 或 AI! <模块管理命令>"))
            return@handler
        }

        if (apiKey.isBlank()) {
            chat(markAsError("§c[AIChat] 请先设置智谱 API Key！在 ClickGUI -> AIChat -> APIKey 中配置"))
            return@handler
        }

        if (showThinking) {
            chat("§b[AIChat] §7思考中...")
        }

        val isManagerMode = isModuleManager && enableModuleManager
        scope.launch {
            try {
                val response = if (isManagerMode) {
                    handleModuleManagerRequest(userInput)
                } else {
                    callGLMAPI(userInput, false)
                }
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

    /**
     * 处理模块管理请求
     */
    private suspend fun handleModuleManagerRequest(userInput: String): String = withContext(Dispatchers.IO) {
        val tools = buildTools()
        val messages = JsonArray()
        messages.add(buildMessage("system", moduleManagerSystemPrompt))
        messages.add(buildMessage("user", userInput))

        val requestBody = JsonObject().apply {
            addProperty("model", model)
            add("messages", messages)
            add("tools", tools)
            addProperty("tool_choice", "auto")
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

            val messageObj = choices[0].asJsonObject.getAsJsonObject("message")
            val toolCalls = messageObj.getAsJsonArray("tool_calls")

            return@use if (toolCalls != null && toolCalls.size() > 0) {
                // 执行工具调用
                val results = executeToolCalls(toolCalls)
                // 将结果返回给 AI 进行总结
                val followUpMessages = JsonArray()
                followUpMessages.add(buildMessage("system", moduleManagerSystemPrompt))
                followUpMessages.add(buildMessage("user", userInput))
                followUpMessages.add(messageObj)
                results.forEach { result ->
                    followUpMessages.add(buildMessage("tool", result))
                }
                callGLMAPIWithMessages(followUpMessages)
            } else {
                // 直接返回 AI 的文本回复
                messageObj.get("content")?.asString ?: "AI 未返回有效内容"
            }
        }
    }

    /**
     * 定义构建工具
     */
    private fun buildTools(): JsonArray {
        val tools = JsonArray()

        // list_modules
        val listModulesParams = JsonObject()
        listModulesParams.addProperty("type", "object")
        val listModulesProps = JsonObject()
        val categoryProp = JsonObject()
        categoryProp.addProperty("type", "string")
        categoryProp.addProperty("description", "模块分类，可选值: Combat, Movement, Player, Render, World, Exploit, Misc, Fun。为空则列出所有模块")
        listModulesProps.add("category", categoryProp)
        val showDetailsProp = JsonObject()
        showDetailsProp.addProperty("type", "boolean")
        showDetailsProp.addProperty("description", "是否显示详细信息（状态、按键绑定等）")
        listModulesProps.add("show_details", showDetailsProp)
        listModulesParams.add("properties", listModulesProps)
        tools.add(buildTool("list_modules", "列出 LiquidBounce 的模块，可按分类过滤", listModulesParams))

        // toggle_module
        val toggleParams = JsonObject()
        toggleParams.addProperty("type", "object")
        val toggleProps = JsonObject()
        val toggleNameProp = JsonObject()
        toggleNameProp.addProperty("type", "string")
        toggleNameProp.addProperty("description", "模块名称")
        toggleProps.add("module_name", toggleNameProp)
        val toggleEnableProp = JsonObject()
        toggleEnableProp.addProperty("type", "boolean")
        toggleEnableProp.addProperty("description", "true=开启, false=关闭。不提供则自动切换当前状态")
        toggleProps.add("enable", toggleEnableProp)
        toggleParams.add("properties", toggleProps)
        val toggleRequired = JsonArray()
        toggleRequired.add("module_name")
        toggleParams.add("required", toggleRequired)
        tools.add(buildTool("toggle_module", "开启或关闭指定模块", toggleParams))

        // set_keybind
        val setBindParams = JsonObject()
        setBindParams.addProperty("type", "object")
        val setBindProps = JsonObject()
        val setBindNameProp = JsonObject()
        setBindNameProp.addProperty("type", "string")
        setBindNameProp.addProperty("description", "模块名称")
        setBindProps.add("module_name", setBindNameProp)
        val setBindKeyProp = JsonObject()
        setBindKeyProp.addProperty("type", "string")
        setBindKeyProp.addProperty("description", "按键名称，如 R, G, LEFT_SHIFT, RIGHT_CONTROL, SPACE, MOUSE_4 等")
        setBindProps.add("key", setBindKeyProp)
        val setBindActionProp = JsonObject()
        setBindActionProp.addProperty("type", "string")
        setBindActionProp.addProperty("description", "绑定动作: Toggle(切换), Hold(按住), Smart(智能)。默认 Toggle")
        setBindProps.add("action", setBindActionProp)
        setBindParams.add("properties", setBindProps)
        val setBindRequired = JsonArray()
        setBindRequired.add("module_name")
        setBindRequired.add("key")
        setBindParams.add("required", setBindRequired)
        tools.add(buildTool("set_keybind", "为模块设置按键绑定", setBindParams))

        // remove_keybind
        val removeBindParams = JsonObject()
        removeBindParams.addProperty("type", "object")
        val removeBindProps = JsonObject()
        val removeBindNameProp = JsonObject()
        removeBindNameProp.addProperty("type", "string")
        removeBindNameProp.addProperty("description", "模块名称")
        removeBindProps.add("module_name", removeBindNameProp)
        removeBindParams.add("properties", removeBindProps)
        val removeBindRequired = JsonArray()
        removeBindRequired.add("module_name")
        removeBindParams.add("required", removeBindRequired)
        tools.add(buildTool("remove_keybind", "移除模块的按键绑定", removeBindParams))

        // get_module_info
        val infoParams = JsonObject()
        infoParams.addProperty("type", "object")
        val infoProps = JsonObject()
        val infoNameProp = JsonObject()
        infoNameProp.addProperty("type", "string")
        infoNameProp.addProperty("description", "模块名称")
        infoProps.add("module_name", infoNameProp)
        infoParams.add("properties", infoProps)
        val infoRequired = JsonArray()
        infoRequired.add("module_name")
        infoParams.add("required", infoRequired)
        tools.add(buildTool("get_module_info", "获取模块的详细信息，包括设置项、当前状态、按键绑定等", infoParams))

        // search_modules
        val searchParams = JsonObject()
        searchParams.addProperty("type", "object")
        val searchProps = JsonObject()
        val searchQueryProp = JsonObject()
        searchQueryProp.addProperty("type", "string")
        searchQueryProp.addProperty("description", "搜索关键词")
        searchProps.add("query", searchQueryProp)
        searchParams.add("properties", searchProps)
        val searchRequired = JsonArray()
        searchRequired.add("query")
        searchParams.add("required", searchRequired)
        tools.add(buildTool("search_modules", "按名称搜索模块", searchParams))

        return tools
    }

    /**
     * 调用执行工具
     */
    private fun executeToolCalls(toolCalls: JsonArray): List<String> {
        val results = mutableListOf<String>()

        for (toolCall in toolCalls) {
            val toolCallObj = toolCall.asJsonObject
            val function = toolCallObj.getAsJsonObject("function")
            val name = function.get("name").asString
            val arguments = gson.fromJson(function.get("arguments").asString, JsonObject::class.java)

            val result = when (name) {
                "list_modules" -> {
                    val category = arguments.get("category")?.asString
                    val showDetails = arguments.get("show_details")?.asBoolean ?: false
                    executeListModules(category, showDetails)
                }
                "toggle_module" -> {
                    val moduleName = arguments.get("module_name").asString
                    val enable = arguments.get("enable")?.asBoolean
                    executeToggleModule(moduleName, enable)
                }
                "set_keybind" -> {
                    val moduleName = arguments.get("module_name").asString
                    val key = arguments.get("key").asString
                    val action = arguments.get("action")?.asString ?: "Toggle"
                    executeSetKeybind(moduleName, key, action)
                }
                "remove_keybind" -> {
                    val moduleName = arguments.get("module_name").asString
                    executeRemoveKeybind(moduleName)
                }
                "get_module_info" -> {
                    val moduleName = arguments.get("module_name").asString
                    executeGetModuleInfo(moduleName)
                }
                "search_modules" -> {
                    val query = arguments.get("query").asString
                    executeSearchModules(query)
                }
                else -> "未知工具: $name"
            }
            results.add(result)
        }

        return results
    }


    private fun executeListModules(category: String?, showDetails: Boolean): String {
        val modules = if (category != null) {
            val cat = ModuleCategories.entries.find { it.tag.equals(category, true) }
                ?: return "错误: 未找到分类 '$category'。可用分类: " + ModuleCategories.entries.joinToString { it.tag }
            ModuleManager.filter { it.category == cat }
        } else {
            ModuleManager.toList()
        }

        if (modules.isEmpty()) {
            return "未找到任何模块"
        }

        val sb = StringBuilder()
        val header = "找到 ${modules.size} 个模块" + (category?.let { " (分类: $it)" } ?: "") + ":"
        sb.appendLine(header)

        modules.sortedBy { it.name }.forEach { module ->
            if (showDetails) {
                val status = if (module.enabled) "[开]" else "[关]"
                val bindStr = if (module.bind.isUnbound) "无绑定" else module.bind.keyName
                sb.appendLine("- ${module.name} $status (${module.category.tag}) [按键: $bindStr]")
            } else {
                val status = if (module.enabled) "●" else "●"
                sb.appendLine("$status ${module.name}")
            }
        }

        return sb.toString().trim()
    }

    private fun executeToggleModule(moduleName: String, enable: Boolean?): String {
        val module = ModuleManager[moduleName]
            ?: return "错误: 未找到模块 '$moduleName'。请使用 AI! 搜索模块 来查找正确名称。"

        if (module.disableActivation) {
            return "错误: 模块 '${module.name}' 无法被手动开关。"
        }

        val targetState = enable ?: !module.enabled
        module.enabled = targetState

        val stateStr = if (targetState) "已开启" else "已关闭"
        return "模块 '${module.name}' $stateStr"
    }

    private fun executeSetKeybind(moduleName: String, key: String, action: String): String {
        val module = ModuleManager[moduleName]
            ?: return "错误: 未找到模块 '$moduleName'"

        if (module.notActivatable) {
            return "错误: 模块 '${module.name}' 不支持按键绑定。"
        }

        val bindAction = when (action.lowercase()) {
            "hold" -> InputBind.BindAction.HOLD
            "smart" -> InputBind.BindAction.SMART
            else -> InputBind.BindAction.TOGGLE
        }

        return try {
            val inputKey = inputByName(key)
            if (inputKey == InputConstants.UNKNOWN) {
                return "错误: 无法识别按键 '$key'。请使用有效的按键名称，如 R, G, LEFT_SHIFT, SPACE, MOUSE_4 等。"
            }

            module.bindValue.bind(inputKey, bindAction, emptySet())
            "已为模块 '${module.name}' 设置按键绑定: ${inputKey.name} (${bindAction.tag})"
        } catch (e: Exception) {
            "错误: 设置按键绑定失败 - ${e.message}"
        }
    }

    private fun executeRemoveKeybind(moduleName: String): String {
        val module = ModuleManager[moduleName]
            ?: return "错误: 未找到模块 '$moduleName'"

        if (module.bind.isUnbound) {
            return "模块 '${module.name}' 当前没有按键绑定。"
        }

        module.bindValue.unbind()
        return "已移除模块 '${module.name}' 的按键绑定"
    }

    private fun executeGetModuleInfo(moduleName: String): String {
        val module = ModuleManager[moduleName]
            ?: return "错误: 未找到模块 '$moduleName'"

        val sb = StringBuilder()
        sb.appendLine("=== 模块信息: ${module.name} ===")
        sb.appendLine("分类: ${module.category.tag}")
        sb.appendLine("状态: ${if (module.enabled) "已启用" else "已禁用"}")
        sb.appendLine("运行中: ${if (module.running) "是" else "否"}")
        sb.appendLine("隐藏: ${if (module.hidden) "是" else "否"}")
        sb.appendLine("按键绑定: ${if (module.bind.isUnbound) "无" else "${module.bind.keyName} (${module.bind.action.tag})"}")
        module.tag?.let { sb.appendLine("标签: $it") }

        val moduleSettings = module.settings
        if (moduleSettings.isNotEmpty()) {
            sb.appendLine("")
            sb.appendLine("设置项:")
            moduleSettings.values.forEach { value ->
                val valueStr = when (val v = value.get()) {
                    is InputConstants.Key -> if (v == InputConstants.UNKNOWN) "None" else v.name
                    else -> v.toString()
                }
                sb.appendLine("  - ${value.name}: $valueStr")
            }
        }

        return sb.toString().trim()
    }

    private fun executeSearchModules(query: String): String {
        val modules = ModuleManager.filter {
            it.name.contains(query, ignoreCase = true)
        }.sortedBy { it.name }

        if (modules.isEmpty()) {
            return "未找到包含 '$query' 的模块。"
        }

        val sb = StringBuilder()
        sb.appendLine("搜索 '$query' 找到 ${modules.size} 个模块:")
        modules.forEach { module ->
            val status = if (module.enabled) "[开]" else "[关]"
            sb.appendLine("- ${module.name} $status (${module.category.tag})")
        }

        return sb.toString().trim()
    }

    // 调用智谱 glm模型的api实现

    private suspend fun callGLMAPI(userMessage: String, addToHistory: Boolean = true): String = withContext(Dispatchers.IO) {
        val messages = mutableListOf(
            Message("system", systemPrompt),
            Message("user", userMessage)
        )
        conversationHistory.takeLast(maxHistory - 2).forEach { messages.add(it) }

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

            if (addToHistory) {
                conversationHistory.add(Message("user", userMessage))
                conversationHistory.add(Message("assistant", content))
                while (conversationHistory.size > maxHistory) conversationHistory.removeAt(0)
            }

            content
        }
    }

    private suspend fun callGLMAPIWithMessages(messages: JsonArray): String = withContext(Dispatchers.IO) {
        val requestBody = JsonObject().apply {
            addProperty("model", model)
            add("messages", messages)
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

            choices[0].asJsonObject
                .getAsJsonObject("message")
                .get("content").asString
        }
    }


    private fun buildMessage(role: String, content: String): JsonObject {
        return JsonObject().apply {
            addProperty("role", role)
            addProperty("content", content)
        }
    }

    private fun buildTool(name: String, description: String, parameters: JsonObject): JsonObject {
        return JsonObject().apply {
            addProperty("type", "function")
            add("function", JsonObject().apply {
                addProperty("name", name)
                addProperty("description", description)
                add("parameters", parameters)
            })
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

    // 放常量的地方

    private const val DEFAULT_SYSTEM_PROMPT = "你是一个有帮助的AI助手，请用中文简洁回答。"

    private data class Message(val role: String, val content: String)
}
