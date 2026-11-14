<!--
    苹果 Liquid Glass 风格计分板。
-->
<script lang="ts">
    import {listen} from "../../../integration/ws";
    import type {PlayerData, Scoreboard} from "../../../integration/types";
    import TextComponent from "../../menu/common/TextComponent.svelte";
    import type {ClientPlayerDataEvent} from "../../../integration/events";

    export let settings: { [name: string]: any };

    const cSettings = settings as HudScoreboardSettings;

    let scoreboard: Scoreboard | null = null;

    listen("clientPlayerData", (e: ClientPlayerDataEvent) => {
        const playerData: PlayerData = e.playerData;
        scoreboard = playerData.scoreboard;
    });
</script>

<svg width="0" height="0" aria-hidden="true" style="position: absolute;">
  <filter id="liquid-lens">
    <feTurbulence type="fractalNoise" baseFrequency="0.01" numOctaves="2" result="noise" />
    <feDisplacementMap in="SourceGraphic" in2="noise" scale="40" xChannelSelector="R" yChannelSelector="G" />
  </filter>
</svg>

{#if scoreboard}
    <div class="scoreboard">
        {#if scoreboard.header && cSettings.show.includes('Header')}
            <div class="header">
                <TextComponent fontSize={14} allowPreformatting={true} textComponent={scoreboard.header}/>
            </div>
        {/if}
        <div class="entries">
            {#each scoreboard.entries as {name, score}}
                <div class="row">
                    {#if cSettings.show.includes('Name')}
                        <TextComponent fontSize={14} allowPreformatting={true} textComponent={name}/>
                    {/if}
                    {#if cSettings.show.includes('Score')}
                        <TextComponent fontSize={14} allowPreformatting={true} textComponent={score}/>
                    {/if}
                </div>
            {/each}
        </div>
    </div>
{/if}

<style lang="scss">
    .scoreboard {
        width: max-content;

        background: rgba(255, 255, 255, 0.05);
        backdrop-filter: blur(12px) saturate(1.8);
        -webkit-backdrop-filter: blur(12px) saturate(1.8);
        backdrop-filter: blur(12px) saturate(1.8) url(#liquid-lens);

        border: 1px solid rgba(255, 255, 255, 0.25);
        border-radius: 12px;
        box-shadow:
            0 12px 30px rgba(0, 0, 0, 0.3),
            -1px 0 6px rgba(255, 100, 100, 0.15),
            1px 0 6px rgba(100, 200, 255, 0.15),
            inset 0 1.5px 2px rgba(255, 255, 255, 0.8),
            inset 0 -1px 2px rgba(255, 255, 255, 0.1),
            inset 0 0 0 1px rgba(0, 0, 0, 0.05);

        overflow: hidden;
        font-size: 14px;
        transition: box-shadow 0.3s;
        text-shadow: 0 1px 3px rgba(0, 0, 0, 0.6);
    }

    .scoreboard:hover {
        box-shadow:
            0 16px 40px rgba(0, 0, 0, 0.4),
            -2px 0 8px rgba(255, 100, 100, 0.2),
            2px 0 8px rgba(100, 200, 255, 0.2),
            inset 0 2px 3px rgba(255, 255, 255, 1),
            inset 0 -2px 3px rgba(255, 255, 255, 0.2);
    }

    .entries {
        background-color: transparent !important;
        padding: 8px 12px;
        color: #ffffff;
    }

    .row {
        display: flex;
        column-gap: 15px;
        justify-content: space-between;
    }

    .header {
        text-align: center;
        background-color: rgba(0, 0, 0, 0.1) !important;
        padding: 6px 12px;
        border-bottom: 1px solid rgba(255, 255, 255, 0.15);
        color: #ffffff;
        font-weight: 700;
    }
</style>
