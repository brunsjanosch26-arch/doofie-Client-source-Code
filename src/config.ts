// ── Backend URLs ─────────────────────────────────────────────────────────────
// Nach dem Upload des /backend Ordners auf GitHub hier die Raw-URLs eintragen.
// Beispiel: https://raw.githubusercontent.com/DEIN-USER/DEIN-REPO/main/backend/news.json

export const BACKEND_URLS = {
  news:    'https://raw.githubusercontent.com/brunsjanosch26-arch/norisk-client-backend/main/backend/news.json',
  version: 'https://raw.githubusercontent.com/brunsjanosch26-arch/norisk-client-backend/main/backend/version.json',
}

// Fallback-News falls GitHub nicht erreichbar ist
export const FALLBACK_NEWS: NewsItem[] = [
  {
    id: 1,
    tag: 'NORISK CLIENT',
    title: 'Willkommen beim NoRisk Client',
    emoji: '⚡',
    gradient: 'linear-gradient(135deg, #0a1830, #0d1f3a)',
    accentColor: '#3d7ff0',
  },
]

export interface NewsItem {
  id: number
  tag: string
  title: string
  subtitle?: string
  emoji: string
  gradient: string
  accentColor: string
}
