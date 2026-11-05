<script lang="ts">
    export let max: number;
    export let value: number;
    export let color: string;
    export let alignRight: boolean;

    export let label: string | null = null;
    export let icon: string | null = null;

    export let showText: boolean = true;
    export let showPercent: boolean = false;

    $: percentage = Math.max(0, Math.min(100, (value / max) * 100));
    $: displayValue = Math.ceil(value);
    $: displayMax = Math.ceil(max);
    $: fraction = Math.max(0, Math.min(100, (value / max) * 100));
</script>

<div class="progress" class:align-right={alignRight}>
    {#if showText}
        <div class="label">
            {#if label}
                {label}
            {:else if showPercent}
                {Math.floor(percentage)}%
            {:else}
                {displayValue} / {displayMax}
            {/if}
        </div>
    {/if}
    {#if icon}
        <img class="themed-icon-mask icon" src="img/hud/hotbar/icon-{icon}.svg" alt="" aria-hidden="true">
    {/if}
    <div
            class:align-right={alignRight}
            class="progress-bar"
            style="--pct: {fraction}%; background-color: {color};"
    ></div>
</div>

<style lang="scss">
  .progress {
    position: relative;
    border-radius: 8px;
    background-color: var(--hud-panel-soft-background, rgba(255, 255, 255, 0.35));
    overflow: hidden;

    &.align-right {
      .label {
        left: 6px;
        right: unset;
      }

      .icon {
        right: 6px;
        left: unset;
      }
    }
  }

  .label {
    color: var(--clickgui-text-color);
    position: absolute;
    font-size: 12px;
    font-weight: 500;
    right: 6px;
    top: 50%;
    transform: translateY(-50%);
    font-variant-numeric: tabular-nums;
    z-index: 1;
  }

  .icon {
    position: absolute;
    left: 6px;
    top: 50%;
    transform: translateY(-50%);
    width: 14px;
    height: 14px;
    --themed-icon-size: 14px;
    --themed-icon-color: var(--clickgui-text-color);
    z-index: 1;
  }

  .progress-bar {
    width: 100%;
    height: 18px;
    border-radius: 8px;
    clip-path: inset(0 calc(100% - var(--pct)) 0 0 round 8px);
    transition: clip-path 0.4s ease;

    &.align-right {
      clip-path: inset(0 0 0 calc(100% - var(--pct)) round 8px);
    }
  }
</style>
