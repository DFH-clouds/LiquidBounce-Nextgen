package net.ccbluex.liquidbounce.features.module.modules.render.clickgui

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import net.minecraft.client.renderer.RenderPipelines
import com.mojang.blaze3d.platform.InputConstants
import net.ccbluex.liquidbounce.LiquidBounce
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
import net.ccbluex.liquidbounce.render.drawRoundedRect
import net.ccbluex.liquidbounce.render.drawTexQuad
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.input.InputBind
import net.ccbluex.liquidbounce.utils.render.readNativeImage
import net.ccbluex.liquidbounce.utils.render.registerTexture
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import java.awt.Desktop
import java.io.File
import java.nio.file.Path
import kotlin.math.*

class ClickGuiHCS : Screen(Component.literal("ClickGUI")) {

    enum class LayoutStyle(val displayName: String) {
        HCS("HCS Panels"),
        FDP("FDP Compact"),
        YZY("YZY Cards"),
        NEVERLOSE("Neverlose")
    }

    class ClickGuiTheme {
        var accent = 0xFF00D2FF.toInt()
        var panelBg = 0xF016161A.toInt()
        var headerBg = 0xFF101013.toInt()
        var border = 0xFF2C2C33.toInt()
        var rowHover = 0xFF1B1B21.toInt()
        var textOn = 0xFFE0E0E0.toInt()
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
            accent = 0xFF00D2FF.toInt()
            panelBg = 0xF016161A.toInt()
            headerBg = 0xFF101013.toInt()
            border = 0xFF2C2C33.toInt()
            rowHover = 0xFF1B1B21.toInt()
            textOn = 0xFFE0E0E0.toInt()
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
        private val SAVED_SCROLL: MutableMap<String, Float> = HashMap()
        private var SAVED_COMPACT_SCROLL: Float = 0f
        private var SAVED_GRID_SCROLL: Float = 0f
        private var SAVED_SEARCH: String = ""
        private var initialized: Boolean = false
        private var currentStyle: LayoutStyle = LayoutStyle.HCS

        private var NL_X: Float = 80f
        private var NL_Y: Float = 60f
        private var NL_CAT: String = "Combat"
        private var NL_SCROLL: Float = 0f

        val SHARED_THEME = ClickGuiTheme()

        private var themeLoaded = false
        private var guiConfigLoaded = false

        private val BG_TEXTURE_ID: Identifier =
            Identifier.fromNamespaceAndPath("liquidbounce", "nativeclickgui/background")

        private val BG_FIT_NAMES = listOf("Fill", "Fit", "Stretch", "Tile")

        private fun themeFile(): File {
            val dir = File(mc.gameDirectory, "config/liquidbounce")
            if (!dir.exists()) dir.mkdirs()
            return File(dir, "nativeclickgui-theme.txt")
        }

        fun loadThemeFromDisk() {
            if (themeLoaded) return
            themeLoaded = true
            try {
                val f = themeFile()
                if (!f.exists()) return
                for (line in f.readLines()) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                    val parts = trimmed.split("=", limit = 2)
                    if (parts.size != 2) continue
                    val key = parts[0].trim()
                    val value = parts[1].trim().toLongOrNull(16)?.toInt() ?: continue
                    val idx = ClickGuiTheme.NAMES.indexOf(key)
                    if (idx >= 0) SHARED_THEME.setColor(idx, value)
                }
            } catch (_: Throwable) {
            }
        }

        fun saveThemeToDisk() {
            try {
                val sb = StringBuilder()
                sb.append("# NativeClickGui Theme\n")
                sb.append("# Format: Name=HEX_ARGB\n")
                for (i in 0 until ClickGuiTheme.COUNT) {
                    sb.append(ClickGuiTheme.NAMES[i]).append("=")
                        .append("%08X".format(SHARED_THEME.getColor(i))).append("\n")
                }
                themeFile().writeText(sb.toString())
            } catch (_: Throwable) {
            }
        }

        fun resetTheme() {
            SHARED_THEME.resetToDefault()
            saveThemeToDisk()
        }

        private fun guiConfigFile(): File {
            val dir = File(mc.gameDirectory, "config/liquidbounce")
            if (!dir.exists()) dir.mkdirs()
            return File(dir, "nativeclickgui-gui.txt")
        }

        fun loadGuiConfig() {
            if (guiConfigLoaded) return
            guiConfigLoaded = true
            try {
                val f = guiConfigFile()
                if (!f.exists()) return
                for (line in f.readLines()) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                    val parts = trimmed.split("=", limit = 2)
                    if (parts.size != 2) continue
                    when (parts[0].trim()) {
                        "style" -> currentStyle = runCatching { LayoutStyle.valueOf(parts[1].trim()) }
                            .getOrDefault(LayoutStyle.HCS)

                        "nlx" -> parts[1].trim().toFloatOrNull()?.let { NL_X = it }
                        "nly" -> parts[1].trim().toFloatOrNull()?.let { NL_Y = it }
                        "nlcat" -> NL_CAT = parts[1].trim()
                    }
                }
            } catch (_: Throwable) {
            }
        }

        fun saveGuiConfig() {
            try {
                val sb = StringBuilder()
                sb.append("# NativeClickGui GUI Config\n")
                sb.append("style=").append(currentStyle.name).append("\n")
                sb.append("nlx=").append(NL_X).append("\n")
                sb.append("nly=").append(NL_Y).append("\n")
                sb.append("nlcat=").append(NL_CAT).append("\n")
                guiConfigFile().writeText(sb.toString())
            } catch (_: Throwable) {
            }
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

    private enum class Kind {
        MODULE_ROW, BOOLEAN, SLIDER, RANGE, TEXT,
        LISTEN_BIND, LISTEN_KEY, CHOOSE, MULTI_ITEM
    }

    private class Hit(
        val x: Float, val y: Float, val w: Float, val h: Float,
        val kind: Kind,
        val value: Value<*>? = null,
        val module: ClientModule? = null,
        val index: Int = 0
    )

    private class ThemeHit(
        val x: Float, val y: Float, val w: Float, val h: Float,
        val colorIdx: Int, val channel: Int
    )

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

    private var bgMode = false
    private var bgEnabled = true
    private var bgDim = 150
    private var bgFit = 0
    private var bgTexture: DynamicTexture? = null
    private var bgLoadTried = false
    private var bgLoadFailed = false
    private var pendingBgReload = false
    private var bgStatusMsg: String? = null
    private var bgStatusUntil = 0L

    private class BgHit(val x: Float, val y: Float, val w: Float, val h: Float, val action: Int)

    private val bgHits = ArrayList<BgHit>()
    private var draggingBgDim = false

    private var searchQuery = ""
    private var searchFocused = false
    private var hoveredModule: ClientModule? = null

    private var compactScroll = 0f
    private var gridScroll = 0f
    private var compactTargetScroll = 0f
    private var gridTargetScroll = 0f

    private val globalOpenModules = HashSet<ClientModule>()

    private var nlDragging = false
    private var nlDragOffX = 0f
    private var nlDragOffY = 0f
    private var nlScroll = 0f
    private var nlCategory = "Combat"

    private var nlHitCategory = ""
    private var nlHitHeader = false
    private var nlHitClose = false

    private fun Int.toColor4b(): Color4b {
        return Color4b(
            (this shr 16) and 0xFF,
            (this shr 8) and 0xFF,
            this and 0xFF,
            (this ushr 24) and 0xFF
        )
    }

    private fun isEditingSomething(): Boolean =
        editingText != null || searchFocused

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

        loadThemeFromDisk()
        loadGuiConfig()
        loadBgConfig()

        nlCategory = NL_CAT
        nlScroll = NL_SCROLL

        val order = listOf(
            "Combat", "Exploit", "Fun", "Misc", "Movement",
            "Player", "Render", "World"
        )
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
                for (m in modulesInRaw(cat)) {
                    if (m.name in savedOpen) p.openModules.add(m)
                }
            }

            val savedSaved = SAVED_SAVED_OPEN[cat]
            if (savedSaved != null && savedSaved.isNotEmpty()) {
                for (m in modulesInRaw(cat)) {
                    if (m.name in savedSaved) p.savedOpenModules.add(m)
                }
            }

            p.scroll = SAVED_SCROLL[cat] ?: 0f
            clampScroll(p)

            panels.add(p)
            defaultX += PANEL_W + 8f
        }

        compactScroll = SAVED_COMPACT_SCROLL
        compactTargetScroll = compactScroll
        gridScroll = SAVED_GRID_SCROLL
        gridTargetScroll = gridScroll
        clampGlobalScroll()
    }

    private fun saveState() {
        for (p in panels) {
            SAVED_OPEN[p.categoryName] = p.openModules.map { it.name }.toMutableSet()
            SAVED_SAVED_OPEN[p.categoryName] = p.savedOpenModules.map { it.name }.toMutableSet()
            SAVED_MINIMIZED[p.categoryName] = p.minimized
            SAVED_POS[p.categoryName] = floatArrayOf(p.x, p.y)
            SAVED_SCROLL[p.categoryName] = p.scroll
        }
        SAVED_SEARCH = searchQuery
        SAVED_COMPACT_SCROLL = compactScroll
        SAVED_GRID_SCROLL = gridScroll
        NL_CAT = nlCategory
        NL_SCROLL = nlScroll

        saveThemeToDisk()
        saveGuiConfig()
        saveBgConfig()
    }

    private fun measureValue(v: Value<*>): Int = when {
        !isVisible(v) -> 0
        v is ModeValueGroup<*> ->
            16 + v.activeMode.containedValues.filter(::isVisible).sumOf { measureValue(it) }

        v is ToggleableValueGroup ->
            14 + if (v.enabled) v.containedValues
                .filter { it !== v.enabledValue && isVisible(it) }
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

    private fun measureModuleHcs(m: ClientModule, openSet: Set<ClientModule>): Int {
        if (m !in openSet) return 16
        return 16 + m.containedValues.filter(::isVisible).sumOf { measureValue(it) }
    }

    private fun contentHeightHcs(p: Panel) =
        HEADER_H + modulesIn(p.categoryName).sumOf { measureModuleHcs(it, p.openModules) } + 4f

    private fun panelHeight(p: Panel): Float {
        if (p.minimized) return HEADER_H
        val content = contentHeightHcs(p)
        val cap = (this.height * 0.75f).coerceIn(180f, 500f)
        return min(content, cap).coerceAtLeast(HEADER_H + 4f)
    }

    private fun clampScroll(p: Panel) {
        if (p.minimized) {
            p.scroll = 0f; return
        }
        p.scroll = p.scroll.coerceIn(0f, max(0f, contentHeightHcs(p) - panelHeight(p)))
    }

    private fun measureModuleGlobal(m: ClientModule): Int {
        if (m !in globalOpenModules) return 22
        return 22 + m.containedValues.filter(::isVisible).sumOf { measureValue(it) }
    }

    private fun contentHeightGlobal(): Float {
        var h = 0f
        ModuleManager.filter { !it.hidden }
            .map { it.category.tag }
            .distinct()
            .forEach { cat ->
                val mods = modulesIn(cat)
                if (mods.isEmpty()) return@forEach
                h += 20f
                mods.forEach { h += measureModuleGlobal(it) }
                h += 8f
            }
        return h
    }

    private fun viewportHeightGlobal(): Float =
        this.height - SEARCH_TOP_PAD - 40f

    private fun clampGlobalScroll() {
        val maxScroll = max(0f, contentHeightGlobal() - viewportHeightGlobal())
        compactScroll = compactScroll.coerceIn(0f, maxScroll)
        compactTargetScroll = compactScroll
        gridScroll = gridScroll.coerceIn(0f, maxScroll)
        gridTargetScroll = gridScroll
    }

    override fun onClose() {
        saveState()
        super.onClose()
    }

    override fun removed() {
        saveState()
        super.removed()
    }

    override fun isPauseScreen(): Boolean = false

    override fun extractRenderState(
        context: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float
    ) {
        if (currentStyle != LayoutStyle.NEVERLOSE) {
            super.extractRenderState(context, mouseX, mouseY, partialTick)
        }

        ensureLayout()
        lastMx = mouseX.toFloat()
        lastMy = mouseY.toFloat()

        if (pendingBgReload) {
            pendingBgReload = false
            reloadBackground()
        }
        if (bgTexture == null && !bgLoadTried && bgImageFile().exists()) {
            bgLoadTried = true
            reloadBackground()
        }

        val bg = bgTexture
        if (currentStyle != LayoutStyle.NEVERLOSE) {
            if (bgEnabled && bg != null) {
                try {
                    drawBackgroundImage(context, bg)
                } catch (_: Throwable) {
                }
                val overlay = (bgDim.coerceIn(0, 255) shl 24) or 0x101015
                rect(context, 0f, 0f, this.width.toFloat(), this.height.toFloat(), overlay)
            } else {
                rect(context, 0f, 0f, this.width.toFloat(), this.height.toFloat(), 0xC0101015.toInt())
            }
        }

        if (bgMode) {
            drawBgEditor(context)
            return
        }

        if (themeMode) {
            drawThemeEditor(context)
            return
        }

        hits.clear()
        nlHitCategory = ""
        nlHitHeader = false
        nlHitClose = false
        hoveredModule = null

        when (currentStyle) {
            LayoutStyle.HCS -> {
                for (p in panels) {
                    clampScroll(p)
                    try {
                        drawHcsPanel(context, p)
                    } catch (_: Throwable) {
                    }
                }
            }

            LayoutStyle.FDP -> drawCompactList(context)
            LayoutStyle.YZY -> drawGridCards(context)
            LayoutStyle.NEVERLOSE -> drawNeverlose(context, mouseX, mouseY, partialTick)
        }

        try {
            drawSearchBox(context)
        } catch (_: Throwable) {
        }
        try {
            hoveredModule?.let { drawModuleTooltip(context, it) }
        } catch (_: Throwable) {
        }

        drawBottomBar(context)

        val msg = when {
            bgStatusMsg != null && System.currentTimeMillis() < bgStatusUntil -> bgStatusMsg
            listeningBind != null -> "Press a key... (Backspace: unbind, ESC: cancel)"
            listeningKey != null -> "Press a key... (ESC: cancel)"
            editingText != null -> "Typing: ${editBuffer}_ (Enter: confirm, ESC: cancel)"
            else -> null
        }
        if (msg != null) {
            text(context, msg, (this.width - mc.font.width(msg)) / 2, this.height - 28, ACCENT)
        }
    }

    private fun drawBottomBar(c: GuiGraphicsExtractor) {
        val (tbX, tbY, tbW, tbH) = themeBtnRect()
        val hoveringTheme = lastMx in tbX..(tbX + tbW) && lastMy in tbY..(tbY + tbH)
        text(c, "[DIY Theme]", tbX.toInt(), tbY.toInt(), if (hoveringTheme) ACCENT else TEXT_OFF)

        val (bgX, bgY, bgW, bgH) = bgBtnRect()
        val hoveringBg = lastMx in bgX..(bgX + bgW) && lastMy in bgY..(bgY + bgH)
        val bgActive = bgEnabled && bgTexture != null
        text(
            c, "[BG]", bgX.toInt(), bgY.toInt(),
            if (hoveringBg) ACCENT else if (bgActive) TEXT_ON else TEXT_OFF
        )

        val (stX, stY, stW, stH) = styleBtnRect()
        val hoveringStyle = lastMx in stX..(stX + stW) && lastMy in stY..(stY + stH)
        val styleLabel = "[Style: ${currentStyle.displayName}]"
        text(c, styleLabel, stX.toInt(), stY.toInt(), if (hoveringStyle) ACCENT else TEXT_OFF)
    }

    private fun themeBtnRect(): FloatArray {
        val label = "[DIY Theme]"
        val w = mc.font.width(label).toFloat()
        return floatArrayOf(this.width - w - 10f, this.height - 14f, w, 10f)
    }

    private fun bgBtnRect(): FloatArray {
        val w = mc.font.width("[BG]").toFloat()
        val (tX, _, _, _) = themeBtnRect()
        return floatArrayOf(tX - w - 8f, this.height - 14f, w, 10f)
    }

    private fun styleBtnRect(): FloatArray {
        val label = "[Style: ${currentStyle.displayName}]"
        val w = mc.font.width(label).toFloat()
        val (bgX, _, _, _) = bgBtnRect()
        return floatArrayOf(bgX - w - 8f, this.height - 14f, w, 10f)
    }

    private fun drawHcsPanel(c: GuiGraphicsExtractor, p: Panel) {
        val ph = panelHeight(p)
        rect(c, p.x, p.y, PANEL_W, ph, PANEL_BG)
        rect(c, p.x, p.y, PANEL_W, 1f, BORDER)
        rect(c, p.x, p.y + ph - 1, PANEL_W, 1f, BORDER)
        rect(c, p.x, p.y, 1f, ph, BORDER)
        rect(c, p.x + PANEL_W - 1, p.y, 1f, ph, BORDER)

        rect(c, p.x, p.y, PANEL_W, HEADER_H, HEADER_BG)
        text(c, p.categoryName, (p.x + 6).toInt(), (p.y + 5).toInt(), ACCENT)
        text(
            c, if (p.minimized) "+" else "-",
            (p.x + PANEL_W - 9).toInt(), (p.y + 5).toInt(), TEXT_DIM
        )

        if (p.minimized) return

        val sx1 = p.x.toInt()
        val sy1 = (p.y + HEADER_H).toInt()
        val sx2 = (p.x + PANEL_W).toInt()
        val sy2 = (p.y + ph).toInt()

        if (sx2 > sx1 && sy2 > sy1) {
            c.enableScissor(sx1, sy1, sx2, sy2)
            try {
                var y = p.y + HEADER_H - p.scroll
                val x = p.x + 2
                val w = PANEL_W - 4
                for (m in modulesIn(p.categoryName)) {
                    val mh = measureModuleHcs(m, p.openModules).toFloat()
                    if (y + mh > p.y + HEADER_H && y < p.y + ph) {
                        try {
                            drawModuleRow(c, p, m, x, y, w)
                        } catch (_: Throwable) {
                        }
                    }
                    y += mh
                }
            } finally {
                c.disableScissor()
            }
        }

        val ch = contentHeightHcs(p)
        if (ch > ph) {
            val trackH = ph - HEADER_H
            val thumbH = max(20f, trackH * (ph / ch))
            val thumbY = p.y + HEADER_H + (trackH - thumbH) * (p.scroll / (ch - ph))
            rect(c, p.x + PANEL_W - 3, thumbY, 2f, thumbH, ACCENT)
        }
    }

    private fun drawModuleRow(
        c: GuiGraphicsExtractor, p: Panel, m: ClientModule,
        x: Float, y: Float, w: Float
    ) {
        if (lastMx in x..(x + w) && lastMy in y..(y + MODULE_H)) {
            rect(c, x, y, w, MODULE_H, ROW_HOVER)
            hoveredModule = m
        }
        rect(c, x + 4, y + 6, 4f, 4f, if (m.enabled) ACCENT else DOT_OFF)

        val name = splitName(m.name)
        val drawColor = if (m.enabled) TEXT_ON else TEXT_OFF
        text(c, name, (x + 13).toInt(), (y + 4).toInt(), drawColor)

        try {
            if (!m.bind.isUnbound) {
                val bindText = "[${safeStr(m.bind.boundKey.displayName.string)}]"
                text(
                    c, bindText, (x + w - mc.font.width(bindText) - 13).toInt(),
                    (y + 5).toInt(), TEXT_DIM
                )
            }
        } catch (_: Throwable) {
        }

        hits.add(Hit(x, y, w, MODULE_H, Kind.MODULE_ROW, module = m))

        if (m !in p.openModules) return
        var sy = y + MODULE_H
        for (v in m.containedValues.filter(::isVisible)) {
            val sh = measureValue(v).toFloat()
            if (sh > 0f) {
                try {
                    drawValue(c, v, x + 4, sy, w - 10)
                } catch (_: Throwable) {
                }
                sy += sh
            }
        }
    }

    private fun drawCompactList(c: GuiGraphicsExtractor) {
        val top = SEARCH_TOP_PAD
        val bottom = this.height - 40f
        val viewportH = bottom - top
        val x = 10f
        val w = this.width - 20f

        if (bottom <= top) return

        c.enableScissor(0, top.toInt(), this.width, bottom.toInt())
        try {
            var y = top - compactScroll
            ModuleManager.filter { !it.hidden }
                .map { it.category.tag }
                .distinct()
                .forEach { cat ->
                    val mods = modulesIn(cat)
                    if (mods.isEmpty()) return@forEach

                    rect(c, x, y, w, 20f, HEADER_BG)
                    text(c, cat, (x + 8).toInt(), (y + 6).toInt(), ACCENT)
                    y += 20f

                    mods.forEach { m ->
                        val isOpen = m in globalOpenModules
                        val rowH = measureModuleGlobal(m).toFloat()
                        val hover = lastMx in x..(x + w) && lastMy in y..(y + 22f)

                        if (hover) {
                            rect(c, x, y, w, rowH, ROW_HOVER)
                            hoveredModule = m
                        }
                        rect(c, x + 2, y + 6, 4f, 4f, if (m.enabled) ACCENT else DOT_OFF)

                        val name = splitName(m.name)
                        text(
                            c, name, (x + 12).toInt(), (y + 7).toInt(),
                            if (m.enabled) TEXT_ON else TEXT_OFF
                        )

                        try {
                            if (!m.bind.isUnbound) {
                                val bindText = "[${safeStr(m.bind.boundKey.displayName.string)}]"
                                text(
                                    c, bindText, (x + w - mc.font.width(bindText) - 20).toInt(),
                                    (y + 7).toInt(), TEXT_DIM
                                )
                            }
                        } catch (_: Throwable) {
                        }

                        hits.add(Hit(x, y, w, 22f, Kind.MODULE_ROW, module = m))

                        if (isOpen) {
                            var sy = y + 22f
                            for (v in m.containedValues.filter(::isVisible)) {
                                val sh = measureValue(v).toFloat()
                                if (sh > 0f) {
                                    try {
                                        drawValue(c, v, x + 16, sy, w - 28)
                                    } catch (_: Throwable) {
                                    }
                                    sy += sh
                                }
                            }
                        }
                        y += rowH
                    }
                    y += 8f
                }
        } finally {
            c.disableScissor()
        }

        val ch = contentHeightGlobal()
        if (ch > viewportH) {
            val thumbH = max(20f, viewportH * (viewportH / ch))
            val thumbY = top + (viewportH - thumbH) * (compactScroll / (ch - viewportH))
            rect(c, this.width - 6f, thumbY, 2f, thumbH, ACCENT)
        }
    }

    private fun drawGridCards(c: GuiGraphicsExtractor) {
        val top = SEARCH_TOP_PAD
        val bottom = this.height - 40f
        val viewportH = bottom - top
        val cardW = 160f
        val gap = 8f
        val leftPad = 10f
        val cols = max(1, ((this.width - leftPad * 2) / (cardW + gap)).toInt())

        if (bottom <= top) return

        c.enableScissor(0, top.toInt(), this.width, bottom.toInt())
        try {
            var y = top - gridScroll
            ModuleManager.filter { !it.hidden }
                .map { it.category.tag }
                .distinct()
                .forEach { cat ->
                    val mods = modulesIn(cat)
                    if (mods.isEmpty()) return@forEach

                    text(c, cat, leftPad.toInt(), y.toInt(), ACCENT)
                    y += 20f

                    mods.chunked(cols).forEach { row ->
                        val maxCardH = row.maxOf { measureCardHeight(it) }
                        row.forEachIndexed { col, m ->
                            val cx = leftPad + col * (cardW + gap)
                            drawCard(c, m, cx, y, cardW, maxCardH)
                        }
                        y += maxCardH + gap
                    }
                    y += 10f
                }
        } finally {
            c.disableScissor()
        }

        val ch = contentHeightGlobal()
        if (ch > viewportH) {
            val thumbH = max(20f, viewportH * (viewportH / ch))
            val thumbY = top + (viewportH - thumbH) * (gridScroll / (ch - viewportH))
            rect(c, this.width - 6f, thumbY, 2f, thumbH, ACCENT)
        }
    }

    private fun measureCardHeight(m: ClientModule): Float {
        if (m !in globalOpenModules) return 22f
        return 22f + m.containedValues.filter(::isVisible).sumOf { measureValue(it) } + 2f
    }

    private fun drawCard(
        c: GuiGraphicsExtractor, m: ClientModule,
        x: Float, y: Float, w: Float, h: Float
    ) {
        val isOpen = m in globalOpenModules
        val cardH = measureCardHeight(m)
        val hover = lastMx in x..(x + w) && lastMy in y..(y + h)

        rect(c, x, y, w, cardH, if (hover) ROW_HOVER else PANEL_BG)
        rect(c, x, y, w, 1f, if (m.enabled) ACCENT else BORDER)
        rect(c, x, y + cardH - 1, w, 1f, BORDER)
        rect(c, x, y, 1f, cardH, BORDER)
        rect(c, x + w - 1, y, 1f, cardH, BORDER)
        rect(c, x, y, w, 2f, if (m.enabled) ACCENT else DOT_OFF)

        val name = splitName(m.name)
        text(
            c, name, (x + 8).toInt(), (y + 7).toInt(),
            if (m.enabled) TEXT_ON else TEXT_OFF
        )

        val dotX = x + w - 16
        rect(c, dotX, y + 8, 6f, 6f, if (m.enabled) ACCENT else DOT_OFF)

        hits.add(Hit(x, y, w, 22f, Kind.MODULE_ROW, module = m))

        if (hover) hoveredModule = m

        if (isOpen) {
            var sy = y + 22f
            for (v in m.containedValues.filter(::isVisible)) {
                val sh = measureValue(v).toFloat()
                if (sh > 0f) {
                    try {
                        drawValue(c, v, x + 8, sy, w - 16)
                    } catch (_: Throwable) {
                    }
                    sy += sh
                }
            }
        }
    }

    // NEVERLOSE
    private fun drawNeverlose(c: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val winW = this.width * 0.55f
        val winH = this.height * 0.68f
        val nlX = NL_X
        val nlY = NL_Y
        val topBarH = 36f
        val catW = 100f
        val accent = ACCENT
        val textMain = 0xFFE0E0E0.toInt()
        val textDim = 0xFF7A839B.toInt()
        val radius = 8f

        if (bgEnabled && bgTexture != null) {
            drawBgInternal(c, nlX, nlY, winW, winH)
        }

        c.drawRoundedRect(nlX, nlY, nlX + winW, nlY + winH, radius, 0xAA0B0E14.toInt().toColor4b())
        c.drawRoundedRect(nlX, nlY, nlX + winW, nlY + winH, radius, 0x00000000.toColor4b(), 0x35FFFFFF.toColor4b(), 1f)

        //顶栏
        c.drawRoundedRect(nlX, nlY, nlX + winW, nlY + topBarH, radius, 0xAA0C111A.toInt().toColor4b())
        rect(c, nlX, nlY + topBarH - radius, winW, radius, 0xAA0C111A.toInt())

        text(c, "Neverlose", (nlX + 16).toInt(), (nlY + 12).toInt(), accent)

        val closeBtnX = nlX + winW - 28f
        val closeBtnY = nlY + 10f
        val closeHover = mouseX in closeBtnX.toInt()..(closeBtnX + 16).toInt() &&
            mouseY in closeBtnY.toInt()..(closeBtnY + 16).toInt()
        c.drawRoundedRect(
            closeBtnX,
            closeBtnY,
            closeBtnX + 16f,
            closeBtnY + 16f,
            4f,
            (if (closeHover) 0x55FF5555 else 0x25FFFFFF).toInt().toColor4b()
        )
        text(c, "x", (closeBtnX + 6).toInt(), (closeBtnY + 3).toInt(), if (closeHover) 0xFFFF5555.toInt() else textDim)

        //顶端搜索框
        val searchW = 130f
        val searchH = 18f
        val searchX = nlX + winW - 50f - searchW
        val searchY = nlY + (topBarH - searchH) / 2f
        val isSearchHovered =
            mouseX in searchX.toInt()..(searchX + searchW).toInt() && mouseY in searchY.toInt()..(searchY + searchH).toInt()

        c.drawRoundedRect(searchX, searchY, searchX + searchW, searchY + searchH, 4f, 0x44000000.toColor4b())
        c.drawRoundedRect(
            searchX,
            searchY,
            searchX + searchW,
            searchY + searchH,
            4f,
            0x00000000.toColor4b(),
            (if (searchFocused) accent else 0x44FFFFFF).toColor4b(),
            1f
        )

        val queryText = if (searchQuery.isEmpty() && !searchFocused) "Search..." else searchQuery
        text(
            c,
            queryText,
            (searchX + 6).toInt(),
            (searchY + 5).toInt(),
            if (searchQuery.isEmpty() && !searchFocused) textDim else textMain
        )

        if (searchFocused) {
            val cursorX = searchX + 6 + mc.font.width(searchQuery)
            val blink = (System.currentTimeMillis() / 500) % 2 == 0L
            if (blink) rect(c, cursorX, searchY + 3f, 1f, searchH - 6f, textMain)
        }

        if (closeHover) nlHitClose = true
        if (mouseY in nlY.toInt()..(nlY + topBarH).toInt() && mouseX in nlX.toInt()..(nlX + winW).toInt() && !isSearchHovered) {
            nlHitHeader = true
        }

        //左侧分类栏
        val categories = listOf("Combat", "Movement", "Player", "Render", "World", "Misc", "Exploit", "Fun")
        var cy = nlY + topBarH + 8f
        categories.forEach { name ->
            val selected = name.equals(nlCategory, ignoreCase = true)
            val rowHover = mouseX in (nlX + 10).toInt()..(nlX + catW - 10).toInt() &&
                mouseY in cy.toInt()..(cy + 22).toInt()

            if (selected) {
                c.drawRoundedRect(
                    nlX + 10f,
                    cy,
                    nlX + catW - 10f,
                    cy + 22f,
                    6f,
                    (accent and 0x00FFFFFF or 0x44000000).toColor4b()
                )
                rect(c, nlX + 10f, cy + 5f, 3f, 12f, accent)
            } else if (rowHover) {
                c.drawRoundedRect(nlX + 10f, cy, nlX + catW - 10f, cy + 22f, 6f, 0x25FFFFFF.toColor4b())
            }

            text(
                c, name, (nlX + 22).toInt(), (cy + 7).toInt(),
                if (selected) accent else if (rowHover) textMain else textDim
            )

            if (rowHover) nlHitCategory = name
            cy += 26f
        }
        val infoY = nlY + winH - 50f
        rect(c, nlX + 10f, infoY - 6f, catW - 20f, 1f, 0x33FFFFFF.toInt())

        val avatarSize = 32f
        val avatarX = nlX + 10f
        val avatarY = infoY

        val texId = try {
            mc.player?.skin?.let { skin ->
                val field = skin.javaClass.getDeclaredField("texture")
                field.isAccessible = true
                field.get(skin) as? Identifier
            }
        } catch (e: Throwable) {
            null
        }

        val steveTex = Identifier.fromNamespaceAndPath("minecraft", "textures/entity/player/wide/steve.png")
        val finalTex = texId ?: steveTex

        drawHeadCircle(c, finalTex, avatarX + avatarSize / 2f, avatarY + avatarSize / 2f, avatarSize / 2f)

        // 名字和UID
        val playerName = try {
            mc.player?.gameProfile?.name ?: "Player"
        } catch (_: Throwable) {
            "Player"
        }
        val uid = LiquidBounce.clientUid ?: "---"

        val textX = avatarX + avatarSize + 8f
        text(c, playerName, textX.toInt(), (avatarY + 2f).toInt(), textMain)
        text(c, "UID: $uid", textX.toInt(), (avatarY + 14f).toInt(), textDim)

        //右侧模块卡片区域
        val gridX = nlX + catW + 12f
        val gridY = nlY + topBarH + 12f
        val gridW = winW - catW - 24f
        val gridH = winH - topBarH - 24f
        val cardGap = 8f
        val cols = 2
        val cardW = (gridW - cardGap) / cols

        val catModules = modulesIn(nlCategory)

        if (gridW > 0 && gridH > 0) {
            c.enableScissor(gridX.toInt(), gridY.toInt(), (gridX + gridW).toInt(), (gridY + gridH).toInt())
            try {
                var y = gridY - nlScroll
                catModules.chunked(cols).forEach { row ->
                    val maxH = row.maxOfOrNull { measureCardHeight(it) } ?: 22f
                    row.forEachIndexed { col, m ->
                        val cx = gridX + col * (cardW + cardGap)
                        drawNlCard(c, m, cx, y, cardW, maxH, mouseX, mouseY, gridX, gridY, gridW, gridH)
                    }
                    y += maxH + cardGap
                }
            } finally {
                c.disableScissor()
            }
        }

        val totalH = catModules.chunked(cols).fold(0f) { acc, row ->
            acc + (row.maxOfOrNull { measureCardHeight(it) } ?: 22f) + cardGap
        } + 4f

        if (totalH > gridH && gridH > 0) {
            val maxScroll = max(0f, totalH - gridH)
            nlScroll = nlScroll.coerceIn(0f, maxScroll)
            val thumbH = max(20f, gridH * (gridH / totalH))
            val thumbY = gridY + (gridH - thumbH) * (nlScroll / maxScroll)
            c.drawRoundedRect(nlX + winW - 8f, thumbY, nlX + winW - 5f, thumbY + thumbH, 1.5f, 0x55FFFFFF.toColor4b())
        } else {
            nlScroll = 0f
        }
    }

    private fun drawNlCard(
        c: GuiGraphicsExtractor, m: ClientModule,
        x: Float, y: Float, w: Float, h: Float,
        mouseX: Int, mouseY: Int,
        gridX: Float, gridY: Float, gridW: Float, gridH: Float
    ) {
        val isOpen = m in globalOpenModules
        val cardH = measureCardHeight(m)

        val isInViewport = mouseX >= gridX && mouseX <= gridX + gridW && mouseY >= gridY && mouseY <= gridY + gridH
        val hover = isInViewport && mouseX in x.toInt()..(x + w).toInt() && mouseY in y.toInt()..(y + cardH).toInt()

        val accent = ACCENT
        val textMain = 0xFFE0E0E0.toInt()
        val textDim = 0xFF7A839B.toInt()

        val cardBg = when {
            hover -> 0xCC2A3345.toInt()
            m.enabled -> 0xAA101522.toInt()
            else -> 0x88070A10.toInt()
        }

        c.drawRoundedRect(x, y, x + w, y + cardH, 4f, cardBg.toColor4b())

        val name = splitName(m.name)
        text(c, name, (x + 10).toInt(), (y + 7).toInt(), if (m.enabled) textMain else textDim)

        val toggleW = 16f
        val toggleH = 8f
        val toggleX = x + w - toggleW - 10f
        val toggleY = y + 7f

        val trackColor = if (m.enabled) accent else 0x44000000.toInt()
        c.drawRoundedRect(toggleX, toggleY, toggleX + toggleW, toggleY + toggleH, toggleH / 2f, trackColor.toColor4b())

        val dotSize = 6f
        val dotX = if (m.enabled) toggleX + toggleW - dotSize - 1f else toggleX + 1f
        val dotY = toggleY + 1f
        c.drawRoundedRect(
            dotX,
            dotY,
            dotX + dotSize,
            dotY + dotSize,
            dotSize / 2f,
            (if (m.enabled) 0xFFFFFFFF.toInt() else 0xFF7A839B.toInt()).toColor4b()
        )

        hits.add(Hit(x, y, w, 22f, Kind.MODULE_ROW, module = m))
        if (hover) hoveredModule = m

        if (isOpen) {
            c.drawRoundedRect(x + 1f, y + 22f, x + w - 1f, y + cardH - 1f, 3f, 0x66000000.toColor4b())

            var sy = y + 26f
            for (v in m.containedValues.filter(::isVisible)) {
                val sh = measureValue(v).toFloat()
                if (sh > 0f) {
                    try {
                        drawValue(c, v, x + 10, sy, w - 20)
                    } catch (_: Throwable) {
                    }
                    sy += sh
                }
            }
        }
    }

    // 内部反射获取 TextureSetup 以避免依赖报错
    private fun invokeNoArg(obj: Any, names: List<String>): Any? {
        for (n in names) {
            val m = obj.javaClass.methods.firstOrNull { it.name == n && it.parameterCount == 0 } ?: continue
            return runCatching { m.invoke(obj) }.getOrNull()
        }
        return null
    }

    private fun getTextureSetup(idf: Identifier): TextureSetup? {
        val texObj = runCatching { mc.textureManager.getTexture(idf) }.getOrNull() ?: return null
        if (texObj.javaClass.simpleName.contains("Missing", ignoreCase = true)) return null
        val view = invokeNoArg(texObj, listOf("textureView", "getTextureView")) ?: return null
        val sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
        return TextureSetup::class.java.methods.firstOrNull {
            it.name == "singleTexture" && it.parameterCount == 2
        }?.let {
            runCatching { it.invoke(null, view, sampler) as? TextureSetup }.getOrNull()
        }
    }

    private fun drawHeadCircle(
        c: GuiGraphicsExtractor,
        texture: Identifier,
        cx: Float, cy: Float, r: Float
    ) {
        val setup = getTextureSetup(texture)
        if (setup == null) {
            try {
                c.blit(
                    RenderPipelines.GUI_TEXTURED, texture,
                    (cx - r).toInt(), (cy - r).toInt(), 8f, 8f,
                    (r * 2).toInt(), (r * 2).toInt(), 8, 8, 64, 64, -1
                )
            } catch (_: Throwable) {
            }
            return
        }
        drawHeadCircleStrips(c, setup, cx, cy, r)
    }
    private fun drawHeadCircleStrips(
        c: GuiGraphicsExtractor, setup: TextureSetup,
        cx: Float, cy: Float, r: Float
    ) {
        val strips = 128
        val u0 = 8f / 64f
        val v0 = 8f / 64f
        val span = 8f / 64f
        val uCenter = u0 + span * 0.5f
        val vCenter = v0 + span * 0.5f
        val uRadius = span * 0.5f
        val vRadius = span * 0.5f

        for (i in 0 until strips) {
            val x1 = cx - r + 2f * r * i / strips
            val x2 = cx - r + 2f * r * (i + 1) / strips

            val dx1 = (x1 - cx) / r
            val dx2 = (x2 - cx) / r
            val chord1 = sqrt((1f - dx1 * dx1).coerceAtLeast(0f)) * r
            val chord2 = sqrt((1f - dx2 * dx2).coerceAtLeast(0f)) * r
            val chord = (chord1 + chord2) * 0.5f
            if (chord <= 0.01f) continue

            val u1 = u0 + span * i / strips
            val u2 = u0 + span * (i + 1) / strips
            val vLo = vCenter - vRadius * (chord / r)
            val vHi = vCenter + vRadius * (chord / r)

            c.drawTexQuad(setup, x1, cy - chord, x2, cy + chord, u1, vLo, u2, vHi)
        }
    }


    private fun drawBgInternal(c: GuiGraphicsExtractor, nlX: Float, nlY: Float, winW: Float, winH: Float) {
        val tex = bgTexture ?: return
        val img = tex.pixels ?: return
        val iw = img.width
        val ih = img.height
        if (iw > 0 && ih > 0) {
            val panelW = winW.toInt()
            val panelH = winH.toInt()
            val ir = iw.toFloat() / ih
            val sr = panelW.toFloat() / panelH
            when (bgFit) {
                0 -> { // Fill
                    if (ir > sr) {
                        val rw = ih * sr
                        blitBg(c, nlX.toInt(), nlY.toInt(), panelW, panelH, (iw - rw) / 2f, 0f, rw.roundToInt(), ih, iw, ih)
                    } else {
                        val rh = iw / sr
                        blitBg(c, nlX.toInt(), nlY.toInt(), panelW, panelH, 0f, (ih - rh) / 2f, iw, rh.roundToInt(), iw, ih)
                    }
                }
                1 -> { // Fit
                    val dw: Int; val dh: Int
                    if (ir > sr) { dw = panelW; dh = (panelW / ir).roundToInt() } else { dh = panelH; dw = (panelH * ir).roundToInt() }
                    blitBg(c, nlX.toInt() + (panelW - dw) / 2, nlY.toInt() + (panelH - dh) / 2, dw, dh, 0f, 0f, iw, ih, iw, ih)
                }
                2 -> { // Stretch
                    blitBg(c, nlX.toInt(), nlY.toInt(), panelW, panelH, 0f, 0f, iw, ih, iw, ih)
                }
                3 -> { // Tile
                    val tw = max(iw, 8)
                    val th = max(ih, 8)
                    var ty = 0
                    while (ty < panelH) {
                        var tx = 0
                        while (tx < panelW) {
                            blitBg(c, nlX.toInt() + tx, nlY.toInt() + ty, tw, th, 0f, 0f, iw, ih, iw, ih)
                            tx += tw
                        }
                        ty += th
                    }
                }
            }
        }
    }

    private fun drawSearchBox(c: GuiGraphicsExtractor) {
        if (currentStyle == LayoutStyle.NEVERLOSE) return
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
            if (blink) rect(c, cursorX, by + 3f, 1f, bh - 6f, TEXT_ON)
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
        text(c, "ESC to exit  |  [Reset] = default",
            (px + THEME_PANEL_W - 220).toInt(), (py + 8).toInt(), 0xFF666670.toInt())

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
            text(c, hexStr, (px + padding + nameW + previewW + 6f).toInt(), cy.toInt(),
                0xFF9C9CA4.toInt())

            cy += 12f

            val startX = px + padding + nameW + previewW + hexW + 12f
            val values = intArrayOf(r, g, b, a)
            val labels = arrayOf("R", "G", "B", "A")
            val sliderColors = intArrayOf(
                0xFFFF4040.toInt(), 0xFF40FF40.toInt(),
                0xFF4040FF.toInt(), 0xFFCCCCCC.toInt()
            )
            for (j in 0..3) {
                val sx = startX + j * (sliderW + sliderGap)
                rect(c, sx, cy + 3f, sliderW, sliderH, 0xFF202020.toInt())
                rect(c, sx, cy + 3f, sliderW * (values[j] / 255f), sliderH, sliderColors[j])
                val hx = sx + sliderW * (values[j] / 255f) - 1f
                rect(c, hx, cy + 1f, 2f, sliderH + 4f, 0xFFFFFFFF.toInt())
                text(c, "${labels[j]}${values[j]}",
                    (sx + sliderW + 2f).toInt(), cy.toInt(), 0xFF9C9CA4.toInt())
                themeHits.add(ThemeHit(sx, cy, sliderW, sliderH + 4f, i, j))
            }
            cy += 18f
        }

        val resetW = 60f
        val resetH = 14f
        val resetX = px + (THEME_PANEL_W - resetW) / 2f
        val resetY = py + panelH - resetH - 8f
        val hoveringReset = lastMx in resetX..(resetX + resetW) && lastMy in resetY..(resetY + resetH)
        rect(c, resetX, resetY, resetW, resetH,
            if (hoveringReset) 0xFF2A2A33.toInt() else 0xFF1A1A20.toInt())
        rect(c, resetX, resetY, resetW, 1f, 0xFF2C2C33.toInt())
        rect(c, resetX, resetY + resetH - 1, resetW, 1f, 0xFF2C2C33.toInt())
        text(c, "Reset", (resetX + 18f).toInt(), (resetY + 3f).toInt(), 0xFFFFFFFF.toInt())
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
                text(c, "$name: ${fmt(cur, isInt)}${safeStr(rv.suffix)}",
                    x.toInt(), y.toInt(), TEXT_DIM)
                sliderTrack(c, rv, x, y + 12, w, cur)
                hits.add(Hit(x, y + 10, w, 14f, Kind.SLIDER, value = v))
            }
            v.valueType == ValueType.INT_RANGE || v.valueType == ValueType.FLOAT_RANGE -> {
                val rv = v as RangedValue<*>
                val r = v.get() as? ClosedRange<*> ?: 0..0
                val s = (r.start as? Number)?.toFloat() ?: 0f
                val e = (r.endInclusive as? Number)?.toFloat() ?: 0f
                val isInt = v.valueType == ValueType.INT_RANGE
                text(c, "$name: ${fmt(s, isInt)} ~ ${fmt(e, isInt)}${safeStr(rv.suffix)}",
                    x.toInt(), y.toInt(), TEXT_DIM)
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
                val s = if (listeningKey === v) "> press key <"
                else "$name: ${safeStr(key?.displayName?.string)}"
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
                    text(c, choiceTag, (x + 12).toInt(), cy.toInt(),
                        if (isSel) TEXT_ON else TEXT_OFF)
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
                val s = safeStr(v.getValue()).let {
                    if (it.length > 42) it.take(39) + "..." else it
                }
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

    private fun checkRow(
        c: GuiGraphicsExtractor, name: String, on: Boolean,
        x: Float, y: Float, w: Float
    ) {
        if (lastMx in x..(x + w) && lastMy in y..(y + 14)) rect(c, x, y, w, 14f, ROW_HOVER)
        rect(c, x + 2, y + 4, 6f, 6f, if (on) ACCENT else DOT_OFF)
        text(c, name, (x + 12).toInt(), (y + 3).toInt(), if (on) TEXT_ON else TEXT_OFF)
    }

    private fun sliderTrack(
        c: GuiGraphicsExtractor, rv: RangedValue<*>,
        x: Float, y: Float, w: Float, value: Float
    ) {
        val lo = (rv.range.start as? Number)?.toDouble() ?: 0.0
        val hi = (rv.range.endInclusive as? Number)?.toDouble() ?: 0.0
        val span = hi - lo
        val t = if (span == 0.0) 0f else ((value.toDouble() - lo) / span)
            .coerceIn(0.0, 1.0).toFloat()
        rect(c, x, y, w, 2f, DOT_OFF)
        rect(c, x, y, w * t, 2f, ACCENT)
        rect(c, x + w * t - 1, y - 2, 3f, 6f, TEXT_ON)
    }

    private fun fmt(v: Float, isInt: Boolean) =
        if (isInt) v.roundToInt().toString() else "%.2f".format(v)

    private fun wrapText(s: String, maxW: Int): List<String> {
        val out = ArrayList<String>()
        var cur = ""
        for (word in s.split(" ")) {
            if (word.isEmpty()) continue
            val candidate = if (cur.isEmpty()) word else "$cur $word"
            if (mc.font.width(candidate) > maxW && cur.isNotEmpty()) {
                out.add(cur); cur = word
            } else cur = candidate
        }
        if (cur.isNotEmpty()) out.add(cur)
        return out
    }

    private fun drawModuleTooltip(c: GuiGraphicsExtractor, m: ClientModule) {
        val rawDesc = try { m.description.get() } catch (_: Throwable) { null }
            ?.takeIf { it.isNotBlank() } ?: return

        val maxW = 180
        val titleLines = wrapText(splitName(m.name), maxW)
        val descLines = wrapText(rawDesc, maxW)
        val all = titleLines + descLines

        val textW = all.maxOf { mc.font.width(it) }
        val w = textW + 12
        val lineH = 10
        val h = all.size * lineH + 10

        var tx = lastMx + 14f
        var ty = lastMy + 8f
        if (tx + w > this.width) tx = lastMx - w - 6f
        if (ty + h > this.height) ty = lastMy - h - 6f
        tx = tx.coerceAtLeast(2f)
        ty = ty.coerceAtLeast(2f)

        rect(c, tx, ty, w.toFloat(), h.toFloat(), 0xF018181E.toInt())
        rect(c, tx, ty, w.toFloat(), 1f, ACCENT)
        rect(c, tx, ty, 1f, h.toFloat(), BORDER)
        rect(c, tx + w - 1, ty, 1f, h.toFloat(), BORDER)
        rect(c, tx, ty + h - 1, w.toFloat(), 1f, BORDER)

        all.forEachIndexed { i, line ->
            text(c, line, (tx + 6).toInt(), (ty + 5 + i * lineH).toInt(),
                if (i >= titleLines.size) TEXT_OFF else TEXT_ON)
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = event.x().toFloat()
        val my = event.y().toFloat()
        val button = event.button()

        if (bgMode) { handleBgClick(mx, my, button); return true }
        if (themeMode) { handleThemeClick(mx, my, button); return true }

        run {
            val (tbX, tbY, tbW, tbH) = themeBtnRect()
            if (button == 0 && mx in tbX..(tbX + tbW) && my in tbY..(tbY + tbH)) {
                themeMode = true
                return true
            }
            val (bbX, bbY, bbW, bbH) = bgBtnRect()
            if (mx in bbX..(bbX + bbW) && my in bbY..(bbY + bbH)) {
                if (button == 0) bgMode = true else if (button == 1) toggleBgEnabled()
                return true
            }
            val (stX, stY, stW, stH) = styleBtnRect()
            if (button == 0 && mx in stX..(stX + stW) && my in stY..(stY + stH)) {
                val styles = LayoutStyle.values()
                currentStyle = styles[(currentStyle.ordinal + 1) % styles.size]
                saveGuiConfig()
                return true
            }
        }

        if (currentStyle == LayoutStyle.NEVERLOSE) {
            val winW = this.width * 0.55f
            val topBarH = 36f
            val searchW = 130f
            val searchH = 18f
            val searchX = NL_X + winW - 50f - searchW
            val searchY = NL_Y + (topBarH - searchH) / 2f

            if (nlHitClose && button == 0) { mc.setScreen(null); return true }
            if (mx in searchX..(searchX + searchW) && my in searchY..(searchY + searchH) && button == 0) {
                searchFocused = true
                return true
            }
            if (nlHitCategory.isNotEmpty() && button == 0) {
                nlCategory = nlHitCategory
                nlScroll = 0f
                saveGuiConfig()
                return true
            }
            if (nlHitHeader && button == 0) {
                nlDragging = true
                nlDragOffX = mx - NL_X
                nlDragOffY = my - NL_Y
                return true
            }
            if (searchFocused && button == 0) {
                searchFocused = false
            }
        }

        val sbX = searchBoxX()
        val sbY = SEARCH_Y
        if (mx in sbX..(sbX + SEARCH_W) && my in sbY..(sbY + SEARCH_H)) {
            if (button == 0) {
                val clearX = sbX + SEARCH_W - 12f
                if (searchQuery.isNotEmpty() && mx >= clearX) searchQuery = ""
                searchFocused = true
                return true
            }
        }
        if (searchFocused) searchFocused = false

        listeningBind?.let { v ->
            v.set(InputBind(InputConstants.Type.MOUSE.getOrCreate(button),
                v.get().action, v.get().modifiers))
            listeningBind = null
            return true
        }
        if (listeningKey != null) { listeningKey = null; return true }
        if (editingText != null && button == 0) commitText()

        if (currentStyle == LayoutStyle.HCS) {
            val insidePanel = panels.any { p ->
                val ph = panelHeight(p)
                mx >= p.x && mx <= p.x + PANEL_W && my >= p.y && my <= p.y + ph
            }
            if (!insidePanel && hits.isEmpty()) {
                return super.mouseClicked(event, doubleClick)
            }

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
        }

        val hit = hits.lastOrNull { mx in it.x..(it.x + it.w) && my in it.y..(it.y + it.h) }
        if (hit != null) {
            handleValueClick(hit, mx, my, button)
            return true
        }
        return super.mouseClicked(event, doubleClick)
    }

    private fun handleValueClick(hit: Hit, mx: Float, my: Float, button: Int) {
        when (hit.kind) {
            Kind.MODULE_ROW -> {
                val m = hit.module ?: return
                if (button == 0) m.enabled = !m.enabled
                else if (button == 1) toggleOpen(m)
            }
            Kind.BOOLEAN -> {
                val bv = hit.value as? Value<Boolean> ?: return
                bv.set(!bv.get())
            }
            Kind.SLIDER -> { activeSlider = hit; updateSlider(hit, mx) }
            Kind.RANGE -> { activeSlider = hit; updateRange(hit, mx, my) }
            Kind.TEXT -> { hit.value?.let { beginEditText(it) } }
            Kind.LISTEN_BIND -> { listeningBind = hit.value as? Value<InputBind> }
            Kind.LISTEN_KEY -> { listeningKey = hit.value as? Value<InputConstants.Key> }
            Kind.CHOOSE -> { cycleChoice(hit.value) }
            Kind.MULTI_ITEM -> { toggleMultiItem(hit.value, hit.index) }
        }
    }

    private fun toggleOpen(m: ClientModule) {
        when (currentStyle) {
            LayoutStyle.HCS -> {
                val p = panels.firstOrNull { it.categoryName == m.category.tag } ?: return
                if (m in p.openModules) p.openModules.remove(m) else p.openModules.add(m)
                clampScroll(p)
            }
            LayoutStyle.FDP, LayoutStyle.YZY, LayoutStyle.NEVERLOSE -> {
                if (m in globalOpenModules) globalOpenModules.remove(m)
                else globalOpenModules.add(m)
                clampGlobalScroll()
            }
        }
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (bgMode) { draggingBgDim = false; return true }
        if (themeMode) { draggingThemeSlider = null; return true }
        nlDragging = false
        dragPanel = null
        activeSlider = null
        return super.mouseReleased(event)
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        val mx = event.x().toFloat()
        val my = event.y().toFloat()
        if (bgMode) {
            if (draggingBgDim) {
                val track = bgDimTrackRect()
                updateBgDim(BgHit(track[0], track[1], track[2], track[3], 1), mx)
            }
            return true
        }
        if (themeMode) {
            draggingThemeSlider?.let { updateThemeSlider(it, mx) }
            return true
        }
        if (nlDragging && currentStyle == LayoutStyle.NEVERLOSE) {
            NL_X = mx - nlDragOffX
            NL_Y = my - nlDragOffY
            val winW = this.width * 0.55f
            val winH = this.height * 0.68f
            NL_X = NL_X.coerceIn(0f, this.width - winW)
            NL_Y = NL_Y.coerceIn(0f, this.height - winH)
            return true
        }
        dragPanel?.let {
            val ph = panelHeight(it)
            it.x = (mx - dragOffX).coerceIn(0f, this.width - PANEL_W)
            it.y = (my - dragOffY).coerceIn(-(ph - HEADER_H), this.height - HEADER_H)
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

    override fun mouseScrolled(
        mouseX: Double, mouseY: Double,
        scrollX: Double, scrollY: Double
    ): Boolean {
        if (bgMode || themeMode) return true
        val mx = mouseX.toFloat()
        val my = mouseY.toFloat()

        when (currentStyle) {
            LayoutStyle.HCS -> {
                for (p in panels) {
                    if (p.minimized) continue
                    if (mx in p.x..(p.x + PANEL_W) && my in p.y..(p.y + panelHeight(p))) {
                        p.scroll -= scrollY.toFloat() * 14f
                        clampScroll(p)
                        return true
                    }
                }
            }
            LayoutStyle.FDP -> {
                if (mx in 0f..this.width.toFloat() && my in SEARCH_TOP_PAD..(this.height - 40f)) {
                    compactScroll -= scrollY.toFloat() * 14f
                    clampGlobalScroll()
                    return true
                }
            }
            LayoutStyle.YZY -> {
                if (mx in 0f..this.width.toFloat() && my in SEARCH_TOP_PAD..(this.height - 40f)) {
                    gridScroll -= scrollY.toFloat() * 14f
                    clampGlobalScroll()
                    return true
                }
            }
            LayoutStyle.NEVERLOSE -> {
                val winW = this.width * 0.55f
                val winH = this.height * 0.68f
                if (mx in NL_X..(NL_X + winW) && my in NL_Y..(NL_Y + winH)) {
                    nlScroll -= scrollY.toFloat() * 14f
                    if (nlScroll < 0f) nlScroll = 0f
                    return true
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val keyCode = event.key

        if (bgMode) {
            if (keyCode == InputConstants.KEY_ESCAPE) { bgMode = false; saveBgConfig() }
            return true
        }
        if (themeMode) {
            if (keyCode == InputConstants.KEY_ESCAPE) {
                themeMode = false; saveThemeToDisk()
            }
            return true
        }

        if (searchFocused) {
            when {
                keyCode == InputConstants.KEY_ESCAPE -> {
                    if (searchQuery.isNotEmpty()) searchQuery = "" else searchFocused = false
                    return true
                }
                event.isConfirmation -> { searchFocused = false; return true }
                keyCode == InputConstants.KEY_BACKSPACE -> {
                    if (searchQuery.isNotEmpty()) searchQuery = searchQuery.dropLast(1)
                    return true
                }
                keyCode == InputConstants.KEY_DELETE -> { searchQuery = ""; return true }
                else -> return true // 拦截按键
            }
        }

        listeningBind?.let { v ->
            when {
                keyCode == InputConstants.KEY_ESCAPE -> {}
                keyCode == InputConstants.KEY_BACKSPACE -> v.set(InputBind.UNBOUND)
                else -> {
                    val k = InputConstants.Type.KEYSYM.getOrCreate(keyCode)
                    v.set(InputBind(k, v.get().action, v.get().modifiers))
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
                    if (editBuffer.isNotEmpty()) editBuffer.deleteCharAt(editBuffer.length - 1)
                    return true
                }
                keyCode == InputConstants.KEY_DELETE -> { editBuffer.clear(); return true }
                else -> return true // 拦截按键
            }
        }
        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (bgMode || themeMode) return true
        if (searchFocused) {
            val ch = event.codepoint.toChar()
            if (!ch.isISOControl() && searchQuery.length < 64) searchQuery += ch
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
        try { (target as? Value<String>)?.set(editBuffer.toString()) } catch (_: Throwable) {}
        editingText = null
        editBuffer.clear()
    }

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

    @Suppress("UNCHECKED_CAST")
    private fun updateSlider(hit: Hit, mx: Float) {
        val v: Value<*> = hit.value ?: return
        val t = ((mx - hit.x) / hit.w).coerceIn(0f, 1f)
        when (v.valueType) {
            ValueType.INT -> {
                val rv = v as RangedValue<*>
                val lo = (rv.range.start as? Number)?.toFloat() ?: return
                val hi = (rv.range.endInclusive as? Number)?.toFloat() ?: return
                try { (v as Value<Int>).set((lo + (hi - lo) * t).roundToInt()) }
                catch (_: Throwable) {}
            }
            ValueType.FLOAT -> {
                val rv = v as RangedValue<*>
                val lo = (rv.range.start as? Number)?.toFloat() ?: return
                val hi = (rv.range.endInclusive as? Number)?.toFloat() ?: return
                try { (v as Value<Float>).set(lo + (hi - lo) * t) }
                catch (_: Throwable) {}
            }
            ValueType.COLOR -> {
                val cv = v as? Value<Color4b> ?: return
                val col = cv.get()
                val nv = (t * 255f).roundToInt()
                cv.set(when (hit.index) {
                    1 -> Color4b(nv, col.g, col.b, col.a)
                    2 -> Color4b(col.r, nv, col.b, col.a)
                    3 -> Color4b(col.r, col.g, nv, col.a)
                    else -> Color4b(col.r, col.g, col.b, nv)
                })
            }
            else -> {}
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun updateRange(hit: Hit, mx: Float, my: Float) {
        val v: Value<*> = hit.value ?: return
        val rv = v as RangedValue<*>
        val lo = (rv.range.start as? Number)?.toDouble() ?: return
        val hi = (rv.range.endInclusive as? Number)?.toDouble() ?: return
        val t = ((mx - hit.x) / hit.w).coerceIn(0f, 1f)
        val target = lo + (hi - lo) * t
        val old = v.get() as? ClosedRange<*> ?: return
        val oldS = (old.start as? Number)?.toDouble() ?: return
        val oldE = (old.endInclusive as? Number)?.toDouble() ?: return
        val dragFrom = my < (hit.y + hit.h / 2f)
        val isInt = v.valueType == ValueType.INT_RANGE
        if (isInt) {
            val ti = target.roundToInt()
            val s: Int; val e: Int
            if (dragFrom) { s = min(ti, oldE.toInt()); e = oldE.toInt() }
            else { s = oldS.toInt(); e = max(ti, oldS.toInt()) }
            try { (v as Value<Any>).set(s..e) } catch (_: Throwable) {}
        } else {
            val tf = target.toFloat()
            val s: Float; val e: Float
            if (dragFrom) { s = min(tf, oldE.toFloat()); e = oldE.toFloat() }
            else { s = oldS.toFloat(); e = max(tf, oldS.toFloat()) }
            try { (v as Value<Any>).set(s..e) } catch (_: Throwable) {}
        }
    }

    private fun cycleChoice(v: Value<*>?) {
        try {
            when (v) {
                is ModeValueGroup<*> -> {
                    val modes = v.modes
                    if (modes.isNotEmpty()) v.setByString(
                        modes[(modes.indexOf(v.activeMode) + 1) % modes.size].name
                    )
                }
                is ChoiceListValue<*> -> {
                    val list = v.choices.toList()
                    if (list.isNotEmpty()) (v as Value<Any>).set(
                        list[(list.indexOf(v.get()) + 1) % list.size]
                    )
                }
            }
        } catch (_: Throwable) {}
    }

    @Suppress("UNCHECKED_CAST")
    private fun toggleMultiItem(v: Value<*>?, index: Int) {
        val mv = v as? MultiChoiceListValue<*> ?: return
        val choice = mv.choices.toList().getOrNull(index) ?: return
        try {
            val set = LinkedHashSet(mv.get() as Collection<Any>)
            if (choice in set) set.remove(choice) else set.add(choice)
            (mv as Value<Any>).set(set)
        } catch (_: Throwable) {}
    }

    private fun bgDir(): File {
        val dir = File(mc.gameDirectory, "config/liquidbounce")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun bgImageFile() = File(bgDir(), "clickgui-background.png")
    private fun bgConfigFile() = File(bgDir(), "nativeclickgui-bg.txt")

    private fun loadBgConfig() {
        try {
            val f = bgConfigFile()
            if (!f.exists()) return
            for (line in f.readLines()) {
                val t = line.trim()
                if (t.isEmpty() || t.startsWith("#")) continue
                val parts = t.split("=", limit = 2)
                if (parts.size != 2) continue
                when (parts[0].trim().lowercase()) {
                    "enabled" -> bgEnabled = parts[1].trim().equals("true", ignoreCase = true)
                    "dim" -> bgDim = parts[1].trim().toIntOrNull()?.coerceIn(0, 255) ?: bgDim
                    "fit" -> bgFit = (parts[1].trim().toIntOrNull() ?: 0).coerceIn(0, 3)
                }
            }
        } catch (_: Throwable) {}
    }

    private fun saveBgConfig() {
        try {
            bgConfigFile().writeText(
                "# NativeClickGui Background\n" +
                    "# enabled: wallpaper on/off | dim: 0-255 darkness | fit: 0=Fill 1=Fit 2=Stretch 3=Tile\n" +
                    "enabled=$bgEnabled\ndim=$bgDim\nfit=$bgFit\n"
            )
        } catch (_: Throwable) {}
    }

    private fun unloadBackground() {
        try { bgTexture?.close() } catch (_: Throwable) {}
        bgTexture = null
    }

    private fun reloadBackground() {
        unloadBackground()
        bgLoadFailed = false
        val f = bgImageFile()
        if (!f.exists()) return
        try {
            bgTexture = f.readNativeImage().registerTexture(BG_TEXTURE_ID)
        } catch (_: Throwable) {
            bgLoadFailed = true
            setBgStatus("Failed to load wallpaper")
        }
    }

    private fun setBgStatus(msg: String) {
        bgStatusMsg = msg
        bgStatusUntil = System.currentTimeMillis() + 4000
    }

    private fun blitBg(
        c: GuiGraphicsExtractor,
        x: Int, y: Int, w: Int, h: Int,
        u: Float, v: Float, rw: Int, rh: Int, tw: Int, th: Int
    ) {
        c.blit(RenderPipelines.GUI_TEXTURED, BG_TEXTURE_ID,
            x, y, u, v, w, h, rw, rh, tw, th)
    }

    private fun drawBackgroundImage(c: GuiGraphicsExtractor, tex: DynamicTexture) {
        val img = tex.pixels ?: return
        val iw = img.width; val ih = img.height
        val sw = this.width; val sh = this.height
        if (iw <= 0 || ih <= 0 || sw <= 0 || sh <= 0) return
        val ir = iw.toFloat() / ih
        val sr = sw.toFloat() / sh
        when (bgFit) {
            1 -> {
                val dw: Int; val dh: Int
                if (ir > sr) { dw = sw; dh = (sw / ir).roundToInt() }
                else { dh = sh; dw = (sh * ir).roundToInt() }
                blitBg(c, (sw - dw) / 2, (sh - dh) / 2, dw, dh, 0f, 0f, iw, ih, iw, ih)
            }
            2 -> blitBg(c, 0, 0, sw, sh, 0f, 0f, iw, ih, iw, ih)
            3 -> {
                val tw = max(iw, 8); val th = max(ih, 8)
                var ty = 0
                while (ty < sh) {
                    var tx = 0
                    while (tx < sw) {
                        blitBg(c, tx, ty, tw, th, 0f, 0f, iw, ih, iw, ih)
                        tx += tw
                    }
                    ty += th
                }
            }
            else -> {
                if (ir > sr) {
                    val rw = ih * sr
                    blitBg(c, 0, 0, sw, sh, (iw - rw) / 2f, 0f, rw.roundToInt(), ih, iw, ih)
                } else {
                    val rh = iw / sr
                    blitBg(c, 0, 0, sw, sh, 0f, (ih - rh) / 2f, iw, rh.roundToInt(), iw, ih)
                }
            }
        }
    }

    private fun bgPanelRect(): FloatArray {
        val pw = 300f; val ph = 142f
        return floatArrayOf((this.width - pw) / 2f, (this.height - ph) / 2f, pw, ph)
    }

    private fun bgDimTrackRect(): FloatArray {
        val (px, py) = bgPanelRect()
        return floatArrayOf(px + 70f, py + 66f, 150f, 10f)
    }

    private fun drawBgEditor(c: GuiGraphicsExtractor) {
        val (px, py, pw, ph) = bgPanelRect()
        rect(c, px, py, pw, ph, 0xF016161A.toInt())
        rect(c, px, py, pw, 1f, 0xFF2C2C33.toInt())
        rect(c, px, py + ph - 1, pw, 1f, 0xFF2C2C33.toInt())
        rect(c, px, py, 1f, ph, 0xFF2C2C33.toInt())
        rect(c, px + pw - 1, py, 1f, ph, 0xFF2C2C33.toInt())

        text(c, "Background", (px + 10).toInt(), (py + 8).toInt(), 0xFF4677FF.toInt())
        text(c, "ESC to exit", (px + pw - 70).toInt(), (py + 8).toInt(), 0xFF666670.toInt())
        bgHits.clear()

        val prevW = 84f; val prevH = 48f
        val prevX = px + pw - prevW - 12f
        val prevY = py + 24f
        rect(c, prevX - 1, prevY - 1, prevW + 2, prevH + 2, 0xFF2C2C33.toInt())
        val tex = bgTexture
        val pixels = tex?.pixels
        if (pixels != null) {
            val iw = pixels.width; val ih = pixels.height
            val pr = prevW / prevH; val ir = iw.toFloat() / ih
            if (ir > pr) {
                val rw = ih * pr
                blitBg(c, prevX.toInt(), prevY.toInt(), prevW.toInt(), prevH.toInt(),
                    (iw - rw) / 2f, 0f, rw.roundToInt(), ih, iw, ih)
            } else {
                val rh = iw / pr
                blitBg(c, prevX.toInt(), prevY.toInt(), prevW.toInt(), prevH.toInt(),
                    0f, (ih - rh) / 2f, iw, rh.roundToInt(), iw, ih)
            }
        } else {
            rect(c, prevX, prevY, prevW, prevH, 0xFF101013.toInt())
            text(c, "no image", (prevX + 22).toInt(), (prevY + 20).toInt(), 0xFF666670.toInt())
        }

        val ex = px + 12f
        var ey = py + 28f

        if (lastMx in ex..(px + pw - prevW - 16f) && lastMy in ey..(ey + 14)) {
            rect(c, ex, ey, pw - prevW - 28f, 14f, ROW_HOVER)
        }
        rect(c, ex + 2, ey + 4, 6f, 6f, if (bgEnabled) ACCENT else DOT_OFF)
        text(c, "Enabled", (ex + 12).toInt(), (ey + 3).toInt(),
            if (bgEnabled) TEXT_ON else TEXT_OFF)
        bgHits.add(BgHit(ex, ey, pw - prevW - 28f, 14f, 0))
        ey += 24f

        text(c, "Dim: $bgDim", ex.toInt(), ey.toInt(), TEXT_DIM)
        val (tx, ty, tw, _) = bgDimTrackRect()
        rect(c, tx, ty + 2, tw, 4f, DOT_OFF)
        rect(c, tx, ty + 2, tw * (bgDim / 255f), 4f, ACCENT)
        rect(c, tx + tw * (bgDim / 255f) - 1, ty, 2f, 8f, TEXT_ON)
        bgHits.add(BgHit(tx, ty - 2, tw, 12f, 1))
        ey += 26f

        if (lastMx in ex..(px + pw - 12) && lastMy in ey..(ey + 14)) {
            rect(c, ex, ey, pw - 24f, 14f, ROW_HOVER)
        }
        text(c, "Fit mode: ${BG_FIT_NAMES[bgFit]}", (ex + 2).toInt(), (ey + 3).toInt(), TEXT_OFF)
        bgHits.add(BgHit(ex, ey, pw - 24f, 14f, 2))
        ey += 22f

        val labels = arrayOf("Refresh", "Folder", "Clear")
        val bw = 76f; val gap = 10f
        var bx = px + (pw - (bw * 3 + gap * 2)) / 2f
        labels.indices.forEach { i ->
            val hovering = lastMx in bx..(bx + bw) && lastMy in ey..(ey + 14)
            rect(c, bx, ey, bw, 14f,
                if (hovering) 0xFF2A2A33.toInt() else 0xFF1A1A20.toInt())
            rect(c, bx, ey, bw, 1f, 0xFF2C2C33.toInt())
            rect(c, bx, ey + 13, bw, 1f, 0xFF2C2C33.toInt())
            text(c, labels[i],
                (bx + (bw - mc.font.width(labels[i])) / 2f).toInt(),
                (ey + 3).toInt(), TEXT_ON)
            bgHits.add(BgHit(bx, ey, bw, 14f, 3 + i))
            bx += bw + gap
        }

        text(c, "Drag & drop an image into the GUI",
            (px + 12).toInt(), (py + ph - 12f).toInt(), TEXT_DIM)
    }

    private fun handleBgClick(mx: Float, my: Float, button: Int) {
        val (px, py, pw, ph) = bgPanelRect()
        if (mx !in px..(px + pw) || my !in py..(py + ph)) {
            bgMode = false; saveBgConfig(); return
        }
        val hit = bgHits.lastOrNull { mx in it.x..(it.x + it.w) && my in it.y..(it.y + it.h) }
        if (hit != null && button == 0) {
            when (hit.action) {
                0 -> toggleBgEnabled()
                1 -> { draggingBgDim = true; updateBgDim(hit, mx) }
                2 -> { bgFit = (bgFit + 1) % BG_FIT_NAMES.size; saveBgConfig() }
                3 -> {
                    bgLoadTried = false
                    pendingBgReload = true
                    setBgStatus("Reloading wallpaper...")
                }
                4 -> openBgFolder()
                5 -> clearBg()
            }
        }
    }

    private fun handleThemeClick(mx: Float, my: Float, button: Int) {
        val panelH = 30f + ClickGuiTheme.COUNT * 30f + 40f
        val px = (this.width - THEME_PANEL_W) / 2f
        val py = (this.height - panelH) / 2f
        val resetW = 60f; val resetH = 14f
        val resetX = px + (THEME_PANEL_W - resetW) / 2f
        val resetY = py + panelH - resetH - 8f
        if (button == 0 && mx in resetX..(resetX + resetW) && my in resetY..(resetY + resetH)) {
            resetTheme(); return
        }
        val hit = themeHits.lastOrNull { mx in it.x..(it.x + it.w) && my in it.y..(it.y + it.h) }
        if (hit != null) {
            draggingThemeSlider = hit
            updateThemeSlider(hit, mx)
        }
    }

    private fun updateBgDim(hit: BgHit, mx: Float) {
        bgDim = (((mx - hit.x) / hit.w).coerceIn(0f, 1f) * 255f).roundToInt()
    }

    private fun toggleBgEnabled() {
        if (bgTexture != null || bgImageFile().exists()) {
            bgEnabled = !bgEnabled; saveBgConfig()
        } else setBgStatus("No wallpaper image. Drag one into the GUI!")
    }

    private fun openBgFolder() {
        try {
            Desktop.getDesktop().open(bgDir())
            setBgStatus("Opened config folder")
        } catch (_: Throwable) { setBgStatus("Could not open folder") }
    }

    private fun clearBg() {
        unloadBackground()
        try { bgImageFile().delete() } catch (_: Throwable) {}
        bgEnabled = false
        saveBgConfig()
        setBgStatus("Wallpaper cleared")
    }

    override fun onFilesDrop(files: MutableList<Path>) {
        val img = files.firstOrNull {
            it.fileName.toString().substringAfterLast('.', "").lowercase() in setOf("png", "jpg", "jpeg")
        }
        if (img == null) {
            setBgStatus("Not an image (use png/jpg)")
            return
        }
        try {
            val native = img.toFile().readNativeImage()
            native.writeToFile(bgImageFile())
            native.close()
            bgEnabled = true
            bgLoadTried = false
            pendingBgReload = true
            saveBgConfig()
            setBgStatus("Wallpaper set: ${img.fileName}")
        } catch (_: Throwable) {
            setBgStatus("Failed to load image")
        }
    }
}
