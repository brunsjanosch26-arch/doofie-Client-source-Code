import { useState, useEffect } from 'react'
import { invoke } from '@tauri-apps/api/core'
import { open } from '@tauri-apps/plugin-dialog'
import './Settings.css'

interface Props {
  gameDir: string
  setGameDir: (dir: string) => void
}

interface LauncherConfig {
  ram: number
  jvm_args: string
  close_on_launch: boolean
  auto_update: boolean
}

const DEFAULT_CONFIG: LauncherConfig = {
  ram: 4,
  jvm_args: '-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200',
  close_on_launch: false,
  auto_update: true,
}

export default function Settings({ gameDir, setGameDir }: Props) {
  const [javaPath, setJavaPath] = useState('')
  const [tempDir, setTempDir] = useState(gameDir)
  const [config, setConfig] = useState<LauncherConfig>(DEFAULT_CONFIG)
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    setTempDir(gameDir)
    loadAll()
  }, [gameDir])

  const loadAll = async () => {
    try {
      const path = await invoke<string>('get_java_path')
      setJavaPath(path)
    } catch {}
    try {
      const cfg = await invoke<LauncherConfig>('get_config', { gameDir })
      setConfig(cfg)
    } catch {}
  }

  const browseDir = async () => {
    try {
      const selected = await open({ directory: true, title: 'Select .minecraft folder' })
      if (selected) setTempDir(selected as string)
    } catch {}
  }

  const browseJava = async () => {
    try {
      const selected = await open({
        title: 'Select java.exe',
        filters: [{ name: 'Executable', extensions: ['exe'] }],
      })
      if (selected) setJavaPath(selected as string)
    } catch {}
  }

  const save = async () => {
    setSaving(true)
    try {
      await invoke('set_game_directory', { path: tempDir })
      await invoke('set_java_path', { path: javaPath })
      await invoke('save_config', { gameDir: tempDir, config })
      setGameDir(tempDir)
      setSaved(true)
      setTimeout(() => setSaved(false), 2000)
    } catch (e) {
      alert(`Save failed: ${e}`)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="page">
      <div className="page-header">
        <h2>Settings</h2>
        <p>Configure game directory, Java and launcher preferences</p>
      </div>

      {/* Paths */}
      <div className="settings-group">
        <div className="settings-group-title">Paths</div>

        <div className="setting-row">
          <label>.minecraft directory</label>
          <div className="path-row">
            <input
              className="input"
              type="text"
              value={tempDir}
              onChange={(e) => setTempDir(e.target.value)}
              placeholder="C:\Users\...\AppData\Roaming\.minecraft"
            />
            <button className="btn btn-secondary" onClick={browseDir}>Browse</button>
          </div>
        </div>

        <div className="setting-row">
          <label>Java executable</label>
          <div className="path-row">
            <input
              className="input"
              type="text"
              value={javaPath}
              onChange={(e) => setJavaPath(e.target.value)}
              placeholder="C:\Program Files\Java\jdk-17\bin\java.exe"
            />
            <button className="btn btn-secondary" onClick={browseJava}>Browse</button>
          </div>
        </div>
      </div>

      {/* Performance */}
      <div className="settings-group">
        <div className="settings-group-title">Performance</div>

        <div className="setting-row">
          <label>RAM allocation — <strong>{config.ram} GB</strong></label>
          <div className="slider-row">
            <span className="slider-min">1 GB</span>
            <input
              type="range"
              className="ram-slider"
              min={1}
              max={32}
              step={1}
              value={config.ram}
              onChange={(e) => setConfig({ ...config, ram: parseInt(e.target.value) })}
            />
            <span className="slider-max">32 GB</span>
          </div>
        </div>

        <div className="setting-row">
          <label>Extra JVM arguments</label>
          <textarea
            className="input jvm-textarea"
            value={config.jvm_args}
            onChange={(e) => setConfig({ ...config, jvm_args: e.target.value })}
            rows={3}
            placeholder="-XX:+UseG1GC ..."
            spellCheck={false}
          />
        </div>
      </div>

      {/* Launcher */}
      <div className="settings-group">
        <div className="settings-group-title">Launcher Behavior</div>

        <div className="setting-row toggle-row">
          <div>
            <div className="toggle-label">Close launcher when game starts</div>
            <div className="toggle-sub">Launcher closes automatically after launch</div>
          </div>
          <button
            className={`toggle-btn${config.close_on_launch ? ' on' : ''}`}
            onClick={() => setConfig({ ...config, close_on_launch: !config.close_on_launch })}
          />
        </div>

        <div className="setting-row toggle-row">
          <div>
            <div className="toggle-label">Auto-update launcher</div>
            <div className="toggle-sub">Check for launcher updates on startup</div>
          </div>
          <button
            className={`toggle-btn${config.auto_update ? ' on' : ''}`}
            onClick={() => setConfig({ ...config, auto_update: !config.auto_update })}
          />
        </div>
      </div>

      <div className="save-row">
        <button className="btn btn-primary" onClick={save} disabled={saving}>
          {saving ? 'Saving...' : saved ? '✓ Saved' : 'Save Settings'}
        </button>
      </div>
    </div>
  )
}
