package net.ccbluex.liquidbounce.features.module.modules.`fun`

import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.utils.client.chat
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player

/**
 * 检测其他玩家的药水效果与装备武器，并通过聊天框提醒。 26/9/13 By 花辞树
 */
object ModulePlayerStatusDetector : ClientModule(
    "PlayerStatusDetector",
    ModuleCategories.MISC
) {

    private val detectEffects: Boolean by boolean("DetectEffects", true)
    private val detectWeapon: Boolean by boolean("DetectWeapon", true)
    private val detectArmor: Boolean by boolean("DetectArmor", true)
    private val checkInterval: Int by int("CheckInterval", 20, 5..100)
    private val range: Float by float("Range", 64.0f, 8.0f..128.0f)
    private val showDuration: Boolean by boolean("ShowDuration", true)

    private var tickCounter = 0
    private val knownStates = mutableMapOf<String, Pair<String, String>>()

    override fun onEnabled() {
        knownStates.clear()
        tickCounter = 0
        chat("§a[PlayerStatusDetector] §f已启用")
    }

    override fun onDisabled() {
        knownStates.clear()
        tickCounter = 0
        chat("§c[PlayerStatusDetector] §f已禁用")
    }

    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> {
        tickCounter++
        if (tickCounter >= checkInterval) {
            tickCounter = 0
            checkPlayers()
        }
    }

    private fun checkPlayers() {
        val localPlayer = mc.player ?: return
        val level = mc.level ?: return

        level.players().forEach { player ->
            if (player === localPlayer) return@forEach
            if (!player.isAlive) return@forEach
            if (player.isCreative) return@forEach
            if (!isInRange(player)) return@forEach

            val playerName = player.name.string
            val alertParts = mutableListOf<String>()

            // 药水效果检测
            if (detectEffects) {
                val effects = player.activeEffects
                if (effects.isNotEmpty()) {
                    val effectsHash = buildEffectsHash(effects)
                    val prev = knownStates[playerName]
                    if (effectsHash != prev?.first) {
                        val effectList = formatEffects(effects)
                        if (effectList.isNotEmpty()) {
                            alertParts += "§a药水: §f${effectList.joinToString("§f, ")}"
                        }
                    }
                } else {
                    knownStates[playerName] = "" to (knownStates[playerName]?.second ?: "")
                }
            }

            //装备武器检测
            if (detectWeapon || detectArmor) {
                val gearHash = buildGearHash(player)
                val prev = knownStates[playerName]
                if (gearHash != prev?.second) {
                    if (detectWeapon) {
                        getWeaponDescription(player)?.let {
                            alertParts += "§c主手: §f$it"
                        }
                    }
                    if (detectArmor) {
                        getArmorDescription(player)?.let { armor ->
                            alertParts += "§7护甲: ${armor.joinToString(", ")}"
                        }
                    }
                }
            }

            // 发送提醒
            if (alertParts.isNotEmpty()) {
                chat("§8[§6花辞树提醒您§8] §f§e$playerName §f→ ${alertParts.joinToString(" §8| ")}")
            }

            // ---- 更新缓存 ----
            val newEffectsHash = if (detectEffects) {
                val effects = player.activeEffects
                if (effects.isNotEmpty()) buildEffectsHash(effects) else ""
            } else {
                knownStates[playerName]?.first ?: ""
            }
            val newGearHash = if (detectWeapon || detectArmor) {
                buildGearHash(player)
            } else {
                knownStates[playerName]?.second ?: ""
            }
            knownStates[playerName] = newEffectsHash to newGearHash
        }

        // 清理已离开的玩家缓存
        val currentNames = level.players()
            .filter { it !== localPlayer }
            .map { it.name.string }
            .toSet()
        knownStates.keys.removeAll { it !in currentNames }
    }

    // ===== 工具函数 =====

    private fun buildEffectsHash(effects: Collection<MobEffectInstance>): String =
        effects.map { "${it.effect.value().displayName.string}:${it.amplifier}:${it.duration}" }
            .sorted()
            .joinToString("|")

    private fun formatEffects(effects: Collection<MobEffectInstance>): List<String> =
        effects.map { entry ->
            val name = entry.effect.value().displayName.string
            val amp = entry.amplifier
            val levelText = if (amp > 0) " ${toRoman(amp + 1)}" else ""
            val durationText = if (showDuration) {
                val sec = entry.duration / 20
                " (${formatTime(sec)})"
            } else ""
            "§b$name§f$levelText$durationText"
        }

    private fun getWeaponDescription(player: Player): String? {
        val mainHand = player.mainHandItem
        if (mainHand.isEmpty) return null
        val countText = if (mainHand.count > 1) " x${mainHand.count}" else ""
        return "§c${mainHand.hoverName.string}§f$countText"
    }

    private fun getArmorDescription(player: Player): List<String>? {
        val slots = listOf(
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
        )
        val pieces = slots.mapNotNull { slot ->
            val stack = player.getItemBySlot(slot)
            if (!stack.isEmpty) "§7${stack.hoverName.string}" else null
        }
        return pieces.ifEmpty { null }
    }

    private fun buildGearHash(player: Player): String {
        val weaponId = player.mainHandItem.let {
            if (it.isEmpty) "empty" else it.item.toString()
        }
        val armorIds = listOf(
            EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET
        ).joinToString(",") { slot ->
            player.getItemBySlot(slot).let {
                if (it.isEmpty) "empty" else it.item.toString()
            }
        }
        return "$weaponId||$armorIds"
    }

    private fun isInRange(player: Player): Boolean {
        val localPlayer = mc.player ?: return false
        return localPlayer.distanceToSqr(player) <= (range * range).toDouble()
    }

    private fun toRoman(num: Int): String {
        val map = mapOf(
            1 to "I", 2 to "II", 3 to "III", 4 to "IV", 5 to "V",
            6 to "VI", 7 to "VII", 8 to "VIII", 9 to "IX", 10 to "X"
        )
        return map[num] ?: num.toString()
    }

    private fun formatTime(totalSeconds: Int): String {
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return "$m:${s.toString().padStart(2, '0')}"
    }
}
