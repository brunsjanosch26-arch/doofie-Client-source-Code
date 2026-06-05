<script lang="ts">
  import { onMount } from 'svelte'
  import { invoke } from '@tauri-apps/api/core'
  import { listen } from '@tauri-apps/api/event'
  import { getCurrentWindow } from '@tauri-apps/api/window'
  import { activeAccount, gameDir, currentPage, accentColor } from './stores/appStore'
  import type { Account } from './stores/appStore'
  import Sidebar from './components/Sidebar.svelte'
  import NewsPanel from './components/NewsPanel.svelte'
  import Home from './pages/Home.svelte'
  import ModManager from './pages/ModManager.svelte'
  import ShaderManager from './pages/ShaderManager.svelte'
  import ServerList from './pages/ServerList.svelte'
  import CapeManager from './pages/CapeManager.svelte'
  import AccountManager from './pages/AccountManager.svelte'
  import Settings from './pages/Settings.svelte'
  import ProfileManager from './pages/ProfileManager.svelte'
  import ResourcePackManager from './pages/ResourcePackManager.svelte'

  const win = getCurrentWindow()
  let updateAvailable: { version: string } | null = null

  // Apply accent color as CSS variable on init
  $: document.documentElement.style.setProperty('--accent', $accentColor)

  async function loadActiveAccount(dir: string) {
    try {
      const accounts = await invoke<Account[]>('get_accounts', { gameDir: dir })
      const active = accounts.find(a => a.is_active) ?? null
      activeAccount.set(active)
    } catch {}
  }

  async function refreshTokens(dir: string) {
    try {
      const accounts = await invoke<Account[]>('refresh_accounts', { gameDir: dir })
      const active = accounts.find(a => a.is_active) ?? null
      if (active) activeAccount.set(active)
    } catch {}
  }

  onMount(async () => {
    try {
      const dir = await invoke<string>('get_game_directory')
      gameDir.set(dir)
      await loadActiveAccount(dir)
      await refreshTokens(dir)
    } catch {}

    listen('ms_login_complete', async () => {
      const dir = await invoke<string>('get_game_directory').catch(() => '')
      if (dir) await loadActiveAccount(dir)
    })
  })
</script>

<div class="app">
  <div class="titlebar">
    <div class="titlebar-left">
      <span class="logo-bolt">🐾</span>
      <span class="logo-text">DOOFIE CLIENT</span>
    </div>
    <div class="titlebar-center">
      {#if updateAvailable}
        <span class="update-chip">↑ Update {updateAvailable.version}</span>
      {/if}
    </div>
    <div class="titlebar-right">
      <button class="win-btn" on:click={() => win.minimize()}>─</button>
      <button class="win-btn" on:click={() => win.toggleMaximize()}>□</button>
      <button class="win-btn close" on:click={() => win.close()}>✕</button>
    </div>
  </div>

  <div class="layout">
    <Sidebar />

    <main class="main-content">
      {#if $currentPage === 'home'}
        <Home />
      {:else if $currentPage === 'mods'}
        <ModManager />
      {:else if $currentPage === 'shaders'}
        <ShaderManager />
      {:else if $currentPage === 'servers'}
        <ServerList />
      {:else if $currentPage === 'capes'}
        <CapeManager />
      {:else if $currentPage === 'account'}
        <AccountManager />
      {:else if $currentPage === 'settings'}
        <Settings />
      {:else if $currentPage === 'profiles'}
        <ProfileManager />
      {:else if $currentPage === 'resourcepacks'}
        <ResourcePackManager />
      {/if}
    </main>

    <NewsPanel />
  </div>
</div>

<style>
  :global(:root) { --accent: #3d7ff0; }
  :global(*) { box-sizing: border-box; margin: 0; padding: 0; }
  :global(body) {
    font-family: 'Inter', 'Segoe UI', sans-serif;
    background: #080e22;
    color: #e0e0e0;
    overflow: hidden;
    height: 100vh;
    width: 100vw;
  }
  :global(::-webkit-scrollbar) { width: 4px; }
  :global(::-webkit-scrollbar-track) { background: transparent; }
  :global(::-webkit-scrollbar-thumb) { background: rgba(255,255,255,0.1); border-radius: 2px; }

  .app {
    display: flex;
    flex-direction: column;
    height: 100vh;
    width: 100vw;
    overflow: hidden;
  }

  .titlebar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 40px;
    min-height: 40px;
    background: #0d1120;
    border-bottom: 1px solid rgba(255,255,255,0.07);
    padding: 0 10px 0 14px;
    -webkit-app-region: drag;
    flex-shrink: 0;
    z-index: 100;
  }

  .titlebar-left { display: flex; align-items: center; gap: 8px; }
  .logo-bolt { font-size: 16px; color: var(--accent); filter: drop-shadow(0 0 6px var(--accent)); }
  .logo-text { font-size: 11px; font-weight: 800; letter-spacing: 2.5px; color: #fff; }

  .titlebar-center {
    display: flex;
    align-items: center;
    gap: 8px;
    -webkit-app-region: no-drag;
  }

  .titlebar-right {
    display: flex;
    align-items: center;
    gap: 2px;
    -webkit-app-region: no-drag;
  }

  .win-btn {
    width: 32px; height: 28px;
    background: none; border: none;
    color: #888; font-size: 12px;
    cursor: pointer; border-radius: 4px;
    display: flex; align-items: center; justify-content: center;
    transition: background 0.15s, color 0.15s;
  }
  .win-btn:hover { background: rgba(255,255,255,0.08); color: #fff; }
  .win-btn.close:hover { background: #e81123; color: #fff; }

  .update-chip {
    font-size: 10px; padding: 2px 8px;
    background: rgba(255,190,0,0.15);
    border: 1px solid rgba(255,190,0,0.3);
    border-radius: 4px; color: #ffc107;
  }

  .layout { display: flex; flex: 1; overflow: hidden; }

  .main-content {
    flex: 1;
    overflow: hidden;
    background: #080e22;
  }
</style>
