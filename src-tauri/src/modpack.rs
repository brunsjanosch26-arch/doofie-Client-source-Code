use serde::{Deserialize, Serialize};
use std::io::Read;
use std::path::PathBuf;
use tauri::Emitter;

#[derive(Debug, Clone, Serialize)]
pub struct ModpackProgress {
    pub step: String,
    pub current: u32,
    pub total: u32,
    pub percent: u8,
}

#[derive(Debug, Deserialize)]
struct MrpackIndex {
    name: String,
    #[serde(rename = "versionId")]
    version_id: String,
    files: Vec<MrpackFile>,
    dependencies: std::collections::HashMap<String, String>,
}

#[derive(Debug, Deserialize)]
struct MrpackFile {
    path: String,
    downloads: Vec<String>,
    #[serde(rename = "fileSize")]
    file_size: Option<u64>,
}

/// Installs a Modrinth .mrpack modpack from a file path.
#[tauri::command]
pub async fn install_modpack(
    file_path: String,
    game_dir: String,
    app: tauri::AppHandle,
) -> Result<String, String> {
    let client = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(120))
        .build()
        .map_err(|e| e.to_string())?;

    let emit = |step: &str, current: u32, total: u32| {
        let percent = if total > 0 { ((current * 100) / total) as u8 } else { 0 };
        let _ = app.emit("modpack_progress", ModpackProgress {
            step: step.to_string(), current, total, percent,
        });
    };

    emit("Lese Modpack...", 0, 100);

    // ── 1. Parse modrinth.index.json from the ZIP ──────────────────────────
    let index: MrpackIndex = {
        let zip_file = std::fs::File::open(&file_path)
            .map_err(|e| format!("Konnte Datei nicht öffnen: {}", e))?;
        let mut archive = zip::ZipArchive::new(zip_file)
            .map_err(|e| format!("Kein gültiges ZIP/mrpack: {}", e))?;
        let mut idx_file = archive.by_name("modrinth.index.json")
            .map_err(|_| "modrinth.index.json nicht gefunden – keine gültige .mrpack Datei".to_string())?;
        let mut buf = String::new();
        idx_file.read_to_string(&mut buf).map_err(|e| e.to_string())?;
        serde_json::from_str(&buf).map_err(|e| format!("index.json Fehler: {}", e))?
    }; // archive + idx_file dropped here

    emit(&format!("Installiere '{}' v{}", index.name, index.version_id), 2, 100);

    let game_path = PathBuf::from(&game_dir);

    // ── 2. Collect override file names (no borrow held across iterations) ──
    let override_entries: Vec<String> = {
        let zip_file = std::fs::File::open(&file_path).map_err(|e| e.to_string())?;
        let mut archive = zip::ZipArchive::new(zip_file).map_err(|e| e.to_string())?;
        let mut names = vec![];
        for i in 0..archive.len() {
            if let Ok(entry) = archive.by_index(i) {
                let n = entry.name().to_string();
                if n.starts_with("overrides/") && !n.ends_with('/') {
                    names.push(n);
                }
            }
        }
        names
    };

    // ── 3. Extract overrides ───────────────────────────────────────────────
    emit("Extrahiere Overrides...", 3, 100);
    for name in &override_entries {
        let rel_path = name.trim_start_matches("overrides/");
        let out_path = game_path.join(rel_path);
        if let Some(parent) = out_path.parent() {
            std::fs::create_dir_all(parent).ok();
        }
        // Open archive fresh for each file to avoid lifetime issues
        let buf: Vec<u8> = {
            let zip_file2 = std::fs::File::open(&file_path).map_err(|e| e.to_string())?;
            let mut arch2 = zip::ZipArchive::new(zip_file2).map_err(|e| e.to_string())?;
            let mut entry = arch2.by_name(name).map_err(|e| e.to_string())?;
            let mut b = vec![];
            entry.read_to_end(&mut b).map_err(|e| e.to_string())?;
            b
        }; // entry + arch2 dropped here before write
        std::fs::write(&out_path, &buf).ok();
    }

    // ── 4. Download mod files ──────────────────────────────────────────────
    let total_files = index.files.len() as u32;
    emit(&format!("Lade {} Mods herunter...", total_files), 5, 100);

    for (i, file) in index.files.iter().enumerate() {
        let percent = 5 + ((i as u32 * 90) / total_files.max(1)) as u8;
        let fname = PathBuf::from(&file.path)
            .file_name()
            .map(|n| n.to_string_lossy().to_string())
            .unwrap_or_else(|| file.path.clone());
        let _ = app.emit("modpack_progress", ModpackProgress {
            step: format!("Mod {}/{}: {}", i + 1, total_files, fname),
            current: i as u32 + 1,
            total: total_files,
            percent,
        });

        let out_path = game_path.join(&file.path);
        if out_path.exists() { continue; }
        if let Some(parent) = out_path.parent() {
            std::fs::create_dir_all(parent).ok();
        }

        if let Some(url) = file.downloads.first() {
            if let Ok(resp) = client.get(url).send().await {
                if let Ok(bytes) = resp.bytes().await {
                    std::fs::write(&out_path, &bytes).ok();
                }
            }
        }
    }

    emit("Fertig!", total_files, total_files);

    // Clean up temp file if it looks like our temp file
    if file_path.ends_with("temp_modpack_install.mrpack") {
        std::fs::remove_file(&file_path).ok();
    }

    let mc_version = index.dependencies.get("minecraft").cloned().unwrap_or_default();
    let fabric_version = index.dependencies.get("fabric-loader").cloned();

    Ok(serde_json::json!({
        "name": index.name,
        "version": index.version_id,
        "mcVersion": mc_version,
        "fabricVersion": fabric_version,
    }).to_string())
}
