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

    // 颜色配置
    // 蓝: rgb(88, 204, 250)
    // 粉: rgb(245, 150, 200)
    // 紫: rgb(123, 44, 191)

    const tagColor = cSettings.tagColor ?? 'rgba(255,255,255,0.85)';
    const fontSize = cSettings.fontSize ?? 14;
    const fontFamily = 'Inter, system-ui, -apple-system, sans-serif';

    // 循环周期（毫秒）：一个完整颜色循环的时间
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
        // 列表跨度占相位的 0.66（蓝→粉→紫）
        const phase = baseProgress * 0.66;
        // animation-delay 为负值，表示从动画的该相位立即开始
        return `-${phase * (CYCLE_MS / 1000)}s`;
    }
</script>

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
  }

  .module {
    background: rgba(255, 255, 255, 0.18);
    backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px);
    padding: 2px 4px;
    border-radius: 8px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
    width: max-content;
    font-weight: 500;
    text-shadow: none;
  }

  .name {
    font-weight: 700;
    color: rgb(88, 204, 250); /* fallback / 初始色 */
    animation: colorCycle 6s linear infinite;
    animation-delay: var(--delay, 0s);
  }

  @keyframes colorCycle {
    0%    { color: rgb(88, 204, 250); }   /* 蓝 */
    33.3% { color: rgb(245, 150, 200); }  /* 粉 */
    66.6% { color: rgb(123, 44, 191); }   /* 紫 */
    100%  { color: rgb(88, 204, 250); }   /* 回到蓝，无缝循环 */
  }

  .tag {
    margin-left: 4px;
    font-weight: 400;
    text-shadow: 0 0 8px rgba(0, 0, 0, 0.4);
  }
</style>
