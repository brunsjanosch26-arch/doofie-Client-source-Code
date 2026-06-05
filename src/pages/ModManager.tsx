import { useState, useEffect } from 'react'
import { invoke } from '@tauri-apps/api/core'
import './ModManager.css'

interface Mod {
  id: string
  name: string
  version: string
  loader: 'Fabric' | 'Forge' | 'Quilt' | 'Any'
  enabled: boolean
  filename?: string
}

interface ModManagerProps {
  gameDir: string
}

type Filter = 'All' | 'Fabric' | 'Forge' | 'Quilt'

export default function ModManager({ gameDir }: ModManagerProps) {
  const [mods, setMods] = useState<Mod[]>([])
  const [filter, setFilter] = useState<Filter>('All')
  const [newName, setNewName] = useState('')
  const [newVersion, setNewVersion] = useState('')
  const [newLoader, setNewLoader] = useState<'Fabric' | 'Forge' | 'Quilt'>('Fabric')
  const [installedFiles, setInstalledFiles] = useState<string[]>([])

  useEffect(() => {
    if (gameDir) loadInstalledMods()
  }, [gameDir])

  const loadInstalledMods = async () => {
    try {
      const files = await invoke<string[]>('get_mod_files', { gameDir })
      setInstalledFiles(files)
    } catch {}
  }

  const addMod = () => {
    if (!newName.trim()) return
    const mod: Mod = {
      id: Date.now().toString(),
      name: newName.trim(),
      version: newVersion.trim() || '1.0.0',
      loader: newLoader,
      enabled: true,
    }
    setMods((prev) => [...prev, mod])
    setNewName('')
    setNewVersion('')
  }

  const toggleMod = (id: string) =>
    setMods((prev) => prev.map((m) => (m.id === id ? { ...m, enabled: !m.enabled } : m)))

  const removeMod = (id: string) => setMods((prev) => prev.filter((m) => m.id !== id))

  const filtered = filter === 'All' ? mods : mods.filter((m) => m.loader === filter || m.loader === 'Any')

  const FILTERS: Filter[] = ['All', 'Fabric', 'Forge', 'Quilt']

  return (
    <div className="page">
      <div className="page-header">
        <h2>Mod Manager</h2>
        <p>Enable, disable and track your Minecraft mods</p>
      </div>

      {/* Filter tabs */}
      <div className="mod-filters">
        {FILTERS.map((f) => (
          <button
            key={f}
            className={`filter-tab${filter === f ? ' active' : ''}`}
            onClick={() => setFilter(f)}
          >
            {f}
          </button>
        ))}
        <span className="mod-count">{filtered.length} mods</span>
      </div>

      {/* Installed from .minecraft/mods */}
      {installedFiles.length > 0 && (
        <div className="installed-section">
          <div className="section-label">Installed in .minecraft/mods ({installedFiles.length})</div>
          <div className="file-list">
            {installedFiles.map((f) => (
              <div key={f} className="file-row">
                <span className="file-icon">📦</span>
                <span className="file-name">{f}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Tracked mods */}
      <div className="mod-list">
        {filtered.length === 0 ? (
          <div className="empty-state">
            <span>No mods tracked yet</span>
            <p>Add mods below to track them here</p>
          </div>
        ) : (
          filtered.map((mod) => (
            <div key={mod.id} className={`mod-row${mod.enabled ? '' : ' disabled'}`}>
              <button
                className={`toggle-btn${mod.enabled ? ' on' : ''}`}
                onClick={() => toggleMod(mod.id)}
                title={mod.enabled ? 'Disable' : 'Enable'}
              />
              <div className="mod-info">
                <span className="mod-name">{mod.name}</span>
                <div className="mod-tags">
                  <span className="mod-version">v{mod.version}</span>
                  <span className={`mod-loader ${mod.loader.toLowerCase()}`}>{mod.loader}</span>
                </div>
              </div>
              <button className="btn btn-danger small" onClick={() => removeMod(mod.id)}>
                Remove
              </button>
            </div>
          ))
        )}
      </div>

      {/* Add mod form */}
      <div className="add-mod-card">
        <div className="add-mod-title">Add Mod</div>
        <div className="add-mod-form">
          <input
            className="input"
            type="text"
            placeholder="Mod name"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && addMod()}
          />
          <input
            className="input small-input"
            type="text"
            placeholder="Version"
            value={newVersion}
            onChange={(e) => setNewVersion(e.target.value)}
          />
          <select
            className="input small-input"
            value={newLoader}
            onChange={(e) => setNewLoader(e.target.value as any)}
          >
            <option value="Fabric">Fabric</option>
            <option value="Forge">Forge</option>
            <option value="Quilt">Quilt</option>
          </select>
          <button className="btn btn-primary" onClick={addMod} disabled={!newName.trim()}>
            Add
          </button>
        </div>
      </div>
    </div>
  )
}
