<script lang="ts">
  import { onMount, onDestroy, tick, createEventDispatcher } from 'svelte'
  import { listen } from '@tauri-apps/api/event'

  export let maximized = false

  const dispatch = createEventDispatcher()

  interface LogLine { id: number; text: string; type: 'info' | 'warn' | 'error' | 'launcher' | 'fatal' }

  let lines: LogLine[] = []
  let logEl: HTMLDivElement
  let unlistenLog: () => void
  let unlistenStopped: () => void
  let counter = 0
  let autoScroll = true
  let gameRunning = false

  function classify(text: string): LogLine['type'] {
    if (text.startsWith('[LAUNCHER]')) return 'launcher'
    const low = text.toLowerCase()
    if (low.includes('fatal') || low.includes('crash')) return 'fatal'
    if (text.startsWith('[ERR]') || low.includes('error') || low.includes('exception')) return 'error'
    if (low.includes('warn')) return 'warn'
    return 'info'
  }

  function addLine(text: string) {
    lines = [...lines.slice(-1999), { id: counter++, text, type: classify(text) }]
    if (autoScroll) scrollBottom()
  }

  async function scrollBottom() {
    await tick()
    if (logEl) logEl.scrollTop = logEl.scrollHeight
  }

  function onScroll() {
    if (!logEl) return
    autoScroll = logEl.scrollHeight - logEl.scrollTop - logEl.clientHeight < 40
  }

  onMount(async () => {
    unlistenLog = await listen<string>('game_log', e => {
      if (e.payload.includes('[LAUNCHER] Minecraft')) gameRunning = true
      addLine(e.payload)
    })
    unlistenStopped = await listen<number>('game_stopped', e => {
      gameRunning = false
      addLine(`[LAUNCHER] Exit-Code: ${e.payload}`)
    })
  })

  onDestroy(() => { unlistenLog?.(); unlistenStopped?.() })
</script>

<div class="console-panel" class:maximized>
  <!-- Header -->
  <div class="con-header">
    <div class="con-left">
      <span class="con-icon">&gt;_</span>
      <span class="con-title">MINECRAFT KONSOLE</span>
      {#if gameRunning}
        <span class="pill-running">● LÄUFT</span>
      {:else}
        <span class="pill-stopped">■ GESTOPPT</span>
      {/if}
      <span class="line-count">{lines.length} Zeilen</span>
    </div>
    <div class="con-right">
      <button class="hbtn" on:click={() => { lines = []; counter = 0 }} title="Löschen">✕ Löschen</button>
      <button class="hbtn" on:click={() => navigator.clipboard.writeText(lines.map(l => l.text).join('\n'))} title="Alles kopieren">⎘ Kopieren</button>
      <button class="hbtn maximize-btn" on:click={() => maximized = !maximized} title={maximized ? 'Verkleinern' : 'Maximieren'}>
        {maximized ? '⬇ Verkleinern' : '⬆ Maximieren'}
      </button>
      <button class="hbtn close-btn" on:click={() => dispatch('close')} title="Schließen">✕</button>
    </div>
  </div>

  <!-- Log output -->
  <!-- svelte-ignore a11y-no-static-element-interactions -->
  <div class="con-log" bind:this={logEl} on:scroll={onScroll}>
    {#if lines.length === 0}
      <div class="empty">Warte auf Minecraft-Output...</div>
    {/if}
    {#each lines as line (line.id)}
      <div class="line {line.type}">{line.text}</div>
    {/each}
  </div>

  {#if !autoScroll}
    <button class="scroll-fab" on:click={scrollBottom}>↓ Ende</button>
  {/if}
</div>

<style>
  .console-panel {
    position: absolute;
    bottom: 0; left: 0; right: 0;
    height: 230px;
    display: flex;
    flex-direction: column;
    background: rgba(3, 6, 15, 0.97);
    border-top: 1px solid rgba(61,127,240,0.3);
    box-shadow: 0 -6px 30px rgba(0,0,0,0.7);
    font-family: 'Consolas', 'Cascadia Code', 'Courier New', monospace;
    font-size: 11.5px;
    z-index: 50;
    transition: height 0.2s ease;
  }
  .console-panel.maximized {
    height: calc(100% - 56px);
  }

  .con-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 5px 10px;
    background: rgba(0,0,0,0.55);
    border-bottom: 1px solid rgba(255,255,255,0.06);
    flex-shrink: 0;
    gap: 8px;
  }

  .con-left  { display: flex; align-items: center; gap: 8px; flex: 1; min-width: 0; }
  .con-right { display: flex; align-items: center; gap: 5px; flex-shrink: 0; }

  .con-icon  { color: #3d7ff0; font-size: 12px; font-weight: 800; }
  .con-title { font-size: 9px; font-weight: 800; letter-spacing: 2.5px; color: #7aaef5; white-space: nowrap; }

  .pill-running {
    font-size: 9px; font-weight: 700; letter-spacing: 0.8px;
    color: #4ade80; background: rgba(74,222,128,0.12);
    border: 1px solid rgba(74,222,128,0.3);
    border-radius: 20px; padding: 1px 7px; white-space: nowrap;
  }
  .pill-stopped {
    font-size: 9px; font-weight: 700; letter-spacing: 0.8px;
    color: #888; background: rgba(255,255,255,0.05);
    border: 1px solid rgba(255,255,255,0.1);
    border-radius: 20px; padding: 1px 7px; white-space: nowrap;
  }
  .line-count { font-size: 10px; color: #333; white-space: nowrap; }

  .hbtn {
    font-size: 10px; font-weight: 600;
    background: rgba(255,255,255,0.05);
    border: 1px solid rgba(255,255,255,0.1);
    color: #666; border-radius: 4px;
    padding: 2px 8px; cursor: pointer;
    font-family: inherit; white-space: nowrap;
    transition: background 0.12s, color 0.12s;
  }
  .hbtn:hover { background: rgba(255,255,255,0.1); color: #bbb; }
  .maximize-btn:hover { background: rgba(61,127,240,0.15); color: #7aaef5; border-color: rgba(61,127,240,0.3); }
  .close-btn { padding: 2px 7px; }
  .close-btn:hover { background: rgba(224,85,85,0.15); border-color: rgba(224,85,85,0.3); color: #ff8888; }

  .con-log {
    flex: 1;
    overflow-y: auto;
    padding: 5px 10px 6px;
    line-height: 1.55;
  }

  .line { white-space: pre-wrap; word-break: break-all; padding: 0.5px 0; }
  .line.info    { color: #b0bdce; }
  .line.warn    { color: #fbbf24; }
  .line.error   { color: #f87171; }
  .line.fatal   { color: #ff3333; font-weight: 700; }
  .line.launcher { color: #60a5fa; }

  .empty { color: #2a3550; font-size: 11px; padding: 6px 0; font-style: italic; }

  .scroll-fab {
    position: absolute; bottom: 8px; right: 12px;
    font-size: 10px; padding: 3px 10px;
    background: rgba(61,127,240,0.2); border: 1px solid rgba(61,127,240,0.4);
    color: #60a5fa; border-radius: 4px; cursor: pointer; font-family: inherit;
  }
</style>
