package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import java.lang.reflect.Field

/**
 * 布吉岛三段伤害
 * 不知道还有没有用
 */
object ModuleCriticalsGrim : ClientModule("CriticalsGrim", ModuleCategories.COMBAT) {


    private val onlyOnGround by boolean("OnlyOnGround", true)
    private val resetCooldown by boolean("ResetCooldown", true)
    private val cooldownThreshold by float("CooldownThreshold", 0.1f, 0.0f..1.0f)



    private var attackStage = 0
    private var lastAttackTime = 0L



    private val onGroundField: Field? by lazy {
        try {
            net.minecraft.world.entity.Entity::class.java
                .getDeclaredField("onGround")
                .apply { isAccessible = true }
        } catch (_: Exception) { null }
    }

    private val attackCooldownField: Field? by lazy {
        try {
            net.minecraft.world.entity.player.Player::class.java
                .getDeclaredField("attackCooldown")
                .apply { isAccessible = true }
        } catch (_: Exception) { null }
    }

    private val jumpMethod by lazy {
        try {
            net.minecraft.world.entity.LivingEntity::class.java
                .getMethod("jump")
        } catch (_: Exception) { null }
    }

    private fun isOnGround(player: net.minecraft.world.entity.player.Player): Boolean {
        return try {
            onGroundField?.getBoolean(player) ?: false
        } catch (_: Exception) { false }
    }

    private fun setAttackCooldown(player: net.minecraft.world.entity.player.Player, value: Float) {
        try {
            attackCooldownField?.setFloat(player, value)
        } catch (_: Exception) { /* ignore */ }
    }

    private fun getAttackCooldown(player: net.minecraft.world.entity.player.Player): Float {
        return try {
            attackCooldownField?.getFloat(player) ?: 0.0f
        } catch (_: Exception) { 0.0f }
    }

    private fun jumpPlayer(player: net.minecraft.world.entity.player.Player) {
        try {
            jumpMethod?.invoke(player)
        } catch (_: Exception) { /* ignore */ }
    }

    private val inWaterField: Field? by lazy {
        try {
            net.minecraft.world.entity.Entity::class.java
                .getDeclaredField("inWater")
                .apply { isAccessible = true }
        } catch (_: Exception) { null }
    }

    private fun isInWater(player: net.minecraft.world.entity.player.Player): Boolean {
        return try {
            inWaterField?.getBoolean(player) ?: false
        } catch (_: Exception) { false }
    }

    private val vehicleField: Field? by lazy {
        try {
            net.minecraft.world.entity.Entity::class.java
                .getDeclaredField("vehicle")
                .apply { isAccessible = true }
        } catch (_: Exception) { null }
    }

    private fun isPassenger(player: net.minecraft.world.entity.player.Player): Boolean {
        return try {
            vehicleField?.get(player) != null
        } catch (_: Exception) { false }
    }


    /**
     * 监听攻击数据包，触发三段式状态机
     */
    private val packetHandler = handler<PacketEvent> { event ->
        val packet = event.packet
        if (packet !is ServerboundInteractPacket) return@handler

        val player = player ?: return@handler
        if (onlyOnGround && !isOnGround(player)) return@handler
        if (isInWater(player) || isPassenger(player)) return@handler

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAttackTime > 1500L) attackStage = 0
        lastAttackTime = currentTime

        when (attackStage) {
            0 -> {
                // 第一刀（普通）
                attackStage = 1
            }
            1 -> {
                // 第二刀（跳跃暴击）
                if (isOnGround(player)) {
                    jumpPlayer(player)
                }
                attackStage = 2
                if (resetCooldown) {
                    setAttackCooldown(player, 0.0f)
                }
            }
            2 -> {
                // 第三刀（补伤）
                attackStage = 0
            }
        }
    }

    private val tickHandler = handler<PlayerTickEvent> {
        if (attackStage == 2 && resetCooldown) {
            val player = player ?: return@handler
            if (getAttackCooldown(player) > cooldownThreshold) {
                setAttackCooldown(player, 0.0f)
            }
        }
    }


    override fun onEnabled() {
        super.onEnabled()
        attackStage = 0
        lastAttackTime = 0L
    }

    override fun onDisabled() {
        super.onDisabled()
        attackStage = 0
        mc.player?.stopUsingItem()
    }

}
