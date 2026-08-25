<script lang="ts">
    import {
        addProxyFromClipboard,
        checkProxy,
        connectToProxy as connectToProxyRest,
        deleteScreen,
        disconnectFromProxy as disconnectFromProxyRest,
        getCurrentProxy,
        getProxies,
        removeProxy as removeProxyRest,
        setProxyFavorite,
    } from "../../../integration/rest.js";
    import OptionBar from "../common/optionbar/OptionBar.svelte";
    import MenuListItem from "../common/menulist/MenuListItem.svelte";
    import Menu from "../common/Menu.svelte";
    import MenuListItemTag from "../common/menulist/MenuListItemTag.svelte";
    import MenuList from "../common/menulist/MenuList.svelte";
    import Search from "../common/Search.svelte";
    import MenuListItemButton from "../common/menulist/MenuListItemButton.svelte";
    import type {Proxy} from "../../../integration/types";
    import {onMount} from "svelte";
    import AddProxyModal from "./AddProxyModal.svelte";
    import EditProxyModal from "./EditProxyModal.svelte";
    import SwitchSetting from "../common/setting/SwitchSetting.svelte";
    import MultiSelect from "../common/setting/select/MultiSelect.svelte";
    import {notification} from "../common/header/notification_store";
    import lookup from "country-code-lookup";
    import {listen} from "../../../integration/ws";
    import type {ProxyCheckResultEvent} from "../../../integration/events.js";

    $: {
        let filteredProxies = proxies;

        filteredProxies = filteredProxies.filter(p => countries.includes(convertCountryCode(p.ipInfo?.country)));
        filteredProxies = filteredProxies.filter(p => proxyTypes.includes(p.type));
        if (favoritesOnly) {
            filteredProxies = filteredProxies.filter(a => a.favorite);
        }
        if (searchQuery) {
            filteredProxies = filteredProxies.filter(p => p.host.toLowerCase().includes(searchQuery.toLowerCase()));
        }

        renderedProxies = filteredProxies;
    }

    let addProxyModalVisible = false;
    let editProxyModalVisible = false;
    let allCountries: string[] = [];

    let searchQuery = "";
    let favoritesOnly = false;
    let countries: string[] = [];
    let proxyTypes = ["SOCKS5", "HTTP"];

    let proxies: Proxy[] = [];
    let renderedProxies = proxies;
    let isConnectedToProxy = false;

    let currentEditProxy: Proxy | null = null;

    onMount(async () => {
        await refreshProxies();
        renderedProxies = proxies;
        await updateIsConnectedToProxy();
    });

    async function updateIsConnectedToProxy() {
        isConnectedToProxy = await getCurrentProxy() !== null;
    }

    function convertCountryCode(code: string | undefined): string {
        if (code === undefined) {
            return "Unknown";
        }
        return lookup.byIso(code)?.country ?? "Unknown";
    }

    async function refreshProxies() {
        proxies = await getProxies();

        const c = new Set<string>();
        for (const p of proxies) {
            c.add(convertCountryCode(p.ipInfo?.country));
        }
        allCountries = Array.from(c);
        countries = allCountries;
    }

    function handleSearch(e: CustomEvent<{ query: string }>) {
        searchQuery = e.detail.query;
    }

    function handleProxySort() {
        // 留空
    }

    async function removeProxy(id: number) {
        await removeProxyRest(id);
        await refreshProxies();
    }

    async function connectToProxy(id: number) {
        await connectToProxyRest(id);
        notification.set({
            title: "ProxyManager",
            message: "Connected to proxy",
            error: false
        });
        await updateIsConnectedToProxy();
    }

    async function connectToRandomProxy() {
        const proxy = renderedProxies[Math.floor(Math.random() * renderedProxies.length)];
        if (proxy) {
            await connectToProxy(proxy.id);
        }
    }

    async function toggleFavorite(index: number, favorite: boolean) {
        await setProxyFavorite(index, favorite);
        await refreshProxies();
    }

    listen("proxyCheckResult", async (e: ProxyCheckResultEvent) => {
        if (e.error && e.proxy) {
            notification.set({
                title: "ProxyManager",
                message: "The proxy is not working: " + e.error,
                error: true
            });
        } else if (e.error) {
            notification.set({
                title: "ProxyManager",
                message: e.error,
                error: true
            });
        } else if (e.proxy) {
            notification.set({
                title: "ProxyManager",
                message: "Proxy is working",
                error: false
            });

            await refreshProxies();
        }
    });

    async function disconnectFromProxy() {
        await disconnectFromProxyRest();
        await updateIsConnectedToProxy();
        notification.set({
            title: "ProxyManager",
            message: "Disconnected from proxy",
            error: false
        });
    }

    function editProxy(proxy: Proxy) {
        currentEditProxy = proxy;
        editProxyModalVisible = true;
    }

    function fromClipboard() {
        notification.set({
            title: "ProxyManager",
            message: "Checking proxy from clipboard...",
            error: false
        });
        addProxyFromClipboard();
    }
</script>

<AddProxyModal bind:visible={addProxyModalVisible}/>
{#if currentEditProxy}
    <EditProxyModal bind:visible={editProxyModalVisible} id={currentEditProxy.id}
                    host={currentEditProxy.host}
                    port={currentEditProxy.port}
                    proxyType={currentEditProxy.type}
                    forwardAuthentication={currentEditProxy.forwardAuthentication}
                    username={currentEditProxy.credentials?.username ?? ""}
                    password={currentEditProxy.credentials?.password ?? ""}
                    requiresAuthentication={currentEditProxy.credentials !== undefined}/>
{/if}

<Menu>
    <OptionBar>
        <Search on:search={handleSearch}/>
        <SwitchSetting title="Favorites Only" bind:value={favoritesOnly}/>
        <MultiSelect title="Country" options={allCountries} bind:values={countries}/>
        <MultiSelect title="Type" options={["SOCKS5", "HTTP"]} bind:values={proxyTypes}/>
    </OptionBar>

    <MenuList sortable={false} on:sort={handleProxySort}>
        {#each renderedProxies as proxy}
            <MenuListItem
                    image="img/flags/{(proxy.ipInfo?.country ?? 'unknown').toLowerCase()}.svg"
                    title="{proxy.host}:{proxy.port}"
                    favorite={proxy.favorite}
                    on:dblclick={() => connectToProxy(proxy.id)}>
                <svelte:fragment slot="subtitle">
                    <span class="subtitle">{proxy.ipInfo?.org ?? "Unknown"}</span>
                </svelte:fragment>

                <svelte:fragment slot="tag">
                    <MenuListItemTag text={convertCountryCode(proxy.ipInfo?.country)}/>
                    <MenuListItemTag text={proxy.type}/>
                </svelte:fragment>

                <svelte:fragment slot="active-visible">
                    <MenuListItemButton title="Delete" icon="trash" on:click={() => removeProxy(proxy.id)}/>
                    <MenuListItemButton title="Check" icon="check" on:click={() => checkProxy(proxy.id)}/>
                    <MenuListItemButton title="Favorite" icon={proxy.favorite ? "favorite-filled" : "favorite" }
                                        on:click={() => toggleFavorite(proxy.id, !proxy.favorite)}/>
                    <MenuListItemButton title="Edit" icon="pen-2" on:click={() => editProxy(proxy)}/>
                </svelte:fragment>

                <svelte:fragment slot="always-visible">
                    <MenuListItemButton title="Connect" icon="play" on:click={() => connectToProxy(proxy.id)}/>
                </svelte:fragment>
            </MenuListItem>
        {/each}
    </MenuList>

    <div class="bottom-buttons">
        <button class="circle-button" on:click={() => addProxyModalVisible = true}>
            <svg class="icon-img" viewBox="0 0 24 24" fill="currentColor"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6z"/></svg>
            <span class="label">ADD</span>
        </button>

        <button class="circle-button" on:click={fromClipboard}>
            <svg class="icon-img" viewBox="0 0 24 24" fill="currentColor"><path d="M19 3h-4.18C14.4 1.84 13.3 1 12 1s-2.4.84-2.82 2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-7 0c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1zm2 14H7v-2h7v2zm3-4H7v-2h10v2zm0-4H7V7h10v2z"/></svg>
            <span class="label">CLIPBOARD</span>
        </button>

        <button class="circle-button" disabled={renderedProxies.length === 0} on:click={connectToRandomProxy}>
            <svg class="icon-img" viewBox="0 0 24 24" fill="currentColor"><path d="M10.59 9.17L5.41 4 4 5.41l5.17 5.17 1.42-1.41zM14.5 4l2.04 2.04L4 18.59 5.41 20 17.96 7.46 20 9.5V4h-5.5zm0.33 9.41l-1.41 1.41 3.13 3.13L14.5 20H20v-5.5l-2.04 2.04-3.13-3.13z"/></svg>
            <span class="label">RANDOM</span>
        </button>

        <button class="circle-button" disabled={!isConnectedToProxy} on:click={disconnectFromProxy}>
            <svg class="icon-img" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm5 11H7v-2h10v2z"/></svg>
            <span class="label">DISCONNECT</span>
        </button>

        <button class="circle-button" on:click={() => deleteScreen()}>
            <svg class="icon-img" viewBox="0 0 24 24" fill="currentColor"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/></svg>
            <span class="label">BACK</span>
        </button>
    </div>
</Menu>

<style lang="scss">
    .bottom-buttons {
        position: absolute;
        bottom: clamp(15px, 4vh, 35px);
        left: 50%;
        transform: translateX(-50%);
        display: flex;
        gap: clamp(12px, 3vw, 25px);
        flex-wrap: wrap;
        justify-content: center;
        max-width: 90vw;
    }

    .circle-button {
        width: clamp(70px, 11vw, 95px);
        height: clamp(70px, 11vw, 95px);
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
        flex-shrink: 0;

        &:hover:not(:disabled) {
            background: rgba(255, 255, 255, 0.2);
            border-color: rgba(255, 255, 255, 0.4);
            transform: scale(1.06);
            box-shadow: 0 0 25px rgba(255, 255, 255, 0.15);
        }

        &:active:not(:disabled) {
            transform: scale(0.95);
        }

        &:disabled {
            opacity: 0.4;
            cursor: not-allowed;
        }

        .icon-img {
            width: clamp(22px, 4vw, 28px);
            height: clamp(22px, 4vw, 28px);
            filter: drop-shadow(0 2px 4px rgba(0,0,0,0.4));
            display: block;
        }

        .label {
            font-size: clamp(10px, 1.8vw, 13px);
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            margin-top: 2px;
            text-shadow: 0 2px 10px rgba(0,0,0,0.6);
            white-space: nowrap;
        }
    }

    @media (max-width: 600px) {
        .bottom-buttons {
            gap: 10px;
            bottom: 10px;
        }
        .circle-button {
            width: 60px;
            height: 60px;
        }
        .circle-button .icon-img {
            width: 20px;
            height: 20px;
        }
        .circle-button .label {
            font-size: 9px;
        }
    }
</style>
