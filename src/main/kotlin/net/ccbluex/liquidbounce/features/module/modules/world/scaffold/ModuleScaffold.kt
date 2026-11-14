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
 * 下面是修改的东西
 * 1. 原版 Scaffold 方块在背包里无法搭路：当物品栏没有方块时，自动与背包交换。2026/8/23
 * 2. 内置 FallRescue：坠落自救（方块自救 + Stuck 兜底自动集合），可自愈。26/11/5
 * 3.修复已知bug 26/11/8
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
import net.ccbluex.liquidbounce.features.module.modules.combat.velocity.mode.VelocityGrimFull
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

        private val minFallDistance by int("MinFallDistance", 2, 1..30, "blocks")
        private val stuckTriggerAfterTicks by int("StuckTriggerAfterTicks", 5, 1..40, "ticks")
        private val maxRescueTicks by int("MaxRescueTicks", 60, 20..400, "ticks")
        private val maxStuckActivations by int("MaxStuckActivations", 2, 1..5, "times")
        private val rescueCooldownTicks by int("RescueCooldown", 40, 10..200, "ticks")

        private var rescueActive = false
        private var rescueStartTick = 0L
        private var lastPlaceTick = -1L
        private var stuckActivated = false
        private var stuckWasEnabledByUs = false
        private var selfRescueTimedOut = false
        private var stuckActivations = 0
        private var rescueCooldownUntil = 0L

        val isRescuing: Boolean get() = enabled && rescueActive

        val shouldOverridePlacement: Boolean get() = isRescuing && !selfRescueTimedOut

        fun onTick() {
            val now = mc.level?.gameTime ?: 0L

            if (!enabled) {
                if (rescueActive) reset(applyCooldown = false)
                return
            }

            if (!rescueActive) {
                if (now < rescueCooldownUntil) return
                if (checkShouldRescue()) startRescue() else return
            }

            val elapsed = now - rescueStartTick

            if (isSafe()) {
                reset(applyCooldown = false)
                return
            }

            if (!stuckActivated && stuckActivations < maxStuckActivations) {
                val ref = if (lastPlaceTick >= 0) lastPlaceTick else rescueStartTick
                if (now - ref >= stuckTriggerAfterTicks) {
                    activateStuck()
                }
            }

            if (elapsed > maxRescueTicks && !selfRescueTimedOut) {
                if (!stuckActivated && stuckActivations < maxStuckActivations) {
                    activateStuck()
                }
                if (stuckActivated || ModuleStuck.enabled) {
                    selfRescueTimedOut = true
                    logger.info("[Scaffold] Fall rescue timed out, Stuck takes over")
                } else {
                    logger.info("[Scaffold] Fall rescue timed out but Stuck unavailable, extending rescue")
                    rescueStartTick = now
                    lastPlaceTick = -1L
                }
            }

            if ((stuckActivated || selfRescueTimedOut) && !ModuleStuck.enabled) {
                stuckActivated = false
                stuckWasEnabledByUs = false
                if (!isSafe()) {
                    selfRescueTimedOut = false
                    rescueStartTick = now
                    lastPlaceTick = -1L
                    logger.info("[Scaffold] Fall rescue: Stuck stopped, resuming Scaffold coverage")
                }
            }
        }

        private fun isSafe(): Boolean =
            player.onGround() ||
                player.abilities.flying ||
                player.isFallFlying ||
                player.isInWater || player.isInLava || player.isInPowderSnow ||
                player.isSpectator

        fun onBlockPlaced() {
            if (rescueActive) lastPlaceTick = mc.level?.gameTime ?: 0L
        }

        fun getRescueTargetPos(): BlockPos? {
            if (!shouldOverridePlacement) return null
            val below = player.blockPosition().below()
            return if (world.getBlockState(below).isAir) below else null
        }

        private fun checkShouldRescue(): Boolean {
            if (isSafe()) return false
            if (player.fallDistance < minFallDistance) return false
            return player.deltaMovement.y < -0.1
        }

        private fun startRescue() {
            rescueActive = true
            rescueStartTick = mc.level?.gameTime ?: 0L
            lastPlaceTick = -1L
            stuckActivated = false
            stuckWasEnabledByUs = false
            selfRescueTimedOut = false
            stuckActivations = 0
            logger.info("[Scaffold] Fall rescue activated (fall=${player.fallDistance.toInt()})")
        }

        private fun activateStuck() {
            if (stuckActivations >= maxStuckActivations) return

            val alreadyEnabled = ModuleStuck.enabled
            stuckActivated = true

            if (alreadyEnabled) {
                logger.info("[Scaffold] Fall rescue -> using existing ModuleStuck")
                return
            }

            try {
                ModuleStuck.enabled = true
                stuckActivations++
                stuckWasEnabledByUs = true
                logger.info(
                    "[Scaffold] Fall rescue -> ModuleStuck activated " +
                        "($stuckActivations/$maxStuckActivations)"
                )
            } catch (e: Exception) {
                stuckActivated = false
                stuckWasEnabledByUs = false
                logger.error("[Scaffold] Failed to activate ModuleStuck", e)
            }
        }

        fun reset(disableStuck: Boolean = true, applyCooldown: Boolean = true) {
            if (rescueActive) {
                if (disableStuck && stuckWasEnabledByUs) {
                    try { ModuleStuck.forceStop() } catch (_: Exception) {}
                }
                logger.info(
                    "[Scaffold] Fall rescue ended (placed=${lastPlaceTick >= 0}, " +
                        "stuck=$stuckActivated, timedOut=$selfRescueTimedOut)"
                )
            }

            rescueActive = false
            rescueStartTick = 0L
            lastPlaceTick = -1L
            stuckActivated = false
            stuckWasEnabledByUs = false
            selfRescueTimedOut = false

            if (applyCooldown) {
                rescueCooldownUntil = (mc.level?.gameTime ?: 0L) + rescueCooldownTicks
            } else {
                rescueCooldownUntil = 0L
            }
        }

        fun hardReset() {
            reset(applyCooldown = false)
            stuckActivations = 0
            rescueCooldownUntil = 0L
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

    private var originalSlot = -1
    private var bigVelocityTick = 0

    private var swapReflectionFailed = false

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
        swapReflectionFailed = false
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
        ScaffoldFallRescueFeature.hardReset()
        if (switchBack && originalSlot != -1 && originalSlot != player.inventory.selectedSlot) {
            SilentHotbar.selectSlotSilently(this, originalSlot, 0)
        }
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> { reset() }

    private fun updateRenderCount(count: Int?) {
        EventManager.callEvent(BlockCountChangeEvent(nextBlock, count))
    }

    private fun isVelocityHandlingKnockback(): Boolean =
        VelocityGrimFull.isActive()

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

    @Suppress("unused")
    private val moveInputHandler = handler<MovementInputEvent>(priority = EventPriorityConvention.SAFETY_FEATURE) { event ->
        if (ScaffoldFallRescueFeature.isRescuing) {
            event.directionalInput = DirectionalInput.NONE
            event.sneak = true
            return@handler
        }

        if (fullSprint && player.moving) {
            event.sneak = false
        }
        if (bedWars) {
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

        if (ScaffoldFallRescueFeature.isRescuing && isVelocityHandlingKnockback()) {
            return@tickHandler
        }

        if (bigVelocityTick > 0 && !ScaffoldFallRescueFeature.isRescuing) return@tickHandler

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

    private fun findBestBlockStackForTarget(): ItemStack? {
        val hotbarSlot = findBestValidHotbarSlotForTarget()
        if (hotbarSlot != null) return player.inventory.getItem(hotbarSlot)

        if (!ScaffoldFallRescueFeature.isRescuing) return null

        val inventorySlots = findAllPlaceableSlots().filter { (index, _) -> index in 9..35 }
        if (inventorySlots.isEmpty()) return null

        val doNotUseBelowCount = ScaffoldAutoBlockFeature.doNotUseBelowCount
        return (inventorySlots
            .filter { (_, stack) -> stack.count > doNotUseBelowCount }
            .maxWithOrNull { o1, o2 -> BLOCK_COMPARATOR_FOR_INVENTORY.compare(o1.value, o2.value) }
            ?: inventorySlots.maxWithOrNull { o1, o2 -> BLOCK_COMPARATOR_FOR_INVENTORY.compare(o1.value, o2.value) }
            )?.value
    }

    private fun moveInventoryBlockToHotbar(): Int? {
        if (swapReflectionFailed) return null

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
            } ?: run {
                swapReflectionFailed = true
                logger.error("Failed to locate GameMode click method via reflection (disabled until module reload)")
                return null
            }

            val clickTypeClass = clickMethod.parameterTypes[3]
            val swap = clickTypeClass.enumConstants.firstOrNull {
                (it as Enum<*>).name == "SWAP"
            } ?: run {
                swapReflectionFailed = true
                logger.error("Failed to locate SWAP enum via reflection (disabled until module reload)")
                return null
            }

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
            swapReflectionFailed = true
            logger.error("Failed to swap inventory slot via reflection (disabled until module reload)", e)
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
        val allowInventorySwap = ScaffoldAutoBlockFeature.enabled || ScaffoldFallRescueFeature.isRescuing

        if (allowInventorySwap && !hasBlockInMainHand && !hasBlockInOffHand) {
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

        val bestStack = findBestBlockStackForTarget()?.also {
            nextBlock = it.getBlock()
        } ?: run {
            nextBlock = null
            ItemStack(Items.SANDSTONE, 64)
        }

        val overriding = ScaffoldFallRescueFeature.shouldOverridePlacement

        val optimalLine = if (overriding) null else this.currentOptimalLine
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
