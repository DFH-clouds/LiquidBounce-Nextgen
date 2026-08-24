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
        <!-- ✅ 使用 TitleButtonIcon 组件 -->
        <TitleButtonIcon {icon} />
    </div>
    <div class="title">{title}</div>
    <div class="wrapped-content">
        <slot parentHovered={hovered} />
    </div>
</div>

<style lang="scss">
    .main-button {
            /* 圆形按钮 */
        width: 110px;
        height: 110px;
        border-radius: 50%;
        padding: 0;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        cursor: pointer;
        background: rgba(255, 255, 255, 0.08);
        border: 2px solid rgba(255, 255, 255, 0.15);
        transition: all 0.25s ease;
        will-change: transform, background, border-color;
        overflow: hidden;
        box-sizing: border-box;
        /* 清除继承的网格属性 */
        grid-template-columns: unset;
        column-gap: unset;
        background-size: unset;
        background-position: unset;
    }

    .main-button:hover {
        background: rgba(255, 255, 255, 0.2);
        border-color: rgba(255, 255, 255, 0.4);
        transform: scale(1.06);
        box-shadow: 0 0 25px rgba(255, 255, 255, 0.15);
    }

    .main-button:active {
        transform: scale(0.95);
    }

    .icon {
        background-color: transparent !important;
        color: white;
        width: auto;
        height: auto;
        border-radius: 0;
        display: flex;
        align-items: center;
        justify-content: center;
        transition: none;
        margin: 0;
        padding: 0;
        line-height: 0;
    }

    /* 适配 TitleButtonIcon 内部的 SVG */
    .icon :global(svg) {
        width: 36px;
        height: 36px;
        fill: white;
        filter: drop-shadow(0 2px 4px rgba(0,0,0,0.4));
        display: block;
    }

    .title {
        font-size: 13px;
        color: white;
        font-weight: 600;
        text-shadow: 0 2px 10px rgba(0,0,0,0.6);
        text-align: center;
        letter-spacing: 0.5px;
        text-transform: uppercase;
        opacity: 0.9;
        white-space: nowrap;
        max-width: 100%;
        padding: 0 4px;
        margin: 4px 0 0 0;
        line-height: 1.2;
        overflow: hidden;
        text-overflow: ellipsis;
    }

    .wrapped-content {
        display: none; /* 隐藏子插槽 */
    }
</style>
