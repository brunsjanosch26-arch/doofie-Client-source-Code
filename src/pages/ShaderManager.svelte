<script lang="ts">
  import { onMount } from 'svelte'
  import { invoke } from '@tauri-apps/api/core'
  import { gameDir } from '../stores/appStore'

  type Tab = 'modrinth' | 'local'
  let activeTab: Tab = 'modrinth'

  // ── Modrinth Shaders ────────────────────────────────────────────────────────
  interface ShaderHit {
    project_id: string
    title: string
    description: string
    author: string
    downloads: number
    follows: number
    icon_url: string | null
    categories: string[]
    date_modified: string
  }

  interface ShaderDetail {
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
    license: { id: string; name: string }
    date_updated: string
    source_url?: string
  }

  let query = ''
  let results: ShaderHit[] = []
  let searching = false
  let searchTimeout: ReturnType<typeof setTimeout>
  let selectedShader: ShaderDetail | null = null
  let loadingDetail = false
  let selectedImg = ''
  let installing: Record<string, boolean> = {}
  let installMsg: Record<string, string> = {}

  async function search() {
    searching = true
    try {
      const q = query.trim() || ''
      const facets = `[["project_type:shader"]]`
      const url = `https://api.modrinth.com/v2/search?query=${encodeURIComponent(q)}&facets=${encodeURIComponent(facets)}&limit=20`
      results = (await (await fetch(url)).json()).hits ?? []
    } catch {}
    searching = false
  }

  function onInput() {
    clearTimeout(searchTimeout)
    searchTimeout = setTimeout(search, 400)
  }

  async function openDetail(id: string) {
    loadingDetail = true; selectedShader = null; selectedImg = ''
    try {
      selectedShader = await (await fetch(`https://api.modrinth.com/v2/project/${id}`)).json()
      if (selectedShader?.gallery?.length) selectedImg = selectedShader.gallery[0].url
    } catch {}
    loadingDetail = false
  }

  async function installShader(id: string) {
    if (!$gameDir) return
    installing[id] = true; installMsg[id] = ''
    try {
      const versions = await (await fetch(`https://api.modrinth.com/v2/project/${id}/version?limit=1`)).json()
      if (!versions?.length) throw new Error('Keine Version verfügbar')
      const file = versions[0].files?.find((f: any) => f.primary) ?? versions[0].files?.[0]
      if (!file) throw new Error('Keine Datei')
      const bytes = new Uint8Array(await (await fetch(file.url)).arrayBuffer())
      const { writeFile, mkdir } = await import('@tauri-apps/plugin-fs')
      const dir = `${$gameDir}\\shaderpacks`
      await mkdir(dir, { recursive: true }).catch(() => {})
      await writeFile(`${dir}\\${file.filename}`, bytes)
      installMsg[id] = '✓ Installiert'
    } catch (e: any) { installMsg[id] = `✗ ${e?.message ?? e}` }
    installing[id] = false
    setTimeout(() => { delete installMsg[id]; installMsg = installMsg }, 5000)
  }

  // ── Local shaders ───────────────────────────────────────────────────────────
  interface Shader { name: string }
  let localShaders: Shader[] = []
  let loadingLocal = false

  async function loadLocal() {
    loadingLocal = true
    localShaders = await invoke<Shader[]>('get_shaders', { gameDir: $gameDir }).catch(() => [])
    loadingLocal = false
  }

  async function deleteShader(name: string) {
    await invoke('delete_shader', { name, gameDir: $gameDir }).catch(() => {})
    await loadLocal()
  }

  function fmt(n: number) {
    if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M'
    if (n >= 1_000) return (n / 1_000).toFixed(0) + 'K'
    return String(n)
  }

  function fmtDate(s: string) {
    try { return new Date(s).toLocaleDateString('de-DE') } catch { return s }
  }

  $: if (activeTab === 'local') loadLocal()
  onMount(search)
</script>

<div class="page">
  <div class="header">
    <h1>Shader</h1>
    <p>Shader-Packs suchen, installieren und verwalten</p>
  </div>

  <div class="tabs">
    <button class="tab" class:active={activeTab==='modrinth'} on:click={() => { activeTab='modrinth'; selectedShader=null }}>MODRINTH</button>
    <button class="tab" class:active={activeTab==='local'}    on:click={() => activeTab='local'}>INSTALLIERT</button>
  </div>

  <!-- ── MODRINTH ─────────────────────────────────────────────────────── -->
  {#if activeTab === 'modrinth'}
    {#if selectedShader}
      <div class="detail-wrap">
        <button class="back-btn" on:click={() => selectedShader = null}>← Zurück</button>

        <div class="detail-top">
          {#if selectedShader.icon_url}
            <img class="detail-icon" src={selectedShader.icon_url} alt={selectedShader.title} />
          {:else}
            <div class="detail-icon-fb">✦</div>
          {/if}
          <div class="detail-meta">
            <div class="detail-title">{selectedShader.title}</div>
            <div class="detail-author">von {selectedShader.author}</div>
            <div class="detail-stats">
              <span>↓ {fmt(selectedShader.downloads)}</span>
              <span>♥ {fmt(selectedShader.follows)}</span>
              <span>🕒 {fmtDate(selectedShader.date_updated)}</span>
            </div>
            <div class="detail-tags">
              {#each selectedShader.categories as cat}<span class="tag">{cat}</span>{/each}
            </div>
          </div>
          <div class="detail-actions">
            {#if installMsg[selectedShader.id]}
              <div class="msg" class:err={installMsg[selectedShader.id].startsWith('✗')}>
                {installMsg[selectedShader.id]}
              </div>
            {:else}
              <button class="install-btn-lg" disabled={installing[selectedShader.id]}
                on:click={() => { if (selectedShader) installShader(selectedShader.id) }}>
                {installing[selectedShader.id] ? 'Installiere...' : '↓ INSTALLIEREN'}
              </button>
            {/if}
            {#if selectedShader.source_url}
              <a class="ext-link" href={selectedShader.source_url} target="_blank" rel="noreferrer">Source ↗</a>
            {/if}
          </div>
        </div>

        {#if selectedShader.gallery?.length}
          <div class="gallery-row">
            <div class="gallery-thumbs">
              {#each selectedShader.gallery as img}
                <button class="thumb" class:active={selectedImg===img.url}
                  on:click={() => selectedImg=img.url}>
                  <img src={img.url} alt={img.title??''} />
                </button>
              {/each}
            </div>
            {#if selectedImg}
              <div class="gallery-main"><img src={selectedImg} alt="" /></div>
            {/if}
          </div>
        {/if}

        <div class="section-label">BESCHREIBUNG</div>
        <div class="body-text">{selectedShader.body || selectedShader.description}</div>

        {#if selectedShader.license}
          <div class="license-row">Lizenz: {selectedShader.license.name} ({selectedShader.license.id})</div>
        {/if}
      </div>

    {:else}
      <div class="search-bar">
        <input class="search-input" bind:value={query} on:input={onInput}
          placeholder="Shader suchen auf Modrinth..." />
      </div>

      {#if loadingDetail}
        <div class="empty">Lade Beschreibung...</div>
      {:else if searching}
        <div class="empty">Suche...</div>
      {:else if results.length === 0}
        <div class="empty">Keine Shader{query ? ` für „${query}"` : ' gefunden'}</div>
      {:else}
        <div class="shader-list">
          {#each results as s}
            <div class="shader-card" on:click={() => openDetail(s.project_id)}
              role="button" tabindex="0" on:keydown={(e) => e.key==='Enter' && openDetail(s.project_id)}>
              <div class="shader-icon-wrap">
                {#if s.icon_url}
                  <img src={s.icon_url} alt={s.title}
                    on:error={(e) => { e.currentTarget.style.display='none' }} />
                {:else}
                  <span class="icon-fb">✦</span>
                {/if}
              </div>
              <div class="shader-info">
                <div class="shader-name">{s.title} <span class="shader-author">by {s.author}</span></div>
                <div class="shader-desc">{s.description}</div>
                <div class="shader-tags">
                  {#each s.categories.slice(0,4) as cat}<span class="tag">{cat}</span>{/each}
                </div>
              </div>
              <div class="shader-right" on:click|stopPropagation={() => {}}>
                <div class="dl-count">↓ {fmt(s.downloads)}</div>
                {#if installMsg[s.project_id]}
                  <div class="msg" class:err={installMsg[s.project_id].startsWith('✗')}>
                    {installMsg[s.project_id]}
                  </div>
                {:else}
                  <button class="install-btn" disabled={installing[s.project_id]}
                    on:click|stopPropagation={() => installShader(s.project_id)}>
                    {installing[s.project_id] ? '...' : 'INSTALL'}
                  </button>
                {/if}
              </div>
            </div>
          {/each}
        </div>
      {/if}
    {/if}
  {/if}

  <!-- ── LOCAL ────────────────────────────────────────────────────────── -->
  {#if activeTab === 'local'}
    <div class="toolbar">
      <button class="btn-ghost" on:click={loadLocal}>↻ Aktualisieren</button>
      <span class="count">{localShaders.length} Shader</span>
    </div>
    {#if loadingLocal}
      <div class="empty">Lade...</div>
    {:else if localShaders.length === 0}
      <div class="empty">Keine Shader in <code>{$gameDir}\shaderpacks\</code></div>
    {:else}
      <div class="shader-list">
        {#each localShaders as s}
          <div class="shader-card static">
            <div class="shader-icon-wrap"><span class="icon-fb">✦</span></div>
            <div class="shader-info">
              <div class="shader-name">{s.name}</div>
              <div class="shader-desc">.zip / .txt Shader-Pack</div>
            </div>
            <button class="btn-del" on:click={() => deleteShader(s.name)}>Löschen</button>
          </div>
        {/each}
      </div>
    {/if}
  {/if}
</div>

<style>
  .page { display:flex; flex-direction:column; height:100%; padding:28px 32px; overflow:hidden; }
  .header { margin-bottom:12px; }
  .header h1 { font-size:22px; font-weight:700; color:#fff; margin-bottom:4px; }
  .header p { font-size:13px; color:#888; }

  .tabs { display:flex; gap:2px; margin-bottom:16px; border-bottom:1px solid rgba(255,255,255,.07); flex-shrink:0; }
  .tab { padding:9px 18px; border:none; background:none; color:#666; font-size:11px; font-weight:700;
    letter-spacing:.08em; cursor:pointer; border-bottom:2px solid transparent; transition:color .15s,border-color .15s; }
  .tab.active { color:var(--accent); border-bottom-color:var(--accent); }
  .tab:hover:not(.active) { color:#aaa; }

  .search-bar { margin-bottom:12px; flex-shrink:0; }
  .search-input { width:100%; background:rgba(255,255,255,.05); border:1px solid rgba(255,255,255,.1);
    border-radius:8px; padding:10px 14px; color:#fff; font-size:13px; outline:none; transition:border-color .2s; }
  .search-input:focus { border-color:color-mix(in srgb,var(--accent) 50%,transparent); }

  .shader-list { display:flex; flex-direction:column; gap:8px; overflow-y:auto; flex:1; padding-bottom:8px; }
  .shader-card { display:flex; align-items:flex-start; gap:12px;
    background:rgba(255,255,255,.03); border:1px solid rgba(255,255,255,.07);
    border-radius:10px; padding:12px 14px; cursor:pointer;
    transition:background .15s,border-color .15s; }
  .shader-card:hover:not(.static) { background:rgba(255,255,255,.07); border-color:rgba(255,255,255,.14); }
  .shader-card.static { cursor:default; }

  .shader-icon-wrap { width:44px; height:44px; border-radius:8px; overflow:hidden; flex-shrink:0;
    display:flex; align-items:center; justify-content:center;
    background:rgba(167,139,250,.1); border:1px solid rgba(167,139,250,.2); }
  .shader-icon-wrap img { width:100%; height:100%; object-fit:cover; }
  .icon-fb { font-size:22px; color:#a78bfa; }

  .shader-info { flex:1; min-width:0; }
  .shader-name { font-size:13px; font-weight:600; color:#e0e0e0; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .shader-author { font-weight:400; color:#666; font-size:12px; }
  .shader-desc { font-size:12px; color:#777; margin-top:3px; overflow:hidden; text-overflow:ellipsis;
    display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; }
  .shader-tags { display:flex; gap:4px; margin-top:6px; flex-wrap:wrap; }
  .tag { font-size:10px; padding:2px 7px; border-radius:4px; background:rgba(255,255,255,.06); color:#888; }

  .shader-right { display:flex; flex-direction:column; align-items:flex-end; gap:8px; flex-shrink:0; }
  .dl-count { font-size:11px; color:#555; }
  .install-btn { padding:6px 14px; border-radius:6px; font-size:11px; font-weight:700;
    background:color-mix(in srgb,var(--accent) 18%,transparent);
    border:1px solid color-mix(in srgb,var(--accent) 40%,transparent);
    color:var(--accent); cursor:pointer; transition:background .15s; }
  .install-btn:hover:not(:disabled) { background:color-mix(in srgb,var(--accent) 30%,transparent); }
  .install-btn:disabled { opacity:.5; cursor:default; }

  /* Detail */
  .detail-wrap { display:flex; flex-direction:column; gap:18px; overflow-y:auto; flex:1; padding-bottom:12px; }
  .back-btn { background:none; border:none; color:#888; font-size:13px; cursor:pointer; padding:0; transition:color .15s; }
  .back-btn:hover { color:#fff; }
  .detail-top { display:flex; align-items:flex-start; gap:16px; }
  .detail-icon { width:72px; height:72px; border-radius:12px; object-fit:cover; flex-shrink:0; }
  .detail-icon-fb { width:72px; height:72px; border-radius:12px; flex-shrink:0;
    background:rgba(167,139,250,.1); border:1px solid rgba(167,139,250,.25);
    display:flex; align-items:center; justify-content:center; font-size:28px; color:#a78bfa; }
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
  .body-text { font-size:13px; color:#aaa; line-height:1.7; white-space:pre-wrap; word-break:break-word; }
  .license-row { font-size:11px; color:#555; }

  .toolbar { display:flex; align-items:center; gap:12px; margin-bottom:12px; flex-shrink:0; }
  .btn-ghost { padding:7px 14px; border-radius:7px; background:rgba(255,255,255,.05);
    border:1px solid rgba(255,255,255,.1); color:#888; font-size:12px; cursor:pointer; transition:all .2s; }
  .btn-ghost:hover { color:#ccc; }
  .count { font-size:12px; color:#555; }

  .btn-del { padding:5px 12px; border-radius:6px;
    background:rgba(239,68,68,.1); border:1px solid rgba(239,68,68,.2);
    color:#f87171; font-size:12px; cursor:pointer; transition:all .15s; flex-shrink:0; }
  .btn-del:hover { background:rgba(239,68,68,.22); }

  .msg { font-size:11px; color:#4ade80; max-width:90px; text-align:right; }
  .msg.err { color:#f87171; }
  .empty { font-size:13px; color:#555; text-align:center; padding:40px; }
  .empty code { color:var(--accent); }
</style>
