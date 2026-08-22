<script lang="ts">
    import { onDestroy, onMount } from "svelte";
    import LiquidBounceLogo from "../../../components/LiquidBounceLogo.svelte";
    import { getClientInfo, getSession, getPlayerData } from "../../../integration/rest";
    import { listen } from "../../../integration/ws";
    import type { ClientInfo, ClientPlayerDataEvent, ModuleToggleEvent } from "../../../integration/events";
    import type { PlayerData, Session } from "../../../integration/types";

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

    let clientInfo: ClientInfo | null = null;
    let session: Session | null = null;
    let playerData: PlayerData | null = null;
    let showUsername = true;

    let fps = 0;
    let clientInfoInterval: number | null = null;

    async function updateSession() {
        session = await getSession();
    }

    async function updatePlayerData() {
        playerData = await getPlayerData();
    }

    async function updateClientInfo() {
        clientInfo = await getClientInfo();
        fps = clientInfo?.fps ?? 0;
    }

    $: playerName = session?.username ?? playerData?.username ?? settings.username ?? "Player";
    $: displayName = showUsername ? playerName : "Protected";

    //时间
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
        updateSession();
        updatePlayerData();
        updateClientInfo();

        updateTime();
        timeInterval = setInterval(updateTime, 1000);
        clientInfoInterval = setInterval(updateClientInfo, 1000); // 每秒更新 FPS

        const unsubSession = listen("session", updateSession);
        const unsubPlayer = listen("clientPlayerData", (e: ClientPlayerDataEvent) => {
            playerData = e.playerData;
        });
        const unsubModule = listen("moduleToggle", (e: ModuleToggleEvent) => {
            if (e.moduleName.toLowerCase() === "nameprotect") {
                showUsername = !e.enabled;
            }
        });

        onDestroy(() => {
            if (timeInterval) clearInterval(timeInterval);
            if (clientInfoInterval) clearInterval(clientInfoInterval);
            unsubSession();
            unsubPlayer();
            unsubModule();
        });
    });

    $: html = `
        <div class="watermark-onetap">
            <span class="text">${displayName} | FPS: ${fps} | ${currentTime}</span>
        </div>
    `;
</script>

<div
    class="watermark"
    style="
        --text-color: {settings.textColor ?? '#ffffff'};
        --accent-color: {settings.accentColor ?? '#ff6b6b'};
        --font-family: {settings.fontFamily ?? 'Inter, sans-serif'};
        --font-size: {settings.fontSize ?? 16}px;
    "
>
    {#if settings.showLogo ?? true}
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
        width: max-content;
    }

    .logo { flex-shrink: 0; }
    .content-wrapper { display: flex; align-items: center; }

    :global(.watermark-onetap) {
        background: rgba(255, 255, 255, 0.35) !important;
        backdrop-filter: blur(12px) !important;
        -webkit-backdrop-filter: blur(12px) !important;
        box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12) !important;
        padding: 8px 12px;
        border-radius: 12px;
        display: inline-flex;
        align-items: center;
        font-weight: 500;
    }

    :global(.watermark-onetap .text) {
        white-space: nowrap;
    }
</style>
