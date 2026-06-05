<script lang="ts">
  import { currentPage, activeAccount } from '../stores/appStore'
  import type { Page } from '../stores/appStore'

  const NAV: { id: Page; icon: string; label: string }[] = [
    { id: 'home',         icon: '▶',  label: 'Spielen'         },
    { id: 'profiles',     icon: '⊛',  label: 'Profile'         },
    { id: 'mods',         icon: '⊞',  label: 'Mods'            },
    { id: 'resourcepacks',icon: '🖼',  label: 'Ressourcenpakete'},
    { id: 'shaders',      icon: '✦',  label: 'Shader'          },
    { id: 'servers',      icon: '⊟',  label: 'Server'          },
    { id: 'capes',        icon: '◈',  label: 'Capes'           },
    { id: 'account',      icon: '◉',  label: 'Account'         },
  ]
</script>

<nav class="sidebar">
  <!-- Top: Player head (or Doofie logo if not logged in) -->
  <button class="logo-btn" on:click={() => currentPage.set('home')} title="Doofie Client">
    {#if $activeAccount}
      <img
        class="logo-head"
        src="https://mc-heads.net/avatar/{$activeAccount.uuid}/40"
        alt={$activeAccount.username}
        on:error={(e) => { e.currentTarget.src = 'https://mc-heads.net/avatar/MHF_Steve/40' }}
      />
    {:else}
      <span class="logo-letter">D</span>
    {/if}
  </button>

  <div class="nav-list">
    {#each NAV as item}
      <button
        class="nav-btn"
        class:active={$currentPage === item.id}
        on:click={() => currentPage.set(item.id)}
        title={item.label}
      >
        <span class="nav-icon">{item.icon}</span>
        {#if $currentPage === item.id}
          <div class="active-bar" />
        {/if}
      </button>
    {/each}
  </div>

  <div class="sidebar-bottom">
    {#if $activeAccount}
      <button class="avatar-btn" on:click={() => currentPage.set('account')} title={$activeAccount.username}>
        <img
          src="https://mc-heads.net/avatar/{$activeAccount.uuid}/32"
          alt={$activeAccount.username}
          on:error={(e) => { e.currentTarget.src = 'https://mc-heads.net/avatar/MHF_Steve/32' }}
        />
      </button>
    {/if}
    <button
      class="nav-btn"
      class:active={$currentPage === 'settings'}
      on:click={() => currentPage.set('settings')}
      title="Einstellungen"
    >
      <span class="nav-icon">⚙</span>
      {#if $currentPage === 'settings'}<div class="active-bar" />{/if}
    </button>
  </div>
</nav>

<style>
  .sidebar {
    width: 56px; min-width: 56px;
    background: #0d1120;
    border-right: 1px solid rgba(255,255,255,0.07);
    display: flex; flex-direction: column; align-items: center;
    padding: 10px 0 12px; gap: 4px; z-index: 10;
  }

  .logo-btn {
    background: none; border: none; cursor: pointer;
    padding: 4px; margin-bottom: 12px; border-radius: 10px;
    transition: transform 0.15s;
  }
  .logo-btn:hover { transform: scale(1.08); }

  .logo-head {
    width: 38px; height: 38px;
    border-radius: 8px;
    image-rendering: pixelated;
    border: 2px solid rgba(61, 127, 240, 0.45);
    display: block;
    box-shadow: 0 0 10px rgba(61, 127, 240, 0.25);
  }

  .logo-letter {
    display: flex; align-items: center; justify-content: center;
    width: 38px; height: 38px;
    font-size: 22px; font-weight: 900;
    color: var(--accent);
    filter: drop-shadow(0 0 8px var(--accent));
  }

  .nav-list { display: flex; flex-direction: column; align-items: center; gap: 2px; flex: 1; }
  .sidebar-bottom { display: flex; flex-direction: column; align-items: center; gap: 6px; }

  .nav-btn {
    position: relative; width: 42px; height: 42px;
    border-radius: 10px; background: none; border: none;
    color: #666; font-size: 16px; cursor: pointer;
    display: flex; align-items: center; justify-content: center;
    transition: background 0.15s, color 0.15s;
  }
  .nav-btn:hover { background: rgba(255,255,255,0.06); color: #8ab4ff; }
  .nav-btn.active { background: color-mix(in srgb, var(--accent) 18%, transparent); color: var(--accent); }
  .nav-icon { font-style: normal; line-height: 1; }

  .active-bar {
    position: absolute; left: 0; top: 20%; height: 60%; width: 3px;
    background: var(--accent); border-radius: 0 3px 3px 0;
    box-shadow: 0 0 8px var(--accent);
  }

  .avatar-btn {
    width: 36px; height: 36px; border-radius: 8px;
    border: 2px solid color-mix(in srgb, var(--accent) 40%, transparent);
    background: none; cursor: pointer; padding: 0; overflow: hidden;
    transition: border-color 0.2s;
  }
  .avatar-btn:hover { border-color: var(--accent); }
  .avatar-btn img { width: 100%; height: 100%; image-rendering: pixelated; display: block; }
</style>
