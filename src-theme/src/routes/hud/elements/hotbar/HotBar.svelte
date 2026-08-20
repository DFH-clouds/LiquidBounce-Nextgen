<script lang="ts">
    import Status from "./Status.svelte";
    import {listen} from "../../../../integration/ws";
    import type {PlayerData, TextComponent as TTExtComponent} from "../../../../integration/types";
    import {onMount} from "svelte";
    import {getPlayerData} from "../../../../integration/rest";
    import {fade} from "svelte/transition";
    import TextComponent from "../../../menu/common/TextComponent.svelte";
    import type {ClientPlayerDataEvent, OverlayMessageEvent} from "../../../../integration/events";

    let lastSlot = 0;
    let currentSlot = 0;
    let playerData: PlayerData | null = null;
    let maxAbsorption = 0;
    let slotsElement: HTMLElement | undefined;

    let showItemStackName = false;
    let showItemStackNameTimeout: number | null = null;
    let itemStackName: TTExtComponent | string | null = null;
    let overlayMessage: OverlayMessageEvent | null = null;
    let overlayMessageTimeout: number | null = null;

    function updatePlayerData(s: PlayerData) {
        playerData = s;
        if (playerData.absorption <= 0) {
            maxAbsorption = 0;
        }
        if (playerData.absorption > maxAbsorption) {
            maxAbsorption = playerData.absorption;
        }
        currentSlot = playerData.selectedSlot;
        if (currentSlot !== lastSlot) {
            lastSlot = currentSlot;
            if (playerData.mainHandStack.identifier !== "minecraft:air") {
                itemStackName = playerData.mainHandStack.displayName;
                if (showItemStackNameTimeout !== null) {
                    clearTimeout(showItemStackNameTimeout);
                }
                showItemStackName = true;
                showItemStackNameTimeout = setTimeout(() => {
                    showItemStackName = false;
                }, 2000);
            }
        }
    }

    listen("clientPlayerData", (event: ClientPlayerDataEvent) => {
        updatePlayerData(event.playerData);
    });

    listen("overlayMessage", (event: OverlayMessageEvent) => {
        overlayMessage = event;
        if (overlayMessageTimeout !== null) {
            clearTimeout(overlayMessageTimeout);
        }
        overlayMessageTimeout = setTimeout(() => {
            overlayMessage = null;
        }, 3000)
    });

    onMount(async () => {
        updatePlayerData(await getPlayerData());
    });
</script>

{#if playerData && playerData.gameMode !== "spectator"}
    <div class="hotbar">
        {#if overlayMessage !== null}
            <div class="overlay-message" out:fade={{duration: 300}}
                 style="max-width: {slotsElement?.offsetWidth ?? 0}px">
                <TextComponent fontSize={14} textComponent={overlayMessage.text} allowPreformatting={true} />
            </div>
        {/if}
        {#if showItemStackName && itemStackName !== null}
            <div class="item-name" out:fade={{duration: 300}}>
                <TextComponent fontSize={14} textComponent={itemStackName}/>
            </div>
        {/if}
        <div class="status">

            <div class="pair">
                {#if playerData.armor > 0}
                    <Status
                            max={20}
                            value={playerData.armor}
                            color="var(--hotbar-armor-color)"
                            alignRight={false}
                            icon="shield"
                    />
                {:else}
                    <div></div>
                {/if}

                {#if playerData.air < playerData.maxAir}
                    <Status
                            max={playerData.maxAir}
                            value={playerData.air}
                            color="var(--hotbar-air-color)"
                            alignRight={true}
                    />
                {:else}
                    <div></div>
                {/if}
            </div>

            {#if playerData.gameMode !== "creative"}
                {#if playerData.absorption > 0}
                    <div class="pair">
                        <Status
                                max={maxAbsorption}
                                value={playerData.absorption}
                                color="var(--hotbar-absorption-color)"
                                alignRight={false}
                        />

                        <div></div>
                    </div>
                {/if}
                <div class="pair">
                    <Status
                            max={playerData.maxHealth}
                            value={playerData.actualHealth ?? playerData.health}
                            color="var(--hotbar-health-color)"
                            alignRight={false}
                            icon="heart"
                    />
                    <Status
                            max={20}
                            value={playerData.food}
                            color="var(--hotbar-hunger-color)"
                            alignRight={true}
                            icon="food"
                    />
                </div>
            {/if}
            {#if playerData.experienceLevel > 0}
                <Status
                        max={100} value={playerData.experienceProgress * 100}
                        color="var(--hotbar-experience-color)"
                        alignRight={false}
                        label={playerData.experienceLevel.toString()}
                />
            {/if}

        </div>

        <div class="hotbar-elements">
            <div class="slider" style="left: {currentSlot * 45}px"></div>
            <div class="slots" bind:this={slotsElement}>
                <div class="slot"></div>
                <div class="slot"></div>
                <div class="slot"></div>
                <div class="slot"></div>
                <div class="slot"></div>
                <div class="slot"></div>
                <div class="slot"></div>
                <div class="slot"></div>
                <div class="slot"></div>
            </div>
        </div>

        {#if playerData?.offHandStack.identifier !== "minecraft:air"}
            <div class="offhand-slot"></div>
        {/if}
    </div>
{/if}

<style lang="scss">

  .pair {
    display: grid;
    grid-template-columns: 1fr 1fr;
    column-gap: 24px;
  }

  .status {
    display: flex;
    flex-direction: column;
    margin-bottom: 6px;
    row-gap: 6px;
    column-gap: 20px;
  }

  .hotbar-elements {
    background-color: var(--hud-panel-background, rgba(255, 255, 255, 1));
    backdrop-filter: blur(var(--hud-panel-blur, 0px));
    -webkit-backdrop-filter: blur(var(--hud-panel-blur, 0px));
    box-shadow: 0 8px 20px rgba(25, 35, 55, var(--hud-panel-shadow-alpha, 0.12));
    position: relative;
    border-radius: 12px;
    overflow: hidden;

    .slider {
      border: solid 2px var(--accent-color);
      height: 45px;
      width: 45px;
      position: absolute;
      border-radius: 12px;
      /* transition: linear left 0.05s; TODO: Animation is possible but annoying */
    }

    .slots {
      display: flex;
    }

    .slot {
      height: 45px;
      width: 45px;
    }
  }

  .offhand-slot {
    height: 45px;
    width: 45px;
    border-radius: 12px;
    background-color: var(--hud-panel-soft-background, rgba(255, 255, 255, 0.35));
    position: absolute;
    bottom: 0;
    left: -64px;
  }

  .item-name {
    color: var(--hotbar-text-color);
    text-shadow: 0 0 8px color-mix(in srgb, var(--hotbar-text-color) 40%, transparent);
    font-size: 14px;
    margin: 0 auto 20px;
    font-weight: 500;
    background-color: var(--hud-panel-background, rgba(255, 255, 255, 1));
    backdrop-filter: blur(var(--hud-panel-blur, 0px));
    -webkit-backdrop-filter: blur(var(--hud-panel-blur, 0px));
    box-shadow: 0 8px 20px rgba(25, 35, 55, var(--hud-panel-shadow-alpha, 0.12));
    padding: 6px 8px;
    border-radius: 10px;
    width: max-content;
  }

  .overlay-message {
    text-align: center;
    color: var(--hotbar-text-color);
    margin-bottom: 20px;
    overflow: hidden;
  }
</style>
