package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.utils.client.chat
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket

/**
 *通过处理系统聊天框的击杀内容 判断玩家是否击杀人 然后调用原版chat进行提醒 26/8/27 By 花辞树
 */
object ModuleAutoL : ClientModule("AutoL", ModuleCategories.MISC) {

    val resetOnEnable by boolean("ResetOnEnable", true)
    val displayMessage by boolean("DisplayMessage", true)
    val displayTotalByDefault by boolean("DisplayTotalByDefault", true)

    private var killCount = 0

    @Suppress("unused")
    val packetHandler = handler<PacketEvent> { event ->
        val packet = event.packet
        // 处理系统聊天包
        if (packet !is ClientboundSystemChatPacket) return@handler

        val content = packet.content().string
        if (content.contains("你击败了") || content.contains("You defeated")) {
            // 增加计数
            killCount++

            if (displayMessage) {
                val msg = "§a[花辞树提醒您] §f你已击杀 §e$killCount §fLow IQ的主播"
                mc.execute {
                    chat(msg)
                }
            }
        }
    }

    override fun onEnabled() {
        if (resetOnEnable) {
            killCount = 0
            if (displayTotalByDefault) {
                chat("§a[AutoL] §f击杀计数器已重置")
            }
        }
    }

    fun resetCounter() {
        killCount = 0
        chat("§a[AutoL] §f击杀计数已重置")
    }

    fun showCount() {
        chat("§a[花辞树提醒您] §f当前击杀数: §e$killCount")
    }
}
