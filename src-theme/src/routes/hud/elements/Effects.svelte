<script lang="ts">
    import { listen } from "../../../integration/ws";
    import type { ClientPlayerDataEvent, ClientPlayerEffectEvent } from "../../../integration/events";
    import type { StatusEffect } from "../../../integration/types";
    import { effectTextureUrl } from "../../../integration/rest";

    let effects: StatusEffect[] = [];

    listen("clientPlayerData", (event: ClientPlayerDataEvent) => {
        effects = event.playerData.effects;
    });

    listen("clientPlayerEffect", (event: ClientPlayerEffectEvent) => {
        effects = event.effects;
    });

    function formatTime(duration: number): string {
        if (duration === -1) return "*:*";
        const totalSeconds = Math.floor(duration / 20);
        const minutes = Math.floor(totalSeconds / 60);
        const seconds = totalSeconds % 60;
        return `${minutes}:${seconds.toString().padStart(2, "0")}`;
    }

    function formatAmplifier(n: number): string {
        return (n + 1).toString();
    }
</script>

{#if effects.length > 0}
    <div class="effects">
        {#each effects as e}
            <div class="effect">
                <img class="effect-icon" src={effectTextureUrl(e.effect)} alt={e.localizedName} />
                <span class="name">{e.localizedName}  <span class="amplifier">{formatAmplifier(e.amplifier)}</span></span>
                <span class="duration">{formatTime(e.duration)}</span>
            </div>
        {/each}
    </div>
{/if}

<style lang="scss">
    .effects {
        display: flex;
        flex-direction: column;
        gap: 4px;
        background: transparent !important;
        backdrop-filter: blur(8px) saturate(1.2) !important;
        -webkit-backdrop-filter: blur(8px) saturate(1.2) !important;
        border: 1px solid rgba(255, 255, 255, 0.08);
        animation: glassPulse 4s ease-in-out infinite;
        box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15) !important;
        border-radius: 12px;
        padding: 8px 14px;
        transition: box-shadow 0.3s;
    }

    .effects:hover {
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

    .effect {
        display: flex;
        align-items: center;
        gap: 8px;
        font-weight: 500;
        font-size: 14px;
        padding: 2px 0;

        .effect-icon {
            width: 16px;
            height: 16px;
            image-rendering: pixelated;
            image-rendering: -moz-crisp-edges;
            image-rendering: crisp-edges;
        }

        .name {
            color: var(--effects-name-color, #e0e0e0);
        }

        .amplifier {
            color: var(--effects-amplifier-color, #ffaa66);
        }

        .duration {
            margin-left: auto;
            font-family: monospace;
            color: var(--effects-duration-color, #aac8ff);
            font-size: 12px;
        }
    }
</style>
