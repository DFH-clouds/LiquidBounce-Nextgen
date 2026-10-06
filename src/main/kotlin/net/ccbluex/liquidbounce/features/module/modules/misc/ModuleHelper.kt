/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.module.modules.misc

import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.regular
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket

/**
 * BedWars Helper
 *
 * 自动计算并提醒钻石/绿宝石刷新波次的 BedWars 辅助模块。
 */
object ModuleHelper : ClientModule("Helper", ModuleCategories.MISC) {


    private val diamondInterval by int("DiamondInterval", 30, 10..120)
    private val emeraldInterval by int("EmeraldInterval", 60, 20..180)
    private val startMessage by text("StartMessage", "游戏准备开始")
    private val endMessage by text("EndMessage", "游戏结束")


    private var diamondTimer = 0
    private var emeraldTimer = 0

    private var diamondCount = 0
    private var emeraldCount = 0

    private var gameRunning = false


    override fun onEnabled() {
        resetAll()
    }

    override fun onDisabled() {
        resetAll()
    }

    private fun resetAll() {
        gameRunning = false
        diamondTimer = 0
        emeraldTimer = 0
        diamondCount = 0
        emeraldCount = 0
    }


    /**
     * 每 tick 累加计时器，到达间隔后提醒并重置。
     */
    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> {
        if (!gameRunning) return@handler

        diamondTimer++
        emeraldTimer++

        if (diamondTimer >= diamondInterval * 20) {
            diamondCount++
            chat(regular("第 $diamondCount 波钻石刷新"))
            diamondTimer = 0
        }

        if (emeraldTimer >= emeraldInterval * 20) {
            emeraldCount++
            chat(regular("第 $emeraldCount 波绿宝石刷新"))
            emeraldTimer = 0
        }
    }

    /**
     * 监听服务器系统聊天消息，检测游戏开始/结束。
     */
    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        if (event.origin != TransferOrigin.INCOMING) return@handler
        if (event.packet !is ClientboundSystemChatPacket) return@handler

        val text = event.packet.content.string

        if (text.contains(startMessage)) {
            resetAll()
            gameRunning = true
        } else if (text.contains(endMessage)) {
            gameRunning = false
        }
    }

    /**
     * 切换世界/服务器时重置状态。
     */
    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        resetAll()
    }
}
