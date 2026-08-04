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
package net.ccbluex.liquidbounce.utils.client

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/**
 * 客户端本地消息显示工具（完全基于反射，兼容所有映射版本）
 */
object ClientChat2 {

    /**
     * 向客户端聊天栏添加一条本地消息（仅自己可见，不会发送到服务器）
     * @param message 要显示的消息文本（支持 § 颜色代码）
     */
    @JvmStatic
    fun displayLocalMessage(message: String) {
        val mc = Minecraft.getInstance()
        try {
            // 1. 获取 Minecraft 的 gui 字段
            val guiField = mc.javaClass.getDeclaredField("gui")
            guiField.isAccessible = true
            val gui = guiField.get(mc)

            // 2. 获取 IngameGui 的 chat 字段
            val chatField = gui.javaClass.getDeclaredField("chat")
            chatField.isAccessible = true
            val chat = chatField.get(gui)

            // 3. 获取 Chat 的 addMessage 方法
            val addMessageMethod = chat.javaClass.getMethod("addMessage", Component::class.java)
            addMessageMethod.invoke(chat, Component.literal(message))
        } catch (e: Exception) {
            // 反射失败时输出到日志（避免游戏崩溃）
            e.printStackTrace()
            println("[ClientChat] 无法显示消息: $message")
        }
    }

    /**
     * 重载：支持 Component 对象
     */
    @JvmStatic
    fun displayLocalMessage(component: Component) {
        displayLocalMessage(component.string)
    }
}
