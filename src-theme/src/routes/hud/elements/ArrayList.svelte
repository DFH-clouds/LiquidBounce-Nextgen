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
    const blueColor   = { r: 88,  g: 204, b: 250 };  // 顶部蓝
    const pinkColor   = { r: 245, g: 150, b: 200 };  // 中间粉
    const purpleColor = { r: 123, g: 44,  b: 191 };  // 底部紫

    const tagColor = cSettings.tagColor ?? 'rgba(255,255,255,0.85)';

    // 固定字体，只保留字号设置
    const fontSize = cSettings.fontSize ?? 14;
    const fontFamily = 'Inter, system-ui, -apple-system, sans-serif';

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

    onMount(async () => {
        await updateEnabledModules();
    });

    listen("moduleToggle", async () => {
        await updateEnabledModules();
    });

    listen("refreshArrayList", async () => {
        await updateEnabledModules();
    });
</script>

<div class="arraylist">
    {#each enabledModules as { name, tag }, index (name)}
        {@const totalItems = enabledModules.length}
        {@const progress = totalItems > 1 ? index / (totalItems - 1) : 0}

        <!-- 三色分段渐变 -->
        {@const half = 0.5}
        {@const r = progress <= half
            ? Math.round(blueColor.r + (pinkColor.r - blueColor.r) * (progress / half))
            : Math.round(pinkColor.r + (purpleColor.r - pinkColor.r) * ((progress - half) / half))}
        {@const g = progress <= half
            ? Math.round(blueColor.g + (pinkColor.g - blueColor.g) * (progress / half))
            : Math.round(pinkColor.g + (purpleColor.g - pinkColor.g) * ((progress - half) / half))}
        {@const b = progress <= half
            ? Math.round(blueColor.b + (pinkColor.b - blueColor.b) * (progress / half))
            : Math.round(pinkColor.b + (purpleColor.b - pinkColor.b) * ((progress - half) / half))}

        <div
            class="module"
            style="
                font-size: {fontSize}px;
                font-family: {fontFamily};
                {cSettings.itemAlignment === 'Left' ? 'margin-right: auto;' : 'margin-left: auto;'}
            "
            animate:flip={{ duration: 200 }}
            transition:fly={{ x: 50, duration: 200 }}
        >
            <span
                class="name"
                style="color: rgb({r}, {g}, {b});"
            >
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
  }

  .tag {
    margin-left: 4px;
    font-weight: 400;
    text-shadow: 0 0 8px rgba(0, 0, 0, 0.4);
  }
</style>
