import { useState, useEffect } from 'react'
import { invoke } from '@tauri-apps/api/core'
import { listen } from '@tauri-apps/api/event'
import './VersionInstaller.css'

interface ManifestVersion {
  id: string
  version_type: string
  url: string
}

interface DownloadProgress {
  step: string
  current: number
  total: number
  percent: number
}

interface Props {
  gameDir: string
  onInstalled: () => void
}

export default function VersionInstaller({ gameDir, onInstalled }: Props) {
  const [versions, setVersions] = useState<ManifestVersion[]>([])
  const [selected, setSelected] = useState('')
  const [showSnapshots, setShowSnapshots] = useState(false)
  const [downloading, setDownloading] = useState(false)
  const [progress, setProgress] = useState<DownloadProgress | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    loadVersions()
  }, [showSnapshots])

  const loadVersions = async () => {
    try {
      const list = await invoke<ManifestVersion[]>('get_available_versions', {
        releaseOnly: !showSnapshots,
      })
      setVersions(list)
      if (list.length > 0 && !selected) setSelected(list[0].id)
    } catch (e) {
      setError(`Could not fetch versions: ${e}`)
    }
  }

  const install = async () => {
    if (!selected || !gameDir) return
    setDownloading(true)
    setError('')
    setProgress({ step: 'Starting...', current: 0, total: 100, percent: 0 })

    const unlisten = await listen<DownloadProgress>('download_progress', (e) => {
      setProgress(e.payload)
    })

    try {
      await invoke('download_version', { version: selected, gameDir })
      onInstalled()
    } catch (e) {
      setError(`Download failed: ${e}`)
    } finally {
      unlisten()
      setDownloading(false)
      setProgress(null)
    }
  }

  return (
    <div className="version-installer">
      <div className="installer-icon">⬇</div>
      <div className="installer-title">No Minecraft version installed</div>
      <div className="installer-sub">Download a version to start playing</div>

      {error && <div className="installer-error">{error}</div>}

      {!downloading ? (
        <>
          <div className="installer-controls">
            <select
              className="version-select"
              value={selected}
              onChange={(e) => setSelected(e.target.value)}
            >
              {versions.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.id} {v.version_type !== 'release' ? `(${v.version_type})` : ''}
                </option>
              ))}
            </select>

            <label className="snapshot-toggle">
              <input
                type="checkbox"
                checked={showSnapshots}
                onChange={(e) => setShowSnapshots(e.target.checked)}
              />
              Show snapshots
            </label>
          </div>

          <button
            className="install-btn"
            onClick={install}
            disabled={!selected || !gameDir}
          >
            Install {selected}
          </button>
        </>
      ) : (
        <div className="progress-area">
          <div className="progress-step">{progress?.step}</div>
          <div className="progress-bar-track">
            <div
              className="progress-bar-fill"
              style={{ width: `${progress?.percent ?? 0}%` }}
            />
          </div>
          <div className="progress-percent">{progress?.percent ?? 0}%</div>
        </div>
      )}
    </div>
  )
}
