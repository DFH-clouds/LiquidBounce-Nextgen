/*
 * 1. 重构自定义图片加载逻辑，支持打包内置和外部热替换：
 * 2. 新增图像调整参数（ImageScale, OffsetX, OffsetY）：
 * 3. 图片渲染优化
 * 4. 兜底策略
 * =============================================================================
 */
package net.ccbluex.liquidbounce.features.module.modules.render

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTextureView
import it.unimi.dsi.fastutil.floats.Float2IntFunction
import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.render.FontManager
import net.ccbluex.liquidbounce.render.drawCircle
import net.ccbluex.liquidbounce.render.drawQuad
import net.ccbluex.liquidbounce.render.drawRoundedRect
import net.ccbluex.liquidbounce.render.drawTexQuad
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.withPush
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.entity.RenderedEntities
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.TextColor
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.LivingEntity
import java.io.File
import java.nio.file.Files
import java.util.function.Supplier
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

object ModuleTargetHud : ClientModule("TargetHUD", ModuleCategories.RENDER, aliases = listOf("TargetHUD")) {

    private val range by float("Range", 6f, 2f..12f)
    private val posX by float("X", 490f, -500f..500f)
    private val posY by float("Y", 205f, -500f..500f)
    private val textScale by float("TextScale", 0.20f, 0.2f..0.5f)
    private val showHead by boolean("ShowHead", true)

    private val imageScale by float("ImageScale", 1.0f, 0.5f..3.0f)
    private val offsetX by float("OffsetX", 0.0f, -1.0f..1.0f)
    private val offsetY by float("OffsetY", 0.0f, -1.0f..1.0f)

    private val fontRenderer
        get() = FontManager.fontFace("Inter Bold")?.renderer ?: FontManager.FONT_RENDERER

    private var target: LivingEntity? = null
    private var lastTarget: LivingEntity? = null
    private var animatedHealth = 0f
    private var animatedAbsorption = 0f
    private var openAnim = 0f
    private var lastFrameMs = 0L

    private var customAvatarId: Identifier? = null
    private var customSampler: GpuSampler? = null
    private var loadFailed = false

    override fun onEnabled() {
        RenderedEntities.subscribe(this)
        loadCustomAvatar()
        super.onEnabled()
    }

    override fun onDisabled() {
        RenderedEntities.unsubscribe(this)
        target = null
        customAvatarId = null
        customSampler = null
        loadFailed = false
        super.onDisabled()
    }

    private fun loadCustomAvatar() {
        if (customAvatarId != null || loadFailed) return
        try {
            var image: NativeImage? = null

            val externalFile = File(System.getProperty("user.dir"), "avatar.png")
            if (externalFile.exists()) {
                image = NativeImage.read(Files.newInputStream(externalFile.toPath()))
                LiquidBounce.logger.info("[TargetHUD] 加载外部头像: ${externalFile.absolutePath}")
            }

            if (image == null) {
                val id = Identifier.fromNamespaceAndPath("liquidbounce", "textures/gui/avatar.png")
                val resource = mc.resourceManager.getResource(id).orElse(null)
                if (resource != null) {
                    resource.open().use { stream ->
                        image = NativeImage.read(stream)
                    }
                    LiquidBounce.logger.info("[TargetHUD] 加载内置头像资源: $id")
                }
            }

            if (image != null) {
                val id = Identifier.fromNamespaceAndPath("liquidbounce", "custom_avatar")
                val finalImage = image

                mc.execute {
                    val dynamicTexture = DynamicTexture(Supplier { id.toString() }, finalImage)
                    mc.textureManager.register(id, dynamicTexture)
                    customAvatarId = id
                    customSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                }
            } else {
                LiquidBounce.logger.warn("[TargetHUD] 未找到 avatar.png（外部和内部都没有），将使用代码兜底绘制。")
                loadFailed = true
            }
        } catch (e: Exception) {
            LiquidBounce.logger.error("[TargetHUD] 加载头像失败", e)
            loadFailed = true
        }
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        val player = mc.player ?: return@handler
        var best: LivingEntity? = null
        var bestDist = range
        for (e in RenderedEntities) {
            if (e !is LivingEntity || e === player || !e.isAlive) continue
            val d = player.distanceTo(e)
            if (d <= bestDist) {
                bestDist = d
                best = e
            }
        }
        target = best
    }

    private fun lerp(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).toInt()

    private fun palette(q: Double): Triple<Int, Int, Int> {
        val x = ((q % 1.0) + 1.0) % 1.0
        val t = x.toFloat()
        return when {
            x < 1.0 / 3.0 -> Triple(
                lerp(88, 245, t * 3f), lerp(204, 150, t * 3f), lerp(250, 200, t * 3f))
            x < 2.0 / 3.0 -> {
                val u = (t - 1f / 3f) * 3f
                Triple(lerp(245, 123, u), lerp(150, 44, u), lerp(200, 191, u))
            }
            else -> {
                val u = (t - 2f / 3f) * 3f
                Triple(lerp(123, 88, u), lerp(44, 204, u), lerp(191, 250, u))
            }
        }
    }

    private fun argb(alpha: Int, rgb: Triple<Int, Int, Int>): Int =
        (alpha shl 24) or ((rgb.first and 0xFF) shl 16) or ((rgb.second and 0xFF) shl 8) or (rgb.third and 0xFF)

    private fun colored(text: String, rgb: Int): MutableComponent =
        Component.literal(text).withStyle { it.withColor(TextColor.fromRgb(rgb)) }

    private fun GuiGraphicsExtractor.drawGradientBar(x: Float, y: Float, w: Float, h: Float,
                                                     c1: Triple<Int, Int, Int>, c2: Triple<Int, Int, Int>, alpha: Int) {
        if (w <= 0f || alpha <= 0) return
        val r = h / 2f
        if (w <= h) {
            drawCircle(x + w / 2f, y + h / 2f, r, 0f, Float2IntFunction { argb(alpha, c1) })
            return
        }

        val segs = 24
        val midStart = x + r
        val midEnd = x + w - r
        for (i in 0 until segs) {
            val t = (i + 0.5f) / segs
            val col = Triple(lerp(c1.first, c2.first, t), lerp(c1.second, c2.second, t), lerp(c1.third, c2.third, t))
            drawQuad(midStart + (midEnd - midStart) * i / segs, y,
                midStart + (midEnd - midStart) * (i + 1) / segs, y + h,
                Color4b(col.first, col.second, col.third, alpha))
        }
        val cy = y + h / 2f
        drawCircle(x + r, cy, r, 0f, Float2IntFunction { argb(alpha, c1) })
        drawCircle(x + w - r, cy, r, 0f, Float2IntFunction { argb(alpha, c2) })
    }

    private fun invokeNoArg(obj: Any, names: List<String>): Any? {
        for (n in names) {
            val m = obj.javaClass.methods.firstOrNull { it.name == n && it.parameterCount == 0 } ?: continue
            return runCatching { m.invoke(obj) }.getOrNull()
        }
        return null
    }

    private fun GuiGraphicsExtractor.drawCustomAvatar(cx: Float, cy: Float, r: Float) {
        val id = customAvatarId
        val sampler = customSampler
        if (id == null || sampler == null) return

        try {
            val tex = mc.textureManager.getTexture(id)
            if (tex.javaClass.simpleName.contains("Missing", ignoreCase = true)) return

            val view = invokeNoArg(tex, listOf("textureView", "getTextureView")) as? GpuTextureView ?: return
            val setup = TextureSetup.singleTexture(view, sampler)

            val centerU = 0.5f + offsetX
            val centerV = 0.5f + offsetY
            val halfWidth = 0.5f / imageScale
            val halfHeight = 0.5f / imageScale

            val strips = 128
            for (i in 0 until strips) {
                val x1 = cx - r + 2f * r * i / strips
                val x2 = cx - r + 2f * r * (i + 1) / strips
                val dx1 = (x1 - cx) / r
                val dx2 = (x2 - cx) / r
                val chord1 = sqrt((1f - dx1 * dx1).coerceAtLeast(0f)) * r
                val chord2 = sqrt((1f - dx2 * dx2).coerceAtLeast(0f)) * r
                val chord = (chord1 + chord2) * 0.5f
                if (chord <= 0.01f) continue

                val u1 = centerU - halfWidth + 2f * halfWidth * i / strips
                val u2 = centerU - halfWidth + 2f * halfWidth * (i + 1) / strips
                val vLo = centerV - halfHeight * (chord / r)
                val vHi = centerV + halfHeight * (chord / r)

                drawTexQuad(setup, x1, cy - chord, x2, cy + chord, u1, vLo, u2, vHi)
            }
        } catch (_: Exception) {}
    }

    private fun GuiGraphicsExtractor.drawHead(t: LivingEntity, hx: Float, hy: Float, size: Float,
                                              timeSec: Double, openAnim: Float) {
        val alpha = (openAnim * 255).toInt().coerceIn(0, 255)
        if (alpha <= 0) return
        val cx = hx + size / 2f
        val cy = hy + size / 2f

        if (customAvatarId != null) {
            drawCustomAvatar(cx, cy, size / 2f)
        } else {
            val colorHair = (alpha shl 24) or 0x2C4C8B
            val colorFace = (alpha shl 24) or 0xFFE3CD
            val colorWhite = (alpha shl 24) or 0xFFFFFF
            val colorEye = (alpha shl 24) or 0x1A1A2E
            val colorBlush = (alpha shl 24) or 0xFF9E9E

            drawCircle(cx, cy, size / 2f, 0f, Float2IntFunction { colorHair })
            drawCircle(cx, cy + size * 0.1f, size * 0.4f, 0f, Float2IntFunction { colorFace })
            drawRoundedRect(cx - size * 0.4f, cy - size * 0.5f, cx + size * 0.4f, cy - size * 0.35f, size * 0.1f, Color4b(colorWhite))
            drawRoundedRect(cx - size * 0.25f, cy - size * 0.05f, cx - size * 0.1f, cy + size * 0.05f, size * 0.05f, Color4b(colorEye))
            drawRoundedRect(cx + size * 0.1f, cy - size * 0.05f, cx + size * 0.25f, cy + size * 0.05f, size * 0.05f, Color4b(colorEye))
            drawCircle(cx - size * 0.3f, cy + size * 0.15f, size * 0.08f, 0f, Float2IntFunction { colorBlush })
            drawCircle(cx + size * 0.3f, cy + size * 0.15f, size * 0.08f, 0f, Float2IntFunction { colorBlush })
            drawCircle(cx, cy + size * 0.3f, size * 0.25f, 0f, Float2IntFunction { colorWhite })
        }
    }

    @Suppress("unused")
    private val renderHandler = handler<OverlayRenderEvent> { event ->
        val t = target
        val openTarget = if (t != null && t.isAlive) 1f else 0f

        val timeSec = System.currentTimeMillis() / 1000.0
        val dt = ((timeSec - lastFrameMs / 1000.0).coerceIn(0.0, 0.1)).toFloat()
        lastFrameMs = System.currentTimeMillis()
        openAnim += (openTarget - openAnim) * (1f - exp(-dt * 10f))
        if (t == null || !t.isAlive || openAnim < 0.05f) return@handler

        if (t !== lastTarget) {
            lastTarget = t
            animatedHealth = t.health
            animatedAbsorption = 0f
        }
        animatedHealth += (t.health - animatedHealth) * (1f - exp(-dt * 8f))

        val absorption = try { t.absorptionAmount } catch (_: Throwable) { 0f }
        animatedAbsorption += (absorption - animatedAbsorption) * (1f - exp(-dt * 8f))
        val dist = mc.player?.let { p -> runCatching { t.distanceTo(p) }.getOrNull() }

        val pad = 8f
        val headSize = 24f
        val headerH = 10f
        val rowGap = 5f
        val barH = 11f
        val radius = 16f

        val name = t.name.string
        val nameP = fontRenderer.process(Component.literal(name))
        val nameW = fontRenderer.getStringWidth(nameP) * textScale
        val distScale = textScale * 0.85f
        val distP = fontRenderer.process(colored(if (dist != null) "%.1fm".format(dist) else "--", 0x6A7180))
        val distW = fontRenderer.getStringWidth(distP) * distScale

        val headBlock = if (showHead) headSize + 8f else 0f
        val contentW = max(nameW + (if (distW > 0f) distW + 10f else 0f), 90f)
        val width = contentW + headBlock + pad * 2
        val height = pad + headerH + rowGap + barH + pad

        val x1 = posX; val y1 = posY
        val x2 = posX + width; val y2 = posY + height
        val alpha = (openAnim * 255).toInt().coerceIn(0, 255)

        val maxTotal = t.maxHealth + animatedAbsorption
        val hpPct = if (maxTotal > 0f) (animatedHealth / maxTotal).coerceIn(0f, 1f) else 0f
        val absPct = if (maxTotal > 0f) (animatedAbsorption / maxTotal).coerceIn(0f, 1f) else 0f

        with(event.context) {
            pose().withPush {
                val s = 0.85f + 0.15f * openAnim
                translate(posX + width / 2f, posY + height / 2f)
                scale(s, s)
                translate(-(posX + width / 2f), -(posY + height / 2f))

                val pulse = (0.5f - 0.5f * cos(timeSec * PI / 2.0)).toFloat()

                val shadowE = 3f
                val shadowA = ((14 + 12 * pulse) * openAnim).toInt().coerceIn(0, 255)
                drawRoundedRect(
                    x1 - shadowE, y1 - shadowE, x2 + shadowE, y2 + shadowE,
                    radius + shadowE,
                    Color4b(0, 0, 0, shadowA)
                )

                val glassA = ((60 + 22 * pulse) * openAnim).toInt().coerceIn(0, 255)
                drawRoundedRect(x1, y1, x2, y2, radius, Color4b(242, 246, 255, glassA))

                val borderA = ((8 + 16 * pulse) * openAnim).toInt().coerceIn(0, 255)
                drawRoundedRect(x1, y1, x2, y2, radius, Color4b(0, 0, 0, 0),
                    Color4b(255, 255, 255, borderA), 1f)

                val cx1 = x1 + pad + headBlock
                if (showHead) {
                    drawHead(t, x1 + pad, y1 + pad, headSize, timeSec, openAnim)
                }

                fontRenderer.draw(fontRenderer.process(colored(name, 0x232833))) {
                    x = cx1; y = y1 + pad + 1f; scale = textScale; shadow = false
                }
                fontRenderer.draw(distP) {
                    x = x2 - pad - distW; y = y1 + pad + 2.5f; scale = distScale; shadow = false
                }

                val barX = cx1
                val barW = width - pad - headBlock - pad
                val barY = y1 + pad + headerH + rowGap

                drawRoundedRect(barX, barY, barX + barW, barY + barH, barH / 2f,
                    Color4b(20, 24, 35, (35 * openAnim).toInt()),
                    Color4b(90, 96, 110, (120 * openAnim).toInt()), 1f)

                val inset = 1.5f
                val fillH = barH - inset * 2f
                val fillY = barY + inset
                val hpW = barW * hpPct
                if (hpW > 0.5f) {
                    val c1 = palette(timeSec / 4.0)
                    val c2 = palette(timeSec / 4.0 + 0.33)
                    drawGradientBar(barX + inset, fillY, hpW - inset * 2f, fillH, c1, c2, alpha)
                }
                if (absPct > 0.001f) {
                    val absX = barX + hpW
                    val absW = barW * absPct
                    drawGradientBar(absX - inset, fillY, absW, fillH,
                        Triple(255, 200, 60), Triple(255, 170, 30), alpha)
                }
            }
        }
    }
}
