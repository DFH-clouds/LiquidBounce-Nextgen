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
package net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.grim

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.events.PlayerMoveEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.ModuleSpeed
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Grim 1.20.1 实体加速
 *
 * 原理：当玩家碰撞箱与实体碰撞箱重叠时，利用实体推动机制
 * 获得额外的速度加成。Grim 允许合理的实体碰撞速度增量。
 */
class SpeedGrim (override val parent: ModeValueGroup<*>) : Mode("Grim") {

    /** 基础加速倍率 */
    val speedBoost by float("SpeedBoost", 1.8f, 1.0f..3.0f)

    /** 最大加速次数 per tick */
    val maxBoostsPerTick by int("MaxBoostsPerTick", 2, 1..5)

    /** 碰撞检测扩展范围 */
    val collisionExpand by float("CollisionExpand", 0.1f, 0.0f..0.5f)

    /** 是否只在地面时触发 */
    val onlyOnGround by boolean("OnlyOnGround", false)

    /** 是否只在 sprinting 时触发 */
    val onlyWhenSprinting by boolean("OnlyWhenSprinting", true)

    /** 减速因子（碰撞后逐渐恢复正常速度） */
    val deceleration by float("Deceleration", 0.6f, 0.1f..1.0f)

    /** 是否随机化加速幅度（防 pattern 检测） */
    val randomize by boolean("Randomize", true)

    /** 随机化范围 */
    val randomRange by float("RandomRange", 0.1f, 0.0f..0.3f)

    // ========== 状态 ==========

    private var remainingBoosts = 0
    private var currentMultiplier = 1.0
    private var lastCollidedEntity: Int = -1
    private var consecutiveCollisions = 0

    // ========== 事件处理器 ==========

    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> {
        val player = mc.player ?: return@handler

        if (!canActivate(player)) {
            reset()
            return@handler
        }

        val collidingEntity = findCollidingEntity(player) ?: run {
            applyDeceleration()
            return@handler
        }

        if (lastCollidedEntity != collidingEntity.id) {
            consecutiveCollisions = 0
            lastCollidedEntity = collidingEntity.id
        } else {
            consecutiveCollisions++
        }

        if (consecutiveCollisions >= maxBoostsPerTick) {
            applyDeceleration()
            return@handler
        }

        val baseMultiplier = speedBoost.toDouble()
        val finalMultiplier = if (randomize) {
            baseMultiplier + (Math.random() * randomRange * 2 - randomRange)
        } else {
            baseMultiplier
        }

        currentMultiplier = finalMultiplier.coerceIn(1.0, 2.5)
        remainingBoosts = maxBoostsPerTick - consecutiveCollisions
    }

    @Suppress("unused")
    private val moveHandler = handler<PlayerMoveEvent> { event ->
        if (currentMultiplier <= 1.0 || remainingBoosts <= 0) return@handler

        val player = mc.player ?: return@handler

        val motionX = event.movement.x
        val motionZ = event.movement.z

        val horizontalSpeed = sqrt(motionX * motionX + motionZ * motionZ)

        if (horizontalSpeed < 0.01) return@handler

        val boostMultiplier = currentMultiplier

        val newMotionX = motionX * boostMultiplier
        val newMotionZ = motionZ * boostMultiplier

        val deltaX = abs(newMotionX - motionX)
        val deltaZ = abs(newMotionZ - motionZ)
        val maxDelta = 0.35

        val clampedX = if (deltaX > maxDelta) {
            motionX + (newMotionX - motionX).coerceIn(-maxDelta, maxDelta)
        } else newMotionX

        val clampedZ = if (deltaZ > maxDelta) {
            motionZ + (newMotionZ - motionZ).coerceIn(-maxDelta, maxDelta)
        } else newMotionZ

        event.movement = Vec3(clampedX, event.movement.y, clampedZ)

        remainingBoosts--
        if (remainingBoosts <= 0) {
            currentMultiplier = 1.0
        }
    }

    // ========== 实体碰撞检测 ==========

    private fun findCollidingEntity(player: net.minecraft.client.player.LocalPlayer): Entity? {
        val world = mc.level ?: return null

        val expand = collisionExpand.toDouble()
        val playerBox = player.boundingBox.inflate(expand, 0.0, expand)

        val nearbyEntities = world.getEntities(player, playerBox.inflate(2.0)) { entity ->
            entity != player && entity.isAlive && entity.isPickable
        }

        return nearbyEntities.firstOrNull { entity ->
            val entityBox = entity.boundingBox
            playerBox.intersects(entityBox)
        }
    }

    // ========== 工具方法 ==========

    private fun canActivate(player: net.minecraft.client.player.LocalPlayer): Boolean {
        if (onlyOnGround && !player.onGround()) return false
        if (onlyWhenSprinting && !player.isSprinting) return false

        // 检查玩家是否在移动 - 使用速度判断
        val isMoving = player.deltaMovement.horizontalDistanceSqr() > 0.001

        if (!isMoving) return false
        if (player.isSpectator || player.isInWater || player.isInLava) return false

        return true
    }

    private fun applyDeceleration() {
        currentMultiplier = 1.0 + (currentMultiplier - 1.0) * deceleration
        if (currentMultiplier < 1.05) {
            currentMultiplier = 1.0
        }
        remainingBoosts = 0
        consecutiveCollisions = 0
        lastCollidedEntity = -1
    }

    private fun reset() {
        currentMultiplier = 1.0
        remainingBoosts = 0
        consecutiveCollisions = 0
        lastCollidedEntity = -1
    }

    override fun disable() {
        reset()
        super.disable()
    }
}
