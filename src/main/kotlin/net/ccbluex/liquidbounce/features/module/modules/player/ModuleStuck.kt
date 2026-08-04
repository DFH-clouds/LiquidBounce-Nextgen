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
package net.ccbluex.liquidbounce.features.module.modules.player

import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket

/**
 * 卡空
 */
object ModuleStuck : ClientModule("Stuck", ModuleCategories.PLAYER) {


    // 冻结玩家运动
    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (enabled) {
            player.deltaMovement = player.deltaMovement.multiply(0.0, 0.0, 0.0)
        }
    }

    // 数据包处理
    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        val packet = event.packet


        // 回弹自动禁用模块，避免严重漂移  当末影珍珠扔中目的地 则自动Disabled
        if (packet is ClientboundPlayerPositionPacket && enabled) {
            enabled = false
            // chat("Stuck disabled")
            return@handler
        }

    }
}
