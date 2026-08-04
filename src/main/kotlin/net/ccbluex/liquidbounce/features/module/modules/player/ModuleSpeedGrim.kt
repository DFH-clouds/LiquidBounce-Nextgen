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

import net.ccbluex.liquidbounce.event.events.PlayerMovementTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.phys.AABB

/**
 * 利用实体碰撞加速，绕过Grim反作弊检测
 */
internal object ModuleSpeedGrim : ClientModule("Grim", ModuleCategories.PLAYER) {

    val boostMultiplier by float("BoostMultiplier", 1.2f, 1.0f..2.0f)
    val searchRadius by float("SearchRadius", 3.0f, 1.0f..6.0f)
    val maxBoosts by int("MaxBoosts", 3, 1..10)

    private var boostCount = 0
    private var lastBoostTick = 0
    private var collidedEntity: net.minecraft.world.entity.Entity? = null

    @Suppress("unused")
    private val movementHandler = handler<PlayerMovementTickEvent> { event ->
        val player = player
        val world = world

        // 检查玩家是否在移动（通过输入按键判断）
        val isMoving = player.input.keyPresses.forward ||
            player.input.keyPresses.backward ||
            player.input.keyPresses.left ||
            player.input.keyPresses.right
        if (!isMoving) return@handler

        // 扫描附近实体
        val entities = world.getEntities(
            player,
            AABB(
                player.x - searchRadius,
                player.y - searchRadius,
                player.z - searchRadius,
                player.x + searchRadius,
                player.y + searchRadius,
                player.z + searchRadius
            )
        ) { entity ->
            entity != player && entity !is ArmorStand && entity !is Animal && entity !is Monster
        }

        var hasCollision = false
        val playerBox = player.boundingBox
        for (entity in entities) {
            if (playerBox.intersects(entity.boundingBox)) {
                hasCollision = true
                collidedEntity = entity
                break
            }
        }

        // 没有碰撞则重置状态
        if (!hasCollision) {
            boostCount = 0
            lastBoostTick = 0
            collidedEntity = null
            return@handler
        }

        val currentTick = player.tickCount
        if (currentTick - lastBoostTick < 2) return@handler
        if (boostCount >= maxBoosts) {
            boostCount = 0 // 达到上限后重置，允许新一轮加速
            return@handler
        }

        val entity = collidedEntity ?: return@handler
        val dx = player.x - entity.x
        val dz = player.z - entity.z
        val distance = kotlin.math.sqrt(dx * dx + dz * dz)

        if (distance < 0.1) return@handler

        val normalizedDx = dx / distance
        val normalizedDz = dz / distance
        val boost = boostMultiplier * 0.1

        player.deltaMovement = player.deltaMovement.add(
            normalizedDx * boost,
            0.0,
            normalizedDz * boost
        )

        // 限制最大速度
        val maxSpeed = 0.42
        val currentSpeed = kotlin.math.sqrt(
            player.deltaMovement.x * player.deltaMovement.x +
                player.deltaMovement.z * player.deltaMovement.z
        )
        if (currentSpeed > maxSpeed) {
            val scale = maxSpeed / currentSpeed
            player.deltaMovement = player.deltaMovement.multiply(scale, 1.0, scale)
        }

        // 发送位置同步包，防止回弹
        val onGround = player.onGround()
        network.send(
            net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot(
                player.x, player.y, player.z,
                player.yRot, player.xRot,
                onGround, player.horizontalCollision
            )
        )

        boostCount++
        lastBoostTick = currentTick
    }
}
