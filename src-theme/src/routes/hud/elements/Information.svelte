<script lang="ts">
 // 原代码来自 https://github.com/snwmdd114514-lang/BeautifyBouncePlus/tree/main
 // 本代码移除了音乐播放相关代码 并修复了在scaffold状态下 方块过多 block文本显示异常的bug
 // 额外修改：移除坐标和服务器延迟显示，添加时间显示，修复图标，紧凑布局，宽度自适应
    import { cubicOut, quintOut } from "svelte/easing";
    import { fade, fly, slide } from "svelte/transition";
    import { onDestroy, onMount } from "svelte";
    import type { Unsubscriber } from "svelte/store";
    import { listen } from "../../../integration/ws";
    import type {
        ClientPlayerDataEvent,
        KeyEvent,
        KeyboardKeyEvent,
        ModuleToggleEvent,
        NotificationEvent,
        PlayerListEntry,
        PlayerListEvent,
        ServerPingedEvent
    } from "../../../integration/events";
    import {
        getClientInfo,
        getMinecraftKeybinds,
        getModules,
        getPlayerData,
        getRememberedConnectedServerAddress,
        getServers,
        getSession,
        itemTextureUrl,
        setTyping
    } from "../../../integration/rest";
    import type {
        ClientInfo,
        Module,
        PlayerData,
        Server,
        Session,
        TextComponent as TTextComponent
    } from "../../../integration/types";
    import TextComponent from "../../menu/common/TextComponent.svelte";

    export let settings: { [name: string]: any } = {};
    export let editMode = false;

    type IslandPlayer = {
        id: string;
        displayName: TTextComponent | string;
        ping: number;
        self?: boolean;
    };

    type IslandNotification = {
        id: number;
        title: string;
        message: string;
        severity: NotificationEvent["severity"];
        icon: string;
    };

    const NOTIFICATION_TTL = 3200;
    const MAX_NOTIFICATIONS = 6;
    const MAX_EXPANDED_NOTIFICATIONS = 3;
    const MAX_DISPLAY_BLOCKS = 64;
    const MAX_ISLAND_WIDTH_SETTING = 200;
    const SERVER_SNAPSHOT_INTERVAL_MS = 5000;
    const LIVE_STATE_INTERVAL_MS = 1000;
    const MODULE_SNAPSHOT_INTERVAL_MS = 5000;
    const SESSION_SNAPSHOT_INTERVAL_MS = 10000;
    const PLAYER_LIST_SUPPRESS_INTERVAL_MS = 500;
    const DEFAULT_PLAYER_LIST_KEY = "key.keyboard.tab";
    const PING_BAR_LEVELS = [0, 1, 2, 3, 4];
    const PLAYER_LIST_BIND_NAMES = new Set([
        "key.playerlist",
        "key.player_list",
        "key.list_players",
        "key.listplayers"
    ]);
    const NON_BLOCK_KEYWORDS = [
        "sword", "pickaxe", "axe", "shovel", "hoe", "helmet", "chestplate",
        "leggings", "boots", "potion", "apple", "beef", "porkchop", "chicken",
        "bow", "arrow", "trident", "crossbow", "shield", "totem", "bucket",
        "ender_pearl", "snowball", "egg", "compass", "clock", "fishing_rod",
        "ingot", "nugget", "gem", "diamond", "emerald", "coal", "lapis",
        "redstone", "book", "paper", "map", "music_disc"
    ];

    let clientInfo: ClientInfo | null = null;
    let session: Session | null = null;
    let playerData: PlayerData | null = null;
    let modules: Module[] = [];
    let playerListOpen = false;
    let playerListEntries: PlayerListEntry[] = [];
    let playerListHeader: TTextComponent | string | null = null;
    let playerListFooter: TTextComponent | string | null = null;
    let islandNotifications: IslandNotification[] = [];
    let notificationTimers = new Map<number, ReturnType<typeof setTimeout>>();
    let notificationSequence = 0;
    let lastNotificationSignature = "";
    let lastNotificationTime = 0;
    let updateInterval: ReturnType<typeof setInterval> | null = null;
    let moduleSnapshotInterval: ReturnType<typeof setInterval> | null = null;
    let sessionSnapshotInterval: ReturnType<typeof setInterval> | null = null;
    let serverSnapshotInterval: ReturnType<typeof setInterval> | null = null;
    let playerListKey = DEFAULT_PLAYER_LIST_KEY;
    let playerListSuppressInterval: ReturnType<typeof setInterval> | null = null;
    let serverSnapshotBusy = false;
    let lastKnownServerAddress = "";
    let lastKnownServerName = "";
    let lastKnownServerPing = 0;
    let lastServerPlayerListEntries: PlayerListEntry[] = [];
    let clientInfoPlayerEntries: PlayerListEntry[] = [];
    let playerDataPlayerEntries: PlayerListEntry[] = [];
    let moduleSignature = "";
    let moduleIconByName = new Map<string, string>();
    let scaffoldActive = false;
    let scaffoldCount = 0;
    let scaffoldBlockId = "minecraft:oak_planks";
    let scaffoldBlockColor = "var(--accent-color)";
    let lastSampledScaffoldBlockId: string | null = null;
    const scaffoldColorCache = new Map<string, string>();
    let scaffoldTimeout: ReturnType<typeof setTimeout> | null = null;

    let showUsername = true;
    let protectedName = "Protected";

    // 时间显示
    let currentTime = "";
    let timeInterval: ReturnType<typeof setInterval> | null = null;

    function updateTime() {
        const now = new Date();
        currentTime = now.toLocaleTimeString("en-US", {
            hour: "2-digit",
            minute: "2-digit",
            second: "2-digit"
        });
    }

    function settingValue<T>(names: string[], fallback: T): T {
        for (const name of names) {
            const value = settings?.[name];
            if (value !== undefined) return value as T;
        }

        return fallback;
    }

    $: show = (settings?.show as string[] | undefined) ?? ["Username", "FPS", "Coords"];
    $: playerListEnabled = settingValue<boolean>(["player list", "playerList", "Player List"], true);
    $: islandNotificationsEnabled = settingValue<boolean>(
        ["Notifications Settings", "notifications Settings", "notificationsSettings"],
        true
    );
    $: scaffoldIslandEnabled = settingValue<boolean>(["scaffold", "Scaffold"], true);
    $: rawIslandWidthSetting = Number(settingValue<number>(["Width", "width", "Island Width"], 50));
    $: islandWidthSetting = Math.max(0, Math.min(MAX_ISLAND_WIDTH_SETTING, Number.isFinite(rawIslandWidthSetting) ? rawIslandWidthSetting : 50));
    $: islandWidthOffset = (islandWidthSetting - 50) * 3;
    $: showNick = show.includes("Username");
    $: showFps = show.includes("FPS");
    $: if (!playerListEnabled && playerListOpen) setPlayerListOpen(false);
    $: if (!islandNotificationsEnabled && islandNotifications.length > 0) clearIslandNotifications();
    $: if (!scaffoldIslandEnabled && scaffoldActive) scaffoldActive = false;

    const demoPlayerEntries: IslandPlayer[] = [
        {
            id: "demo-1",
            displayName: {
                text: "",
                color: "white",
                extra: [
                    { text: "Hikari", color: "aqua", bold: true },
                    { text: "Mo", color: "light_purple", bold: true },
                    { text: " Error", color: "white" }
                ]
            },
            ping: 42
        },
        { id: "demo-2", displayName: "§b花辞树 §7Ccvc", ping: 58 },
        { id: "demo-3", displayName: "§fLiaoX1", ping: 74 },
        { id: "demo-4", displayName: "§fdiamao", ping: 103 },
        { id: "demo-5", displayName: "§7snmudd", ping: 188 }
    ];

    const demoIslandNotifications: IslandNotification[] = [
        {
            id: 1,
            title: "KillAura",
            message: "Enabled",
            severity: "ENABLED",
            icon: "img/clickgui/icon-combat.svg"
        },
        {
            id: 2,
            title: "Fly",
            message: "Disabled",
            severity: "DISABLED",
            icon: "img/clickgui/icon-movement.svg"
        }
    ];

    $: compactIslandWidth = showNick && showFps
        ? 330
        : showNick
            ? 230
            : 190;

    $: renderedNotifications = editMode && islandNotificationsEnabled ? demoIslandNotifications : islandNotifications;
    $: notificationStack = [...renderedNotifications].reverse();
    $: showNotificationPanel = islandNotificationsEnabled && !showScaffoldPanel && renderedNotifications.length > 0;
    $: islandBaseWidth = showPlayerPanel
        ? 408
        : showScaffoldPanel
            ? 310
            : showNotificationPanel
                ? 366
                : compactIslandWidth;
    $: islandTargetWidth = Math.max(190, islandBaseWidth + islandWidthOffset);

    onMount(async () => {
        await refreshClientState();
        await Promise.all([updatePlayerListKeybind(), updateServerSnapshot()]);
        updateInterval = setInterval(refreshLiveClientState, LIVE_STATE_INTERVAL_MS);
        moduleSnapshotInterval = setInterval(updateModules, MODULE_SNAPSHOT_INTERVAL_MS);
        sessionSnapshotInterval = setInterval(updateSession, SESSION_SNAPSHOT_INTERVAL_MS);
        serverSnapshotInterval = setInterval(updateServerSnapshot, SERVER_SNAPSHOT_INTERVAL_MS);

        updateTime();
        timeInterval = setInterval(updateTime, 1000);
    });

    onDestroy(() => {
        if (updateInterval) clearInterval(updateInterval);
        if (moduleSnapshotInterval) clearInterval(moduleSnapshotInterval);
        if (sessionSnapshotInterval) clearInterval(sessionSnapshotInterval);
        if (serverSnapshotInterval) clearInterval(serverSnapshotInterval);
        if (timeInterval) clearInterval(timeInterval);
        setVanillaPlayerListSuppressed(false);
        clearIslandNotifications();
    });

    async function refreshClientState() {
        await Promise.all([
            updateClientInfo(),
            updateSession(),
            updatePlayerData(),
            updateModules()
        ]);
    }

    async function refreshLiveClientState() {
        await Promise.all([
            updateClientInfo(),
            updatePlayerData()
        ]);
    }

    async function updateClientInfo() {
        const nextClientInfo = await getClientInfo();
        clientInfo = {
            ...nextClientInfo,
            currentServer: nextClientInfo.currentServer ?? (nextClientInfo.inGame ? clientInfo?.currentServer : undefined)
        };
        clientInfoPlayerEntries = extractPlayerListEntries(clientInfo);
    }

    async function updateSession() {
        session = await getSession();
    }

    async function updatePlayerData() {
        applyPlayerData(await getPlayerData());
    }

    function applyPlayerData(nextPlayerData: PlayerData | null) {
        playerData = nextPlayerData;
        playerDataPlayerEntries = extractPlayerListEntries(nextPlayerData);
    }

    async function updateModules() {
        const nextModules = await getModules();
        const nextSignature = nextModules
            .map(module => `${module.name}:${module.category}:${module.enabled ? 1 : 0}:${module.hidden ? 1 : 0}`)
            .join("|");

        if (nextSignature !== moduleSignature) {
            moduleSignature = nextSignature;
            modules = nextModules;
            moduleIconByName = new Map(nextModules.map(module => [
                module.name,
                `img/clickgui/icon-${module.category.toLowerCase()}.svg`
            ]));
        }

        const np = nextModules.find(m => m.name === "NameProtect");
        showUsername = !(np && np.enabled);
    }

    function setLocalModuleEnabled(moduleName: string, enabled: boolean) {
        let changed = false;
        const lowerName = moduleName.toLowerCase();
        const nextModules = modules.map(module => {
            if (module.name.toLowerCase() !== lowerName || module.enabled === enabled) return module;
            changed = true;
            return { ...module, enabled };
        });

        if (!changed) return;
        modules = nextModules;
        moduleSignature = nextModules
            .map(module => `${module.name}:${module.category}:${module.enabled ? 1 : 0}:${module.hidden ? 1 : 0}`)
            .join("|");
    }

    async function updatePlayerListKeybind() {
        try {
            const keybinds = await getMinecraftKeybinds();
            const bind = keybinds.find(keybind => {
                const bindName = keybind.bindName.toLowerCase();
                return PLAYER_LIST_BIND_NAMES.has(bindName) || bindName.includes("playerlist");
            });

            playerListKey = bind?.key.translationKey ?? DEFAULT_PLAYER_LIST_KEY;
        } catch {
            playerListKey = DEFAULT_PLAYER_LIST_KEY;
        }
    }

    async function updateServerSnapshot() {
        if (serverSnapshotBusy) return;
        if (!clientInfo?.inGame) return;

        const knownAddresses = knownServerAddresses();
        if (knownAddresses.length === 0) return;

        serverSnapshotBusy = true;
        try {
            const servers = await getServers();
            const matchedServer = findMatchingServer(servers, knownAddresses);
            if (matchedServer) applyServerSnapshot(matchedServer);
        } catch {
        } finally {
            serverSnapshotBusy = false;
        }
    }

    function normalizeServerAddress(value: string | undefined | null) {
        return value?.trim().toLowerCase().replace(/\.$/, "") ?? "";
    }

    function isPlaceholderServerAddress(value: string | undefined | null) {
        const normalized = normalizeServerAddress(value);
        return !normalized || normalized === "server" || normalized === "singleplayer";
    }

    function knownServerAddresses() {
        return [
            lastKnownServerAddress,
            getRememberedConnectedServerAddress(),
            asRecord(clientInfo?.currentServer)?.address,
            asRecord(clientInfo?.currentServer)?.ip,
            clientInfo?.serverAddress,
            clientInfo?.serverIp
        ]
            .filter((value): value is string => typeof value === "string" && !isPlaceholderServerAddress(value))
            .map(normalizeServerAddress);
    }

    function findMatchingServer(servers: Server[], knownAddresses = knownServerAddresses()) {
        if (knownAddresses.length === 0) return undefined;
        return servers.find(server => {
            const address = normalizeServerAddress(server.address);
            return knownAddresses.some(known => known === address || known.split(":")[0] === address.split(":")[0]);
        });
    }

    function clearServerSnapshot() {
        lastKnownServerAddress = "";
        lastKnownServerName = "";
        lastKnownServerPing = 0;
        lastServerPlayerListEntries = [];
        playerListEntries = [];

        if (clientInfo) {
            clientInfo = {
                ...clientInfo,
                currentServer: undefined
            };
        }
    }

    function applyServerSnapshot(server: Server | Record<string, any>) {
        const record = asRecord(server);
        if (!record) return;

        const address = firstString(record.address, record.ip, record.serverAddress, record.serverIp);
        const name = firstString(record.name, record.label);
        const ping = firstFiniteNumber(record.ping, record.latency, record.responseTime);

        if (address) lastKnownServerAddress = address;
        if (name) lastKnownServerName = name;
        if (ping !== undefined && ping > 0) {
            lastKnownServerPing = Math.round(ping);
        }

        clientInfo = {
            ...(clientInfo as ClientInfo),
            inGame: clientInfo?.inGame ?? true,
            currentServer: {
                ...(clientInfo?.currentServer ?? {}),
                address: address ?? clientInfo?.currentServer?.address,
                name: name ?? clientInfo?.currentServer?.name,
                ping: ping ?? clientInfo?.currentServer?.ping
            }
        };

        const entries = extractPlayerListEntries(record);
        if (entries.length > 0) {
            lastServerPlayerListEntries = entries;
            playerListEntries = entries;
        }
    }

    function asRecord(value: unknown): Record<string, any> | null {
        return value && typeof value === "object" ? value as Record<string, any> : null;
    }

    function firstString(...values: unknown[]) {
        for (const value of values) {
            if (typeof value === "string" && value.trim().length > 0) return value.trim();
        }

        return undefined;
    }

    function firstFiniteNumber(...values: unknown[]) {
        for (const value of values) {
            const number = typeof value === "number" ? value : Number(value);
            if (Number.isFinite(number) && number >= 0) return number;
        }

        return undefined;
    }

    function getNestedRecord(record: Record<string, any>, key: string) {
        return asRecord(record[key]);
    }

    function isTextComponentLike(value: unknown) {
        const record = asRecord(value);
        return !!record && (
            typeof record.text === "string" ||
            typeof record.literal === "string" ||
            typeof record.content === "string" ||
            typeof record.string === "string" ||
            typeof record.plainText === "string" ||
            Array.isArray(record.extra) ||
            Array.isArray(record.siblings) ||
            typeof record.translate === "string"
        );
    }

    function textComponentToPlain(value: unknown): string | undefined {
        if (typeof value === "string") return value;
        const record = asRecord(value);
        if (!record) return undefined;

        const parts: string[] = [];
        if (typeof record.text === "string") parts.push(record.text);
        if (typeof record.literal === "string") parts.push(record.literal);
        if (typeof record.content === "string") parts.push(record.content);
        if (typeof record.string === "string") parts.push(record.string);
        if (typeof record.plainText === "string") parts.push(record.plainText);
        if (typeof record.translate === "string") parts.push(record.translate);
        if (Array.isArray(record.extra)) {
            for (const child of record.extra) {
                const text = textComponentToPlain(child);
                if (text) parts.push(text);
            }
        }
        if (Array.isArray(record.siblings)) {
            for (const child of record.siblings) {
                const text = textComponentToPlain(child);
                if (text) parts.push(text);
            }
        }

        const result = parts.join("").trim();
        return result || undefined;
    }

    function isLikelyUuid(value: string) {
        return /^[0-9a-f]{32}$/i.test(value) ||
            /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
    }

    function hasPlayerIdentity(record: Record<string, any>) {
        return !!firstString(
            record.uuid,
            record.id,
            record.profileId,
            record.gameProfileId,
            record.profile?.id,
            record.gameProfile?.id,
            record.player?.uuid,
            record.player?.id
        );
    }

    function hasPlayerName(record: Record<string, any>) {
        return !!firstString(
            record.name,
            record.username,
            record.profileName,
            record.gameProfileName,
            record.profile?.name,
            record.gameProfile?.name,
            record.player?.name,
            record.player?.username
        ) || isTextComponentLike(record.displayName) ||
            isTextComponentLike(record.display) ||
            isTextComponentLike(record.playerName) ||
            isTextComponentLike(record.listName);
    }

    function hasPlayerLatency(record: Record<string, any>) {
        const latencyRecord = getNestedRecord(record, "latency");
        return firstFiniteNumber(
            record.latency,
            record.ping,
            record.responseTime,
            record.networkLatency,
            record.latencyMs,
            latencyRecord?.value,
            latencyRecord?.ms,
            record.player?.latency,
            record.player?.ping
        ) !== undefined;
    }

    function looksLikePlayerEntry(record: Record<string, any>) {
        const profile = getNestedRecord(record, "profile");
        const gameProfile = getNestedRecord(record, "gameProfile");
        const player = getNestedRecord(record, "player");

        return hasPlayerIdentity(record) ||
            !!profile?.name ||
            !!gameProfile?.name ||
            !!player?.username ||
            (hasPlayerName(record) && hasPlayerLatency(record));
    }

    function entryFromRecord(entry: Record<string, any>, index: number): PlayerListEntry {
        const profile = getNestedRecord(entry, "profile");
        const gameProfile = getNestedRecord(entry, "gameProfile");
        const player = getNestedRecord(entry, "player");
        const latencyRecord = getNestedRecord(entry, "latency");

        const displayName = entry.displayName
            ?? entry.display
            ?? entry.playerName
            ?? entry.listName
            ?? entry.name
            ?? entry.username
            ?? profile?.name
            ?? gameProfile?.name
            ?? player?.displayName
            ?? player?.name
            ?? null;

        const plainDisplayName = textComponentToPlain(displayName);

        return {
            uuid: firstString(
                entry.uuid,
                entry.id,
                entry.profileId,
                entry.gameProfileId,
                profile?.id,
                gameProfile?.id,
                player?.uuid,
                player?.id
            ),
            name: firstString(
                entry.name,
                entry.username,
                entry.profileName,
                entry.gameProfileName,
                profile?.name,
                gameProfile?.name,
                player?.name,
                player?.username,
                plainDisplayName,
                `player-${index}`
            ),
            displayName,
            latency: firstFiniteNumber(
                entry.latency,
                entry.ping,
                entry.responseTime,
                entry.networkLatency,
                entry.latencyMs,
                latencyRecord?.value,
                latencyRecord?.ms,
                player?.latency,
                player?.ping
            )
        };
    }

    function tupleToPlayerEntry(value: unknown[], index: number): PlayerListEntry | null {
        const strings = value.filter((item): item is string => typeof item === "string" && item.trim().length > 0);
        if (strings.length === 0) return null;

        const uuid = strings.find(isLikelyUuid);
        const name = strings.find(item => item !== uuid) ?? uuid ?? `player-${index}`;
        const latency = firstFiniteNumber(...value);

        return {
            uuid,
            name,
            displayName: name,
            latency
        };
    }

    function withMapKeyFallback(entry: PlayerListEntry, key: string) {
        if (!key || key === "entries" || key === "players") return entry;
        if (isLikelyUuid(key)) return entry.uuid ? entry : { ...entry, uuid: key };
        if (entry.name || entry.displayName) return entry;
        return {
            ...entry,
            name: key,
            displayName: key
        };
    }

    function stringToPlayerEntries(value: string): PlayerListEntry[] {
        return value
            .split(/\r?\n|,/)
            .map(part => part.trim())
            .filter(Boolean)
            .map((name, index) => ({
                uuid: isLikelyUuid(name) ? name : undefined,
                name,
                displayName: name || `player-${index}`
            }));
    }

    function extractPlayerListEntries(
        value: unknown,
        visited = new Set<unknown>(),
        fromPlayerListContainer = false
    ): PlayerListEntry[] {
        if (!value) return [];
        if (fromPlayerListContainer && typeof value === "string") return stringToPlayerEntries(value);
        if (visited.has(value)) return [];
        if (typeof value === "object") visited.add(value);

        if (Array.isArray(value)) {
            const entries: PlayerListEntry[] = [];

            value.forEach((item, index) => {
                if (typeof item === "string" && item.trim().length > 0) {
                    entries.push({
                        uuid: isLikelyUuid(item.trim()) ? item.trim() : undefined,
                        name: item.trim(),
                        displayName: item.trim()
                    });
                    return;
                }

                const record = asRecord(item);
                if (record && looksLikePlayerEntry(record)) {
                    entries.push(entryFromRecord(record, index));
                    return;
                }

                const nested = extractPlayerListEntries(item, visited, true);
                if (nested.length > 0) {
                    entries.push(...nested);
                    return;
                }

                if (Array.isArray(item)) {
                    const tupleEntry = tupleToPlayerEntry(item, index);
                    if (tupleEntry) entries.push(tupleEntry);
                }
            });

            return entries;
        }

        const record = asRecord(value);
        if (!record) return [];

        if (fromPlayerListContainer && isTextComponentLike(record)) {
            const name = textComponentToPlain(record);
            return name ? [{
                name,
                displayName: record as TTextComponent
            }] : [];
        }

        if (fromPlayerListContainer && looksLikePlayerEntry(record)) {
            return [entryFromRecord(record, 0)];
        }

        const entries: PlayerListEntry[] = [];

        for (const key of [
            "entries",
            "players",
            "playerList",
            "playerListEntries",
            "listedPlayers",
            "onlinePlayers",
            "shownPlayers",
            "tabList",
            "tabEntries",
            "playerInfos",
            "playerInfoMap",
            "networkPlayers",
            "list",
            "sample",
            "values"
        ]) {
            entries.push(...extractPlayerListEntries(record[key], visited, true));
        }

        if (fromPlayerListContainer) {
            for (const [key, child] of Object.entries(record)) {
                if (key === "header" || key === "footer") continue;

                if (typeof child === "string" && child.trim().length > 0) {
                    entries.push(withMapKeyFallback({
                        uuid: isLikelyUuid(key) ? key : undefined,
                        name: child.trim(),
                        displayName: child.trim()
                    }, key));
                    continue;
                }

                if (isTextComponentLike(child)) {
                    entries.push(withMapKeyFallback({
                        uuid: isLikelyUuid(key) ? key : undefined,
                        name: textComponentToPlain(child) ?? key,
                        displayName: child as TTextComponent
                    }, key));
                    continue;
                }

                const nested = extractPlayerListEntries(child, visited, true)
                    .map(entry => withMapKeyFallback(entry, key));
                entries.push(...nested);
            }
        }

        return entries;
    }

    function normalizePlayerEntry(entry: PlayerListEntry, index: number): IslandPlayer {
        const rawName = entry.displayName ?? entry.name ?? "Player";
        const normalizedName = typeof rawName === "string"
            ? rawName
            : entry.name ?? `player-${index}`;

        return {
            id: entry.uuid ?? entry.name ?? normalizedName,
            displayName: showUsername ? rawName : protectedName,
            ping: entry.latency ?? entry.ping ?? 0
        };
    }

    function uniquePlayers(entries: IslandPlayer[]) {
        const seen = new Set<string>();
        const unique: IslandPlayer[] = [];

        for (const entry of entries) {
            const key = entry.id || String(entry.displayName);
            if (seen.has(key)) continue;
            seen.add(key);
            unique.push(entry);
        }

        return unique;
    }

    function isPlayerListKey(key: string | undefined) {
        return !!playerListKey && playerListKey !== "key.keyboard.unknown" && key === playerListKey;
    }

    function isPlayerListKeyboardEvent(event: KeyboardKeyEvent) {
        return !event.screen && isPlayerListKey(event.key);
    }

    function isPlayerListGameKeyEvent(event: KeyEvent) {
        return isPlayerListKey(event.key);
    }

    function setVanillaPlayerListSuppressed(suppressed: boolean) {
        if (playerListSuppressInterval) {
            clearInterval(playerListSuppressInterval);
            playerListSuppressInterval = null;
        }

        if (!suppressed) {
            void setTyping(false).catch(() => {});
            return;
        }

        void setTyping(true).catch(() => {});
        playerListSuppressInterval = setInterval(() => {
            void setTyping(true).catch(() => {});
        }, PLAYER_LIST_SUPPRESS_INTERVAL_MS);
    }

    function setPlayerListOpen(open: boolean) {
        if (playerListOpen === open) return;
        playerListOpen = open;
        setVanillaPlayerListSuppressed(open);
        if (open) {
            void refreshClientState();
            void updateServerSnapshot();
        }
    }

    function isBuildableBlock(identifier: string): boolean {
        if (!identifier || identifier === "minecraft:air") return false;
        const id = identifier.toLowerCase();
        return !NON_BLOCK_KEYWORDS.some(keyword => id.includes(keyword));
    }

    async function sampleScaffoldBlockColor(identifier: string) {
        const cachedColor = scaffoldColorCache.get(identifier);
        if (cachedColor) {
            scaffoldBlockColor = cachedColor;
            return;
        }

        try {
            const img = new Image();
            img.crossOrigin = "anonymous";
            img.src = itemTextureUrl(identifier);
            await new Promise(resolve => {
                img.onload = resolve;
                img.onerror = resolve;
            });

            const canvas = document.createElement("canvas");
            canvas.width = img.naturalWidth || 16;
            canvas.height = img.naturalHeight || 16;
            const ctx = canvas.getContext("2d");
            if (!ctx) return;

            ctx.drawImage(img, 0, 0);
            const data = ctx.getImageData(0, 0, canvas.width, canvas.height).data;
            let r = 0, g = 0, b = 0, total = 0;

            for (let i = 0; i < data.length; i += 4) {
                if (data[i + 3] < 128) continue;
                r += data[i];
                g += data[i + 1];
                b += data[i + 2];
                total++;
            }

            if (total > 0) {
                scaffoldBlockColor = `rgb(${Math.round(r / total)}, ${Math.round(g / total)}, ${Math.round(b / total)})`;
                scaffoldColorCache.set(identifier, scaffoldBlockColor);
            }
        } catch {
            scaffoldBlockColor = "var(--accent-color)";
        }
    }

    function normalizeItemIdentifier(identifier: string | undefined | null, fallback = "minecraft:oak_planks") {
        return identifier && identifier !== "minecraft:air" ? identifier : fallback;
    }

    function setScaffoldBlock(identifier: string | undefined | null) {
        const nextIdentifier = normalizeItemIdentifier(identifier, scaffoldBlockId);
        if (!nextIdentifier || nextIdentifier === scaffoldBlockId) return;
        scaffoldBlockId = nextIdentifier;

        if (lastSampledScaffoldBlockId !== nextIdentifier) {
            lastSampledScaffoldBlockId = nextIdentifier;
            void sampleScaffoldBlockColor(nextIdentifier);
        }
    }

    function formatBlockName(identifier: string) {
        return identifier
            .replace(/^minecraft:/, "")
            .split("_")
            .map(part => part.charAt(0).toUpperCase() + part.slice(1))
            .join(" ");
    }

    function getNotificationIcon(title: string, severity: NotificationEvent["severity"]) {
        const moduleIcon = moduleIconByName.get(title);
        if (moduleIcon) return moduleIcon;

        switch (severity) {
            case "ERROR": return "img/hud/notification/icon-error.svg";
            case "DISABLED": return "img/hud/notification/icon-error.svg";
            case "SUCCESS":
            case "ENABLED": return "img/hud/notification/icon-success.svg";
            default: return "img/hud/notification/icon-info.svg";
        }
    }

    function normalizeModuleNotification(event: NotificationEvent) {
        if (event.severity === "ENABLED" || event.severity === "DISABLED") {
            const lowerMessage = event.message.toLowerCase();
            const title = lowerMessage.startsWith("module ") ? event.title : event.message || event.title;

            return {
                title,
                message: event.severity === "ENABLED" ? "Enabled" : "Disabled",
                severity: event.severity
            };
        }

        return {
            title: event.title,
            message: event.message,
            severity: event.severity
        };
    }

    function scheduleNotificationRemoval(id: number) {
        notificationTimers.set(id, setTimeout(() => {
            islandNotifications = islandNotifications.filter(n => n.id !== id);
            clearNotificationTimer(id);
        }, NOTIFICATION_TTL));
    }

    function addIslandNotification(title: string, message: string, severity: NotificationEvent["severity"]) {
        if (editMode || !islandNotificationsEnabled) return;

        const signature = `${title}\u0000${message}\u0000${severity}`;
        const now = performance.now();
        if (signature === lastNotificationSignature && now - lastNotificationTime < 120) return;
        lastNotificationSignature = signature;
        lastNotificationTime = now;

        const item: IslandNotification = {
            id: ++notificationSequence,
            title,
            message,
            severity,
            icon: getNotificationIcon(title, severity)
        };

        const next = [...islandNotifications, item].slice(-MAX_NOTIFICATIONS);
        const visibleIds = new Set(next.map(n => n.id));

        for (const notification of islandNotifications) {
            if (!visibleIds.has(notification.id)) clearNotificationTimer(notification.id);
        }

        islandNotifications = next;
        scheduleNotificationRemoval(item.id);
    }

    function clearNotificationTimer(id: number) {
        const timer = notificationTimers.get(id);
        if (timer) clearTimeout(timer);
        notificationTimers.delete(id);
    }

    function clearIslandNotifications() {
        for (const timer of notificationTimers.values()) clearTimeout(timer);
        notificationTimers.clear();
        islandNotifications = [];
        lastNotificationSignature = "";
        lastNotificationTime = 0;
    }

    function pingStrength(ping: number) {
        if (ping <= 0) return 1;
        if (ping <= 75) return 5;
        if (ping <= 150) return 4;
        if (ping <= 300) return 3;
        if (ping <= 600) return 2;
        return 1;
    }

    listen("clientPlayerData", (e: ClientPlayerDataEvent) => {
        if (editMode) return;
        applyPlayerData(e.playerData);

        const mainHand = e.playerData.mainHandStack;
        if (mainHand && isBuildableBlock(mainHand.identifier) && mainHand.count > 0) {
            setScaffoldBlock(mainHand.identifier);
            if (scaffoldCount <= 0) scaffoldCount = mainHand.count;
        } else if (!scaffoldActive) {
            scaffoldCount = 0;
        }

        const entriesFromPlayerData = extractPlayerListEntries(e.playerData);
        if (entriesFromPlayerData.length > 0) playerListEntries = entriesFromPlayerData;
    });

    listen("blockCountChange", (event) => {
        if (editMode || !scaffoldIslandEnabled) return;
        if (event.nextBlock) setScaffoldBlock(event.nextBlock);
        if (event.count !== undefined) scaffoldCount = event.count;
        scaffoldActive = true;

        if (scaffoldTimeout) clearTimeout(scaffoldTimeout);
        scaffoldTimeout = setTimeout(() => {
            const scaffoldModule = modules.find(m => m.name.toLowerCase() === "scaffold");
            scaffoldActive = !!scaffoldModule?.enabled;
        }, 1200);
    });

    listen("playerList", (event: PlayerListEvent) => {
        const record = asRecord(event);
        const entries = extractPlayerListEntries(event);
        if (entries.length > 0) playerListEntries = entries;
        playerListHeader = record?.header ?? null;
        playerListFooter = record?.footer ?? null;
    });

    listen("serverPinged", (event: ServerPingedEvent) => {
        const server = event.server;
        if (!server) return;

        const knownAddresses = knownServerAddresses();
        if (knownAddresses.length === 0) return;

        const serverAddress = normalizeServerAddress(server.address);
        const matchesKnownServer = knownAddresses.some(known =>
            known === serverAddress || known.split(":")[0] === serverAddress.split(":")[0]
        );

        if (matchesKnownServer) applyServerSnapshot(server);
    });

    listen("session", updateSession);
    listen("disconnect", clearServerSnapshot);
    listen("keybindChange", updatePlayerListKeybind);
    listen("key", (event: KeyEvent) => {
        if (!playerListEnabled || !isPlayerListGameKeyEvent(event)) return;
        setPlayerListOpen(event.action !== 0);
    });
    listen("keyboardKey", (event: KeyboardKeyEvent) => {
        if (!playerListEnabled || !isPlayerListKeyboardEvent(event)) return;
        setPlayerListOpen(event.action !== 0);
    });

    listen("notification", (event: NotificationEvent) => {
        const notification = normalizeModuleNotification(event);
        if (notification.title.toLowerCase() === "scaffold" && (notification.severity === "ENABLED" || notification.severity === "DISABLED")) return;
        addIslandNotification(notification.title, notification.message, notification.severity);
    });

    listen("moduleToggle", (event: ModuleToggleEvent) => {
        const moduleName = event.moduleName.toLowerCase();
        setLocalModuleEnabled(event.moduleName, event.enabled);

        if (moduleName === "nameprotect") {
            showUsername = !event.enabled;
        }

        if (moduleName === "scaffold") {
            scaffoldActive = event.enabled;
            if (!event.enabled) {
                if (scaffoldTimeout) {
                    clearTimeout(scaffoldTimeout);
                    scaffoldTimeout = null;
                }
                scaffoldCount = 0;
            }
            return;
        }

        addIslandNotification(
            event.moduleName,
            event.enabled ? "Enabled" : "Disabled",
            event.enabled ? "ENABLED" : "DISABLED"
        );
    });

    $: dispFps = editMode ? 120 : (clientInfo?.fps ?? 0);
    $: playerName = session?.username ?? playerData?.username ?? "Player";
    $: displayName = showUsername ? playerName : protectedName;

    $: scaffoldModuleEnabled = modules.some(m => m.name.toLowerCase() === "scaffold" && m.enabled);
    $: scaffoldProgress = Math.max(0, Math.min(100, (scaffoldCount / MAX_DISPLAY_BLOCKS) * 100));
    $: scaffoldBlockName = formatBlockName(scaffoldBlockId);
    $: scaffoldTextureUrl = itemTextureUrl(normalizeItemIdentifier(scaffoldBlockId));
    $: scaffoldShownCount = scaffoldCount || (editMode ? 48 : 0);
    $: if (editMode && scaffoldBlockColor === "var(--accent-color)") scaffoldBlockColor = "#a08150";
    $: fallbackPlayerEntries = playerData ? [{
        id: playerData.uuid,
        displayName: showUsername ? playerName : protectedName,
        ping: playerData.ping ?? 0,
        self: true
    }] : [];
    $: livePlayerEntries = uniquePlayers([
        ...playerListEntries,
        ...lastServerPlayerListEntries,
        ...clientInfoPlayerEntries,
        ...playerDataPlayerEntries
    ].map(normalizePlayerEntry));
    $: playerEntries = editMode ? demoPlayerEntries : (livePlayerEntries.length > 0 ? livePlayerEntries : fallbackPlayerEntries);
    $: showScaffoldPanel = scaffoldIslandEnabled && (editMode || ((scaffoldActive || scaffoldModuleEnabled) && scaffoldShownCount > 0));
    $: showPlayerPanel = playerListEnabled && playerListOpen;
    $: expanded = showScaffoldPanel || showPlayerPanel || showNotificationPanel;
</script>


<div
    class="coordinates-info"
    class:expanded
    class:scaffold-mode={showScaffoldPanel}
    class:player-mode={showPlayerPanel}
    class:notification-mode={showNotificationPanel}
    style="--user-width-offset: {islandWidthOffset}px;"
    transition:fade={{ duration: 150, easing: cubicOut }}
>
    <div class="island-primary-shell" class:scaffold-primary={showScaffoldPanel}>
        {#if !showScaffoldPanel}
            <div
                class="island-main"
                in:fade|global={{ duration: 110, easing: cubicOut }}
                out:fade|global={{ duration: 80, easing: cubicOut }}
            >
                {#if showNick}
                    <div class="value user-value">
                        <span class="info-mask icon-user-mask" aria-hidden="true"></span>
                        <span class="user-name">{displayName}</span>
                    </div>
                {/if}

                {#if showNick && showFps}
                    <span class="dot"></span>
                {/if}

                {#if showFps}
                    <div class="fps-wrap">
                        <span class="info-mask icon-fps-mask" aria-hidden="true"></span>
                        <span class="value">{dispFps}</span>
                        <span class="unit">FPS</span>
                    </div>
                {/if}

                {#if showFps}
                    <span class="dot"></span>
                {/if}

                <span class="value">{currentTime}</span>
            </div>
        {/if}

        {#if showScaffoldPanel}
            <div
                class="scaffold-panel"
                in:fly|global={{ y: -6, duration: 180, easing: cubicOut }}
                out:fade|global={{ duration: 90, easing: cubicOut }}
            >
                <div class="scaffold-icon-wrapper" style="background-color: color-mix(in srgb, {scaffoldBlockColor} 40%, transparent); box-shadow: 0 0 12px color-mix(in srgb, {scaffoldBlockColor} 40%, transparent);">
                    <img
                        class="scaffold-mc-icon"
                        src={scaffoldTextureUrl}
                        alt={scaffoldBlockName}
                        onerror={(event) => ((event.currentTarget as HTMLImageElement).src = itemTextureUrl("minecraft:oak_planks"))}
                    >
                </div>

                <div class="scaffold-progress-wrapper">
                    <div class="scaffold-progress-bar" style="background-color: color-mix(in srgb, {scaffoldBlockColor} 40%, transparent); box-shadow: 0 0 12px color-mix(in srgb, {scaffoldBlockColor} 40%, transparent);">
                        <div class="scaffold-progress-fill" style="width: {editMode ? 75 : scaffoldProgress}%; background-color: {scaffoldBlockColor}; box-shadow: 0 0 8px color-mix(in srgb, {scaffoldBlockColor} 60%, transparent);"></div>
                    </div>
                </div>

                <div class="scaffold-text-wrapper">
                    <div class="scaffold-main-info">
                        <span class="scaffold-count" style="color: {scaffoldBlockColor}; text-shadow: 0 0 8px color-mix(in srgb, {scaffoldBlockColor} 60%, transparent);">{scaffoldShownCount}</span>
                        <span class="scaffold-label">blocks</span>
                    </div>
                </div>
            </div>
        {/if}
    </div>

    {#if showNotificationPanel}
        <div
            class="notification-stack"
            in:slide|global={{ duration: 260, easing: quintOut }}
            out:fly|global={{ y: -8, duration: 180, easing: cubicOut }}
        >
            {#each notificationStack as notification, index (notification.id)}
                <div
                    class="island-notification {notification.severity.toLowerCase()}"
                    class:compact={index >= MAX_EXPANDED_NOTIFICATIONS}
                    style="--stack-index: {index};"
                    in:fly|global={{ y: -14, duration: 220, easing: cubicOut }}
                    out:fly|global={{ y: -10, duration: 220, easing: cubicOut }}
                >
                    <div class="notification-icon">
                        <img class="themed-icon-mask notification-mask" src={notification.icon} alt="" aria-hidden="true">
                    </div>
                    <div class="notification-copy">
                        <span class="notification-title">{notification.title}</span>
                        <span class="notification-message">{notification.message}</span>
                    </div>
                    <div class="notification-state"></div>
                </div>
            {/each}
        </div>
    {/if}

    {#if showPlayerPanel}
        <div class="player-list" transition:slide={{ duration: 250, easing: quintOut }}>
            {#if playerListHeader}
                <div class="player-list-text">
                    <TextComponent fontSize={12} allowPreformatting={true} textComponent={playerListHeader}/>
                </div>
            {/if}

            <div class="player-list-header">
                <span>Players</span>
                <span>{playerEntries.length}</span>
            </div>

            <div class="player-list-body">
                {#each playerEntries as player (player.id)}
                    <div class="player-row">
                        <div class="player-name">
                            <TextComponent fontSize={14} allowPreformatting={true} textComponent={player.displayName}/>
                        </div>
                        <div class="ping-bars" title="{player.ping}ms">
                            {#each PING_BAR_LEVELS as index}
                                <i class:active={index < pingStrength(player.ping)} style="height: {4 + index * 3}px;"></i>
                            {/each}
                        </div>
                    </div>
                {/each}
            </div>

            {#if playerListFooter}
                <div class="player-list-text footer">
                    <TextComponent fontSize={12} allowPreformatting={true} textComponent={playerListFooter}/>
                </div>
            {/if}
        </div>
    {/if}
</div>

<style lang="scss">
    .coordinates-info {
        display: flex;
        flex-direction: column;
        align-items: stretch;
        gap: 0;
        background: rgba(255, 255, 255, 0.18) !important;
        backdrop-filter: blur(12px) !important;
        -webkit-backdrop-filter: blur(12px) !important;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2) !important;
        padding: 10px;
        border-radius: 8px;
        width: fit-content;
        max-width: min(560px, 92vw);
        overflow: hidden;
        contain: layout paint;
        transition:
            width 190ms cubic-bezier(0.22, 1, 0.36, 1),
            max-width 190ms cubic-bezier(0.22, 1, 0.36, 1),
            background 120ms ease-out,
            border-radius 160ms ease-out,
            padding 160ms ease-out;
        will-change: width, border-radius;

        min-width: 0;

        &.expanded {
            border-radius: 16px;   /* 保留原展开样式 */
        }

        &.notification-mode:not(.player-mode) {
            border-radius: 17px;
        }

        &.scaffold-mode:not(.player-mode) {
            border-radius: 18px;
        }
    }

    .island-primary-shell {
        position: relative;
        width: 100%;
        min-height: 18px;
        overflow: hidden;
        transition: min-height 170ms cubic-bezier(0.22, 1, 0.36, 1);
    }

    .island-primary-shell.scaffold-primary {
        min-height: 40px;
    }

    .island-main {
        display: flex;
        align-items: center;
        gap: 4px;
        width: 100%;
        overflow: hidden;
        white-space: nowrap;
    }

    .island-main > * {
        min-width: 0;
    }

    .info-mask {
        width: 14px;
        height: 14px;
        flex-shrink: 0;
        background-color: #ffffff !important;
        filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.25)) !important;
        mask-size: contain;
        mask-repeat: no-repeat;
        mask-position: center;
        -webkit-mask-size: contain;
        -webkit-mask-repeat: no-repeat;
        -webkit-mask-position: center;
    }

    .icon-user-mask {
        mask-image: url("/img/hud/information/icon-user.svg");
        -webkit-mask-image: url("/img/hud/information/icon-user.svg");
    }

    .icon-fps-mask {
        mask-image: url("/img/hud/information/icon-fps.svg");
        -webkit-mask-image: url("/img/hud/information/icon-fps.svg");
    }

    .value {
        font-size: 14px;
        font-weight: 600;
        color: #000000 !important;
        text-shadow: 0 1px 4px rgba(0, 0, 0, 0.08) !important;
        font-variant-numeric: tabular-nums;
        min-width: 0;
        line-height: 18px;
    }

    .user-value,
    .fps-wrap {
        flex: 0 0 auto;
        display: flex;
        align-items: center;
        gap: 4px;
    }

    .user-value .user-name {
        max-width: 120px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .dot {
        width: 4px;
        height: 4px;
        border-radius: 50%;
        background: rgba(0, 0, 0, 0.25) !important;
        flex-shrink: 0;
    }

    .unit {
        font-size: 12px;
        font-weight: 600;
        color: #3a3a3a !important;
    }

    .scaffold-panel,
    .notification-stack,
    .player-list {
        margin-top: 9px;
        padding-top: 9px;
        border-top: 1px solid rgba(0, 0, 0, 0.1) !important;
        transform-origin: top center;
    }

    .scaffold-panel {
        position: relative;
        display: flex;
        align-items: center;
        gap: 16px;
        min-height: 40px;
        margin-top: 0 !important;
        padding-top: 0 !important;
        border-top: none !important;
    }

    .scaffold-icon-wrapper {
        width: 40px;
        height: 40px;
        border-radius: 14px;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
        transition: background-color 0.4s ease, box-shadow 0.4s ease;
    }

    .scaffold-mc-icon {
        width: 24px;
        height: 24px;
        image-rendering: pixelated;
        filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.2)) !important;
    }

    .scaffold-progress-wrapper {
        width: 110px !important;
        display: flex;
        align-items: center;
        flex-shrink: 0;
    }

    .scaffold-progress-bar {
        width: 100%;
        height: 6px;
        border-radius: 8px;
        overflow: hidden;
    }

    .scaffold-progress-fill {
        height: 100%;
        border-radius: 8px;
        transition: width 0.4s ease, background-color 0.4s ease, box-shadow 0.4s ease;
    }

    .scaffold-text-wrapper {
        display: flex;
        flex-direction: column;
        justify-content: center;
        align-items: flex-start;
        min-width: 0;
    }

    .scaffold-main-info {
        display: flex;
        align-items: baseline;
        gap: 6px;
    }

    .scaffold-count {
        font-size: 20px;
        font-weight: 500;
        font-variant-numeric: tabular-nums;
        transition: color 0.4s ease;
        -webkit-text-stroke: 0 !important;
        text-shadow: 0 2px 8px rgba(0, 0, 0, 0.2), 0 0 4px rgba(0, 0, 0, 0.1) !important;
        flex-shrink: 0 !important;
    }

    .scaffold-label {
        font-size: 16px;
        font-weight: 500;
        color: #000000 !important;
        text-shadow: 0 1px 4px rgba(0, 0, 0, 0.08) !important;
        flex-shrink: 0 !important;
        white-space: nowrap !important;
    }

    .notification-stack {
        display: flex;
        flex-direction: column;
        gap: 6px;
        overflow: hidden;
        contain: layout paint;
    }

    .island-notification {
        display: grid;
        grid-template-columns: 28px minmax(0, 1fr) 4px;
        align-items: center;
        gap: 9px;
        min-height: 42px;
        padding: 7px 8px;
        border-radius: 12px;
        background: rgba(0, 0, 0, 0.06) !important;  /* 保持原样，不修改 */
        overflow: hidden;
        contain: layout paint;
        transition: opacity 120ms ease-out, background 120ms ease-out;
    }

    .island-notification.compact {
        min-height: 28px;
        padding: 3px 8px;
        opacity: 0.72;
        grid-template-columns: 22px minmax(0, 1fr) 3px;
    }

    .island-notification.compact .notification-icon {
        width: 22px;
        height: 22px;
        border-radius: 7px;
    }

    .island-notification.compact .notification-mask {
        --themed-icon-size: 13px;
    }

    .island-notification.compact .notification-message {
        display: none;
    }

    .island-notification.compact .notification-title {
        font-size: 12px;
    }

    .island-notification.compact .notification-state {
        width: 3px;
        height: 18px;
    }

    .notification-icon {
        width: 28px;
        height: 28px;
        border-radius: 9px;
        display: flex;
        align-items: center;
        justify-content: center;
        background: color-mix(in srgb, var(--accent-color) 13%, transparent);
    }

    .notification-mask {
        --themed-icon-size: 16px;
        --themed-icon-color: #ffffff !important;
        filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.25)) !important;
    }

    .notification-copy {
        display: flex;
        flex-direction: column;
        gap: 1px;
        min-width: 0;
    }

    .notification-title,
    .notification-message {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .notification-title {
        color: #000000 !important;
        font-size: 13px;
        font-weight: 700;
    }

    .notification-message {
        color: #3a3a3a !important;
        font-size: 11px;
        font-weight: 600;
    }

    .notification-state {
        width: 4px;
        height: 26px;
        border-radius: 999px;
        background: var(--accent-color);
    }

    .island-notification.error .notification-state,
    .island-notification.disabled .notification-state {
        background: var(--error-color);
    }

    .island-notification.success .notification-state,
    .island-notification.enabled .notification-state {
        background: var(--success-color);
    }

    .player-list {
        display: flex;
        flex-direction: column;
        gap: 6px;
    }

    .player-list-header,
    .player-list-text {
        display: flex;
        justify-content: space-between;
        color: #3a3a3a !important;
        font-size: 12px;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0;
    }

    .player-list-body {
        display: flex;
        flex-direction: column;
        gap: 1px;
        overflow: hidden;
    }

    .player-row {
        display: grid;
        grid-template-columns: minmax(0, 1fr) 34px;
        align-items: center;
        gap: 10px;
        min-height: 21px;
        padding: 2px 4px;
        border-radius: 6px;
        background: rgba(0, 0, 0, 0.06) !important;
        transform: translateZ(0);
        will-change: transform, opacity;
    }

    .player-name {
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        text-shadow: 1px 1px 0 rgba(0, 0, 0, 0.62);
    }

    .ping-bars {
        display: inline-flex;
        align-items: flex-end;
        justify-content: flex-end;
        gap: 2px;
        height: 18px;
    }

    .ping-bars i {
        width: 4px;
        border-radius: 2px 2px 0 0;
        background: rgba(0, 0, 0, 0.2) !important;
    }

    .ping-bars i.active {
        background: #20e85b;
        box-shadow: 0 0 5px rgba(32, 232, 91, 0.45);
    }

    .footer {
        text-transform: none;
    }
</style>
