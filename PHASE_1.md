# Phase 1: MVP - Kernfunktionalität

## Ziele
- Tauri-App mit React-Frontend
- Lokale Minecraft-Verwaltung (Vanilla)
- Basis-Launcher-Funktionalität
- Einfache Mod-Installation (lokal)

## Komponenten

### Frontend (React + TypeScript)
- **Home/Dashboard**: Übersicht installierter Versionen
- **Version Manager**: Minecraft-Versionen hinzufügen/löschen
- **Launch Panel**: Version auswählen + spielen
- **Mod Manager (Basic)**: Mods in Ordner ziehen/Verzeichnis-Browser
- **Settings**: Java-Pfad, RAM-Zuordnung, Game-Ordner

### Backend (Rust + Tauri)
- **Minecraft-Installation Discovery**: Lokale `.minecraft`-Ordner erkennen
- **Launcher Core**: 
  - Minecraft-Download (von launcher.mojang.com)
  - JVM-Arguments erstellen
  - Minecraft-Prozess starten
- **File Management**: Mods kopieren, Versionsordner verwalten

### Dateistruktur (lokal)
```
.minecraft/
├── launcher_profiles.json
├── versions/
│   ├── 1.20.1/
│   │   ├── mods/
│   │   └── config/
│   └── [mehr Versionen]
├── libraries/
└── assets/
```

## Technologie-Stack
- **Frontend**: React 18 + TypeScript + Tailwind CSS
- **Build**: Tauri 2.0
- **Runtime**: Node.js 18+, Rust latest
- **Launcher-Core**: `rs-minecraft` oder eigene Implementierung

## Deliverables
- ✅ Tauri-Projekt initialisiert
- ✅ Basis-UI mit Home + Settings
- ✅ Minecraft-Installation erkennen
- ✅ Vanilla Minecraft starten
- ✅ Einfaches Mod-System (Ordner-basiert)
