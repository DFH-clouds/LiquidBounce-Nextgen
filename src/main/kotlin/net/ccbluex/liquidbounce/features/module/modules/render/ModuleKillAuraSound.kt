package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.events.AttackEntityEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.utils.sound.ExternalSoundPlayer

object ModuleKillAuraSound : ClientModule("KillAuraSound", ModuleCategories.COMBAT) {

    private val volume by float("Volume", 1.0f, 0.0f..1.0f)
    private val pitch by float("Pitch", 1.0f, 0.5f..2.0f)
    private val soundType by enumChoice("Sound", SoundType.JP)

   //下面是你放MP3的位置 名字自己定义就行
    private enum class SoundType(
        override val tag: String,
        val resourcePath: String
    ) : Tagged {
        BBB("BBB","/assets/liquidbounce/sounds/bbb.mp3"),
        JP("JP", "/assets/liquidbounce/sounds/jp.mp3"),

    }

    @Suppress("unused")
    private val attackHandler = handler<AttackEntityEvent> { event ->
        //杀戮没攻击或为开启时 不会进行播放
        val target = ModuleKillAura.targetTracker.target
        if (!ModuleKillAura.running || target == null) return@handler
        // 只对杀戮当前目标才播放
        if (event.entity != target) return@handler

        ExternalSoundPlayer.play(soundType.resourcePath, volume, pitch)
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        // 回收播放完毕的 OpenAL source / buffer，防止资源泄漏
        ExternalSoundPlayer.tick()
    }
}
