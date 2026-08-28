<script lang="ts">
    import ClickGui from "./ClickGui.svelte";
    import GlobalSettings from "./tabs/GlobalSettings.svelte";
    import Tabs from "./tabs/Tabs.svelte";
    import { gridSize, os, scaleFactor, showGrid, snappingEnabled } from "./clickgui_store";
    import type { ConfigurableSetting, TogglableSetting } from "../../integration/types";
    import { onMount, onDestroy } from "svelte";
    import { getClientInfo, getGameWindow, getModuleSettings, setTyping } from "../../integration/rest";
    import { listen } from "../../integration/ws";
    import type { ClickGuiValueChangeEvent, ScaleFactorChangeEvent } from "../../integration/events";

    const tabs = [
        { title: "ClickGUI", content: ClickGui },
        { title: "Settings", content: GlobalSettings }
    ];

    let activeTab = $state(0);
    let minecraftScaleFactor = $state(2);
    let clickGuiScaleFactor = $state(1);
    let rafId: number | null = null;

    // 使用 requestAnimationFrame 节流样式更新
    $effect(() => {
        $scaleFactor = minecraftScaleFactor * clickGuiScaleFactor;
        if (rafId !== null) {
            cancelAnimationFrame(rafId);
        }
        rafId = requestAnimationFrame(() => {
            // 样式绑定会自动更新
            rafId = null;
        });
    });

    function applyValues(configurable: ConfigurableSetting) {
        const scaleValue = configurable.value.find(v => v.name === "Scale");
        const snappingValue = configurable.value.find(v => v.name === "Snapping") as TogglableSetting | undefined;

        if (scaleValue) {
            clickGuiScaleFactor = scaleValue.value as number;
        }

        if (snappingValue) {
            $snappingEnabled = snappingValue.value.find(v => v.name === "Enabled")?.value as boolean ?? true;
            $gridSize = snappingValue.value.find(v => v.name === "GridSize")?.value as number ?? 10;
        }
    }

    onMount(async () => {
        $os = (await getClientInfo()).os;
        const gameWindow = await getGameWindow();
        minecraftScaleFactor = gameWindow.scaleFactor;
        const clickGuiSettings = await getModuleSettings("ClickGUI");
        applyValues(clickGuiSettings);
        await setTyping(false);
    });

    listen("scaleFactorChange", (e: ScaleFactorChangeEvent) => {
        minecraftScaleFactor = e.scaleFactor;
    });

    listen("clickGuiValueChange", (e: ClickGuiValueChangeEvent) => {
        applyValues(e.configurable);
    });

    onDestroy(() => {
        if (rafId !== null) cancelAnimationFrame(rafId);
    });
</script>

<!--
  移除网格背景：删除了 class:grid 绑定和 background-size 样式
  背景色改为透明（可自行替换为纯色）
-->
<div
    class="tabbed-clickgui"
    style="
        transform: scale({$scaleFactor * 50}%);
        width: {2 / $scaleFactor * 100}vw;
        height: {2 / $scaleFactor * 100}vh;
    "
>
    <Tabs {tabs} bind:activeTab />
</div>

<style lang="scss">
    .tabbed-clickgui {
        /* 背景改为透明，完全移除背景绘制 */
        background-color: transparent;
        overflow: hidden;
        position: absolute;
        will-change: opacity;
        transform-origin: top left;
        left: 0;
        top: 0;
        /* 限制重排范围并启用 GPU 加速 */
        contain: layout style paint;
        transform: translateZ(0);
        backface-visibility: hidden;
    }
</style>
