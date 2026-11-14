
<script lang="ts">
    import {
        deleteScreen,
        getAccounts,
        loginToAccount as loginToAccountRest,
        orderAccounts,
        removeAccount as restRemoveAccount,
        restoreSession,
        setAccountFavorite
    } from "../../../integration/rest.js";
    import SwitchSetting from "../common/setting/SwitchSetting.svelte";
    import OptionBar from "../common/optionbar/OptionBar.svelte";
    import MenuListItem from "../common/menulist/MenuListItem.svelte";
    import Menu from "../common/Menu.svelte";
    import MenuListItemTag from "../common/menulist/MenuListItemTag.svelte";
    import MenuList from "../common/menulist/MenuList.svelte";
    import Search from "../common/Search.svelte";
    import MenuListItemButton from "../common/menulist/MenuListItemButton.svelte";
    import type {Account} from "../../../integration/types";
    import {onMount} from "svelte";
    import MultiSelect from "../common/setting/select/MultiSelect.svelte";
    import AddAccountModal from "./addaccount/AddAccountModal.svelte";
    import {listen} from "../../../integration/ws";
    import {notification} from "../common/header/notification_store";
    import type {
        AccountManagerAdditionEvent,
        AccountManagerLoginEvent,
    } from "../../../integration/events.js";
    import DirectLoginModal from "./directLogin/DirectLoginModal.svelte";

    let premiumOnly = false;
    let favoritesOnly = false;
    let accountTypes = ["Mojang", "TheAltening"];
    let accounts: Account[] = [];
    let renderedAccounts: Account[] = [];
    let searchQuery = "";

    let addAccountModalVisible = false;
    let directLoginModalVisible = false;

    $: {
        let filteredAccounts = accounts;
        if (premiumOnly) {
            filteredAccounts = filteredAccounts.filter(a => a.type !== "Cracked");
        }
        if (favoritesOnly) {
            filteredAccounts = filteredAccounts.filter(a => a.favorite);
        }
        if (!accountTypes.includes("Mojang")) {
            filteredAccounts = filteredAccounts.filter(a => a.type !== "Cracked" && a.type !== "Microsoft")
        }
        if (!accountTypes.includes("TheAltening")) {
            filteredAccounts = filteredAccounts.filter(a => a.type !== "TheAltening")
        }
        if (searchQuery) {
            filteredAccounts = filteredAccounts.filter(a => a.username.toLowerCase().includes(searchQuery.toLowerCase()));
        }
        renderedAccounts = filteredAccounts;
    }

    async function refreshAccounts() {
        accounts = await getAccounts();
    }

    onMount(async () => {
        await refreshAccounts();
        renderedAccounts = accounts;
    });

    function handleSearch(e: CustomEvent<{ query: string }>) {
        searchQuery = e.detail.query;
    }

    async function handleAccountSort(e: CustomEvent<{ newOrder: number[] }>) {
        await orderAccounts(e.detail.newOrder);
        await refreshAccounts();
        renderedAccounts = accounts;
    }

    async function removeAccount(id: number) {
        await restRemoveAccount(id);
        await refreshAccounts();
    }

    async function loginToRandomAccount() {
        const account = renderedAccounts[Math.floor(Math.random() * renderedAccounts.length)];
        if (account) {
            await loginToAccount(account.id);
        }
    }

    async function toggleFavorite(index: number, favorite: boolean) {
        await setAccountFavorite(index, favorite);
        await refreshAccounts();
    }

    async function loginToAccount(id: number) {
        notification.set({
            title: "AltManager",
            message: "Logging in...",
            error: false
        });
        await loginToAccountRest(id);
    }

    listen("accountManagerAddition", (e: AccountManagerAdditionEvent) => {
        addAccountModalVisible = false;
        refreshAccounts();
    });

    listen("accountManagerLogin", (e: AccountManagerLoginEvent) => {
        directLoginModalVisible = false;
    });
</script>

<!-- 必须在每个页面独立声明，否则切换页面后滤镜失效 -->
<svg width="0" height="0" aria-hidden="true" style="position: absolute;">
  <filter id="liquid-lens">
    <feTurbulence type="fractalNoise" baseFrequency="0.01" numOctaves="2" result="noise" />
    <feDisplacementMap in="SourceGraphic" in2="noise" scale="40" xChannelSelector="R" yChannelSelector="G" />
  </filter>
</svg>

<DirectLoginModal bind:visible={directLoginModalVisible}/>
<AddAccountModal bind:visible={addAccountModalVisible}/>
<Menu>
    <!-- 新增包裹容器，与单人界面对齐 -->
    <div class="compact-layout">
        <OptionBar>
            <Search on:search={handleSearch}/>
            <SwitchSetting title="Premium Only" bind:value={premiumOnly}/>
            <SwitchSetting title="Favorites Only" bind:value={favoritesOnly}/>
            <MultiSelect title="Account Type" options={["Mojang", "TheAltening"]} bind:values={accountTypes}/>
        </OptionBar>

        <MenuList sortable={accounts.length === renderedAccounts.length} elementCount={accounts.length}
                  on:sort={handleAccountSort}>
            {#key accounts}
                {#each renderedAccounts as account}
                    <MenuListItem
                            image={account.avatar}
                            title={account.username}
                            favorite={account.favorite}
                            on:dblclick={() => loginToAccount(account.id)}>
                        <svelte:fragment slot="subtitle">
                            <pre class="uuid">{account.uuid}</pre>
                        </svelte:fragment>

                        <svelte:fragment slot="tag">
                            <MenuListItemTag text={account.type}/>
                        </svelte:fragment>

                        <svelte:fragment slot="active-visible">
                            <MenuListItemButton title="Delete" icon="trash" on:click={() => removeAccount(account.id)}/>
                            <MenuListItemButton title="Favorite" icon={account.favorite ? "favorite-filled" : "favorite" }
                                                on:click={() => toggleFavorite(account.id, !account.favorite)}/>
                        </svelte:fragment>

                        <svelte:fragment slot="always-visible">
                            <MenuListItemButton title="Login" icon="play" on:click={() => loginToAccount(account.id)}/>
                        </svelte:fragment>
                    </MenuListItem>
                {/each}
            {/key}
        </MenuList>
    </div>

    <div class="bottom-buttons">
        <button class="circle-button" on:click={() => addAccountModalVisible = true}>
            <svg class="icon-img" viewBox="0 0 24 24" fill="currentColor"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6z"/></svg>
            <span class="label">ADD</span>
        </button>

        <button class="circle-button" on:click={() => directLoginModalVisible = true}>
            <svg class="icon-img" viewBox="0 0 24 24" fill="currentColor"><path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/></svg>
            <span class="label">DIRECT</span>
        </button>

        <button class="circle-button" disabled={renderedAccounts.length === 0} on:click={loginToRandomAccount}>
            <svg class="icon-img" viewBox="0 0 24 24" fill="currentColor"><path d="M10.59 9.17L5.41 4 4 5.41l5.17 5.17 1.42-1.41zM14.5 4l2.04 2.04L4 18.59 5.41 20 17.96 7.46 20 9.5V4h-5.5zm0.33 9.41l-1.41 1.41 3.13 3.13L14.5 20H20v-5.5l-2.04 2.04-3.13-3.13z"/></svg>
            <span class="label">RANDOM</span>
        </button>

        <button class="circle-button" on:click={restoreSession}>
            <svg class="icon-img" viewBox="0 0 24 24" fill="currentColor"><path d="M17.65 6.35A8 8 0 1 0 19.73 14h-2.08A6 6 0 1 1 12 6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"/></svg>
            <span class="label">RESTORE</span>
        </button>

        <button class="circle-button" on:click={() => deleteScreen()}>
            <svg class="icon-img" viewBox="0 0 24 24" fill="currentColor"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/></svg>
            <span class="label">BACK</span>
        </button>
    </div>
</Menu>

<style lang="scss">
    .uuid {
        font-family: monospace;
    }

    .compact-layout {
        max-width: 1600px;
        width: 100%;
        margin: 0 auto;
        display: flex;
        flex-direction: column;
        flex: 1;
        min-height: 0;

        background: rgba(255, 255, 255, 0.03);

        backdrop-filter: blur(8px) saturate(1.8);
        -webkit-backdrop-filter: blur(8px) saturate(1.8);
        backdrop-filter: blur(8px) saturate(1.8) url(#liquid-lens);

        border: 1px solid rgba(255, 255, 255, 0.2);
        border-radius: 20px;
        box-shadow:
            0 16px 40px rgba(0, 0, 0, 0.4),
            -2px 0 8px rgba(255, 100, 100, 0.15),
            2px 0 8px rgba(100, 200, 255, 0.15),
            inset 0 1.5px 2px rgba(255, 255, 255, 0.8),
            inset 0 -2px 4px rgba(255, 255, 255, 0.1),
            inset 0 0 0 1px rgba(0, 0, 0, 0.1);
        padding: 15px;
        margin-top: 10px;
        margin-bottom: 10px;
    }

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
        border-radius: 42% / 42%;
        position: relative;
        overflow: hidden;

        background: rgba(255, 255, 255, 0.08);
        backdrop-filter: blur(8px) saturate(1.8);
        -webkit-backdrop-filter: blur(8px) saturate(1.8);
        backdrop-filter: blur(8px) saturate(1.8) url(#liquid-lens);

        border: 1px solid rgba(255, 255, 255, 0.4);
        box-shadow:
            0 16px 40px rgba(0, 0, 0, 0.5),
            -2px 0 8px rgba(255, 100, 100, 0.2),
            2px 0 8px rgba(100, 200, 255, 0.2),
            inset 0 1.5px 2px rgba(255, 255, 255, 1),
            inset 0 -2px 4px rgba(255, 255, 255, 0.2),
            inset 0 0 0 1px rgba(0, 0, 0, 0.1);

        color: white;
        cursor: pointer;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        transition: all 0.4s cubic-bezier(0.25, 1, 0.5, 1);
        font-family: inherit;
        padding: 0;
        outline: none;
        flex-shrink: 0;

        &:hover:not(:disabled) {
            background: rgba(255, 255, 255, 0.12);
            border-color: rgba(255, 255, 255, 0.6);
            transform: translateY(-3px) scale(1.02);
            box-shadow:
                0 20px 50px rgba(0, 0, 0, 0.6),
                -3px 0 12px rgba(255, 100, 100, 0.3),
                3px 0 12px rgba(100, 200, 255, 0.3),
                inset 0 2px 3px rgba(255, 255, 255, 1),
                inset 0 -2px 4px rgba(255, 255, 255, 0.3),
                inset 0 0 0 1px rgba(0, 0, 0, 0.1);
        }

        &:active:not(:disabled) {
            transform: translateY(0) scale(0.96);
        }

        &:disabled {
            opacity: 0.4;
            cursor: not-allowed;
        }

        .icon-img {
            width: clamp(22px, 4vw, 28px);
            height: clamp(22px, 4vw, 28px);
            filter: drop-shadow(0 2px 6px rgba(0,0,0,0.5));
            display: block;
            position: relative;
            z-index: 2;
        }

        .label {
            font-size: clamp(10px, 1.8vw, 13px);
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 1px;
            margin-top: 2px;
            text-shadow: 0 1px 4px rgba(0,0,0,0.8);
            white-space: nowrap;
            position: relative;
            z-index: 2;
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
