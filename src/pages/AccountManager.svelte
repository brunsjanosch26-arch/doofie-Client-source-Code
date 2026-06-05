<script lang="ts">
  import { onMount } from 'svelte'
  import { invoke } from '@tauri-apps/api/core'
  import { listen } from '@tauri-apps/api/event'
  import { activeAccount, gameDir } from '../stores/appStore'
  import type { Account } from '../stores/appStore'

  let accounts: Account[] = []
  let offlineName = ''
  let msLoading = false
  let msStatus = ''
  let addingOffline = false

  async function load() {
    accounts = await invoke<Account[]>('get_accounts', { gameDir: $gameDir }).catch(() => [])
    const active = accounts.find(a => a.is_active) ?? null
    activeAccount.set(active)
  }

  async function setActive(id: string) {
    await invoke('set_active_account', { id, gameDir: $gameDir }).catch(() => {})
    await load()
  }

  async function remove(id: string) {
    await invoke('remove_account', { id, gameDir: $gameDir }).catch(() => {})
    await load()
  }

  async function addOffline() {
    if (!offlineName.trim()) return
    await invoke('add_offline_account', { username: offlineName.trim(), gameDir: $gameDir }).catch(() => {})
    offlineName = ''
    addingOffline = false
    await load()
  }

  async function loginMicrosoft() {
    msLoading = true
    msStatus = 'Login-Fenster wird geöffnet...'

    const u1 = await listen('ms_login_complete', async () => {
      msStatus = '✓ Erfolgreich angemeldet!'
      msLoading = false
      await load()
      u1(); u2()
      setTimeout(() => msStatus = '', 2500)
    })
    const u2 = await listen<string>('ms_login_error', e => {
      msStatus = `Fehler: ${e.payload}`
      msLoading = false
      u1(); u2()
    })

    try {
      await invoke('start_microsoft_webview_login', { gameDir: $gameDir })
    } catch (e) {
      msStatus = `Fehler: ${e}`
      msLoading = false
      u1(); u2()
    }
  }

  onMount(load)
</script>

<div class="page">
  <div class="header">
    <h1>Accounts</h1>
    <p>Verwalte deine Minecraft-Accounts</p>
  </div>

  <!-- MS Login -->
  <div class="ms-section">
    <button class="ms-btn" on:click={loginMicrosoft} disabled={msLoading}>
      <span class="ms-icon">⊞</span>
      {msLoading ? 'Öffne...' : 'Microsoft-Account hinzufügen'}
    </button>
    {#if msStatus}
      <div class="ms-status" class:success={msStatus.startsWith('✓')}>{msStatus}</div>
    {/if}
  </div>

  <!-- Offline -->
  {#if addingOffline}
    <div class="offline-row">
      <input class="offline-input" bind:value={offlineName} placeholder="Username" on:keydown={e => e.key === 'Enter' && addOffline()} />
      <button class="btn-primary" on:click={addOffline}>Hinzufügen</button>
      <button class="btn-ghost" on:click={() => addingOffline = false}>Abbrechen</button>
    </div>
  {:else}
    <button class="btn-ghost small" on:click={() => addingOffline = true}>+ Offline-Account</button>
  {/if}

  <!-- Account list -->
  <div class="account-list">
    {#each accounts as acc}
      <div class="account-card" class:active-card={acc.is_active}>
        <img
          class="avatar"
          src="https://mc-heads.net/head/{acc.uuid}/40"
          alt={acc.username}
          on:error={(e) => { e.currentTarget.src = 'https://mc-heads.net/head/MHF_Steve/40' }}
        />
        <div class="account-info">
          <div class="account-name">{acc.username}</div>
          <div class="account-type">{acc.account_type}</div>
        </div>
        <div class="account-actions">
          {#if acc.is_active}
            <span class="active-badge">Aktiv</span>
          {:else}
            <button class="btn-sm" on:click={() => setActive(acc.id)}>Auswählen</button>
          {/if}
          <button class="btn-sm danger" on:click={() => remove(acc.id)}>✕</button>
        </div>
      </div>
    {/each}
    {#if accounts.length === 0}
      <div class="empty">Noch kein Account. Melde dich oben an.</div>
    {/if}
  </div>
</div>

<style>
  .page { display: flex; flex-direction: column; height: 100%; padding: 28px 32px; overflow-y: auto; gap: 20px; }
  .header h1 { font-size: 22px; font-weight: 700; letter-spacing: 0.05em; color: #fff; margin-bottom: 4px; }
  .header p { font-size: 13px; color: #888; }

  .ms-section { display: flex; flex-direction: column; gap: 10px; }
  .ms-btn {
    display: flex; align-items: center; gap: 10px;
    padding: 12px 20px; border-radius: 8px;
    background: #3d7ff0; border: none; color: #fff;
    font-size: 14px; font-weight: 700; cursor: pointer; width: fit-content;
    transition: background 0.2s;
  }
  .ms-btn:hover:not(:disabled) { background: #5a99ff; }
  .ms-btn:disabled { opacity: 0.6; cursor: default; }
  .ms-icon { font-size: 16px; }
  .ms-status { font-size: 13px; color: #f87171; }
  .ms-status.success { color: #4ade80; }

  .offline-row { display: flex; gap: 10px; align-items: center; }
  .offline-input {
    background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.12);
    border-radius: 8px; padding: 8px 14px; color: #fff; font-size: 14px; outline: none;
  }

  .btn-primary {
    padding: 8px 16px; border-radius: 8px; background: #3d7ff0;
    border: none; color: #fff; font-size: 13px; font-weight: 600; cursor: pointer;
  }
  .btn-ghost {
    padding: 8px 16px; border-radius: 8px;
    background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.1);
    color: #888; font-size: 13px; cursor: pointer; transition: all 0.2s;
  }
  .btn-ghost:hover { color: #ccc; border-color: rgba(255,255,255,0.2); }
  .btn-ghost.small { padding: 6px 14px; font-size: 12px; width: fit-content; }

  .account-list { display: flex; flex-direction: column; gap: 10px; }
  .account-card {
    display: flex; align-items: center; gap: 14px;
    background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.07);
    border-radius: 10px; padding: 12px 16px;
    transition: border-color 0.2s;
  }
  .account-card.active-card { border-color: rgba(61,127,240,0.4); background: rgba(61,127,240,0.07); }

  .avatar { width: 40px; height: 40px; border-radius: 6px; image-rendering: pixelated; }
  .account-info { flex: 1; }
  .account-name { font-size: 14px; font-weight: 700; color: #fff; }
  .account-type { font-size: 11px; color: #666; margin-top: 2px; }

  .account-actions { display: flex; align-items: center; gap: 8px; }
  .active-badge {
    font-size: 11px; padding: 3px 10px; border-radius: 4px;
    background: rgba(61,127,240,0.2); color: #3d7ff0; font-weight: 600;
  }
  .btn-sm {
    padding: 5px 12px; border-radius: 6px;
    background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.1);
    color: #ccc; font-size: 12px; cursor: pointer; transition: all 0.15s;
  }
  .btn-sm:hover { background: rgba(255,255,255,0.12); }
  .btn-sm.danger:hover { background: rgba(239,68,68,0.2); border-color: rgba(239,68,68,0.4); color: #f87171; }

  .empty { font-size: 13px; color: #555; text-align: center; padding: 20px; }
</style>
