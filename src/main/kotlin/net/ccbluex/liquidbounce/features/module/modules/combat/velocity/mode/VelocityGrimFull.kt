/*
 * LiquidBounce NextGen 0.30 - Velocity GrimFull
 * 有用 但还能进一步优化
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.velocity.mode

import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.sequenceHandler
import java.lang.reflect.Field
import kotlin.math.abs

internal object VelocityGrimFull : VelocityMode("GrimFull") {

    private var waitForUpdate = false
    private var lastVelocityTime = 0L
    private val TIMEOUT = 150L // 毫秒


    private var xField: Field? = null
    private var yField: Field? = null
    private var zField: Field? = null

    //别管下面这俩
    override fun enable() {
        waitForUpdate = false
        lastVelocityTime = 0L
    }

    override fun disable() {

    }

    @Suppress("unused")
    private val packetHandler = sequenceHandler<PacketEvent> { event ->
        val packet = event.packet
        val className = packet.javaClass.simpleName

        //  拦截击退包
        if (className.contains("EntityVelocityUpdate") ||
            (className.contains("Velocity") && className.contains("S2C")) ||
            (className.contains("Explosion") && className.contains("S2C"))) {
            event.cancelEvent()
            waitForUpdate = true
            lastVelocityTime = System.currentTimeMillis()
            return@sequenceHandler
        }

        //  拦截low玩家移动包
        if (className.contains("PlayerMove") && className.contains("C2S") && waitForUpdate) {
            if (System.currentTimeMillis() - lastVelocityTime > TIMEOUT) {
                waitForUpdate = false
                return@sequenceHandler
            }

            //检查位置是否同步
            if (hasPositionChanged(packet)) {
                event.cancelEvent()          // 取消该移动包，阻止GrimAC验证
                waitForUpdate = false        // 仅取消一次，避免影响后续移动
                return@sequenceHandler
            }

        }
    }

    /**
     * 使用反射读取包内坐标，与玩家真实位置比较。
     *
     */
    private fun hasPositionChanged(packet: Any): Boolean {
        try {
            val clazz = packet.javaClass
            if (xField == null || yField == null || zField == null) {
                xField = clazz.getDeclaredField("x").apply { isAccessible = true }
                yField = clazz.getDeclaredField("y").apply { isAccessible = true }
                zField = clazz.getDeclaredField("z").apply { isAccessible = true }
            }


            val packetX = (xField?.get(packet) as? Double) ?: return true
            val packetY = (yField?.get(packet) as? Double) ?: return true
            val packetZ = (zField?.get(packet) as? Double) ?: return true


            val player = mc.player ?: return true

            val dx = abs(packetX - player.x!!)
            val dy = abs(packetY - player.y!!)
            val dz = abs(packetZ - player.z!!)

            return dx > 1e-4 || dy > 1e-4 || dz > 1e-4
        } catch (e: Exception) {

            return true
        }
    }
}
