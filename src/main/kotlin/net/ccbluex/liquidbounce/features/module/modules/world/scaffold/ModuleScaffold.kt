/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.module.modules.world.scaffold

import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.*
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.movement.ModuleSafeWalk
import net.ccbluex.liquidbounce.features.module.modules.player.ModuleStuck
import net.ccbluex.liquidbounce.features.module.modules.player.nofall.modes.NoFallBlink
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug.debugGeometry
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug.debugParameter
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold.ScaffoldRotationValueGroup.RotationTimingMode.NORMAL
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold.ScaffoldRotationValueGroup.RotationTimingMode.ON_TICK
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold.ScaffoldRotationValueGroup.RotationTimingMode.ON_TICK_SNAP
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold.ScaffoldRotationValueGroup.considerInventory
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold.ScaffoldRotationValueGroup.rotationTiming
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ScaffoldBlockItemSelection.isValidBlock
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.features.*
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.techniques.*
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.techniques.normal.ScaffoldDownFeature
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.techniques.normal.ScaffoldEagleFeature
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.tower.*
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationsValueGroup
import net.ccbluex.liquidbounce.utils.aiming.utils.withFixedYaw
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.block.doPlacement
import net.ccbluex.liquidbounce.utils.block.targetBlockPos
import net.ccbluex.liquidbounce.utils.block.targetfinding.BlockPlacementTarget
import net.ccbluex.liquidbounce.utils.clicking.Clicker
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.client.Timer
import net.ccbluex.liquidbounce.utils.entity.moving
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.item.*
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.math.copy
import net.ccbluex.liquidbounce.utils.math.geometry.Line
import net.ccbluex.liquidbounce.utils.math.minus
import net.ccbluex.liquidbounce.utils.math.allEmpty
import net.ccbluex.liquidbounce.utils.math.toVec3d
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.ccbluex.liquidbounce.utils.render.placement.PlacementRenderer
import net.ccbluex.liquidbounce.utils.sorting.ComparatorChain
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Pose
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import java.lang.Math.abs
import java.lang.Math.sqrt

//修复了原版Scaffold 方块在背包里面无法进行搭路的bug  注：当物品栏没有方块时 会自动替换当前物品栏的物品进行交换  2026/8/23
//内置 FallRescue：坠落自救（方块自救 + Stuck 兜底自动集合），可自愈 26/11/5

@Suppress("TooManyFunctions")
object ModuleScaffold : ClientModule("Scaffold", ModuleCategories.WORLD) {

    val fullSprint by boolean("FullSprint", false)
    val bedWars by boolean("BedWars", false)
    val switchBack by boolean("SwitchBack", true)

    private val delay by intRange("Delay", 0..0, 0..40, "ticks")
    private val minDist by float("MinDist", 0.0f, 0.0f..0.25f)
    private val timer by float("Timer", 1f, 0.01f..10f)

    init {
        tree(ScaffoldBlockItemSelection)
        tree(ScaffoldAutoBlockFeature)
        tree(ScaffoldMovementPrediction)
    }

    internal val technique = choices(
        "Technique",
        ScaffoldNormalTechnique,
        arrayOf(
            ScaffoldNormalTechnique,
            ScaffoldExpandTechnique,
            ScaffoldGodBridgeTechnique,
            ScaffoldBreezilyTechnique
        )
    ).apply(::tagBy)

    private val sameYMode by enumChoice("SameY", SameYMode.OFF)

    @Suppress("unused")
    private enum class SameYMode(
        override val tag: String,
        val getTargetedBlockPos: (BlockPos) -> BlockPos?
    ) : Tagged {
        OFF("Off", { null }),
        ON("On", { blockPos -> blockPos.copy(y = placementY) }),
        FALLING("Falling", { blockPos -> blockPos.copy(y = placementY).takeIf { player.deltaMovement.y < 0.2 } }),
        HYPIXEL("Hypixel", { blockPos ->
            if (player.deltaMovement.y == -0.15233518685055708 && jumps >= 2) {
                jumps = 0
                blockPos.copy(y = startY)
            } else {
                blockPos.copy(y = startY - 1)
            }
        })
    }

    val towerMode = choices("Tower", 0) {
        arrayOf(
            ScaffoldTowerNone,
            ScaffoldTowerMotion,
            ScaffoldTowerPulldown,
            ScaffoldTowerKarhu,
            ScaffoldTowerVulcan,
            ScaffoldTowerHypixel
        )
    }

    internal val isTowering: Boolean
        get() = if (towerMode.activeMode != ScaffoldTowerNone && mc.options.keyJump.isDown) {
            this.wasTowering = true
            true
        } else {
            false
        }
    private var wasTowering: Boolean = false

    // 救援优先级高于其它模式，强制用 NormalTechnique（Simple 的脚下放置逻辑）
    private val activeTechnique get() = when {
        ScaffoldFallRescueFeature.isRescuing -> ScaffoldNormalTechnique
        isTowering -> ScaffoldNormalTechnique
        else -> technique.activeMode
    }

    @Suppress("unused")
    private val safeWalkMode = choices("SafeWalk", 1, ModuleSafeWalk::safeWalkChoices)

    internal object ScaffoldRotationValueGroup : RotationsValueGroup(this) {
        val considerInventory by boolean("ConsiderInventory", false)
        val rotationTiming by enumChoice("RotationTiming", NORMAL)

        enum class RotationTimingMode(override val tag: String) : Tagged {
            NORMAL("Normal"),
            ON_TICK("OnTick"),
            ON_TICK_SNAP("OnTickSnap")
        }
    }

    private var currentTarget: BlockPlacementTarget? = null
    private val swingMode by enumChoice("Swing", SwingMode.DO_NOT_HIDE)

    private object SimulatePlacementAttempts : ToggleableValueGroup(this, "SimulatePlacementAttempts", false) {
        val clicker = tree(Clicker(ModuleScaffold, mc.options.keyUse, null, maxCps = 100))
        val failedAttemptsOnly by boolean("FailedAttemptsOnly", true)
    }

    internal object ScaffoldFallRescueFeature : ToggleableValueGroup(ModuleScaffold, "FallRescue", true) {

        // 触发阈值：掉落 2 格即开始尝试自救（从 4 格掉最终会扣 3 心，来得及）
        private val minFallDistance by int("MinFallDistance", 2, 1..30, "blocks")
        // 距上次成功放置超过 N tick 仍无进展 → 上 Stuck
        private val stuckTriggerAfterTicks by int("StuckTriggerAfterTicks", 5, 1..40, "ticks")
        // 救援总上限，到点停止 Scaffold 自救让 Stuck 完全接管
        private val maxRescueTicks by int("MaxRescueTicks", 60, 20..400, "ticks")

        private var rescueActive = false
        private var rescueStartTick = 0L
        private var lastPlaceTick = -1L
        private var stuckActivated = false
        private var wasStuckEnabledBefore = false
        private var selfRescueTimedOut = false

        /** 处于救援中（开关打开时才可能为 true） */
        val isRescuing: Boolean get() = enabled && rescueActive

        /**
         * 救援期间**始终**覆盖 Scaffold 落点到「脚下」（不因放置成功停止）。
         * 只有超时（让 Stuck 完全接管）时才停。
         */
        val shouldOverridePlacement: Boolean get() = isRescuing && !selfRescueTimedOut

        fun onTick() {
            if (!enabled) {
                if (rescueActive) reset()
                return
            }
            if (!rescueActive) {
                if (checkShouldRescue()) startRescue() else return
            }

            val now = mc.level?.gameTime ?: 0L
            val elapsed = now - rescueStartTick

            // 结束条件（已安全）
            if (player.onGround()
                || player.abilities.flying
                || player.isFallFlying
                || player.isInWater || player.isInLava || player.isInPowderSnow
                || player.isSpectator
            ) {
                reset()
                return
            }

            if (!stuckActivated) {
                val ref = if (lastPlaceTick >= 0) lastPlaceTick else rescueStartTick
                if (now - ref >= stuckTriggerAfterTicks) {
                    activateStuck()
                }
            }

            if (elapsed > maxRescueTicks && !selfRescueTimedOut) {
                selfRescueTimedOut = true
                if (!stuckActivated) activateStuck()
                logger.info("[Scaffold] Fall rescue timed out, Stuck takes over")
            }

            // Stuck 跑完后自动收尾（若 Stuck 由本 Feature 启用）
            if (stuckActivated && !wasStuckEnabledBefore && !ModuleStuck.enabled) {
                reset(disableStuck = false)
            }
        }

        /** 由 commonPlaceSucceed 调用：记录最近一次成功放置的时刻 */
        fun onBlockPlaced() {
            if (rescueActive) lastPlaceTick = mc.level?.gameTime ?: 0L
        }

        /**
         * 供 getTargetedPosition 使用：
         * 只要玩家脚下格是空气就返回它（每下落一格自动放一块）。
         * 若脚下已经是方块（已踩到）则返回 null → 交给正常 Scaffold 逻辑。
         */
        fun getRescueTargetPos(): BlockPos? {
            if (!shouldOverridePlacement) return null
            val below = player.blockPosition().below()
            return if (world.getBlockState(below).isAir) below else null
        }

        private fun checkShouldRescue(): Boolean {
            if (player.onGround()) return false
            if (player.abilities.flying) return false
            if (player.isFallFlying) return false
            if (player.isInWater || player.isInLava || player.isInPowderSnow) return false
            if (player.isSpectator) return false
            if (player.fallDistance < minFallDistance) return false
            return player.deltaMovement.y < -0.1
        }

        private fun startRescue() {
            rescueActive = true
            rescueStartTick = mc.level?.gameTime ?: 0L
            lastPlaceTick = -1L
            stuckActivated = false
            wasStuckEnabledBefore = false
            selfRescueTimedOut = false
            logger.info("[Scaffold] Fall rescue activated (fall=${player.fallDistance.toInt()})")
        }

        private fun activateStuck() {
            stuckActivated = true
            wasStuckEnabledBefore = ModuleStuck.enabled
            if (!wasStuckEnabledBefore) {
                try {
                    ModuleStuck.enabled = true
                    logger.info("[Scaffold] Fall rescue -> ModuleStuck activated")
                } catch (e: Exception) {
                    logger.error("[Scaffold] Failed to activate ModuleStuck", e)
                }
            }
        }

        fun reset(disableStuck: Boolean = true) {
            if (rescueActive) {
                if (disableStuck && stuckActivated && !wasStuckEnabledBefore) {
                    try { ModuleStuck.forceStop() } catch (_: Exception) {}
                }
                logger.info(
                    "[Scaffold] Fall rescue ended (placedThisTick=${lastPlaceTick >= 0}, " +
                        "stuck=$stuckActivated, timedOut=$selfRescueTimedOut)"
                )
            }
            rescueActive = false
            rescueStartTick = 0L
            lastPlaceTick = -1L
            stuckActivated = false
            wasStuckEnabledBefore = false
            selfRescueTimedOut = false
        }
    }

    init {
        tree(ScaffoldRotationValueGroup)
        tree(ScaffoldSprintControlFeature)
        tree(SimulatePlacementAttempts)
        tree(ScaffoldAccelerationFeature)
        tree(ScaffoldStrafeFeature)
        tree(ScaffoldJumpStrafe)
        tree(ScaffoldSpeedLimiterFeature)
        tree(ScaffoldBlinkFeature)
        tree(ScaffoldFallRescueFeature)
    }

    val autoSpeed by boolean("AutoSpeed", false)

    private var ledge by boolean("Ledge", true)

    private val renderer = tree(PlacementRenderer("Render", true, this, keep = false))

    private var placementY = 0
    private var forceSneak = 0
    private var startY = 0
    private var jumps = 0
    private var nextBlock: Block? = null

    // 抄的南方
    private var originalSlot = -1
    private var bigVelocityTick = 0

    val blockCount: Int
        get() {
            fun ItemStack.blockCount() = if (isValidBlock(this)) this.count else 0

            return player.offhandItem.blockCount() + if (ScaffoldAutoBlockFeature.enabled) {
                findAllPlaceableSlots().sumOf { it.value.blockCount() }
            } else {
                player.inventory.getItem(player.inventory.selectedSlot).blockCount()
            }
        }

    val isBlockBelow: Boolean
        get() = !world.getBlockCollisions(
            player,
            player.boundingBox.inflate(0.5, 0.0, 0.5).move(0.0, -1.05, 0.0)
        ).allEmpty()

    private val BLOCK_COMPARATOR_FOR_HOTBAR = ComparatorChain(
        PreferFavourableBlocks,
        PreferSolidBlocks,
        PreferFullCubeBlocks,
        PreferWalkableBlocks,
        PreferAverageHardBlocks(neutralRange = true),
        PreferStackSize.PREFER_MORE,
        PreferAverageHardBlocks(neutralRange = false),
    )
    @JvmField
    val BLOCK_COMPARATOR_FOR_INVENTORY = ComparatorChain(
        PreferFavourableBlocks,
        PreferSolidBlocks,
        PreferFullCubeBlocks,
        PreferWalkableBlocks,
        PreferAverageHardBlocks(neutralRange = true),
        PreferStackSize.PREFER_FEWER,
        PreferAverageHardBlocks(neutralRange = false),
    )

    override fun onEnabled() {
        placementY = player.blockPosition().y - 1
        startY = player.blockPosition().y
        jumps = 2
        originalSlot = player.inventory.selectedSlot
        bigVelocityTick = 0
        ScaffoldMovementPlanner.reset()
        super.onEnabled()
    }

    override fun onDisabled() {
        reset()
    }

    private fun reset() {
        NoFallBlink.waitUntilGround = false
        ScaffoldMovementPlanner.reset()
        ScaffoldMovementPrediction.reset()
        SilentHotbar.resetSlot(this)
        nextBlock = null
        updateRenderCount(null)
        forceSneak = 0
        currentTarget = null
        renderer.clearSilently()
        ScaffoldFallRescueFeature.reset()
        if (switchBack && originalSlot != -1 && originalSlot != player.inventory.selectedSlot) {
            SilentHotbar.selectSlotSilently(this, originalSlot, 0)
        }
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> { reset() }

    private fun updateRenderCount(count: Int?) {
        EventManager.callEvent(BlockCountChangeEvent(nextBlock, count))
    }

    //击退检测
    @Suppress("unused")
    private val velocityHandler = handler<PacketEvent> { event ->
        val packet = event.packet
        if (packet is net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket) {
            val id = try {
                val field = packet.javaClass.getDeclaredField("entityId")
                field.isAccessible = true
                field.getInt(packet)
            } catch (_: Exception) {
                val field = packet.javaClass.getDeclaredField("id")
                field.isAccessible = true
                field.getInt(packet)
            }
            if (id == player.id) {
                val x = try {
                    val field = packet.javaClass.getDeclaredField("xa")
                    field.isAccessible = true
                    field.getInt(packet).toDouble() / 8000.0
                } catch (_: Exception) {
                    try {
                        val field = packet.javaClass.getDeclaredField("velocityX")
                        field.isAccessible = true
                        field.getInt(packet).toDouble() / 8000.0
                    } catch (_: Exception) { 0.0 }
                }
                val z = try {
                    val field = packet.javaClass.getDeclaredField("za")
                    field.isAccessible = true
                    field.getInt(packet).toDouble() / 8000.0
                } catch (_: Exception) {
                    try {
                        val field = packet.javaClass.getDeclaredField("velocityZ")
                        field.isAccessible = true
                        field.getInt(packet).toDouble() / 8000.0
                    } catch (_: Exception) { 0.0 }
                }
                val strength = sqrt(x * x + z * z)
                if (strength >= 1.5) {
                    bigVelocityTick = 60
                }
            }
        }
    }

    // 全速跑 & 救援水平输入清空（防止玩家擦边错过脚下方块）
    @Suppress("unused")
    private val moveInputHandler = handler<MovementInputEvent>(priority = EventPriorityConvention.SAFETY_FEATURE) { event ->
        // 救援优先级最高：清空水平输入 + 强制蹲（垂直下落到脚下方块上）
        if (ScaffoldFallRescueFeature.isRescuing) {
            event.directionalInput = DirectionalInput.NONE
            event.sneak = true
            return@handler
        }

        if (fullSprint && player.moving) {
            event.sneak = false
        }
        if (bedWars) {
            // BedWars 模式可在此扩展 懒得写
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        ScaffoldFallRescueFeature.onTick()
        updateRenderCount(blockCount)
        if (bigVelocityTick > 0) bigVelocityTick--

        if (player.onGround()) {
            placementY = player.blockPosition().y - 1
            jumps++
            wasTowering = false
        }

        if (mc.options.keyJump.isDown) {
            startY = player.blockPosition().y
            jumps = 2
        }

        debugParameter("IsTowering") { isTowering }
        debugParameter("WasTowering") { wasTowering }
        debugParameter("BigVelocityTick") { bigVelocityTick }
        debugParameter("IsRescuing") { ScaffoldFallRescueFeature.isRescuing }

        if (bigVelocityTick > 0) return@tickHandler

        val target = currentTarget
        val technique = activeTechnique

        val currentRotation = if ((rotationTiming == ON_TICK || rotationTiming == ON_TICK_SNAP) && target != null) {
            technique.getRotations(target) ?: (RotationManager.currentRotation ?: player.rotation)
        } else {
            RotationManager.currentRotation ?: player.rotation
        }.normalize()
        val currentCrosshairTarget = technique.getCrosshairTarget(target, currentRotation)
        val currentDelay = delay.random()

        var hasBlockInMainHand = isValidBlock(player.inventory.getItem(player.inventory.selectedSlot))
        val hasBlockInOffHand = isValidBlock(player.offhandItem)

        if (ScaffoldAutoBlockFeature.alwaysHoldBlock) {
            hasBlockInMainHand = handleSilentBlockSelection(hasBlockInMainHand, hasBlockInOffHand)
        }

        val suitableHand = InteractionHand.entries.firstOrNull {
            isValidBlock(player.getItemInHand(it))
        }

        fun commonPlaceSucceed(placed: BlockPos) {
            ScaffoldMovementPlanner.trackPlacedBlock(placed)
            renderer.addBlock(placed)
            ScaffoldEagleFeature.onBlockPlacement()
            ScaffoldBlinkFeature.onBlockPlacement()
            ScaffoldSprintControlFeature.onBlockPlacement()
            ScaffoldFallRescueFeature.onBlockPlaced()
            if (switchBack && originalSlot != -1 && originalSlot != player.inventory.selectedSlot) {
                SilentHotbar.selectSlotSilently(this, originalSlot, 0)
            }
        }

        if (simulatePlacementAttempts(currentCrosshairTarget, suitableHand) && player.moving
            && SimulatePlacementAttempts.clicker.isClickTick
        ) {
            SimulatePlacementAttempts.clicker.click {
                doPlacement(currentCrosshairTarget!!, suitableHand!!, {
                    commonPlaceSucceed(currentCrosshairTarget.targetBlockPos)
                    true
                }, swingMode = swingMode)
                true
            }
        }

        if (target == null || currentCrosshairTarget == null) {
            return@tickHandler
        }

        //救援时忽略 minDist：快速垂直下坠时视线几乎正下方，dist 会小于阈值
        if (!target.doesCrosshairTargetMatchRequirements(currentCrosshairTarget) ||
            (!ScaffoldFallRescueFeature.isRescuing && !isValidCrosshairTarget(currentCrosshairTarget))
        ) {
            return@tickHandler
        }

        if (!ScaffoldAutoBlockFeature.alwaysHoldBlock) {
            hasBlockInMainHand = handleSilentBlockSelection(hasBlockInMainHand, hasBlockInOffHand)
        }

        if (!hasBlockInMainHand && !hasBlockInOffHand) {
            return@tickHandler
        }

        val handToInteractWith = if (hasBlockInMainHand) InteractionHand.MAIN_HAND else InteractionHand.OFF_HAND
        var wasSuccessful = false

        if (rotationTiming == ON_TICK || rotationTiming == ON_TICK_SNAP) {
            if (currentRotation != RotationManager.serverRotation) {
                network.send(
                    PosRot(
                        player.x, player.y, player.z,
                        currentRotation.yaw,
                        currentRotation.pitch,
                        player.onGround(),
                        player.horizontalCollision
                    )
                )
            }
            if (rotationTiming == ON_TICK_SNAP) {
                RotationManager.setRotationTarget(
                    currentRotation,
                    considerInventory = considerInventory,
                    valueGroup = ScaffoldRotationValueGroup,
                    provider = this@ModuleScaffold,
                    priority = Priority.IMPORTANT_FOR_PLAYER_LIFE
                )
            }
        }

        val previousFallOffPos = currentOptimalLine?.let { l -> ScaffoldMovementPrediction.getFallOffPositionOnLine(l) }

        doPlacement(currentCrosshairTarget, handToInteractWith, {
            commonPlaceSucceed(target.placedBlock)
            currentTarget = null
            wasSuccessful = true
            true
        }, swingMode = swingMode)

        if (rotationTiming == ON_TICK && RotationManager.serverRotation != player.rotation) {
            network.send(
                PosRot(
                    player.x, player.y, player.z,
                    player.withFixedYaw(currentRotation), player.xRot, player.onGround(),
                    player.horizontalCollision
                )
            )
        }

        if (wasSuccessful) {
            ScaffoldMovementPrediction.onPlace(currentOptimalLine, previousFallOffPos)
            if (!ScaffoldFallRescueFeature.isRescuing) {
                waitTicks(currentDelay)
            }
        }
    }

    private fun findPlaceableSlots() = buildList(9) {
        for (i in 0..8) {
            val stack = player.inventory.getItem(i)
            if (isValidBlock(stack)) {
                add(IndexedValue(i, stack))
            }
        }
    }
    /**
     * 搜索整个背包能用的方块
     */
    private fun findAllPlaceableSlots() = buildList(36) {
        for (i in 0..35) {
            val stack = player.inventory.getItem(i)
            if (isValidBlock(stack)) {
                add(IndexedValue(i, stack))
            }
        }
    }

    private fun findBestValidHotbarSlotForTarget(): Int? {
        val placeableSlots = findPlaceableSlots()
        val doNotUseBelowCount = ScaffoldAutoBlockFeature.doNotUseBelowCount
        val (slot, _) = placeableSlots
            .filter { (_, stack) -> stack.count > doNotUseBelowCount }
            .maxWithOrNull { o1, o2 -> BLOCK_COMPARATOR_FOR_HOTBAR.compare(o1.value, o2.value) }
            ?: placeableSlots.maxWithOrNull { o1, o2 -> BLOCK_COMPARATOR_FOR_HOTBAR.compare(o1.value, o2.value) }
            ?: return null
        return slot
    }

    private fun moveInventoryBlockToHotbar(): Int? {
        val allSlots = findAllPlaceableSlots()
        val doNotUseBelowCount = ScaffoldAutoBlockFeature.doNotUseBelowCount

        val inventorySlots = allSlots.filter { (index, _) -> index in 9..35 }
        if (inventorySlots.isEmpty()) return null

        val (bestInvSlot, _) = inventorySlots
            .filter { (_, stack) -> stack.count > doNotUseBelowCount }
            .maxWithOrNull { o1, o2 -> BLOCK_COMPARATOR_FOR_INVENTORY.compare(o1.value, o2.value) }
            ?: inventorySlots.maxWithOrNull { o1, o2 -> BLOCK_COMPARATOR_FOR_INVENTORY.compare(o1.value, o2.value) }
            ?: return null

        val targetHotbarSlot = (0..8).firstOrNull { player.inventory.getItem(it).isEmpty }
            ?: player.inventory.selectedSlot

        try {
            val gameMode = mc.gameMode ?: return null
            val gameModeClass = gameMode.javaClass

            val clickMethod = gameModeClass.methods.firstOrNull { m ->
                m.parameterTypes.size == 5 &&
                    m.parameterTypes[0] == Int::class.javaPrimitiveType &&
                    m.parameterTypes[1] == Int::class.javaPrimitiveType &&
                    m.parameterTypes[2] == Int::class.javaPrimitiveType &&
                    m.parameterTypes[3].isEnum &&
                    m.parameterTypes[4].name.contains("Player")
            } ?: return null

            val clickTypeClass = clickMethod.parameterTypes[3]
            val swap = clickTypeClass.enumConstants.firstOrNull {
                (it as Enum<*>).name == "SWAP"
            } ?: return null

            clickMethod.invoke(
                gameMode,
                player.inventoryMenu.containerId,
                bestInvSlot,
                targetHotbarSlot,
                swap,
                player
            )

            return targetHotbarSlot
        } catch (e: Exception) {
            logger.error("Failed to swap inventory slot via reflection", e)
            return null
        }
    }

    internal fun isValidCrosshairTarget(rayTraceResult: BlockHitResult): Boolean {
        val diff = rayTraceResult.location - player.eyePosition
        val side = rayTraceResult.direction
        if (side.axis != Direction.Axis.Y) {
            val dist = if (side == Direction.NORTH || side == Direction.SOUTH) diff.z else diff.x
            if (abs(dist) < minDist) return false
        }
        return true
    }

    internal fun getTargetedPosition(blockPos: BlockPos) = when {
        //救援：始终覆盖到脚下空气格
        ScaffoldFallRescueFeature.shouldOverridePlacement ->
            ScaffoldFallRescueFeature.getRescueTargetPos() ?: blockPos

        isTowering || wasTowering -> towerMode.activeMode.getTargetedPosition(blockPos)
        ScaffoldDownFeature.running && ScaffoldDownFeature.shouldGoDown ->
            blockPos.offset(0, -2, 0)
        ScaffoldCeilingFeature.running && ScaffoldCeilingFeature.canConstructCeiling() ->
            blockPos.offset(0, 3, 0)
        player.input.keyPresses.jump && (!player.moving || player.horizontalCollision) ->
            blockPos.offset(0, -1, 0)
        else -> sameYMode.getTargetedBlockPos(blockPos) ?: blockPos.offset(0, -1, 0)
    }

    private fun simulatePlacementAttempts(
        hitResult: BlockHitResult?,
        suitableHand: InteractionHand?,
    ): Boolean {
        val stack = suitableHand?.let(player::getItemInHand) ?: return false
        if (hitResult == null || !SimulatePlacementAttempts.enabled) return false
        if (hitResult.type != HitResult.Type.BLOCK) return false
        val context = UseOnContext(player, suitableHand, hitResult)
        val canPlaceOnFace = (stack.item as BlockItem).getPlacementState(BlockPlaceContext(context)) != null
        return when {
            SimulatePlacementAttempts.failedAttemptsOnly -> !canPlaceOnFace
            sameYMode != SameYMode.OFF -> {
                context.clickedPos.y == placementY && (hitResult.direction != Direction.UP || !canPlaceOnFace)
            }
            else -> {
                val isTargetUnderPlayer = context.clickedPos.y <= player.blockY - 1
                val isTowering = context.clickedPos.y == player.blockY - 1 &&
                    canPlaceOnFace && context.clickedFace == Direction.UP
                isTargetUnderPlayer && !isTowering
            }
        }
    }

    private fun handleSilentBlockSelection(hasBlockInMainHand: Boolean, hasBlockInOffHand: Boolean): Boolean {
        if (ScaffoldAutoBlockFeature.enabled && !hasBlockInMainHand && !hasBlockInOffHand) {
            val bestMainHandSlot = findBestValidHotbarSlotForTarget()
                ?: moveInventoryBlockToHotbar()

            if (bestMainHandSlot != null) {
                SilentHotbar.selectSlotSilently(
                    this, bestMainHandSlot,
                    ScaffoldAutoBlockFeature.slotResetDelay
                )
                return true
            } else {
                SilentHotbar.resetSlot(this)
            }
        } else {
            SilentHotbar.resetSlot(this)
        }

        return hasBlockInMainHand
    }

    //Rotation 处理
    var currentOptimalLine: Line? = null
    var rawInput = DirectionalInput.NONE

    @Suppress("unused")
    private val handleMovementInput = handler<MovementInputEvent>(
        priority = EventPriorityConvention.MODEL_STATE
    ) { event ->
        this.currentOptimalLine = null
        this.rawInput = event.directionalInput
        if (event.directionalInput == DirectionalInput.NONE) return@handler
        this.currentOptimalLine = ScaffoldMovementPlanner.getOptimalMovementLine(event.directionalInput)
    }

    @Suppress("unused")
    private val movementInputHandler = handler<MovementInputEvent>(
        priority = EventPriorityConvention.SAFETY_FEATURE
    ) { event ->
        // 救援时不走 ledge 逻辑（已在坠落，ledge 无意义且可能干扰）
        if (ScaffoldFallRescueFeature.isRescuing) return@handler

        if (forceSneak > 0) {
            event.sneak = true
            forceSneak--
        }
        if (ledge) {
            val technique = activeTechnique
            val ledgeAction = ledge(
                this.currentTarget,
                RotationManager.currentRotation ?: player.rotation,
                technique as? ScaffoldLedgeExtension
            )
            if (ledgeAction.jump) event.jump = true
            if (ledgeAction.stopInput) event.directionalInput = DirectionalInput.NONE
            if (ledgeAction.stepBack) event.directionalInput = event.directionalInput.copy(
                forwards = false,
                backwards = true
            )
            if (ledgeAction.sneakTime > forceSneak) {
                event.sneak = true
                forceSneak = ledgeAction.sneakTime
            }
        }
    }

    @Suppress("unused")
    private val timerHandler = handler<GameTickEvent> {
        if (timer != 1f) {
            Timer.requestTimerSpeed(timer, Priority.IMPORTANT_FOR_USAGE_1, this@ModuleScaffold)
        }
    }

    @Suppress("unused")
    private val rotationUpdateHandler = handler<RotationUpdateEvent> {
        NoFallBlink.waitUntilGround = true

        val blockInHotbar = findBestValidHotbarSlotForTarget()
        val bestStack = if (blockInHotbar == null) {
            nextBlock = null
            ItemStack(Items.SANDSTONE, 64)
        } else {
            player.inventory.getItem(blockInHotbar).also {
                nextBlock = it.getBlock()
            }
        }

        val overriding = ScaffoldFallRescueFeature.shouldOverridePlacement

        val optimalLine = if (overriding) null else this.currentOptimalLine
        // ★ 救援时直接用真实玩家位置作为预测点，让 findPlacementTarget 以玩家脚下为中心搜索
        val predictedPos = if (overriding) {
            player.position()
        } else {
            ScaffoldMovementPrediction.getPredictedPlacementPos(optimalLine) ?: player.position()
        }

        val predictedPose = if (overriding) {
            Pose.STANDING
        } else if (ScaffoldEagleFeature.enabled && ScaffoldEagleFeature.shouldEagle(DirectionalInput(player.input))) {
            Pose.CROUCHING
        } else {
            Pose.STANDING
        }

        debugGeometry("predictedPos") {
            ModuleDebug.DebuggedPoint(predictedPos, Color4b.GREEN, size = 0.1)
        }

        val technique = activeTechnique
        val target = technique.findPlacementTarget(predictedPos, predictedPose, optimalLine, bestStack)
            .also { this.currentTarget = it }

        debugGeometry("lineToBlock") {
            val b = target?.placedBlock?.toVec3d(0.5, 1.0, 0.5) ?: return@debugGeometry null
            val a = optimalLine?.getNearestPointTo(b) ?: return@debugGeometry null
            ModuleDebug.DebuggedLineSegment(from = a, to = b, Color4b.RED)
        }

        if (rotationTiming == NORMAL) {
            val rotation = technique.getRotations(target)
            if (rotation != null) {
                RotationManager.setRotationTarget(
                    rotation,
                    considerInventory = considerInventory,
                    valueGroup = ScaffoldRotationValueGroup,
                    provider = this@ModuleScaffold,
                    priority = Priority.IMPORTANT_FOR_PLAYER_LIFE
                )
            }
        }
    }
}
