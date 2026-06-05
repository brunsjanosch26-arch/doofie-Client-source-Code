<script lang="ts">
  import { onMount, onDestroy } from 'svelte'
  import { invoke } from '@tauri-apps/api/core'
  import { listen } from '@tauri-apps/api/event'
  import { getCurrentWindow } from '@tauri-apps/api/window'
  import { gameDir } from '../stores/appStore'

  type Tab = 'modrinth' | 'local' | 'modloader' | 'modpacks'

  let activeTab: Tab = 'modrinth'

  // ── Modrinth search ─────────────────────────────────────────────────────────
  interface ModrinthHit {
    project_id: string
    title: string
    description: string
    author: string
    downloads: number
    follows: number
    icon_url: string | null
    categories: string[]
    versions: string[]
    date_modified: string
  }

  interface ModrinthDetail {
    id: string
    title: string
    description: string
    body: string
    author: string
    icon_url: string | null
    gallery: { url: string; title?: string; featured: boolean }[]
    downloads: number
    follows: number
    categories: string[]
    versions: string[]
    source_url?: string
    issues_url?: string
    wiki_url?: string
    discord_url?: string
    license: { id: string; name: string }
    date_updated: string
  }

  let query = ''
  let searchResults: ModrinthHit[] = []
  let searching = false
  let searchError = ''
  let loaderFilter = 'fabric'
  let searchTimeout: ReturnType<typeof setTimeout>

  // Detail view
  let selectedMod: ModrinthDetail | null = null
  let loadingDetail = false
  let selectedGalleryImg = ''

  // Install state
  let installing: Record<string, boolean> = {}
  let installMsg: Record<string, string> = {}

  async function searchModrinth() {
    searching = true; searchError = ''
    try {
      const q = query.trim() || ''
      const facets = `[["project_type:mod"],["categories:${loaderFilter}"]]`
      const url = `https://api.modrinth.com/v2/search?query=${encodeURIComponent(q)}&facets=${encodeURIComponent(facets)}&limit=20`
      const resp = await fetch(url)
      const data = await resp.json()
      searchResults = data.hits ?? []
    } catch { searchError = 'Suche fehlgeschlagen.' }
    searching = false
  }

  function onQueryInput() {
    clearTimeout(searchTimeout)
    searchTimeout = setTimeout(searchModrinth, 400)
  }

  async function openModDetail(id: string) {
    loadingDetail = true; selectedMod = null; selectedGalleryImg = ''
    try {
      const resp = await fetch(`https://api.modrinth.com/v2/project/${id}`)
      selectedMod = await resp.json()
      if (selectedMod?.gallery?.length) selectedGalleryImg = selectedMod.gallery[0].url
    } catch {}
    loadingDetail = false
  }

  function closeDetail() { selectedMod = null }

  async function installMod(id: string, title: string) {
    if (!$gameDir) return
    installing[id] = true; installMsg[id] = ''
    try {
      const verResp = await fetch(
        `https://api.modrinth.com/v2/project/${id}/version?loaders=${encodeURIComponent(JSON.stringify([loaderFilter]))}&limit=1`
      )
      const versions = await verResp.json()
      if (!versions?.length) throw new Error('Keine kompatible Version verfügbar')
      const file = versions[0].files?.find((f: any) => f.primary) ?? versions[0].files?.[0]
      if (!file) throw new Error('Keine Datei in Version')

      const bytes = new Uint8Array(await (await fetch(file.url)).arrayBuffer())
      const { writeFile, mkdir } = await import('@tauri-apps/plugin-fs')
      const modsDir = `${$gameDir}\\mods`
      await mkdir(modsDir, { recursive: true }).catch(() => {})
      await writeFile(`${modsDir}\\${file.filename}`, bytes)
      installMsg[id] = '✓ Installiert'
    } catch (e: any) {
      installMsg[id] = `✗ ${e?.message ?? e}`
    }
    installing[id] = false
    setTimeout(() => { delete installMsg[id]; installMsg = installMsg }, 5000)
  }

  // ── Local mods ─────────────────────────────────────────────────────────────
  let localMods: string[] = []
  let loadingLocal = false

  async function loadLocalMods() {
    loadingLocal = true
    localMods = await invoke<string[]>('get_mod_files', { gameDir: $gameDir }).catch(() => [])
    loadingLocal = false
  }

  // ── Modloader ──────────────────────────────────────────────────────────────
  let installedVersions: string[] = []
  let selectedMcVersion = ''
  let fabricVersions: { version: string; stable: boolean }[] = []
  let selectedFabric = ''
  let installingLoader = false
  let loaderProgress = ''
  let loaderPercent = 0
  let loaderDone = ''
  let loaderError = ''
  let loaderUnlisten: (() => void) | null = null

  async function loadInstalledVersions() {
    installedVersions = await invoke<string[]>('get_installed_versions', { gameDir: $gameDir }).catch(() => [])
    if (installedVersions.length > 0 && !selectedMcVersion)
      selectedMcVersion = installedVersions[installedVersions.length - 1]
  }

  async function loadFabricVersions() {
    if (!selectedMcVersion) return
    fabricVersions = []
    try {
      fabricVersions = await invoke<{ version: string; stable: boolean }[]>('get_fabric_versions', { mcVersion: selectedMcVersion })
      if (fabricVersions.length > 0) selectedFabric = fabricVersions[0].version
    } catch {}
  }

  async function installFabric() {
    if (!selectedMcVersion || !selectedFabric) return
    installingLoader = true; loaderDone = ''; loaderError = ''; loaderPercent = 0
    const u = await listen<{ step: string; percent: number }>('modloader_progress', e => {
      loaderProgress = e.payload.step; loaderPercent = e.payload.percent
    })
    loaderUnlisten = u
    try {
      const result = await invoke<string>('install_fabric', {
        mcVersion: selectedMcVersion, loaderVersion: selectedFabric, gameDir: $gameDir,
      })
      loaderDone = result
      await loadInstalledVersions()
    } catch (e: any) { loaderError = String(e) }
    u(); installingLoader = false
  }

  // ── Modpacks (Drag & Drop via Tauri event) ──────────────────────────────────
  let isDragging = false
  let packInstalling = false
  let packProgress = ''
  let packPercent = 0
  let packDone = ''
  let packError = ''
  let packUnlisten: (() => void) | null = null
  let tauriDropUnlisten: (() => void) | null = null
  let tauriDragUnlisten: (() => void) | null = null

  // Tauri intercepts OS file drops — listen to its events instead of HTML5
  async function registerTauriDrop() {
    const win = getCurrentWindow()

    tauriDragUnlisten = await win.onDragDropEvent((event) => {
      if (event.payload.type === 'over') {
        isDragging = true
      } else if (event.payload.type === 'leave') {
        isDragging = false
      } else if (event.payload.type === 'drop') {
        isDragging = false
        if (activeTab !== 'modpacks') return
        const paths: string[] = (event.payload as any).paths ?? []
        const mrpack = paths.find(p => p.endsWith('.mrpack'))
        if (mrpack) {
          installPackFromPath(mrpack)
        } else if (paths.length > 0) {
          packError = 'Nur .mrpack Dateien werden unterstützt.'
        }
      }
    })
  }

  async function browseModpack() {
    try {
      const { open } = await import('@tauri-apps/plugin-dialog')
      const path = await open({
        filters: [{ name: 'Modrinth Modpack', extensions: ['mrpack'] }],
        title: 'Modpack öffnen'
      })
      if (!path || typeof path !== 'string') return
      await installPackFromPath(path)
    } catch (e: any) {
      packError = String(e)
    }
  }

  async function installPackFromPath(path: string) {
    packInstalling = true; packDone = ''; packError = ''; packPercent = 0
    const u = await listen<{ step: string; percent: number }>('modpack_progress', e => {
      packProgress = e.payload.step; packPercent = e.payload.percent
    })
    packUnlisten = u
    try {
      const result = await invoke<string>('install_modpack', { filePath: path, gameDir: $gameDir })
      const info = JSON.parse(result)
      packDone = `✓ "${info.name}" v${info.version} installiert (MC ${info.mcVersion})`
      if (info.fabricVersion) {
        packDone += `. Fabric ${info.fabricVersion} noch benötigt — installiere es im "Mod Loader" Tab.`
      }
    } catch (e: any) { packError = String(e) }
    u(); packInstalling = false
  }

  function formatNum(n: number): string {
    if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M'
    if (n >= 1_000) return (n / 1_000).toFixed(0) + 'K'
    return String(n)
  }

  function formatDate(s: string): string {
    try { return new Date(s).toLocaleDateString('de-DE') } catch { return s }
  }

  $: if (activeTab === 'local') loadLocalMods()
  $: if (activeTab === 'modloader') loadInstalledVersions()
  $: if (selectedMcVersion && activeTab === 'modloader') loadFabricVersions()

  onMount(() => {
    searchModrinth()
    registerTauriDrop()
  })
  onDestroy(() => {
    loaderUnlisten?.()
    packUnlisten?.()
    tauriDropUnlisten?.()
    tauriDragUnlisten?.()
  })
</script>

<div class="page">
  <div class="header">
    <h1>Mods</h1>
    <p>Mods suchen, installieren und Modloader einrichten</p>
  </div>

  <div class="tabs">
    <button class="tab" class:active={activeTab==='modrinth'}  on:click={() => activeTab='modrinth'}>MODRINTH</button>
    <button class="tab" class:active={activeTab==='local'}     on:click={() => activeTab='local'}>INSTALLIERT</button>
    <button class="tab" class:active={activeTab==='modloader'} on:click={() => activeTab='modloader'}>MOD LOADER</button>
    <button class="tab" class:active={activeTab==='modpacks'}  on:click={() => activeTab='modpacks'}>MODPACKS</button>
  </div>

  <!-- ── MODRINTH TAB ─────────────────────────────────────────────────── -->
  {#if activeTab === 'modrinth'}
    {#if selectedMod}
      <!-- Full detail view -->
      <div class="detail-wrap">
        <button class="back-btn" on:click={closeDetail}>← Zurück</button>

        <div class="detail-top">
          {#if selectedMod.icon_url}
            <img class="detail-icon" src={selectedMod.icon_url} alt={selectedMod.title} />
          {/if}
          <div class="detail-meta">
            <div class="detail-title">{selectedMod.title}</div>
            <div class="detail-author">von {selectedMod.author}</div>
            <div class="detail-stats">
              <span>↓ {formatNum(selectedMod.downloads)} Downloads</span>
              <span>♥ {formatNum(selectedMod.follows)} Follows</span>
              <span>🕒 {formatDate(selectedMod.date_updated)}</span>
            </div>
            <div class="detail-tags">
              {#each selectedMod.categories as cat}
                <span class="tag">{cat}</span>
              {/each}
            </div>
          </div>
          <div class="detail-actions">
            {#if installMsg[selectedMod.id]}
              <div class="msg" class:err={installMsg[selectedMod.id].startsWith('✗')}>
                {installMsg[selectedMod.id]}
              </div>
            {:else}
              <button class="install-btn-lg"
                disabled={installing[selectedMod.id]}
                on:click={() => { if (selectedMod) installMod(selectedMod.id, selectedMod.title) }}>
                {installing[selectedMod.id] ? 'Installiere...' : '↓ INSTALLIEREN'}
              </button>
            {/if}
            {#if selectedMod.source_url}
              <a class="ext-link" href={selectedMod.source_url} target="_blank" rel="noreferrer">Source ↗</a>
            {/if}
            {#if selectedMod.issues_url}
              <a class="ext-link" href={selectedMod.issues_url} target="_blank" rel="noreferrer">Issues ↗</a>
            {/if}
          </div>
        </div>

        {#if selectedMod.gallery?.length}
          <div class="gallery-row">
            <div class="gallery-thumbs">
              {#each selectedMod.gallery as img}
                <button
                  class="thumb"
                  class:active={selectedGalleryImg === img.url}
                  on:click={() => selectedGalleryImg = img.url}
                >
                  <img src={img.url} alt={img.title ?? ''} />
                </button>
              {/each}
            </div>
            {#if selectedGalleryImg}
              <div class="gallery-main">
                <img src={selectedGalleryImg} alt="" />
              </div>
            {/if}
          </div>
        {/if}

        <div class="detail-description">
          <div class="section-label">BESCHREIBUNG</div>
          <div class="body-text">{selectedMod.body || selectedMod.description}</div>
        </div>

        {#if selectedMod.license}
          <div class="license-row">Lizenz: {selectedMod.license.name} ({selectedMod.license.id})</div>
        {/if}
      </div>

    {:else}
      <!-- Search list -->
      <div class="search-bar">
        <input class="search-input" bind:value={query} on:input={onQueryInput}
          placeholder="Mods suchen auf Modrinth..." />
        <select class="filter-select" bind:value={loaderFilter} on:change={searchModrinth}>
          <option value="fabric">Fabric</option>
          <option value="forge">Forge</option>
          <option value="quilt">Quilt</option>
          <option value="neoforge">NeoForge</option>
        </select>
      </div>

      {#if loadingDetail}
        <div class="empty">Lade Beschreibung...</div>
      {:else if searching}
        <div class="empty">Suche...</div>
      {:else if searchError}
        <div class="empty error">{searchError}</div>
      {:else if searchResults.length === 0}
        <div class="empty">Keine Ergebnisse{query ? ` für „${query}"` : ''}</div>
      {:else}
        <div class="mod-list">
          {#each searchResults as mod}
            <div class="mod-card" on:click={() => openModDetail(mod.project_id)} role="button" tabindex="0"
              on:keydown={(e) => e.key === 'Enter' && openModDetail(mod.project_id)}>
              <div class="mod-icon">
                {#if mod.icon_url}
                  <img src={mod.icon_url} alt={mod.title}
                    on:error={(e) => { e.currentTarget.style.display='none' }} />
                {:else}
                  <span class="icon-fb">⬡</span>
                {/if}
              </div>
              <div class="mod-info">
                <div class="mod-name">{mod.title} <span class="mod-author">by {mod.author}</span></div>
                <div class="mod-desc">{mod.description}</div>
                <div class="mod-tags">
                  {#each mod.categories.slice(0,4) as cat}
                    <span class="tag">{cat}</span>
                  {/each}
                </div>
              </div>
              <div class="mod-right" on:click|stopPropagation={() => {}}>
                <div class="dl-count">↓ {formatNum(mod.downloads)}</div>
                {#if installMsg[mod.project_id]}
                  <div class="msg" class:err={installMsg[mod.project_id].startsWith('✗')}>
                    {installMsg[mod.project_id]}
                  </div>
                {:else}
                  <button class="install-btn" disabled={installing[mod.project_id]}
                    on:click|stopPropagation={() => installMod(mod.project_id, mod.title)}>
                    {installing[mod.project_id] ? '...' : 'INSTALL'}
                  </button>
                {/if}
              </div>
            </div>
          {/each}
        </div>
      {/if}
    {/if}
  {/if}

  <!-- ── LOCAL TAB ────────────────────────────────────────────────────── -->
  {#if activeTab === 'local'}
    <div class="toolbar">
      <button class="btn-ghost" on:click={loadLocalMods}>↻ Aktualisieren</button>
      <span class="count">{localMods.length} Mod{localMods.length !== 1 ? 's' : ''}</span>
    </div>
    {#if loadingLocal}
      <div class="empty">Lade...</div>
    {:else if localMods.length === 0}
      <div class="empty">Keine Mods in <code>{$gameDir}\mods\</code></div>
    {:else}
      <div class="mod-list">
        {#each localMods as mod}
          <div class="mod-card static">
            <div class="mod-icon"><span class="icon-fb">🧩</span></div>
            <div class="mod-info">
              <div class="mod-name">{mod.replace(/\.jar(\.disabled)?$/, '')}</div>
              <div class="mod-desc">.jar{mod.endsWith('.disabled') ? ' · deaktiviert' : ''}</div>
            </div>
            <div class="mod-badge" class:off={mod.endsWith('.disabled')}>
              {mod.endsWith('.disabled') ? 'Deaktiviert' : 'Aktiv'}
            </div>
          </div>
        {/each}
      </div>
    {/if}
  {/if}

  <!-- ── MODLOADER TAB ─────────────────────────────────────────────────── -->
  {#if activeTab === 'modloader'}
    <div class="section-wrap">
      <div class="section-title">FABRIC INSTALLIEREN</div>
      <p class="section-sub">Installiere den Fabric-Modloader für eine bestehende Minecraft-Version.</p>
      <label class="field-label">Minecraft-Version</label>
      <select class="field-select" bind:value={selectedMcVersion} on:change={loadFabricVersions}>
        {#each installedVersions as v}<option value={v}>{v}</option>{/each}
      </select>
      <label class="field-label">Fabric-Loader-Version</label>
      <select class="field-select" bind:value={selectedFabric}>
        {#each fabricVersions as fv}
          <option value={fv.version}>{fv.version}{fv.stable ? ' (stabil)' : ''}</option>
        {/each}
        {#if fabricVersions.length === 0}<option value="">-- wählen --</option>{/if}
      </select>
      {#if installingLoader}
        <div class="prog-wrap"><div class="prog-bar" style="width:{loaderPercent}%"></div></div>
        <div class="prog-label">{loaderProgress}</div>
      {/if}
      {#if loaderDone}<div class="ok-msg">✓ {loaderDone}</div>{/if}
      {#if loaderError}<div class="err-msg">✗ {loaderError}</div>{/if}
      <button class="action-btn" disabled={installingLoader || !selectedFabric} on:click={installFabric}>
        {installingLoader ? 'Installiere...' : 'Fabric installieren'}
      </button>
    </div>
  {/if}

  <!-- ── MODPACKS TAB ──────────────────────────────────────────────────── -->
  {#if activeTab === 'modpacks'}
    <div class="section-wrap">
      <div class="section-title">MODRINTH MODPACK IMPORTIEREN</div>
      <p class="section-sub">
        Ziehe eine <code>.mrpack</code>-Datei in die Box oder wähle sie über den Button aus.
        Alle Mods werden automatisch heruntergeladen.
      </p>

      <div
        class="drop-zone"
        class:dragging={isDragging}
        role="region"
        aria-label="Modpack Drag & Drop"
      >
        {#if packInstalling}
          <div class="drop-inner">
            <div class="drop-spinner">⏳</div>
            <div class="drop-label">{packProgress}</div>
            <div class="prog-wrap wide"><div class="prog-bar" style="width:{packPercent}%"></div></div>
          </div>
        {:else}
          <div class="drop-inner">
            <span class="drop-icon">📦</span>
            <div class="drop-label">
              {isDragging ? 'Loslassen zum Installieren' : '.mrpack hier ablegen'}
            </div>
            <div class="drop-sub">oder</div>
            <button class="action-btn" on:click={browseModpack}>Datei auswählen</button>
          </div>
        {/if}
      </div>

      {#if packDone}<div class="ok-msg" style="margin-top:12px">{packDone}</div>{/if}
      {#if packError}<div class="err-msg" style="margin-top:12px">✗ {packError}</div>{/if}

      <div class="modpack-info">
        <div class="info-title">Unterstützte Formate</div>
        <div class="info-row"><span class="badge green">✓</span> Modrinth <code>.mrpack</code></div>
        <div class="info-row"><span class="badge gray">✗</span> CurseForge (API-Key benötigt – nicht unterstützt)</div>
      </div>
    </div>
  {/if}
</div>

<style>
  .page { display:flex; flex-direction:column; height:100%; padding:28px 32px; overflow:hidden; }
  .header { margin-bottom:12px; }
  .header h1 { font-size:22px; font-weight:700; color:#fff; margin-bottom:4px; }
  .header p { font-size:13px; color:#888; }

  .tabs { display:flex; gap:2px; margin-bottom:16px; border-bottom:1px solid rgba(255,255,255,0.07); flex-shrink:0; }
  .tab { padding:9px 16px; border:none; background:none; color:#666; font-size:11px; font-weight:700;
    letter-spacing:.08em; cursor:pointer; border-bottom:2px solid transparent; transition:color .15s,border-color .15s; }
  .tab.active { color:var(--accent); border-bottom-color:var(--accent); }
  .tab:hover:not(.active) { color:#aaa; }

  /* Search */
  .search-bar { display:flex; gap:8px; margin-bottom:12px; flex-shrink:0; }
  .search-input { flex:1; background:rgba(255,255,255,.05); border:1px solid rgba(255,255,255,.1);
    border-radius:8px; padding:9px 14px; color:#fff; font-size:13px; outline:none; transition:border-color .2s; }
  .search-input:focus { border-color:color-mix(in srgb,var(--accent) 50%,transparent); }
  .filter-select, .field-select { background:rgba(255,255,255,.06); border:1px solid rgba(255,255,255,.12);
    border-radius:8px; padding:9px 12px; color:#ccc; font-size:13px; cursor:pointer; outline:none; }
  .field-select { width:100%; margin-bottom:8px; }

  /* Mod list */
  .mod-list { display:flex; flex-direction:column; gap:8px; overflow-y:auto; flex:1; padding-bottom:8px; }
  .mod-card { display:flex; align-items:flex-start; gap:12px;
    background:rgba(255,255,255,.03); border:1px solid rgba(255,255,255,.07);
    border-radius:10px; padding:12px 14px; cursor:pointer;
    transition:background .15s,border-color .15s; }
  .mod-card:hover:not(.static) { background:rgba(255,255,255,.07); border-color:rgba(255,255,255,.14); }
  .mod-card.static { cursor:default; }
  .mod-icon { width:44px; height:44px; border-radius:8px; overflow:hidden; flex-shrink:0;
    display:flex; align-items:center; justify-content:center; background:rgba(255,255,255,.05); }
  .mod-icon img { width:100%; height:100%; object-fit:cover; }
  .icon-fb { font-size:22px; }
  .mod-info { flex:1; min-width:0; }
  .mod-name { font-size:13px; font-weight:600; color:#e0e0e0; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .mod-author { font-weight:400; color:#666; font-size:12px; }
  .mod-desc { font-size:12px; color:#777; margin-top:3px; overflow:hidden; text-overflow:ellipsis;
    display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; }
  .mod-tags { display:flex; gap:4px; margin-top:6px; flex-wrap:wrap; }
  .tag { font-size:10px; padding:2px 7px; border-radius:4px; background:rgba(255,255,255,.06); color:#888; }
  .mod-right { display:flex; flex-direction:column; align-items:flex-end; gap:8px; flex-shrink:0; }
  .dl-count { font-size:11px; color:#555; }
  .install-btn { padding:6px 14px; border-radius:6px; font-size:11px; font-weight:700; letter-spacing:.05em;
    background:color-mix(in srgb,var(--accent) 18%,transparent);
    border:1px solid color-mix(in srgb,var(--accent) 40%,transparent);
    color:var(--accent); cursor:pointer; transition:background .15s; }
  .install-btn:hover:not(:disabled) { background:color-mix(in srgb,var(--accent) 30%,transparent); }
  .install-btn:disabled { opacity:.5; cursor:default; }
  .mod-badge { font-size:10px; padding:3px 8px; border-radius:4px; background:rgba(74,222,128,.12); color:#4ade80; }
  .mod-badge.off { background:rgba(255,255,255,.05); color:#666; }

  /* Detail view */
  .detail-wrap { display:flex; flex-direction:column; gap:18px; overflow-y:auto; flex:1; padding-bottom:12px; }
  .back-btn { background:none; border:none; color:#888; font-size:13px; cursor:pointer;
    width:fit-content; padding:0; transition:color .15s; }
  .back-btn:hover { color:#fff; }
  .detail-top { display:flex; align-items:flex-start; gap:16px; }
  .detail-icon { width:72px; height:72px; border-radius:12px; object-fit:cover; flex-shrink:0; }
  .detail-meta { flex:1; }
  .detail-title { font-size:20px; font-weight:700; color:#fff; margin-bottom:4px; }
  .detail-author { font-size:13px; color:#888; margin-bottom:8px; }
  .detail-stats { display:flex; gap:16px; font-size:12px; color:#666; margin-bottom:8px; flex-wrap:wrap; }
  .detail-tags { display:flex; gap:4px; flex-wrap:wrap; }
  .detail-actions { display:flex; flex-direction:column; align-items:flex-end; gap:8px; flex-shrink:0; }
  .install-btn-lg { padding:11px 22px; border-radius:9px; font-size:13px; font-weight:700;
    background:var(--accent); border:none; color:#fff; cursor:pointer; transition:opacity .15s; white-space:nowrap; }
  .install-btn-lg:disabled { opacity:.5; cursor:default; }
  .install-btn-lg:not(:disabled):hover { opacity:.85; }
  .ext-link { font-size:11px; color:var(--accent); text-decoration:none; }
  .ext-link:hover { text-decoration:underline; }

  .gallery-row { display:flex; gap:12px; }
  .gallery-thumbs { display:flex; flex-direction:column; gap:6px; max-height:220px; overflow-y:auto; }
  .thumb { width:72px; height:48px; border-radius:6px; overflow:hidden; border:2px solid transparent;
    cursor:pointer; padding:0; background:none; flex-shrink:0; transition:border-color .15s; }
  .thumb.active { border-color:var(--accent); }
  .thumb img { width:100%; height:100%; object-fit:cover; }
  .gallery-main { flex:1; border-radius:10px; overflow:hidden; max-height:220px; }
  .gallery-main img { width:100%; height:100%; object-fit:cover; }

  .section-label { font-size:11px; font-weight:700; letter-spacing:.1em; color:var(--accent);
    text-transform:uppercase; margin-bottom:10px; }
  .detail-description { }
  .body-text { font-size:13px; color:#aaa; line-height:1.7; white-space:pre-wrap; word-break:break-word; }
  .license-row { font-size:11px; color:#555; margin-top:4px; }

  /* Toolbar */
  .toolbar { display:flex; align-items:center; gap:12px; margin-bottom:12px; flex-shrink:0; }
  .btn-ghost { padding:7px 14px; border-radius:7px; background:rgba(255,255,255,.05);
    border:1px solid rgba(255,255,255,.1); color:#888; font-size:12px; cursor:pointer; transition:all .2s; }
  .btn-ghost:hover { color:#ccc; }
  .count { font-size:12px; color:#555; }

  /* Modloader / Modpacks sections */
  .section-wrap { display:flex; flex-direction:column; gap:10px; max-width:500px; overflow-y:auto; flex:1; padding-bottom:12px; }
  .section-title { font-size:11px; font-weight:700; letter-spacing:.1em; color:var(--accent); text-transform:uppercase; }
  .section-sub { font-size:12px; color:#666; margin-bottom:4px; }
  .field-label { font-size:12px; color:#888; }
  .prog-wrap { width:100%; height:6px; background:rgba(255,255,255,.08); border-radius:3px; overflow:hidden; }
  .prog-wrap.wide { width:220px; }
  .prog-bar { height:100%; background:var(--accent); transition:width .3s; border-radius:3px; }
  .prog-label { font-size:11px; color:#888; }
  .ok-msg { font-size:12px; color:#4ade80; padding:8px 12px; background:rgba(74,222,128,.08); border-radius:6px; line-height:1.5; }
  .err-msg { font-size:12px; color:#f87171; padding:8px 12px; background:rgba(248,113,113,.08); border-radius:6px; }
  .action-btn { padding:11px 28px; border-radius:9px; width:fit-content; background:var(--accent);
    border:none; color:#fff; font-size:13px; font-weight:700; cursor:pointer; transition:opacity .2s; margin-top:4px; }
  .action-btn:disabled { opacity:.5; cursor:default; }
  .action-btn:not(:disabled):hover { opacity:.85; }

  /* Drop zone */
  .drop-zone { border:2px dashed rgba(255,255,255,.12); border-radius:14px; padding:32px;
    text-align:center; transition:border-color .2s,background .2s; margin:8px 0; }
  .drop-zone.dragging { border-color:var(--accent); background:color-mix(in srgb,var(--accent) 8%,transparent); }
  .drop-inner { display:flex; flex-direction:column; align-items:center; gap:10px; }
  .drop-icon { font-size:36px; }
  .drop-label { font-size:14px; font-weight:600; color:#ccc; }
  .drop-sub { font-size:12px; color:#555; }
  .drop-spinner { font-size:28px; animation:spin 1.5s linear infinite; }
  @keyframes spin { to { transform:rotate(360deg); } }

  .modpack-info { margin-top:16px; padding:14px; background:rgba(255,255,255,.03);
    border:1px solid rgba(255,255,255,.07); border-radius:10px; display:flex; flex-direction:column; gap:8px; }
  .info-title { font-size:11px; font-weight:700; letter-spacing:.08em; color:#666; text-transform:uppercase; margin-bottom:4px; }
  .info-row { display:flex; align-items:center; gap:8px; font-size:12px; color:#888; }
  .badge { font-size:11px; padding:2px 6px; border-radius:4px; font-weight:700; }
  .badge.green { background:rgba(74,222,128,.15); color:#4ade80; }
  .badge.gray { background:rgba(255,255,255,.06); color:#666; }

  .msg { font-size:11px; color:#4ade80; max-width:90px; text-align:right; }
  .msg.err { color:#f87171; }
  .empty { font-size:13px; color:#555; text-align:center; padding:40px; }
  .empty code { color:var(--accent); }
  .empty.error { color:#f87171; }
</style>
