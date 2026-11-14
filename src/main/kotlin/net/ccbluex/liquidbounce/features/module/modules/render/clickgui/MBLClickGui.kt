/*
 * MBL ClickGUI (未完成的版本)
 */
package net.ccbluex.liquidbounce.features.module.modules.render.clickgui

import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.ccbluex.liquidbounce.features.module.modules.render.MblClickGuiModule
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import java.util.HashSet
import kotlin.math.ceil

class MBLClickGui(private val parentModule: MblClickGuiModule) : Screen(Component.literal("MBL ClickGUI")) {

    private var currentCategory = "Combat"
    private var nlScroll = 0f
    private val openModules = HashSet<ClientModule>()
    private var isDarkMode = true

    private var dragging = false
    private var dragX = 0.0
    private var dragY = 0.0
    private var panelX = 0f
    private var panelY = 0f
    private val panelW = 600f
    private val panelH = 420f

    private val categories = listOf(
        "Combat", "Movement", "Player", "Render", "World", "Misc", "Exploit", "Fun"
    )

    override fun init() {
        super.init()
        if (panelX == 0f && panelY == 0f) {
            panelX = (this.width - panelW) / 2f
            panelY = (this.height - panelH) / 2f
        }
    }

    override fun extractRenderState(
        context: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float
    ) {
        val t = LiquidPalette.of(if (isDarkMode) LiquidTheme.DARK else LiquidTheme.LIGHT)

        context.fill(0, 0, this.width, this.height, 0x60000000)

        val r = 8f

        drawRoundedRect(context, panelX, panelY, panelW, panelH, r, t.glassBg)

        context.fill((panelX + r).toInt(), panelY.toInt(), (panelX + panelW - r).toInt(), (panelY + 1).toInt(), t.glassBorder)
        context.fill((panelX + r).toInt(), (panelY + panelH - 1).toInt(), (panelX + panelW - r).toInt(), (panelY + panelH).toInt(), 0x30000000)

        context.fill(panelX.toInt(), panelY.toInt(), (panelX + panelW).toInt(), (panelY + 1).toInt(), t.glassBorder)
        context.fill(panelX.toInt(), (panelY + panelH - 1).toInt(), (panelX + panelW).toInt(), (panelY + panelH).toInt(), t.glassBorder)
        context.fill(panelX.toInt(), panelY.toInt(), (panelX + 1).toInt(), (panelY + panelH).toInt(), t.glassBorder)
        context.fill((panelX + panelW - 1).toInt(), panelY.toInt(), (panelX + panelW).toInt(), (panelY + panelH).toInt(), t.glassBorder)

        context.fill((panelX + 1).toInt(), (panelY + 1).toInt(), (panelX + panelW - 1).toInt(), (panelY + 36).toInt(), t.glassHeaderBg)
        context.fill((panelX + 1).toInt(), (panelY + 35).toInt(), (panelX + panelW - 1).toInt(), (panelY + 36).toInt(), t.glassBorder)

        context.text(mc.font, "Neverlose", (panelX + 16).toInt(), (panelY + 12).toInt(), t.accent, false)

        val hoverTheme = isHovered(mouseX.toDouble(), mouseY.toDouble(), panelX + 120, panelY + 8, 70f, 20f)
        drawRoundedRect(context, panelX + 120, panelY + 8, 70f, 20f, 4f, if (hoverTheme) t.rowHover else 0x15FFFFFF)
        context.text(mc.font, "Theme: ${if (isDarkMode) "Dark" else "Light"}", (panelX + 126).toInt(), (panelY + 13).toInt(), if (hoverTheme) t.accent else t.textSecondary, false)

        val hoverClose = isHovered(mouseX.toDouble(), mouseY.toDouble(), panelX + panelW - 26, panelY + 10, 16f, 16f)
        drawRoundedRect(context, panelX + panelW - 26, panelY + 10, 16f, 16f, 4f, if (hoverClose) 0x55FF5555 else t.rowHover)
        context.text(mc.font, "x", (panelX + panelW - 20).toInt(), (panelY + 13).toInt(), if (hoverClose) 0xFFFF5555.toInt() else t.textSecondary, false)

        var catY = panelY + 50
        for (cat in categories) {
            val selected = cat == currentCategory
            val hovered = isHovered(mouseX.toDouble(), mouseY.toDouble(), panelX + 10, catY, 100f, 22f)

            if (selected) {
                drawRoundedRect(context, panelX + 10, catY, 100f, 22f, 6f, (t.accent and 0x00FFFFFF) or 0x30000000)
                drawRoundedRect(context, panelX + 10, catY + 4, 2f, 14f, 1f, t.accent)
            } else if (hovered) {
                drawRoundedRect(context, panelX + 10, catY, 100f, 22f, 6f, t.rowHover)
            }

            val textColor = if (selected) t.accent else if (hovered) t.textPrimary else t.textSecondary
            context.text(mc.font, cat, (panelX + 22).toInt(), (catY + 7).toInt(), textColor, false)
            catY += 26f
        }

        val gridX = panelX + 120
        val gridY = panelY + 50
        val gridW = panelW - 135
        val gridH = panelH - 65
        val cardW = (gridW - 10) / 2
        val cardH = 22f

        val modules = ModuleManager.filter { !it.hidden && it.category.tag == currentCategory }

        context.enableScissor(gridX.toInt(), gridY.toInt(), (gridX + gridW).toInt(), (gridY + gridH).toInt())

        var currentY = gridY - nlScroll
        var col = 0

        for (module in modules) {
            val x = gridX + (col * (cardW + 10))
            val y = currentY

            if (y + cardH > gridY && y < gridY + gridH) {
                val bgColor = when {
                    module.enabled -> t.cardEnabledBg
                    isHovered(mouseX.toDouble(), mouseY.toDouble(), x, y, cardW, cardH) -> t.cardHoverBg
                    else -> t.cardIdleBg
                }
                drawRoundedRect(context, x, y, cardW, cardH, 6f, bgColor)

                val borderColor = if (module.enabled) t.accent else t.cardBorder
                context.fill(x.toInt(), y.toInt(), (x + cardW).toInt(), (y + 1).toInt(), borderColor)
                context.fill(x.toInt(), (y + cardH - 1).toInt(), (x + cardW).toInt(), (y + cardH).toInt(), borderColor)
                context.fill(x.toInt(), y.toInt(), (x + 1).toInt(), (y + cardH).toInt(), borderColor)
                context.fill((x + cardW - 1).toInt(), y.toInt(), (x + cardW).toInt(), (y + cardH).toInt(), borderColor)

                val textColor = if (module.enabled) t.textPrimary else t.textSecondary
                context.text(mc.font, module.name, (x + 10).toInt(), (y + 7).toInt(), textColor, false)

                val toggleW = 16f
                val toggleH = 8f
                val toggleX = x + cardW - toggleW - 8f
                val toggleY = y + 7f
                val trackColor = if (module.enabled) t.toggleTrackOn else t.toggleTrackOff
                drawRoundedRect(context, toggleX, toggleY, toggleW, toggleH, toggleH / 2f, trackColor)

                val dotSize = 6f
                val dotX = if (module.enabled) toggleX + toggleW - dotSize - 1f else toggleX + 1f
                drawRoundedRect(context, dotX, toggleY + 1f, dotSize, dotSize, dotSize / 2f, t.toggleKnob)
            }

            col++
            if (col >= 2) {
                col = 0
                currentY += cardH + 6f
            }
        }

        context.disableScissor()
        super.extractRenderState(context, mouseX, mouseY, partialTick)
    }

    private fun drawRoundedRect(
        context: GuiGraphicsExtractor,
        x: Float, y: Float, width: Float, height: Float,
        radius: Float, color: Int
    ) {
        val r = radius.coerceAtMost(width / 2f).coerceAtMost(height / 2f)
        context.fill((x + r).toInt(), y.toInt(), (x + width - r).toInt(), (y + height).toInt(), color)
        context.fill(x.toInt(), (y + r).toInt(), (x + width).toInt(), (y + height - r).toInt(), color)

        val steps = r.toInt().coerceAtLeast(1)
        for (i in 0 until steps) {
            val stepY = y + i
            val stepH = 1f
            val offset = r - kotlin.math.sqrt(r * r - (r - i) * (r - i))

            context.fill((x + offset).toInt(), stepY.toInt(), (x + r).toInt(), (stepY + stepH).toInt(), color)
            context.fill((x + width - r).toInt(), stepY.toInt(), (x + width - offset).toInt(), (stepY + stepH).toInt(), color)

            val bottomY = y + height - i - 1
            context.fill((x + offset).toInt(), bottomY.toInt(), (x + r).toInt(), (bottomY + stepH).toInt(), color)
            context.fill((x + width - r).toInt(), bottomY.toInt(), (x + width - offset).toInt(), (bottomY + stepH).toInt(), color)
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mouseX = event.x()
        val mouseY = event.y()
        val button = event.button()

        if (isHovered(mouseX, mouseY, panelX + panelW - 26, panelY + 10, 16f, 16f) && button == 0) {
            this.onClose()
            return true
        }

        if (isHovered(mouseX, mouseY, panelX + 120, panelY + 8, 70f, 20f) && button == 0) {
            isDarkMode = !isDarkMode
            return true
        }

        if (isHovered(mouseX, mouseY, panelX, panelY, panelW, 36f) && button == 0) {
            dragging = true
            dragX = mouseX - panelX
            dragY = mouseY - panelY
            return true
        }

        var catY = panelY + 50
        for (cat in categories) {
            if (isHovered(mouseX, mouseY, panelX + 10, catY, 100f, 22f) && button == 0) {
                currentCategory = cat
                nlScroll = 0f
                return true
            }
            catY += 26f
        }

        val gridX = panelX + 120
        val gridY = panelY + 50
        val gridW = panelW - 135
        val cardW = (gridW - 10) / 2
        val cardH = 22f

        val modules = ModuleManager.filter { !it.hidden && it.category.tag == currentCategory }
        var currentY = gridY - nlScroll
        var col = 0
        for (module in modules) {
            val x = gridX + (col * (cardW + 10))
            val y = currentY

            if (isHovered(mouseX, mouseY, x, y, cardW, cardH)) {
                if (button == 0) {
                    module.enabled = !module.enabled
                } else if (button == 1) {
                    if (openModules.contains(module)) openModules.remove(module)
                    else openModules.add(module)
                }
                return true
            }

            col++
            if (col >= 2) {
                col = 0
                currentY += cardH + 6f
            }
        }

        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (dragging) {
            panelX = (event.x() - this.dragX).toFloat()
            panelY = (event.y() - this.dragY).toFloat()
            return true
        }
        return super.mouseDragged(event, dx, dy)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        dragging = false
        return super.mouseReleased(event)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val modules = ModuleManager.filter { !it.hidden && it.category.tag == currentCategory }
        val gridH = panelH - 65
        val rows = ceil(modules.size / 2.0).toInt()
        val totalHeight = rows * 28f

        if (totalHeight > gridH) {
            val maxScroll = totalHeight - gridH
            nlScroll -= scrollY.toFloat() * 15f
            if (nlScroll < 0f) nlScroll = 0f
            if (nlScroll > maxScroll) nlScroll = maxScroll
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key == 256) {
            this.onClose()
            return true
        }
        return super.keyPressed(event)
    }

    override fun onClose() {
        parentModule.enabled = false
        super.onClose()
    }

    override fun isPauseScreen(): Boolean = false

    private fun isHovered(mouseX: Double, mouseY: Double, x: Float, y: Float, w: Float, h: Float): Boolean {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h
    }
}
