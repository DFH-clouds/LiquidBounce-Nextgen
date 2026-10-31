package net.ccbluex.liquidbounce.features.module.modules.render

import com.mojang.authlib.GameProfile
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
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
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import java.util.concurrent.CompletableFuture
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

//一个targethud 一个半成品 26/10/18 By花辞树  //已完工 26/10/31
object ModuleTargetHud : ClientModule("TargetHUD", ModuleCategories.RENDER, aliases = listOf("TargetHUD")) {

    private val range by float("Range", 6f, 2f..12f)
    private val posX by float("X", 490f, -500f..500f)
    private val posY by float("Y", 205f, -500f..500f)
    private val textScale by float("TextScale", 0.38f, 0.2f..0.8f)
    private val showHead by boolean("ShowHead", true)

    private val fontRenderer
        get() = FontManager.fontFace("Inter Bold")?.renderer ?: FontManager.FONT_RENDERER

    private var target: LivingEntity? = null
    private var lastTarget: LivingEntity? = null
    private var animatedHealth = 0f
    private var animatedAbsorption = 0f
    private var openAnim = 0f
    private var lastFrameMs = 0L
    private var skinLogCooldown = 0L

    override fun onEnabled() {
        RenderedEntities.subscribe(this)
        super.onEnabled()
    }

    override fun onDisabled() {
        RenderedEntities.unsubscribe(this)
        target = null
        skinSetupCache.clear()
        skinMethodCache = null
        skinSampler = null
        super.onDisabled()
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

    private fun logSkin(msg: String, e: Exception? = null) {
        val now = System.currentTimeMillis()
        if (now - skinLogCooldown < 5000) return
        skinLogCooldown = now
        if (e != null) LiquidBounce.logger.warn("[TargetHud] $msg", e)
        else LiquidBounce.logger.warn("[TargetHud] $msg")
    }

    private var skinMethodCache: java.lang.reflect.Method? = null
    private var skinSampler: GpuSamplerRef? = null
    private val skinSetupCache = HashMap<java.util.UUID, Pair<Any?, Long>>()

    private fun invokeNoArg(obj: Any, names: List<String>): Any? {
        for (n in names) {
            val m = obj.javaClass.methods.firstOrNull { it.name == n && it.parameterCount == 0 } ?: continue
            return runCatching { m.invoke(obj) }.getOrNull()
        }
        return null
    }

    /**
     * 完整皮肤解析链（每个玩家只做一次）
     */
    private fun resolveSkinSetup(profile: GameProfile): Any? {
        val sm = mc.skinManager
        val method = skinMethodCache ?: sm.javaClass.methods.firstOrNull {
            it.parameterCount == 1 && it.parameterTypes[0] == GameProfile::class.java
        }?.also { skinMethodCache = it }
        if (method == null) {
            logSkin("no GameProfile methods on SkinManager (${sm.javaClass.name})")
            return null
        }
        val raw = runCatching { method.invoke(sm, profile) }.getOrNull() ?: return null
        var resolved: Any? = when (raw) {
            is CompletableFuture<*> -> if (raw.isDone) runCatching { raw.getNow(null) }.getOrNull() else null
            else -> raw
        }
        if (resolved is java.util.Optional<*>) resolved = resolved.orElse(null)
        if (resolved == null) return null

        val body = invokeNoArg(resolved, listOf("body", "texture"))
        if (body == null) {
            logSkin("skin=${resolved.javaClass.name} methods=" + resolved.javaClass.methods
                .filter { it.parameterCount == 0 }
                .joinToString(",") { "${it.name}:${it.returnType.name}" })
            return null
        }
        var texObj: Any? = null
        var triedIds = StringBuilder()
        for (name in listOf("id", "texturePath")) {
            var idf = invokeNoArg(body, listOf(name)) as? Identifier ?: continue
            if (!idf.path.startsWith("textures/")) {
                idf = Identifier.fromNamespaceAndPath(idf.namespace, "textures/" + idf.path)
            }
            if (triedIds.isNotEmpty()) triedIds.append(", ")
            triedIds.append(idf.toString())
            val tex = runCatching { mc.textureManager.getTexture(idf) }.getOrNull()
            if (tex == null || tex.javaClass.simpleName.contains("Missing", ignoreCase = true)) continue
            if (body.javaClass.simpleName.contains("Resource")) {
                val exists = runCatching { mc.resourceManager.getResource(idf).isPresent }.getOrDefault(false)
                if (!exists) continue
            }
            texObj = tex
            break
        }
        if (texObj == null) {
            logSkin("body=${body.javaClass.name} tried=[$triedIds] 未找到可用纹理")
            dumpSkinTextureKeys()
            return null
        }
        val view = invokeNoArg(texObj, listOf("textureView", "getTextureView"))
        if (view == null) {
            logSkin("tex=${texObj.javaClass.name} methods=" + texObj.javaClass.methods
                .filter { it.parameterCount == 0 }
                .joinToString(",") { "${it.name}:${it.returnType.name}" })
            return null
        }
        if (skinSampler == null) {
            skinSampler = GpuSamplerRef(RenderSystem.getSamplerCache()
                .getClampToEdge(FilterMode.NEAREST))
        }
        val setup = TextureSetup::class.java.methods.firstOrNull {
            it.name == "singleTexture" && it.parameterCount == 2
        }?.let {
            runCatching { it.invoke(null, view, skinSampler?.ref) }.getOrNull()
        }
        if (setup == null) logSkin("TextureSetup.singleTexture not found")
        return setup
    }
    //一个诊断小工具
    private fun dumpSkinTextureKeys() {
        try {
            val tm = mc.textureManager
            val fields = tm.javaClass.fields.toList() +
                tm.javaClass.declaredFields.onEach { it.isAccessible = true }
            for (f in fields) {
                if (!Map::class.java.isAssignableFrom(f.type)) continue
                val map = f.get(tm) as? Map<*, *> ?: continue
                val keys = map.keys.mapNotNull { it?.toString() }
                    .filter { it.contains("player", true) || it.contains("skin", true) }
                if (keys.isNotEmpty()) {
                    logSkin("texman.${f.name} [${keys.size}]: ${keys.take(25)}")
                }
            }
        } catch (_: Throwable) {}
    }

    // 每个玩家的 TextureSetup 缓存：成功永久缓存，失败 10 秒后重试
    private fun cachedSkinSetup(t: Player): Any? {
        val uuid = t.uuid
        val now = System.currentTimeMillis()
        val hit = skinSetupCache[uuid]
        if (hit != null && (hit.first != null || now - hit.second < 10_000)) return hit.first
        val setup = resolveSkinSetup(t.gameProfile)
        skinSetupCache[uuid] = setup to now
        return setup
    }

    private class GpuSamplerRef(val ref: Any)

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

    // 圆形裁剪头像
    private fun GuiGraphicsExtractor.drawHeadCircle(setup: TextureSetup, cx: Float, cy: Float, r: Float) {
        val strips = 24
        val u0 = 8f / 64f
        val v0 = 8f / 64f
        val span = 8f / 64f
        for (i in 0 until strips) {
            val dx = ((i + 0.5f) / strips) * 2f - 1f
            val chord = sqrt((1f - dx * dx).coerceAtLeast(0f)) * r
            if (chord <= 0.2f) continue
            val x1 = cx - r + 2f * r * i / strips
            val x2 = cx - r + 2f * r * (i + 1) / strips
            val u1 = u0 + span * i / strips
            val u2 = u0 + span * (i + 1) / strips
            val half = (chord / r) * 0.5f
            drawTexQuad(setup, x1, cy - chord, x2, cy + chord,
                u1, v0 + span * (0.5f - half), u2, v0 + span * (0.5f + half))
        }
    }

    // 头像（皮肤失败回退彩球）
    private fun GuiGraphicsExtractor.drawHead(t: LivingEntity, hx: Float, hy: Float, size: Float,
                                              timeSec: Double, openAnim: Float) {
        var drew = false
        if (t is Player) {
            try {
                val setup = cachedSkinSetup(t)
                if (setup != null) {
                    drawHeadCircle(setup as TextureSetup, hx + size / 2f, hy + size / 2f, size / 2f)
                    drew = true
                }
            } catch (e: Exception) {
                logSkin("head draw failed", e)
            }
        }
        if (!drew) {
            val cr = size / 2f - 2f
            val rimC = palette(timeSec / 5.0)
            drawCircle(hx + size / 2f, hy + size / 2f, cr + 1.2f, 0f,
                Float2IntFunction { argb((90 * openAnim).toInt(), rimC) })
            drawCircle(hx + size / 2f, hy + size / 2f, cr, 0f,
                Float2IntFunction { argb((200 * openAnim).toInt(), rimC) })
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

        // 血量/吸收占比
        val maxTotal = t.maxHealth + animatedAbsorption
        val hpPct = if (maxTotal > 0f) (animatedHealth / maxTotal).coerceIn(0f, 1f) else 0f
        val absPct = if (maxTotal > 0f) (animatedAbsorption / maxTotal).coerceIn(0f, 1f) else 0f

        with(event.context) {
            pose().withPush {
                val s = 0.85f + 0.15f * openAnim
                translate(posX + width / 2f, posY + height / 2f)
                scale(s, s)
                translate(-(posX + width / 2f), -(posY + height / 2f))

                val oc = palette(timeSec / 5.0)
                drawRoundedRect(x1, y1, x2, y2, radius, Color4b(242, 246, 255, (72 * openAnim).toInt()))
                drawRoundedRect(x1, y1, x2, y2, radius, Color4b(0, 0, 0, 0),
                    Color4b(oc.first, oc.second, oc.third, (70 * openAnim).toInt()), 1f)

                // 头像
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

                val hpScale = textScale * 0.8f
                val hpText = "%.1f".format(animatedHealth)
                val hpTextP = fontRenderer.process(Component.literal(hpText))
                val hpTextW = fontRenderer.getStringWidth(hpTextP) * hpScale
                var absTextW = 0f
                val absTextP = if (animatedAbsorption > 0.5f) {
                    val p = fontRenderer.process(colored("+${animatedAbsorption.toInt()}",
                        0xFFCC44))
                    absTextW = fontRenderer.getStringWidth(p) * hpScale
                    p
                } else null
                val textX = barX + barW / 2f - (hpTextW + absTextW) / 2f
                fontRenderer.draw(hpTextP) {
                    x = textX; y = barY + barH / 2f - 3.5f; scale = hpScale; shadow = true
                }
                if (absTextP != null) {
                    fontRenderer.draw(absTextP) {
                        x = textX + hpTextW; y = barY + barH / 2f - 3.5f; scale = hpScale; shadow = true
                    }
                }
            }
        }
    }
}
