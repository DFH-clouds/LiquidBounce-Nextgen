/*
 * 检测其他玩家的药水效果与装备武器，并通过聊天框提醒。 26/9/13 By 花辞树    FIX 26/10/5
 */
package net.ccbluex.liquidbounce.features.module.modules.`fun`

import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.utils.client.chat
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player

object ModulePlayerStatusDetector : ClientModule(
    "PlayerStatusDetector",
    ModuleCategories.MISC
) {


    private val detectEffects by boolean("DetectEffects", true)
    private val alertOnEffectGain by boolean("AlertOnEffectGain", true)
    private val alertOnEffectChange by boolean("AlertOnEffectChange", true)
    private val alertOnEffectLost by boolean("AlertOnEffectLost", true)
    private val showDuration by boolean("ShowDuration", true)
    private val hideInvisibleEffects by boolean("HideInvisibleEffects", true)
    private val ignoreEffects by text("IgnoreEffects", "night_vision,conduit_power")


    private val detectWeapon by boolean("DetectWeapon", true)
    private val detectOffhand by boolean("DetectOffhand", true)
    private val detectArmor by boolean("DetectArmor", true)


    private val checkInterval by int("CheckInterval", 20, 5..100)
    private val range by float("Range", 64.0f, 8.0f..128.0f)


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

            //药水效果检测
            if (detectEffects) {
                val effects = getFilteredEffects(player)
                val effectsHash = buildEffectsHash(effects)
                val prevHash = knownStates[playerName]?.first ?: ""

                if (effectsHash != prevHash) {
                    val prevEffects = parseEffectHash(prevHash)
                    val currEffects = parseEffectHash(effectsHash)

                    val gained = currEffects.keys - prevEffects.keys
                    val lost = prevEffects.keys - currEffects.keys
                    val changed = currEffects.keys.intersect(prevEffects.keys)
                        .filter { currEffects[it] != prevEffects[it] }

                    if (gained.isNotEmpty() && alertOnEffectGain) {
                        val list = gained.mapNotNull { effects.find { e -> effectKey(e) == it } }
                            .filter { effects.isNotEmpty() }
                            .map { formatEffect(it) }
                        if (list.isNotEmpty()) {
                            alertParts += "§a获得药水: §f${list.joinToString("§f, ")}"
                        }
                    }

                    if (changed.isNotEmpty() && alertOnEffectChange) {
                        val list = changed.mapNotNull { key ->
                            effects.find { e -> effectKey(e) == key }?.let { formatEffect(it) }
                        }
                        if (list.isNotEmpty()) {
                            alertParts += "§e药水变化: §f${list.joinToString("§f, ")}"
                        }
                    }

                    if (lost.isNotEmpty() && alertOnEffectLost) {
                        val list = lost.map { key ->
                            val sec = prevEffects[key] ?: ""
                            val name = key.substringBeforeLast(":")
                            val amp = key.substringAfterLast(":").toIntOrNull() ?: 0
                            val levelText = if (amp > 0) " ${toRoman(amp + 1)}" else ""
                            "§b$name§f$levelText"
                        }
                        if (list.isNotEmpty()) {
                            alertParts += "§c药水消失: §f${list.joinToString("§f, ")}"
                        }
                    }
                }

                knownStates[playerName] = effectsHash to (knownStates[playerName]?.second ?: "")
            }

            if (detectWeapon || detectOffhand || detectArmor) {
                val gearHash = buildGearHash(player)
                val prev = knownStates[playerName]
                if (gearHash != prev?.second) {
                    if (detectWeapon) {
                        getWeaponDescription(player)?.let {
                            alertParts += "§c主手: §f$it"
                        }
                    }
                    if (detectOffhand) {
                        getOffhandDescription(player)?.let {
                            alertParts += "§e副手: §f$it"
                        }
                    }
                    if (detectArmor) {
                        getArmorDescription(player)?.let { armor ->
                            alertParts += "§7护甲: ${armor.joinToString(", ")}"
                        }
                    }

                    knownStates[playerName] = (knownStates[playerName]?.first ?: "") to gearHash
                }
            }

            // 发送提醒
            if (alertParts.isNotEmpty()) {
                chat("§8[§6花辞树提醒您§8] §f§e$playerName §f→ ${alertParts.joinToString(" §8| ")}")
            }
        }

        val currentNames = level.players()
            .filter { it !== localPlayer }
            .map { it.name.string }
            .toSet()
        knownStates.keys.removeAll { it !in currentNames }
    }

    private fun getFilteredEffects(player: Player): Collection<MobEffectInstance> {
        val ignored = ignoreEffects.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()

        return player.activeEffects.filter { effect ->
            if (hideInvisibleEffects && !effect.isVisible) return@filter false
            val key = effectKey(effect)
            val name = key.substringBeforeLast(":")
            name.lowercase() !in ignored
        }
    }

    private fun buildEffectsHash(effects: Collection<MobEffectInstance>): String =
        effects.map { effectKey(it) + ":" + effectDurationSec(it) }
            .sorted()
            .joinToString("|")

    private fun effectKey(effect: MobEffectInstance): String {
        val name = effect.effect.value().displayName.string
        return "$name:${effect.amplifier}"
    }

    private fun effectDurationSec(effect: MobEffectInstance): Int =
        if (effect.isInfiniteDuration) -1 else effect.duration / 20

    private fun parseEffectHash(hash: String): Map<String, String> {
        if (hash.isEmpty()) return emptyMap()
        return hash.split("|").mapNotNull { part ->
            val idx = part.lastIndexOf(":")
            if (idx <= 0) return@mapNotNull null
            val key = part.substring(0, idx)
            val sec = part.substring(idx + 1)
            key to sec
        }.toMap()
    }

    private fun formatEffect(entry: MobEffectInstance): String {
        val name = entry.effect.value().displayName.string
        val amp = entry.amplifier
        val levelText = if (amp > 0) " ${toRoman(amp + 1)}" else ""
        val durationText = if (showDuration) {
            val sec = effectDurationSec(entry)
            " (${if (sec < 0) "∞" else formatTime(sec)})"
        } else ""
        return "§b$name§f$levelText$durationText"
    }

    private fun getWeaponDescription(player: Player): String? {
        val mainHand = player.mainHandItem
        if (mainHand.isEmpty) return null
        val countText = if (mainHand.count > 1) " x${mainHand.count}" else ""
        return "§c${mainHand.hoverName.string}§f$countText"
    }

    private fun getOffhandDescription(player: Player): String? {
        val offhand = player.getItemBySlot(EquipmentSlot.OFFHAND)
        if (offhand.isEmpty) return null
        val countText = if (offhand.count > 1) " x${offhand.count}" else ""
        return "§e${offhand.hoverName.string}§f$countText"
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
        val offhandId = player.getItemBySlot(EquipmentSlot.OFFHAND).let {
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
        return "$weaponId||$offhandId||$armorIds"
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
