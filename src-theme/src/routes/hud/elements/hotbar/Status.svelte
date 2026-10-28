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
            style="transform: scaleX({Math.max(0, Math.min(1, value / max))}); background-color: {color};"
    ></div>
</div>

<style lang="scss">

  .label {
    color: var(--clickgui-text-color);
    position: absolute;
    font-size: 12px;
    font-weight: 500;
    right: 6px;
    top: 50%;
    transform: translateY(-50%);
    font-variant-numeric: tabular-nums;
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
  }

    .progress {
        position: relative;
        border-radius: 8px;
        background-color: var(--hud-panel-soft-background, rgba(255, 255, 255, 0.35));

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

    /* [Perf] width 动画 -> transform: scaleX：视觉完全一致，但只走合成层，不触发 layout */
    .progress-bar {
    width: 100%;
    transform-origin: left;
    border-radius: 8px;
    height: 18px;
    will-change: transform;
    transition: transform 0.4s ease;

    &.align-right {
      margin-left: auto;
      transform-origin: right;
    }
  }
</style>
