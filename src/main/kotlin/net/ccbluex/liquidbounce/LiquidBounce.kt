/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce

import com.mojang.blaze3d.systems.RenderSystem
import kotlinx.coroutines.*
import kotlinx.coroutines.future.future
import net.ccbluex.liquidbounce.LiquidBounce.Client.commit
import net.ccbluex.liquidbounce.api.core.ApiConfig
import net.ccbluex.liquidbounce.api.core.ioScope
import net.ccbluex.liquidbounce.api.models.auth.ClientAccount
import net.ccbluex.liquidbounce.api.services.client.ClientUpdate
import net.ccbluex.liquidbounce.api.thirdparty.IpInfoApi
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.config.autoconfig.AutoConfig
import net.ccbluex.liquidbounce.config.types.Config
import net.ccbluex.liquidbounce.deeplearn.DeepLearningEngine
import net.ccbluex.liquidbounce.deeplearn.ModelManager
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.ClientShutdownEvent
import net.ccbluex.liquidbounce.event.events.ClientStartEvent
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.events.ScreenEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.account.AccountManager
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.command.CommandManager
import net.ccbluex.liquidbounce.features.cosmetic.ClientAccountManager
import net.ccbluex.liquidbounce.features.cosmetic.CosmeticService
import net.ccbluex.liquidbounce.features.creativetab.tabs.HeadsCreativeModeTab
import net.ccbluex.liquidbounce.features.global.GlobalManager
import net.ccbluex.liquidbounce.features.marketplace.MarketplaceManager
import net.ccbluex.liquidbounce.features.misc.FriendManager
import net.ccbluex.liquidbounce.features.misc.proxy.ProxyManager
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.ccbluex.liquidbounce.features.spoofer.SpooferManager
import net.ccbluex.liquidbounce.integration.backend.BrowserBackendManager
import net.ccbluex.liquidbounce.integration.interop.ClientInteropServer
import net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.game.ActiveServerList
import net.ccbluex.liquidbounce.integration.screen.ScreenManager
import net.ccbluex.liquidbounce.integration.task.TaskManager
import net.ccbluex.liquidbounce.integration.task.TaskProgressScreen
import net.ccbluex.liquidbounce.integration.theme.ThemeManager
import net.ccbluex.liquidbounce.lang.LanguageManager
import net.ccbluex.liquidbounce.render.FontManager
import net.ccbluex.liquidbounce.render.HAS_AMD_VEGA_APU
import net.ccbluex.liquidbounce.render.engine.BlurEffectRenderer
import net.ccbluex.liquidbounce.render.gui.ItemImageAtlas
import net.ccbluex.liquidbounce.script.ScriptManager
import net.ccbluex.liquidbounce.utils.aiming.PostRotationExecutor
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.block.ChunkScanner
import net.ccbluex.liquidbounce.utils.client.InteractionTracker
import net.ccbluex.liquidbounce.utils.client.ServerObserver
import net.ccbluex.liquidbounce.utils.client.clientIdentifier
import net.ccbluex.liquidbounce.utils.client.error.ErrorHandler
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.combat.CombatManager
import net.ccbluex.liquidbounce.utils.entity.RenderedEntities
import net.ccbluex.liquidbounce.utils.input.InputTracker
import net.ccbluex.liquidbounce.utils.inventory.EnderChestInventoryTracker
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.FIRST_PRIORITY
import net.ccbluex.liquidbounce.utils.kotlin.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.packs.resources.ReloadableResourceManager
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.InputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import kotlin.time.measureTime
import java.net.NetworkInterface
import java.net.URL
import java.net.HttpURLConnection
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID
import java.io.IOException
import kotlinx.coroutines.TimeoutCancellationException

//Fix By 花辞树  删这行的死全家
//添加了UID显示和检测 2026/8/23 原理：通过查询HWID所在的行数 来生成uid 例如HWID在第一行 则为001以此类推
//添加了Version Check 以及version显示 26/9/27
//修改为验证失败弹窗，支持复制 HWID 和 B站链接 26/11/5

//验证错误类型
enum class VerificationType { VERSION, HWID }

// 自定义验证异常
class ClientVerificationException(
    message: String,
    val type: VerificationType,
    val jumpUrl: String? = null,
    val hwid: String? = null
) : Exception(message)

object LiquidBounce : EventListener {

    const val CLIENT_NAME = "LiquidBounce"
    const val CLIENT_AUTHOR = "CCBlueX"
    val version1 = "10.0"

    private object Client : Config("Client") {
        val version = text("Version", version1)
            .immutable()
        val commit: String
            get() = "DEV BUILD"
        val branch = text("***", "FUCK CCBLueX").immutable()

        init {
            ConfigSystem.root(this)

            version.onChange { previousVersion ->
                runCatching {
                    ConfigSystem.backup("automatic_${previousVersion}-${version.inner}")
                }.onFailure {
                    logger.error("Unable to create backup", it)
                }
                previousVersion
            }
        }
    }

    val clientVersion by Client.version
    val clientCommit get() = commit
    val clientBranch by Client.branch
    val clientuid get() = clientUid

    const val IN_DEVELOPMENT = false

    val logger get() = net.ccbluex.liquidbounce.utils.client.logger

    var taskManager: TaskManager? = null
    var isInitialized = false
        private set

    var clientUid: String? = null
        internal set

    private var verificationErrorScreen: CustomVerificationErrorScreen? = null

    @JvmStatic
    fun identifier(path: String): Identifier = clientIdentifier(path)

    @JvmStatic
    fun resource(path: String): InputStream =
        LiquidBounce::class.java.getResourceAsStream("/resources/liquidbounce/$path")
            ?: throw IllegalArgumentException("Resource $path not found")

    @JvmStatic
    fun resourceToString(path: String): String =
        resource(path).use { it.bufferedReader().readText() }

    private fun getHWID(): String {
        val hwidFile = Paths.get("hwid.dat")
        if (Files.exists(hwidFile)) {
            return Files.readAllLines(hwidFile).firstOrNull() ?: generateAndStoreHWID()
        }
        return generateAndStoreHWID()
    }

    private fun generateAndStoreHWID(): String {
        val hwidFile = Paths.get("hwid.dat")
        val hwid = try {
            NetworkInterface.networkInterfaces().toList()
                .firstOrNull { it.isUp && !it.isLoopback && it.hardwareAddress != null }
                ?.hardwareAddress
                ?.joinToString("") { "%02x".format(it) }
                ?: UUID.randomUUID().toString()
        } catch (_: Exception) {
            UUID.randomUUID().toString()
        }
        Files.write(hwidFile, listOf(hwid))
        return hwid
    }

    private const val HWID_LIST_URL = "https://gitee.com/Huacishu1/liquid-bounce-nextgen-hwid/raw/master/HWID"
    private const val VERSION_CHECK_URL = "https://gitee.com/Huacishu1/liquid-bounce-nextgen-hwid/raw/master/version"

    private suspend fun verifyClient() {
        if (IN_DEVELOPMENT || System.getProperty("liquidbounce.skipVerification") == "true") {
            logger.warn("HWID verification is disabled (development mode).")
            return
        }

        withContext(Dispatchers.IO) {
            var lastException: Exception? = null
            repeat(3) { attempt ->
                try {
                    val url = URL(HWID_LIST_URL)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    connection.setRequestProperty("User-Agent", "$CLIENT_NAME/$clientVersion")

                    val responseCode = connection.responseCode
                    if (responseCode != 200) {
                        throw IOException("Failed to fetch HWID list, HTTP $responseCode")
                    }

                    val content = connection.inputStream.bufferedReader().use { it.readText() }
                    val hwidList = content.lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toList()

                    if (hwidList.isEmpty()) {
                        throw IllegalStateException("HWID list is empty")
                    }

                    val currentHWID = getHWID()
                    val index = hwidList.indexOf(currentHWID)

                    if (index == -1) {
                        throw ClientVerificationException(
                            "您的设备未授权！\n(HWID不在白名单内)\n请联系作者上HWID\nB站账号：我是花辞树",
                            VerificationType.HWID,
                            "https://space.bilibili.com/1124549701",
                            currentHWID
                        )
                    } else {
                        val lineNumber = index + 1
                        clientUid = String.format("%03d", lineNumber)
                        logger.info("HWID verification passed. (Line $lineNumber, UID: ${clientUid})")
                        return@withContext
                    }
                } catch (e: Exception) {
                    lastException = e
                    logger.warn("HWID verification attempt ${attempt+1}/3 failed: ${e.message}")
                    if (attempt < 2) delay(2000)
                }
            }

            logger.error("HWID verification failed after 3 attempts", lastException)
            if (lastException is ClientVerificationException) {
                throw lastException
            } else {
                throw ClientVerificationException(
                    "HWID验证失败，请检查网络连接！\n详情: ${lastException?.message}",
                    VerificationType.HWID,
                    "https://space.bilibili.com/1124549701",
                    getHWID() // 网络失败时也尝试显示本地 HWID
                )
            }
        }
    }

    private suspend fun checkVersion() {
        if (IN_DEVELOPMENT || System.getProperty("liquidbounce.skipVerification") == "true") {
            logger.warn("Version verification is disabled (development mode).")
            return
        }

        withContext(Dispatchers.IO) {
            var lastException: Exception? = null
            repeat(3) { attempt ->
                try {
                    val url = URL(VERSION_CHECK_URL)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    connection.setRequestProperty("User-Agent", "$CLIENT_NAME/$clientVersion")

                    val responseCode = connection.responseCode
                    if (responseCode != 200) {
                        throw IOException("Failed to fetch version, HTTP $responseCode")
                    }

                    val latestVersion = connection.inputStream.bufferedReader().use { it.readText().trim() }

                    if (latestVersion.isEmpty()) {
                        throw IllegalStateException("Version content is empty")
                    }

                    if (latestVersion != version1) {
                        logger.error("Version mismatch! Client version: $version1, Remote version: $latestVersion.")
                        throw ClientVerificationException(
                            "客户端版本与服务器不一致！\n请前往 GitHub 获取最新版本。\n(当前: $version1, 最新: $latestVersion)",
                            VerificationType.VERSION,
                            "https://github.com/DFH-clouds/LiquidBounce-Nextgen/releases"
                        )
                    }

                    logger.info("Version verification passed. (Client: $version1, Remote: $latestVersion)")
                    return@withContext
                } catch (e: Exception) {
                    lastException = e
                    logger.warn("Version verification attempt ${attempt + 1}/3 failed: ${e.message}")
                    if (attempt < 2) delay(2000)
                }
            }

            logger.error("Version verification failed after 3 attempts", lastException)
            if (lastException is ClientVerificationException) {
                throw lastException
            } else {
                throw ClientVerificationException(
                    "版本验证失败，请检查网络连接！\n详情: ${lastException?.message}",
                    VerificationType.VERSION,
                    "https://github.com/DFH-clouds/LiquidBounce-Nextgen/releases"
                )
            }
        }
    }

    private fun initializeClient(
        workerDispatcher: CoroutineDispatcher,
        renderThreadDispatcher: CoroutineDispatcher,
    ): CompletableFuture<Unit> = CoroutineScope(
        renderThreadDispatcher + CoroutineName("$CLIENT_NAME Initializer")
    ).future {
        if (isInitialized) return@future

        RenderSystem.assertOnRenderThread()

        Client
        try {
            initializeManagers(workerDispatcher, renderThreadDispatcher)
            initializeFeatures()
            initializeResources(workerDispatcher)

            prepareGuiStage(renderThreadDispatcher)
        } catch (e: Exception) {
            logger.error("Client initialization failed: ${e.message}", e)

            if (e is ClientVerificationException) {
                val title = Component.literal("客户端验证失败")
                val reason = Component.literal("${e.message}\n请点击下方按钮复制对应信息。")

                val screen = CustomVerificationErrorScreen(title, reason, e.jumpUrl, e.hwid)
                verificationErrorScreen = screen

                mc.execute {
                    mc.setScreen(screen)
                }

                return@future
            }

            Runtime.getRuntime().halt(1)
            throw e
        }

        Runtime.getRuntime().addShutdownHook(Thread(::shutdownClient))

        if (HAS_AMD_VEGA_APU) {
            logger.info("AMD Vega iGPU detected, enabling different line smooth handling.")
        }

        if (!ConfigSystem.isFirstLaunch && !Client.jsonFile.exists()) {
            runCatching { ConfigSystem.backup("automatic_${Client.version.inner}") }
                .onFailure { logger.error("Unable to create backup", it) }
        }

        ConfigSystem.loadAll()
        isInitialized = true
        logger.info("$CLIENT_NAME has been successfully initialized.")
    }.exceptionally { throwable ->
        ErrorHandler.fatal(throwable, additionalMessage = "$CLIENT_NAME initializer")
    }

    private suspend fun initializeManagers(
        workerDispatcher: CoroutineDispatcher,
        renderThreadDispatcher: CoroutineDispatcher,
    ) = withContext(renderThreadDispatcher) {
        val scriptEngineJob = launch(workerDispatcher) {
            runCatching(ScriptManager::initializeEngine).onFailure { error ->
                logger.error("[ScriptAPI] Failed to initialize script engine.", error)
            }
        }
        ConfigSystem
        RenderedEntities
        ChunkScanner
        InputTracker
        ModuleManager
        CommandManager
        ProxyManager
        AccountManager
        RotationManager
        BlinkManager
        InteractionTracker
        CombatManager
        FriendManager
        InventoryManager
        EnderChestInventoryTracker
        ActiveServerList
        ConfigSystem.root(ClientAccountManager)
        ConfigSystem.root(SpooferManager)
        ConfigSystem.root(GlobalManager)
        ConfigSystem.root(MarketplaceManager)
        PostRotationExecutor
        ServerObserver
        ItemImageAtlas
        scriptEngineJob.join()
    }

    private fun initializeFeatures() {
        CommandManager.registerInbuilt()
        ModuleManager.registerInbuilt()
        runCatching(ScriptManager::loadAll).onFailure { error ->
            logger.error("ScriptManager was unable to load scripts.", error)
        }
    }

    private suspend fun initializeResources(
        dispatcher: CoroutineDispatcher,
    ) = withContext(dispatcher) {
        logger.info("Initializing API...")
        ApiConfig.config

        try {
            withTimeout(8000L) { checkVersion() }
        } catch (e: TimeoutCancellationException) {
            if (!IN_DEVELOPMENT && System.getProperty("liquidbounce.skipVerification") != "true") {
                throw ClientVerificationException("版本验证超时，请检查您的网络连接！", VerificationType.VERSION, "https://github.com/DFH-clouds/LiquidBounce-Nextgen/releases")
            }
        } catch (e: Exception) {
            if (!IN_DEVELOPMENT && System.getProperty("liquidbounce.skipVerification") != "true") {
                throw e
            }
        }

        try {
            withTimeout(8000L) { verifyClient() }
        } catch (e: TimeoutCancellationException) {
            if (!IN_DEVELOPMENT && System.getProperty("liquidbounce.skipVerification") != "true") {
                throw ClientVerificationException("HWID验证超时，请检查您的网络连接！", VerificationType.HWID, "https://space.bilibili.com/1124549701", getHWID())
            }
        } catch (e: Exception) {
            if (!IN_DEVELOPMENT && System.getProperty("liquidbounce.skipVerification") != "true") {
                throw e
            }
        }

        if (clientUid == null && !IN_DEVELOPMENT && System.getProperty("liquidbounce.skipVerification") != "true") {
            throw ClientVerificationException(
                "HWID验证失败，无法获取客户端UID。",
                VerificationType.HWID,
                "https://space.bilibili.com/1124549701",
                getHWID()
            )
        }

        supervisorScope {
            launch { LanguageManager.loadDefault() }
            launch { withTimeoutOrNull(8000) { ClientUpdate.update.await() }?.let { logger.info("[Update] Update available: $clientVersion -> ${it.lbVersion}") } }
            launch { CosmeticService.refreshCarriers(force = true) { logger.info("Successfully loaded ${CosmeticService.carriers.size} cosmetics carriers.") } }
            launch { HeadsCreativeModeTab.heads.getFinalState() }
            launch { AutoConfig.reloadConfigs() }
            launch { IpInfoApi.original }
            launch {
                ConfigSystem.load(ClientAccountManager)
                if (ClientAccount.ENV_ACCOUNT != null) {
                    ClientAccountManager.clientAccount = ClientAccount.ENV_ACCOUNT
                }
                if (ClientAccountManager.clientAccount != ClientAccount.EMPTY_ACCOUNT) {
                    runCatching { ClientAccountManager.clientAccount.renew() }
                        .onFailure { ClientAccountManager.clientAccount = ClientAccount.EMPTY_ACCOUNT }
                    ConfigSystem.store(ClientAccountManager)
                }
            }
        }
        logger.info("API initialization done.")
    }

    private suspend fun prepareGuiStage(dispatcher: CoroutineDispatcher) = withContext(dispatcher) {
        RenderSystem.assertOnRenderThread()
        BrowserBackendManager.init()
        ClientInteropServer.start()
        if (!ClientInteropServer.isSkipping) {
            ThemeManager.init()
            ConfigSystem.load(MarketplaceManager)
            ConfigSystem.load(ThemeManager)
            ThemeManager.load()
        }
        BlurEffectRenderer
        ScreenManager
        taskManager = TaskManager(ioScope).apply {
            BrowserBackendManager.makeDependenciesAvailable(this)
            launch("Deep Learning") { task -> runCatching { DeepLearningEngine.init(task); ModelManager.load() }.onFailure { task.subTasks.clear() } }
            launch("Marketplace") { task -> runCatching { MarketplaceManager.updateAll(task) }.onFailure { logger.error("Failed to update marketplace items.", it) }; task.isCompleted = true }
        }
        val duration = measureTime { FontManager.createGlyphManager() }
        logger.info("Completed loading fonts in ${duration.inWholeMilliseconds} ms.")
    }

    private fun shutdownClient() {
        if (!isInitialized) return
        isInitialized = false
        ChunkScanner.stopThread()
        EventManager.unregisterAll()
        ConfigSystem.storeAll()
        BrowserBackendManager.stop()
    }

    @Suppress("unused")
    private val startHandler = handler<ClientStartEvent> {
        runCatching {
            logger.info("Launching $CLIENT_NAME v$clientVersion by $CLIENT_AUTHOR")
            logger.info("Client Version: $clientVersion ($clientCommit)")
            logger.info("Client Branch: $clientBranch")
            logger.info("UID:$clientuid")
            EventManager
            val resourceManager = mc.resourceManager
            if (resourceManager is ReloadableResourceManager) {
                resourceManager.registerReloadListener(ClientResourceReloader)
                resourceManager.registerReloadListener(ThemeManager.reloader)
            } else {
                initializeClient(Dispatchers.Default, Dispatchers.Minecraft).thenRun {
                    ThemeManager.reloader.onResourceManagerReload(resourceManager)
                }
            }
        }.onFailure { ErrorHandler.fatal(it, additionalMessage = "Client start") }
    }

    @Suppress("unused")
    private val screenHandler = handler<ScreenEvent>(priority = FIRST_PRIORITY) { event ->
        if (verificationErrorScreen != null) {
            if (event.screen !== verificationErrorScreen) {
                event.cancelEvent()
                mc.execute {
                    if (mc.screen !== verificationErrorScreen) {
                        mc.setScreen(verificationErrorScreen)
                    }
                }
            }
            return@handler
        }

        val taskManager = taskManager ?: return@handler
        if (!taskManager.isCompleted && event.screen !is TaskProgressScreen) {
            event.cancelEvent()
            mc.setScreen(TaskProgressScreen("Loading Required Libraries", taskManager))
        }
    }

    @Suppress("unused")
    private val shutdownHandler = handler<ClientShutdownEvent> {
        shutdownClient()
    }

    @Suppress("unused")
    private val uidRenderHandler = handler<OverlayRenderEvent> { event ->
        val uid = clientUid ?: return@handler
        val context = event.context
        val font = mc.font
        val x = 5
        val y = mc.window.guiScaledHeight - font.lineHeight - 5
        val y1 = mc.window.guiScaledHeight - font.lineHeight - 16
        context.text(font, "§7Version: §f$version1", x, y, -1, true)
        context.text(font, "§7UID: §f$uid", x, y1, -1, true)
    }

    private object ClientResourceReloader : PreparableReloadListener {
        override fun reload(
            store: PreparableReloadListener.SharedState,
            prepareExecutor: Executor,
            synchronizer: PreparableReloadListener.PreparationBarrier,
            applyExecutor: Executor
        ): CompletableFuture<Void> {
            return synchronizer.wait(net.minecraft.util.Unit.INSTANCE)
                .thenCompose {
                    @Suppress("UNCHECKED_CAST")
                    initializeClient(
                        workerDispatcher = prepareExecutor.asCoroutineDispatcher(),
                        renderThreadDispatcher = applyExecutor.asCoroutineDispatcher(),
                    ) as CompletableFuture<Void>
                }
        }
        override fun getName() = CLIENT_NAME
    }

    // 自定义的验证失败弹窗
    class CustomVerificationErrorScreen(
        private val errorTitle: Component,
        private val errorReason: Component,
        private val jumpUrl: String?,
        private val hwid: String?
    ) : Screen(Component.literal("Verification Failed")) {

        private fun copyToClipboard(text: String) {
            try {
                mc.keyboardHandler.setClipboard(text)
            } catch (_: Throwable) {
                try {
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    clipboard.setContents(StringSelection(text), null)
                } catch (_: Throwable) {}
            }
        }

        private fun quitGame() {
            mc.stop()
            Thread {
                try { Thread.sleep(1000) } catch (_: Exception) {}
                System.exit(0)
            }.start()
        }

        override fun init() {
            super.init()
            var btnY = this.height / 2 + 30

            if (hwid != null) {
                this.addRenderableWidget(
                    Button.builder(Component.literal("复制 HWID")) { _ ->
                        copyToClipboard(hwid)
                    }.bounds(this.width / 2 - 100, btnY, 200, 20).build()
                )
                btnY += 24
            }

            if (jumpUrl != null) {
                this.addRenderableWidget(
                    Button.builder(Component.literal("复制 B站/GitHub 链接并退出")) { _ ->
                        copyToClipboard(jumpUrl)
                        quitGame()
                    }.bounds(this.width / 2 - 100, btnY, 200, 20).build()
                )
                btnY += 24
            }

            this.addRenderableWidget(
                Button.builder(Component.literal("直接退出")) { _ ->
                    quitGame()
                }.bounds(this.width / 2 - 100, btnY, 200, 20).build()
            )
        }

        override fun extractRenderState(
            context: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
            partialTick: Float
        ) {
            context.fill(0, 0, this.width, this.height, 0xEF201010.toInt())
            val font = mc.font

            val titleW = font.width(this.errorTitle)
            context.text(font, this.errorTitle, (this.width - titleW) / 2, this.height / 2 - 110, 0xFFFF5555.toInt())

            val lines = this.errorReason.string.split("\n")
            var y = this.height / 2 - 85
            for (line in lines) {
                val w = font.width(line)
                context.text(font, line, (this.width - w) / 2, y, 0xFFFFFFFF.toInt())
                y += 12
            }

            super.extractRenderState(context, mouseX, mouseY, partialTick)
        }

        override fun shouldCloseOnEsc(): Boolean = false
    }
}
