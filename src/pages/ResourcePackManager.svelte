<script lang="ts">
  import { onMount } from 'svelte'
  import { invoke } from '@tauri-apps/api/core'
  import { gameDir } from '../stores/appStore'

  type Tab = 'modrinth' | 'local'
  let activeTab: Tab = 'modrinth'

  // ── Modrinth Resource Packs ─────────────────────────────────────────────────
  interface ModrinthPack {
    project_id: string
    title: string
    description: string
    author: string
    downloads: number
    icon_url: string | null
    gallery: string[]
    categories: string[]
    versions: string[]
  }

  interface PackDetail {
    id: string
    title: string
    description: string
    body: string
    author: string
    icon_url: string | null
    gallery: { url: string; title?: string; featured: boolean }[]
    downloads: number
    categories: string[]
    versions: string[]
  }

  let query = ''
  let searchResults: ModrinthPack[] = []
  let searching = false
  let selectedPack: PackDetail | null = null
  let loadingDetail = false
  let installing: Record<string, boolean> = {}
  let installSuccess: Record<string, string> = {}
  let searchTimeout: ReturnType<typeof setTimeout>

  async function searchModrinth() {
    searching = true
    try {
      const q = query.trim() || 'resource pack'
      const facets = `[["project_type:resourcepack"]]`
      const url = `https://api.modrinth.com/v2/search?query=${encodeURIComponent(q)}&facets=${encodeURIComponent(facets)}&limit=20`
      const resp = await fetch(url)
      const data = await resp.json()
      searchResults = data.hits ?? []
    } catch {}
    searching = false
  }

  function onQueryInput() {
    clearTimeout(searchTimeout)
    searchTimeout = setTimeout(searchModrinth, 400)
  }

  async function openDetail(pack: ModrinthPack) {
    loadingDetail = true; selectedPack = null
    try {
      const resp = await fetch(`https://api.modrinth.com/v2/project/${pack.project_id}`)
      selectedPack = await resp.json()
    } catch {}
    loadingDetail = false
  }

  function closeDetail() { selectedPack = null }

  async function installPack(pack: ModrinthPack | PackDetail) {
    const id = 'id' in pack ? pack.id : pack.project_id
    if (!$gameDir) return
    installing[id] = true
    try {
      const verResp = await fetch(`https://api.modrinth.com/v2/project/${id}/version?limit=1`)
      const versions = await verResp.json()
      if (!versions?.length) throw new Error('Keine Version verfügbar')
      const file = versions[0].files?.find((f: any) => f.primary) ?? versions[0].files?.[0]
      if (!file) throw new Error('Keine Datei')

      const fileResp = await fetch(file.url)
      const bytes = await fileResp.arrayBuffer()

      const { writeFile, mkdir } = await import('@tauri-apps/plugin-fs')
      const rpsDir = `${$gameDir}\\resourcepacks`
      await mkdir(rpsDir, { recursive: true }).catch(() => {})
      await writeFile(`${rpsDir}\\${file.filename}`, new Uint8Array(bytes))
      installSuccess[id] = 'Installiert!'
    } catch (e: any) {
      installSuccess[id] = `Fehler: ${e?.message ?? e}`
    }
    installing[id] = false
    setTimeout(() => { delete installSuccess[id]; installSuccess = installSuccess }, 4000)
  }

  function formatDownloads(n: number): string {
    if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M'
    if (n >= 1_000) return (n / 1_000).toFixed(0) + 'K'
    return String(n)
  }

  // ── Local Resource Packs ────────────────────────────────────────────────────
  let localPacks: string[] = []
  let loadingLocal = false

  async function loadLocalPacks() {
    loadingLocal = true
    try {
      const { readDir } = await import('@tauri-apps/plugin-fs')
      const entries = await readDir(`${$gameDir}\\resourcepacks`)
      localPacks = entries.map((e: any) => e.name ?? e.path).filter(Boolean)
    } catch {
      localPacks = []
    }
    loadingLocal = false
  }

  $: if (activeTab === 'local') loadLocalPacks()

  onMount(searchModrinth)
</script>

<div class="page">
  <div class="header">
    <h1>Ressourcenpakete</h1>
    <p>Ressourcenpakete suchen, installieren und verwalten</p>
  </div>

  <div class="tabs">
    <button class="tab" class:active={activeTab === 'modrinth'} on:click={() => activeTab = 'modrinth'}>MODRINTH</button>
    <button class="tab" class:active={activeTab === 'local'}    on:click={() => activeTab = 'local'}>INSTALLIERT</button>
  </div>

  {#if activeTab === 'modrinth'}
    <div class="search-bar">
      <input
        class="search-input"
        bind:value={query}
        on:input={onQueryInput}
        placeholder="Ressourcenpakete suchen auf Modrinth..."
      />
    </div>

    {#if selectedPack}
      <!-- Detail View -->
      <div class="detail-view">
        <button class="back-btn" on:click={closeDetail}>← Zurück</button>
        <div class="detail-header">
          {#if selectedPack.icon_url}
            <img class="detail-icon" src={selectedPack.icon_url} alt={selectedPack.title} />
          {/if}
          <div>
            <div class="detail-title">{selectedPack.title}</div>
            <div class="detail-author">von {selectedPack.author} · ↓ {formatDownloads(selectedPack.downloads)}</div>
            <div class="detail-tags">
              {#each selectedPack.categories.slice(0,4) as cat}
                <span class="tag">{cat}</span>
              {/each}
            </div>
          </div>
          <div class="detail-actions">
            {#if installSuccess[selectedPack.id]}
              <div class="install-status" class:err={installSuccess[selectedPack.id].startsWith('Fehler')}>
                {installSuccess[selectedPack.id]}
              </div>
            {:else}
              <button class="install-btn" disabled={installing[selectedPack.id]} on:click={() => { if (selectedPack) installPack(selectedPack) }}>
                {installing[selectedPack.id] ? 'Installiere...' : 'INSTALLIEREN'}
              </button>
            {/if}
          </div>
        </div>

        {#if selectedPack.gallery?.length > 0}
          <div class="gallery">
            {#each selectedPack.gallery.slice(0,6) as img}
              <img class="gallery-img" src={img.url} alt={img.title ?? ''} />
            {/each}
          </div>
        {/if}

        <div class="section-label">BESCHREIBUNG</div>
        <div class="body-text">{selectedPack.body || selectedPack.description}</div>

        {#if selectedPack.downloads}
          <div class="detail-stats">
            <span>↓ {formatDownloads(selectedPack.downloads)} Downloads</span>
          </div>
        {/if}

        {#if selectedPack.categories?.length}
          <div class="detail-tags">
            {#each selectedPack.categories as cat}<span class="tag">{cat}</span>{/each}
          </div>
        {/if}
      </div>

    {:else if searching}
      <div class="empty">Suche...</div>
    {:else}
      <div class="pack-grid">
        {#each searchResults as pack}
          <button class="pack-card" on:click={() => openDetail(pack)}>
            <div class="pack-img">
              {#if pack.icon_url}
                <img src={pack.icon_url} alt={pack.title} on:error={(e) => { e.currentTarget.style.display='none' }} />
              {:else}
                <span class="pack-fallback">🖼</span>
              {/if}
            </div>
            <div class="pack-body">
              <div class="pack-name">{pack.title}</div>
              <div class="pack-desc">{pack.description}</div>
              <div class="pack-footer">
                <span class="dl-count">↓ {formatDownloads(pack.downloads)}</span>
                {#if installSuccess[pack.project_id]}
                  <span class="install-status" class:err={installSuccess[pack.project_id].startsWith('Fehler')}>
                    {installSuccess[pack.project_id]}
                  </span>
                {:else}
                  <button
                    class="install-btn-sm"
                    disabled={installing[pack.project_id]}
                    on:click|stopPropagation={() => installPack(pack)}
                  >
                    {installing[pack.project_id] ? '...' : '↓'}
                  </button>
                {/if}
              </div>
            </div>
          </button>
        {/each}
      </div>
    {/if}
  {/if}

  {#if activeTab === 'local'}
    <div class="toolbar">
      <button class="btn-ghost" on:click={loadLocalPacks}>↻ Aktualisieren</button>
      <span class="count">{localPacks.length} Paket{localPacks.length !== 1 ? 'e' : ''}</span>
    </div>
    {#if loadingLocal}
      <div class="empty">Lade...</div>
    {:else if localPacks.length === 0}
      <div class="empty">Keine Ressourcenpakete in <code>{$gameDir}\resourcepacks\</code></div>
    {:else}
      <div class="local-list">
        {#each localPacks as pack}
          <div class="local-card">
            <span class="local-icon">🗂</span>
            <span class="local-name">{pack}</span>
          </div>
        {/each}
      </div>
    {/if}
  {/if}
</div>

<style>
  .page { display: flex; flex-direction: column; height: 100%; padding: 28px 32px; overflow: hidden; gap: 0; }
  .header { margin-bottom: 12px; }
  .header h1 { font-size: 22px; font-weight: 700; color: #fff; margin-bottom: 4px; }
  .header p { font-size: 13px; color: #888; }

  .tabs { display: flex; gap: 2px; margin-bottom: 16px; border-bottom: 1px solid rgba(255,255,255,0.07); }
  .tab {
    padding: 9px 18px; border: none; background: none;
    color: #666; font-size: 11px; font-weight: 700; letter-spacing: 0.08em;
    cursor: pointer; border-bottom: 2px solid transparent; transition: color 0.15s, border-color 0.15s;
  }
  .tab.active { color: var(--accent); border-bottom-color: var(--accent); }
  .tab:hover:not(.active) { color: #aaa; }

  .search-bar { margin-bottom: 16px; }
  .search-input {
    width: 100%; background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.1);
    border-radius: 8px; padding: 10px 14px; color: #fff; font-size: 13px; outline: none;
    transition: border-color 0.2s;
  }
  .search-input:focus { border-color: color-mix(in srgb, var(--accent) 50%, transparent); }

  /* Grid layout for resource packs */
  .pack-grid {
    display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 12px; overflow-y: auto; flex: 1; padding-bottom: 8px;
  }
  .pack-card {
    background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.08);
    border-radius: 10px; overflow: hidden; cursor: pointer; text-align: left;
    transition: border-color 0.15s, transform 0.15s; display: flex; flex-direction: column;
    padding: 0;
  }
  .pack-card:hover { border-color: rgba(255,255,255,0.18); transform: translateY(-2px); }

  .pack-img {
    width: 100%; aspect-ratio: 16/9; background: rgba(0,0,0,0.3);
    display: flex; align-items: center; justify-content: center; overflow: hidden;
  }
  .pack-img img { width: 100%; height: 100%; object-fit: cover; }
  .pack-fallback { font-size: 32px; opacity: 0.4; }

  .pack-body { padding: 10px 12px; display: flex; flex-direction: column; gap: 4px; flex: 1; }
  .pack-name { font-size: 13px; font-weight: 600; color: #e0e0e0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .pack-desc { font-size: 11px; color: #666; overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; flex: 1; }
  .pack-footer { display: flex; align-items: center; justify-content: space-between; margin-top: 6px; }
  .dl-count { font-size: 11px; color: #555; }

  .install-btn-sm {
    width: 28px; height: 28px; border-radius: 6px; font-size: 14px;
    background: color-mix(in srgb, var(--accent) 18%, transparent);
    border: 1px solid color-mix(in srgb, var(--accent) 40%, transparent);
    color: var(--accent); cursor: pointer; display: flex; align-items: center; justify-content: center;
  }
  .install-btn-sm:disabled { opacity: 0.5; cursor: default; }

  /* Detail view */
  .detail-view { display: flex; flex-direction: column; gap: 16px; overflow-y: auto; flex: 1; }
  .back-btn {
    background: none; border: none; color: #888; font-size: 13px; cursor: pointer;
    display: inline-flex; align-items: center; gap: 6px; padding: 0; width: fit-content;
    transition: color 0.15s;
  }
  .back-btn:hover { color: #fff; }
  .detail-header { display: flex; align-items: flex-start; gap: 14px; }
  .detail-icon { width: 64px; height: 64px; border-radius: 10px; object-fit: cover; flex-shrink: 0; }
  .detail-title { font-size: 18px; font-weight: 700; color: #fff; }
  .detail-author { font-size: 12px; color: #777; margin: 4px 0; }
  .detail-tags { display: flex; gap: 4px; flex-wrap: wrap; }
  .tag { font-size: 10px; padding: 2px 7px; border-radius: 4px; background: rgba(255,255,255,0.06); color: #888; }
  .detail-actions { margin-left: auto; }
  .install-btn {
    padding: 10px 22px; border-radius: 8px; font-size: 13px; font-weight: 700;
    background: var(--accent); border: none; color: #fff; cursor: pointer; transition: opacity 0.15s;
  }
  .install-btn:disabled { opacity: 0.5; cursor: default; }
  .install-btn:not(:disabled):hover { opacity: 0.85; }

  .gallery {
    display: flex; gap: 10px; overflow-x: auto; padding-bottom: 4px;
  }
  .gallery-img { height: 140px; width: auto; border-radius: 8px; object-fit: cover; flex-shrink: 0; }
  .section-label { font-size:11px; font-weight:700; letter-spacing:.1em; color:var(--accent); text-transform:uppercase; margin-bottom:10px; }
  .body-text { font-size:13px; color:#aaa; line-height:1.7; white-space:pre-wrap; word-break:break-word; }
  .detail-stats { display:flex; gap:16px; font-size:12px; color:#666; }
  .detail-tags { display:flex; gap:4px; flex-wrap:wrap; }

  .install-status { font-size: 12px; color: #4ade80; }
  .install-status.err { color: #f87171; }

  /* Local packs */
  .toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
  .btn-ghost {
    padding: 7px 14px; border-radius: 7px;
    background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.1);
    color: #888; font-size: 12px; cursor: pointer; transition: all 0.2s;
  }
  .btn-ghost:hover { color: #ccc; }
  .count { font-size: 12px; color: #555; }

  .local-list { display: flex; flex-direction: column; gap: 8px; overflow-y: auto; }
  .local-card {
    display: flex; align-items: center; gap: 12px;
    background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.07);
    border-radius: 9px; padding: 10px 14px;
  }
  .local-icon { font-size: 20px; flex-shrink: 0; }
  .local-name { font-size: 13px; color: #ccc; }

  .empty { font-size: 13px; color: #555; text-align: center; padding: 40px; }
  .empty code { color: var(--accent); }
</style>
