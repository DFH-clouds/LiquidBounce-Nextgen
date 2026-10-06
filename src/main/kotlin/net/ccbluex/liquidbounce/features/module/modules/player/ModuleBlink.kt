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
package net.ccbluex.liquidbounce.features.module.modules.player

import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.events.BlinkPacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.client.player.RemotePlayer
import net.minecraft.network.protocol.game.*
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket
import net.minecraft.network.protocol.common.ServerboundPongPacket
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket
import net.minecraft.network.protocol.handshake.ClientIntentionPacket
import net.minecraft.network.protocol.login.ServerboundHelloPacket
import net.minecraft.network.protocol.login.ServerboundKeyPacket
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket
import net.minecraft.network.protocol.status.ServerboundStatusRequestPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import java.util.UUID

object ModuleBlink : ClientModule("Blink", ModuleCategories.PLAYER) {

    enum class Mode(override val tag: String) : Tagged {
        SMOOTH("Smooth"),
        BURST("Burst"),
        DAMAGE("Damage")
    }

    private val mode by enumChoice("Mode", Mode.SMOOTH)
    private val directions by multiEnumChoice("Directions", TransferOrigin.OUTGOING, canBeNone = false)
    private val maxTicks by int("MaxTicks", 100, 20..500)
    private val showFakePlayer by boolean("ShowFakePlayer", true)
    private val fakePlayerEquipment by boolean("FakePlayerEquipment", true)
    private val autoDisableOnWorldChange by boolean("AutoDisableOnWorldChange", true)
    private val smoothReleaseRate by int("SmoothReleaseRate", 2, 1..10)
    private val releaseOnDamage by int("ReleaseOnDamage", 20, 0..50)

    private var tickCounter = 0
    private var shouldReleaseTicks = 0
    private var isReleasing = false
    private var fakePlayer: RemotePlayer? = null
    private val fakePlayerUUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

    /**
     * 永不排队的数据包白名单。
     * 1.20.6 中部分包位于 common 包。
     */
    private val passthroughPackets: Set<Class<*>> = setOf(
        ServerboundKeepAlivePacket::class.java,
        ServerboundPongPacket::class.java,
        ServerboundAcceptTeleportationPacket::class.java,
        ServerboundChatPacket::class.java,
        ServerboundChatCommandPacket::class.java,
        ServerboundContainerClickPacket::class.java,
        ServerboundContainerClosePacket::class.java,
        ServerboundClientInformationPacket::class.java,
        ServerboundCustomPayloadPacket::class.java,
        ClientIntentionPacket::class.java,
        ServerboundStatusRequestPacket::class.java,
        ServerboundPingRequestPacket::class.java,
        ServerboundHelloPacket::class.java,
        ServerboundKeyPacket::class.java
    )

    override fun onEnabled() {
        val player = mc.player ?: run { enabled = false; return }
        tickCounter = 0
        shouldReleaseTicks = 0
        isReleasing = false
        if (showFakePlayer) spawnFakePlayer(player)
    }

    override fun onDisabled() {
        BlinkManager.flush(TransferOrigin.OUTGOING)
        cleanup()
        super.onDisabled()
    }

    // ==================== 供其他模块调用的公开 API ====================

    /**
     * 判断给定实体 ID 是否属于 Blink 生成的假玩家。
     *
     * 供 [ModuleLogoffSpot] 在实体移除事件中过滤假玩家，
     * 避免把 Blink 的假玩家误判为登出玩家而创建多余的克隆实体。
     *
     * @param entityId 来自 [WorldEntityRemoveEvent] 的实体 ID
     * @return 若模块正在运行且假玩家 ID 匹配则返回 true
     */
    fun isDummyPlayer(entityId: Int): Boolean {
        return this.running && fakePlayer?.id == entityId
    }

    // ==================== 数据包拦截 ====================

    @Suppress("unused")
    private val packetHandler = handler<BlinkPacketEvent> { event ->
        if (!directions.contains(event.origin)) return@handler

        val packet = event.packet ?: return@handler
        if (packet.javaClass in passthroughPackets) return@handler
        if (isReleasing) return@handler

        // 仅排队携带位置信息的移动包（Pos 或 PosRot）
        if (packet is ServerboundMovePlayerPacket.Pos ||
            packet is ServerboundMovePlayerPacket.PosRot) {
            event.action = BlinkManager.Action.QUEUE
        }
    }

    // ==================== Tick 处理 ====================

    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> {
        val player = mc.player ?: return@handler

        fakePlayer?.let { updateFakePlayerState(it, player) }

        if (player.hurtTime == 10) {
            shouldReleaseTicks += releaseOnDamage
        }

        val blinkTicks = getBlinkTicks()

        when (mode) {
            Mode.BURST -> {
                if (blinkTicks >= maxTicks || shouldReleaseTicks > 0) {
                    BlinkManager.flush(TransferOrigin.OUTGOING)
                    shouldReleaseTicks = 0
                    tickCounter = 0
                }
            }
            Mode.DAMAGE -> {
                if (shouldReleaseTicks > 0) {
                    BlinkManager.flush(TransferOrigin.OUTGOING)
                    shouldReleaseTicks = 0
                    tickCounter = 0
                }
            }
            Mode.SMOOTH -> {
                if (isReleasing) {
                    if (blinkTicks == 0) {
                        isReleasing = false
                    } else {
                        BlinkManager.flush(count = smoothReleaseRate)
                    }
                } else if (blinkTicks >= maxTicks || shouldReleaseTicks > 0) {
                    isReleasing = true
                    BlinkManager.flush(count = smoothReleaseRate)
                    shouldReleaseTicks = 0
                }
            }
        }
        tickCounter++
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        if (autoDisableOnWorldChange) enabled = false
    }

    // ==================== 辅助方法 ====================

    /**
     * 统计队列中携带位置信息的移动包数量。
     * 使用子类判断，避免访问不存在的 hasPos/hasPosition。
     */
    private fun getBlinkTicks(): Int {
        return BlinkManager.packetQueue.count { snapshot ->
            val packet = snapshot.packet
            packet is ServerboundMovePlayerPacket.Pos ||
                packet is ServerboundMovePlayerPacket.PosRot
        }
    }

    // ==================== 假玩家管理 ====================

    private fun spawnFakePlayer(player: net.minecraft.client.player.LocalPlayer) {
        val world = mc.level ?: return
        val fake = RemotePlayer(world, player.gameProfile).apply {
            setUUID(fakePlayerUUID)
            setPos(player.x, player.y, player.z)
            yHeadRot = player.yHeadRot
            yBodyRot = player.yBodyRot
            xRot = player.xRot
            yRot = player.yRot
            noPhysics = true
            if (fakePlayerEquipment) {
                EquipmentSlot.entries.forEach { slot ->
                    setItemSlot(slot, player.getItemBySlot(slot))
                }
            }
        }
        world.addEntity(fake)
        fakePlayer = fake
    }

    private fun updateFakePlayerState(fake: RemotePlayer, player: net.minecraft.client.player.LocalPlayer) {
        // BlinkManager.positions 自动从队列中的移动包提取位置
        val lastPos = BlinkManager.positions.lastOrNull() ?: return
        fake.setPos(lastPos.x, lastPos.y, lastPos.z)
        fake.yHeadRot = player.yHeadRot
        fake.yBodyRot = player.yBodyRot
        fake.xRot = player.xRot
        fake.yRot = player.yRot
        if (fakePlayerEquipment) {
            EquipmentSlot.entries.forEach { slot ->
                fake.setItemSlot(slot, player.getItemBySlot(slot))
            }
        }
    }

    private fun cleanup() {
        fakePlayer?.let {
            mc.level?.removeEntity(it.id, Entity.RemovalReason.DISCARDED)
        }
        fakePlayer = null
        isReleasing = false
        tickCounter = 0
        shouldReleaseTicks = 0
    }
}
