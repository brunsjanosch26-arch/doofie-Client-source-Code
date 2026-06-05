use serde::{Deserialize, Serialize};
use std::path::PathBuf;
use std::sync::Arc;
use tauri::State;

use crate::launch_engine::{launch, LaunchConfig};

pub struct LauncherState {
    pub game_dir: Arc<std::sync::Mutex<PathBuf>>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct LauncherConfig {
    pub ram: u32,
    pub jvm_args: String,
    pub close_on_launch: bool,
    pub auto_update: bool,
}

impl Default for LauncherConfig {
    fn default() -> Self {
        Self {
            ram: 4,
            jvm_args: "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200".to_string(),
            close_on_launch: false,
            auto_update: true,
        }
    }
}

fn config_path(game_dir: &str) -> PathBuf {
    PathBuf::from(format!("{}\\launcher_config.json", game_dir))
}

#[tauri::command]
pub async fn get_game_directory(state: State<'_, LauncherState>) -> Result<String, String> {
    let dir = state.game_dir.lock().map_err(|e| e.to_string())?;
    Ok(dir.to_string_lossy().to_string())
}

#[tauri::command]
pub async fn set_game_directory(path: String, state: State<'_, LauncherState>) -> Result<(), String> {
    let mut dir = state.game_dir.lock().map_err(|e| e.to_string())?;
    *dir = PathBuf::from(path);
    Ok(())
}

#[tauri::command]
pub async fn get_config(game_dir: String) -> Result<LauncherConfig, String> {
    let p = config_path(&game_dir);
    if !p.exists() { return Ok(LauncherConfig::default()); }
    let content = std::fs::read_to_string(p).map_err(|e| e.to_string())?;
    serde_json::from_str(&content).map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn save_config(game_dir: String, config: LauncherConfig) -> Result<(), String> {
    let p = config_path(&game_dir);
    if let Some(parent) = p.parent() {
        std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
    }
    let json = serde_json::to_string_pretty(&config).map_err(|e| e.to_string())?;
    std::fs::write(&p, json).map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn get_installed_versions(game_dir: String) -> Result<Vec<String>, String> {
    let versions_path = PathBuf::from(format!("{}\\versions", game_dir));
    if !versions_path.exists() { return Ok(vec![]); }
    let mut versions = vec![];
    if let Ok(entries) = std::fs::read_dir(&versions_path) {
        for entry in entries.flatten() {
            if entry.path().is_dir() {
                if let Some(name) = entry.file_name().to_str() {
                    versions.push(name.to_string());
                }
            }
        }
    }
    versions.sort();
    Ok(versions)
}

#[tauri::command]
pub async fn apply_profile_mods(game_dir: String, profile_id: String, mods_enabled: Vec<String>) -> Result<(), String> {
    if mods_enabled.is_empty() { return Ok(()); }
    let mods_path = PathBuf::from(format!("{}\\profiles\\{}\\mods", game_dir, profile_id));
    if !mods_path.exists() { return Ok(()); }

    for entry in std::fs::read_dir(&mods_path).map_err(|e| e.to_string())?.flatten() {
        let name = entry.file_name().to_string_lossy().to_string();
        if name.ends_with(".jar") {
            if !mods_enabled.contains(&name) {
                let disabled = mods_path.join(format!("{}.disabled", name));
                std::fs::rename(entry.path(), disabled).ok();
            }
        } else if name.ends_with(".disabled") {
            let base = name.trim_end_matches(".disabled").to_string();
            if mods_enabled.contains(&base) {
                std::fs::rename(entry.path(), mods_path.join(&base)).ok();
            }
        }
    }
    Ok(())
}

#[tauri::command]
pub async fn get_mod_files(game_dir: String) -> Result<Vec<String>, String> {
    let mods_path = PathBuf::from(format!("{}\\mods", game_dir));
    if !mods_path.exists() { return Ok(vec![]); }
    let mut files = vec![];
    if let Ok(entries) = std::fs::read_dir(&mods_path) {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().to_string();
            if name.ends_with(".jar") || name.ends_with(".disabled") {
                files.push(name);
            }
        }
    }
    files.sort();
    Ok(files)
}

/// Vanilla launch (instance = game_dir, Java auto-detected from version.json).
#[tauri::command]
pub async fn launch_minecraft(
    version: String,
    game_dir: String,
    java_path: String,
    username: String,
    uuid: String,
    access_token: String,
    ram: u32,
    app: tauri::AppHandle,
) -> Result<String, String> {
    let config = get_config(game_dir.clone()).await.unwrap_or_default();
    launch(LaunchConfig {
        version,
        game_dir: game_dir.clone(),
        instance_dir: game_dir,
        java_path: if java_path.is_empty() { None } else { Some(java_path) },
        username,
        uuid,
        access_token,
        ram_gb: ram,
        extra_jvm_args: config.jvm_args,
    }, app).await
}

/// Profile launch with per-profile instance directory.
#[tauri::command]
pub async fn launch_with_profile(
    version: String,
    game_dir: String,
    instance_dir: Option<String>,
    java_path: String,
    username: String,
    uuid: String,
    access_token: String,
    ram: u32,
    jvm_args: String,
    app: tauri::AppHandle,
) -> Result<String, String> {
    let inst = instance_dir.unwrap_or_else(|| game_dir.clone());
    launch(LaunchConfig {
        version,
        game_dir,
        instance_dir: inst,
        java_path: if java_path.is_empty() { None } else { Some(java_path) },
        username,
        uuid,
        access_token,
        ram_gb: ram,
        extra_jvm_args: jvm_args,
    }, app).await
}

#[tauri::command]
pub fn get_instance_dir(game_dir: String, profile_id: String) -> String {
    format!("{}\\profiles\\{}", game_dir, profile_id)
}

#[tauri::command]
pub async fn get_profile_mod_files(game_dir: String, profile_id: String) -> Result<Vec<String>, String> {
    let mods_path = PathBuf::from(format!("{}\\profiles\\{}\\mods", game_dir, profile_id));
    if !mods_path.exists() { return Ok(vec![]); }
    let mut files = vec![];
    if let Ok(entries) = std::fs::read_dir(&mods_path) {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().to_string();
            if name.ends_with(".jar") || name.ends_with(".disabled") {
                files.push(name);
            }
        }
    }
    files.sort();
    Ok(files)
}

#[tauri::command]
pub async fn setup_client_pack(instance_dir: String) -> Result<String, String> {
    let crash_blacklist = ["essential", "controlify"];
    let modrinth_mods = std::env::var("APPDATA")
        .map(|d| PathBuf::from(format!("{}\\ModrinthApp\\profiles\\client mods\\mods", d)))
        .unwrap_or_default();
    if !modrinth_mods.exists() {
        return Err("Modrinth 'client mods' Profil nicht gefunden.".to_string());
    }
    let target_mods = PathBuf::from(format!("{}\\mods", instance_dir));
    std::fs::create_dir_all(&target_mods).map_err(|e| e.to_string())?;
    let mut copied = 0usize;
    let mut skipped = 0usize;
    for entry in std::fs::read_dir(&modrinth_mods).map_err(|e| e.to_string())?.flatten() {
        let name = entry.file_name().to_string_lossy().to_lowercase();
        if !name.ends_with(".jar") { skipped += 1; continue; }
        if crash_blacklist.iter().any(|bl| name.contains(bl)) { skipped += 1; continue; }
        let dest = target_mods.join(entry.file_name());
        if !dest.exists() {
            std::fs::copy(entry.path(), &dest).map_err(|e| format!("Kopieren fehlgeschlagen: {}", e))?;
        }
        copied += 1;
    }
    Ok(format!("{} Mods installiert, {} übersprungen.", copied, skipped))
}

#[tauri::command]
pub async fn open_game_folder(path: String) -> Result<(), String> {
    std::process::Command::new("cmd")
        .args(["/c", "start", "", &path])
        .spawn()
        .map_err(|e| e.to_string())?;
    Ok(())
}
