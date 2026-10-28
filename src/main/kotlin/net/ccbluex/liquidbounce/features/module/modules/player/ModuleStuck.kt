package net.ccbluex.liquidbounce.features.module.modules.player

import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ServerboundPongPacket
import net.minecraft.network.protocol.game.*
import net.minecraft.world.item.*
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue


//卡空 Fix 26/10/7
object ModuleStuck : ClientModule("Stuck", ModuleCategories.PLAYER, disableOnQuit = true) {


    private enum class StuckStage { IDLE, CAPTURED, ROTATION_SENT, EXITING }

    private var stage = StuckStage.IDLE
    private var capturedPacket: Packet<*>? = null
    private var tryDisable = false
    private var exitTicks = 0
    private var disablingInProgress = false

    private val pendingPongs: Queue<ServerboundPongPacket> = ConcurrentLinkedQueue()


    override fun onEnabled() {
        if (tryDisable) return

        stage = StuckStage.IDLE
        capturedPacket = null
        tryDisable = false
        exitTicks = 0
        pendingPongs.clear()
    }

    override fun onDisabled() {
        if (disablingInProgress) return

        if (stage != StuckStage.EXITING) {
            disablingInProgress = true
            tryDisable = true
            enabled = true
            disablingInProgress = false
        } else {
            stage = StuckStage.IDLE
            capturedPacket = null
            pendingPongs.clear()
        }
    }

    //冻结玩家运动

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (!running) return@tickHandler

        player.deltaMovement = player.deltaMovement.multiply(0.0, 0.0, 0.0)

        if (stage == StuckStage.EXITING) {
            exitTicks++
            if (exitTicks > 100) {
                forceExit()
            }
        }
    }


    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        val packet = event.packet

        when {
            packet is ServerboundMovePlayerPacket.Pos -> {
                event.cancelEvent()
            }

            packet is ServerboundMovePlayerPacket.PosRot -> {
                event.cancelEvent()
                sendRotation()
            }


            packet is ServerboundPongPacket -> {
                pendingPongs.offer(packet)
                event.cancelEvent()
            }

            packet is ServerboundUseItemPacket -> {
                val stack = player.getItemInHand(packet.hand)
                if (isInstantUseItem(stack)) {
                    sendRotation()
                    stage = StuckStage.ROTATION_SENT
                } else {
                    capturedPacket = packet
                    stage = StuckStage.CAPTURED
                    event.cancelEvent()
                }
            }

            packet is ServerboundPlayerActionPacket -> {
                if (packet.action == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
                    sendRotation()
                    stage = StuckStage.ROTATION_SENT
                } else {
                    capturedPacket = packet
                    stage = StuckStage.CAPTURED
                    event.cancelEvent()
                }
            }

            packet is ClientboundPlayerPositionPacket -> {
                if (stage == StuckStage.EXITING || stage == StuckStage.ROTATION_SENT) {
                    flushPendingPongs()
                    stage = StuckStage.EXITING
                    forceExit()
                }
            }
        }
    }


    @Suppress("unused")
    private val playerTickHandler = handler<PlayerTickEvent> { _ ->
        if (!running) return@handler
        if (stage != StuckStage.CAPTURED) return@handler

        sendRotation()
        flushPendingPongs()

        capturedPacket?.let { sendPacket(it) }
        capturedPacket = null
        stage = StuckStage.ROTATION_SENT

        if (tryDisable) {
            triggerExit()
        }
    }

    private fun triggerExit() {
        stage = StuckStage.EXITING
        exitTicks = 0

        sendPacket(
            ServerboundMovePlayerPacket.Pos(
                player.x + 1337.0, player.y, player.z + 1337.0,
                player.onGround(), player.horizontalCollision
            )
        )
        flushPendingPongs()
    }

    private fun forceExit() {
        flushPendingPongs()
        stage = StuckStage.EXITING
        enabled = false
    }


    private fun sendPacket(packet: Packet<*>) {
        player.connection.send(packet)
    }

    private fun sendRotation() {
        sendPacket(
            ServerboundMovePlayerPacket.Rot(
                player.yRot, player.xRot,
                player.onGround(), player.horizontalCollision
            )
        )
    }

    private fun flushPendingPongs() {
        while (pendingPongs.isNotEmpty()) {
            sendPacket(pendingPongs.poll())
        }
    }

    private fun isInstantUseItem(stack: ItemStack): Boolean {
        val item = stack.item
        return item is EnderpearlItem ||
            item is SnowballItem ||
            item is EggItem ||
            item is ThrowablePotionItem ||
            item is ExperienceBottleItem
    }


    @Suppress("unused")
    private val moveInputHandler = handler<MovementInputEvent> { event ->
        if (running) {
            event.directionalInput = DirectionalInput(
                forwards = false,
                backwards = false,
                left = false,
                right = false
            )
            event.jump = false
            event.sneak = false
        }
    }
}
