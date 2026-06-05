<script lang="ts">
  import { activeAccount } from '../stores/appStore'
</script>

<div class="page">
  <div class="header">
    <h1>Capes</h1>
    <p>Capes werden von Mojang und OptiFine verwaltet.</p>
  </div>

  {#if $activeAccount}
    <div class="layout">
      <div class="preview">
        <div class="preview-label">DEIN SKIN</div>
        <img
          class="skin-img"
          src="https://mc-heads.net/body/{$activeAccount.uuid}/160"
          alt={$activeAccount.username}
          on:error={(e) => { e.currentTarget.src = 'https://mc-heads.net/body/MHF_Steve/160' }}
        />
        <div class="player-name">{$activeAccount.username}</div>
      </div>

      <div class="info-section">
        <div class="section-title">Mojang Capes</div>
        <p class="text">
          Offizielle Capes (Migrator, Vanilla, Promo-Capes) sind direkt an deinen Mojang-Account
          gebunden und erscheinen automatisch im Spiel.
        </p>

        <div class="cape-grid">
          {#each ['Migrator', 'Vanilla', 'Optifine', 'Promo'] as name}
            <div class="cape-chip">{name}</div>
          {/each}
        </div>

        <div class="section-title" style="margin-top:24px">OptiFine Cape</div>
        <p class="text">
          Erstelle ein kostenloses Cape auf optifine.net — sichtbar für alle Spieler mit OptiFine.
        </p>
        <a class="link-btn" href="https://optifine.net/cape" target="_blank" rel="noreferrer">
          optifine.net/cape öffnen ↗
        </a>
      </div>
    </div>
  {:else}
    <div class="empty">Kein Account angemeldet.</div>
  {/if}
</div>

<style>
  .page { display: flex; flex-direction: column; height: 100%; padding: 28px 32px; overflow-y: auto; gap: 20px; }
  .header h1 { font-size: 22px; font-weight: 700; color: #fff; margin-bottom: 4px; }
  .header p { font-size: 13px; color: #888; }
  .layout { display: flex; gap: 48px; }
  .preview { display: flex; flex-direction: column; align-items: center; gap: 12px; min-width: 120px; }
  .preview-label { font-size: 10px; letter-spacing: 0.12em; color: #444; font-weight: 600; }
  .skin-img { width: 80px; image-rendering: pixelated; border-radius: 4px; }
  .player-name { font-size: 12px; font-weight: 600; color: #aaa; }
  .info-section { flex: 1; }
  .section-title { font-size: 11px; font-weight: 700; letter-spacing: 0.1em; color: #3d7ff0; text-transform: uppercase; margin-bottom: 10px; }
  .text { font-size: 13px; color: #777; line-height: 1.6; margin-bottom: 16px; }
  .cape-grid { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
  .cape-chip {
    font-size: 12px; padding: 5px 14px; border-radius: 6px;
    background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.1); color: #ccc;
  }
  .link-btn {
    display: inline-block; padding: 8px 18px; border-radius: 8px;
    background: rgba(61,127,240,0.15); border: 1px solid rgba(61,127,240,0.4);
    color: #3d7ff0; font-size: 13px; font-weight: 600; text-decoration: none;
    transition: background 0.2s;
  }
  .link-btn:hover { background: rgba(61,127,240,0.28); }
  .empty { font-size: 13px; color: #555; padding: 40px; text-align: center; }
</style>
