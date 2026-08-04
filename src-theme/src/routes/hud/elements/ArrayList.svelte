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


    // 顶浅蓝
    const startColor = cSettings.startColor ?? { r: 88, g: 204, b: 250 };
    // 终深紫
    const endColor = cSettings.endColor ?? { r: 123, g: 44, b: 191 };

    const tagColor = cSettings.tagColor ?? '#ffffff';


    const enableGradient = cSettings.enableGradient ?? false;

    //字体配置 没用
    const fontMap: Record<string, string> = {
        'Client': 'Inter, system-ui, -apple-system, sans-serif',
        'Minecraft': '"Minecraft", "Minecraft Default", monospace',
        'Harmany': '"Harmany Sans", "Harmany", sans-serif'
    };
    const selectedFont = cSettings.font ?? 'Client';
    const fontSize = cSettings.fontSize ?? 14;
    const fontFamily = fontMap[selectedFont] ?? fontMap['Client'];

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
        {@const currentR = Math.round(startColor.r + (endColor.r - startColor.r) * progress)}
        {@const currentG = Math.round(startColor.g + (endColor.g - startColor.g) * progress)}
        {@const currentB = Math.round(startColor.b + (endColor.b - startColor.b) * progress)}

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
                style="color: rgb({currentR}, {currentG}, {currentB});"
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
    background: rgba(255, 255, 255, 0.25);
    backdrop-filter: blur(16px);
    -webkit-backdrop-filter: blur(16px);
    padding: 2px 4px;
    border-radius: 8px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);
    width: max-content;
    font-weight: 500;
    text-shadow: none;
  }

  .name {
    font-weight: 700;   /* 加粗 */
  }

  .tag {
    margin-left: 4px;
    font-weight: 400;   /* 常规 */
    text-shadow: 0 0 8px rgba(0, 0, 0, 0.4);
  }
</style>
