<script lang="ts">
    import { listen } from "../../../../integration/ws";
    import { itemTextureUrl } from "../../../../integration/rest";
    import type {
        TargetChangeEvent,
        ClientPlayerDataEvent
    } from "../../../../integration/events";
    import type { PlayerData, ItemStack, TextComponent, Vec3 } from "../../../../integration/types";
//半成品targethud

    let target = $state<PlayerData | null>(null);
    let localPos = $state<Vec3 | null>(null);
    let visible = $state(true);

    let hideTimeout: ReturnType<typeof setTimeout> | undefined;

    let distance = $derived(computeDistance(target, localPos));
    let healthPct = $derived(target ? healthPercent(target.health, target.maxHealth) : 0);
    let isDanger = $derived(healthPct <= 25);

    function startHideTimeout() {
        clearTimeout(hideTimeout);
        hideTimeout = setTimeout(() => {
            visible = false;
        }, 1000);
    }

    function clonePlayerData(p: PlayerData): PlayerData {
        return {
            ...p,
            position: p.position ? { ...p.position } : p.position,
            armorItems: p.armorItems ? p.armorItems.map(s => ({ ...s })) : p.armorItems,
            mainHandStack: p.mainHandStack ? { ...p.mainHandStack } : p.mainHandStack,
            offHandStack: p.offHandStack ? { ...p.offHandStack } : p.offHandStack,
        };
    }

    function setTarget(next: PlayerData | null) {
        target = next ? clonePlayerData(next) : null;
        if (target) {
            visible = true;
            startHideTimeout();
        }
    }

    const DEBUG = false;
    function log(...args: any[]) {
        if (DEBUG) console.log("[TargetHud]", ...args);
    }

    listen("targetChange", (data: TargetChangeEvent) => {
        log("targetChange fired", data.target?.username, data.target?.health, "/", data.target?.maxHealth);

        if (!data.target) {
            setTarget(null);
            return;
        }

        if (target && target.username === data.target.username) {
            setTarget({ ...target, ...data.target });
        } else {
            setTarget(data.target);
        }
    });


    listen("clientPlayerData", (event: ClientPlayerDataEvent) => {
        localPos = event.playerData?.position
            ? { ...event.playerData.position }
            : null;
    });

    startHideTimeout();

    function computeDistance(t: PlayerData | null, local: Vec3 | null): number | null {
        if (!t || !local) return null;
        const dx = t.position.x - local.x;
        const dy = t.position.y - local.y;
        const dz = t.position.z - local.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    function formatHealth(hp: number): string {
        return Math.max(0, Math.round(hp)).toString();
    }

    function healthPercent(hp: number, maxHp: number): number {
        if (maxHp <= 0) return 0;
        return Math.max(0, Math.min(100, (hp / maxHp) * 100));
    }

    function hasItem(stack: ItemStack | undefined | null): boolean {
        return !!stack && stack.count > 0;
    }

    function textOf(component: TextComponent | string | undefined | null): string {
        if (!component) return "";
        if (typeof component === "string") return component;
        let result = component.text ?? "";
        if (component.extra) {
            for (const child of component.extra) {
                result += textOf(child);
            }
        }
        return result;
    }

    function itemTexture(stack: ItemStack | null | undefined): string | null {
        if (!stack || stack.count <= 0) return null;
        return itemTextureUrl(stack.identifier);
    }

    const ARMOR_SLOTS = ["头盔", "胸甲", "护腿", "靴子"] as const;

    function armorStack(index: number): ItemStack | null {
        if (!target?.armorItems || index >= target.armorItems.length) return null;
        const stack = target.armorItems[index];
        return hasItem(stack) ? stack : null;
    }
</script>

{#if visible && target}
    <div
        class="target-hud"
        class:danger={isDanger}
    >
        <img class="glow-layer" src="img/hud/glow.png" alt="" aria-hidden="true" />
        <div class="flow-layer"></div>
        <div class="border-layer"></div>

        <div class="target-header">
            <span class="target-name">{target.username}</span>
            <div class="target-meta">
                {#if target.armor > 0}
                    <span class="armor-value">🛡 {target.armor}</span>
                {/if}
                {#if distance !== null}
                    <span class="distance-value">{distance.toFixed(1)}m</span>
                {/if}
            </div>
        </div>

        <div class="health-bar-container">
            <!-- 用 style: 指令，比字符串插值更稳 -->
            <div
                class="health-bar-fill"
                style:transform={`scaleX(${healthPct / 100})`}
            >
                <div class="health-bar-gradient"></div>
            </div>
            <span class="health-text">
                {formatHealth(target.health)} / {formatHealth(target.maxHealth)}
                {#if target.absorption > 0}
                    <span class="absorption">+{formatHealth(target.absorption)}</span>
                {/if}
            </span>
        </div>

        <div class="gear-row">
            {#each ARMOR_SLOTS as label, i}
                {#if armorStack(i)}
                    <div class="gear-slot" title="{label}: {textOf(armorStack(i)!.displayName)}">
                        <img
                            class="gear-icon"
                            src={itemTexture(armorStack(i))!}
                            alt={textOf(armorStack(i)!.displayName)}
                        />
                    </div>
                {/if}
            {/each}

            {#if hasItem(target.mainHandStack)}
                <div class="gear-slot main-hand" title="主手: {textOf(target.mainHandStack.displayName)}">
                    <img class="gear-icon" src={itemTexture(target.mainHandStack)!} alt="" />
                </div>
            {/if}

            {#if hasItem(target.offHandStack)}
                <div class="gear-slot off-hand" title="副手: {textOf(target.offHandStack.displayName)}">
                    <img class="gear-icon" src={itemTexture(target.offHandStack)!} alt="" />
                </div>
            {/if}
        </div>
    </div>
{/if}

<style lang="scss">
    /* [Perf] 面板本体：blur(8px) 静态 + 去 saturate；glassPulse 只动画 border/box-shadow */
    .target-hud {
        position: relative;
        display: flex;
        flex-direction: column;
        gap: 6px;
        min-width: 200px;
        padding: 12px 16px;
        border-radius: 20px;

        background: transparent !important;
        backdrop-filter: blur(8px) !important;
        -webkit-backdrop-filter: blur(8px) !important;
        border: 1px solid rgba(255, 255, 255, 0.08);
        box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15) !important;

        animation: glassPulse 4s ease-in-out infinite;
        transition: box-shadow 0.3s, border-color 0.5s;
        overflow: hidden;

        --c-blue: 88, 204, 250;
        --c-pink: 245, 150, 200;
        --c-purple: 123, 44, 191;
    }

    .target-hud:hover {
        box-shadow: 0 6px 24px rgba(0, 0, 0, 0.25) !important;
    }

    .target-hud.danger {
        animation-duration: 1.6s;
    }

    @keyframes glassPulse {
        0%, 100% {
            box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15),
                        0 0 0 0 rgba(var(--c-blue), 0);
            border-color: rgba(255, 255, 255, 0.08);
        }
        50% {
            box-shadow: 0 6px 24px rgba(0, 0, 0, 0.25),
                        0 0 12px 1px rgba(var(--c-pink), 0.4);
            border-color: rgba(var(--c-pink), 0.5);
        }
    }

    .glow-layer {
        position: absolute;
        inset: -20px;
        width: calc(100% + 40px);
        height: calc(100% + 40px);
        object-fit: cover;
        border-radius: 20px;
        pointer-events: none;
        z-index: -3;
        opacity: 0.7;
        animation: glowRotate 6s linear infinite;
        will-change: transform;
    }

    @keyframes glowRotate {
        from { transform: rotate(0deg) scale(1.05); }
        to   { transform: rotate(360deg) scale(1.05); }
    }

    .flow-layer {
        position: absolute;
        inset: 0;
        border-radius: 20px;
        pointer-events: none;
        z-index: -2;

        background: linear-gradient(
            90deg,
            rgba(var(--c-blue), 0.9) 0%,
            rgba(var(--c-pink), 0.95) 25%,
            rgba(var(--c-purple), 0.95) 50%,
            rgba(var(--c-pink), 0.95) 75%,
            rgba(var(--c-blue), 0.9) 100%
        );
        background-size: 300% 100%;

        padding: 1.5px;
        -webkit-mask:
            linear-gradient(#fff 0 0) content-box,
            linear-gradient(#fff 0 0);
        -webkit-mask-composite: xor;
                mask-composite: exclude;

        opacity: 0.9;
        filter: drop-shadow(0 0 4px rgba(var(--c-pink), 0.5));
        animation:
            flowMove 4s linear infinite,
            flowGlow 3s ease-in-out infinite;
        will-change: background-position, opacity;
    }

    @keyframes flowMove {
        0%   { background-position: 0% 50%; }
        100% { background-position: 300% 50%; }
    }

    @keyframes flowGlow {
        0%, 100% { opacity: 0.75; }
        50%      { opacity: 1; }
    }

    .border-layer {
        position: absolute;
        inset: 0;
        border-radius: 20px;
        pointer-events: none;
        z-index: -1;

        background: linear-gradient(
            135deg,
            rgba(var(--c-blue), 0.45) 0%,
            rgba(var(--c-pink), 0.45) 50%,
            rgba(var(--c-purple), 0.45) 100%
        );
        padding: 1px;
        -webkit-mask:
            linear-gradient(#fff 0 0) content-box,
            linear-gradient(#fff 0 0);
        -webkit-mask-composite: xor;
                mask-composite: exclude;

        box-shadow:
            inset 0 0 16px rgba(var(--c-purple), 0.12),
            0 0 10px rgba(var(--c-pink), 0.18);
        transition: box-shadow 0.5s;
    }

    .target-header {
        display: flex;
        align-items: baseline;
        justify-content: space-between;
        gap: 12px;
    }

    .target-name {
        font-weight: 700;
        font-size: 15px;
        letter-spacing: 0.3px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;

        background: linear-gradient(
            90deg,
            rgba(var(--c-blue), 1) 0%,
            rgba(var(--c-pink), 1) 50%,
            rgba(var(--c-purple), 1) 100%
        );
        background-size: 200% 100%;
        -webkit-background-clip: text;
        background-clip: text;
        -webkit-text-fill-color: transparent;
        color: transparent;

        filter: drop-shadow(0 0 6px rgba(var(--c-pink), 0.35));
        animation: nameFlow 3s linear infinite;
    }

    @keyframes nameFlow {
        0%   { background-position: 0% 50%; }
        100% { background-position: 200% 50%; }
    }

    .target-meta {
        display: flex;
        gap: 8px;
        flex-shrink: 0;
        font-family: monospace;
        font-size: 11px;
    }

    .armor-value {
        color: rgba(var(--c-blue), 0.9);
        opacity: 0.9;
    }

    .distance-value {
        color: rgba(var(--c-pink), 0.9);
        opacity: 0.9;
    }

    .health-bar-container {
        position: relative;
        width: 100%;
        height: 14px;
        background: rgba(0, 0, 0, 0.35);
        border-radius: 999px;
        overflow: hidden;
        border: 1px solid rgba(255, 255, 255, 0.06);
        box-shadow: inset 0 0 8px rgba(var(--c-purple), 0.18);
    }

    .health-bar-fill {
        width: 100%;
        height: 100%;
        border-radius: 999px;
        position: relative;
        overflow: hidden;
        background: rgba(var(--c-blue), 1);
        transform-origin: left;
        transition: transform 0.25s ease-out;
        will-change: transform;
        contain: layout paint;
    }

    .health-bar-gradient {
        position: absolute;
        top: 0;
        left: 0;
        width: 200%;
        height: 100%;
        background: linear-gradient(
            90deg,
            rgba(var(--c-blue), 1) 0%,
            rgba(var(--c-pink), 1) 25%,
            rgba(var(--c-purple), 1) 50%,
            rgba(var(--c-pink), 1) 75%,
            rgba(var(--c-blue), 1) 100%
        );
        animation: healthFlow 3s linear infinite;
        will-change: transform;
        transform: translate3d(0, 0, 0);
    }

    @keyframes healthFlow {
        0%   { transform: translate3d(0, 0, 0); }
        100% { transform: translate3d(-50%, 0, 0); }
    }

    .health-bar-fill::after {
        content: "";
        position: absolute;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: linear-gradient(
            90deg,
            transparent 0%,
            rgba(255, 255, 255, 0.35) 50%,
            transparent 100%
        );
        animation: healthShine 2.5s ease-in-out infinite;
        will-change: transform;
        pointer-events: none;
    }

    @keyframes healthShine {
        0%   { transform: translate3d(-100%, 0, 0); }
        100% { transform: translate3d(200%, 0, 0); }
    }

    .health-text {
        position: absolute;
        inset: 0;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 4px;
        font-family: monospace;
        font-size: 10px;
        font-weight: 700;
        color: #fff;
        text-shadow: 0 1px 3px rgba(0, 0, 0, 0.8);
        letter-spacing: 0.5px;
    }

    .absorption {
        color: #ffcc44;
        font-size: 9px;
    }

    .gear-row {
        display: flex;
        gap: 6px;
        flex-wrap: wrap;
        padding-top: 2px;
    }

    .gear-slot {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 24px;
        height: 24px;
        border-radius: 999px;
        background: rgba(255, 255, 255, 0.05);
        border: 1px solid rgba(255, 255, 255, 0.08);
        transition: border-color 0.2s, box-shadow 0.2s;
    }

    .gear-slot:hover {
        border-color: rgba(var(--c-pink), 0.5);
        box-shadow: 0 0 8px rgba(var(--c-pink), 0.45);
    }

    .gear-slot.main-hand {
        border-color: rgba(var(--c-blue), 0.35);
    }

    .gear-slot.off-hand {
        border-color: rgba(var(--c-purple), 0.35);
    }

    .gear-icon {
        width: 16px;
        height: 16px;
        image-rendering: pixelated;
        image-rendering: -moz-crisp-edges;
        image-rendering: crisp-edges;
    }
</style>
