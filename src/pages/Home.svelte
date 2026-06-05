<script lang="ts">
  import { onMount, onDestroy } from 'svelte'
  import { invoke } from '@tauri-apps/api/core'
  import { listen } from '@tauri-apps/api/event'
  import { SkinViewer, WalkingAnimation } from 'skinview3d'
  import { activeAccount, gameDir, currentPage, backgroundEffect } from '../stores/appStore'
  import GameConsole from '../components/GameConsole.svelte'

  interface GameProfile {
    id: string
    name: string
    version: string
    java_args: string
    ram: number
    mods_enabled: string[]
    shader: string | null
  }

  const PRESET_VERSIONS = ['1.8', '1.21.10', '1.21.11', '26.1.2']

  let versions: string[] = []
  let profiles: GameProfile[] = []
  let selectionKey = ''
  let selectedVersion = ''
  let selectedProfile: GameProfile | null = null
  let isLaunching = false
  let launchStatus = ''
  let loginBusy = false
  let loginStatus = ''
  let canvas: HTMLCanvasElement
  let gridCanvas: HTMLCanvasElement
  let skinViewer: SkinViewer | null = null
  let animFrame = 0
  let unlisten: (() => void)[] = []

  let showConsole = false
  let consoleMaximized = false

  // How far the launch bar sits above the console
  $: launchBarBottom = showConsole ? (consoleMaximized ? 8 : 246) : 28

  // 3D Skin Viewer
  function initSkinViewer(uuid: string, skinUrl?: string) {
    if (skinViewer) { skinViewer.dispose(); skinViewer = null }
    if (!canvas) return

    skinViewer = new SkinViewer({
      canvas,
      width: 380,
      height: 600,
      skin: skinUrl ?? `https://mc-heads.net/skin/${uuid}`,
    })
    const anim = new WalkingAnimation()
    anim.speed = 0.4
    skinViewer.animation = anim
    skinViewer.autoRotate = false
    skinViewer.camera.position.set(-8, 4, 52)
    skinViewer.camera.lookAt(0, 0, 0)
  }

  // Background animation engine
  function startBackground(effect: string) {
    if (!gridCanvas) return
    cancelAnimationFrame(animFrame)
    const ctx = gridCanvas.getContext('2d')!

    if (effect === 'retrogrid') {
      let offset = 0
      const draw = () => {
        gridCanvas.width = gridCanvas.offsetWidth
        gridCanvas.height = gridCanvas.offsetHeight
        const W = gridCanvas.width, H = gridCanvas.height
        const bg = ctx.createRadialGradient(W/2, H*0.38, 0, W/2, H*0.38, W*0.75)
        bg.addColorStop(0, '#0d1e42'); bg.addColorStop(0.5, '#080e22'); bg.addColorStop(1, '#04070f')
        ctx.fillStyle = bg; ctx.fillRect(0, 0, W, H)
        const horizon = H * 0.5, COLS = 18, colSpacing = W / COLS, ROWS = 22, rowSpacing = 55
        offset = (offset + 0.4) % rowSpacing
        ctx.strokeStyle = 'rgba(50,110,255,0.18)'; ctx.lineWidth = 1
        for (let i = 0; i <= COLS; i++) {
          const xB = i * colSpacing, xH = W/2 + ((i - COLS/2) / COLS) * W * 0.05
          ctx.beginPath(); ctx.moveTo(xB, H); ctx.lineTo(xH, horizon); ctx.stroke()
        }
        for (let j = 0; j < ROWS; j++) {
          const rawY = horizon + ((j * rowSpacing + offset) / (ROWS * rowSpacing)) * (H - horizon) * 1.6
          if (rawY > H) continue
          const t = (rawY - horizon) / (H - horizon)
          ctx.beginPath(); ctx.moveTo(W/2 - t*W*0.7, rawY); ctx.lineTo(W/2 + t*W*0.7, rawY); ctx.stroke()
        }
        animFrame = requestAnimationFrame(draw)
      }; draw()

    } else if (effect === 'particles') {
      gridCanvas.width = gridCanvas.offsetWidth; gridCanvas.height = gridCanvas.offsetHeight
      const W = gridCanvas.width, H = gridCanvas.height
      const pts = Array.from({length: 80}, () => ({
        x: Math.random()*W, y: Math.random()*H,
        vx: (Math.random()-0.5)*0.4, vy: (Math.random()-0.5)*0.4,
        r: Math.random()*2+1, o: Math.random()*0.5+0.2
      }))
      const draw = () => {
        ctx.fillStyle = '#080e22'; ctx.fillRect(0, 0, W, H)
        pts.forEach(p => {
          p.x += p.vx; p.y += p.vy
          if (p.x < 0) p.x = W; if (p.x > W) p.x = 0
          if (p.y < 0) p.y = H; if (p.y > H) p.y = 0
          ctx.beginPath(); ctx.arc(p.x, p.y, p.r, 0, Math.PI*2)
          ctx.fillStyle = `rgba(100,160,255,${p.o})`; ctx.fill()
        })
        pts.forEach((a, i) => pts.slice(i+1).forEach(b => {
          const d = Math.hypot(a.x-b.x, a.y-b.y)
          if (d < 100) { ctx.strokeStyle = `rgba(80,140,255,${0.15*(1-d/100)})`; ctx.lineWidth=0.5; ctx.beginPath(); ctx.moveTo(a.x,a.y); ctx.lineTo(b.x,b.y); ctx.stroke() }
        }))
        animFrame = requestAnimationFrame(draw)
      }; draw()

    } else if (effect === 'matrix') {
      gridCanvas.width = gridCanvas.offsetWidth; gridCanvas.height = gridCanvas.offsetHeight
      const W = gridCanvas.width, H = gridCanvas.height
      const cols = Math.floor(W / 14), drops = Array(cols).fill(0)
      const chars = '0123456789ABCDEF'
      const draw = () => {
        ctx.fillStyle = 'rgba(8,14,34,0.05)'; ctx.fillRect(0, 0, W, H)
        ctx.fillStyle = '#0f0'; ctx.font = '13px monospace'
        drops.forEach((y, i) => {
          const c = chars[Math.floor(Math.random()*chars.length)]
          ctx.fillStyle = Math.random() > 0.95 ? '#fff' : `rgba(0,${Math.floor(150+Math.random()*105)},0,0.9)`
          ctx.fillText(c, i*14, y*14)
          if (y*14 > H && Math.random() > 0.975) drops[i] = 0
          else drops[i]++
        })
        animFrame = requestAnimationFrame(draw)
      }; draw()

    } else if (effect === 'snow') {
      gridCanvas.width = gridCanvas.offsetWidth; gridCanvas.height = gridCanvas.offsetHeight
      const W = gridCanvas.width, H = gridCanvas.height
      const flakes = Array.from({length: 120}, () => ({
        x: Math.random()*W, y: Math.random()*H,
        r: Math.random()*2.5+0.5, s: Math.random()*1+0.3, drift: (Math.random()-0.5)*0.3
      }))
      const draw = () => {
        ctx.fillStyle = '#080e22'; ctx.fillRect(0,0,W,H)
        flakes.forEach(f => {
          f.y += f.s; f.x += f.drift
          if (f.y > H) { f.y = 0; f.x = Math.random()*W }
          if (f.x < 0) f.x = W; if (f.x > W) f.x = 0
          ctx.beginPath(); ctx.arc(f.x,f.y,f.r,0,Math.PI*2)
          ctx.fillStyle = `rgba(220,240,255,${0.4+f.r/4})`; ctx.fill()
        })
        animFrame = requestAnimationFrame(draw)
      }; draw()

    } else if (effect === 'nebula') {
      let t = 0
      const draw = () => {
        gridCanvas.width = gridCanvas.offsetWidth; gridCanvas.height = gridCanvas.offsetHeight
        const W = gridCanvas.width, H = gridCanvas.height
        ctx.fillStyle = '#080e22'; ctx.fillRect(0,0,W,H)
        for (let i=0; i<6; i++) {
          const x = W/2 + Math.sin(t*0.3+i)*W*0.3, y = H/2 + Math.cos(t*0.2+i)*H*0.25
          const g = ctx.createRadialGradient(x,y,0,x,y,120)
          g.addColorStop(0,`hsla(${200+i*30+t*5},80%,60%,0.06)`)
          g.addColorStop(1,'transparent')
          ctx.fillStyle=g; ctx.fillRect(0,0,W,H)
        }
        t+=0.015; animFrame=requestAnimationFrame(draw)
      }; draw()

    } else {
      // 'none' - just clear
      ctx.fillStyle = '#080e22'; ctx.fillRect(0, 0, gridCanvas.width, gridCanvas.height)
    }
  }

  async function loadVersions(dir: string) {
    try {
      const installed = await invoke<string[]>('get_installed_versions', { gameDir: dir })
      // Merge presets with installed, deduplicated
      versions = [...new Set([...PRESET_VERSIONS, ...installed])]
      profiles = await invoke<GameProfile[]>('get_profiles', { gameDir: dir }).catch(() => [])
      // Default selection: last profile, else first preset version
      if (profiles.length > 0) {
        selectionKey = `p:${profiles[profiles.length - 1].id}`
      } else {
        selectionKey = `v:${versions[versions.length - 1]}`
      }
    } catch {
      versions = [...PRESET_VERSIONS]
    }
  }

  // Derive selectedVersion and selectedProfile from selectionKey
  $: {
    if (selectionKey.startsWith('v:')) {
      selectedVersion = selectionKey.slice(2)
      selectedProfile = null
    } else if (selectionKey.startsWith('p:')) {
      const pid = selectionKey.slice(2)
      selectedProfile = profiles.find(p => p.id === pid) ?? null
      selectedVersion = selectedProfile?.version ?? ''
    }
  }

  async function launch() {
    if (!$activeAccount) return
    isLaunching = true
    launchStatus = ''
    showConsole = true
    consoleMaximized = false
    try {
      const javaPath = await invoke<string>('get_java_path')
      if (selectedProfile) {
        let versionToUse = selectedProfile.version

        // Determine the base MC version (extract from Fabric string if needed)
        let baseVersion = versionToUse
        if (baseVersion.toLowerCase().includes('fabric')) {
          const parts = baseVersion.split('-')
          baseVersion = parts[parts.length - 1]
        }

        // If profile has mods, ensure_fabric handles MC download + Fabric install
        if (selectedProfile.mods_enabled.length > 0) {
          launchStatus = 'Prüfe / lade Minecraft & Fabric...'
          try {
            versionToUse = await invoke<string>('ensure_fabric', {
              mcVersion: baseVersion,
              gameDir: $gameDir,
            })
          } catch (e) {
            launchStatus = `Fehler: ${e}`
            setTimeout(() => { launchStatus = ''; isLaunching = false }, 6000)
            return
          }
          const instanceDir = `${$gameDir}\\instances\\${selectedProfile.id}`
          launchStatus = 'Aktiviere Mods...'
          await invoke('apply_profile_mods', {
            gameDir: instanceDir,
            modsEnabled: selectedProfile.mods_enabled,
          })
        } else {
          // Vanilla: just ensure base MC is downloaded
          const installed = await invoke<string[]>('get_installed_versions', { gameDir: $gameDir }).catch(() => [] as string[])
          if (!installed.includes(baseVersion)) {
            launchStatus = `Lade Minecraft ${baseVersion}...`
            try {
              await invoke('download_version', { version: baseVersion, gameDir: $gameDir })
            } catch (e) {
              launchStatus = `Fehler: ${e}`
              setTimeout(() => { launchStatus = ''; isLaunching = false }, 6000)
              return
            }
          }
        }

        launchStatus = 'Starte...'
        await invoke('launch_with_profile', {
          version: versionToUse,
          gameDir: $gameDir,
          instanceDir: `${$gameDir}\\instances\\${selectedProfile.id}`,
          javaPath,
          username: $activeAccount.username,
          uuid: $activeAccount.uuid ?? '',
          accessToken: $activeAccount.access_token ?? '0',
          ram: selectedProfile.ram,
          jvmArgs: selectedProfile.java_args,
        })
      } else if (selectedVersion) {
        const config = await invoke<{ ram: number }>('get_config', { gameDir: $gameDir }).catch(() => ({ ram: 4 }))

        // Download version if not yet installed
        const installed = await invoke<string[]>('get_installed_versions', { gameDir: $gameDir }).catch(() => [] as string[])
        if (!installed.includes(selectedVersion)) {
          launchStatus = `Lade Minecraft ${selectedVersion}...`
          try {
            await invoke('download_version', { version: selectedVersion, gameDir: $gameDir })
          } catch (e) {
            launchStatus = `Fehler: ${e}`
            setTimeout(() => { launchStatus = ''; isLaunching = false }, 6000)
            return
          }
        }

        launchStatus = 'Starte...'
        await invoke('launch_minecraft', {
          version: selectedVersion,
          gameDir: $gameDir,
          javaPath,
          username: $activeAccount.username,
          uuid: $activeAccount.uuid ?? '',
          accessToken: $activeAccount.access_token ?? '0',
          ram: config.ram,
        })
      }
    } catch (e) {
      launchStatus = `Fehler: ${e}`
      setTimeout(() => launchStatus = '', 6000)
    }
    setTimeout(() => { isLaunching = false; launchStatus = '' }, 3000)
  }

  async function openLogin() {
    loginBusy = true
    loginStatus = 'Login-Fenster wird geöffnet...'

    const u1 = await listen('ms_login_complete', () => {
      loginStatus = ''
      loginBusy = false
      unlisten.forEach(f => f())
    })
    const u2 = await listen<string>('ms_login_error', e => {
      loginStatus = `Fehler: ${e.payload}`
      loginBusy = false
      unlisten.forEach(f => f())
    })
    unlisten = [u1, u2]

    try {
      await invoke('start_microsoft_webview_login', { gameDir: $gameDir })
    } catch (e) {
      loginStatus = `Fehler: ${e}`
      loginBusy = false
    }
  }

  onMount(() => {
    startBackground($backgroundEffect)
    if ($gameDir) loadVersions($gameDir)

    const unsub = gameDir.subscribe(dir => { if (dir) loadVersions(dir) })
    const unsub2 = activeAccount.subscribe(acc => {
      if (acc && canvas) initSkinViewer(acc.uuid, acc.skin_url)
    })
    const unsub3 = backgroundEffect.subscribe(effect => startBackground(effect))

    if ($activeAccount && canvas) initSkinViewer($activeAccount.uuid, $activeAccount.skin_url)

    return () => { unsub(); unsub2(); unsub3() }
  })

  onDestroy(() => {
    cancelAnimationFrame(animFrame)
    skinViewer?.dispose()
    unlisten.forEach(f => f())
  })
</script>

<div class="home">
  <canvas bind:this={gridCanvas} class="grid-bg" />

  {#if !$activeAccount}
    <!-- Login prompt -->
    <div class="login-prompt">
      <div class="login-logo">⚡</div>
      <div class="login-title">COOKIE CLIENT</div>
      <div class="login-sub">Melde dich an um zu spielen</div>

      {#if loginStatus}
        <div class="login-status">{loginStatus}</div>
      {/if}

      <button class="login-ms-btn" on:click={openLogin} disabled={loginBusy}>
        <span class="ms-icon">⊞</span>
        {loginBusy ? 'Öffne...' : 'Mit Microsoft anmelden'}
      </button>
      <button class="login-offline-btn" on:click={() => currentPage.set('account')} disabled={loginBusy}>
        Offline fortfahren
      </button>
    </div>

  {:else}
    <!-- Play screen — skin large and centered, controls pinned to bottom -->
    <div class="play-screen">

      <!-- 3D Skin Viewer — center of screen -->
      <div class="skin-section">
        <div class="player-name">{$activeAccount.username.toUpperCase()}</div>
        <canvas bind:this={canvas} class="skin-canvas" />
      </div>

    </div>

    <!-- Launch controls — fixed at bottom center, shifts up when console is open -->
    <div class="launch-bar" style="bottom: {launchBarBottom}px">
      {#if false}<!-- versions always present via presets -->
        <div class="no-version"></div>
      {:else}
        <select class="version-select" bind:value={selectionKey}>
          {#if profiles.length > 0}
            <optgroup label="── PROFILE ──">
              {#each profiles as p}
                <option value="p:{p.id}">{p.name} ({p.version})</option>
              {/each}
            </optgroup>
          {/if}
          {#if versions.length > 0}
            <optgroup label="── VERSIONEN ──">
              {#each versions as v}
                <option value="v:{v}">{v}</option>
              {/each}
            </optgroup>
          {/if}
        </select>
        <button
          class="launch-btn"
          class:launching={isLaunching}
          on:click={launch}
          disabled={isLaunching || (versions.length === 0 && profiles.length === 0)}
        >
          {isLaunching ? (launchStatus || 'STARTET...') : 'SPIELEN'}
        </button>
      {/if}
    </div>
    {#if launchStatus && !isLaunching}
      <div class="launch-error" style="bottom: {launchBarBottom + 56}px">{launchStatus}</div>
    {/if}

    {#if showConsole}
      <GameConsole
        bind:maximized={consoleMaximized}
        on:close={() => showConsole = false}
      />
    {/if}
  {/if}
</div>

<style>
  .home { position: relative; width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; overflow: hidden; }

  .grid-bg { position: absolute; inset: 0; width: 100%; height: 100%; }

  /* Login */
  .login-prompt {
    position: relative; z-index: 1;
    display: flex; flex-direction: column; align-items: center; gap: 14px;
  }
  .login-logo { font-size: 48px; color: #3d7ff0; filter: drop-shadow(0 0 20px #3d7ff0); }
  .login-title { font-size: 22px; font-weight: 900; letter-spacing: 4px; color: #fff; }
  .login-sub { font-size: 13px; color: #666; margin-bottom: 8px; }
  .login-status { font-size: 12px; color: #ffaa44; }

  .login-ms-btn {
    display: flex; align-items: center; gap: 10px;
    padding: 12px 28px; border-radius: 8px;
    background: rgba(61, 127, 240, 0.22);
    backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px);
    border: 1px solid rgba(61, 127, 240, 0.45);
    color: #fff;
    font-size: 14px; font-weight: 700; cursor: pointer;
    min-width: 220px; justify-content: center;
    box-shadow: inset 0 1px 0 rgba(255,255,255,0.18), 0 4px 18px rgba(0,0,0,0.35), 0 0 16px rgba(61,127,240,0.2);
    transition: all 0.2s;
  }
  .login-ms-btn:hover:not(:disabled) {
    background: rgba(61, 127, 240, 0.35);
    border-color: rgba(61, 127, 240, 0.65);
    box-shadow: inset 0 1px 0 rgba(255,255,255,0.22), 0 4px 20px rgba(0,0,0,0.35), 0 0 28px rgba(61,127,240,0.35);
    transform: translateY(-1px);
  }
  .login-ms-btn:disabled { opacity: 0.5; cursor: default; }
  .ms-icon { font-size: 16px; }

  .login-offline-btn {
    background: rgba(255, 255, 255, 0.05);
    backdrop-filter: blur(10px);
    -webkit-backdrop-filter: blur(10px);
    border: 1px solid rgba(255,255,255,0.12);
    border-radius: 8px; padding: 8px 24px; color: #888;
    font-size: 13px; cursor: pointer;
    box-shadow: inset 0 1px 0 rgba(255,255,255,0.08);
    transition: all 0.2s;
  }
  .login-offline-btn:hover:not(:disabled) {
    background: rgba(255, 255, 255, 0.09);
    border-color: rgba(255,255,255,0.2);
    color: #ccc;
  }

  /* Play screen — skin fills center */
  .play-screen {
    position: absolute; inset: 0; z-index: 1;
    display: flex; align-items: center; justify-content: center;
  }

  .skin-section { display: flex; flex-direction: column; align-items: center; gap: 6px; }
  .skin-canvas { border-radius: 14px; filter: drop-shadow(0 16px 48px rgba(0,0,0,0.7)); }
  .player-name {
    font-size: 18px; font-weight: 800; letter-spacing: 3px;
    color: #fff;
    text-shadow: 0 0 18px rgba(61,127,240,0.7), 0 2px 8px rgba(0,0,0,0.8);
  }

  /* Controls bar pinned to bottom */
  .launch-bar {
    position: absolute; left: 50%; transform: translateX(-50%);
    z-index: 2; display: flex; align-items: center; gap: 10px;
    background: rgba(8,14,34,0.7); backdrop-filter: blur(12px);
    border: 1px solid rgba(255,255,255,0.1); border-radius: 12px;
    padding: 10px 16px;
    transition: bottom 0.2s ease;
  }

  .version-select {
    background: rgba(255,255,255,0.06);
    border: 1px solid rgba(255,255,255,0.12);
    border-radius: 8px; padding: 7px 12px;
    color: #ccc; font-size: 13px; cursor: pointer; outline: none; min-width: 180px;
  }

  .launch-btn {
    padding: 10px 28px; border-radius: 9px; flex-shrink: 0;
    background: rgba(61, 127, 240, 0.22);
    backdrop-filter: blur(14px);
    -webkit-backdrop-filter: blur(14px);
    border: 1px solid rgba(61, 127, 240, 0.5);
    color: #fff; font-size: 14px;
    font-weight: 800; letter-spacing: 3px; cursor: pointer;
    box-shadow:
      inset 0 1px 0 rgba(255, 255, 255, 0.2),
      0 0 24px rgba(61, 127, 240, 0.3),
      0 4px 18px rgba(0, 0, 0, 0.4);
    transition: all 0.2s;
  }
  .launch-btn:hover:not(:disabled) {
    background: rgba(61, 127, 240, 0.35);
    border-color: rgba(61, 127, 240, 0.7);
    box-shadow:
      inset 0 1px 0 rgba(255, 255, 255, 0.25),
      0 0 40px rgba(61, 127, 240, 0.5),
      0 4px 22px rgba(0, 0, 0, 0.4);
    transform: translateY(-1px);
  }
  .launch-btn:disabled { opacity: 0.45; cursor: default; }
  .launch-btn.launching {
    background: rgba(26, 74, 128, 0.3);
    border-color: rgba(61, 127, 240, 0.3);
    box-shadow: inset 0 1px 0 rgba(255,255,255,0.1), 0 0 12px rgba(61,127,240,0.15);
  }

  .no-version { text-align: center; color: #666; font-size: 13px; line-height: 1.8; }
  .install-link { background: none; border: none; color: #3d7ff0; cursor: pointer; font-size: 13px; text-decoration: underline; }

  .launch-error {
    position: absolute; left: 50%; transform: translateX(-50%);
    background: rgba(224, 85, 85, 0.15); backdrop-filter: blur(10px);
    border: 1px solid rgba(224, 85, 85, 0.3); border-radius: 8px;
    padding: 6px 16px; font-size: 12px; color: #ff8888;
    white-space: nowrap; z-index: 3;
  }
</style>
