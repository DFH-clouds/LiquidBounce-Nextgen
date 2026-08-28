<script lang="ts">
    import {onMount} from "svelte";
    import {getModules} from "../../../integration/rest";
    import {listen} from "../../../integration/ws";
    import {convertToSpacedString, spaceSeperatedNames} from "../../../theme/theme_config";
    import type {Module} from "../../../integration/types";
    import {UNKNOWN_KEY} from "../../../util/utils";
    import BindDisplay from "../../clickgui/setting/bind/BindDisplay.svelte";

    let modules: Module[] = $state([]);

    async function updateModulesWithBinds() {
        modules = (await getModules()).filter(m => m.keyBind.boundKey !== UNKNOWN_KEY);
    }

    listen("moduleToggle", updateModulesWithBinds);
    listen("valueChanged", async (e) => {
        if (e.value.name === "Bind") {
            await updateModulesWithBinds();
        }
    })

    onMount(async () => {
        await updateModulesWithBinds();
    });
</script>

<div class="keybinds">
    <div class="header">
        <span class="title">Binds</span>
        <img class="icon" src="img/hud/keybinds/icon-keybinds.svg" alt="keybinds">
    </div>
    <div class="entries">
        {#each modules as m (m.name)}
            <div class="row" class:enabled={m.enabled}>
                <span class="module-name">{$spaceSeperatedNames ? convertToSpacedString(m.name) : m.name}</span>
                <span class="key-bind" class:muted={!m.enabled}>
                    [<BindDisplay boundKey={m.keyBind.boundKey} modifiers={m.keyBind.modifiers}/>]
                </span>
            </div>
        {:else}
            <div class="no-binds">No key bindings</div>
        {/each}
    </div>
</div>

<style lang="scss">
    .keybinds {
        width: max-content;
        background: transparent !important;
        backdrop-filter: blur(8px) saturate(1.2) !important;
        -webkit-backdrop-filter: blur(8px) saturate(1.2) !important;
        border: 1px solid rgba(255, 255, 255, 0.08);
        animation: glassPulse 4s ease-in-out infinite;
        box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15) !important;
        border-radius: 8px;
        padding: 10px;
        overflow: hidden;
        font-size: 14px;
        min-width: 150px;
        max-width: 200px;
        transition: box-shadow 0.3s;
    }

    .keybinds:hover {
        box-shadow: 0 6px 24px rgba(0, 0, 0, 0.25) !important;
    }

    @keyframes glassPulse {
        0%, 100% {
            backdrop-filter: blur(8px) saturate(1.2);
            box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
            border-color: rgba(255, 255, 255, 0.08);
        }
        50% {
            backdrop-filter: blur(12px) saturate(1.3);
            box-shadow: 0 6px 24px rgba(0, 0, 0, 0.25);
            border-color: rgba(255, 255, 255, 0.15);
        }
    }

    .header {
        background-color: transparent !important;
        padding: 0 0 8px 0;
        display: flex;
        justify-content: space-between;
        align-items: center;

        .title {
            color: var(--keybinds-text-color);
            font-weight: 600;
        }

        .icon {
            width: 16px;
            height: 16px;
        }
    }

    .entries {
        background-color: transparent !important;
        padding: 0;
        color: var(--keybinds-text-color);

        .no-binds {
            font-style: italic;
            margin-bottom: 5px;
        }
    }

    .row {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 5px;
        gap: 12px;
        min-width: 0;

        &:last-child {
            margin-bottom: 0;
        }

        &.enabled {
            .module-name {
                color: var(--keybinds-enabled-color);
                font-weight: 500;
            }
        }

        .module-name {
            color: var(--keybinds-text-color);
            font-size: 14px;
            flex: 1;
            min-width: 0;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }

        .key-bind {
            display: inline-flex;
            align-items: center;
            font-family: monospace;
            font-size: 11px;
            color: var(--keybinds-accent-color);
            font-weight: 600;
            flex-shrink: 0;
            min-width: max-content;

            &.muted {
                color: var(--keybinds-text-muted-color);
                font-weight: 500;
            }
        }
    }
</style>
