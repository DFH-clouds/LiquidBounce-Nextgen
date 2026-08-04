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

import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.client.player.RemotePlayer
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.*
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import java.util.*
import kotlin.math.abs

/**
 * 好像不能用
 */
object ModuleBlink : ClientModule("Blink", ModuleCategories.PLAYER) {

    enum class Mode(override val tag: String) : Tagged {
        SIMPLE("Simple"),
        GRIM("Grim"),
        SMOOTH("Smooth")
    }

    private val mode by enumChoice("Mode", Mode.GRIM)
    private val directions by multiEnumChoice("Directions", TransferOrigin.OUTGOING, canBeNone = false)
    private val maxTicks by int("MaxTicks", 100, 20..300)
    private val showFakePlayer by boolean("ShowFakePlayer", true)
    private val fakePlayerEquipment by boolean("FakePlayerEquipment", true)
    private val autoDisableOnDamage by boolean("AutoDisableOnDamage", true)
    private val autoDisableOnWorldChange by boolean("AutoDisableOnWorldChange", true)
    private val smoothReleaseRate by int("SmoothReleaseRate", 3, 1..20)
    private val teleportOnGround by boolean("TeleportOnGround", true)


    private val packetQueue = ArrayDeque<List<Packet<*>>>()
    private var currentTickPackets = mutableListOf<Packet<*>>()
    private var tickCounter = 0
    private var isReleasing = false
    private var lastSentPosition: Triple<Double, Double, Double>? = null
    private var lastSentRotation: Pair<Float, Float>? = null
    private var lastOnGround = true

    private var fakePlayer: RemotePlayer? = null
    private val fakePlayerUUID = UUID.fromString("00000000-0000-0000-0000-000000000001")


    private val passthroughPacketNames = listOf(
        "ServerboundKeepAlivePacket",
        "ServerboundPongPacket",
        "ServerboundAcceptTeleportationPacket",
        "ServerboundChatPacket",
        "ServerboundChatCommandPacket",
        "ServerboundContainerClickPacket",
        "ServerboundContainerButtonClickPacket",
        "ServerboundContainerClosePacket",
        "ServerboundClientInformationPacket",
        "ServerboundCustomPayloadPacket"
    )


    override fun onEnabled() {
        val player = mc.player ?: run {
            enabled = false
            return
        }

        packetQueue.clear()
        currentTickPackets = mutableListOf()
        tickCounter = 0
        isReleasing = false
        lastSentPosition = Triple(player.x, player.y, player.z)
        lastSentRotation = Pair(player.yRot, player.xRot)
        lastOnGround = player.onGround()

        if (showFakePlayer) {
            spawnFakePlayer(player)
        }
    }

    override fun onDisabled() {
        if (!isReleasing) {
            releaseAll()
        }
        cleanup()
        super.onDisabled()
    }


    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        if (event.isCancelled) return@handler
        if (!directions.contains(event.origin)) return@handler
        if (isReleasing) return@handler

        val packet = event.packet

        if (shouldPassthrough(packet)) {
            return@handler
        }

        if (!isClientGamePacket(packet)) {
            return@handler
        }

        event.cancelEvent()
        currentTickPackets.add(packet)
    }

    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> { event ->
        val player = mc.player ?: return@handler

        if (tickCounter >= maxTicks && !isReleasing) {
            when (mode) {
                Mode.SIMPLE, Mode.GRIM -> releaseAll()
                Mode.SMOOTH -> startSmoothRelease()
            }
            return@handler
        }

        tickCounter++

        if (currentTickPackets.isNotEmpty()) {
            packetQueue.add(currentTickPackets.toList())
            currentTickPackets = mutableListOf()
        }

        fakePlayer?.let { updateFakePlayerState(it, player) }

        if (isReleasing && mode == Mode.SMOOTH) {
            releaseSmoothTick()
        }
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        if (autoDisableOnWorldChange) {
            enabled = false
        }
    }


    private fun releaseAll() {
        if (packetQueue.isEmpty() && currentTickPackets.isEmpty()) return

        isReleasing = true
        val connection = mc.connection ?: return

        when (mode) {
            Mode.SIMPLE -> releaseTeleport()
            Mode.GRIM -> releaseGrimOptimized()
            Mode.SMOOTH -> startSmoothRelease()
        }

        if (mode != Mode.SMOOTH) {
            isReleasing = false
        }
    }

    private fun releaseTeleport() {
        val connection = mc.connection ?: return

        packetQueue.forEach { tickPackets ->
            tickPackets.forEach { packet ->
                if (packet !is ServerboundMovePlayerPacket) {
                    connection.send(packet)
                }
            }
        }

        val lastTick = packetQueue.lastOrNull() ?: currentTickPackets
        val lastMovePacket = lastTick.filterIsInstance<ServerboundMovePlayerPacket>().lastOrNull()

        if (lastMovePacket != null) {
            val teleportPacket = createFinalMovePacket(lastMovePacket)
            connection.send(teleportPacket)
        }

        lastTick.forEach { packet ->
            if (packet !is ServerboundMovePlayerPacket) {
                connection.send(packet)
            }
        }

        if (currentTickPackets.isNotEmpty()) {
            currentTickPackets.forEach { connection.send(it) }
        }
    }

    private fun releaseGrimOptimized() {
        val connection = mc.connection ?: return
        val player = mc.player ?: return

        val lastTickPackets = packetQueue.lastOrNull() ?: currentTickPackets
        val lastMovePacket = lastTickPackets.filterIsInstance<ServerboundMovePlayerPacket>().lastOrNull()

        if (lastMovePacket == null) {
            flushAllPackets()
            return
        }

        val targetX = when (lastMovePacket) {
            is ServerboundMovePlayerPacket.Pos -> lastMovePacket.x
            is ServerboundMovePlayerPacket.PosRot -> lastMovePacket.x
            else -> player.x
        }
        val targetY = when (lastMovePacket) {
            is ServerboundMovePlayerPacket.Pos -> lastMovePacket.y
            is ServerboundMovePlayerPacket.PosRot -> lastMovePacket.y
            else -> player.y
        }
        val targetZ = when (lastMovePacket) {
            is ServerboundMovePlayerPacket.Pos -> lastMovePacket.z
            is ServerboundMovePlayerPacket.PosRot -> lastMovePacket.z
            else -> player.z
        }

        packetQueue.forEach { tickPackets ->
            tickPackets.forEach { packet ->
                if (packet !is ServerboundMovePlayerPacket) {
                    connection.send(packet)
                }
            }
        }

        val finalPacket = ServerboundMovePlayerPacket.PosRot(
            targetX,
            targetY,
            targetZ,
            player.yRot,
            player.xRot,
            teleportOnGround,
            true
        )

        if (teleportOnGround) {
            val groundConfirm = ServerboundMovePlayerPacket.PosRot(
                targetX,
                targetY - 0.001,
                targetZ,
                player.yRot,
                player.xRot,
                true,
                true
            )
            connection.send(groundConfirm)
        }

        connection.send(finalPacket)

        lastTickPackets.forEach { packet ->
            if (packet !is ServerboundMovePlayerPacket) {
                connection.send(packet)
            }
        }

        currentTickPackets.forEach { connection.send(it) }
    }

    private fun startSmoothRelease() {
        isReleasing = true
    }

    private fun releaseSmoothTick() {
        if (packetQueue.isEmpty()) {
            isReleasing = false
            return
        }

        val connection = mc.connection ?: return
        var sent = 0

        while (packetQueue.isNotEmpty() && sent < smoothReleaseRate) {
            val tickPackets = packetQueue.removeFirst()
            tickPackets.forEach { connection.send(it) }
            sent++
        }

        if (packetQueue.isEmpty()) {
            isReleasing = false
        }
    }

    private fun flushAllPackets() {
        val connection = mc.connection ?: return

        packetQueue.forEach { tickPackets ->
            tickPackets.forEach { connection.send(it) }
        }
        currentTickPackets.forEach { connection.send(it) }
    }

    // ========== 假玩家管理 ==========

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
        if (fakePlayerEquipment) {
            EquipmentSlot.entries.forEach { slot ->
                val realItem = player.getItemBySlot(slot)
                val fakeItem = fake.getItemBySlot(slot)
                if (!ItemStack.matches(realItem, fakeItem)) {
                    fake.setItemSlot(slot, realItem.copy())
                }
            }
        }

        if (abs(fake.health - player.health) > 0.5f) {
            fake.health = player.health
        }

        if (player.swinging) {
            fake.swing(player.swingingArm ?: InteractionHand.MAIN_HAND)
        }

        fake.isSprinting = player.isSprinting
        fake.isShiftKeyDown = player.isShiftKeyDown
    }

    private fun cleanup() {
        fakePlayer?.let { fake ->
            mc.level?.let { world ->
                fake.remove(Entity.RemovalReason.DISCARDED)
            }
        }
        fakePlayer = null
        packetQueue.clear()
        currentTickPackets.clear()
    }


    private fun shouldPassthrough(packet: Packet<*>): Boolean {
        val className = packet.javaClass.simpleName
        return passthroughPacketNames.any { it == className }
    }

    private fun isClientGamePacket(packet: Packet<*>): Boolean {
        val className = packet.javaClass.name
        return className.startsWith("net.minecraft.network.protocol.game.Serverbound") ||
            className.startsWith("net.minecraft.network.packet.c2s.play.")
    }

    private fun createFinalMovePacket(lastPacket: ServerboundMovePlayerPacket): ServerboundMovePlayerPacket {
        return when (lastPacket) {
            is ServerboundMovePlayerPacket.Pos -> ServerboundMovePlayerPacket.Pos(
                lastPacket.x, lastPacket.y, lastPacket.z,
                teleportOnGround, true
            )
            is ServerboundMovePlayerPacket.PosRot -> ServerboundMovePlayerPacket.PosRot(
                lastPacket.x, lastPacket.y, lastPacket.z,
                lastPacket.yRot, lastPacket.xRot,
                teleportOnGround, true
            )
            is ServerboundMovePlayerPacket.Rot -> ServerboundMovePlayerPacket.Rot(
                lastPacket.yRot, lastPacket.xRot,
                teleportOnGround, true
            )
            is ServerboundMovePlayerPacket.StatusOnly -> ServerboundMovePlayerPacket.StatusOnly(
                teleportOnGround, true
            )
            else -> lastPacket
        }
    }

    fun isDummyPlayer(entityId: Int): Boolean {
        return fakePlayer?.id == entityId
    }

    fun getQueuedTicks(): Int = packetQueue.size
}
