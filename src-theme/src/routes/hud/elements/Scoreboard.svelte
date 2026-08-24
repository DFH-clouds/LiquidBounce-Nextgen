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
    background: rgba(255, 255, 255, 0.18) !important;
    backdrop-filter: blur(12px) !important;
    -webkit-backdrop-filter: blur(12px) !important;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2) !important;
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
