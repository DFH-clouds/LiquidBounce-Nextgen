package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.ccbluex.liquidbounce.render.drawRoundedRect
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.withPush
import net.ccbluex.liquidbounce.utils.client.scaledDimension
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import kotlin.math.exp

//已完工 26/10/31
object ModuleArrayList : ClientModule("ArrayList", ModuleCategories.RENDER, aliases = listOf("ArrList")) {

    private val accentBar    by boolean("AccentBar", true)
    private val rainbow      by boolean("Rainbow", true)
    private val rainbowSpeed by float("RainbowSpeed", 3f, 1f..15f)

    private val posX by float("X", 0f, -500f..500f)
    private val posY by float("Y", 0f, -500f..500f)

    private val uiScale by float("Scale", 0.9f, 0.7f..1.2f)


    private class Entry(val module: ClientModule) {
        var anim = 0f
        var currentY = 0f
        var targetY = 0f
        var cachedWidth: Float = -1f
    }

    private var entries: List<Entry> = emptyList()

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        val enabled = ModuleManager.filter { it.enabled }.toHashSet()
        val previous = entries.associateBy { it.module }

        entries = ModuleManager
            .mapNotNull { m ->
                val e = previous[m]
                when {
                    m in enabled -> (e ?: Entry(m))
                    e != null && e.anim > 0.01f -> e
                    else -> null
                }
            }
            .sortedByDescending {
                if (it.cachedWidth < 0f) {
                    it.cachedWidth = mc.font.width(it.module.name).toFloat()
                }
                it.cachedWidth
            }
    }

    private fun lerp(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).toInt()

    private fun palette(qRaw: Double): Triple<Int, Int, Int> {
        val q = ((qRaw % 1.0) + 1.0) % 1.0
        val t = q.toFloat()
        return when {
            q < 1.0 / 3.0 -> Triple(lerp(60, 220, t * 3f), lerp(180, 120, t * 3f), lerp(230, 180, t * 3f))
            q < 2.0 / 3.0 -> {
                val u = (t - 1f / 3f) * 3f
                Triple(lerp(220, 100, u), lerp(120, 30, u), lerp(180, 170, u))
            }
            else -> {
                val u = (t - 2f / 3f) * 3f
                Triple(lerp(100, 60, u), lerp(30, 180, u), lerp(170, 230, u))
            }
        }
    }

    private fun colored(text: String, rgb: Triple<Int, Int, Int>): Component {
        val c = (rgb.first shl 16) or (rgb.second shl 8) or rgb.third
        return Component.literal(text).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(c)))
    }

    private var lastFrameMs = 0L

    @Suppress("unused")
    private val renderHandler = handler<OverlayRenderEvent> { event ->
        if (entries.isEmpty()) return@handler

        val (scaledWidthInt, _) = mc.window.scaledDimension
        val timeSec = System.currentTimeMillis() / 1000.0
        val dt = ((timeSec - lastFrameMs / 1000.0).coerceIn(0.0, 0.1)).toFloat()
        lastFrameMs = System.currentTimeMillis()
        val animF = 1f - exp(-dt * 14f)

        val total = entries.size
        var currentTargetY = 0f

        with(event.context) {
            pose().withPush {

                translate(posX, posY)

                scale(uiScale, uiScale)

                val virtualWidth = scaledWidthInt / uiScale
                val lineHeight = 10f

                for ((index, entry) in entries.withIndex()) {
                    val module = entry.module

                    entry.anim += ((if (module.enabled) 1f else 0f) - entry.anim) * animF
                    if (entry.anim <= 0.01f) continue

                    entry.targetY = currentTargetY
                    if (entry.currentY == 0f) entry.currentY = currentTargetY
                    entry.currentY += (entry.targetY - entry.currentY) * animF * 1.5f

                    currentTargetY += lineHeight * entry.anim

                    val q = timeSec / rainbowSpeed + (if (total <= 1) 0.0 else index / (total - 1.0)) * 0.66
                    val rgb = palette(q)

                    val label = if (rainbow) colored(module.name, rgb) else Component.literal(module.name)

                    val a = entry.anim
                    val chipH = lineHeight * a
                    val textWidth = mc.font.width(label).toFloat()

                    val chipRight = virtualWidth - 2f
                    val chipX = chipRight - textWidth
                    val chipY = entry.currentY

                    // 右侧霓虹细线
                    if (accentBar) {
                        drawRoundedRect(
                            virtualWidth - 1.2f - 1f, chipY + 1f, virtualWidth - 1f, chipY + chipH - 1f,
                            radius = 0f,
                            fillColor = Color4b(rgb.first, rgb.second, rgb.third, (255 * a).toInt())
                        )
                    }

                    val textX = chipX - 2f
                    val textY = chipY + (chipH - 8f) / 2f

                    // 计算 ARGB 颜色
                    val alphaInt = (a * 255).toInt().coerceIn(0, 255)
                    val colorInt = (alphaInt shl 24) or (rgb.first shl 16) or (rgb.second shl 8) or rgb.third

                    event.context.text(
                        mc.font,
                        label,
                        textX.toInt(),
                        textY.toInt(),
                        colorInt,
                        true
                    )
                }
            }
        }
    }
}
