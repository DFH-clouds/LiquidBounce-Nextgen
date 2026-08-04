/*
 * Utility extensions for velocity handling
 */

package net.ccbluex.liquidbounce.utils.client

import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player

/**
 * Find the closest enemy player within range
 */
fun findEnemy(range: Double): Entity? {
    val player = mc.player ?: return null
    val level = mc.level ?: return null

    var closestEntity: Entity? = null
    var closestDistance = range * range

    // 使用 getEntitiesOfClass 获取所有玩家，避免映射问题
    val players = level.getEntitiesOfClass(Player::class.java, player.boundingBox.inflate(range, range, range))
    players.forEach { entity ->
        if (entity === player) return@forEach
        if (!entity.isAlive) return@forEach

        val distanceSq = player.distanceToSqr(entity)
        if (distanceSq < closestDistance) {
            closestDistance = distanceSq
            closestEntity = entity
        }
    }

    return closestEntity
}
