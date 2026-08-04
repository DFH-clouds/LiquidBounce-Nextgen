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
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundSwingPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import kotlin.math.sqrt

/**
 * Grim 1.20.1 Velocity Bypass
 *
 * 原理：利用 Grim 的 transaction 确认机制，在收到 SetEntityMotion 后
 * 通过发送攻击包触发 Grim 的 simulate 路径，配合 onGround=true 的位置包
 * 让服务端认为玩家处于地面状态，从而减少水平击退。
 */
internal object VelocityGrim726 : VelocityMode("Grim726") {

    /** 攻击次数 - Grim 1.20.1 建议 1-2 次，过多会触发攻击频率检测 */
    val attackCount by int("AttackCount", 1, 1..3)

    /** 水平速度减少因子 */
    val reduceFactor by float("ReduceFactor", 0.6f, 0.0f..1.0f)

    /** 触发阈值 - 低于此值的击退不处理 */
    val threshold by float("Threshold", 0.08f, 0.0f..0.5f)

    /** 使用 KillAura 目标 */
    val useKillAuraTarget by boolean("UseKillAuraTarget", true)

    /** 是否发送地面状态包 (关键 bypass) */
    val sendGroundPacket by boolean("SendGroundPacket", true)

    /** 是否在地面时才触发 */
    val onlyOnGround by boolean("OnlyOnGround", false)

    /** 延迟发送攻击包 (tick) - 用于 ping 补偿 */
    val attackDelay by int("AttackDelay", 0, 0..3)

    /** 目标搜索半径 */
    val targetRange by float("TargetRange", 3.0f, 1.0f..6.0f)

    // ========== 状态变量 ==========

    private var velocityTriggered = false
    private var pendingVelocityX = 0.0
    private var pendingVelocityZ = 0.0
    private var delayTicks = 0

    // ========== 事件处理器 ==========

    /**
     * 包处理器 - 捕获 SetEntityMotion
     */
    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        val packet = event.packet

        if (packet !is ClientboundSetEntityMotionPacket) return@handler

        val player = mc.player ?: return@handler

        // 检查是否是玩家自身的击退包
        if (packet.id != player.id) return@handler

        // 1.20.1 中使用反射获取速度字段（Mojang 映射下字段名可能不同）
        val velX = getPacketVelocityX(packet)
        val velY = getPacketVelocityY(packet)
        val velZ = getPacketVelocityZ(packet)

        // 计算水平速度
        val horizontalSpeed = sqrt(velX * velX + velZ * velZ)

        // 低于阈值不处理
        if (horizontalSpeed < threshold) return@handler

        // 检查地面条件
        if (onlyOnGround && !player.onGround()) return@handler

        // 保存速度数据
        pendingVelocityX = velX
        pendingVelocityZ = velZ
        velocityTriggered = true
        delayTicks = 0
    }

    /**
     * 每 tick 处理延迟攻击
     */
    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> { event ->
        if (!velocityTriggered) return@handler

        val player = mc.player ?: return@handler

        // 等待延迟
        if (delayTicks < attackDelay) {
            delayTicks++
            return@handler
        }

        // 检查是否仍在受伤状态
        if (player.hurtTime <= 0) {
            reset()
            return@handler
        }

        // 查找目标
        val target = findTarget() ?: run {
            reset()
            return@handler
        }

        // 执行 bypass
        performGrimBypass(player, target)

        reset()
    }

    // ========== 核心 Bypass 逻辑 ==========

    /**
     * 执行 Grim Bypass
     */
    private fun performGrimBypass(player: net.minecraft.client.player.LocalPlayer, target: Entity) {
        val connection = player.connection

        // 步骤 1: 发送挥动手臂包（增加真实性）
        connection.send(ServerboundSwingPacket(InteractionHand.MAIN_HAND))

        // 步骤 2: 发送攻击包
        repeat(attackCount.coerceAtMost(1)) {
            sendInteractPacket(connection, target)
        }

        // 步骤 3: 发送地面位置包（关键 bypass）
        if (sendGroundPacket) {
            val groundPacket = ServerboundMovePlayerPacket.PosRot(
                player.x,
                player.y,
                player.z,
                player.yRot,
                player.xRot,
                true,  // onGround = true - 这是关键
                true
            )
            connection.send(groundPacket)
        }

        // 步骤 4: 本地减少速度
        val factor = reduceFactor.toDouble()
        val currentMotion = player.deltaMovement
        player.setDeltaMovement(
            currentMotion.x * factor,
            currentMotion.y,
            currentMotion.z * factor
        )

        // 可选：如果 reduceFactor 为 0，完全重置水平速度
        if (reduceFactor <= 0.01f) {
            player.setDeltaMovement(0.0, currentMotion.y, 0.0)
        }
    }
    /**
     * 发送交互攻击包
     *
     * 1.20.1 中 ServerboundInteractPacket 的构造方式
     */
    private fun sendInteractPacket(
        connection: net.minecraft.client.multiplayer.ClientPacketListener,
        target: Entity
    ) {
        try {
            // 方式1: 尝试使用 createAttackPacket 工厂方法
            val method = ServerboundInteractPacket::class.java.getDeclaredMethod(
                "createAttackPacket",
                Entity::class.java,
                Boolean::class.java
            )
            val packet = method.invoke(null, target, player.isShiftKeyDown) as ServerboundInteractPacket
            connection.send(packet)
            return
        } catch (_: Exception) {}

        try {
            // 方式2: 尝试使用 createInteractionPacket 工厂方法
            val method = ServerboundInteractPacket::class.java.getDeclaredMethod(
                "createInteractionPacket",
                Entity::class.java,
                Boolean::class.java,
                InteractionHand::class.java
            )
            val packet = method.invoke(null, target, player.isShiftKeyDown, InteractionHand.MAIN_HAND) as ServerboundInteractPacket
            connection.send(packet)
            return
        } catch (_: Exception) {}

        try {
            // 方式3: 直接构造 - 1.20.1 构造参数: (entityId, isAttack, hand, hitVec, usingSecondaryAction)
            // 或者可能是: (entityId, action, hand, vec3)
            val constructor = ServerboundInteractPacket::class.java.getDeclaredConstructors().firstOrNull()
            if (constructor != null) {
                constructor.isAccessible = true
                // 尝试不同参数组合
                val packet = try {
                    // 尝试 (int, boolean, InteractionHand, Vec3)
                    constructor.newInstance(target.id, true, InteractionHand.MAIN_HAND, target.position())
                } catch (_: Exception) {
                    try {
                        // 尝试 (int, InteractionHand, Vec3)
                        constructor.newInstance(target.id, InteractionHand.MAIN_HAND, target.position())
                    } catch (_: Exception) {
                        try {
                            // 尝试 (Entity, boolean, InteractionHand)
                            constructor.newInstance(target, player.isShiftKeyDown, InteractionHand.MAIN_HAND)
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
                if (packet != null) {
                    connection.send(packet as ServerboundInteractPacket)
                    return
                }
            }
        } catch (_: Exception) {}

        try {
            // 方式4: 使用反射设置字段构造
            val packet = ServerboundInteractPacket::class.java.getDeclaredConstructor().newInstance()

            // 设置 entityId
            val entityIdField = ServerboundInteractPacket::class.java.getDeclaredField("entityId")
            entityIdField.isAccessible = true
            entityIdField.setInt(packet, target.id)

            // 设置 action
            val actionField = ServerboundInteractPacket::class.java.getDeclaredField("action")
            actionField.isAccessible = true
            val actionTypeClass = Class.forName("net.minecraft.network.protocol.game.ServerboundInteractPacket\$ActionType")
            val attackValue = actionTypeClass.enumConstants?.firstOrNull { it.toString().contains("ATTACK", true) }
            if (attackValue != null) {
                actionField.set(packet, attackValue)
            }

            connection.send(packet)
        } catch (_: Exception) {
            chat("§c[VelocityGrim726] Failed to create attack packet")
        }
    }

    // ========== 目标查找 ==========

    /**
     * 查找攻击目标
     */
    private fun findTarget(): Entity? {
        val player = mc.player ?: return null

        // 优先使用 KillAura 目标
        if (useKillAuraTarget) {
            val kaTarget = ModuleKillAura.targetTracker.target
            if (kaTarget is LivingEntity && kaTarget.shouldBeAttacked()) {
                if (player.distanceTo(kaTarget) <= targetRange) {
                    return kaTarget
                }
            }
        }

        // 备用：搜索附近实体
        val world = mc.level ?: return null
        val playerPos = player.position()

        return world.entitiesForRendering().filterIsInstance<LivingEntity>().filter {
            it.shouldBeAttacked() && it.distanceTo(player) <= targetRange
        }.minByOrNull { it.distanceTo(player) }
    }

    // ========== 反射获取速度 ==========

    private fun getPacketVelocityX(packet: ClientboundSetEntityMotionPacket): Double {
        return try {
            // 尝试字段名 xa
            val field = ClientboundSetEntityMotionPacket::class.java.getDeclaredField("xa")
            field.isAccessible = true
            field.getInt(packet).toDouble() / 8000.0
        } catch (_: Exception) {
            try {
                val field = ClientboundSetEntityMotionPacket::class.java.getDeclaredField("f_132611_")
                field.isAccessible = true
                field.getInt(packet).toDouble() / 8000.0
            } catch (_: Exception) {
                0.0
            }
        }
    }

    private fun getPacketVelocityY(packet: ClientboundSetEntityMotionPacket): Double {
        return try {
            val field = ClientboundSetEntityMotionPacket::class.java.getDeclaredField("ya")
            field.isAccessible = true
            field.getInt(packet).toDouble() / 8000.0
        } catch (_: Exception) {
            try {
                val field = ClientboundSetEntityMotionPacket::class.java.getDeclaredField("f_132612_")
                field.isAccessible = true
                field.getInt(packet).toDouble() / 8000.0
            } catch (_: Exception) {
                0.0
            }
        }
    }

    private fun getPacketVelocityZ(packet: ClientboundSetEntityMotionPacket): Double {
        return try {
            val field = ClientboundSetEntityMotionPacket::class.java.getDeclaredField("za")
            field.isAccessible = true
            field.getInt(packet).toDouble() / 8000.0
        } catch (_: Exception) {
            try {
                val field = ClientboundSetEntityMotionPacket::class.java.getDeclaredField("f_132613_")
                field.isAccessible = true
                field.getInt(packet).toDouble() / 8000.0
            } catch (_: Exception) {
                0.0
            }
        }
    }

    // ========== 工具方法 ==========

    private fun reset() {
        velocityTriggered = false
        pendingVelocityX = 0.0
        pendingVelocityZ = 0.0
        delayTicks = 0
    }

    override fun disable() {
        reset()
        super.disable()
    }
}
