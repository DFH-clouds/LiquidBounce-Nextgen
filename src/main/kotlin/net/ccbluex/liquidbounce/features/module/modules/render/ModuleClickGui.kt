package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.render.clickgui.ClickGuiHCS
import net.ccbluex.liquidbounce.utils.client.inGame
import org.lwjgl.glfw.GLFW
import net.ccbluex.liquidbounce.integration.screen.impl.CustomSharedMinecraftScreen
import net.ccbluex.liquidbounce.integration.screen.impl.CustomStandaloneMinecraftScreen
import net.ccbluex.liquidbounce.integration.screen.CustomScreenType
import net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.game.isTyping


//重写了ClickGUI 26/10/4
object ModuleClickGui :
    ClientModule("ClickGUI", ModuleCategories.RENDER, bind = GLFW.GLFW_KEY_RIGHT_SHIFT, disableActivation = true) {

    override val running get() = true

    private val scale by float("Scale", 1f, 0.5f..2f)

    fun guiScale(): Float = scale

    override fun onEnabled() {
        if (!LiquidBounce.isInitialized || !inGame) {
            return
        }

        mc.execute {
            mc.setScreen(ClickGuiHCS())
        }
        super.onEnabled()
    }
    fun invalidate() {
        val standaloneScreen = standaloneScreen ?: return
        val wasOpen = mc.screen == standaloneScreen

        // Close and invalidate old cache
        if (wasOpen) {
            mc.setScreen(null)
        }
        standaloneScreen.close()
        this.standaloneScreen = null

        // Only bother updating now if it was open before.
        if (wasOpen) {
            updateStandaloneScreen()
            mc.setScreen(this.standaloneScreen ?: CustomSharedMinecraftScreen(CustomScreenType.CLICK_GUI))
        }
    }
    fun sync() {
        if (!LiquidBounce.isInitialized) {
            return
        }

        standaloneScreen?.sync()
    }
    val isInSearchBar: Boolean
        get() {
            if (!isTyping) {
                return false
            }

            val screen = mc.screen ?: return false
            return screen is CustomSharedMinecraftScreen && screen.screenType == CustomScreenType.CLICK_GUI ||
                screen is CustomStandaloneMinecraftScreen && screen.screenType == CustomScreenType.CLICK_GUI
        }
    @Suppress("UnusedPrivateProperty")
    private val useStandaloneScreen by boolean("Cache", true).onChanged {
        mc.execute(::onEnabled)
    }
    fun updateStandaloneScreen(): Boolean {
        // Standalone Screen Cache
        if (useStandaloneScreen) {
            if (standaloneScreen == null) {
                standaloneScreen = CustomStandaloneMinecraftScreen(CustomScreenType.CLICK_GUI)
            } else {
                // Used in [worldChangeHandler] to determine if we need to sync.
                return true
            }
        } else if (standaloneScreen != null) {
            standaloneScreen?.close()
            standaloneScreen = null
        }

        return false
    }
    // Standalone screen instance for caching
    private var standaloneScreen: CustomStandaloneMinecraftScreen? = null

}
