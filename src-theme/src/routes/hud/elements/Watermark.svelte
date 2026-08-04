<script lang="ts">
    import { onDestroy, onMount } from "svelte";
    import LiquidBounceLogo from "../../../components/LiquidBounceLogo.svelte";

//player显示异常没修
    // 配置
    export let settings: {
        username?: string;
        textColor?: string;
        accentColor?: string;
        backgroundColor?: string;
        fontFamily?: string;
        fontSize?: number;
        showLogo?: boolean;
    } = {};

    const config = {
        username: settings.username ?? "Player",
        textColor: settings.textColor ?? "#ffffff",
        accentColor: settings.accentColor ?? "#ff6b6b",
        backgroundColor: settings.backgroundColor ?? "transparent",
        fontFamily: settings.fontFamily ?? "Inter, sans-serif",
        fontSize: settings.fontSize ?? 16,
        showLogo: settings.showLogo ?? true,
    };

    // FPS 计算 最高上限60
    let fps = 0;
    let frameCount = 0;
    let lastFpsUpdate = performance.now();
    let rafId: number | null = null;

    function updateFps() {
        frameCount++;
        const now = performance.now();
        const delta = now - lastFpsUpdate;
        if (delta >= 1000) {
            fps = Math.round((frameCount * 1000) / delta);
            frameCount = 0;
            lastFpsUpdate = now;
        }
        rafId = requestAnimationFrame(updateFps);
    }

    // 时间更新
    let currentTime = "";
    let timeInterval: number | null = null;

    function updateTime() {
        const now = new Date();
        currentTime = now.toLocaleTimeString("en-US", {
            hour: "2-digit",
            minute: "2-digit",
            second: "2-digit"
        });
    }

    onMount(() => {
        updateFps();
        updateTime();
        timeInterval = setInterval(updateTime, 1000);
        onDestroy(() => {
            if (rafId) cancelAnimationFrame(rafId);
            if (timeInterval) clearInterval(timeInterval);
        });
    });

    // 模板
    $: html = `
        <div class="watermark-onetap">
            <span class="text">${config.username} | FPS: ${fps} | ${currentTime}</span>
        </div>
    `;
</script>

<div
    class="watermark"
    style="
        --text-color: {config.textColor};
        --accent-color: {config.accentColor};
        --bg-color: {config.backgroundColor};
        --font-family: {config.fontFamily};
        --font-size: {config.fontSize}px;
    "
>
    {#if config.showLogo}
        <div class="logo">
            <LiquidBounceLogo width="120px" height="auto" badgeFill="var(--accent-color)" />
        </div>
    {/if}
    <div class="content-wrapper">
        {@html html}
    </div>
</div>

<style lang="scss">
    .watermark {
        display: flex;
        align-items: center;
        gap: 12px;
        background: var(--bg-color);
        padding: 4px 8px;
        font-family: var(--font-family);
        font-size: var(--font-size);
        color: var(--text-color);
        width: max-content;
        box-shadow: none;
        backdrop-filter: none;
        border-radius: 0;
    }

    .logo { flex-shrink: 0; }
    .content-wrapper { display: flex; align-items: center; }

    .watermark-onetap .text {
        font-weight: 500;
        white-space: nowrap;
    }
</style>
