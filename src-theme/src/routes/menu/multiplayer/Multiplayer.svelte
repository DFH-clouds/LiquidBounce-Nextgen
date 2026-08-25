<script lang="ts">
    import OptionBar from "../common/optionbar/OptionBar.svelte";
    import MenuList from "../common/menulist/MenuList.svelte";
    import Menu from "../common/Menu.svelte";
    import Search from "../common/Search.svelte";
    import MenuListItem from "../common/menulist/MenuListItem.svelte";
    import MenuListItemButton from "../common/menulist/MenuListItemButton.svelte";
    import {onMount, onDestroy} from "svelte";
    import {
        browse, connectToServer, getClientInfo, getModule, getProtocols, getSelectedProtocol,
        getServers, getSpooferSettings, openScreen, orderServers,
        removeServer as removeServerRest, setModuleEnabled, setSelectedProtocol, setSpooferSettings
    } from "../../../integration/rest";
    import type {ClientInfo, ConfigurableSetting, Protocol, Server} from "../../../integration/types";
    import {listen} from "../../../integration/ws";
    import TextComponent from "../common/TextComponent.svelte";
    import MenuListItemTag from "../common/menulist/MenuListItemTag.svelte";
    import SingleSelect from "../common/setting/select/SingleSelect.svelte";
    import {REST_BASE} from "../../../integration/host";
    import AddServerModal from "./AddServerModal.svelte";
    import DirectConnectModal from "./DirectConnectModal.svelte";
    import EditServerModal from "./EditServerModal.svelte";
    import type {ServerPingedEvent} from "../../../integration/events";
    import ButtonSetting from "../common/setting/ButtonSetting.svelte";
    import Divider from "../common/optionbar/Divider.svelte";
    import WrappedSetting from "../common/setting/WrappedSetting.svelte";
    import SwitchSetting from "../common/setting/SwitchSetting.svelte";

    let onlineOnly = false;
    let searchQuery = "";
    let addServerModalVisible = false;
    let directConnectModalVisible = false;
    let editServerModalVisible = false;
    let currentEditServer: Server | null = null;

    $: {
        let filteredServers = servers;
        if (onlineOnly) filteredServers = filteredServers.filter(s => s.ping > 0);
        if (searchQuery) filteredServers = filteredServers.filter(s => s.name.toLowerCase().includes(searchQuery.toLowerCase()));
        renderedServers = filteredServers;
    }

    let clientInfo: ClientInfo | null = null;
    let autoConfig = false;
    let spooferConfigurable: ConfigurableSetting | null = null;
    let servers: Server[] = [];
    let renderedServers: Server[] = [];
    let protocols: Protocol[] = [];
    let selectedProtocol: Protocol = { name: "", version: -1 };
    let timesSorted = 0;

    let showBottomButtons = true;
    let isConnecting = false;

    function onWindowFocus() {
        showBottomButtons = true;
        window.removeEventListener('focus', onWindowFocus);
    }

    function handleViaFabricPlus() {
        showBottomButtons = false;
        openScreen("viafabricplus_protocol_selection");
        window.addEventListener('focus', onWindowFocus, { once: true });
    }

    async function handleConnect(address: string) {
        if (isConnecting) return;
        isConnecting = true;

        showBottomButtons = false;
        window.addEventListener('focus', onWindowFocus, { once: true });

        try {
            await connectToServer(address);
        } catch (err) {
            console.error("Connection failed", err);
        } finally {
            isConnecting = false;
        }
    }

    onMount(async () => {
        clientInfo = await getClientInfo();
        spooferConfigurable = await getSpooferSettings();
        autoConfig = (await getModule("AutoConfig")).enabled;
        await refreshServers();
        renderedServers = servers;
        protocols = await getProtocols();
        selectedProtocol = await getSelectedProtocol();
        showBottomButtons = true;
    });

    onDestroy(() => {
        window.removeEventListener('focus', onWindowFocus);
    });

    listen("serverPinged", (pingedEvent: ServerPingedEvent) => {
        const server = pingedEvent.server;
        servers = servers.map((s) => {
            if (s.address === server.address) {
                const clone = structuredClone(server);
                clone.id = s.id; clone.name = s.name; clone.resourcePackPolicy = s.resourcePackPolicy;
                return clone;
            } else return s;
        });
    });

    async function refreshServers() { servers = await getServers(); }
    async function removeServer(index: number) { await removeServerRest(index); await refreshServers(); }

    function getPingColor(ping: number) {
        if (ping < 0) return "#E84C3D";
        if (ping <= 50) return "#2DCC70";
        if (ping <= 100) return "#F1C40F";
        return "#E84C3D";
    }

    async function changeProtocolVersion(e: CustomEvent<{ value: string }>) {
        const p = protocols.find(p => p.name == e.detail.value);
        if (!p) return;
        await setSelectedProtocol(p);
        selectedProtocol = await getSelectedProtocol();
    }

    async function handleServerSort(e: CustomEvent<{ newOrder: number[] }>) {
        await orderServers(e.detail.newOrder);
        await refreshServers(); renderedServers = servers; timesSorted++;
    }

    function handleSearch(e: CustomEvent<{ query: string }>) { searchQuery = e.detail.query; }
    function editServer(server: Server) { currentEditServer = server; editServerModalVisible = true; }

    async function updateSpooferSettings() {
        if (!spooferConfigurable) return;
        await setSpooferSettings(spooferConfigurable);
        spooferConfigurable = await getSpooferSettings();
    }

    async function updateAutoConfigState() { await setModuleEnabled("AutoConfig", autoConfig); }
</script>

<AddServerModal bind:visible={addServerModalVisible} on:serverAdd={refreshServers}/>
{#if currentEditServer}
    <EditServerModal bind:visible={editServerModalVisible} address={currentEditServer.address}
                     name={currentEditServer.name} on:serverEdit={refreshServers} id={currentEditServer.id}
                     resourcePackPolicy={currentEditServer.resourcePackPolicy}/>
{/if}
<DirectConnectModal bind:visible={directConnectModalVisible}/>

<Menu>
    <OptionBar>
        <Search on:search={handleSearch}/>
        <SwitchSetting title="Online only" bind:value={onlineOnly}/>
        <Divider/>
        <SwitchSetting title="Auto Config" bind:value={autoConfig} on:change={updateAutoConfigState}/>
        {#if spooferConfigurable}
            <WrappedSetting bind:value={spooferConfigurable} on:change={updateSpooferSettings} path="multiplayer.spoofer"/>
        {/if}
        {#if clientInfo && clientInfo.viaFabricPlus}
            <SingleSelect title="Version" value={selectedProtocol.name} options={protocols.map(p => p.name)}
                          on:change={changeProtocolVersion}/>
            <ButtonSetting title="ViaFabricPlus" on:click={handleViaFabricPlus}/>
        {:else}
            <ButtonSetting title="Install ViaFabricPlus" on:click={() => browse("VIAFABRICPLUS")}/>
        {/if}
    </OptionBar>

    <MenuList sortable={renderedServers.length === servers.length} elementCount={servers.length}
              on:sort={handleServerSort}>
        {#key timesSorted}
            {#each renderedServers as server}
                <MenuListItem imageText={server.ping > 0 ? `${server.ping}ms` : null}
                              imageTextBackgroundColor={getPingColor(server.ping)}
                              image={server.ping < 0 || !server.icon
                            ? `${REST_BASE}/api/v1/client/resource?id=minecraft:textures/misc/unknown_server.png`
                            :`data:image/png;base64,${server.icon}`}
                              title={server.name}
                              on:dblclick={() => handleConnect(server.address)}>
                    <TextComponent allowPreformatting={true} preFormattingMonospace={false} slot="subtitle"
                                   fontSize={18}
                                   textComponent={server.ping <= 0 ? "§CCan't connect to server" : server.label}/>

                    <svelte:fragment slot="tag">
                        {#if server.ping > 0}
                            <MenuListItemTag text="{server.players.online}/{server.players.max} Players"/>
                            <MenuListItemTag text={server.version}/>
                        {/if}
                    </svelte:fragment>

                    <svelte:fragment slot="active-visible">
                        <MenuListItemButton title="Remove" icon="trash" on:click={() => removeServer(server.id)}/>
                        <MenuListItemButton title="Edit" icon="pen-2" on:click={() => editServer(server)}/>
                    </svelte:fragment>

                    <svelte:fragment slot="always-visible">
                        <MenuListItemButton title="Join" icon="play" on:click={() => handleConnect(server.address)}/>
                    </svelte:fragment>
                </MenuListItem>
            {/each}
        {/key}
    </MenuList>

    {#if showBottomButtons}
        <div class="bottom-buttons">
            <button class="circle-button" on:click={() => addServerModalVisible = true}>
                <svg class="icon-img" viewBox="0 0 24 24" fill="currentColor"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6z"/></svg>
                <span class="label">ADD</span>
            </button>
            <button class="circle-button" on:click={() => directConnectModalVisible = true}>
                <svg class="icon-img" viewBox="0 0 24 24" fill="currentColor"><path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/></svg>
                <span class="label">DIRECT</span>
            </button>
            <button class="circle-button" on:click={refreshServers}>
                <svg class="icon-img" viewBox="0 0 24 24" fill="currentColor"><path d="M17.65 6.35A8 8 0 1 0 19.73 14h-2.08A6 6 0 1 1 12 6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"/></svg>
                <span class="label">REFRESH</span>
            </button>
            <button class="circle-button" on:click={() => openScreen("title")}>
                <svg class="icon-img" viewBox="0 0 24 24" fill="currentColor"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/></svg>
                <span class="label">BACK</span>
            </button>
        </div>
    {/if}
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

    .circle-button .icon-img {
        width: clamp(22px, 4vw, 28px);
        height: clamp(22px, 4vw, 28px);
        filter: drop-shadow(0 2px 4px rgba(0,0,0,0.4));
        display: block;
    }

    .circle-button .label {
        font-size: clamp(10px, 1.8vw, 13px);
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.5px;
        margin-top: 2px;
        text-shadow: 0 2px 10px rgba(0,0,0,0.6);
        white-space: nowrap;
    }

    @media (max-width: 600px) {
        .bottom-buttons { gap: 10px; bottom: 10px; }
        .circle-button { width: 60px; height: 60px; }
        .circle-button .icon-img { width: 20px; height: 20px; }
        .circle-button .label { font-size: 9px; }
    }
</style>
