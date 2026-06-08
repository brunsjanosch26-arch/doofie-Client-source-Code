# Minecraft Launcher - Phase 1

Ein Minecraft-Launcher gebaut mit Tauri (React + Rust).

## Phase 1 Features

- ✅ Launcher UI mit Home, Mods, Settings Tabs
- ✅ Vanilla Minecraft starten
- ✅ Version-Management (lokale Versionen erkennen)
- ✅ RAM-Einstellung
- ✅ Java-Pfad Konfiguration
- ✅ Basis Mod-System

## Projektstruktur

```
minecraft-launcher/
├── src/                    # React Frontend
│   ├── pages/
│   │   ├── Home.tsx       # Play-Seite
│   │   ├── Settings.tsx   # Einstellungen
│   │   └── ModManager.tsx # Mod-Verwaltung
│   ├── App.tsx            # Main App
│   └── main.tsx           # React Entry Point
├── src-tauri/             # Rust Backend
│   ├── src/
│   │   ├── main.rs        # Tauri Entry Point
│   │   ├── launcher.rs    # Launcher Commands
│   │   ├── minecraft.rs   # Minecraft-Logik
│   │   └── profile.rs     # Profile Management
│   └── Cargo.toml
├── index.html
├── tauri.conf.json
├── package.json
└── vite.config.ts
```

## Installation

1. Node.js 18+ und Rust installieren
2. `npm install` - npm Dependencies installieren
3. Rust Dependencies: `cd src-tauri && cargo build`

## Entwicklung

```bash
npm run dev        # Frontend Dev-Server starten
npm run tauri dev  # Tauri mit Hot-Reload starten
```

## Build

```bash
npm run build      # Frontend bauen
npm run tauri build # Executable erstellen
```

## Nächste Schritte (Phase 2)

- [ ] Microsoft OAuth Login
- [ ] Mod-Datenbank Integration
- [ ] Shader-Installation
- [ ] Profile-Management erweitern
- [ ] Auto-Update System
- [ ] Fabric/Forge/Quilt Support
