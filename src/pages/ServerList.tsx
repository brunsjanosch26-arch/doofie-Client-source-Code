import { useState } from 'react'
import './ServerList.css'

interface Server {
  name: string
  ip: string
  description: string
  version: string
  type: 'official' | 'community'
}

const SERVERS: Server[] = [
  { name: 'Hypixel', ip: 'mc.hypixel.net', description: 'Größter Minecraft Server der Welt. Minigames, SkyBlock und mehr.', version: '1.8 – 1.20', type: 'official' },
  { name: 'GommeHD', ip: 'gommehd.net', description: 'Beliebter deutscher Server mit vielen Minigames.', version: '1.8 – 1.20', type: 'official' },
  { name: '2b2t', ip: '2b2t.org', description: 'Ältester Anarchy-Server. Keine Regeln.', version: '1.12', type: 'community' },
  { name: 'Mineplex', ip: 'us.mineplex.com', description: 'Klassischer Minigame-Server.', version: '1.8 – 1.19', type: 'official' },
  { name: 'CubeCraft', ip: 'play.cubecraft.net', description: 'Große Minigame-Auswahl für Java & Bedrock.', version: '1.9 – 1.20', type: 'official' },
]

export default function ServerList() {
  const [copied, setCopied] = useState<string | null>(null)
  const [custom, setCustom] = useState('')

  const copyIp = (ip: string) => {
    navigator.clipboard.writeText(ip)
    setCopied(ip)
    setTimeout(() => setCopied(null), 1500)
  }

  return (
    <div className="server-page">
      <div className="server-header">
        <h1>Server</h1>
        <p>Klicke auf eine IP um sie zu kopieren — dann direkt in Minecraft einfügen.</p>
      </div>

      <div className="server-custom">
        <input
          className="server-custom-input"
          placeholder="Eigene Server-IP eingeben..."
          value={custom}
          onChange={(e) => setCustom(e.target.value)}
        />
        <button
          className="server-copy-btn"
          onClick={() => custom && copyIp(custom)}
          disabled={!custom}
        >
          Kopieren
        </button>
      </div>

      <div className="server-section-title">Bekannte Server</div>

      <div className="server-list">
        {SERVERS.map((s) => (
          <div key={s.ip} className="server-card" onClick={() => copyIp(s.ip)}>
            <div className="server-card-left">
              <div className="server-icon">{s.name[0]}</div>
            </div>
            <div className="server-card-info">
              <div className="server-card-name">{s.name}</div>
              <div className="server-card-ip">{s.ip}</div>
              <div className="server-card-desc">{s.description}</div>
            </div>
            <div className="server-card-right">
              <div className={`server-type-badge ${s.type}`}>{s.type === 'official' ? 'Populär' : 'Community'}</div>
              <div className="server-version">{s.version}</div>
              {copied === s.ip && <div className="server-copied">✓ Kopiert</div>}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
