use serde::{Deserialize, Serialize};
use std::io::Read;
use std::path::PathBuf;
use tauri::Emitter;

// ── Mojang manifest types ────────────────────────────────────────────────────

#[derive(Debug, Deserialize)]
struct VersionManifest {
    versions: Vec<ManifestVersion>,
}

#[derive(Debug, Deserialize, Clone, Serialize)]
pub struct ManifestVersion {
    pub id: String,
    #[serde(rename = "type")]
    pub version_type: String,
    pub url: String,
}

#[derive(Debug, Deserialize)]
struct VersionJson {
    downloads: VersionDownloads,
    libraries: Vec<Library>,
    #[serde(rename = "assetIndex")]
    asset_index: Option<AssetIndex>,
}

#[derive(Debug, Deserialize)]
struct VersionDownloads {
    client: FileDownload,
}

#[derive(Debug, Deserialize)]
struct FileDownload {
    url: String,
    size: u64,
}

#[derive(Debug, Deserialize)]
struct Library {
    downloads: Option<LibraryDownloads>,
    name: String,
}

#[derive(Debug, Deserialize)]
struct LibraryDownloads {
    artifact: Option<LibraryArtifact>,
}

#[derive(Debug, Deserialize)]
struct LibraryArtifact {
    url: String,
    path: String,
    size: u64,
}

#[derive(Debug, Deserialize)]
struct AssetIndex {
    url: String,
    id: String,
}

// ── Progress event ────────────────────────────────────────────────────────────

#[derive(Debug, Clone, Serialize)]
pub struct DownloadProgress {
    pub step: String,
    pub current: u64,
    pub total: u64,
    pub percent: u8,
}

// ── Commands ─────────────────────────────────────────────────────────────────

#[tauri::command]
pub async fn get_java_path() -> Result<String, String> {
    let common_paths = [
        "C:\\Program Files\\Java\\jdk-21\\bin\\java.exe",
        "C:\\Program Files\\Java\\jdk-17\\bin\\java.exe",
        "C:\\Program Files\\Java\\jdk-11\\bin\\java.exe",
        "C:\\Program Files (x86)\\Java\\jdk-21\\bin\\java.exe",
        "C:\\Program Files\\Eclipse Adoptium\\jdk-21\\bin\\java.exe",
        "C:\\Program Files\\Eclipse Adoptium\\jdk-17\\bin\\java.exe",
        "C:\\Program Files\\Microsoft\\jdk-17\\bin\\java.exe",
    ];
    for path in &common_paths {
        if PathBuf::from(path).exists() {
            return Ok(path.to_string());
        }
    }
    if let Ok(path) = which::which("java") {
        return Ok(path.to_string_lossy().to_string());
    }
    Err("Java not found. Install Java 17+ from adoptium.net".to_string())
}

#[tauri::command]
pub async fn set_java_path(path: String) -> Result<(), String> {
    if PathBuf::from(&path).exists() {
        Ok(())
    } else {
        Err(format!("Java not found at: {}", path))
    }
}

#[tauri::command]
pub async fn get_available_versions(release_only: bool) -> Result<Vec<ManifestVersion>, String> {
    let client = reqwest::Client::new();
    let manifest: VersionManifest = client
        .get("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json")
        .send().await.map_err(|e| format!("Network error: {}", e))?
        .json().await.map_err(|e| format!("Parse error: {}", e))?;

    let versions = if release_only {
        manifest.versions.into_iter().filter(|v| v.version_type == "release").collect()
    } else {
        manifest.versions
    };
    Ok(versions)
}

#[tauri::command]
pub async fn download_version(
    version: String,
    game_dir: String,
    app: tauri::AppHandle,
) -> Result<String, String> {
    let client = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(120))
        .build()
        .map_err(|e| e.to_string())?;

    let emit = |step: &str, current: u64, total: u64| {
        let percent = if total > 0 { ((current * 100) / total) as u8 } else { 0 };
        let _ = app.emit("download_progress", DownloadProgress {
            step: step.to_string(), current, total, percent,
        });
    };

    // ── 1. Version manifest ────────────────────────────────────────────────
    emit("Fetching version list...", 0, 100);
    let manifest: VersionManifest = client
        .get("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json")
        .send().await.map_err(|e| e.to_string())?
        .json().await.map_err(|e| e.to_string())?;

    let meta = manifest.versions.iter()
        .find(|v| v.id == version)
        .ok_or_else(|| format!("Version {} not found", version))?;

    // ── 2. Version JSON ────────────────────────────────────────────────────
    emit("Downloading version metadata...", 5, 100);
    let ver_json_bytes = client
        .get(&meta.url)
        .send().await.map_err(|e| e.to_string())?
        .bytes().await.map_err(|e| e.to_string())?;

    let ver_dir = PathBuf::from(format!("{}\\versions\\{}", game_dir, version));
    std::fs::create_dir_all(&ver_dir).map_err(|e| e.to_string())?;

    let ver_json_path = ver_dir.join(format!("{}.json", version));
    std::fs::write(&ver_json_path, &ver_json_bytes).map_err(|e| e.to_string())?;

    let ver_json: VersionJson = serde_json::from_slice(&ver_json_bytes)
        .map_err(|e| format!("Version JSON parse error: {}", e))?;

    // ── 3. Client JAR ──────────────────────────────────────────────────────
    emit("Downloading client JAR...", 10, 100);
    let jar_bytes = client
        .get(&ver_json.downloads.client.url)
        .send().await.map_err(|e| e.to_string())?
        .bytes().await.map_err(|e| e.to_string())?;

    let jar_path = ver_dir.join(format!("{}.jar", version));
    std::fs::write(&jar_path, &jar_bytes).map_err(|e| e.to_string())?;

    // ── 4. Libraries ───────────────────────────────────────────────────────
    let libs_dir = PathBuf::from(format!("{}\\libraries", game_dir));
    std::fs::create_dir_all(&libs_dir).map_err(|e| e.to_string())?;

    let libs: Vec<&Library> = ver_json.libraries.iter()
        .filter(|l| l.downloads.as_ref().and_then(|d| d.artifact.as_ref()).is_some())
        .collect();

    let total_libs = libs.len() as u64;
    for (i, lib) in libs.iter().enumerate() {
        if let Some(artifact) = lib.downloads.as_ref().and_then(|d| d.artifact.as_ref()) {
            let percent = 20 + ((i as u64 * 65) / total_libs.max(1)) as u8;
            emit(&format!("Downloading library: {}", lib.name), i as u64, total_libs);
            let _ = app.emit("download_progress", DownloadProgress {
                step: format!("Library {}/{}", i + 1, total_libs),
                current: i as u64,
                total: total_libs,
                percent,
            });

            let lib_path = libs_dir.join(&artifact.path);
            if lib_path.exists() { continue; }

            if let Some(parent) = lib_path.parent() {
                std::fs::create_dir_all(parent).ok();
            }

            if let Ok(resp) = client.get(&artifact.url).send().await {
                if let Ok(bytes) = resp.bytes().await {
                    std::fs::write(&lib_path, &bytes).ok();
                }
            }
        }
    }

    // ── 5. Native libraries – download + extract .dll files ───────────────
    emit("Extracting natives...", 87, 100);
    let natives_dir = PathBuf::from(format!("{}\\versions\\{}\\natives", game_dir, version));
    std::fs::create_dir_all(&natives_dir).ok();

    // Re-parse raw bytes as untyped Value to access classifiers
    if let Ok(raw) = serde_json::from_slice::<serde_json::Value>(&ver_json_bytes) {
        if let Some(libraries) = raw["libraries"].as_array() {
            for lib in libraries {
                // Check if this library has a windows native classifier
                let native_key = lib["natives"]["windows"].as_str().unwrap_or("natives-windows");
                let classifier_url = lib["downloads"]["classifiers"][native_key]["url"].as_str();
                let classifier_path_str = lib["downloads"]["classifiers"][native_key]["path"].as_str();

                if let (Some(nat_url), Some(nat_path)) = (classifier_url, classifier_path_str) {
                    let jar_path = libs_dir.join(nat_path);
                    if let Some(parent) = jar_path.parent() {
                        std::fs::create_dir_all(parent).ok();
                    }
                    // Download native JAR if not present
                    if !jar_path.exists() {
                        if let Ok(resp) = client.get(nat_url).send().await {
                            if let Ok(bytes) = resp.bytes().await {
                                std::fs::write(&jar_path, &bytes).ok();
                            }
                        }
                    }
                    // Extract DLL files from the JAR (which is a ZIP)
                    if jar_path.exists() {
                        if let Ok(file) = std::fs::File::open(&jar_path) {
                            if let Ok(mut archive) = zip::ZipArchive::new(file) {
                                for i in 0..archive.len() {
                                    if let Ok(mut zip_file) = archive.by_index(i) {
                                        let name = zip_file.name().to_string();
                                        if name.ends_with(".dll") || name.ends_with(".so") || name.ends_with(".dylib") {
                                            let out_path = natives_dir.join(&name);
                                            if !out_path.exists() {
                                                let mut buf = vec![];
                                                zip_file.read_to_end(&mut buf).ok();
                                                std::fs::write(&out_path, &buf).ok();
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 6. Asset index ─────────────────────────────────────────────────────
    if let Some(asset_index) = &ver_json.asset_index {
        emit("Downloading asset index...", 92, 100);
        let assets_dir = PathBuf::from(format!("{}\\assets\\indexes", game_dir));
        std::fs::create_dir_all(&assets_dir).map_err(|e| e.to_string())?;
        let index_path = assets_dir.join(format!("{}.json", asset_index.id));
        if !index_path.exists() {
            if let Ok(resp) = client.get(&asset_index.url).send().await {
                if let Ok(bytes) = resp.bytes().await {
                    std::fs::write(&index_path, &bytes).ok();
                }
            }
        }
    }

    emit("Fertig!", 100, 100);
    Ok(format!("Version {} erfolgreich installiert", version))
}

/// Downloads the base MC version if the version JSON doesn't exist yet.
pub async fn download_version_if_missing(version: &str, game_dir: &str, app: &tauri::AppHandle) -> Result<(), String> {
    let ver_json = PathBuf::from(format!("{}\\versions\\{}\\{}.json", game_dir, version, version));
    if ver_json.exists() {
        return Ok(());
    }
    download_version(version.to_string(), game_dir.to_string(), app.clone())
        .await
        .map(|_| ())
}
