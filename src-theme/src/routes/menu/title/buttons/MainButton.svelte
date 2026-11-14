<!--
    苹果 Liquid Glass 圆形按钮。
    通过低白度基础底色 + SVG 折射滤镜 + 多重高光模拟真实的玻璃透镜感。
-->
<script lang="ts">
    import { fly } from "svelte/transition";
    import { createEventDispatcher } from "svelte";
    import { backIn, backOut } from "svelte/easing";
    import TitleButtonIcon from "./TitleButtonIcon.svelte";

    export let title: string;
    export let icon: string;
    export let index: number;

    let hovered = false;
    const dispatch = createEventDispatcher();
</script>

<!-- svelte-ignore a11y-no-static-element-interactions -->
<!-- svelte-ignore a11y-click-events-have-key-events -->
<div
    class="main-button"
    on:mouseenter={() => hovered = true}
    on:mouseleave={() => hovered = false}
    on:click={() => { hovered = false; dispatch("click"); }}
    out:fly|global={{ duration: 400, x: -500, delay: index * 100, easing: backIn }}
    in:fly|global={{ duration: 400, x: -500, delay: index * 100, easing: backOut }}
>
    <div class="icon">
        <TitleButtonIcon {icon} />
    </div>
    <div class="title">{title}</div>
    <div class="wrapped-content">
        <slot parentHovered={hovered} />
    </div>
</div>

<style lang="scss">
    .main-button {
        width: 110px;
        height: 110px;
        border-radius: 42% / 42%;
        padding: 0;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        cursor: pointer;
        position: relative;
        overflow: hidden;
        box-sizing: border-box;

        background: rgba(255, 255, 255, 0.03);

        backdrop-filter: blur(8px) saturate(1.8);
        -webkit-backdrop-filter: blur(8px) saturate(1.8);
        backdrop-filter: blur(8px) saturate(1.8) url(#liquid-lens);

        border: 1px solid rgba(255, 255, 255, 0.3);
        box-shadow:
            0 16px 40px rgba(0, 0, 0, 0.5),
            -2px 0 8px rgba(255, 100, 100, 0.2),
            2px 0 8px rgba(100, 200, 255, 0.2),
            inset 0 1.5px 2px rgba(255, 255, 255, 0.9),
            inset 0 -2px 4px rgba(255, 255, 255, 0.1),
            inset 0 0 0 1px rgba(0, 0, 0, 0.1);

        transition: all 0.4s cubic-bezier(0.25, 1, 0.5, 1);
    }

    .main-button:hover {
        background: rgba(255, 255, 255, 0.08);
        border-color: rgba(255, 255, 255, 0.5);
        transform: translateY(-3px) scale(1.02);
        box-shadow:
            0 20px 50px rgba(0, 0, 0, 0.6),
            -3px 0 12px rgba(255, 100, 100, 0.3),
            3px 0 12px rgba(100, 200, 255, 0.3),
            inset 0 2px 3px rgba(255, 255, 255, 1),
            inset 0 -2px 4px rgba(255, 255, 255, 0.2),
            inset 0 0 0 1px rgba(0, 0, 0, 0.1);
    }

    .main-button:active {
        transform: translateY(0) scale(0.96);
        box-shadow:
            0 4px 16px rgba(0, 0, 0, 0.3),
            inset 0 1px 2px rgba(255, 255, 255, 0.4);
    }

    .icon, .title {
        position: relative;
        z-index: 2;
    }

    .icon {
        background-color: transparent !important;
        color: white;
        width: auto;
        height: auto;
        display: flex;
        align-items: center;
        justify-content: center;
        transition: transform 0.3s ease;
        margin: 0;
        padding: 0;
        line-height: 0;
    }

    .main-button:hover .icon {
        transform: scale(1.15);
    }

    .icon :global(svg) {
        width: 36px;
        height: 36px;
        fill: white;
        filter: drop-shadow(0 4px 8px rgba(0,0,0,0.4));
        display: block;
    }

    .title {
        font-size: 13px;
        color: white;
        font-weight: 700;
        text-shadow: 0 2px 10px rgba(0,0,0,0.8);
        text-align: center;
        letter-spacing: 1px;
        text-transform: uppercase;
        opacity: 0.95;
        white-space: nowrap;
        max-width: 100%;
        padding: 0 4px;
        margin: 6px 0 0 0;
        line-height: 1.2;
        overflow: hidden;
        text-overflow: ellipsis;
    }

    .wrapped-content {
        display: none;
    }
</style>
