import { useState, useEffect, useRef } from 'react'
import { invoke } from '@tauri-apps/api/core'
import { listen } from '@tauri-apps/api/event'
import type { Account } from '../App'
import VersionInstaller from '../components/VersionInstaller'
import './Home.css'

interface HomeProps {
  gameDir: string
  account: Account | null
  onNavigateToAccount: () => void
}

export default function Home({ gameDir, account, onNavigateToAccount }: HomeProps) {
  const [versions, setVersions] = useState<string[]>([])
  const [selectedVersion, setSelectedVersion] = useState<string>('')
  const [isLaunching, setIsLaunching] = useState(false)
  const [showDropdown, setShowDropdown] = useState(false)
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const animRef = useRef<number>(0)

  const username = account?.username ?? 'Steve'
  const skinUrl = account?.skin_url
    ? `https://mc-heads.net/body/${account.uuid}/120`
    : `https://mc-heads.net/body/${username}/120`

  useEffect(() => {
    if (gameDir) loadVersions()
  }, [gameDir])

  useEffect(() => {
    startGrid()
    return () => cancelAnimationFrame(animRef.current)
  }, [])

  const loadVersions = async () => {
    try {
      const list = await invoke<string[]>('get_installed_versions', { gameDir })
      setVersions(list)
      if (list.length > 0) setSelectedVersion(list[list.length - 1])
    } catch {}
  }

  const startGrid = () => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')!
    let offset = 0

    const draw = () => {
      canvas.width = canvas.offsetWidth
      canvas.height = canvas.offsetHeight
      const W = canvas.width
      const H = canvas.height

      const bg = ctx.createRadialGradient(W / 2, H * 0.38, 0, W / 2, H * 0.38, W * 0.75)
      bg.addColorStop(0, '#0d1e42')
      bg.addColorStop(0.5, '#080e22')
      bg.addColorStop(1, '#04070f')
      ctx.fillStyle = bg
      ctx.fillRect(0, 0, W, H)

      const horizon = H * 0.5
      const COLS = 18
      const colSpacing = W / COLS
      const ROWS = 22
      const rowSpacing = 55
      offset = (offset + 0.4) % rowSpacing

      ctx.strokeStyle = 'rgba(50, 110, 255, 0.2)'
      ctx.lineWidth = 1

      for (let i = 0; i <= COLS; i++) {
        const xBottom = i * colSpacing
        const xHorizon = W / 2 + ((i - COLS / 2) / COLS) * W * 0.05
        ctx.beginPath()
        ctx.moveTo(xBottom, H)
        ctx.lineTo(xHorizon, horizon)
        ctx.stroke()
      }

      for (let j = 0; j < ROWS; j++) {
        const rawY = horizon + ((j * rowSpacing + offset) / (ROWS * rowSpacing)) * (H - horizon) * 1.6
        if (rawY > H) continue
        const t = (rawY - horizon) / (H - horizon)
        ctx.beginPath()
        ctx.moveTo(W / 2 - t * W * 0.7, rawY)
        ctx.lineTo(W / 2 + t * W * 0.7, rawY)
        ctx.stroke()
      }

      const glow = ctx.createRadialGradient(W / 2, horizon, 0, W / 2, horizon, W * 0.4)
      glow.addColorStop(0, 'rgba(61, 127, 240, 0.12)')
      glow.addColorStop(1, 'transparent')
      ctx.fillStyle = glow
      ctx.fillRect(0, 0, W, H)

      animRef.current = requestAnimationFrame(draw)
    }
    draw()
  }

  const handleLaunch = async () => {
    setIsLaunching(true)
    setShowDropdown(false)
    try {
      const javaPath = await invoke<string>('get_java_path')
      const config = await invoke<{ ram: number }>('get_config', { gameDir }).catch(() => ({ ram: 4 }))
      await invoke('launch_minecraft', {
        version: selectedVersion || 'latest',
        gameDir,
        javaPath,
        username,
        ram: config.ram,
      })
    } catch (err) {
      console.error('Launch error:', err)
    } finally {
      setTimeout(() => setIsLaunching(false), 3000)
    }
  }

  const [loginBusy, setLoginBusy] = useState(false)
  const [loginStatus, setLoginStatus] = useState('')

  const openMicrosoftLogin = async () => {
    setLoginBusy(true)
    setLoginStatus('Microsoft login window is opening...')

    const unOk = await listen('ms_login_complete', () => {
      setLoginStatus('')
      setLoginBusy(false)
      unOk(); unErr()
    })
    const unErr = await listen<string>('ms_login_error', (e) => {
      setLoginStatus(`Error: ${e.payload}`)
      setLoginBusy(false)
      unOk(); unErr()
    })

    try {
      await invoke('start_microsoft_webview_login', { gameDir })
    } catch (e) {
      setLoginStatus(`Error: ${e}`)
      setLoginBusy(false)
      unOk(); unErr()
    }
  }

  // ── No account → fullscreen login prompt ─────────────────────────────────
  if (!account) {
    return (
      <div className="home">
        <canvas ref={canvasRef} className="home-grid" />
        <div className="login-prompt">
          <div className="login-logo">⚡</div>
          <div className="login-title">COOKIE CLIENT</div>
          <div className="login-sub">Sign in to start playing</div>

          {loginStatus && (
            <div className="login-status">{loginStatus}</div>
          )}

          <button
            className="login-ms-btn"
            onClick={openMicrosoftLogin}
            disabled={loginBusy}
          >
            <span className="ms-logo">⊞</span>
            {loginBusy ? 'Opening...' : 'Sign in with Microsoft'}
          </button>
          <button
            className="login-offline-btn"
            onClick={onNavigateToAccount}
            disabled={loginBusy}
          >
            Continue offline
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="home">
      <canvas ref={canvasRef} className="home-grid" />

      <div className="home-overlay">
        {versions.length === 0 && (
          <VersionInstaller gameDir={gameDir} onInstalled={loadVersions} />
        )}

        <div className="player-block">
          <img
            className="player-skin"
            src={skinUrl}
            alt={username}
            onError={(e) => {
              ;(e.target as HTMLImageElement).src = 'https://mc-heads.net/body/MHF_Steve/120'
            }}
          />
          <span className="player-name">{username.toUpperCase()}</span>
        </div>

        <div className="launch-block">
          <div className="launch-row">
            <button
              className={`launch-btn${isLaunching ? ' launching' : ''}`}
              onClick={handleLaunch}
              disabled={isLaunching || versions.length === 0}
            >
              {isLaunching ? 'STARTING...' : 'LAUNCH'}
            </button>
            <button
              className="version-arrow"
              onClick={() => setShowDropdown((v) => !v)}
            >
              ▾
            </button>
          </div>

          <span className="version-label">
            {selectedVersion || (versions.length === 0 ? 'No version installed' : 'Select version')}
          </span>

          {showDropdown && (
            <div className="version-list">
              {versions.length === 0 ? (
                <div className="version-item disabled">No versions found</div>
              ) : (
                versions.map((v) => (
                  <div
                    key={v}
                    className={`version-item${v === selectedVersion ? ' selected' : ''}`}
                    onClick={() => { setSelectedVersion(v); setShowDropdown(false) }}
                  >
                    {v}
                  </div>
                ))
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
