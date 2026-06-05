import { useState, useEffect } from 'react'
import { invoke } from '@tauri-apps/api/core'
import { listen } from '@tauri-apps/api/event'
import { getCurrentWindow } from '@tauri-apps/api/window'
import './App.css'
import Home from './pages/Home'
import Settings from './pages/Settings'
import ModManager from './pages/ModManager'
import AccountManager from './pages/AccountManager'
import ShaderManager from './pages/ShaderManager'
import CapeManager from './pages/CapeManager'
import ServerList from './pages/ServerList'
import Sidebar, { type Page } from './components/Sidebar'
import NewsPanel from './components/NewsPanel'
import { BACKEND_URLS } from './config'

export interface Account {
  id: string
  username: string
  uuid: string
  account_type: string
  is_active: boolean
  access_token?: string
  refresh_token?: string
  skin_url?: string
  expires_at?: number
}

interface VersionInfo {
  version: string
  online_count?: number
  changelog?: string
  download_url?: string
  required?: boolean
}

function App() {
  const [currentPage, setCurrentPage] = useState<Page>('home')
  const [gameDir, setGameDir] = useState<string>('')
  const [activeAccount, setActiveAccount] = useState<Account | null>(null)
  const [onlineCount, setOnlineCount] = useState(0)
  const [updateAvailable, setUpdateAvailable] = useState<VersionInfo | null>(null)

  useEffect(() => {
    loadGameDirectory()
    fetchBackendInfo()
  }, [])

  // Auto-refresh expired tokens on startup
  useEffect(() => {
    if (!gameDir) return
    invoke<Account[]>('refresh_accounts', { gameDir })
      .then((accounts) => {
        const active = accounts.find((a) => a.is_active)
        if (active) setActiveAccount(active)
      })
      .catch(() => {})
  }, [gameDir])

  // Reload active account whenever Microsoft login completes
  useEffect(() => {
    let unlisten: (() => void) | null = null
    listen('ms_login_complete', () => {
      if (gameDir) loadActiveAccount(gameDir)
    }).then((fn) => { unlisten = fn })
    return () => { if (unlisten) unlisten() }
  }, [gameDir])

  const loadGameDirectory = async () => {
    try {
      const dir = await invoke<string>('get_game_directory')
      setGameDir(dir)
      await loadActiveAccount(dir)
    } catch {}
  }

  const loadActiveAccount = async (dir: string) => {
    try {
      const accounts = await invoke<Account[]>('get_accounts', { gameDir: dir })
      const active = accounts.find((a) => a.is_active)
      setActiveAccount(active ?? null)
    } catch {}
  }

  const fetchBackendInfo = async () => {
    try {
      const data: VersionInfo = await fetch(BACKEND_URLS.version).then((r) => r.json())
      if (data.online_count) setOnlineCount(data.online_count)
      const current = await invoke<{ current_version: string }>('check_for_updates').catch(() => ({ current_version: '2.0.0' }))
      if (data.version && data.version !== current.current_version) setUpdateAvailable(data)
    } catch {}
  }

  const win = getCurrentWindow()

  return (
    <div className="app">
      {/* Custom titlebar */}
      <div className="titlebar">
        <div className="titlebar-left">
          <span className="logo-bolt">⚡</span>
          <span className="logo-text">COOKIE CLIENT</span>
        </div>
        <div className="titlebar-center">
          <div className="online-indicator">
            <span className="online-dot" />
            <span>{onlineCount > 0 ? `${onlineCount.toLocaleString()} ONLINE` : 'CONNECTING...'}</span>
          </div>
          <span className="version-chip">v2.0</span>
          {updateAvailable && (
            <span className="update-chip" title={updateAvailable.changelog ?? ''}>
              ↑ Update {updateAvailable.version}
            </span>
          )}
        </div>
        <div className="titlebar-right">
          <button className="win-btn" onClick={() => win.minimize()}>─</button>
          <button className="win-btn" onClick={() => win.toggleMaximize()}>□</button>
          <button className="win-btn close" onClick={() => win.close()}>✕</button>
        </div>
      </div>

      {/* Body */}
      <div className="layout">
        <Sidebar currentPage={currentPage} onNavigate={setCurrentPage} account={activeAccount} />

        <main className="main-content">
          {currentPage === 'home' && (
            <Home gameDir={gameDir} account={activeAccount} onNavigateToAccount={() => setCurrentPage('account')} />
          )}
          {currentPage === 'mods' && <ModManager gameDir={gameDir} />}
          {currentPage === 'shaders' && <ShaderManager gameDir={gameDir} />}
          {currentPage === 'servers' && <ServerList />}
          {currentPage === 'capes' && <CapeManager account={activeAccount} />}
          {currentPage === 'account' && (
            <AccountManager gameDir={gameDir} onAccountChange={setActiveAccount} />
          )}
          {currentPage === 'settings' && (
            <Settings gameDir={gameDir} setGameDir={setGameDir} />
          )}
        </main>

        <NewsPanel />
      </div>
    </div>
  )
}

export default App
