<script lang="ts">
    import {
        editWorld,
        getWorlds,
        openScreen,
        openWorld,
        removeWorld as removeWorldRest
    } from "../../../integration/rest.js";
    import OptionBar from "../common/optionbar/OptionBar.svelte";
    import MenuListItem from "../common/menulist/MenuListItem.svelte";
    import Menu from "../common/Menu.svelte";
    import MenuListItemTag from "../common/menulist/MenuListItemTag.svelte";
    import MenuList from "../common/menulist/MenuList.svelte";
    import Search from "../common/Search.svelte";
    import MenuListItemButton from "../common/menulist/MenuListItemButton.svelte";
    import type {World} from "../../../integration/types";
    import {onMount} from "svelte";
    import MultiSelect from "../common/setting/select/MultiSelect.svelte";
    import {REST_BASE} from "../../../integration/host";
    import dateFormat from "dateformat";

    let gameModes = ["Survival", "Creative", "Adventure", "Spectator"];
    let difficulties = ["Peaceful", "Easy", "Normal", "Hard"];
    let searchQuery = "";

    let worlds: World[] = [];
    let renderedWorlds: World[] = [];

    $: {
        let filteredWorlds = worlds;
        filteredWorlds = filteredWorlds.filter(w => gameModes.includes(capitalize(w.gameMode)));
        filteredWorlds = filteredWorlds.filter(w => difficulties.includes(capitalize(w.difficulty)));
        if (searchQuery) {
            filteredWorlds = filteredWorlds.filter(w => w.displayName.toLowerCase().includes(searchQuery.toLowerCase()));
        }
        renderedWorlds = filteredWorlds;
    }

    function capitalize(s: string) {
        return s[0].toUpperCase() + s.slice(1);
    }

    async function refreshWorlds() {
        worlds = await getWorlds();
    }

    onMount(() => {
        refreshWorlds().catch(console.error);
    });

    function handleSearch(e: CustomEvent<{ query: string }>) {
        searchQuery = e.detail.query;
    }

    function handleWorldSort() {
    }

    async function removeWorld(name: string) {
        await removeWorldRest(name);
        await refreshWorlds();
    }
</script>


<Menu>
    <div class="compact-layout">
        <OptionBar>
            <Search on:search={handleSearch}/>
            <MultiSelect title="Game Mode" options={["Survival", "Creative", "Adventure", "Spectator"]}
                        bind:values={gameModes}/>
            <MultiSelect title="Difficulty" options={["Peaceful", "Easy", "Normal", "Hard"]} bind:values={difficulties}/>
        </OptionBar>

        <MenuList sortable={false} on:sort={handleWorldSort}>
            {#each renderedWorlds as world}
                <MenuListItem
                        image={!world.icon ?
                            `${REST_BASE}/api/v1/client/resource?id=minecraft:textures/misc/unknown_server.png` :
                            `data:image/png;base64,${world.icon}`}
                        title={world.displayName}
                        on:dblclick={() => openWorld(world.name)}>
                    <svelte:fragment slot="subtitle">
                        <span class="world-name">{world.name}</span>
                        <span>({dateFormat(new Date(world.lastPlayed), "yyyy/mm/dd h:MM:ss TT")})</span>
                    </svelte:fragment>

                    <svelte:fragment slot="tag">
                        <MenuListItemTag text={capitalize(world.gameMode)}/>
                        <MenuListItemTag text={capitalize(world.difficulty)}/>
                        <MenuListItemTag text="Minecraft {world.version}"/>
                    </svelte:fragment>

                    <svelte:fragment slot="active-visible">
                        <MenuListItemButton title="Delete" icon="trash" on:click={() => removeWorld(world.name)}/>
                        <MenuListItemButton title="Edit" icon="pen-2" on:click={() => editWorld(world.name)}/>
                    </svelte:fragment>

                    <svelte:fragment slot="always-visible">
                        <MenuListItemButton title="Open" icon="play" on:click={() => openWorld(world.name)}/>
                    </svelte:fragment>
                </MenuListItem>
            {/each}
        </MenuList>
    </div>

    <div class="bottom-buttons">
        <button class="circle-button" on:click={() => openScreen("create_world")}>
            <span class="icon">＋</span>
            <span class="label">Add</span>
        </button>
        <button class="circle-button" on:click={() => openScreen("title")}>
            <span class="icon">⬅</span>
            <span class="label">Back</span>
        </button>
    </div>
</Menu>

<style lang="scss">
    .world-name {
        font-weight: 500;
    }

    .compact-layout {
        max-width: 1600px;
        width: 100%;
        margin: 0 auto;
        display: flex;
        flex-direction: column;
        flex: 1;
        min-height: 0;
    }

    .bottom-buttons {
        position: absolute;
        bottom: 35px;
        left: 50%;
        transform: translateX(-50%);
        display: flex;
        gap: 30px;
        z-index: 2;
    }

    .circle-button {
        width: 100px;
        height: 100px;
        border-radius: 50%;
        background: rgba(255, 255, 255, 0.08);
        border: 2px solid rgba(255, 255, 255, 0.15);
        color: white;
        cursor: pointer;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        transition: all 0.25s ease;
        font-family: inherit;
        padding: 0;
        outline: none;
        backdrop-filter: blur(2px);
    }

    .circle-button:hover {
        background: rgba(255, 255, 255, 0.2);
        border-color: rgba(255, 255, 255, 0.4);
        transform: scale(1.06);
        box-shadow: 0 0 25px rgba(255, 255, 255, 0.15);
    }

    .circle-button:active {
        transform: scale(0.95);
    }

    .circle-button .icon {
        font-size: 28px;
        line-height: 1;
        filter: drop-shadow(0 2px 4px rgba(0,0,0,0.4));
    }

    .circle-button .label {
        font-size: 13px;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.5px;
        margin-top: 4px;
        text-shadow: 0 2px 10px rgba(0,0,0,0.6);
    }
</style>
