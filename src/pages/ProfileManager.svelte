<script lang="ts">
  import { onMount, onDestroy } from 'svelte'
  import { invoke } from '@tauri-apps/api/core'
  import { listen } from '@tauri-apps/api/event'
  import { getCurrentWindow } from '@tauri-apps/api/window'
  import { gameDir, currentPage, accentColor, activeAccount } from '../stores/appStore'

  interface GameProfile {
    id: string
    name: string
    version: string
    java_args: string
    ram: number
    mods_enabled: string[]
    shader: string | null
  }

  interface ModrinthHit {
    project_id: string
    title: string
    description: string
    author: string
    downloads: number
    icon_url: string | null
    categories: string[]
  }

  // ── Views ──────────────────────────────────────────────────────────────────
  type View = 'list' | 'detail'
  let view: View = 'list'
  let selectedProfile: GameProfile | null = null

  // ── Profile list ───────────────────────────────────────────────────────────
  let profiles: GameProfile[] = []
  let loading = false
  let showCreate = false
  let saving = false
  // Versions that are always offered, regardless of what's installed
  const PRESET_VERSIONS = ['1.8', '1.21.10', '1.21.11', '26.1.2']

  let installedVersions: string[] = []
  $: allVersions = [...new Set([...PRESET_VERSIONS, ...installedVersions])]

  let newProfile: GameProfile = {
    id: '', name: '', version: '1.21.11',
    java_args: '-XX:+UseG1GC', ram: 4,
    mods_enabled: [], shader: null,
  }

  async function loadProfiles() {
    loading = true
    profiles = await invoke<GameProfile[]>('get_profiles', { gameDir: $gameDir }).catch(() => [])
    loading = false
  }

  async function loadVersions() {
    installedVersions = await invoke<string[]>('get_installed_versions', { gameDir: $gameDir }).catch(() => [])
    // Default to latest preset, not blank
    if (!newProfile.version) newProfile.version = PRESET_VERSIONS[PRESET_VERSIONS.length - 1]
  }

  async function createProfile() {
    if (!newProfile.name.trim() || !newProfile.version) return
    saving = true
    newProfile.id = crypto.randomUUID()
    await invoke('create_profile', { profile: newProfile, gameDir: $gameDir }).catch(() => {})
    await loadProfiles()
    showCreate = false
    newProfile = { id: '', name: '', version: '1.21.11', java_args: '-XX:+UseG1GC', ram: 4, mods_enabled: [], shader: null }
    packDone = ''; packError = ''
    saving = false
  }

  async function deleteProfile(id: string) {
    await invoke('delete_profile', { profileId: id, gameDir: $gameDir }).catch(() => {})
    if (selectedProfile?.id === id) { view = 'list'; selectedProfile = null }
    await loadProfiles()
  }

  function openProfile(p: GameProfile) {
    selectedProfile = { ...p }
    profileModQuery = ''
    profileMods = []
    localMods = []
    searchProfileMods()
    loadLocalProfileMods()
    view = 'detail'
  }

  // ── Launch profile ─────────────────────────────────────────────────────────
  let isLaunching = false
  let launchError = ''

  async function playProfile(p: GameProfile) {
    if (!$activeAccount) {
      launchError = 'Kein Account aktiv. Bitte erst einloggen.'
      setTimeout(() => launchError = '', 5000)
      return
    }
    if (!p.version) {
      launchError = 'Keine Version im Profil gesetzt.'
      setTimeout(() => launchError = '', 5000)
      return
    }
    isLaunching = true
    launchError = ''
    try {
      const javaPath = await invoke<string>('get_java_path')
      let versionToUse = p.version

      if (p.mods_enabled.length > 0) {
        launchError = 'Prüfe Fabric...'
        versionToUse = await invoke<string>('ensure_fabric', {
          mcVersion: p.version,
          gameDir: $gameDir,
        })
        launchError = 'Aktiviere Mods...'
        await invoke('apply_profile_mods', {
          gameDir: $gameDir,
          profileId: p.id,
          modsEnabled: p.mods_enabled,
        })
      }

      launchError = ''
      await invoke('launch_with_profile', {
        version: versionToUse,
        gameDir: $gameDir,
        instanceDir: `${$gameDir}\\profiles\\${p.id}`,
        javaPath,
        username: $activeAccount.username,
        uuid: $activeAccount.uuid ?? '',
        accessToken: $activeAccount.access_token ?? '0',
        ram: p.ram,
        jvmArgs: p.java_args,
      })
    } catch (e: any) {
      launchError = String(e)
      setTimeout(() => launchError = '', 8000)
    }
    setTimeout(() => isLaunching = false, 3000)
  }

  // ── Open profile folder ────────────────────────────────────────────────────
  async function openFolder() {
    const path = selectedProfile
      ? `${$gameDir}\\profiles\\${selectedProfile.id}`
      : $gameDir
    await invoke('open_game_folder', { path }).catch(() => {})
  }

  // ── Profile detail — Modrinth mod search ──────────────────────────────────
  let profileModQuery = ''
  let profileMods: ModrinthHit[] = []
  let searchingMods = false
  let loaderFilter = 'fabric'
  let modSearchTimeout: ReturnType<typeof setTimeout>

  async function searchProfileMods() {
    if (!selectedProfile) return
    searchingMods = true
    try {
      const q = profileModQuery.trim() || ''
      const facets = `[["project_type:mod"],["categories:${loaderFilter}"]]`
      const url = `https://api.modrinth.com/v2/search?query=${encodeURIComponent(q)}&facets=${encodeURIComponent(facets)}&limit=20`
      profileMods = (await (await fetch(url)).json()).hits ?? []
    } catch {}
    searchingMods = false
  }

  function onModQueryInput() {
    clearTimeout(modSearchTimeout)
    modSearchTimeout = setTimeout(searchProfileMods, 400)
  }

  let installingMod: Record<string, boolean> = {}
  let modMsg: Record<string, string> = {}

  async function installModToProfile(mod: ModrinthHit) {
    if (!selectedProfile || !$gameDir) return
    installingMod[mod.project_id] = true; modMsg[mod.project_id] = ''
    try {
      const verResp = await fetch(
        `https://api.modrinth.com/v2/project/${mod.project_id}/version?loaders=${encodeURIComponent(JSON.stringify([loaderFilter]))}&limit=1`
      )
      const versions = await verResp.json()
      if (!versions?.length) throw new Error('Keine Version verfügbar')
      const file = versions[0].files?.find((f: any) => f.primary) ?? versions[0].files?.[0]
      if (!file) throw new Error('Keine Datei')
      const bytes = new Uint8Array(await (await fetch(file.url)).arrayBuffer())
      const { writeFile, mkdir } = await import('@tauri-apps/plugin-fs')
      const modsDir = `${$gameDir}\\profiles\\${selectedProfile.id}\\mods`
      await mkdir(modsDir, { recursive: true }).catch(() => {})
      await writeFile(`${modsDir}\\${file.filename}`, bytes)
      modMsg[mod.project_id] = '✓ Installiert'
      if (selectedProfile && !selectedProfile.mods_enabled.includes(file.filename)) {
        selectedProfile = { ...selectedProfile, mods_enabled: [...selectedProfile.mods_enabled, file.filename] }
        await saveProfileChanges()
      }
      await loadLocalProfileMods()
    } catch (e: any) { modMsg[mod.project_id] = `✗ ${e?.message ?? e}` }
    installingMod[mod.project_id] = false
    setTimeout(() => { delete modMsg[mod.project_id]; modMsg = modMsg }, 5000)
  }

  // ── Local mods in profile ──────────────────────────────────────────────────
  let localMods: string[] = []

  async function loadLocalProfileMods() {
    if (!selectedProfile) return
    localMods = await invoke<string[]>('get_profile_mod_files', { gameDir: $gameDir, profileId: selectedProfile.id }).catch(() => [])
  }

  async function toggleMod(filename: string) {
    if (!selectedProfile) return
    const next = selectedProfile.mods_enabled.includes(filename)
      ? selectedProfile.mods_enabled.filter(m => m !== filename)
      : [...selectedProfile.mods_enabled, filename]
    selectedProfile = { ...selectedProfile, mods_enabled: next }
    await saveProfileChanges()
  }

  async function saveProfileChanges() {
    if (!selectedProfile) return
    const id = selectedProfile.id
    await invoke('delete_profile', { profileId: id, gameDir: $gameDir }).catch(() => {})
    await invoke('create_profile', { profile: selectedProfile, gameDir: $gameDir }).catch(() => {})
    await loadProfiles()
    // Re-sync selectedProfile from the freshly saved profiles array
    const fresh = profiles.find(p => p.id === id)
    if (fresh) selectedProfile = { ...fresh }
  }

  function fmt(n: number) {
    if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M'
    if (n >= 1_000) return (n / 1_000).toFixed(0) + 'K'
    return String(n)
  }

  // ── Modpack drop (in create form) ─────────────────────────────────────────
  let isDragging = false
  let packInstalling = false
  let packProgress = ''
  let packPercent = 0
  let packDone = ''
  let packError = ''
  let dropUnlisten: (() => void) | null = null

  async function browseModpack() {
    try {
      const { open } = await import('@tauri-apps/plugin-dialog')
      const path = await open({ filters: [{ name: 'Modrinth Modpack', extensions: ['mrpack'] }], title: 'Modpack öffnen' })
      if (!path || typeof path !== 'string') return
      await installPackFromPath(path)
    } catch (e: any) {
      packError = String(e)
    }
  }

  async function installPackFromPath(filePath: string) {
    packInstalling = true; packDone = ''; packError = ''; packPercent = 0
    const u = await listen<{ step: string; percent: number }>('modpack_progress', e => {
      packProgress = e.payload.step; packPercent = e.payload.percent
    })
    try {
      const result = await invoke<string>('install_modpack', { filePath, gameDir: $gameDir })
      const info = JSON.parse(result)
      packDone = `✓ "${info.name}" v${info.version} (MC ${info.mcVersion})`
      // Auto-populate version if it matches an installed version
      if (info.mcVersion) {
        const matchFabric = installedVersions.find(v => v.includes(info.mcVersion) && v.toLowerCase().includes('fabric'))
        const matchVanilla = installedVersions.find(v => v === info.mcVersion)
        if (matchFabric) newProfile.version = matchFabric
        else if (matchVanilla) newProfile.version = matchVanilla
      }
    } catch (e: any) { packError = String(e) }
    u(); packInstalling = false
  }

  async function registerDrop() {
    const win = getCurrentWindow()
    dropUnlisten = await win.onDragDropEvent(event => {
      if (event.payload.type === 'over') {
        isDragging = true
      } else if (event.payload.type === 'leave') {
        isDragging = false
      } else if (event.payload.type === 'drop') {
        isDragging = false
        if (!showCreate) return
        const paths: string[] = (event.payload as any).paths ?? []
        const mrpack = paths.find(p => p.endsWith('.mrpack'))
        if (mrpack) installPackFromPath(mrpack)
        else if (paths.length > 0) { packError = 'Nur .mrpack Dateien werden unterstützt.' }
      }
    })
  }

  onMount(async () => {
    await loadVersions()
    await loadProfiles()
    await registerDrop()
  })

  onDestroy(() => {
    dropUnlisten?.()
  })
</script>

<div class="page">

  {#if view === 'list'}
    <!-- ── PROFILE LIST ──────────────────────────────────────────────────── -->
    <div class="page-header">
      <div>
        <h1>Profile</h1>
        <p>Verwalte deine Minecraft-Profile und Modpacks</p>
      </div>
      <button class="create-btn" on:click={() => { showCreate = !showCreate; packDone=''; packError='' }}>
        {showCreate ? '✕ Abbrechen' : '+ Erstellen'}
      </button>
    </div>

    {#if launchError}
      <div class="launch-err">{launchError}</div>
    {/if}

    {#if showCreate}
      <div class="create-card" class:drag-active={isDragging}>
        <div class="create-title">Neues Profil</div>
        <div class="form-row">
          <div class="form-group">
            <span class="form-label">Name</span>
            <input class="form-input" bind:value={newProfile.name} placeholder="Mein Profil" />
          </div>
          <div class="form-group">
            <span class="form-label">Version</span>
            <select class="form-select" bind:value={newProfile.version}>
              {#each allVersions as v}
                <option value={v}>{v}{installedVersions.includes(v) ? ' ✓' : ''}</option>
              {/each}
            </select>
          </div>
        </div>
        <div class="form-row">
          <div class="form-group">
            <span class="form-label">RAM: {newProfile.ram} GB</span>
            <input type="range" class="slider" min="1" max="16" bind:value={newProfile.ram} />
          </div>
          <div class="form-group">
            <span class="form-label">JVM-Argumente</span>
            <input class="form-input" bind:value={newProfile.java_args} />
          </div>
        </div>

        <!-- Modpack drop zone -->
        <div class="drop-zone" class:dragging={isDragging}>
          {#if packInstalling}
            <div class="pack-label">{packProgress || 'Installiere...'}</div>
            <div class="pack-bar"><div class="pack-fill" style="width:{packPercent}%"></div></div>
          {:else if packDone}
            <div class="pack-done">{packDone}</div>
          {:else}
            <div class="pack-label">
              {#if isDragging}
                📦 Loslassen zum Installieren
              {:else}
                📦 Modpack (.mrpack) hierher ziehen
              {/if}
            </div>
            <button class="browse-btn" on:click={browseModpack}>Durchsuchen</button>
            {#if packError}<div class="pack-err">{packError}</div>{/if}
          {/if}
        </div>

        <button class="save-btn" disabled={saving || !newProfile.name || !newProfile.version} on:click={createProfile}>
          {saving ? 'Erstelle...' : 'Profil erstellen'}
        </button>
      </div>
    {/if}

    {#if loading}
      <div class="empty">Lade Profile...</div>
    {:else if profiles.length === 0}
      <div class="empty">
        Noch keine Profile.<br/>
        Klicke auf "+ Erstellen" um loszulegen.
      </div>
    {:else}
      <div class="profile-list">
        {#each profiles as profile}
          <div class="profile-card" on:click={() => openProfile(profile)} role="button" tabindex="0"
            on:keydown={(e) => e.key === 'Enter' && openProfile(profile)}>
            <div class="profile-icon">⚡</div>
            <div class="profile-info">
              <div class="profile-name">{profile.name}</div>
              <div class="profile-meta">
                {profile.version} · {profile.ram} GB RAM
                {#if profile.mods_enabled.length > 0}
                  · {profile.mods_enabled.length} Mod{profile.mods_enabled.length !== 1 ? 's' : ''}
                {/if}
              </div>
            </div>
            <div class="profile-actions" on:click|stopPropagation={() => {}}>
              <button class="play-btn" disabled={isLaunching}
                on:click|stopPropagation={() => playProfile(profile)}>
                {isLaunching ? '...' : '▶ SPIELEN'}
              </button>
              <button class="del-btn" on:click|stopPropagation={() => deleteProfile(profile.id)} title="Löschen">✕</button>
            </div>
          </div>
        {/each}
      </div>
    {/if}

    {#if installedVersions.length === 0}
      <div class="hint">
        Keine Versionen installiert.
        <button class="link-btn" on:click={() => currentPage.set('settings')}>Zu Einstellungen →</button>
      </div>
    {/if}

  {:else if view === 'detail' && selectedProfile}
    <!-- ── PROFILE DETAIL ─────────────────────────────────────────────────── -->
    <div class="detail-header">
      <button class="back-btn" on:click={() => { view = 'list'; selectedProfile = null }}>← Profile</button>
      <div class="detail-title-row">
        <div class="detail-icon">⚡</div>
        <div>
          <div class="detail-name">{selectedProfile.name}</div>
          <div class="detail-meta">{selectedProfile.version} · {selectedProfile.ram} GB RAM</div>
        </div>
        <div class="detail-actions">
          <button class="folder-btn" on:click={openFolder} title="Ordner öffnen">📁 Ordner</button>
          <button class="play-btn" disabled={isLaunching}
            on:click={() => selectedProfile && playProfile(selectedProfile)}>
            {isLaunching ? 'STARTET...' : '▶ SPIELEN'}
          </button>
        </div>
      </div>
      {#if launchError}
        <div class="launch-err">{launchError}</div>
      {/if}
    </div>

    <div class="detail-tabs-wrap">
      <div class="two-col">

        <!-- LEFT: Modrinth search -->
        <div class="col">
          <div class="col-title">MODS HINZUFÜGEN</div>
          <div class="search-row">
            <input class="search-input" bind:value={profileModQuery} on:input={onModQueryInput}
              placeholder="Modrinth durchsuchen..." />
            <select class="small-select" bind:value={loaderFilter} on:change={searchProfileMods}>
              <option value="fabric">Fabric</option>
              <option value="forge">Forge</option>
              <option value="quilt">Quilt</option>
            </select>
          </div>

          {#if searchingMods}
            <div class="col-empty">Suche...</div>
          {:else if profileMods.length === 0}
            <div class="col-empty">Keine Ergebnisse</div>
          {:else}
            <div class="mod-scroll">
              {#each profileMods as mod}
                <div class="mod-row">
                  <div class="mod-ico">
                    {#if mod.icon_url}
                      <img src={mod.icon_url} alt={mod.title}
                        on:error={(e) => { e.currentTarget.style.display='none' }} />
                    {:else}
                      <span>⬡</span>
                    {/if}
                  </div>
                  <div class="mod-text">
                    <div class="mod-name">{mod.title}</div>
                    <div class="mod-sub">{mod.description}</div>
                  </div>
                  <div class="mod-action">
                    {#if modMsg[mod.project_id]}
                      <span class="mod-msg" class:err={modMsg[mod.project_id].startsWith('✗')}>
                        {modMsg[mod.project_id]}
                      </span>
                    {:else}
                      <button class="add-btn" disabled={installingMod[mod.project_id]}
                        on:click={() => installModToProfile(mod)}>
                        {installingMod[mod.project_id] ? '...' : '+'}
                      </button>
                    {/if}
                  </div>
                </div>
              {/each}
            </div>
          {/if}
        </div>

        <!-- RIGHT: Installed mods in this profile -->
        <div class="col">
          <div class="col-title">INSTALLIERTE MODS
            <span class="col-count">{localMods.length}</span>
          </div>

          {#if localMods.length === 0}
            <div class="col-empty">Keine Mods installiert</div>
          {:else}
            <div class="mod-scroll">
              {#each localMods as mod}
                {@const enabled = selectedProfile.mods_enabled.includes(mod)}
                <div class="mod-row">
                  <div class="mod-ico"><span>🧩</span></div>
                  <div class="mod-text">
                    <div class="mod-name">{mod.replace(/\.jar(\.disabled)?$/, '')}</div>
                    <div class="mod-sub">.jar</div>
                  </div>
                  <button
                    class="toggle-btn"
                    class:on={enabled}
                    on:click={() => toggleMod(mod)}
                    title={enabled ? 'Im Profil aktiv' : 'Nicht im Profil'}
                  >
                    {enabled ? '✓' : '○'}
                  </button>
                </div>
              {/each}
            </div>
          {/if}
        </div>

      </div>

      <!-- Profile settings strip at bottom -->
      <div class="settings-strip">
        <div class="strip-title">PROFIL-EINSTELLUNGEN</div>
        <div class="strip-row">
          <div class="strip-group">
            <span class="strip-label">RAM: {selectedProfile.ram} GB</span>
            <input type="range" class="slider" min="1" max="16"
              bind:value={selectedProfile.ram} on:change={saveProfileChanges} />
          </div>
          <div class="strip-group flex1">
            <span class="strip-label">JVM-Argumente</span>
            <input class="form-input" bind:value={selectedProfile.java_args}
              on:blur={saveProfileChanges} />
          </div>
          <button class="save-btn" on:click={saveProfileChanges}>Speichern</button>
        </div>
      </div>
    </div>
  {/if}
</div>

<style>
  .page { display:flex; flex-direction:column; height:100%; padding:28px 32px; overflow:hidden; gap:14px; }

  /* Header */
  .page-header { display:flex; align-items:flex-start; justify-content:space-between; flex-shrink:0; }
  .page-header h1 { font-size:22px; font-weight:700; color:#fff; margin-bottom:4px; }
  .page-header p { font-size:13px; color:#888; }

  .create-btn {
    padding:9px 18px; border-radius:8px; flex-shrink:0;
    background:color-mix(in srgb,var(--accent) 18%,transparent);
    border:1px solid color-mix(in srgb,var(--accent) 40%,transparent);
    color:var(--accent); font-size:12px; font-weight:700; cursor:pointer; transition:background .15s;
  }
  .create-btn:hover { background:color-mix(in srgb,var(--accent) 28%,transparent); }

  .launch-err {
    font-size:12px; color:#f87171;
    background:rgba(248,113,113,.1); border:1px solid rgba(248,113,113,.25);
    border-radius:8px; padding:8px 14px; flex-shrink:0;
  }

  /* Create form */
  .create-card {
    background:rgba(255,255,255,.04); border:1px solid rgba(255,255,255,.1);
    border-radius:12px; padding:18px; display:flex; flex-direction:column; gap:12px; flex-shrink:0;
    transition:border-color .15s;
  }
  .create-card.drag-active { border-color:color-mix(in srgb,var(--accent) 60%,transparent); }
  .create-title { font-size:11px; font-weight:700; color:var(--accent); letter-spacing:.08em; text-transform:uppercase; }
  .form-row { display:flex; gap:14px; }
  .form-group { flex:1; display:flex; flex-direction:column; gap:5px; }
  .form-label, .strip-label { font-size:12px; color:#888; }
  .form-input {
    background:rgba(255,255,255,.06); border:1px solid rgba(255,255,255,.1);
    border-radius:8px; padding:8px 12px; color:#fff; font-size:13px; outline:none;
  }
  .form-input:focus { border-color:color-mix(in srgb,var(--accent) 50%,transparent); }
  .form-select, .small-select {
    background:rgba(255,255,255,.06); border:1px solid rgba(255,255,255,.12);
    border-radius:8px; padding:8px 10px; color:#ccc; font-size:13px; cursor:pointer; outline:none;
    max-height:200px; overflow-y:auto;
  }
  .slider { accent-color:var(--accent); width:100%; }
  .save-btn {
    padding:10px 24px; border-radius:9px; width:fit-content; background:var(--accent);
    border:none; color:#fff; font-size:13px; font-weight:700; cursor:pointer; transition:opacity .2s; align-self:flex-end;
  }
  .save-btn:disabled { opacity:.5; cursor:default; }
  .save-btn:not(:disabled):hover { opacity:.85; }

  /* Modpack drop zone */
  .drop-zone {
    border:1.5px dashed rgba(255,255,255,.15); border-radius:10px;
    padding:14px 18px; display:flex; flex-direction:column; align-items:center; gap:8px;
    transition:border-color .15s, background .15s; min-height:64px; justify-content:center;
  }
  .drop-zone.dragging {
    border-color:var(--accent);
    background:color-mix(in srgb,var(--accent) 8%,transparent);
  }
  .pack-label { font-size:12px; color:#888; }
  .pack-done { font-size:12px; color:#4ade80; text-align:center; }
  .pack-err { font-size:12px; color:#f87171; }
  .pack-bar { width:100%; height:4px; background:rgba(255,255,255,.08); border-radius:2px; }
  .pack-fill { height:100%; background:var(--accent); border-radius:2px; transition:width .3s; }
  .browse-btn {
    padding:6px 14px; border-radius:6px; font-size:11px;
    background:rgba(255,255,255,.06); border:1px solid rgba(255,255,255,.12);
    color:#aaa; cursor:pointer; transition:all .15s;
  }
  .browse-btn:hover { background:rgba(255,255,255,.12); color:#fff; }

  /* Profile list */
  .profile-list { display:flex; flex-direction:column; gap:8px; overflow-y:auto; flex:1; }
  .profile-card {
    display:flex; align-items:center; gap:14px;
    background:rgba(255,255,255,.04); border:1px solid rgba(255,255,255,.08);
    border-radius:10px; padding:14px 18px; cursor:pointer;
    transition:background .15s, border-color .15s;
  }
  .profile-card:hover { background:rgba(255,255,255,.08); border-color:rgba(255,255,255,.15); }
  .profile-icon {
    width:42px; height:42px; border-radius:10px; flex-shrink:0;
    background:color-mix(in srgb,var(--accent) 15%,transparent);
    display:flex; align-items:center; justify-content:center;
    font-size:18px; color:var(--accent);
  }
  .profile-info { flex:1; }
  .profile-name { font-size:14px; font-weight:600; color:#e0e0e0; }
  .profile-meta { font-size:12px; color:#666; margin-top:3px; }
  .profile-actions { display:flex; gap:8px; align-items:center; }

  .play-btn {
    padding:8px 16px; border-radius:7px; font-size:11px; font-weight:700; letter-spacing:.05em;
    background:var(--accent); border:none; color:#fff; cursor:pointer; transition:opacity .15s; flex-shrink:0;
  }
  .play-btn:hover:not(:disabled) { opacity:.85; }
  .play-btn:disabled { opacity:.5; cursor:default; }
  .del-btn {
    width:30px; height:30px; border-radius:6px; font-size:12px; flex-shrink:0;
    background:rgba(255,255,255,.05); border:1px solid rgba(255,255,255,.08);
    color:#666; cursor:pointer; display:flex; align-items:center; justify-content:center; transition:all .15s;
  }
  .del-btn:hover { background:rgba(248,113,113,.15); border-color:rgba(248,113,113,.3); color:#f87171; }

  /* Detail header */
  .detail-header { flex-shrink:0; }
  .back-btn { background:none; border:none; color:#888; font-size:13px; cursor:pointer;
    padding:0; margin-bottom:14px; display:block; transition:color .15s; }
  .back-btn:hover { color:#fff; }
  .detail-title-row { display:flex; align-items:center; gap:14px; }
  .detail-icon {
    width:46px; height:46px; border-radius:12px; flex-shrink:0;
    background:color-mix(in srgb,var(--accent) 18%,transparent);
    display:flex; align-items:center; justify-content:center; font-size:20px; color:var(--accent);
  }
  .detail-name { font-size:18px; font-weight:700; color:#fff; }
  .detail-meta { font-size:12px; color:#888; margin-top:2px; }
  .detail-actions { display:flex; gap:8px; align-items:center; margin-left:auto; }

  .folder-btn {
    padding:8px 14px; border-radius:7px; font-size:11px; font-weight:600;
    background:rgba(255,255,255,.06); border:1px solid rgba(255,255,255,.12);
    color:#bbb; cursor:pointer; transition:all .15s; flex-shrink:0;
  }
  .folder-btn:hover { background:rgba(255,255,255,.12); color:#fff; }

  /* Two-column detail */
  .detail-tabs-wrap { display:flex; flex-direction:column; gap:14px; flex:1; overflow:hidden; }
  .two-col { display:flex; gap:16px; flex:1; overflow:hidden; }
  .col { flex:1; display:flex; flex-direction:column; gap:10px; overflow:hidden;
    background:rgba(255,255,255,.03); border:1px solid rgba(255,255,255,.07); border-radius:12px; padding:14px; }
  .col-title { font-size:11px; font-weight:700; letter-spacing:.08em; color:var(--accent);
    text-transform:uppercase; display:flex; align-items:center; gap:8px; flex-shrink:0; }
  .col-count { background:rgba(255,255,255,.08); border-radius:4px; padding:1px 6px; font-size:10px; color:#888; }
  .col-empty { font-size:12px; color:#555; text-align:center; padding:20px; }

  .search-row { display:flex; gap:8px; flex-shrink:0; }
  .search-input {
    flex:1; background:rgba(255,255,255,.06); border:1px solid rgba(255,255,255,.1);
    border-radius:8px; padding:8px 12px; color:#fff; font-size:12px; outline:none; transition:border-color .2s;
  }
  .search-input:focus { border-color:color-mix(in srgb,var(--accent) 50%,transparent); }

  .mod-scroll { display:flex; flex-direction:column; gap:6px; overflow-y:auto; flex:1; }
  .mod-row { display:flex; align-items:center; gap:10px;
    background:rgba(255,255,255,.03); border-radius:8px; padding:8px 10px; }
  .mod-ico { width:34px; height:34px; border-radius:6px; overflow:hidden; flex-shrink:0;
    background:rgba(255,255,255,.06); display:flex; align-items:center; justify-content:center; font-size:16px; }
  .mod-ico img { width:100%; height:100%; object-fit:cover; }
  .mod-text { flex:1; min-width:0; }
  .mod-name { font-size:12px; font-weight:600; color:#ddd; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .mod-sub { font-size:11px; color:#666; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .mod-action { flex-shrink:0; }
  .mod-msg { font-size:11px; color:#4ade80; }
  .mod-msg.err { color:#f87171; }
  .add-btn {
    width:28px; height:28px; border-radius:6px; font-size:16px;
    background:color-mix(in srgb,var(--accent) 18%,transparent);
    border:1px solid color-mix(in srgb,var(--accent) 40%,transparent);
    color:var(--accent); cursor:pointer; display:flex; align-items:center; justify-content:center;
    transition:background .15s;
  }
  .add-btn:hover:not(:disabled) { background:color-mix(in srgb,var(--accent) 30%,transparent); }
  .add-btn:disabled { opacity:.5; cursor:default; }

  .toggle-btn {
    width:28px; height:28px; border-radius:6px; font-size:13px; font-weight:700;
    background:rgba(255,255,255,.05); border:1px solid rgba(255,255,255,.1);
    color:#555; cursor:pointer; display:flex; align-items:center; justify-content:center; transition:all .15s;
  }
  .toggle-btn.on { background:color-mix(in srgb,var(--accent) 18%,transparent);
    border-color:color-mix(in srgb,var(--accent) 40%,transparent); color:var(--accent); }

  /* Settings strip */
  .settings-strip {
    background:rgba(255,255,255,.03); border:1px solid rgba(255,255,255,.07);
    border-radius:10px; padding:12px 16px; flex-shrink:0;
  }
  .strip-title { font-size:10px; font-weight:700; letter-spacing:.1em; color:var(--accent);
    text-transform:uppercase; margin-bottom:10px; }
  .strip-row { display:flex; align-items:flex-end; gap:16px; }
  .strip-group { display:flex; flex-direction:column; gap:5px; min-width:160px; }
  .strip-group.flex1 { flex:1; }

  .empty { font-size:13px; color:#555; text-align:center; padding:40px; line-height:1.8; }
  .hint { font-size:12px; color:#555; display:flex; align-items:center; gap:8px; flex-shrink:0; }
  .link-btn { background:none; border:none; color:var(--accent); font-size:12px; cursor:pointer; text-decoration:underline; }
</style>
