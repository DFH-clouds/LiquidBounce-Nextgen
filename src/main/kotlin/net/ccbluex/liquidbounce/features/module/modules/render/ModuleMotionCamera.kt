package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.event.events.PerspectiveEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.minecraft.client.CameraType
import net.minecraft.world.phys.Vec3

//运动相机 写完才发现原本就有这个模块.... 26/11/1
object ModuleMotionCamera : ClientModule(
    name = "MotionCamera",
    category = ModuleCategories.RENDER,
    disableOnQuit = true
) {

    private val smoothness by float("Smoothness", 0.05f, 0.01f..0.50f)

    private val affectFirstPerson by boolean("AffectFirstPerson", false)

    @Volatile
    var isThirdPerson: Boolean = false
        private set

    @Volatile
    private var smoothedPos: Vec3? = null

    private val perspectiveHandler = handler<PerspectiveEvent> { event ->
        isThirdPerson = event.perspective == CameraType.THIRD_PERSON_BACK ||
            event.perspective == CameraType.THIRD_PERSON_FRONT
    }

    fun smooth(rawTarget: Vec3, tickDelta: Float): Vec3 {
        if (!affectFirstPerson && !isThirdPerson) {
            smoothedPos = rawTarget
            return rawTarget
        }

        val last = smoothedPos
        if (last == null) {
            smoothedPos = rawTarget
            return rawTarget
        }

        val alpha = (smoothness * tickDelta * 20.0).coerceIn(0.001, 1.0)
        val newPos = last.add(rawTarget.subtract(last).scale(alpha))
        smoothedPos = newPos
        return newPos
    }

    fun reset() {
        smoothedPos = null
    }
}
