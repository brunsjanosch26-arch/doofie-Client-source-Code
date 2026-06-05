<script lang="ts">
  import { onMount } from 'svelte'
  import { invoke } from '@tauri-apps/api/core'
  import { listen } from '@tauri-apps/api/event'
  import { gameDir, accentColor, backgroundEffect } from '../stores/appStore'

  interface Config { ram: number; jvm_args: string; close_on_launch: boolean; auto_update: boolean }

  let config: Config = { ram: 4, jvm_args: '-XX:+UseG1GC', close_on_launch: false, auto_update: true }
  let javaPath = ''
  let saved = false
  let activeTab: 'general' | 'background' | 'versions' | 'advanced' = 'general'

  // ── Version management ──────────────────────────────────────────────────────
  const MANAGED_VERSIONS = [
    { id: '1.8',     label: 'Minecraft 1.8',     desc: 'Classic PvP' },
    { id: '1.21.10', label: 'Minecraft 1.21.10',  desc: 'Neueste stabile Version' },
    { id: '1.21.11', label: 'Minecraft 1.21.11',  desc: 'Empfohlen für Doofie Client' },
    { id: '26.1.2',  label: 'Minecraft 26.1.2',   desc: 'Neuestes Drop-Format' },
  ]

  let installedVersions: string[] = []
  let installingVersion: Record<string, boolean> = {}
  let installProgress: Record<string, string> = {}
  let installPercent: Record<string, number> = {}

  async function loadInstalledVersions() {
    installedVersions = await invoke<string[]>('get_installed_versions', { gameDir: $gameDir }).catch(() => [])
  }

  async function installVersion(versionId: string) {
    if (installingVersion[versionId]) return
    installingVersion[versionId] = true
    installProgress[versionId] = 'Verbinde...'
    installPercent[versionId] = 0

    const u = await listen<{ step: string; progress: number; total: number }>('download_progress', e => {
      installProgress[versionId] = e.payload.step
      installPercent[versionId] = e.payload.total > 0
        ? Math.round((e.payload.progress / e.payload.total) * 100)
        : 0
      installProgress = installProgress
      installPercent = installPercent
    })

    try {
      await invoke('download_version', { version: versionId, gameDir: $gameDir })
      await loadInstalledVersions()
    } catch (e) {
      installProgress[versionId] = `Fehler: ${e}`
    }

    u()
    installingVersion[versionId] = false
    installProgress[versionId] = ''
    installingVersion = installingVersion
  }

  async function deleteVersion(versionId: string) {
    const vPath = `${$gameDir}\\versions\\${versionId}`
    // Just remove from installed list UI — user can re-install
    installedVersions = installedVersions.filter(v => v !== versionId)
  }

  const ACCENT_PRESETS = [
    { label: 'Rot',     color: '#ef4444' },
    { label: 'Orange',  color: '#f97316' },
    { label: 'Amber',   color: '#f59e0b' },
    { label: 'Gelb',    color: '#eab308' },
    { label: 'Grün',    color: '#22c55e' },
    { label: 'Smaragd', color: '#10b981' },
    { label: 'Blaugrün',color: '#14b8a6' },
    { label: 'Cyan',    color: '#06b6d4' },
    { label: 'Blau',    color: '#3d7ff0' },
    { label: 'Indigo',  color: '#6366f1' },
    { label: 'Violett', color: '#7c3aed' },
    { label: 'Lila',    color: '#a855f7' },
    { label: 'Pink',    color: '#ec4899' },
    { label: 'Grau',    color: '#6b7280' },
    { label: 'Weiß',    color: '#e2e8f0' },
  ]

  const BG_PRESETS = [
    { id: 'retrogrid',      label: 'Retro Grid',        icon: '▦' },
    { id: 'particles',      label: 'Partikel',           icon: '✦' },
    { id: 'matrix',         label: 'Matrix Code',        icon: '</>' },
    { id: 'snow',           label: 'Schnee',             icon: '❄' },
    { id: 'nebula',         label: 'Nebula Wellen',      icon: '〰' },
    { id: 'none',           label: 'Kein Effekt',        icon: '⬛' },
  ]

  async function load() {
    try {
      javaPath = await invoke<string>('get_java_path')
      const c = await invoke<Config>('get_config', { gameDir: $gameDir })
      config = c
    } catch {}
  }

  async function save() {
    await invoke('save_config', { gameDir: $gameDir, config }).catch(() => {})
    await invoke('set_java_path', { path: javaPath }).catch(() => {})
    saved = true
    setTimeout(() => saved = false, 2000)
  }

  async function changeDir() {
    try {
      const { open } = await import('@tauri-apps/plugin-dialog')
      const selected = await open({ directory: true, title: 'Game-Ordner wählen' })
      if (selected && typeof selected === 'string') gameDir.set(selected)
    } catch {}
  }

  onMount(async () => {
    await load()
    await loadInstalledVersions()
  })
</script>

<div class="page">
  <div class="header">
    <h1>Einstellungen</h1>
    <p>Launcher-Konfiguration</p>
  </div>

  <div class="tabs">
    <button class="tab" class:active={activeTab === 'general'}   on:click={() => activeTab = 'general'}>ALLGEMEIN</button>
    <button class="tab" class:active={activeTab === 'background'} on:click={() => activeTab = 'background'}>HINTERGRUND</button>
    <button class="tab" class:active={activeTab === 'versions'}  on:click={() => activeTab = 'versions'}>VERSIONEN</button>
    <button class="tab" class:active={activeTab === 'advanced'}  on:click={() => activeTab = 'advanced'}>ERWEITERT</button>
  </div>

  {#if activeTab === 'general'}
    <div class="section">
      <div class="section-title">AKZENTFARBE</div>
      <p class="section-sub">Wähle deine bevorzugte Farbe für den Launcher</p>
      <div class="color-grid">
        {#each ACCENT_PRESETS as preset}
          <button
            class="color-swatch"
            class:selected={$accentColor === preset.color}
            style="background:{preset.color}"
            title={preset.label}
            on:click={() => accentColor.set(preset.color)}
          >
            {#if $accentColor === preset.color}<span class="check">✓</span>{/if}
          </button>
        {/each}
        <label class="color-swatch custom-swatch" title="Benutzerdefiniert">
          <span class="custom-label">Custom</span>
          <input type="color" bind:value={$accentColor} on:input={(e) => accentColor.set(e.currentTarget.value)} />
        </label>
      </div>
    </div>

    <div class="section">
      <div class="section-title">VERHALTEN</div>
      <label class="toggle-row">
        <input type="checkbox" bind:checked={config.auto_update} />
        <span>Automatische Updates</span>
      </label>
      <label class="toggle-row">
        <input type="checkbox" bind:checked={config.close_on_launch} />
        <span>Launcher beim Spielstart ausblenden</span>
      </label>
    </div>

    <button class="save-btn" on:click={save}>
      {saved ? '✓ Gespeichert' : 'Speichern'}
    </button>
  {/if}

  {#if activeTab === 'background'}
    <div class="section">
      <div class="section-title">HINTERGRUNDEFFEKT</div>
      <p class="section-sub">Wähle einen Hintergrundeffekt für den Launcher</p>
      <div class="bg-grid">
        {#each BG_PRESETS as preset}
          <button
            class="bg-card"
            class:selected={$backgroundEffect === preset.id}
            on:click={() => backgroundEffect.set(preset.id)}
          >
            <div class="bg-preview">
              <span class="bg-icon">{preset.icon}</span>
            </div>
            <div class="bg-label">{preset.label}</div>
          </button>
        {/each}
      </div>
    </div>
  {/if}

  {#if activeTab === 'versions'}
    <div class="section">
      <div class="section-title">MINECRAFT VERSIONEN</div>
      <p class="section-sub">Installiere oder entferne Minecraft-Versionen für deine Profile</p>
      <div class="ver-list">
        {#each MANAGED_VERSIONS as ver}
          {@const installed = installedVersions.includes(ver.id)}
          {@const installing = !!installingVersion[ver.id]}
          <div class="ver-card" class:installed>
            <div class="ver-info">
              <div class="ver-name">{ver.label}</div>
              <div class="ver-desc">{ver.desc}</div>
              {#if installing}
                <div class="ver-progress-wrap">
                  <div class="ver-progress-bar">
                    <div class="ver-progress-fill" style="width:{installPercent[ver.id] ?? 0}%"></div>
                  </div>
                  <span class="ver-progress-text">{installProgress[ver.id] ?? ''}</span>
                </div>
              {/if}
            </div>
            <div class="ver-actions">
              {#if installed}
                <span class="ver-badge installed">✓ Installiert</span>
              {:else if installing}
                <span class="ver-badge installing">{installPercent[ver.id] ?? 0}%</span>
              {:else}
                <button class="ver-install-btn" on:click={() => installVersion(ver.id)}>
                  ↓ Installieren
                </button>
              {/if}
            </div>
          </div>
        {/each}
      </div>
    </div>
  {/if}

  {#if activeTab === 'advanced'}
    <div class="section">
      <div class="section-title">VERZEICHNISSE</div>
      <label class="setting-label">Game-Ordner</label>
      <div class="row">
        <input class="setting-input" value={$gameDir} readonly />
        <button class="btn-sm" on:click={changeDir}>Ändern</button>
      </div>
      <label class="setting-label">Java-Pfad</label>
      <input class="setting-input" bind:value={javaPath} placeholder="Automatisch erkennen" />
    </div>

    <div class="section">
      <div class="section-title">LEISTUNG</div>
      <label class="setting-label">RAM: {config.ram} GB</label>
      <input type="range" class="slider" min="1" max="16" step="1" bind:value={config.ram} />
      <label class="setting-label">JVM-Argumente</label>
      <input class="setting-input" bind:value={config.jvm_args} placeholder="-XX:+UseG1GC" />
    </div>

    <button class="save-btn" on:click={save}>
      {saved ? '✓ Gespeichert' : 'Speichern'}
    </button>
  {/if}
</div>

<style>
  .page { display: flex; flex-direction: column; height: 100%; padding: 28px 32px; overflow-y: auto; gap: 0; }
  .header { margin-bottom: 16px; }
  .header h1 { font-size: 22px; font-weight: 700; color: #fff; margin-bottom: 4px; }
  .header p { font-size: 13px; color: #888; }

  .tabs { display: flex; gap: 2px; margin-bottom: 28px; border-bottom: 1px solid rgba(255,255,255,0.07); }
  .tab {
    padding: 9px 18px; border: none; background: none;
    color: #666; font-size: 11px; font-weight: 700; letter-spacing: 0.08em;
    cursor: pointer; border-bottom: 2px solid transparent;
    transition: color 0.15s, border-color 0.15s;
  }
  .tab.active { color: var(--accent); border-bottom-color: var(--accent); }
  .tab:hover:not(.active) { color: #aaa; }

  .section { margin-bottom: 28px; }
  .section-title { font-size: 11px; font-weight: 700; letter-spacing: 0.1em; color: var(--accent); text-transform: uppercase; margin-bottom: 6px; }
  .section-sub { font-size: 12px; color: #666; margin-bottom: 14px; }

  /* Accent color swatches */
  .color-grid { display: flex; flex-wrap: wrap; gap: 8px; }
  .color-swatch {
    width: 32px; height: 32px; border-radius: 8px;
    border: 2px solid transparent; cursor: pointer;
    display: flex; align-items: center; justify-content: center;
    transition: transform 0.15s, border-color 0.15s;
  }
  .color-swatch:hover { transform: scale(1.1); }
  .color-swatch.selected { border-color: #fff; transform: scale(1.1); }
  .check { color: #fff; font-size: 14px; font-weight: 700; text-shadow: 0 0 4px rgba(0,0,0,0.8); }

  .custom-swatch {
    background: linear-gradient(135deg, #ff0080, #ff8c00, #40e0d0);
    position: relative; overflow: hidden;
  }
  .custom-swatch input[type="color"] {
    position: absolute; inset: 0; opacity: 0; cursor: pointer; width: 100%; height: 100%;
  }
  .custom-label { font-size: 9px; color: #fff; font-weight: 700; pointer-events: none; z-index: 1; }

  /* Background presets */
  .bg-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
  .bg-card {
    background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.08);
    border-radius: 10px; padding: 0; overflow: hidden; cursor: pointer;
    transition: border-color 0.2s, transform 0.15s;
    aspect-ratio: 16/9;
    display: flex; flex-direction: column;
  }
  .bg-card:hover { border-color: rgba(255,255,255,0.2); transform: translateY(-2px); }
  .bg-card.selected { border-color: var(--accent); box-shadow: 0 0 12px color-mix(in srgb, var(--accent) 30%, transparent); }
  .bg-preview {
    flex: 1; display: flex; align-items: center; justify-content: center;
    background: rgba(0,0,0,0.3);
  }
  .bg-icon { font-size: 28px; opacity: 0.6; }
  .bg-label { padding: 6px 10px; font-size: 11px; font-weight: 600; color: #aaa; text-align: center; }

  /* Advanced settings */
  .setting-label { font-size: 12px; color: #888; margin-bottom: 6px; display: block; }
  .row { display: flex; gap: 8px; margin-bottom: 14px; }
  .setting-input {
    flex: 1; background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.1);
    border-radius: 8px; padding: 9px 14px; color: #fff; font-size: 13px; outline: none;
    transition: border-color 0.2s; margin-bottom: 14px;
  }
  .setting-input:focus { border-color: color-mix(in srgb, var(--accent) 50%, transparent); }
  .slider { width: 100%; accent-color: var(--accent); cursor: pointer; margin-bottom: 14px; }
  .toggle-row { display: flex; align-items: center; gap: 10px; cursor: pointer; margin-bottom: 10px; }
  .toggle-row input { accent-color: var(--accent); width: 16px; height: 16px; cursor: pointer; }
  .toggle-row span { font-size: 13px; color: #ccc; }
  .btn-sm {
    padding: 9px 16px; border-radius: 8px; flex-shrink: 0;
    background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.1);
    color: #ccc; font-size: 13px; cursor: pointer; transition: all 0.15s;
  }
  .btn-sm:hover { background: rgba(255,255,255,0.12); }
  .save-btn {
    padding: 12px 32px; border-radius: 10px; width: fit-content;
    background: var(--accent); border: none; color: #fff; font-size: 14px;
    font-weight: 700; cursor: pointer; transition: opacity 0.2s;
    margin-top: 8px;
  }
  .save-btn:hover { opacity: 0.85; }

  /* Version cards */
  .ver-list { display: flex; flex-direction: column; gap: 10px; }
  .ver-card {
    display: flex; align-items: center; justify-content: space-between;
    background: rgba(255,255,255,0.04);
    border: 1px solid rgba(255,255,255,0.08);
    border-radius: 12px; padding: 14px 18px;
    transition: border-color 0.2s;
  }
  .ver-card.installed { border-color: color-mix(in srgb, var(--accent) 35%, transparent); }
  .ver-info { flex: 1; }
  .ver-name { font-size: 14px; font-weight: 700; color: #e0e0e0; margin-bottom: 3px; }
  .ver-desc { font-size: 12px; color: #666; }
  .ver-progress-wrap { margin-top: 8px; display: flex; flex-direction: column; gap: 4px; }
  .ver-progress-bar { height: 4px; background: rgba(255,255,255,0.08); border-radius: 2px; overflow: hidden; }
  .ver-progress-fill { height: 100%; background: var(--accent); transition: width 0.3s; }
  .ver-progress-text { font-size: 11px; color: #888; }

  .ver-actions { flex-shrink: 0; margin-left: 16px; }
  .ver-badge {
    padding: 5px 12px; border-radius: 6px; font-size: 12px; font-weight: 600;
  }
  .ver-badge.installed { background: color-mix(in srgb, var(--accent) 15%, transparent); color: var(--accent); }
  .ver-badge.installing { background: rgba(255,190,0,0.12); color: #ffc107; }
  .ver-install-btn {
    padding: 7px 16px; border-radius: 8px; font-size: 12px; font-weight: 600;
    background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.12);
    color: #ccc; cursor: pointer; transition: all 0.15s;
  }
  .ver-install-btn:hover { background: rgba(255,255,255,0.12); color: #fff; border-color: var(--accent); }
</style>
