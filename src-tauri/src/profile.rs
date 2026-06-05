use serde::{Deserialize, Serialize};
use std::path::PathBuf;

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct GameProfile {
    pub id: String,
    pub name: String,
    pub version: String,
    pub java_args: String,
    pub ram: u32,
    pub mods_enabled: Vec<String>,
    pub shader: Option<String>,
}

#[tauri::command]
pub async fn create_profile(profile: GameProfile, game_dir: String) -> Result<String, String> {
    let profiles_path = PathBuf::from(format!("{}\\profiles.json", game_dir));
    let mut profiles: Vec<GameProfile> = if profiles_path.exists() {
        let content = std::fs::read_to_string(&profiles_path)
            .map_err(|e| e.to_string())?;
        serde_json::from_str(&content).unwrap_or_default()
    } else {
        vec![]
    };

    profiles.push(profile.clone());
    let json = serde_json::to_string_pretty(&profiles).map_err(|e| e.to_string())?;
    std::fs::write(&profiles_path, json).map_err(|e| e.to_string())?;

    Ok(profile.id)
}

#[tauri::command]
pub async fn delete_profile(profile_id: String, game_dir: String) -> Result<(), String> {
    let profiles_path = PathBuf::from(format!("{}\\profiles.json", game_dir));
    if !profiles_path.exists() {
        return Err("Profiles file not found".to_string());
    }

    let content = std::fs::read_to_string(&profiles_path).map_err(|e| e.to_string())?;
    let mut profiles: Vec<GameProfile> = serde_json::from_str(&content)
        .map_err(|e| e.to_string())?;

    profiles.retain(|p| p.id != profile_id);

    let json = serde_json::to_string_pretty(&profiles).map_err(|e| e.to_string())?;
    std::fs::write(&profiles_path, json).map_err(|e| e.to_string())?;

    Ok(())
}

#[tauri::command]
pub async fn get_profiles(game_dir: String) -> Result<Vec<GameProfile>, String> {
    let profiles_path = PathBuf::from(format!("{}\\profiles.json", game_dir));
    if !profiles_path.exists() {
        return Ok(vec![]);
    }

    let content = std::fs::read_to_string(&profiles_path).map_err(|e| e.to_string())?;
    serde_json::from_str(&content).map_err(|e| e.to_string())
}
