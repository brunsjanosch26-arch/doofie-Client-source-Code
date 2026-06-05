import { useState, useEffect } from 'react'
import type { Account } from '../App'
import './CapeManager.css'

interface CapeManagerProps {
  account: Account | null
}

const MOJANG_CAPES: { name: string; img: string }[] = [
  { name: 'Migrator', img: 'https://textures.minecraft.net/texture/2340c0e03dd24a11b15a8b33c2a7e9e32abb2051b2481d0ba7defd635ca7a933' },
  { name: 'Vanilla', img: 'https://textures.minecraft.net/texture/953cac8b779fe41383e675ee2b86071a71658f2180f56fbce8aa315ea70e2ed6' },
  { name: 'Optifine', img: 'https://optifine.net/adloadx?f=OptiFine_preview.png' },
]

export default function CapeManager({ account }: CapeManagerProps) {
  const [skinUrl, setSkinUrl] = useState('')

  useEffect(() => {
    if (account?.uuid) {
      setSkinUrl(`https://mc-heads.net/body/${account.uuid}/200`)
    }
  }, [account])

  if (!account) {
    return (
      <div className="cape-page">
        <div className="cape-empty">Kein Account angemeldet.</div>
      </div>
    )
  }

  return (
    <div className="cape-page">
      <div className="cape-header">
        <h1>Capes</h1>
        <p>Dein aktiver Cape wird direkt von Mojang geladen.</p>
      </div>

      <div className="cape-layout">
        <div className="cape-preview">
          <div className="cape-preview-label">DEIN SKIN</div>
          <img
            src={skinUrl}
            alt={account.username}
            className="cape-skin-img"
            onError={(e) => { (e.target as HTMLImageElement).src = 'https://mc-heads.net/body/MHF_Steve/200' }}
          />
          <div className="cape-player-name">{account.username}</div>
        </div>

        <div className="cape-list-section">
          <div className="cape-section-title">Bekannte Capes</div>
          <p className="cape-info">
            Capes werden direkt von Mojang gesteuert. Käufliche Capes (z.B. Migrator,
            Vanilla) erscheinen automatisch wenn sie deinem Account zugeordnet sind.
          </p>

          <div className="cape-grid">
            {MOJANG_CAPES.map((cape) => (
              <div key={cape.name} className="cape-card">
                <div className="cape-card-name">{cape.name}</div>
                <div className="cape-card-badge">Mojang</div>
              </div>
            ))}
          </div>

          <div className="cape-optifine-section">
            <div className="cape-section-title">OptiFine Cape</div>
            <p className="cape-info">
              OptiFine Capes können auf <strong>optifine.net</strong> erstellt und verwaltet werden.
              Sie sind sichtbar für andere Spieler mit OptiFine.
            </p>
            <a
              className="cape-link-btn"
              href="https://optifine.net/adloadx?f=OptiFine_preview.png"
              target="_blank"
              rel="noreferrer"
            >
              optifine.net öffnen
            </a>
          </div>
        </div>
      </div>
    </div>
  )
}
