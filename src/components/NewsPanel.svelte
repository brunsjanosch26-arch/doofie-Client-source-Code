<script lang="ts">
  import { onMount } from 'svelte'

  interface NewsItem { title: string; body: string; color: string; emoji: string }

  let news: NewsItem[] = [
    { title: 'Doofie Client v2.0', body: 'Neuer Launcher mit Svelte, 3D-Skin, Capes und mehr!', color: '#1a2a4a', emoji: '🚀' },
    { title: 'Microsoft Login', body: 'Sicherer Login mit deinem Microsoft-Account direkt im Launcher.', color: '#1a2a3a', emoji: '🔐' },
    { title: 'Token Refresh', body: 'Tokens werden automatisch erneuert — kein erneutes Anmelden nötig.', color: '#1a2a1a', emoji: '✅' },
  ]

  onMount(async () => {
    try {
      const data = await fetch('https://api.github.com/repos/NoRiskClient/noriskclient-launcher/releases?per_page=3')
        .then(r => r.json())
      if (Array.isArray(data) && data.length > 0) {
        news = data.slice(0, 3).map((r: any, i: number) => ({
          title: r.name || r.tag_name,
          body: (r.body || '').slice(0, 120) + '...',
          color: ['#1a2a4a','#1a1a3a','#2a1a2a'][i],
          emoji: '📦',
        }))
      }
    } catch {}
  })
</script>

<aside class="news-panel">
  <div class="news-header">NEUIGKEITEN</div>
  <div class="news-list">
    {#each news as item}
      <div class="news-card" style="background:{item.color}">
        <div class="news-emoji">{item.emoji}</div>
        <div class="news-content">
          <div class="news-title">{item.title}</div>
          <div class="news-body">{item.body}</div>
        </div>
      </div>
    {/each}
  </div>
</aside>

<style>
  .news-panel {
    width: 220px; min-width: 220px;
    background: #0d1120;
    border-left: 1px solid rgba(255,255,255,0.07);
    display: flex; flex-direction: column;
    overflow: hidden;
  }
  .news-header {
    padding: 14px 14px 10px;
    font-size: 10px; font-weight: 700;
    letter-spacing: 0.12em; color: #444;
    text-transform: uppercase;
    border-bottom: 1px solid rgba(255,255,255,0.05);
  }
  .news-list { display: flex; flex-direction: column; gap: 8px; padding: 10px; overflow-y: auto; }
  .news-card {
    border-radius: 10px; padding: 12px;
    display: flex; gap: 10px;
    border: 1px solid rgba(255,255,255,0.06);
  }
  .news-emoji { font-size: 20px; flex-shrink: 0; }
  .news-title { font-size: 12px; font-weight: 700; color: #e0e0e0; margin-bottom: 4px; }
  .news-body { font-size: 11px; color: #888; line-height: 1.5; }
</style>
