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
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.events.AttackEntityEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.minecraft.network.protocol.Packet
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import java.time.Instant

/**
 * AutoL – 自动击杀喊话模块（连杀计数）
 * 参考 Southside 客户端实现，支持延迟、自定义消息、连杀语音。
 */
object MouduleAutoL : ClientModule("AutoL", ModuleCategories.COMBAT) {

    // ---- 配置项 ----
    val sendToGlobal by boolean("SendToGlobal", true)
    val delay by int("Delay", 5, 0..50)
    val messageTemplate by text("MessageTemplate", "§a{player} §e已被击杀，当前连杀 §c{count}")
    val streakMessages by text("StreakMessages",
        "一破，卧龙出山|双连，一战成名|三连，举世皆惊|四连，天下无敌")
    val streakSuffix by text("StreakSuffix", "{count}连，诛天灭地")
    val ignoredServers by text("IgnoredServers", "mc.loyisa.cn")

    // ---- 内部状态 ----
    private var killCount = 0
    private var target: LivingEntity? = null
    private var ticks = 0

    @Suppress("unused")
    private val attackHandler = handler<AttackEntityEvent> { event ->
        val entity = event.entity
        if (entity is Player && entity != mc.player) {
            target = entity
            ticks = 0
        }
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        val currentTarget = target ?: return@handler

        if (currentTarget.isDeadOrDying) {
            // 击杀后等待延迟
            if (ticks < delay) {
                ticks++
                return@handler
            }

            killCount++
            val msg = buildMessage(currentTarget)
            if (sendToGlobal) {
                sendGlobalMessage(msg)
            }
            target = null
            ticks = 0
        } else if (!currentTarget.isAlive) {
            target = null
            ticks = 0
        }
    }

    /**
     * 构建消息内容
     */
    private fun buildMessage(target: LivingEntity): String {
        val playerName = target.name.string
        val count = killCount

        val streakList = streakMessages.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        val streakMsg = if (count <= streakList.size) {
            streakList[count - 1]
        } else {
            streakSuffix.replace("{count}", count.toString())
        }

        var msg = messageTemplate
            .replace("{player}", playerName)
            .replace("{count}", count.toString())
            .replace("{kills}", count.toString())
            .replace("{streak}", streakMsg)

        if (!msg.contains("{streak}")) {
            msg += " §7" + streakMsg
        }

        return msg.replace(Regex("\\{[^}]*\\}"), "").trim()
    }

    /**
     * 使用反射发送全局聊天消息（兼容所有映射）
     */
    private fun sendGlobalMessage(message: String) {
        val player = mc.player ?: return
        val server = mc.currentServer?.ip ?: ""
        if (ignoredServers.split(',').any { server.contains(it.trim()) }) {
            return
        }

        try {
            val packet = createChatPacket(message)
            if (packet != null) {
                player.connection.send(packet)
            } else {
                logger.error("无法创建聊天数据包，消息未发送")
            }
        } catch (e: Exception) {
            logger.error("发送聊天消息失败", e)
        }
    }

    private fun createChatPacket(message: String): Packet<*>? {
        val classNames = listOf(
            "net.minecraft.network.protocol.game.ServerboundChatPacket",
            "net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket"
        )
        for (className in classNames) {
            try {
                val clazz = Class.forName(className)
                try {
                    val constructor = clazz.getDeclaredConstructor(String::class.java)
                    constructor.isAccessible = true
                    return constructor.newInstance(message) as Packet<*>
                } catch (_: NoSuchMethodException) {
                    try {
                        val constructor = clazz.getDeclaredConstructor(String::class.java, Instant::class.java)
                        constructor.isAccessible = true
                        return constructor.newInstance(message, Instant.now()) as Packet<*>
                    } catch (_: NoSuchMethodException) {
                        // 继续
                    }
                }
            } catch (_: ClassNotFoundException) {
                // 继续
            }
        }
        return null
    }

    override fun onEnabled() {
        killCount = 0
        target = null
        ticks = 0
        super.onEnabled()
    }

    override fun onDisabled() {
        target = null
        ticks = 0
        super.onDisabled()
    }
}
