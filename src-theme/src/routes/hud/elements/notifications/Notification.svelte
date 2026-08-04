<script lang="ts">
    export let title: string;
    export let message: string;
    export let severity: 'success' | 'error' | 'info' | 'enabled' | 'disabled' = 'info';
    export let duration: number = 3000;

    import { onMount } from 'svelte';
    let progress = 0;
//灵感来于木糖醇
    onMount(() => {
        const start = performance.now();
        const interval = setInterval(() => {
            const elapsed = performance.now() - start;
            progress = Math.min(elapsed / duration, 1);
            if (progress >= 1) clearInterval(interval);
        }, 16);
        return () => clearInterval(interval);
    });

    function isEnabled(): boolean {
        if (severity === 'enabled' || severity === 'success') return true;
        if (severity === 'disabled' || severity === 'error') return false;
        const text = (title + message).toLowerCase();
        if (text.includes('enable') || text.includes('开启') || text.includes('启用')) return true;
        if (text.includes('disable') || text.includes('关闭') || text.includes('禁用')) return false;
        return false;
    }

    function getColor(): string {
        return isEnabled() ? '#4CAF50' : '#F44336';
    }

    function getIconSVG(): string {
        if (isEnabled()) {
            return `<svg viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>`;
        } else {
            return `<svg viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>`;
        }
    }
</script>

<div class="notification">
    <div class="progress-overlay" style="width: {progress * 100}%; background-color: {getColor()};"></div>

    <div class="icon" style="background-color: {getColor()};">
        {@html getIconSVG()}
    </div>

    <div class="content">
        <div class="title">{title}</div>
        <div class="message">{message}</div>
    </div>
</div>

<style lang="scss">
    .notification {
        position: relative;
        display: flex;
        align-items: center;
        gap: 12px;
        background: #ffffff;
        border-radius: 10px;
        padding: 14px 18px;
        width: 340px;
        margin-bottom: 12px;
        box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
        overflow: hidden;
        font-family: 'Segoe UI', 'PingFang SC', Roboto, sans-serif;
        color: #222;
        z-index: 0;
    }

    .progress-overlay {
        position: absolute;
        top: 0;
        left: 0;
        height: 100%;
        transition: width 0.1s linear;
        border-radius: 10px;
        pointer-events: none;
        z-index: 1;
        opacity: 0.3;
    }

    .icon {
        flex-shrink: 0;
        width: 38px;
        height: 38px;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 2;
        position: relative;

        svg {
            display: block;
            width: 24px;
            height: 24px;
            stroke: white;
            fill: none;
        }
    }

    .content {
        flex: 1;
        display: flex;
        flex-direction: column;
        gap: 4px;
        overflow: hidden;
        z-index: 2;
        position: relative;
    }

    .title {
        font-size: 15px;
        font-weight: 600;
        color: #1a1a1a;
        line-height: 1.2;
    }

    .message {
        font-size: 13px;
        color: #555;
        white-space: nowrap;
        text-overflow: ellipsis;
        overflow: hidden;
    }
</style>
