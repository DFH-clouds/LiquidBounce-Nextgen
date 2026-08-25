<script lang="ts">
    import ButtonSetting from "../common/setting/ButtonSetting.svelte";
    import {
        directLoginToCrackedAccount,
        getAccounts,
        loginToAccount,
        randomUsername,
        reconnectToServer
    } from "../../../integration/rest";
    import {listen} from "../../../integration/ws";
    import {onMount} from "svelte";
    import type {Account} from "../../../integration/types";
    import {restoreSession} from "../../../integration/rest.js";
    import {isLoggingIn} from "../altmanager/altmanager_store";

    const premiumAccounts: Account[] = $state([]);

    async function reconnectWithRandomUsername() {
        const username = await randomUsername();
        await directLoginToCrackedAccount(username, false);
    }

    async function reconnectWithRandomAccount() {
        const account = premiumAccounts[Math.floor(Math.random() * premiumAccounts.length)];
        await loginToAccount(account.id);
    }

    onMount(async () => {
        const accounts = await getAccounts();
        premiumAccounts.push(...accounts.filter(a => a.type !== "Cracked" && !a.favorite));
    });

    listen("accountManagerLogin", reconnectToServer);
</script>

<div class="reconnect">
    <ButtonSetting title="Reconnect" on:click={reconnectToServer}
                   disabled={$isLoggingIn}/>
    <ButtonSetting title="Restore initial session" on:click={restoreSession}
                   disabled={$isLoggingIn}/>
    <ButtonSetting title="Reconnect with random account" on:click={reconnectWithRandomAccount}
                   disabled={premiumAccounts.length === 0 || $isLoggingIn}/>
    <ButtonSetting title="Reconnect with random username" on:click={reconnectWithRandomUsername}
                   disabled={$isLoggingIn}/>
</div>

<style lang="scss">
  .reconnect {
    position: fixed;
    bottom: 20px;
    left: 45px;
    display: flex;
    flex-direction: column;
    row-gap: 10px;
    align-items: flex-start;

    :global(.button-setting) {
      --menu-button-background-color: rgba(255, 255, 255, 0.18);
      --menu-button-hover-background-color: rgba(255, 255, 255, 0.35);
      --menu-button-secondary-background-color: rgba(255, 255, 255, 0.18);
      --menu-button-secondary-hover-background-color: rgba(255, 255, 255, 0.35);

      background-color: var(--menu-button-background-color);
      backdrop-filter: blur(12px);
      -webkit-backdrop-filter: blur(12px);
      border-radius: 8px;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
      padding: 6px 14px;
      font-size: 14px;
      font-weight: 500;
      color: #1a1a1a;
      border: none;

      &:hover:not([disabled]) {
        background-color: var(--menu-button-hover-background-color);
      }

      &[disabled] {
        opacity: 0.4;
      }
    }
  }
</style>
