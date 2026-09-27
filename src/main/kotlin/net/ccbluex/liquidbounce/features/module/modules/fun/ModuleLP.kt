package net.ccbluex.liquidbounce.features.module.modules.`fun`

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
//一个仿windows蓝屏故障的娱乐模块
object ModuleLP : ClientModule("LP", ModuleCategories.FUN), EventListener {

    private var previousScreen: Screen? = null

    override fun onEnabled() {
        previousScreen = mc.screen
        mc.setScreen(FakeBsodScreen())
    }

    override fun onDisabled() {
        if (mc.screen is FakeBsodScreen) {
            mc.setScreen(previousScreen)
        }
    }

    private class FakeBsodScreen : Screen(Component.literal("Fake BSOD")) {

        override fun isPauseScreen(): Boolean = false

        override fun extractRenderState(
            graphics: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
            delta: Float
        ) {
            // 蓝色背景
            graphics.fill(0, 0, width, height, 0xFF0078D7.toInt())

            val lines = listOf(
                ":(",
                "",
                "你的设备遇到问题，需要重启。",
                "",
                "错误代码: MEMORY_MANAGEMENT",
                "已完成：100%",
            )

            val lineHeight = 30
            var y = height / 2 - (lines.size * lineHeight) / 2

            for (line in lines) {
                graphics.text(
                    font,
                    line,
                    60,
                    y,
                    0xFFFFFFFF.toInt(),
                    true
                )
                y += lineHeight
            }

            super.extractRenderState(graphics, mouseX, mouseY, delta)
        }

        override fun keyPressed(event: KeyEvent): Boolean {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                ModuleLP.enabled = false
                return true
            }
            return super.keyPressed(event)
        }
    }
}
