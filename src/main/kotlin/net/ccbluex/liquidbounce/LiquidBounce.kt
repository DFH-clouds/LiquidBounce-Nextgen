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
import net.ccbluex.liquidbounce.LiquidBounce.CLIENT_NAME
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
import net.minecraft.client.gui.screens.ErrorScreen
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.packs.resources.ReloadableResourceManager
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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

/**
 * LiquidBounce
 *
 * A free mixin-based injection hacked-client for Minecraft using FabricMC.
 *
 * @author kawaiinekololis (@team CCBlueX)
 */
object LiquidBounce : EventListener {

    /**
     * CLIENT INFORMATION
     */
    const val CLIENT_NAME = "LiquidBounce"
    const val CLIENT_AUTHOR = "CCBlueX"
    val version1 = "3.0"

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

    const val IN_DEVELOPMENT = false

    val logger get() = net.ccbluex.liquidbounce.utils.client.logger

    var taskManager: TaskManager? = null
    var isInitialized = false
        private set

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


//这里替换你的HWID验证仓库
    private const val HWID_LIST_URL = "https://gitee.com/Huacishu1/liquid-bounce-nextgen-hwid/raw/master/HWID"
//你猜我有什么东西没删
    private suspend fun verifyClient() {
        if (IN_DEVELOPMENT || System.getProperty("liquidbounce.skipVerification") == "true") {
            logger.warn("HWID verification is disabled (development mode).")
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val url = URL(HWID_LIST_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000


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
                if (currentHWID in hwidList) {
                    logger.info("HWID verification passed. ($currentHWID)")
                } else {
                    logger.warn("HWID not in whitelist: $currentHWID")
                    throw IllegalStateException("Device not authorized (HWID not in whitelist)")
                }
            } catch (e: Exception) {
                logger.error("HWID verification error", e)
                throw e
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
            initializeResources(workerDispatcher)   // 内部执行 HWID 验证
            prepareGuiStage(renderThreadDispatcher)
        } catch (e: Exception) {
            withContext(Dispatchers.Minecraft) {
                val title = Component.literal("Device Verification Failed")
                val message = Component.literal(e.message ?: "Unknown verification error")
                mc.setScreen(ErrorScreen(title, message))
            }
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

        supervisorScope {

            val verificationDeferred = async {
                try {
                    withTimeout(8000L) { verifyClient() }
                } catch (e: TimeoutCancellationException) {
                    if (IN_DEVELOPMENT || System.getProperty("liquidbounce.skipVerification") == "true") {
                        logger.warn("HWID verification timed out, but skipping is enabled.")
                        return@async
                    }
                    throw IllegalStateException("HWID verification timed out", e)
                } catch (e: Exception) {
                    if (IN_DEVELOPMENT || System.getProperty("liquidbounce.skipVerification") == "true") {
                        logger.warn("HWID verification failed, but skipping is enabled: ${e.message}")
                        return@async
                    }
                    throw e
                }
            }

            launch { LanguageManager.loadDefault() }
            launch {
                val update = withTimeoutOrNull(8000) { ClientUpdate.update.await() } ?: return@launch
                logger.info("[Update] Update available: $clientVersion -> ${update.lbVersion}")
            }
            launch {
                CosmeticService.refreshCarriers(force = true) {
                    logger.info("Successfully loaded ${CosmeticService.carriers.size} cosmetics carriers.")
                }
            }
            launch { HeadsCreativeModeTab.heads.getFinalState() }
            launch { AutoConfig.reloadConfigs() }
            launch { IpInfoApi.original }
            launch {
                ConfigSystem.load(ClientAccountManager)
                if (ClientAccount.ENV_ACCOUNT != null) {
                    ClientAccountManager.clientAccount = ClientAccount.ENV_ACCOUNT
                }
                if (ClientAccountManager.clientAccount != ClientAccount.EMPTY_ACCOUNT) {
                    runCatching {
                        ClientAccountManager.clientAccount.renew()
                    }.onFailure {
                        logger.error("Failed to renew client account token.", it)
                        ClientAccountManager.clientAccount = ClientAccount.EMPTY_ACCOUNT
                    }.onSuccess {
                        logger.info("Successfully renewed client account token.")
                    }
                    ConfigSystem.store(ClientAccountManager)
                }
            }


            verificationDeferred.await()
        }

        logger.info("API initialization done.")
    }

    private suspend fun prepareGuiStage(
        dispatcher: CoroutineDispatcher
    ) = withContext(dispatcher) {
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
            launch("Deep Learning") { task ->
                runCatching {
                    DeepLearningEngine.init(task)
                    ModelManager.load()
                }.onFailure { exception ->
                    task.subTasks.clear()
                    logger.info("Failed to initialize deep learning.", exception)
                }
            }
            launch("Marketplace") { task ->
                runCatching {
                    MarketplaceManager.updateAll(task)
                }.onFailure { exception ->
                    logger.error("Failed to update marketplace items.", exception)
                }
                task.isCompleted = true
            }
        }

        val duration = measureTime { FontManager.createGlyphManager() }
        logger.info("Completed loading fonts in ${duration.inWholeMilliseconds} ms.")
        logger.info("Fonts: [ ${FontManager.fontFaces.keys.joinToString()} ]")
    }

    private fun shutdownClient() {
        if (!isInitialized) return
        isInitialized = false
        logger.info("Shutting down client...")
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
            logger.info("Operating System: ${System.getProperty("os.name")} (${System.getProperty("os.version")})")
            logger.info("Java Version: ${System.getProperty("java.version")}")
            logger.info("Screen Resolution: ${mc.window.screenWidth}x${mc.window.screenHeight}")
            logger.info("Refresh Rate: ${mc.window.refreshRate} Hz")

            EventManager

            val resourceManager = mc.resourceManager
            if (resourceManager is ReloadableResourceManager) {
                resourceManager.registerReloadListener(ClientResourceReloader)
                resourceManager.registerReloadListener(ThemeManager.reloader)
            } else {
                logger.warn("Failed to register resource reloader!")
                initializeClient(
                    workerDispatcher = Dispatchers.Default,
                    renderThreadDispatcher = Dispatchers.Minecraft,
                ).thenRun {
                    ThemeManager.reloader.onResourceManagerReload(resourceManager)
                }
            }
        }.onFailure {
            ErrorHandler.fatal(it, additionalMessage = "Client start")
        }
    }

    @Suppress("unused")
    private val screenHandler = handler<ScreenEvent>(priority = FIRST_PRIORITY) { event ->
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

    private object ClientResourceReloader : PreparableReloadListener {
        override fun reload(
            store: PreparableReloadListener.SharedState,
            prepareExecutor: Executor,
            synchronizer: PreparableReloadListener.PreparationBarrier,
            applyExecutor: Executor
        ): CompletableFuture<Void> {
            return synchronizer.wait(net.minecraft.util.Unit.INSTANCE)
                .thenCompose {
                    val prepareDispatcher = prepareExecutor.asCoroutineDispatcher()
                    val applyDispatcher = applyExecutor.asCoroutineDispatcher()
                    @Suppress("UNCHECKED_CAST")
                    initializeClient(
                        workerDispatcher = prepareDispatcher,
                        renderThreadDispatcher = applyDispatcher,
                    ) as CompletableFuture<Void>
                }
        }

        override fun getName() = CLIENT_NAME
    }
}
