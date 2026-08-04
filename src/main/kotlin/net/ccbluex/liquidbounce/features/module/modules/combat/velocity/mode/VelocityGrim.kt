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
package net.ccbluex.liquidbounce.features.module.modules.combat.velocity.mode

import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.client.findEnemy
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.raytracing.findEntityInCrosshair
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundSwingPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import kotlin.math.sqrt

internal object VelocityGrim : VelocityMode("Grim") {

    val attackCount by int("AttackCount", 6, 1..10)
    val reduceFactor by float("ReduceFactor", 0.6f, 0.1f..1.0f)
    val threshold by float("Threshold", 0.12f, 0.0f..0.5f)
    val useKillAuraTarget by boolean("UseKillAuraTarget", true)
    val reduceClientVelocity by boolean("ReduceClientVelocity", false) // 可能被反作弊检测

    private var velocityTriggered = false
    private var attacked = false

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        val packet = event.packet
        if (packet !is ClientboundSetEntityMotionPacket) return@handler

        val player = mc.player ?: return@handler

        // 获取实体 ID（兼容反射）
        val entityId = getEntityId(packet)
        if (entityId != player.id) return@handler

        val velX = getVelocityComponent(packet, "xa", "velocityX") / 8000.0
        val velZ = getVelocityComponent(packet, "za", "velocityZ") / 8000.0
        val horizontalSpeed = sqrt(velX * velX + velZ * velZ)

        if (horizontalSpeed < threshold) return@handler

        velocityTriggered = true
        attacked = false
    }

    @Suppress("unused")
    private val rotationUpdateHandler = handler<RotationUpdateEvent> {
        val player = mc.player ?: return@handler

        if (!velocityTriggered || attacked) return@handler
        if (player.hurtTime <= 0) return@handler

        val target = findTarget() ?: run {
            velocityTriggered = false
            return@handler
        }

        repeat(attackCount) {
            player.connection.send(ServerboundSwingPacket(InteractionHand.MAIN_HAND))
            player.connection.send(createAttackPacket(target, player.isShiftKeyDown))
        }

        // 发送 onGround=true 的位置包（补全 horizontalCollision=false）
        player.connection.send(
            ServerboundMovePlayerPacket.PosRot(
                player.x, player.y, player.z,
                player.yRot, player.xRot,
                true, false
            )
        )

        if (reduceClientVelocity) {
            val factor = reduceFactor.toDouble()
            player.setDeltaMovement(
                player.deltaMovement.x * factor,
                player.deltaMovement.y,
                player.deltaMovement.z * factor
            )
        }

        attacked = true
        velocityTriggered = false
    }

    private fun findTarget(): Entity? {
        val player = mc.player ?: return null

        // 1. KillAura 目标
        if (useKillAuraTarget) {
            val kaTarget = ModuleKillAura.targetTracker.target
            if (kaTarget is LivingEntity && kaTarget.shouldBeAttacked()) {
                return kaTarget
            }
        }

        // 2. 准星实体（需传入玩家当前视角）
        val rotation = Rotation(player.yRot, player.xRot)
        val hitResult = findEntityInCrosshair(4.0, rotation) { it is LivingEntity && it.shouldBeAttacked() }
        if (hitResult != null) return hitResult.entity

        // 3. 周围最近敌人
        return findEnemy(4.0)?.takeIf { it.shouldBeAttacked() }
    }

    // 反射获取实体 ID
    private fun getEntityId(packet: ClientboundSetEntityMotionPacket): Int {
        return try {
            // 尝试直接调用 getId() 方法
            packet.javaClass.getMethod("getId").invoke(packet) as Int
        } catch (_: Exception) {
            // 回退到字段 entityId / id
            try {
                val field = packet.javaClass.getDeclaredField("entityId")
                field.isAccessible = true
                field.getInt(packet)
            } catch (_: NoSuchFieldException) {
                val field = packet.javaClass.getDeclaredField("id")
                field.isAccessible = true
                field.getInt(packet)
            }
        }
    }

    // 反射获取速度分量
    private fun getVelocityComponent(packet: ClientboundSetEntityMotionPacket, vararg fieldNames: String): Double {
        for (name in fieldNames) {
            try {
                // 尝试 getter 方法
                val method = packet.javaClass.getMethod("get${name.replaceFirstChar { it.uppercase() }}")
                return (method.invoke(packet) as Int).toDouble()
            } catch (_: Exception) { /* ignore */ }
            try {
                // 尝试字段直接访问
                val field = packet.javaClass.getDeclaredField(name)
                field.isAccessible = true
                return field.getInt(packet).toDouble()
            } catch (_: Exception) { /* ignore */ }
        }
        return 0.0
    }

    // 构造攻击包（反射兼容多种构造方式）
    private fun createAttackPacket(target: Entity, shifting: Boolean): ServerboundInteractPacket {
        return try {
            // 首选：ServerboundInteractPacket(Entity, boolean)
            val constructor = ServerboundInteractPacket::class.java.getDeclaredConstructor(Entity::class.java, Boolean::class.javaPrimitiveType)
            constructor.isAccessible = true
            constructor.newInstance(target, shifting)
        } catch (_: Exception) {
            try {
                // 备选：ServerboundInteractPacket(Entity, InteractionHand)
                val constructor = ServerboundInteractPacket::class.java.getDeclaredConstructor(Entity::class.java, InteractionHand::class.java)
                constructor.isAccessible = true
                constructor.newInstance(target, InteractionHand.MAIN_HAND)
            } catch (_: Exception) {
                // 最后手段：无参构造 + 设置字段
                val packet = ServerboundInteractPacket::class.java.newInstance()
                val targetIdField = ServerboundInteractPacket::class.java.getDeclaredField("targetId")
                targetIdField.isAccessible = true
                targetIdField.setInt(packet, target.id)

                // 设置 action = ATTACK
                try {
                    val actionField = ServerboundInteractPacket::class.java.getDeclaredField("action")
                    actionField.isAccessible = true
                    val actionClass = Class.forName("net.minecraft.network.protocol.game.ServerboundInteractPacket\$Action")
                    val attackValue = actionClass.enumConstants?.firstOrNull { it.toString() == "ATTACK" }
                    if (attackValue != null) {
                        actionField.set(packet, attackValue)
                    }
                } catch (_: Exception) {
                    // 无 action 时用 hand
                    val handField = ServerboundInteractPacket::class.java.getDeclaredField("hand")
                    handField.isAccessible = true
                    handField.set(packet, InteractionHand.MAIN_HAND)
                }
                packet
            }
        }
    }
}
