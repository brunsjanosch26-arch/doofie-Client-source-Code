# Phase 2: Erweiterte Features

## Ziele
- Vollständige Mod-Management-Suite
- Shader-Installation
- Microsoft Account Login
- Erweiterte Konfiguration
- Auto-Launcher-Updates (lokal)

## Komponenten

### Frontend-Erweiterungen
- **Mod Browser (Lokal)**:
  - Mod-Verzeichnis durchsuchen
  - Mod-Details, Abhängigkeiten
  - Bulk-Installation
- **Shader Manager**: Shader herunterladen + installieren
- **Account Manager**: 
  - Microsoft OAuth Login
  - Account Switcher
  - Skin-Anzeige
- **Advanced Settings**:
  - JVM-Arguments Editor
  - Game-Ordner anpassen
  - Mod-Ordner pro Version
  - Auto-Update-Einstellungen

### Backend-Erweiterungen
- **Account System**:
  - Microsoft OAuth Flow
  - Offline-Cache für Accounts
  - Token-Management
- **Mod-Database (Lokal)**:
  - JSON-basierte Mod-Metadaten
  - Abhängigkeits-Resolver
  - Forge/Fabric/Quilt Support
- **Auto-Updater**:
  - Version-Check lokal
  - Delta-Updates
  - Rollback-Unterstützung
- **Profile-System**:
  - Multiple Game-Profile pro Version
  - Profile exportieren/importieren

### Datei-Struktur (erweitert)
```
.minecraft/
├── accounts.json (verschlüsselt)
├── profiles.json
├── launcher_config.json
├── mods/
│   ├── metadata.json
│   └── [Mod-Dateien]
├── shaders/
└── versions/
    ├── [Version]/
    │   ├── mods.json (installierte Mods)
    │   └── [Ordner-Struktur]
```

## Technologie-Stack (zusätzlich)
- **Auth**: oauth2 Rust crate + Microsoft OAuth
- **Database**: serde_json für lokale Datenspeicherung
- **Encryption**: argon2 für Passwort-Hash

## Deliverables
- ✅ Microsoft-Login integriert
- ✅ Mod-Datenbank (JSON-basiert)
- ✅ Shader-System
- ✅ Profile-Management
- ✅ Auto-Update-System
- ✅ Accounts persistent speichern
