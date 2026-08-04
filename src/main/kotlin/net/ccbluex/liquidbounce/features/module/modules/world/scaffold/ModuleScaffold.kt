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

/**
 *FIX  fix 来自soudide
 */
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

    private val activeTechnique get() = if (isTowering) {
        ScaffoldNormalTechnique
    } else {
        technique.activeMode
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

    init {
        tree(ScaffoldRotationValueGroup)
        tree(ScaffoldSprintControlFeature)
        tree(SimulatePlacementAttempts)
        tree(ScaffoldAccelerationFeature)
        tree(ScaffoldStrafeFeature)
        tree(ScaffoldJumpStrafe)
        tree(ScaffoldSpeedLimiterFeature)
        tree(ScaffoldBlinkFeature)
    }

    val autoSpeed by boolean("AutoSpeed", false)

    private var ledge by boolean("Ledge", true)

    private val renderer = tree(PlacementRenderer("Render", true, this, keep = false))

    private var placementY = 0
    private var forceSneak = 0
    private var startY = 0
    private var jumps = 0
    private var nextBlock: Block? = null

    // ---- 新状态（Southside 移植） ----
    private var originalSlot = -1
    private var bigVelocityTick = 0

    val blockCount: Int
        get() {
            fun ItemStack.blockCount() = if (isValidBlock(this)) this.count else 0
            return player.offhandItem.blockCount() + if (ScaffoldAutoBlockFeature.enabled) {
                findPlaceableSlots().sumOf { it.value.blockCount() }
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
        if (switchBack && originalSlot != -1 && originalSlot != player.inventory.selectedSlot) {
            SilentHotbar.selectSlotSilently(this, originalSlot, 0)
        }
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> { reset() }

    private fun updateRenderCount(count: Int?) {
        EventManager.callEvent(BlockCountChangeEvent(nextBlock, count))
    }

    // ---- 击退检测（使用反射兼容所有映射） ----
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

    // ---- 全速跑（修改输入） ----
    @Suppress("unused")
    private val moveInputHandler = handler<MovementInputEvent>(priority = EventPriorityConvention.SAFETY_FEATURE) { event ->
        if (fullSprint && player.moving) {
            // 全速跑：强制取消潜行，并确保冲刺（由 SprintEvent 控制）
            event.sneak = false
        }
        if (bedWars) {
            // BedWars 模式可在此扩展，例如调整放置时机
        }
    }

    // ---- 原有 tickHandler 修改 ----
    @Suppress("unused")
    private val tickHandler = tickHandler {
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

        if (!target.doesCrosshairTargetMatchRequirements(currentCrosshairTarget) ||
            !isValidCrosshairTarget(currentCrosshairTarget)
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
                    player.x, player.y, player.z, player.withFixedYaw(currentRotation), player.xRot, player.onGround(),
                    player.horizontalCollision
                )
            )
        }

        if (wasSuccessful) {
            ScaffoldMovementPrediction.onPlace(currentOptimalLine, previousFallOffPos)
            waitTicks(currentDelay)
        }
    }

    // ---- 以下为原有辅助方法，未改动 ----
    private fun findPlaceableSlots() = buildList(9) {
        for (i in 0..8) {
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

    // ---- Rotation 处理 ----
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

        val optimalLine = this.currentOptimalLine
        val predictedPos = ScaffoldMovementPrediction.getPredictedPlacementPos(optimalLine) ?: player.position()
        val predictedPose = if (ScaffoldEagleFeature.enabled && ScaffoldEagleFeature.shouldEagle(DirectionalInput(player.input))) {
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
