package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.ccbluex.liquidbounce.render.FontManager
import net.ccbluex.liquidbounce.render.drawRoundedRect
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.withPush
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.scaledDimension
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import kotlin.math.exp

//半成品ArrayList 26/10/18
object ModuleArrayList : ClientModule("ArrayList", ModuleCategories.RENDER, aliases = listOf("ArrList")) {

    private val background by boolean("Background", true)
    private val accentBar by boolean("AccentBar", false)
    private val lineHeight by float("Height", 11.5f, 9f..24f)
    private val textScale by float("TextScale", 0.27f, 0.18f..0.8f)
    private val rainbow by boolean("Rainbow", true)
    private val rainbowSpeed by float("RainbowSpeed", 3f, 1f..15f)

    private val fontRenderer
        get() = FontManager.fontFace("Inter Bold")?.renderer ?: FontManager.FONT_RENDERER

    private class Entry(val module: ClientModule) {
        var anim = 0f
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
                    it.cachedWidth = fontRenderer.getStringWidth(fontRenderer.process(Component.literal(it.module.name))) * textScale
                }
                it.cachedWidth
            }
    }

    private fun lerp(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).toInt()

    private fun palette(qRaw: Double): Triple<Int, Int, Int> {
        val q = ((qRaw % 1.0) + 1.0) % 1.0
        val t = q.toFloat()
        return when {
            q < 1.0 / 3.0 -> Triple(
                lerp(88, 245, t * 3f), lerp(204, 150, t * 3f), lerp(250, 200, t * 3f))
            q < 2.0 / 3.0 -> {
                val u = (t - 1f / 3f) * 3f
                Triple(lerp(245, 123, u), lerp(150, 44, u), lerp(200, 191, u))
            }
            else -> {
                val u = (t - 2f / 3f) * 3f
                Triple(lerp(123, 88, u), lerp(44, 204, u), lerp(191, 250, u))
            }
        }
    }

    private fun colored(text: String, rgb: Triple<Int, Int, Int>): MutableComponent {
        val c = (rgb.first shl 16) or (rgb.second shl 8) or rgb.third
        return Component.literal(text).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(c)))
    }

    private var lastFrameMs = 0L

    @Suppress("unused")
    private val renderHandler = handler<OverlayRenderEvent> { event ->
        if (entries.isEmpty()) return@handler

        val (scaledWidth, _) = mc.window.scaledDimension
        val timeSec = System.currentTimeMillis() / 1000.0
        val dt = ((timeSec - lastFrameMs / 1000.0).coerceIn(0.0, 0.1)).toFloat()
        lastFrameMs = System.currentTimeMillis()
        val animF = 1f - exp(-dt * 14f)

        var yOffset = 0f
        val total = entries.size

        with(event.context) {
            pose().withPush {
                for ((index, entry) in entries.withIndex()) {
                    val module = entry.module
                    entry.anim += ((if (module.enabled) 1f else 0f) - entry.anim) * animF
                    if (entry.anim <= 0.01f) continue

                    val q = timeSec / rainbowSpeed + (if (total <= 1) 0.0 else index / (total - 1.0)) * 0.66
                    val rgb = palette(q)

                    val label = if (rainbow) colored(module.name, rgb) else Component.literal(module.name)
                    val processed = fontRenderer.process(label)
                    val textWidth = fontRenderer.getStringWidth(processed) * textScale

                    val chipX = scaledWidth - textWidth - 10f
                    val chipY = 3f + yOffset
                    val chipW = textWidth + 8f
                    val chipH = lineHeight - 2.5f
                    val radius = chipH / 2f - 0.5f
                    val a = entry.anim

                    if (background) {
                        drawRoundedRect(
                            chipX - 0.8f, chipY - 0.8f, chipX + chipW + 0.8f, chipY + chipH + 0.8f, radius + 0.8f,
                            fillColor = Color4b(255, 255, 255, (40 * a).toInt().coerceIn(0, 255)),
                        )
                        drawRoundedRect(
                            chipX, chipY, chipX + chipW, chipY + chipH, radius,
                            fillColor = Color4b(20, 20, 35, (85 * a).toInt().coerceIn(0, 255)),
                        )
                    }

                    if (accentBar) {
                        drawRoundedRect(
                            scaledWidth - 2.5f, chipY, scaledWidth - 1f, chipY + chipH, 1f,
                            fillColor = Color4b(rgb.first, rgb.second, rgb.third, (230 * a).toInt()),
                        )
                    }

                    fontRenderer.draw(processed) {
                        x = chipX + 4f
                        y = chipY + (chipH - fontRenderer.height * textScale) / 2f
                        scale = textScale
                        shadow = true
                    }

                    yOffset += lineHeight * entry.anim
                }
            }
        }
    }
}
