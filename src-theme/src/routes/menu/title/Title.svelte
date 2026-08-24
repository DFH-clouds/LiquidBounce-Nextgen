<script lang="ts">
    import MainButton from "./buttons/MainButton.svelte";
    import { onDestroy } from "svelte";
    import {
        browse,
        exitClient,
        getClientUpdate,
        openScreen,
        toggleBackgroundShaderEnabled,
    } from "../../../integration/rest";
    import Menu from "../common/Menu.svelte";
    import { fly } from "svelte/transition";
    import { onMount } from "svelte";
    import { notification } from "../common/header/notification_store";

    import IconButton from "../common/buttons/IconButton.svelte";
    import IconTextButton from "../common/buttons/IconTextButton.svelte";
    import ButtonContainer from "../common/buttons/ButtonContainer.svelte";

    let regularButtonsShown = true;
    let clientButtonsShown = false;

    function preloadBackground() {
        const img = new Image();
        img.src = '/backgrounds/background.png';
    }

    onMount(() => {
        preloadBackground();
        setTimeout(async () => {
            const update = await getClientUpdate();
            if (update.updateAvailable) {
                notification.set({
                    title: `LiquidBounce ${update.newestVersion?.clientVersion} has been released!`,
                    message: `Download it from liquidbounce.net!`,
                    error: false,
                    delay: 99999999,
                });
            }
        }, 1500);
    });

    function toggleButtons() {
        if (clientButtonsShown) {
            clientButtonsShown = false;
            setTimeout(() => {
                regularButtonsShown = true;
            }, 700);
        } else {
            regularButtonsShown = false;
            setTimeout(() => {
                clientButtonsShown = true;
            }, 500);
        }
    }

    let currentTime: string;

    function updateTime() {
        const now = new Date();
        const hours = now.getHours();
        const minutes = now.getMinutes();
        currentTime = `${hours.toString().padStart(2, "0")}:${minutes.toString().padStart(2, "0")}`;
    }

    const interval = setInterval(updateTime, 10);

    onDestroy(() => {
        clearInterval(interval);
    });

    updateTime();
</script>

<!-- 背景图片（无模糊遮罩） -->
<div class="bg-image"></div>

<Menu>
    <div class="content">
        <div class="clock" transition:fly|global={{ duration: 500, y: -50 }}>
            {currentTime}
        </div>

        <div class="main-buttons">
            {#if regularButtonsShown}
                <MainButton
                    title="Singleplayer"
                    icon="singleplayer"
                    index={0}
                    on:click={() => openScreen("singleplayer")}
                />
                <MainButton
                    title="Multiplayer"
                    icon="multiplayer"
                    index={1}
                    on:click={() => openScreen("multiplayer")}
                />
                <MainButton
                    title="Other"
                    icon="other"
                    index={2}
                    on:click={toggleButtons}
                />
            {:else if clientButtonsShown}
                <MainButton
                    title="Exit"
                    icon="shutdown"
                    index={0}
                    on:click={exitClient}
                />
                <MainButton
                    title="Toggle Shader"
                    icon="pen-2"
                    index={1}
                    on:click={toggleBackgroundShaderEnabled}
                />
                <MainButton
                    title="Proxies"
                    icon="proxymanager"
                    index={2}
                    on:click={() => openScreen("proxymanager")}
                />
                <MainButton
                    title="ClickGUI"
                    icon="clickgui"
                    index={3}
                    on:click={() => openScreen("clickgui")}
                />
                <MainButton
                    title="Options"
                    icon="options"
                    index={4}
                    on:click={() => openScreen("options")}
                />
                <MainButton
                    title="Back"
                    icon="back-large"
                    index={5}
                    on:click={toggleButtons}
                />
            {/if}
        </div>

        <div class="social-buttons" transition:fly|global={{ duration: 700, y: 100 }}>
            <ButtonContainer>
                <IconButton title="GitHub" icon="github" on:click={() => browse("MAINTAINER_GITHUB")} />
                <IconTextButton
                    title="Get the Latest Version"
                    icon="icon-liquidbounce.net.svg"
                    on:click={() => browse("CLIENT_WEBSITE")}
                />
            </ButtonContainer>
        </div>
    </div>
</Menu>

<style lang="scss">
    $primary-shadow: 0 0 30px rgba(0, 0, 0, 0.5);

    .clock {
        color: white;
        opacity: 0.8;
        font-size: 250px;
        font-weight: 800;
        position: fixed;
        left: 50%;
        transform: translateX(-50%);
        z-index: 1;
        text-shadow: $primary-shadow;
    }

    .content {
        flex: 1;
        display: grid;
        grid-template-areas:
            "a ."
            "b c";
        grid-template-rows: 1fr max-content;
        grid-template-columns: 1fr max-content;
        position: relative;
    }

    .main-buttons {
        display: flex;
        flex-direction: row;
        column-gap: 30px;
        grid-area: a;
        position: absolute;
        bottom: 35px;
        left: 50%;
        transform: translateX(-50%);
    }

    .social-buttons {
        position: absolute;
        bottom: 30px;
        right: 30px;
        z-index: 2;
    }

    .bg-image {
        position: fixed;
        inset: 0;
        background-image: url('/backgrounds/background.png');
        background-size: cover;
        background-position: center;
        background-repeat: no-repeat;
        z-index: -2;
        will-change: transform;
        transform: translateZ(0);
    }

    /* 移除了 .bg-overlay，所以背景清晰无模糊 */

    /* 覆盖 IconTextButton 样式：保留半透明白色，无滑动 */
    :global(.icon-text-button) {
        background-color: rgba(255, 255, 255, 0.35) !important;
        background-image: none !important;
        background-size: auto !important;
        background-position: 0% 0% !important;
        transition: background-position 0s !important;
        backdrop-filter: none !important;
    }
    :global(.icon-text-button:hover) {
        background-color: rgba(255, 255, 255, 0.45) !important;
        background-position: 0% 0% !important;
    }
</style>
