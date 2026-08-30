<script lang="ts">
    import { onDestroy, onMount } from "svelte";
    import { getClientInfo, getSession, getPlayerData } from "../../../integration/rest";
    import { listen } from "../../../integration/ws";
    import type { ClientInfo, ClientPlayerDataEvent, ModuleToggleEvent } from "../../../integration/events";
    import type { PlayerData, Session } from "../../../integration/types";

    export let settings: {
        username?: string;
        textColor?: string;
        accentColor?: string;
        backgroundColor?: string;
        fontFamily?: string;
        fontSize?: number;
        showLogo?: boolean;
    } = {
        fontFamily: 'Minecraft, sans-serif',
        fontSize: 16
    };

    let clientInfo: ClientInfo | null = null;
    let session: Session | null = null;
    let playerData: PlayerData | null = null;
    let fps = 0;
    let clientInfoInterval: number | null = null;
    const DISPLAY_NAME = "Exhibition";

    $: serverIp = clientInfo?.serverIp ?? "N/A";
    $: ping = clientInfo?.ping ?? 0;

    let currentTime = "";
    let timeInterval: number | null = null;

    let isFontReady = false;

    function updateTime() {
        const now = new Date();
        currentTime = now.toLocaleTimeString("en-US", {
            hour: "2-digit", minute: "2-digit", hour12: true,
        });
    }

    async function updateSession() { session = await getSession(); }
    async function updatePlayerData() { playerData = await getPlayerData(); }
    async function updateClientInfo() { clientInfo = await getClientInfo(); fps = clientInfo?.fps ?? 0; }

    onMount(async () => {
        try {
            await document.fonts.ready;
            await document.fonts.load("16px Minecraft");
            isFontReady = true;
        } catch (e) {
            console.error("字体加载失败，显示备用字体", e);
            isFontReady = true;
        }

        await updateSession();
        await updatePlayerData();
        await updateClientInfo();
        updateTime();

        timeInterval = setInterval(updateTime, 1000);
        clientInfoInterval = setInterval(updateClientInfo, 1000);

        const unsubSession = listen("session", updateSession);
        const unsubPlayer = listen("clientPlayerData", (e: ClientPlayerDataEvent) => { playerData = e.playerData; });
        const unsubModule = listen("moduleToggle", (e: ModuleToggleEvent) => {});

        onDestroy(() => {
            if (timeInterval) clearInterval(timeInterval);
            if (clientInfoInterval) clearInterval(clientInfoInterval);
            unsubSession(); unsubPlayer(); unsubModule();
        });
    });
</script>

{#if isFontReady}
<div class="exhibition-watermark" style="--accent-color: {settings.accentColor ?? '#ff6b6b'}; --font-family: {settings.fontFamily ?? 'Minecraft, sans-serif'}; --font-size: {settings.fontSize ?? 16}px;">
    <span class="exhibition-wrapper">
        <span class="name-container">
            {DISPLAY_NAME}
            <span class="name-accent">{DISPLAY_NAME.charAt(0)}</span>
        </span>
        <span class="info-part">
            <span style="color: var(--accent-color);"> </span>
            <span style="color: #808080;">[</span><span style="color: #ffffff;">1.8.x</span><span style="color: #808080;">]</span>
            <span style="color: #808080;">[</span><span style="color: #ffffff;">{currentTime}</span><span style="color: #808080;">]</span>
            <span style="color: #808080;">[</span><span style="color: #ffffff;">{fps} FPS</span><span style="color: #808080;">]</span>
            <span style="color: #808080;">[</span><span style="color: #ffffff;">{serverIp}</span><span style="color: #808080;">]</span>
        </span>
    </span>
</div>
{/if}

<style lang="scss">
    @font-face {
        font-family: 'Minecraft';
        src: url('/fonts/Minecraft.ttf') format('truetype');
        font-display: block;
        font-weight: normal;
        font-style: normal;
    }

    .exhibition-watermark {
        display: flex;
        align-items: center;
        width: max-content;
        text-shadow: 1px 1px 0 rgba(0, 0, 0, 1);
        font-family: var(--font-family);
        font-size: var(--font-size);
        letter-spacing: 1px;
        -webkit-font-smoothing: none;
        -moz-osx-font-smoothing: unset;
        font-smooth: never;
        image-rendering: pixelated;
    }
    .exhibition-wrapper { display: flex; align-items: center; white-space: nowrap; gap: 0; }
    .name-container { position: relative; display: inline-block; color: #ffffff; }
    .name-accent { position: absolute; left: 0; top: 0; color: var(--accent-color); pointer-events: none; }
    .info-part { display: inline-block; color: var(--accent-color); }
</style>
