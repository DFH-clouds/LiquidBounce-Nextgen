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
import net.ccbluex.liquidbounce.event.handler

/**
 * Grim Noxz Velocity Mode
 * 别TM用 因为我写炸了
 *
 */
internal object VelocityGrimNoxz : VelocityMode("GrimNoxz") {


    val skipChance by float("SkipChance", 0.15f, 0.0f..1.0f)


    val maxConsecutive by int("MaxConsecutive", 3, 1..10)


    val onlyOnGround by boolean("OnlyOnGround", true)


    private var consecutiveMods = 0

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        val packet = event.packet
        val className = packet.javaClass.name

        // Check击退包
        if (className == "net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket" ||
            className == "net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket") {

            //获取实体
            val entityId = try {
                val field = packet.javaClass.getDeclaredField("entityId")
                field.isAccessible = true
                field.getInt(packet)
            } catch (_: NoSuchFieldException) {
                val field = packet.javaClass.getDeclaredField("id")
                field.isAccessible = true
                field.getInt(packet)
            }

            if (entityId != mc.player?.id) return@handler

            // 仅在地面生效
            if (onlyOnGround && !(mc.player?.onGround() ?: false)) {
                return@handler
            }

            // 随机放行
            if (Math.random() < skipChance) {
                consecutiveMods = 0  // 放行时重置计数
                return@handler
            }

            // 4. 连续修改限制
            if (consecutiveMods >= maxConsecutive) {
                consecutiveMods = 0
                return@handler
            }

            // 5. 修改数据包：将 X 和 Z 速度设为 0，保留 Y
            try {
                // 获取速度字段
                val xField = try {
                    packet.javaClass.getDeclaredField("xa")
                } catch (_: NoSuchFieldException) {
                    packet.javaClass.getDeclaredField("velocityX")
                }
                val yField = try {
                    packet.javaClass.getDeclaredField("ya")
                } catch (_: NoSuchFieldException) {
                    packet.javaClass.getDeclaredField("velocityY")
                }
                val zField = try {
                    packet.javaClass.getDeclaredField("za")
                } catch (_: NoSuchFieldException) {
                    packet.javaClass.getDeclaredField("velocityZ")
                }
                xField.isAccessible = true
                yField.isAccessible = true
                zField.isAccessible = true

                // 保留原始 Y 速度，将 X 和 Z 置零
                val originalY = yField.getInt(packet)
                xField.setInt(packet, 0)
                yField.setInt(packet, originalY)  // 保留垂直速度
                zField.setInt(packet, 0)

                consecutiveMods++
            } catch (e: Exception) {

                consecutiveMods = 0
            }
        }
    }
}
