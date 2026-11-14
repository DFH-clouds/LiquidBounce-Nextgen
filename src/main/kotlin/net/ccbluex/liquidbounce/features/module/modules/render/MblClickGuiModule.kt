package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.render.clickgui.MBLClickGui
import net.ccbluex.liquidbounce.utils.client.mc

object MblClickGuiModule : ClientModule("MblClickGui", ModuleCategories.RENDER) {

    override fun onEnabled() {
        super.onEnabled()
        mc.execute {
            mc.setScreen(MBLClickGui(this))
        }
    }

    override fun onDisabled() {
        super.onDisabled()
        if (mc.screen is MBLClickGui) {
            mc.execute {
                mc.setScreen(null)
            }
        }
    }
}
