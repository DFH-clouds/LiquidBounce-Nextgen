<script lang="ts">
    import { fly } from 'svelte/transition';
    import { listen } from '../../../../integration/ws.js';
    import type { PlayerData } from '../../../../integration/types';
    import { REST_BASE } from '../../../../integration/host';
    import HealthProgress from './HealthProgress.svelte';
    import ArmorStatus from './ArmorStatus.svelte';
    import type { TargetChangeEvent } from '../../../../integration/events';

 //灵感来于Miyabi-Client
    // 模式
    export let mode: 'simple' | 'xylitol' | 'moon' | 'exire' | 'new' | 'exhibition' | 'tenacity' | 'akrien' | 'raven' | 'naven' | 'southside' = 'exire';

    let target: PlayerData | null = null;
    let visible = true;
    let hideTimeout: number;


    function startHideTimeout() {
        hideTimeout = setTimeout(() => {
            visible = false;
        }, 1000);
    }

    listen('targetChange', (data: TargetChangeEvent) => {
        target = data.target;
        visible = true;
        clearTimeout(hideTimeout);
        startHideTimeout();
    });

    startHideTimeout();

    // ---------- 辅助计算 ----------
    $: health = target?.actualHealth ?? 0;
    $: maxHealth = target?.maxHealth ?? 20;
    $: absorption = target?.absorption ?? 0;
    $: armor = target?.armor ?? 0;
    $: totalHealth = health + absorption;
    $: maxTotal = maxHealth + absorption;
    $: healthPercent = maxTotal > 0 ? Math.min(totalHealth / maxTotal, 1) : 0;
    $: armorPercent = Math.min(armor / 20, 1);
    $: distance = target?.distance ?? 0;
    $: blockRate = target?.blockRate ?? 0;
    $: gappleCount = target?.gappleCount ?? 0;
    $: avatarUrl = target ? `${REST_BASE}/api/v1/client/resource/skin?uuid=${target.uuid}` : '';


    function getHealthColor(health: number, maxHealth: number): string {
        const ratio = Math.min(health / maxHealth, 1);
        const r = Math.round(255 * (1 - ratio));
        const g = Math.round(255 * ratio);
        return `rgb(${r}, ${g}, 0)`;
    }
</script>

{#if visible && target}
    <div class="targethud mode-{mode}" transition:fly={{ y: -10, duration: 200 }}>
        {#if mode === 'xylitol'}
            <!-- ========== Xylitol 模式 ========== -->
            <div class="xylitol">
                <div class="avatar" style="background-image: url('{avatarUrl}');"></div>
                <div class="info">
                    <div class="name"><span class="label">name: </span>{target.username}</div>
                    <div class="health"><span class="label">health: </span>{health.toFixed(1)}hp</div>
                </div>
                <div class="bar" style="width: {healthPercent * 100}%; background: linear-gradient(to right, var(--color-1), var(--color-6));"></div>
            </div>

        {:else if mode === 'southside'}
            <!-- ========== SouthSide========== -->
            <div class="southside">
                <div class="avatar" style="background-image: url('{avatarUrl}');"></div>
                <div class="info">
                    <div class="name">{target.username}</div>
                    <div class="stats">Health: {health.toFixed(1)}  Block Rate: {(blockRate * 100).toFixed(0)}%</div>
                </div>
                {#if distance <= 3}
                    <div class="proximity-indicator"></div>
                {/if}
                {#if gappleCount > 0}
                    <div class="gapple">Gapple: {gappleCount}</div>
                {/if}
                <div class="bar" style="width: {healthPercent * 100}%; background: white;"></div>
            </div>

        {:else if mode === 'naven'}
            <!-- ========== Naven ========== -->
            <div class="naven">
                <div class="avatar" style="background-image: url('{avatarUrl}');"></div>
                <div class="info">
                    <div class="name">{target.username}</div>
                    <div class="stats">Health: {health.toFixed(1)}</div>
                    <div class="stats">Distance: {distance.toFixed(1)} m</div>
                    <div class="stats">
                        {#if blockRate > 0}
                            Blocking ({(blockRate * 100).toFixed(0)}%)
                        {:else}
                            Not Blocking
                        {/if}
                    </div>
                </div>
                <div class="bar" style="width: {healthPercent * 100}%; background: #d20000;"></div>
            </div>

        {:else if mode === 'moon'}
            <!-- ========== Moon  ========== -->
            <div class="moon">
                <div class="avatar" style="background-image: url('{avatarUrl}');"></div>
                <div class="info">
                    <div class="name">{target.username}</div>
                    <div class="health">{Math.floor(health) + (health % 1 >= 0.5 ? 0.5 : 0)} HP</div>
                    <div class="bar" style="width: {healthPercent * 100}%; background: var(--color-1);"></div>
                </div>
            </div>

        {:else if mode === 'new'}
            <!-- ========== New========== -->
            <div class="new">
                <div class="info">
                    <div class="name">Name: {target.username}</div>
                    <div class="health">Health: {health.toFixed(1)}/{maxHealth.toFixed(1)}</div>
                </div>
            </div>

        {:else if mode === 'exhibition'}
            <!-- ========== Exhibition ========== -->
            <div class="exhibition">
                <div class="avatar" style="background-image: url('{avatarUrl}');"></div>
                <div class="info">
                    <div class="name">{target.username}</div>
                    <div class="health-bar">
                        <div class="fill" style="width: {Math.max(50, healthPercent * 100)}%; background: {getHealthColor(health, maxHealth)};"></div>
                        {#if absorption > 0}
                            <div class="absorption" style="width: {absorption / maxTotal * 100}%; background: #897009;"></div>
                        {/if}
                    </div>
                    <div class="stats">HP: {Math.floor(totalHealth)} | Dist: {Math.floor(distance)}</div>
                </div>
                <div class="armor">
                    {#each target.armorItems as item, i}
                        {#if item.count > 0}
                            <ArmorStatus itemStack={item} />
                        {/if}
                    {/each}
                </div>
            </div>

        {:else if mode === 'exire'}
            <!-- ========== Exire ========== -->
            <div class="exire">
                <div class="avatar" style="background-image: url('{avatarUrl}');"></div>
                <div class="info">
                    <div class="name">{target.username}</div>
                    <div class="bar" style="width: {healthPercent * 100}%; background: linear-gradient(to right, var(--color-1), var(--color-6));"></div>
                </div>
            </div>

        {:else if mode === 'tenacity'}
            <!-- ========== Tenacity========== -->
            <div class="tenacity">
                <div class="avatar" style="background-image: url('{avatarUrl}');"></div>
                <div class="info">
                    <div class="name">{target.username} {(blockRate * 100).toFixed(0)}%</div>
                    <div class="bar">
                        <div class="fill" style="width: {healthPercent * 100}%; background: linear-gradient(to right, var(--color-1), var(--color-6));"></div>
                        <span class="percent">{(healthPercent * 100).toFixed(0)}%</span>
                    </div>
                </div>
            </div>

        {:else if mode === 'akrien'}
            <!-- ========== Akrien ========== -->
            <div class="akrien">
                <div class="avatar" style="background-image: url('{avatarUrl}');"></div>
                <div class="info">
                    <div class="name">{target.username}</div>
                    <div class="stats">Health: {health.toFixed(1)}</div>
                    <div class="stats">Distance: {distance.toFixed(1)} m</div>
                    <div class="bar health-bar" style="width: {healthPercent * 100}%; background: linear-gradient(to right, #009C41, #8EFFC1);"></div>
                    <div class="bar armor-bar" style="width: {armorPercent * 100}%; background: linear-gradient(to right, #0067B0, #39D5FF);"></div>
                </div>
            </div>

        {:else if mode === 'raven'}
            <!-- ========== Raven========== -->
            <div class="raven">
                <div class="info">
                    <div class="name" style="color: #f44336;">{target.username}</div>
                    <div class="status">{health > (target?.maxHealth ?? 20) ? 'L' : 'W'}</div>
                    <div class="health">{health.toFixed(1)}</div>
                </div>
                <div class="bar" style="width: {healthPercent * 100}%; background: linear-gradient(to right, var(--color-1), var(--color-16));"></div>
            </div>

        {:else if mode === 'simple'}
            <!-- ========== Simple========== -->
            <div class="simple">
                <div class="avatar" style="background-image: url('{avatarUrl}');"></div>
                <div class="info">
                    <div class="name">{target.username}</div>
                    <div class="stats">Distance: {distance.toFixed(2)}m</div>
                    <div class="health">{health.toFixed(2)}</div>
                </div>
                <div class="bar" style="width: {healthPercent * 100}%; background: rgba(255,255,255,0.4);"></div>
                <div class="accent" style="height: 8px; background: var(--color-1);"></div>
            </div>

        {/if}
    </div>
{/if}

<style lang="scss">
    :root {
        --color-1: #ff4d4d;
        --color-6: #ffaa00;
        --color-16: #00aaff;
        --targethud-background-color: rgba(0, 0, 0, 0.75);
        --targethud-text-color: #ffffff;
        --targethud-text-dimmed-color: #aaaaaa;
    }

    .targethud {
        background-color: var(--targethud-background-color);
        border-radius: 5px;
        overflow: hidden;
        padding: 6px 10px;
        min-width: 160px;
        backdrop-filter: blur(4px);
        display: inline-block;

        .avatar {
            width: 32px;
            height: 32px;
            flex-shrink: 0;
            image-rendering: pixelated;
            background-size: cover;
            background-position: center;
            border-radius: 0;
            background-color: #2a2a2a;
            background-image: url('/img/steve.png');
        }

        .info {
            flex: 1;
            color: white;
            .name { font-weight: 500; font-size: 16px; }
            .stats { font-size: 12px; color: var(--targethud-text-dimmed-color); }
        }

        .bar {
            height: 3px;
            background: rgba(255,255,255,0.1);
            border-radius: 0;
            overflow: hidden;
            transition: width 0.3s ease-out;
        }

        &.mode-xylitol {
            .xylitol {
                display: flex;
                align-items: center;
                gap: 8px;
                .info {
                    .label { color: var(--color-6); }
                }
                .bar { height: 3px; background: linear-gradient(to right, var(--color-1), var(--color-6)); width: 0; }
            }
        }

        &.mode-southside {
            .southside {
                position: relative;
                display: flex;
                align-items: center;
                gap: 8px;
                .info .stats { font-size: 11px; }
                .proximity-indicator {
                    position: absolute;
                    left: -2px;
                    top: 2px;
                    bottom: 2px;
                    width: 2px;
                    background: white;
                }
                .gapple {
                    background: rgba(0,0,0,0.5);
                    padding: 0 6px;
                    border-radius: 4px;
                    font-size: 12px;
                    color: white;
                }
                .bar { height: 2px; background: white; width: 0; margin-top: 2px; }
            }
        }

        &.mode-naven {
            .naven {
                display: flex;
                gap: 10px;
                .info .stats { font-size: 11px; line-height: 1.4; }
                .bar { height: 3px; background: #d20000; width: 0; margin-top: 4px; }
            }
        }

        &.mode-moon {
            .moon {
                display: flex;
                gap: 10px;
                .info {
                    .health { font-size: 13px; }
                    .bar { height: 4px; background: var(--color-1); width: 0; margin-top: 4px; border-radius: 2px; }
                }
            }
        }

        &.mode-new {
            .new .info {
                .name { font-size: 15px; }
                .health { font-size: 14px; }
            }
        }

        &.mode-exhibition {
            .exhibition {
                display: flex;
                gap: 10px;
                .info {
                    .health-bar {
                        position: relative;
                        height: 4px;
                        background: black;
                        margin: 4px 0;
                        .fill { height: 100%; background: green; }
                        .absorption { position: absolute; right: 0; top: 0; height: 100%; background: #897009; }
                    }
                    .stats { font-size: 12px; }
                }
                .armor {
                    display: flex;
                    gap: 4px;
                    align-items: center;
                }
            }
        }

        &.mode-exire {
            .exire {
                display: flex;
                gap: 8px;
                align-items: center;
                .info {
                    .name { font-size: 18px; }
                    .bar { height: 4px; background: linear-gradient(to right, var(--color-1), var(--color-6)); width: 0; }
                }
            }
        }

        &.mode-tenacity {
            .tenacity {
                display: flex;
                gap: 10px;
                .info {
                    .bar {
                        position: relative;
                        height: 4px;
                        background: rgba(0,0,0,0.3);
                        .fill { height: 100%; background: linear-gradient(to right, var(--color-1), var(--color-6)); }
                        .percent {
                            position: absolute;
                            right: 0;
                            top: -12px;
                            font-size: 10px;
                            color: white;
                        }
                    }
                }
            }
        }

        &.mode-akrien {
            .akrien {
                display: flex;
                gap: 10px;
                .info {
                    .stats { font-size: 11px; }
                    .bar { height: 3px; margin-top: 2px; width: 0; }
                    .health-bar { background: linear-gradient(to right, #009C41, #8EFFC1); }
                    .armor-bar { background: linear-gradient(to right, #0067B0, #39D5FF); }
                }
            }
        }

        &.mode-raven {
            .raven {
                display: flex;
                align-items: center;
                gap: 8px;
                .info {
                    display: flex;
                    gap: 6px;
                    align-items: center;
                    .name { color: #f44336; }
                    .status { font-weight: bold; }
                    .health { color: var(--color-1); }
                }
                .bar { height: 4px; background: linear-gradient(to right, var(--color-1), var(--color-16)); width: 0; }
            }
        }

        &.mode-simple {
            .simple {
                display: flex;
                gap: 8px;
                align-items: center;
                .info {
                    .stats { font-size: 11px; }
                    .health { font-weight: bold; }
                }
                .bar { height: 100%; background: rgba(255,255,255,0.2); width: 0; position: absolute; left: 0; top: 0; }
                .accent { position: absolute; left: 0; bottom: 0; width: 3px; background: var(--color-1); }
                position: relative;
                overflow: hidden;
                padding: 4px 10px;
            }
        }
    }
</style>
