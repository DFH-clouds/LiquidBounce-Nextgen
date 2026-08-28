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
        border-radius: 8px;
        overflow: hidden;
        font-size: 14px;
        background: transparent !important;
        backdrop-filter: blur(8px) saturate(1.2) !important;
        -webkit-backdrop-filter: blur(8px) saturate(1.2) !important;
        border: 1px solid rgba(255, 255, 255, 0.08);
        animation: glassPulse 4s ease-in-out infinite;
        box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15) !important;
        transition: box-shadow 0.3s;
    }

    .scoreboard:hover {
        box-shadow: 0 6px 24px rgba(0, 0, 0, 0.25) !important;
    }

    @keyframes glassPulse {
        0%, 100% {
            backdrop-filter: blur(8px) saturate(1.2);
            box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
            border-color: rgba(255, 255, 255, 0.08);
        }
        50% {
            backdrop-filter: blur(12px) saturate(1.3);
            box-shadow: 0 6px 24px rgba(0, 0, 0, 0.25);
            border-color: rgba(255, 255, 255, 0.15);
        }
    }

    .entries {
        background-color: transparent !important;
        padding: 8px 10px;
    }

    .row {
        display: flex;
        column-gap: 15px;
        justify-content: space-between;
    }

    .header {
        text-align: center;
        background-color: transparent !important;
        padding: 5px 10px;
        border-bottom: 1px solid rgba(255, 255, 255, 0.1);
    }
</style>
