use serde::{Deserialize, Serialize};
use std::path::PathBuf;
use tauri::Emitter;

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct FabricLoaderVersion {
    pub version: String,
    pub stable: bool,
}

#[derive(Debug, Clone, Serialize)]
pub struct ModloaderProgress {
    pub step: String,
    pub percent: u8,
}

#[derive(Debug, Deserialize)]
struct FabricLoaderMeta {
    loader: FabricLoaderInfo,
}

#[derive(Debug, Deserialize)]
struct FabricLoaderInfo {
    version: String,
}

/// Returns available Fabric loader versions for a given MC version
#[tauri::command]
pub async fn get_fabric_versions(mc_version: String) -> Result<Vec<FabricLoaderVersion>, String> {
    let client = reqwest::Client::new();
    let url = format!("https://meta.fabricmc.net/v2/versions/loader/{}", mc_version);
    let versions: Vec<FabricLoaderMeta> = client
        .get(&url)
        .send().await.map_err(|e| format!("Netzwerkfehler: {}", e))?
        .json().await.map_err(|e| format!("Parse-Fehler: {}", e))?;

    Ok(versions.into_iter().map(|m| FabricLoaderVersion {
        version: m.loader.version,
        stable: true,
    }).collect())
}

/// Downloads and installs a Fabric profile JSON + all its libraries
#[tauri::command]
pub async fn install_fabric(
    mc_version: String,
    loader_version: String,
    game_dir: String,
    app: tauri::AppHandle,
) -> Result<String, String> {
    let client = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(120))
        .build()
        .map_err(|e| e.to_string())?;

    let emit = |step: &str, percent: u8| {
        let _ = app.emit("modloader_progress", ModloaderProgress {
            step: step.to_string(),
            percent,
        });
    };

    emit("Lade Fabric-Profil herunter...", 5);

    // Download the Fabric profile JSON
    let profile_url = format!(
        "https://meta.fabricmc.net/v2/versions/loader/{}/{}/profile/json",
        mc_version, loader_version
    );
    let profile_bytes = client
        .get(&profile_url)
        .send().await.map_err(|e| format!("Profil-Download fehlgeschlagen: {}", e))?
        .bytes().await.map_err(|e| e.to_string())?;

    let profile_json: serde_json::Value = serde_json::from_slice(&profile_bytes)
        .map_err(|e| format!("Profil-JSON Fehler: {}", e))?;

    let profile_id = profile_json["id"].as_str()
        .ok_or("Kein 'id' Feld im Profil")?
        .to_string();

    // Save version JSON
    let ver_dir = PathBuf::from(format!("{}\\versions\\{}", game_dir, profile_id));
    std::fs::create_dir_all(&ver_dir).map_err(|e| e.to_string())?;
    let ver_json_path = ver_dir.join(format!("{}.json", profile_id));
    std::fs::write(&ver_json_path, &profile_bytes).map_err(|e| e.to_string())?;

    emit("Lade Fabric-Bibliotheken herunter...", 15);

    // Download all libraries
    let libs_dir = PathBuf::from(format!("{}\\libraries", game_dir));
    std::fs::create_dir_all(&libs_dir).map_err(|e| e.to_string())?;

    let empty = vec![];
    let libraries = profile_json["libraries"].as_array().unwrap_or(&empty);
    let total = libraries.len() as u8;

    for (i, lib) in libraries.iter().enumerate() {
        let percent = 15 + ((i as u8 * 75) / total.max(1));
        emit(&format!("Bibliothek {}/{}", i + 1, total), percent);

        // Parse maven coordinates: group:artifact:version
        let name = lib["name"].as_str().unwrap_or("");
        let url_base = lib["url"].as_str().unwrap_or("https://maven.fabricmc.net/");

        if let Some(artifact_path) = maven_to_path(name) {
            let lib_path = libs_dir.join(&artifact_path);
            if lib_path.exists() { continue; }

            if let Some(parent) = lib_path.parent() {
                std::fs::create_dir_all(parent).ok();
            }

            let download_url = format!("{}{}", url_base.trim_end_matches('/'), format!("/{}", artifact_path));
            if let Ok(resp) = client.get(&download_url).send().await {
                if let Ok(bytes) = resp.bytes().await {
                    std::fs::write(&lib_path, &bytes).ok();
                }
            }
        }
    }

    emit("Fertig!", 100);
    Ok(profile_id)
}

/// Ensures Fabric is installed for the given MC version.
/// If the version is already a Fabric version, it's returned as-is (after existence check).
/// If it's a vanilla version, installs the latest stable Fabric loader and returns the profile ID.
#[tauri::command]
pub async fn ensure_fabric(mc_version: String, game_dir: String, app: tauri::AppHandle) -> Result<String, String> {
    // If already a fabric version, verify it exists
    if mc_version.to_lowercase().contains("fabric") {
        let json_path = PathBuf::from(format!("{}\\versions\\{}\\{}.json", game_dir, mc_version, mc_version));
        if json_path.exists() {
            return Ok(mc_version);
        }
        return Err(format!("Fabric-Version '{}' nicht gefunden. Bitte im ModManager neu installieren.", mc_version));
    }

    // Check if any fabric version is already installed that inherits from this MC version
    let versions_dir = PathBuf::from(format!("{}\\versions", game_dir));
    if let Ok(entries) = std::fs::read_dir(&versions_dir) {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().to_string();
            if !name.to_lowercase().contains("fabric") { continue; }
            let json_path = versions_dir.join(&name).join(format!("{}.json", name));
            if let Ok(content) = std::fs::read_to_string(&json_path) {
                if let Ok(j) = serde_json::from_str::<serde_json::Value>(&content) {
                    if j["inheritsFrom"].as_str() == Some(mc_version.as_str()) {
                        return Ok(name);
                    }
                }
            }
        }
    }

    // Ensure base Minecraft version is installed first
    let _ = app.emit("modloader_progress", ModloaderProgress {
        step: format!("Prüfe Minecraft {}...", mc_version),
        percent: 0,
    });
    crate::minecraft::download_version_if_missing(&mc_version, &game_dir, &app).await?;

    // No fabric installed for this MC version — install latest stable
    let _ = app.emit("modloader_progress", ModloaderProgress {
        step: format!("Installiere Fabric für Minecraft {}...", mc_version),
        percent: 5,
    });

    let client = reqwest::Client::new();
    let url = format!("https://meta.fabricmc.net/v2/versions/loader/{}", mc_version);
    let versions: Vec<serde_json::Value> = client
        .get(&url)
        .send().await
        .map_err(|e| format!("Fabric-Versionen konnten nicht geladen werden: {}", e))?
        .json().await
        .map_err(|e| format!("Parse-Fehler: {}", e))?;

    if versions.is_empty() {
        return Err(format!("Kein Fabric Loader für Minecraft {} verfügbar. Bitte im ModManager manuell installieren.", mc_version));
    }

    let loader_version = versions.iter()
        .find(|v| v["loader"]["stable"].as_bool().unwrap_or(false))
        .or_else(|| versions.first())
        .and_then(|v| v["loader"]["version"].as_str())
        .ok_or("Loader-Version konnte nicht bestimmt werden")?
        .to_string();

    install_fabric(mc_version, loader_version, game_dir.clone(), app).await
}

/// Converts maven coordinate to file path
/// e.g. "net.fabricmc:fabric-loader:0.15.0" → "net/fabricmc/fabric-loader/0.15.0/fabric-loader-0.15.0.jar"
fn maven_to_path(name: &str) -> Option<String> {
    let parts: Vec<&str> = name.split(':').collect();
    if parts.len() < 3 { return None; }
    let group = parts[0].replace('.', "/");
    let artifact = parts[1];
    let version = parts[2];
    Some(format!("{}/{}/{}/{}-{}.jar", group, artifact, version, artifact, version))
}
