/*
 * LiquidBounce NextGen 0.38.0 - Velocity GrimFull
 * 拉弓包 / NoSlow 减速预测思路，适配 MC 26.1.2 Mojang 官方映射
 *
 * 与 VelocityBow 逻辑一致，作为独立 mode 暴露；isActive() 供 Scaffold 检测包序列冲突。
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.velocity.mode

import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.sequenceHandler
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ClientboundExplodePacket
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.InteractionHand
//经测试 目前无法使用 26/11/8
internal object VelocityGrimFull : VelocityMode("GrimFull") {

    private var pendingBow = false
    private var bowTicks = 0
    private val BOW_DURATION_TICKS = 1

    override fun enable() {
        pendingBow = false
        bowTicks = 0
    }

    override fun disable() {
        pendingBow = false
        bowTicks = 0
    }

    fun isActive(): Boolean = pendingBow

    @Suppress("unused")
    private val packetHandler = sequenceHandler<PacketEvent> { event ->
        val packet = event.packet

        if (packet is ClientboundSetEntityMotionPacket || packet is ClientboundExplodePacket) {
            pendingBow = true
            bowTicks = 0
            return@sequenceHandler
        }

        if (pendingBow && packet is ServerboundMovePlayerPacket) {
            bowTicks++
            if (bowTicks <= BOW_DURATION_TICKS) {
                sendUseItem()
            } else {
                sendStopUseItem()
                pendingBow = false
                bowTicks = 0
            }
        }
    }

    private fun sendUseItem() {
        mc.connection?.send(
            ServerboundUseItemPacket(
                InteractionHand.MAIN_HAND,
                0,
                mc.player?.yRot ?: 0f,
                mc.player?.xRot ?: 0f
            )
        )
    }

    private fun sendStopUseItem() {
        mc.connection?.send(
            ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
                BlockPos.ZERO,
                Direction.DOWN,
                0
            )
        )
    }
}
