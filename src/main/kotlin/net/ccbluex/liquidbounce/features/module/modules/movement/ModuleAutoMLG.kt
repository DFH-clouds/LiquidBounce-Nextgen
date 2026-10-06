/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.regular
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.Items
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

/**
 * AutoMLG
 *
 * 自动落地水：从高处坠落时自动切换水桶，落地前在脚下放水防摔，并尝试回收。
 */

//写炸了
object ModuleAutoMLG : ClientModule("AutoMLG", ModuleCategories.MOVEMENT) {

    private val fallDistance by float("FallDistance", 3.0f, 2.0f..15.0f)
    private val autoRecycle by boolean("AutoRecycle", true)
    private val notifyOnFail by boolean("NotifyOnFail", true)

    private var placedWater = false
    private var isPreparing = false
    private var timeout = 0
    private var placedWaterPos: BlockPos? = null
    private var originalSlot = -1

    override fun onEnabled() {
        reset()
    }

    override fun onDisabled() {
        SilentHotbar.resetSlot(this)
        reset()
    }

    private fun reset() {
        isPreparing = false
        timeout = 0
        placedWaterPos = null
        placedWater = false
        originalSlot = -1
    }

    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> {
        val player = mc.player ?: return@handler
        val level = mc.level ?: return@handler

        if (placedWater) {
            if (player.fallDistance <= fallDistance || player.onGround()) {
                if (originalSlot != -1) {
                    player.inventory.setSelectedSlot(originalSlot)
                }
                SilentHotbar.resetSlot(this)
                reset()
            }
            return@handler
        }

        if (player.fallDistance <= fallDistance) {
            if (isPreparing) {
                if (originalSlot != -1) {
                    player.inventory.setSelectedSlot(originalSlot)
                }
                SilentHotbar.resetSlot(this)
                reset()
            }
            return@handler
        }

        if (timeout > 0) {
            timeout--
            if (timeout == 0 && isPreparing) {
                if (notifyOnFail) chat(regular("§c[AutoMLG] 放水超时！"))
                if (originalSlot != -1) {
                    player.inventory.setSelectedSlot(originalSlot)
                }
                SilentHotbar.resetSlot(this)
                reset()
                return@handler
            }
        }

        if (!isPreparing) {
            val slot = findWaterBucket()
            if (slot != -1) {
                originalSlot = player.inventory.getSelectedSlot()
                player.inventory.setSelectedSlot(slot)
                isPreparing = true
                timeout = 40
            }
            return@handler
        }

        if (willHitGroundSoon(player, level)) {
            doPlaceWater(player)
        }
    }

    /**
     * 用 gameMode 走客户端正常交互流程放水。
     */
    private fun doPlaceWater(player: net.minecraft.client.player.LocalPlayer) {
        val level = mc.level ?: return
        val gameMode = mc.gameMode ?: return

        val basePos = player.blockPosition()
        var targetPos: BlockPos? = null

        for (dy in 1..4) {
            val below = basePos.below(dy)
            if (!level.getBlockState(below).isAir) {
                val placeAt = below.above()
                if (level.getBlockState(placeAt).isAir) {
                    targetPos = below
                    break
                }
            }
        }

        if (targetPos == null) return

        val stack = player.getItemBySlot(EquipmentSlot.MAINHAND)
        if (stack.item != Items.WATER_BUCKET) {
            if (notifyOnFail) chat(regular("§c[AutoMLG] 手上不是水桶！"))
            return
        }

        val hitVec = Vec3(
            targetPos.x + 0.5,
            targetPos.y + 1.0,
            targetPos.z + 0.5
        )
        val hitResult = BlockHitResult(hitVec, Direction.UP, targetPos, false)

        val result = gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hitResult)

        if (result.consumesAction()) {
            placedWaterPos = targetPos.above()
            placedWater = true
            isPreparing = false
            timeout = 0
            chat(regular("§a[AutoMLG] 已放水"))
        } else {
            if (notifyOnFail) chat(regular("§c[AutoMLG] 放水失败（服务端拒绝）"))
        }
    }

    @Suppress("unused")
    private val recycleHandler = handler<PlayerTickEvent> {
        if (!autoRecycle) return@handler
        val targetPos = placedWaterPos ?: return@handler
        val player = mc.player ?: return@handler

        val mainHand = player.getItemBySlot(EquipmentSlot.MAINHAND)
        if (mainHand.item != Items.BUCKET) return@handler

        val hit = mc.hitResult
        if (hit is BlockHitResult && hit.blockPos == targetPos) {
            val result = mc.gameMode?.useItem(player, InteractionHand.MAIN_HAND)
            if (result?.consumesAction() == true) {
                chat(regular("§a[AutoMLG] 已回收水源"))
                placedWaterPos = null
            }
        }
    }

    private fun willHitGroundSoon(
        player: net.minecraft.client.player.LocalPlayer,
        level: net.minecraft.client.multiplayer.ClientLevel
    ): Boolean {
        val box = player.boundingBox.move(0.0, -2.5, 0.0)
        return level.getBlockCollisions(player, box).iterator().hasNext()
    }

    private fun findWaterBucket(): Int {
        val player = mc.player ?: return -1
        for (i in 0 until 9) {
            val stack = player.inventory.getItem(i)
            if (!stack.isEmpty && stack.item == Items.WATER_BUCKET) {
                return i
            }
        }
        return -1
    }
}
