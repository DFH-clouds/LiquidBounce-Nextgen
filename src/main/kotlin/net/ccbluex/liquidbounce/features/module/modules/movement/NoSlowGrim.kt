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
package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.world.InteractionHand

/**
 * 专为 Grim 反作弊优化的 NoSlow 模块
 * 支持主手/副手使用物品，使用 Math.random() 实现随机延迟
 */
object MouduleNoslowGrim : ClientModule("NoSlowGrim", ModuleCategories.MOVEMENT) {

    // ---- 枚举实现 Tagged ----
    private enum class Mode(override val tag: String) : Tagged {
        SWITCH_ITEM("SwitchItem"),
        USE_ITEM("UseItem"),
        SWITCH_AND_USE("SwitchAndUse")
    }

    private val mode by enumChoice("Mode", Mode.SWITCH_AND_USE)

    // ---- 可调参数 ----
    private val onlyWhenUsing by boolean("OnlyWhenUsing", true)
    private val minDelay by int("MinDelay", 2, 1..10)
    private val maxDelay by int("MaxDelay", 4, 1..10)

    // ---- 内部状态 ----
    private var lastTriggerTick = 0
    private var oldSlot = -1

    // 反射获取 Inventory.selected 字段（兼容 Mojmap 和 Yarn）
    private val selectedField by lazy {
        val field = net.minecraft.world.entity.player.Inventory::class.java.getDeclaredField("selected")
        field.isAccessible = true
        field
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        val player = mc.player ?: return@handler
        val currentTick = player.tickCount

        // 条件：仅在玩家使用物品时触发
        if (onlyWhenUsing && !player.isUsingItem) {
            oldSlot = -1
            return@handler
        }

        // 使用 Math.random() 生成随机延迟（替代 kotlin.random.Random）
        val delayRange = maxDelay - minDelay + 1
        val randomDelay = minDelay + (Math.random() * delayRange).toInt()
        if (currentTick - lastTriggerTick < randomDelay) return@handler

        // 根据模式执行绕过
        when (mode) {
            Mode.SWITCH_ITEM -> performSwitchItem()
            Mode.USE_ITEM -> performUseItem()
            Mode.SWITCH_AND_USE -> {
                performSwitchItem()
                performUseItem()
            }
        }

        lastTriggerTick = currentTick
    }

    // ---- 绕过实现 ----

    /**
     * 方式一：快速切换主手槽位（经典方式）
     */
    private fun performSwitchItem() {
        val player = mc.player ?: return
        val currentSlot = selectedField.getInt(player.inventory)

        if (oldSlot == -1) {
            oldSlot = currentSlot
            return
        }

        val newSlot = if (oldSlot == 0) 1 else 0
        mc.connection?.send(ServerboundSetCarriedItemPacket(newSlot))
        mc.connection?.send(ServerboundSetCarriedItemPacket(oldSlot))
        oldSlot = -1
    }

    /**
     * 方式二：发送使用物品 + 立即释放（适用于副手）
     */
    private fun performUseItem() {
        val player = mc.player ?: return

        // 确定使用的手
        val hand = if (player.isUsingItem) {
            try {
                val handField = player.javaClass.getDeclaredField("usingItemHand")
                handField.isAccessible = true
                handField.get(player) as InteractionHand
            } catch (_: Exception) {
                InteractionHand.MAIN_HAND
            }
        } else {
            InteractionHand.MAIN_HAND
        }

        // 发送 ServerboundUseItemPacket（通过反射构造）
        try {
            val packetClass = Class.forName("net.minecraft.network.protocol.game.ServerboundUseItemPacket")
            val constructor = packetClass.getConstructor(InteractionHand::class.java)
            val usePacket = constructor.newInstance(hand)
            mc.connection?.send(usePacket as net.minecraft.network.protocol.Packet<*>)
        } catch (_: Exception) {
            try {
                val packetClassYarn = Class.forName("net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket")
                val constructorYarn = packetClassYarn.getConstructor(InteractionHand::class.java)
                val usePacketYarn = constructorYarn.newInstance(hand)
                mc.connection?.send(usePacketYarn as net.minecraft.network.protocol.Packet<*>)
            } catch (_: Exception) {
                // 静默失败
            }
        }

        // 发送 RELEASE_USE_ITEM 动作
        mc.connection?.send(ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
            BlockPos.ZERO,
            Direction.DOWN
        ))
    }

    override fun onDisabled() {
        oldSlot = -1
        lastTriggerTick = 0
        super.onDisabled()
    }
}
