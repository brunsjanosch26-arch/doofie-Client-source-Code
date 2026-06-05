import { useState, useEffect } from 'react'
import { invoke } from '@tauri-apps/api/core'
import { listen } from '@tauri-apps/api/event'
import type { Account } from '../App'
import './AccountManager.css'

interface Props {
  gameDir: string
  onAccountChange: (account: Account) => void
}

export default function AccountManager({ gameDir, onAccountChange }: Props) {
  const [accounts, setAccounts] = useState<Account[]>([])
  const [offlineName, setOfflineName] = useState('')
  const [msLoading, setMsLoading] = useState(false)
  const [msStatus, setMsStatus] = useState<'idle' | 'waiting' | 'success' | 'error'>('idle')
  const [msError, setMsError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (gameDir) loadAccounts()
  }, [gameDir])

  const loadAccounts = async () => {
    try {
      const list = await invoke<Account[]>('get_accounts', { gameDir })
      setAccounts(list)
    } catch {}
  }

  const loginMicrosoft = async () => {
    setMsLoading(true)
    setMsStatus('waiting')
    setMsError('')

    // Listen for result events from Rust
    const unlistenOk = await listen('ms_login_complete', async () => {
      setMsStatus('success')
      setMsLoading(false)
      unlistenOk()
      unlistenErr()
      await loadAccounts()
      const updated = await invoke<Account[]>('get_accounts', { gameDir })
      const active = updated.find((a) => a.is_active)
      if (active) onAccountChange(active)
      setTimeout(() => setMsStatus('idle'), 2500)
    })

    const unlistenErr = await listen<string>('ms_login_error', (e) => {
      setMsStatus('error')
      setMsError(e.payload)
      setMsLoading(false)
      unlistenOk()
      unlistenErr()
    })

    try {
      await invoke('start_microsoft_webview_login', { gameDir })
    } catch (e) {
      setMsStatus('error')
      setMsError(`${e}`)
      setMsLoading(false)
      unlistenOk()
      unlistenErr()
    }
  }

  const addOffline = async () => {
    if (!offlineName.trim()) return
    setBusy(true)
    try {
      await invoke('add_offline_account', { username: offlineName.trim(), gameDir })
      setOfflineName('')
      await loadAccounts()
      const updated = await invoke<Account[]>('get_accounts', { gameDir })
      const active = updated.find((a) => a.is_active)
      if (active) onAccountChange(active)
    } catch (e) {
      alert(`Error: ${e}`)
    } finally {
      setBusy(false)
    }
  }

  const setActive = async (id: string) => {
    await invoke('set_active_account', { id, gameDir })
    await loadAccounts()
    const updated = await invoke<Account[]>('get_accounts', { gameDir })
    const active = updated.find((a) => a.is_active)
    if (active) onAccountChange(active)
  }

  const remove = async (id: string) => {
    await invoke('remove_account', { id, gameDir })
    await loadAccounts()
  }

  return (
    <div className="page">
      <div className="page-header">
        <h2>Account Manager</h2>
        <p>Manage your Minecraft accounts</p>
      </div>

      {/* Account list */}
      <div className="account-list">
        {accounts.length === 0 && (
          <div className="empty-state">No accounts yet — add one below</div>
        )}
        {accounts.map((acc) => (
          <div key={acc.id} className={`account-row${acc.is_active ? ' active' : ''}`}>
            <img
              className="account-avatar"
              src={`https://mc-heads.net/avatar/${acc.uuid}/36`}
              alt={acc.username}
              onError={(e) => {
                ;(e.target as HTMLImageElement).src =
                  'https://mc-heads.net/avatar/MHF_Steve/36'
              }}
            />
            <div className="account-meta">
              <span className="account-name">{acc.username}</span>
              <span className={`account-badge ${acc.account_type.toLowerCase()}`}>
                {acc.account_type}
              </span>
            </div>
            <div className="account-actions">
              {acc.is_active ? (
                <span className="active-chip">ACTIVE</span>
              ) : (
                <button className="btn btn-secondary small" onClick={() => setActive(acc.id)}>
                  Set Active
                </button>
              )}
              <button className="btn btn-danger small" onClick={() => remove(acc.id)}>
                Remove
              </button>
            </div>
          </div>
        ))}
      </div>

      {/* Add Microsoft account */}
      <div className="add-section">
        <h3>Add Account</h3>

        <div className="add-card">
          <div className="add-card-header">
            <span className="ms-icon">⊞</span>
            <div>
              <div className="add-card-title">Microsoft Account</div>
              <div className="add-card-sub">
                Opens a secure Microsoft login window — no credentials stored in the launcher
              </div>
            </div>
          </div>

          {msStatus === 'error' && (
            <div className="ms-error">{msError}</div>
          )}

          {msStatus === 'success' && (
            <div className="ms-success">✓ Login successful!</div>
          )}

          {msStatus === 'waiting' && (
            <div className="ms-waiting">
              <div className="spinner" />
              <span>Microsoft login window is open — sign in there</span>
            </div>
          )}

          {(msStatus === 'idle' || msStatus === 'error') && (
            <button
              className="btn btn-primary ms-login-btn"
              onClick={loginMicrosoft}
              disabled={msLoading}
            >
              <span className="ms-logo">⊞</span>
              Sign in with Microsoft
            </button>
          )}
        </div>

        <div className="divider-line"><span>or</span></div>

        {/* Offline */}
        <div className="add-card">
          <div className="add-card-header">
            <span className="offline-icon">◉</span>
            <div>
              <div className="add-card-title">Offline Account</div>
              <div className="add-card-sub">No login required — offline & LAN servers only</div>
            </div>
          </div>
          <div className="offline-row">
            <input
              className="input"
              type="text"
              placeholder="Username"
              value={offlineName}
              onChange={(e) => setOfflineName(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && addOffline()}
            />
            <button
              className="btn btn-primary"
              onClick={addOffline}
              disabled={busy || !offlineName.trim()}
            >
              Add
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
