/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * 原生 Kotlin ClickGUI —— 经典面板风, 零浏览器零 MCEF
 *
 * 基于 LiquidBounce 0.38.0 (MC 26.1) 源码 API 编写
 * 绘制上下文: GuiGraphicsExtractor (26.1 中 GuiGraphics 的替代)
 * 文字: context.text(font, str, x, y, color)
 * 矩形: GuiGraphicsExtractor.fill(x1, y1, x2, y2, color)
 *
 * 放置路径:
 *   src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/render/clickgui/NativeClickGuiScreen.kt
 */
package net.ccbluex.liquidbounce.features.module.modules.render.clickgui

import com.mojang.blaze3d.platform.InputConstants
import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.ValueType
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.config.types.list.ChoiceListValue
import net.ccbluex.liquidbounce.config.types.list.MultiChoiceListValue
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.input.InputBind
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 原生 ClickGUI —— 经典面板风, 零浏览器零 MCEF
 *
 * 操作:
 *   左键模块 = 开关
 *   右键模块 = 展开/收起该模块设置
 *   右键标题 = 最小化/还原整个面板（还原时恢复之前展开的模块）
 *   拖动标题 = 移动面板
 *   滚轮 = 滚动
 *   顶部搜索框 = 输入关键字过滤模块
 *   右下角 [DIY Theme] = 打开颜色编辑模式
 *
 * 状态持久化:
 *   - 展开模块 / 面板位置 / 最小化状态 / 搜索关键词：静态字段，关闭 ClickGUI 后保留
 *   - 主题颜色：静态字段 + 磁盘文件（config/liquidbounce/nativeclickgui-theme.txt），
 *     关闭游戏后重开依然保留
 *
 * 暂停:
 *   - 编辑文本 / 聚焦搜索框时暂停游戏，角色不再移动
 */
class ClickGuiHCS : Screen(Component.literal("ClickGUI")) {

    //颜色管理
    class ClickGuiTheme {
        var accent = 0xFF4677FF.toInt()
        var panelBg = 0xF016161A.toInt()
        var headerBg = 0xFF101013.toInt()
        var border = 0xFF2C2C33.toInt()
        var rowHover = 0xFF1B1B21.toInt()
        var textOn = 0xFFFFFFFF.toInt()
        var textOff = 0xFF9C9CA4.toInt()
        var textDim = 0xFF666670.toInt()
        var dotOff = 0xFF3A3A42.toInt()

        fun getColor(i: Int): Int = when (i) {
            0 -> accent; 1 -> panelBg; 2 -> headerBg; 3 -> border; 4 -> rowHover
            5 -> textOn; 6 -> textOff; 7 -> textDim; else -> dotOff
        }

        fun setColor(i: Int, v: Int) {
            when (i) {
                0 -> accent = v; 1 -> panelBg = v; 2 -> headerBg = v; 3 -> border = v; 4 -> rowHover = v
                5 -> textOn = v; 6 -> textOff = v; 7 -> textDim = v; else -> dotOff = v
            }
        }

        fun resetToDefault() {
            accent = 0xFF4677FF.toInt()
            panelBg = 0xF016161A.toInt()
            headerBg = 0xFF101013.toInt()
            border = 0xFF2C2C33.toInt()
            rowHover = 0xFF1B1B21.toInt()
            textOn = 0xFFFFFFFF.toInt()
            textOff = 0xFF9C9CA4.toInt()
            textDim = 0xFF666670.toInt()
            dotOff = 0xFF3A3A42.toInt()
        }

        companion object {
            val NAMES = listOf(
                "Accent", "Panel BG", "Header BG", "Border", "Row Hover",
                "Text On", "Text Off", "Text Dim", "Dot Off"
            )
            val COUNT = NAMES.size
        }
    }

    companion object {
        private val SAVED_OPEN: MutableMap<String, MutableSet<String>> = HashMap()
        private val SAVED_SAVED_OPEN: MutableMap<String, MutableSet<String>> = HashMap()
        private val SAVED_MINIMIZED: MutableMap<String, Boolean> = HashMap()
        private val SAVED_POS: MutableMap<String, FloatArray> = HashMap()
        private var SAVED_SEARCH: String = ""

        private var initialized: Boolean = false

        val SHARED_THEME = ClickGuiTheme()

        //check
        private var themeLoaded = false

        //生成主题颜色配置文件
        private fun themeFile(): File {
            val dir = File(mc.gameDirectory, "config/liquidbounce")
            if (!dir.exists()) dir.mkdirs()
            return File(dir, "nativeclickgui-theme.txt")
        }

        //从文件里面读取信息 来加载颜色
        fun loadThemeFromDisk() {
            if (themeLoaded) return
            themeLoaded = true
            try {
                val f = themeFile()
                if (!f.exists()) return
                val lines = f.readLines()
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                    val parts = trimmed.split("=", limit = 2)
                    if (parts.size != 2) continue
                    val key = parts[0].trim()
                    val value = parts[1].trim().toLongOrNull(16)?.toInt() ?: continue
                    val idx = ClickGuiTheme.NAMES.indexOf(key)
                    if (idx >= 0) SHARED_THEME.setColor(idx, value)
                }
            } catch (_: Throwable) {}
        }

        //save配置文件
        fun saveThemeToDisk() {
            try {
                val sb = StringBuilder()
                sb.append("# NativeClickGui Theme\n")
                sb.append("# Format: Name=HEX_ARGB\n")
                for (i in 0 until ClickGuiTheme.COUNT) {
                    val name = ClickGuiTheme.NAMES[i]
                    val color = SHARED_THEME.getColor(i)
                    sb.append(name).append("=").append("%08X".format(color)).append("\n")
                }
                themeFile().writeText(sb.toString())
            } catch (_: Throwable) {}
        }

        //重置你的颜色配置
        fun resetTheme() {
            SHARED_THEME.resetToDefault()
            saveThemeToDisk()
        }
    }

    private val theme get() = SHARED_THEME

    private val ACCENT get() = theme.accent
    private val PANEL_BG get() = theme.panelBg
    private val HEADER_BG get() = theme.headerBg
    private val BORDER get() = theme.border
    private val ROW_HOVER get() = theme.rowHover
    private val TEXT_ON get() = theme.textOn
    private val TEXT_OFF get() = theme.textOff
    private val TEXT_DIM get() = theme.textDim
    private val DOT_OFF get() = theme.dotOff

    private val PANEL_W = 124f
    private val HEADER_H = 18f
    private val MODULE_H = 16f

    private val THEME_PANEL_W = 400f

    // 关于搜索框的变量定义
    private val SEARCH_W = 220f
    private val SEARCH_H = 18f
    private val SEARCH_Y = 10f
    private val SEARCH_TOP_PAD = SEARCH_Y + SEARCH_H + 4f


    private class Panel(val categoryName: String) {
        var x = 10f
        var y = 32f
        var scroll = 0f
        val openModules = HashSet<ClientModule>()
        var savedOpenModules = HashSet<ClientModule>()
        var minimized = false
    }

    private enum class Kind { MODULE_ROW, BOOLEAN, SLIDER, RANGE, TEXT, LISTEN_BIND, LISTEN_KEY, CHOOSE, MULTI_ITEM }
    private class Hit(val x: Float, val y: Float, val w: Float, val h: Float, val kind: Kind,
                      val value: Value<*>? = null, val module: ClientModule? = null, val index: Int = 0)

    private class ThemeHit(val x: Float, val y: Float, val w: Float, val h: Float,
                           val colorIdx: Int, val channel: Int)

    private val panels = ArrayList<Panel>()
    private var hits = ArrayList<Hit>()
    private var dragPanel: Panel? = null
    private var dragOffX = 0f
    private var dragOffY = 0f
    private var activeSlider: Hit? = null
    private var listeningBind: Value<InputBind>? = null
    private var listeningKey: Value<InputConstants.Key>? = null
    private var editingText: Value<*>? = null
    private var editBuffer = StringBuilder()
    private var lastMx = -1f
    private var lastMy = -1f
    private var layoutDone = false

    private var themeMode = false
    private val themeHits = ArrayList<ThemeHit>()
    private var draggingThemeSlider: ThemeHit? = null


    private var searchQuery = ""
    private var searchFocused = false

    // ================================================================
    // 编辑状态判断（用于 isPauseScreen）
    // ================================================================
    private fun isEditingSomething(): Boolean = editingText != null || searchFocused

    // ================================================================
    // 编辑生命周期统一管理
    // ================================================================
    private fun beginEditText(v: Value<*>) {
        editingText = v
        editBuffer = StringBuilder(safeStr(v.get()))
    }

    private fun cancelEditText() {
        editingText = null
        editBuffer.clear()
    }

    private fun modulesIn(cat: String): List<ClientModule> {
        val all = ModuleManager.filter { !it.hidden && it.category.tag == cat }
        if (searchQuery.isBlank()) return all
        return all.filter { matchesSearch(it, searchQuery) }
    }

    private fun modulesInRaw(cat: String): List<ClientModule> =
        ModuleManager.filter { !it.hidden && it.category.tag == cat }

    private fun isVisible(v: Value<*>) = !v.notAnOption

    private fun splitName(n: Any?): String {
        val s = n?.toString() ?: ""
        return s.replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1 $2")
    }

    private fun safeStr(any: Any?): String = any?.toString() ?: ""

    private fun normalize(s: String): String =
        s.lowercase().replace(" ", "").replace("_", "").replace("-", "")

    private fun matchesSearch(m: ClientModule, q: String): Boolean {
        val nq = normalize(q)
        if (nq.isEmpty()) return true
        val raw = safeStr(m.name)
        val spaced = splitName(raw)
        return normalize(raw).contains(nq) || normalize(spaced).contains(nq)
    }

    private fun searchBoxX(): Float = (this.width - SEARCH_W) / 2f

    private fun ensureLayout() {
        if (layoutDone) return
        layoutDone = true

        //读取配置文件并加载
        loadThemeFromDisk()

        val order = listOf("Combat", "Exploit", "Fun", "Misc", "Movement", "Player", "Render", "Client", "World")
        val present = ModuleManager.filter { !it.hidden }.map { it.category.tag }.distinct()
        val names = order.filter { it in present } + present.filter { it !in order }

        if (initialized) {
            searchQuery = SAVED_SEARCH
        } else {
            searchQuery = ""
            initialized = true
        }

        var defaultX = 10f
        names.forEach { cat ->
            val p = Panel(cat)

            val savedPos = SAVED_POS[cat]
            if (savedPos != null && savedPos.size == 2) {
                p.x = savedPos[0]
                p.y = savedPos[1]
            } else {
                p.x = defaultX
                p.y = SEARCH_TOP_PAD
            }

            p.minimized = SAVED_MINIMIZED[cat] ?: false

            val savedOpen = SAVED_OPEN[cat]
            if (savedOpen != null && savedOpen.isNotEmpty()) {
                val all = modulesInRaw(cat)
                for (m in all) {
                    if (m.name in savedOpen) p.openModules.add(m)
                }
            }

            val savedSaved = SAVED_SAVED_OPEN[cat]
            if (savedSaved != null && savedSaved.isNotEmpty()) {
                val all = modulesInRaw(cat)
                for (m in all) {
                    if (m.name in savedSaved) p.savedOpenModules.add(m)
                }
            }

            panels.add(p)
            defaultX += PANEL_W + 8f
        }
    }

    //保存所有状态 并保存配置文件
    private fun saveState() {
        for (p in panels) {
            SAVED_OPEN[p.categoryName] = p.openModules.map { it.name }.toMutableSet()
            SAVED_SAVED_OPEN[p.categoryName] = p.savedOpenModules.map { it.name }.toMutableSet()
            SAVED_MINIMIZED[p.categoryName] = p.minimized
            SAVED_POS[p.categoryName] = floatArrayOf(p.x, p.y)
        }
        SAVED_SEARCH = searchQuery


        saveThemeToDisk()
    }

    private fun measureValue(v: Value<*>): Int = when {
        !isVisible(v) -> 0
        v is ModeValueGroup<*> ->
            16 + v.activeMode.containedValues.filter(::isVisible).sumOf { measureValue(it) }
        v is ToggleableValueGroup ->
            14 + if (v.enabled) v.containedValues.filter { it !== v.enabledValue && isVisible(it) }
                .sumOf { measureValue(it) } + 2 else 0
        v is ValueGroup ->
            12 + v.containedValues.filter(::isVisible).sumOf { measureValue(it) }
        v.valueType == ValueType.BOOLEAN -> 14
        v.valueType == ValueType.INT || v.valueType == ValueType.FLOAT -> 26
        v.valueType == ValueType.INT_RANGE || v.valueType == ValueType.FLOAT_RANGE -> 32
        v.valueType == ValueType.TEXT -> 22
        v.valueType == ValueType.BIND || v.valueType == ValueType.KEY -> 20
        v.valueType == ValueType.CHOOSE -> 14
        v.valueType == ValueType.MULTI_CHOOSE ->
            14 + ((v as? MultiChoiceListValue<*>)?.choices?.size ?: 0) * 13
        v.valueType == ValueType.COLOR -> 44
        else -> 14
    }

    private fun measureModule(m: ClientModule): Int {
        val p = panels.firstOrNull { m in it.openModules } ?: return 16
        return 16 + m.containedValues.filter(::isVisible).sumOf { measureValue(it) }
    }

    private fun contentHeight(p: Panel) =
        HEADER_H + modulesIn(p.categoryName).sumOf { measureModule(it) } + 4f

    private fun panelHeight(p: Panel): Float {
        if (p.minimized) return HEADER_H
        val content = contentHeight(p)
        val cap = (this.height * 0.75f).coerceIn(180f, 500f)
        return min(content, cap).coerceAtLeast(HEADER_H + 4f)
    }

    private fun clampScroll(p: Panel) {
        if (p.minimized) { p.scroll = 0f; return }
        p.scroll = p.scroll.coerceIn(0f, max(0f, contentHeight(p) - panelHeight(p)))
    }

    override fun onClose() {
        saveState()
        super.onClose()
    }

    override fun removed() {
        saveState()
        super.removed()
    }

    // ================================================================
    // 编辑/搜索时暂停游戏，阻止角色移动
    // ================================================================
    override fun isPauseScreen(): Boolean = isEditingSomething()

    override fun extractRenderState(
        context: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float
    ) {
        ensureLayout()
        lastMx = mouseX.toFloat()
        lastMy = mouseY.toFloat()

        rect(context, 0f, 0f, this.width.toFloat(), this.height.toFloat(), 0xC0101015.toInt())

        if (themeMode) {
            drawThemeEditor(context)
            super.extractRenderState(context, mouseX, mouseY, partialTick)
            return
        }

        hits.clear()
        for (p in panels) {
            clampScroll(p)
            try {
                drawPanel(context, p)
            } catch (_: Throwable) {}
        }

        try { drawSearchBox(context) } catch (_: Throwable) {}


        val (tbX, tbY, tbW, tbH) = themeBtnRect()
        val hovering = lastMx in tbX..(tbX + tbW) && lastMy in tbY..(tbY + tbH)
        text(context, "[DIY Theme]", tbX.toInt(), tbY.toInt(), if (hovering) ACCENT else TEXT_OFF)

        val msg = when {
            listeningBind != null -> "Press a key... (Backspace: unbind, ESC: cancel)"
            listeningKey != null -> "Press a key... (ESC: cancel)"
            editingText != null -> "Typing: ${editBuffer}_ (Enter: confirm, ESC: cancel)"
            else -> null
        }
        if (msg != null) {
            text(context, msg, (this.width - mc.font.width(msg)) / 2, this.height - 28, ACCENT)
        }

        super.extractRenderState(context, mouseX, mouseY, partialTick)
    }

    private fun themeBtnRect(): FloatArray {
        val label = "[DIY Theme]"
        val w = mc.font.width(label).toFloat()
        return floatArrayOf(this.width - w - 10f, this.height - 14f, w, 10f)
    }


    //搜索框
    private fun drawSearchBox(c: GuiGraphicsExtractor) {
        val bx = searchBoxX()
        val by = SEARCH_Y
        val bw = SEARCH_W
        val bh = SEARCH_H

        rect(c, bx, by, bw, bh, 0xF01A1A20.toInt())
        val bc = if (searchFocused) ACCENT else BORDER
        rect(c, bx, by, bw, 1f, bc)
        rect(c, bx, by + bh - 1, bw, 1f, bc)
        rect(c, bx, by, 1f, bh, bc)
        rect(c, bx + bw - 1, by, 1f, bh, bc)

        text(c, "?", (bx + 5).toInt(), (by + 5).toInt(), if (searchFocused) ACCENT else TEXT_DIM)

        val textY = (by + 5).toInt()
        val displayText = if (searchQuery.isEmpty() && !searchFocused) "Search modules..." else searchQuery
        val displayColor = if (searchQuery.isEmpty()) TEXT_DIM else TEXT_ON
        text(c, displayText, (bx + 16).toInt(), textY, displayColor)

        if (searchFocused) {
            val cursorX = bx + 16 + mc.font.width(searchQuery)
            val blink = (System.currentTimeMillis() / 500) % 2 == 0L
            if (blink) {
                rect(c, cursorX, by + 3f, 1f, bh - 6f, TEXT_ON)
            }
        }

        if (searchQuery.isNotEmpty()) {
            text(c, "x", (bx + bw - 10).toInt(), textY, TEXT_DIM)
        }

        if (searchQuery.isNotBlank()) {
            val count = ModuleManager.count { !it.hidden && matchesSearch(it, searchQuery) }
            val label = "$count match" + (if (count == 1) "" else "es")
            text(c, label, (bx + bw + 6f).toInt(), textY, TEXT_DIM)
        }
    }

    //编辑器渲染
    private fun drawThemeEditor(c: GuiGraphicsExtractor) {
        val panelH = 30f + ClickGuiTheme.COUNT * 30f + 40f
        val px = (this.width - THEME_PANEL_W) / 2f
        val py = (this.height - panelH) / 2f

        rect(c, px, py, THEME_PANEL_W, panelH, 0xF016161A.toInt())
        rect(c, px, py, THEME_PANEL_W, 1f, 0xFF2C2C33.toInt())
        rect(c, px, py + panelH - 1, THEME_PANEL_W, 1f, 0xFF2C2C33.toInt())
        rect(c, px, py, 1f, panelH, 0xFF2C2C33.toInt())
        rect(c, px + THEME_PANEL_W - 1, py, 1f, panelH, 0xFF2C2C33.toInt())

        text(c, "Theme DIY", (px + 10).toInt(), (py + 8).toInt(), 0xFF4677FF.toInt())
        text(c, "ESC to exit  |  [Reset] = default", (px + THEME_PANEL_W - 220).toInt(), (py + 8).toInt(), 0xFF666670.toInt())

        themeHits.clear()

        val padding = 12f
        val nameW = 100f
        val previewW = 22f
        val hexW = 80f
        val sliderArea = THEME_PANEL_W - padding * 2 - nameW - previewW - hexW - 12f
        val sliderGap = 4f
        val sliderW = (sliderArea - sliderGap * 3) / 4f
        val sliderH = 6f

        var cy = py + 28f
        for (i in 0 until ClickGuiTheme.COUNT) {
            val name = ClickGuiTheme.NAMES[i]
            val color = theme.getColor(i)
            val a = (color ushr 24) and 0xFF
            val r = (color ushr 16) and 0xFF
            val g = (color ushr 8) and 0xFF
            val b = color and 0xFF

            text(c, name, (px + padding).toInt(), cy.toInt(), 0xFFFFFFFF.toInt())
            val previewX = px + padding + nameW
            rect(c, previewX, cy - 2f, previewW, 11f, color)
            rect(c, previewX, cy - 2f, previewW, 1f, 0xFF2C2C33.toInt())
            rect(c, previewX, cy + 9f, previewW, 1f, 0xFF2C2C33.toInt())
            val hexStr = "#%08X".format(color)
            text(c, hexStr, (px + padding + nameW + previewW + 6f).toInt(), cy.toInt(), 0xFF9C9CA4.toInt())

            cy += 12f

            val startX = px + padding + nameW + previewW + hexW + 12f
            val values = intArrayOf(r, g, b, a)
            val labels = arrayOf("R", "G", "B", "A")
            val sliderColors = intArrayOf(
                0xFFFF4040.toInt(),
                0xFF40FF40.toInt(),
                0xFF4040FF.toInt(),
                0xFFCCCCCC.toInt()
            )
            for (j in 0..3) {
                val sx = startX + j * (sliderW + sliderGap)
                rect(c, sx, cy + 3f, sliderW, sliderH, 0xFF202020.toInt())
                rect(c, sx, cy + 3f, sliderW * (values[j] / 255f), sliderH, sliderColors[j])
                val hx = sx + sliderW * (values[j] / 255f) - 1f
                rect(c, hx, cy + 1f, 2f, sliderH + 4f, 0xFFFFFFFF.toInt())
                text(c, "${labels[j]}${values[j]}", (sx + sliderW + 2f).toInt(), cy.toInt(), 0xFF9C9CA4.toInt())

                themeHits.add(ThemeHit(sx, cy, sliderW, sliderH + 4f, i, j))
            }

            cy += 18f
        }

        // 底部 [Reset] 按钮
        val resetW = 60f
        val resetH = 14f
        val resetX = px + (THEME_PANEL_W - resetW) / 2f
        val resetY = py + panelH - resetH - 8f
        val hoveringReset = lastMx in resetX..(resetX + resetW) && lastMy in resetY..(resetY + resetH)
        rect(c, resetX, resetY, resetW, resetH, if (hoveringReset) 0xFF2A2A33.toInt() else 0xFF1A1A20.toInt())
        rect(c, resetX, resetY, resetW, 1f, 0xFF2C2C33.toInt())
        rect(c, resetX, resetY + resetH - 1, resetW, 1f, 0xFF2C2C33.toInt())
        text(c, "Reset", (resetX + 18f).toInt(), (resetY + 3f).toInt(), 0xFFFFFFFF.toInt())
    }


    // 主面板渲染
    private fun drawPanel(c: GuiGraphicsExtractor, p: Panel) {
        val ph = panelHeight(p)
        rect(c, p.x, p.y, PANEL_W, ph, PANEL_BG)
        rect(c, p.x, p.y, PANEL_W, 1f, BORDER)
        rect(c, p.x, p.y + ph - 1, PANEL_W, 1f, BORDER)
        rect(c, p.x, p.y, 1f, ph, BORDER)
        rect(c, p.x + PANEL_W - 1, p.y, 1f, ph, BORDER)

        rect(c, p.x, p.y, PANEL_W, HEADER_H, HEADER_BG)
        text(c, p.categoryName, (p.x + 6).toInt(), (p.y + 5).toInt(), ACCENT)
        text(c, if (p.minimized) "+" else "-",
            (p.x + PANEL_W - 9).toInt(), (p.y + 5).toInt(), TEXT_DIM)

        if (p.minimized) return

        val sx1 = p.x.toInt()
        val sy1 = (p.y + HEADER_H).toInt()
        val sx2 = (p.x + PANEL_W).toInt()
        val sy2 = (p.y + ph).toInt()
        c.enableScissor(sx1, sy1, sx2, sy2)

        var y = p.y + HEADER_H - p.scroll
        val x = p.x + 2
        val w = PANEL_W - 4
        for (m in modulesIn(p.categoryName)) {
            val mh = measureModule(m).toFloat()
            if (y + mh > p.y + HEADER_H && y < p.y + ph) {
                try {
                    drawModule(c, p, m, x, y, w)
                } catch (_: Throwable) {}
            }
            y += mh
        }

        c.disableScissor()

        val ch = contentHeight(p)
        if (ch > ph) {
            val trackH = ph - HEADER_H
            val thumbH = max(20f, trackH * (ph / ch))
            val thumbY = p.y + HEADER_H + (trackH - thumbH) * (p.scroll / (ch - ph))
            rect(c, p.x + PANEL_W - 3, thumbY, 2f, thumbH, ACCENT)
        }
    }

    private fun drawModule(c: GuiGraphicsExtractor, p: Panel, m: ClientModule, x: Float, y: Float, w: Float) {
        if (lastMx in x..(x + w) && lastMy in y..(y + MODULE_H)) {
            rect(c, x, y, w, MODULE_H, ROW_HOVER)
        }
        rect(c, x + 4, y + 6, 4f, 4f, if (m.enabled) ACCENT else DOT_OFF)

        val name = splitName(m.name)
        val drawColor = if (m.enabled) TEXT_ON else TEXT_OFF
        text(c, name, (x + 13).toInt(), (y + 4).toInt(), drawColor)

        try {
            if (!m.bind.isUnbound) {
                val bindText = "[${safeStr(m.bind.boundKey.displayName.string)}]"
                text(c, bindText, (x + w - mc.font.width(bindText) - 13).toInt(), (y + 5).toInt(), TEXT_DIM)
            }
        } catch (_: Throwable) {}

        text(c, if (m in p.openModules) "-" else "+", (x + w - 9).toInt(), (y + 4).toInt(), TEXT_DIM)

        hits.add(Hit(x, y, w, MODULE_H, Kind.MODULE_ROW, module = m))

        if (m !in p.openModules) return
        var sy = y + MODULE_H
        for (v in m.containedValues.filter(::isVisible)) {
            val sh = measureValue(v).toFloat()
            if (sh > 0f) {
                try {
                    drawValue(c, v, x + 8, sy, w - 14)
                } catch (_: Throwable) {}
                sy += sh
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun drawValue(c: GuiGraphicsExtractor, v: Value<*>, x: Float, y: Float, w: Float) {
        val name = splitName(v.name)
        when {
            v is ModeValueGroup<*> -> {
                val modeTag = safeStr(v.activeMode.tag)
                row(c, "Mode: $name: $modeTag", x, y, w)
                hits.add(Hit(x, y, w, 14f, Kind.CHOOSE, value = v))
                var cy = y + 16
                for (sv in v.activeMode.containedValues.filter(::isVisible)) {
                    val sh = measureValue(sv).toFloat()
                    try { drawValue(c, sv, x + 8, cy, w - 10) } catch (_: Throwable) {}
                    cy += sh
                }
            }
            v is ToggleableValueGroup -> {
                checkRow(c, name, v.enabled, x, y, w)
                hits.add(Hit(x, y, w, 14f, Kind.BOOLEAN, value = v.enabledValue))
                if (v.enabled) {
                    var cy = y + 15
                    for (sv in v.containedValues.filter { it !== v.enabledValue && isVisible(it) }) {
                        val sh = measureValue(sv).toFloat()
                        if (sh > 0f) {
                            try { drawValue(c, sv, x + 8, cy, w - 10) } catch (_: Throwable) {}
                            cy += sh
                        }
                    }
                }
            }
            v is ValueGroup -> {
                text(c, name, x.toInt(), y.toInt(), TEXT_DIM)
                var cy = y + 12
                for (sv in v.containedValues.filter(::isVisible)) {
                    val sh = measureValue(sv).toFloat()
                    if (sh > 0f) {
                        try { drawValue(c, sv, x + 8, cy, w - 10) } catch (_: Throwable) {}
                        cy += sh
                    }
                }
            }
            v.valueType == ValueType.BOOLEAN -> {
                val on = (v as? Value<Boolean>)?.get() ?: false
                checkRow(c, name, on, x, y, w)
                hits.add(Hit(x, y, w, 14f, Kind.BOOLEAN, value = v))
            }
            v.valueType == ValueType.INT || v.valueType == ValueType.FLOAT -> {
                val rv = v as RangedValue<*>
                val cur = (v.get() as? Number)?.toFloat() ?: 0f
                val isInt = v.valueType == ValueType.INT
                text(c, "$name: ${fmt(cur, isInt)}${safeStr(rv.suffix)}", x.toInt(), y.toInt(), TEXT_DIM)
                sliderTrack(c, rv, x, y + 12, w, cur)
                hits.add(Hit(x, y + 10, w, 14f, Kind.SLIDER, value = v))
            }
            v.valueType == ValueType.INT_RANGE || v.valueType == ValueType.FLOAT_RANGE -> {
                val rv = v as RangedValue<*>
                val r = v.get() as? ClosedRange<*> ?: 0..0
                val s = (r.start as? Number)?.toFloat() ?: 0f
                val e = (r.endInclusive as? Number)?.toFloat() ?: 0f
                val isInt = v.valueType == ValueType.INT_RANGE
                text(c, "$name: ${fmt(s, isInt)} ~ ${fmt(e, isInt)}${safeStr(rv.suffix)}", x.toInt(), y.toInt(), TEXT_DIM)
                sliderTrack(c, rv, x, y + 13, w, s)
                sliderTrack(c, rv, x, y + 23, w, e)
                hits.add(Hit(x, y + 8, w, 24f, Kind.RANGE, value = v))
            }
            v.valueType == ValueType.TEXT -> {
                text(c, name, x.toInt(), y.toInt(), TEXT_DIM)
                rect(c, x, y + 10, w, 11f, 0xFF1D1D22.toInt())
                rect(c, x, y + 10, w, 1f, if (editingText === v) ACCENT else BORDER)
                val raw = safeStr(v.get())
                val shown = if (editingText === v) editBuffer.toString() + "_" else raw
                text(c, shown, (x + 3).toInt(), (y + 12).toInt(), TEXT_ON)
                hits.add(Hit(x, y, w, 22f, Kind.TEXT, value = v))
            }
            v.valueType == ValueType.BIND -> {
                val bind = (v as? Value<InputBind>)?.get()
                val s = if (listeningBind === v) "> press key <"
                else "$name: ${if (bind == null || bind.isUnbound) "none" else safeStr(bind.boundKey.displayName.string)}"
                row(c, s, x, y, w)
                hits.add(Hit(x, y, w, 20f, Kind.LISTEN_BIND, value = v))
            }
            v.valueType == ValueType.KEY -> {
                val key = (v as? Value<InputConstants.Key>)?.get()
                val s = if (listeningKey === v) "> press key <" else "$name: ${safeStr(key?.displayName?.string)}"
                row(c, s, x, y, w)
                hits.add(Hit(x, y, w, 20f, Kind.LISTEN_KEY, value = v))
            }
            v.valueType == ValueType.CHOOSE -> {
                val cur = (v as? ChoiceListValue<*>)?.get()
                val tag = safeStr((cur as? Tagged)?.tag ?: cur)
                row(c, "$name: $tag", x, y, w)
                hits.add(Hit(x, y, w, 14f, Kind.CHOOSE, value = v))
            }
            v.valueType == ValueType.MULTI_CHOOSE -> {
                text(c, name, x.toInt(), y.toInt(), TEXT_DIM)
                val mv = v as? MultiChoiceListValue<*> ?: return
                val selected = mv.get()
                mv.choices.toList().forEachIndexed { i, choice ->
                    val cy = y + 12 + i * 13
                    val isSel = choice in selected
                    val choiceTag = safeStr((choice as? Tagged)?.tag ?: choice)
                    rect(c, x + 2, cy + 2, 6f, 6f, if (isSel) ACCENT else DOT_OFF)
                    text(c, choiceTag, (x + 12).toInt(), cy.toInt(), if (isSel) TEXT_ON else TEXT_OFF)
                    hits.add(Hit(x, cy, w, 13f, Kind.MULTI_ITEM, value = v, index = i))
                }
            }
            v.valueType == ValueType.COLOR -> {
                text(c, name, x.toInt(), y.toInt(), TEXT_DIM)
                val col = v.get() as? Color4b ?: return
                listOf(col.r, col.g, col.b, col.a).forEachIndexed { i, comp ->
                    val cy = y + 10 + i * 8
                    rect(c, x, cy, w * ((comp and 0xFF) / 255f), 3f, ACCENT)
                    hits.add(Hit(x, cy - 2, w, 7f, Kind.SLIDER, value = v, index = i + 1))
                }
            }
            else -> {
                val s = safeStr(v.getValue()).let { if (it.length > 42) it.take(39) + "..." else it }
                text(c, "$name: $s", x.toInt(), y.toInt(), TEXT_DIM)
            }
        }
    }

    private fun rect(c: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Float, color: Int) {
        if (w <= 0f || h <= 0f) return
        c.fill(x.toInt(), y.toInt(), (x + w).toInt(), (y + h).toInt(), color)
    }

    private fun text(c: GuiGraphicsExtractor, s: String, x: Int, y: Int, color: Int) {
        c.text(mc.font, s, x, y, color)
    }

    private fun row(c: GuiGraphicsExtractor, s: String, x: Float, y: Float, w: Float) {
        if (lastMx in x..(x + w) && lastMy in y..(y + 14)) rect(c, x, y, w, 14f, ROW_HOVER)
        text(c, s, (x + 2).toInt(), (y + 3).toInt(), TEXT_OFF)
    }

    private fun checkRow(c: GuiGraphicsExtractor, name: String, on: Boolean, x: Float, y: Float, w: Float) {
        if (lastMx in x..(x + w) && lastMy in y..(y + 14)) rect(c, x, y, w, 14f, ROW_HOVER)
        rect(c, x + 2, y + 4, 6f, 6f, if (on) ACCENT else DOT_OFF)
        text(c, name, (x + 12).toInt(), (y + 3).toInt(), if (on) TEXT_ON else TEXT_OFF)
    }

    private fun sliderTrack(c: GuiGraphicsExtractor, rv: RangedValue<*>, x: Float, y: Float, w: Float, value: Float) {
        val lo: Double = (rv.range.start as? Number)?.toDouble() ?: 0.0
        val hi: Double = (rv.range.endInclusive as? Number)?.toDouble() ?: 0.0
        val vD: Double = value.toDouble()
        val span: Double = hi - lo
        val t: Float = if (span == 0.0) 0f else ((vD - lo) / span).coerceIn(0.0, 1.0).toFloat()
        rect(c, x, y, w, 2f, DOT_OFF)
        rect(c, x, y, w * t, 2f, ACCENT)
        rect(c, x + w * t - 1, y - 2, 3f, 6f, TEXT_ON)
    }

    private fun fmt(v: Float, isInt: Boolean) = if (isInt) v.roundToInt().toString() else "%.2f".format(v)

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = event.x().toFloat()
        val my = event.y().toFloat()
        val button = event.button()

        if (themeMode) {
            // 先判断 [Reset] 按钮
            val panelH = 30f + ClickGuiTheme.COUNT * 30f + 40f
            val px = (this.width - THEME_PANEL_W) / 2f
            val py = (this.height - panelH) / 2f
            val resetW = 60f
            val resetH = 14f
            val resetX = px + (THEME_PANEL_W - resetW) / 2f
            val resetY = py + panelH - resetH - 8f
            if (button == 0 && mx in resetX..(resetX + resetW) && my in resetY..(resetY + resetH)) {
                resetTheme()
                return true
            }

            val hit = themeHits.lastOrNull { mx in it.x..(it.x + it.w) && my in it.y..(it.y + it.h) }
            if (hit != null) {
                draggingThemeSlider = hit
                updateThemeSlider(hit, mx)
            }
            return true
        }

        val sbX = searchBoxX()
        val sbY = SEARCH_Y
        val inSearchBox = mx in sbX..(sbX + SEARCH_W) && my in sbY..(sbY + SEARCH_H)
        if (inSearchBox) {
            if (button == 0) {
                val clearX = sbX + SEARCH_W - 12f
                if (searchQuery.isNotEmpty() && mx >= clearX) {
                    searchQuery = ""
                    searchFocused = true
                } else {
                    searchFocused = true
                }
                return true
            }
        }
        if (searchFocused) {
            searchFocused = false
        }

        listeningBind?.let { v ->
            val key = InputConstants.Type.MOUSE.getOrCreate(button)
            v.set(InputBind(key, v.get().action, v.get().modifiers))
            listeningBind = null
            return true
        }
        if (listeningKey != null) { listeningKey = null; return true }
        if (editingText != null && button == 0) commitText()

        val (tbX, tbY, tbW, tbH) = themeBtnRect()
        if (button == 0 && mx in tbX..(tbX + tbW) && my in tbY..(tbY + tbH)) {
            themeMode = true
            return true
        }

        val insidePanel = panels.any { p ->
            val ph = panelHeight(p)
            mx >= p.x && mx <= p.x + PANEL_W &&
                my >= p.y && my <= p.y + ph
        }
        if (!insidePanel) return super.mouseClicked(event, doubleClick)

        for (p in panels) {
            if (mx in p.x..(p.x + PANEL_W) && my in p.y..(p.y + HEADER_H)) {
                if (button == 1) {
                    if (!p.minimized) {
                        p.savedOpenModules = HashSet(p.openModules)
                        p.openModules.clear()
                        p.minimized = true
                    } else {
                        p.minimized = false
                        p.openModules.addAll(p.savedOpenModules)
                    }
                    clampScroll(p)
                    return true
                }
                if (button == 0) {
                    dragPanel = p
                    dragOffX = mx - p.x
                    dragOffY = my - p.y
                    return true
                }
            }
        }

        val hit = hits.lastOrNull { mx in it.x..(it.x + it.w) && my in it.y..(it.y + it.h) }
        when (hit?.kind) {
            Kind.MODULE_ROW -> {
                val m = hit.module ?: return true
                if (button == 0) {
                    m.enabled = !m.enabled
                } else if (button == 1) {
                    val p = panels.firstOrNull { it.categoryName == m.category.tag } ?: return true
                    if (m in p.openModules) p.openModules.remove(m) else p.openModules.add(m)
                    clampScroll(p)
                }
                return true
            }
            Kind.BOOLEAN -> {
                val bv = hit.value as? Value<Boolean> ?: return true
                bv.set(!bv.get())
                return true
            }
            Kind.SLIDER -> { activeSlider = hit; updateSlider(hit, mx); return true }
            Kind.RANGE -> { activeSlider = hit; updateRange(hit, mx, my); return true }
            Kind.TEXT -> {
                hit.value?.let { beginEditText(it) }
                return true
            }
            Kind.LISTEN_BIND -> { listeningBind = hit.value as? Value<InputBind>; return true }
            Kind.LISTEN_KEY -> { listeningKey = hit.value as? Value<InputConstants.Key>; return true }
            Kind.CHOOSE -> { cycleChoice(hit.value); return true }
            Kind.MULTI_ITEM -> { toggleMultiItem(hit.value, hit.index); return true }
            null -> {}
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (themeMode) {
            draggingThemeSlider = null
            return true
        }
        dragPanel = null
        activeSlider = null
        return super.mouseReleased(event)
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        val mx = event.x().toFloat()
        val my = event.y().toFloat()
        if (themeMode) {
            draggingThemeSlider?.let { updateThemeSlider(it, mx) }
            return true
        }
        dragPanel?.let {
            val ph = panelHeight(it)
            it.x = (mx - dragOffX).coerceIn(0f, this.width - PANEL_W)
            val minY = -(ph - HEADER_H)
            val maxY = this.height - HEADER_H
            it.y = (my - dragOffY).coerceIn(minY, maxY)
        }
        activeSlider?.let {
            when (it.kind) {
                Kind.SLIDER -> updateSlider(it, mx)
                Kind.RANGE -> updateRange(it, mx, my)
                else -> {}
            }
        }
        return super.mouseDragged(event, dx, dy)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (themeMode) return true
        val mx = mouseX.toFloat()
        val my = mouseY.toFloat()
        for (p in panels) {
            if (p.minimized) continue
            if (mx in p.x..(p.x + PANEL_W) && my in p.y..(p.y + panelHeight(p))) {
                p.scroll -= scrollY.toFloat() * 14f
                clampScroll(p)
                return true
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val keyCode: Int = event.key

        if (themeMode) {
            if (keyCode == InputConstants.KEY_ESCAPE) {
                themeMode = false
                // 退出编辑模式时保存一次磁盘
                saveThemeToDisk()
                return true
            }
            return true
        }

        //搜索框聚焦时
        if (searchFocused) {
            when {
                keyCode == InputConstants.KEY_ESCAPE -> {
                    if (searchQuery.isNotEmpty()) searchQuery = ""
                    else searchFocused = false
                    return true
                }
                event.isConfirmation -> {
                    searchFocused = false
                    return true
                }
                keyCode == InputConstants.KEY_BACKSPACE -> {
                    if (searchQuery.isNotEmpty()) searchQuery = searchQuery.dropLast(1)
                    return true
                }
                keyCode == InputConstants.KEY_DELETE -> {
                    searchQuery = ""
                    return true
                }
                else -> return true
            }
        }

        listeningBind?.let { v ->
            when {
                keyCode == InputConstants.KEY_ESCAPE -> { /* 取消 */ }
                keyCode == InputConstants.KEY_BACKSPACE -> v.set(InputBind.UNBOUND)
                else -> {
                    val boundKey = InputConstants.Type.KEYSYM.getOrCreate(keyCode)
                    v.set(InputBind(boundKey, v.get().action, v.get().modifiers))
                }
            }
            listeningBind = null
            return true
        }
        listeningKey?.let { v ->
            if (keyCode != InputConstants.KEY_ESCAPE) {
                v.set(InputConstants.Type.KEYSYM.getOrCreate(keyCode))
            }
            listeningKey = null
            return true
        }
        editingText?.let {
            when {
                event.isConfirmation -> { commitText(); return true }
                keyCode == InputConstants.KEY_ESCAPE -> { cancelEditText(); return true }
                keyCode == InputConstants.KEY_BACKSPACE -> {
                    if (editBuffer.isNotEmpty()) editBuffer.deleteCharAt(editBuffer.length - 1); return true
                }
                keyCode == InputConstants.KEY_DELETE -> { editBuffer.clear(); return true }
                else -> return true
            }
        }
        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (themeMode) return true
        if (searchFocused) {
            val ch = event.codepoint.toChar()
            if (!ch.isISOControl() && searchQuery.length < 64) {
                searchQuery += ch
            }
            return true
        }
        editingText?.let {
            val ch = event.codepoint.toChar()
            if (!ch.isISOControl()) editBuffer.append(ch)
            return true
        }
        return super.charTyped(event)
    }

    @Suppress("UNCHECKED_CAST")
    private fun commitText() {
        val target = editingText ?: return
        try {
            (target as? Value<String>)?.set(editBuffer.toString())
        } catch (_: Throwable) {}
        editingText = null
        editBuffer.clear()
    }

    // 主题滑块逻辑
    private fun updateThemeSlider(hit: ThemeHit, mx: Float) {
        val t = ((mx - hit.x) / hit.w).coerceIn(0f, 1f)
        val v = (t * 255f).roundToInt()
        val cur = theme.getColor(hit.colorIdx)
        val a = (cur ushr 24) and 0xFF
        val r = (cur ushr 16) and 0xFF
        val g = (cur ushr 8) and 0xFF
        val b = cur and 0xFF
        val newColor = when (hit.channel) {
            0 -> (a shl 24) or (v shl 16) or (g shl 8) or b
            1 -> (a shl 24) or (r shl 16) or (v shl 8) or b
            2 -> (a shl 24) or (r shl 16) or (g shl 8) or v
            else -> (v shl 24) or (r shl 16) or (g shl 8) or b
        }
        theme.setColor(hit.colorIdx, newColor)
    }

    // 控件逻辑
    @Suppress("UNCHECKED_CAST")
    private fun updateSlider(hit: Hit, mx: Float) {
        val v: Value<*> = hit.value ?: return
        val t: Float = ((mx - hit.x) / hit.w).coerceIn(0f, 1f)

        when (v.valueType) {
            ValueType.INT -> {
                val rv: RangedValue<*> = v as RangedValue<*>
                val loN: Number = rv.range.start as? Number ?: return
                val hiN: Number = rv.range.endInclusive as? Number ?: return
                val loF: Float = loN.toFloat()
                val hiF: Float = hiN.toFloat()
                val resultF: Float = loF + (hiF - loF) * t
                val result: Int = resultF.roundToInt()
                try { (v as Value<Int>).set(result) } catch (_: Throwable) {}
            }
            ValueType.FLOAT -> {
                val rv: RangedValue<*> = v as RangedValue<*>
                val loN: Number = rv.range.start as? Number ?: return
                val hiN: Number = rv.range.endInclusive as? Number ?: return
                val loF: Float = loN.toFloat()
                val hiF: Float = hiN.toFloat()
                val result: Float = loF + (hiF - loF) * t
                try { (v as Value<Float>).set(result) } catch (_: Throwable) {}
            }
            ValueType.COLOR -> {
                val cv: Value<Color4b> = v as? Value<Color4b> ?: return
                val col: Color4b = cv.get()
                val nv: Int = (t * 255f).roundToInt()
                val newCol: Color4b = when (hit.index) {
                    1 -> Color4b(nv, col.g, col.b, col.a)
                    2 -> Color4b(col.r, nv, col.b, col.a)
                    3 -> Color4b(col.r, col.g, nv, col.a)
                    else -> Color4b(col.r, col.g, col.b, nv)
                }
                cv.set(newCol)
            }
            else -> {}
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun updateRange(hit: Hit, mx: Float, my: Float) {
        val v: Value<*> = hit.value ?: return
        val rv: RangedValue<*> = v as RangedValue<*>

        val nLo: Number = rv.range.start as? Number ?: return
        val nHi: Number = rv.range.endInclusive as? Number ?: return
        val loD: Double = nLo.toDouble()
        val hiD: Double = nHi.toDouble()

        val relX: Float = mx - hit.x
        val fracRaw: Float = relX / hit.w
        val frac: Float = fracRaw.coerceIn(0f, 1f)
        val fracD: Double = frac.toDouble()

        val spanD: Double = hiD - loD
        val offsD: Double = spanD * fracD
        val targetD: Double = loD + offsD

        val old: ClosedRange<*> = v.get() as? ClosedRange<*> ?: return
        val nOldS: Number = old.start as? Number ?: return
        val nOldE: Number = old.endInclusive as? Number ?: return
        val oldSD: Double = nOldS.toDouble()
        val oldED: Double = nOldE.toDouble()

        val dragFrom: Boolean = my < (hit.y + hit.h / 2f)
        val isInt: Boolean = v.valueType == ValueType.INT_RANGE

        if (isInt) {
            val targetI: Int = targetD.roundToInt()
            val oldSI: Int = oldSD.toInt()
            val oldEI: Int = oldED.toInt()
            val s: Int
            val e: Int
            if (dragFrom) {
                s = if (targetI < oldEI) targetI else oldEI
                e = oldEI
            } else {
                s = oldSI
                e = if (targetI > oldSI) targetI else oldSI
            }
            try { (v as Value<Any>).set(s..e) } catch (_: Throwable) {}
        } else {
            val targetF: Float = targetD.toFloat()
            val oldSF: Float = oldSD.toFloat()
            val oldEF: Float = oldED.toFloat()
            val s: Float
            val e: Float
            if (dragFrom) {
                s = if (targetF < oldEF) targetF else oldEF
                e = oldEF
            } else {
                s = oldSF
                e = if (targetF > oldSF) targetF else oldSF
            }
            try { (v as Value<Any>).set(s..e) } catch (_: Throwable) {}
        }
    }

    private fun cycleChoice(v: Value<*>?) {
        try {
            when (v) {
                is ModeValueGroup<*> -> {
                    val modes = v.modes
                    if (modes.isEmpty()) return
                    val next = modes[(modes.indexOf(v.activeMode) + 1) % modes.size]
                    v.setByString(next.name)
                }
                is ChoiceListValue<*> -> {
                    val list = v.choices.toList()
                    if (list.isEmpty()) return
                    val next = list[(list.indexOf(v.get()) + 1) % list.size]
                    (v as Value<Any>).set(next)
                }
            }
        } catch (_: Throwable) {}
    }

    @Suppress("UNCHECKED_CAST")
    private fun toggleMultiItem(v: Value<*>?, index: Int) {
        val mv = v as? MultiChoiceListValue<*> ?: return
        val choice = mv.choices.toList().getOrNull(index) ?: return
        try {
            val currentSet = LinkedHashSet(mv.get() as Collection<Any>)
            if (choice in currentSet) currentSet.remove(choice) else currentSet.add(choice)
            (mv as Value<Any>).set(currentSet)
        } catch (_: Throwable) {}
    }
}
