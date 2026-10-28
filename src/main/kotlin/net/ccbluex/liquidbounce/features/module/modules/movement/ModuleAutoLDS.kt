package net.ccbluex.liquidbounce.features.module.modules.player

import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.entity.useItem
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.InventoryItemSlot
import net.ccbluex.liquidbounce.utils.inventory.ItemSlot
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks

object ModuleAutoLDS : ClientModule("AutoLDS", ModuleCategories.PLAYER) {

    private val waterFallHeight by float("WaterFallHeight", 3.0f, 3.0f..30.0f)
    private val rodFallHeight by float("RodFallHeight", 10.0f, 3.0f..50.0f)
    private val autoPickUp by boolean("AutoPickUp", true)
    private val pickUpDelay by int("PickUpDelay", 2, 0..10)
    private val successMessage by boolean("SuccessMessage", true)

    private var state = State.IDLE
    private var currentKind = Kind.WATER
    private var currentHand = InteractionHand.MAIN_HAND

    private var fallStartY: Double? = null
    private var warned = false
    private var actionTimer = 0

    // SWAP 记录(用于把物品放回背包原处)
    private var swappedHotbarIndex: Int? = null
    private var swappedInvSlotId: Int? = null

    override suspend fun enabledEffect() {
        resetState()
    }

    private enum class State { IDLE, PLACED, WAIT_LAND, PICKUP }
    private enum class Kind { WATER, ROD }

    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> {
        val player = mc.player ?: return@handler

        when (state) {
            State.IDLE -> {
                if (player.onGround() || player.isInWater() || player.isInLava() ||
                    player.isFallFlying() || player.isPassenger()) {
                    resetState()
                    return@handler
                }

                val distance = fallDistance(player)

                // 扫描背包看有没有水桶或者是自救平台
                val waterSlot = findInventorySlot(player) { it.`is`(Items.WATER_BUCKET) }
                val rodSlot = findInventorySlot(player, ::isBedwarsRod)
                val offhandWater = player.getItemInHand(InteractionHand.OFF_HAND).`is`(Items.WATER_BUCKET)

                val targetSlot: ItemSlot? = when {
                    waterSlot != null && distance >= waterFallHeight -> waterSlot
                    offhandWater && distance >= waterFallHeight -> null          // null = 用副手
                    rodSlot != null && distance >= rodFallHeight -> rodSlot
                    else -> null
                }

                if (targetSlot == null && !(offhandWater && distance >= waterFallHeight)) {
                    if ((distance >= waterFallHeight || distance >= rodFallHeight) && !warned) {
                        warned = true
                        chat("§c[AutoLDS] §f使用失败")
                    }
                    return@handler
                }

                val isRod = rodSlot != null && targetSlot === rodSlot && distance >= rodFallHeight &&
                    waterSlot == null

                if (isRod) {
                    currentKind = Kind.ROD
                    currentHand = InteractionHand.MAIN_HAND
                    switchToHotbar(targetSlot!!)
                    useItem(InteractionHand.MAIN_HAND, player.yRot, 90f, SwingMode.DO_NOT_HIDE)
                    state = State.WAIT_LAND
                } else {
                    currentKind = Kind.WATER
                    currentHand = if (offhandWater && waterSlot == null) InteractionHand.OFF_HAND
                    else InteractionHand.MAIN_HAND
                    if (currentHand == InteractionHand.MAIN_HAND) switchToHotbar(targetSlot!!)

                    // 通过 use 包(interactItem) + 90° 俯角放水
                    lookDown(player)
                    useItem(currentHand, player.yRot, 90f, SwingMode.DO_NOT_HIDE)
                    state = State.PLACED
                    actionTimer = 0
                }
            }

            State.PLACED -> {
                actionTimer++
                val held = heldStack(player)

                // check是否正常自救
                val placedConfirmed = held.`is`(Items.BUCKET) || waterNear(player)

                if (placedConfirmed) {
                    state = State.WAIT_LAND
                    actionTimer = 0
                } else if (player.onGround()) {
                    chat("§c[AutoLDS] §f使用失败")
                    resetState()
                } else if (actionTimer >= 2 && held.`is`(Items.WATER_BUCKET)) {
                    // 若没放出 重新尝试一次
                    lookDown(player)
                    useItem(currentHand, player.yRot, 90f, SwingMode.DO_NOT_HIDE)
                    actionTimer = 0
                }
            }

            State.WAIT_LAND -> {
                if (player.onGround() || player.isInWater() || player.deltaMovement.y >= 0.0) {
                    state = State.PICKUP
                    actionTimer = 0
                }
            }

            State.PICKUP -> {
                actionTimer++
                val held = heldStack(player)

                when {
                    currentKind == Kind.ROD || !autoPickUp -> onSuccess()

                    // 生存和创造检测是否正常自救的逻辑
                    held.`is`(Items.WATER_BUCKET) && mc.player?.isCreative == true -> onSuccess()
                    held.`is`(Items.BUCKET) -> {
                        if (actionTimer >= pickUpDelay) {
                            lookDown(player)
                            useItem(currentHand, player.yRot, 90f, SwingMode.DO_NOT_HIDE)
                            actionTimer = 0
                        }
                    }

                    held.`is`(Items.WATER_BUCKET) -> onSuccess()

                    actionTimer > 20 -> onSuccess()
                }
            }
        }
    }

    private fun findInventorySlot(player: Player, predicate: (ItemStack) -> Boolean): ItemSlot? {
        for (i in 0..8) {
            if (predicate(player.inventory.getItem(i))) return HotbarItemSlot(i)
        }
        for (i in 9..35) {
            if (predicate(player.inventory.getItem(i))) return InventoryItemSlot(i - 9)
        }
        return null
    }

    private fun heldStack(player: Player): ItemStack =
        if (currentHand == InteractionHand.OFF_HAND) {
            player.getItemInHand(InteractionHand.OFF_HAND)
        } else {
            HotbarItemSlot(SilentHotbar.serversideSlot).itemStack
        }

    private fun waterNear(player: Player): Boolean {
        val world = mc.level ?: return false
        val feet = player.blockPosition()
        for (depth in 0..4) {
            val p = feet.below(depth)
            for (dx in -1..1) for (dz in -1..1) {
                if (world.getBlockState(p.offset(dx, 0, dz)).`is`(Blocks.WATER)) return true
            }
        }
        return false
    }

    private fun onSuccess() {
        if (successMessage) {
            chat("§c[AutoLDS] §fSuccessful")
        }
        resetState()
    }

    private fun switchToHotbar(slot: ItemSlot): HotbarItemSlot {
        if (slot is HotbarItemSlot) {
            SilentHotbar.selectSlotSilently(this, slot, 40)
            return slot
        }

        val invSlot = slot as? InventoryItemSlot
            ?: error("非背包槽位: $slot")
        val invServerId = invSlot.getIdForServer(null)
            ?: error("无法获取服务端槽位 id")

        val empty = HotbarItemSlot.mainHandSlots.firstOrNull { it.itemStack.isEmpty }
        val target = empty ?: HotbarItemSlot(mc.player?.inventory?.selectedSlot ?: 0)
        val hotbarIndex = target.hotbarIndex ?: 0

        mc.gameMode?.handleContainerInput(0, invServerId, hotbarIndex, ContainerInput.SWAP, mc.player ?: return target)
        swappedHotbarIndex = hotbarIndex
        swappedInvSlotId = invServerId

        SilentHotbar.selectSlotSilently(this, target, 40)
        return target
    }

    private fun restoreSwap() {
        val hotbarIndex = swappedHotbarIndex ?: return
        val invServerId = swappedInvSlotId ?: return
        val player = mc.player ?: return

        mc.gameMode?.handleContainerInput(0, invServerId, hotbarIndex, ContainerInput.SWAP, player)
        SilentHotbar.resetSlot(this)
        swappedHotbarIndex = null
        swappedInvSlotId = null
    }

    private var oldPitch: Float? = null

    private fun lookDown(player: Player) {
        if (oldPitch == null) oldPitch = player.xRot
        player.xRot = 90f
    }

    private fun restoreLook() {
        oldPitch?.let { mc.player?.xRot = it }
        oldPitch = null
    }

    private fun fallDistance(player: Player): Float {
        if (player.deltaMovement.y < 0.0 && !player.onGround()) {
            val start = fallStartY ?: player.y.also { fallStartY = it }
            return (start - player.y).toFloat()
        }
        fallStartY = null
        return 0f
    }

    private fun isBedwarsRod(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        if (stack.`is`(Items.BLAZE_ROD)) return true
        return stack.hoverName.string.contains("烈焰棒")
    }

    private fun resetState() {
        state = State.IDLE
        fallStartY = null
        warned = false
        actionTimer = 0
        currentHand = InteractionHand.MAIN_HAND
        restoreLook()
        restoreSwap()
        SilentHotbar.resetSlot(this)
    }
}
