import { useState, useEffect } from 'react'
import { invoke } from '@tauri-apps/api/core'
import './ShaderManager.css'

interface Shader {
  name: string
  path: string
}

interface Props {
  gameDir: string
}

export default function ShaderManager({ gameDir }: Props) {
  const [shaders, setShaders] = useState<Shader[]>([])

  useEffect(() => {
    if (gameDir) load()
  }, [gameDir])

  const load = async () => {
    try {
      const list = await invoke<Shader[]>('get_shaders', { gameDir })
      setShaders(list)
    } catch {}
  }

  const remove = async (name: string) => {
    try {
      await invoke('delete_shader', { name, gameDir })
      await load()
    } catch (e) {
      alert(`Error removing shader: ${e}`)
    }
  }

  const shaderpacksPath = `${gameDir}\\shaderpacks`

  return (
    <div className="page">
      <div className="page-header">
        <h2>Shader Manager</h2>
        <p>Install and manage OptiFine / Iris shader packs</p>
      </div>

      <div className="shader-info-bar">
        <span className="path-label">Shader folder:</span>
        <code className="path-value">{shaderpacksPath}</code>
        <button className="btn btn-secondary small" onClick={load}>
          Refresh
        </button>
      </div>

      {shaders.length === 0 ? (
        <div className="empty-state">
          <span>✦ No shaders installed</span>
          <p>Copy a .zip shader pack into your shaderpacks folder, then hit Refresh</p>
        </div>
      ) : (
        <div className="shader-grid">
          {shaders.map((s) => (
            <div key={s.name} className="shader-card">
              <div className="shader-preview">
                <div className="shader-glow" />
                <span className="shader-icon">✦</span>
              </div>
              <div className="shader-details">
                <span className="shader-name">{s.name}</span>
                <span className="shader-type">
                  {s.name.endsWith('.zip') ? 'ZIP Pack' : 'Folder Pack'}
                </span>
              </div>
              <button className="btn btn-danger small" onClick={() => remove(s.name)}>
                Remove
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
