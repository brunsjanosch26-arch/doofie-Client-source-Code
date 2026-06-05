<script lang="ts">
  let copied = ''
  let custom = ''

  const SERVERS = [
    { name: 'Hypixel',   ip: 'mc.hypixel.net',      desc: 'Größter Minecraft Server weltweit. Minigames, SkyBlock.',   type: 'top' },
    { name: 'GommeHD',   ip: 'gommehd.net',          desc: 'Beliebter deutscher Server mit vielen Minigames.',         type: 'de'  },
    { name: 'CubeCraft',  ip: 'play.cubecraft.net',   desc: 'Große Minigame-Auswahl für Java & Bedrock.',              type: 'top' },
    { name: 'Mineplex',  ip: 'us.mineplex.com',       desc: 'Klassischer Minigame-Server.',                            type: 'top' },
    { name: '2b2t',      ip: '2b2t.org',              desc: 'Ältester Anarchy-Server. Keine Regeln.',                  type: 'anarchy' },
  ]

  function copyIp(ip: string) {
    navigator.clipboard.writeText(ip)
    copied = ip
    setTimeout(() => copied = '', 1500)
  }
</script>

<div class="page">
  <div class="header">
    <h1>Server</h1>
    <p>Klicke auf einen Server um die IP zu kopieren — dann in Minecraft einfügen.</p>
  </div>

  <div class="custom-row">
    <input class="ip-input" bind:value={custom} placeholder="Eigene IP eingeben..." />
    <button class="copy-btn" on:click={() => custom && copyIp(custom)} disabled={!custom}>
      {copied === custom && custom ? '✓ Kopiert' : 'Kopieren'}
    </button>
  </div>

  <div class="server-list">
    {#each SERVERS as s}
      <div class="server-card" on:click={() => copyIp(s.ip)} on:keydown={() => {}} role="button" tabindex="0">
        <div class="server-letter">{s.name[0]}</div>
        <div class="server-info">
          <div class="server-name">{s.name}</div>
          <div class="server-ip">{s.ip}</div>
          <div class="server-desc">{s.desc}</div>
        </div>
        <div class="server-right">
          <span class="badge {s.type}">{s.type === 'de' ? 'Deutsch' : s.type === 'anarchy' ? 'Anarchy' : 'Populär'}</span>
          {#if copied === s.ip}
            <span class="copied-label">✓ Kopiert</span>
          {/if}
        </div>
      </div>
    {/each}
  </div>
</div>

<style>
  .page { display: flex; flex-direction: column; height: 100%; padding: 28px 32px; overflow-y: auto; gap: 20px; }
  .header h1 { font-size: 22px; font-weight: 700; color: #fff; margin-bottom: 4px; }
  .header p { font-size: 13px; color: #888; }

  .custom-row { display: flex; gap: 10px; }
  .ip-input {
    flex: 1; background: rgba(255,255,255,0.05);
    border: 1px solid rgba(255,255,255,0.12); border-radius: 8px;
    padding: 10px 14px; color: #fff; font-size: 14px; outline: none;
  }
  .copy-btn {
    padding: 10px 20px; background: rgba(61,127,240,0.2);
    border: 1px solid rgba(61,127,240,0.4); border-radius: 8px;
    color: #3d7ff0; font-size: 13px; font-weight: 600; cursor: pointer;
  }
  .copy-btn:disabled { opacity: 0.4; cursor: default; }

  .server-list { display: flex; flex-direction: column; gap: 10px; }
  .server-card {
    display: flex; align-items: center; gap: 16px;
    background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.07);
    border-radius: 10px; padding: 14px 18px; cursor: pointer; transition: all 0.2s;
  }
  .server-card:hover { background: rgba(61,127,240,0.08); border-color: rgba(61,127,240,0.25); }

  .server-letter {
    width: 42px; height: 42px; border-radius: 8px;
    background: rgba(61,127,240,0.18); display: flex; align-items: center; justify-content: center;
    font-size: 18px; font-weight: 700; color: #3d7ff0; flex-shrink: 0;
  }
  .server-info { flex: 1; }
  .server-name { font-size: 14px; font-weight: 700; color: #fff; margin-bottom: 2px; }
  .server-ip { font-size: 12px; color: #3d7ff0; font-family: monospace; margin-bottom: 3px; }
  .server-desc { font-size: 12px; color: #666; }

  .server-right { display: flex; flex-direction: column; align-items: flex-end; gap: 6px; }
  .badge { font-size: 10px; font-weight: 600; padding: 3px 8px; border-radius: 4px; }
  .badge.top { background: rgba(74,222,128,0.12); color: #4ade80; }
  .badge.de { background: rgba(251,191,36,0.12); color: #fbbf24; }
  .badge.anarchy { background: rgba(239,68,68,0.12); color: #f87171; }
  .copied-label { font-size: 11px; color: #4ade80; font-weight: 600; }
</style>
