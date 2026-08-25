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

<DirectLoginModal bind:visible={directLoginModalVisible}/>
<AddAccountModal bind:visible={addAccountModalVisible}/>
<Menu>
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
