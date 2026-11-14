<!--
    苹果 Liquid Glass 风格 ArrayList。
    每个小条目都是一个独立的液态高光透镜。
-->
<script lang="ts">
    import { onMount, tick } from "svelte";
    import type { Module } from "../../../integration/types";
    import { getModules } from "../../../integration/rest";
    import { listen } from "../../../integration/ws";
    import { getTextWidth } from "../../../integration/text_measurement";
    import { flip } from "svelte/animate";
    import { fly } from "svelte/transition";
    import { convertToSpacedString, spaceSeperatedNames } from "../../../theme/theme_config";

    export let settings: { [name: string]: any };

    const cSettings = settings as HudArrayListSettings;

    const tagColor = cSettings.tagColor ?? 'rgba(255,255,255,0.85)';
    const fontSize = cSettings.fontSize ?? 14;
    const fontFamily = 'Inter, system-ui, -apple-system, sans-serif';

    const CYCLE_MS = 6000;

    let enabledModules: Module[] = [];

    async function updateEnabledModules() {
        const modules = await getModules();
        const visibleModules = modules.filter(m => m.enabled && !m.hidden);

        const modulesWithWidths = visibleModules.map(module => {
            const formattedName = $spaceSeperatedNames ? convertToSpacedString(module.name) : module.name;
            const fullName = (module.tag == null || !cSettings.showTags)
                ? formattedName
                : formattedName + " " + module.tag;

            const fontDesc = `500 ${fontSize}px ${fontFamily}`;
            return {
                ...module,
                width: getTextWidth(fullName, fontDesc)
            };
        });

        modulesWithWidths.sort((a, b) =>
            cSettings.order === "Ascending" ? a.width - b.width : b.width - a.width
        );

        enabledModules = modulesWithWidths;
        await tick();
    }

    spaceSeperatedNames.subscribe(async () => {
        await updateEnabledModules();
    });

    onMount(() => {
        updateEnabledModules();
    });

    listen("moduleToggle", async () => {
        await updateEnabledModules();
    });

    listen("refreshArrayList", async () => {
        await updateEnabledModules();
    });

    function moduleDelay(index: number, total: number): string {
        if (total <= 1) return "0s";
        const baseProgress = index / (total - 1);
        const phase = baseProgress * 0.66;
        return `-${phase * (CYCLE_MS / 1000)}s`;
    }
</script>

<svg width="0" height="0" aria-hidden="true" style="position: absolute;">
  <filter id="liquid-lens">
    <feTurbulence type="fractalNoise" baseFrequency="0.01" numOctaves="2" result="noise" />
    <feDisplacementMap in="SourceGraphic" in2="noise" scale="40" xChannelSelector="R" yChannelSelector="G" />
  </filter>
</svg>

<div class="arraylist">
    {#each enabledModules as { name, tag }, index (name)}
        {@const totalItems = enabledModules.length}
        {@const delay = moduleDelay(index, totalItems)}

        <div
            class="module"
            style="
                font-size: {fontSize}px;
                font-family: {fontFamily};
                {cSettings.itemAlignment === 'Left' ? 'margin-right: auto;' : 'margin-left: auto;'}
                --delay: {delay};
            "
            animate:flip={{ duration: 200 }}
            transition:fly={{ x: 50, duration: 200 }}
        >
            <span class="name">
                {$spaceSeperatedNames ? convertToSpacedString(name) : name}
            </span>

            {#if tag && cSettings.showTags}
                <span class="tag" style="color: {tagColor};"> {tag}</span>
            {/if}
        </div>
    {/each}
</div>

<style lang="scss">
  .arraylist {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 4px;
  }

  .module {
    position: relative;
    background: rgba(255, 255, 255, 0.05);

    backdrop-filter: blur(12px) saturate(1.8);
    -webkit-backdrop-filter: blur(12px) saturate(1.8);
    backdrop-filter: blur(12px) saturate(1.8) url(#liquid-lens);

    padding: 3px 8px;
    border-radius: 8px;
    border: 1px solid rgba(255, 255, 255, 0.25);
    box-shadow:
        0 8px 24px rgba(0, 0, 0, 0.3),
        -1px 0 5px rgba(255, 100, 100, 0.15),
        1px 0 5px rgba(100, 200, 255, 0.15),
        inset 0 1px 1.5px rgba(255, 255, 255, 0.8),
        inset 0 -1px 1px rgba(255, 255, 255, 0.1),
        inset 0 0 0 1px rgba(0, 0, 0, 0.05);

    width: max-content;
    font-weight: 500;
    text-shadow: 0 1px 3px rgba(0, 0, 0, 0.6);
  }

  .name {
    font-weight: 700;
    color: rgb(88, 204, 250);
    animation: colorCycle 6s linear infinite;
    animation-delay: var(--delay, 0s);
    filter: drop-shadow(0 0 2px rgba(255, 255, 255, 0.3));
  }

  @keyframes colorCycle {
    0%    { color: rgb(88, 204, 250); }
    33.3% { color: rgb(245, 150, 200); }
    66.6% { color: rgb(123, 44, 191); }
    100%  { color: rgb(88, 204, 250); }
  }

  .tag {
    margin-left: 4px;
    font-weight: 400;
    text-shadow: 0 1px 3px rgba(0, 0, 0, 0.6);
  }
</style>
