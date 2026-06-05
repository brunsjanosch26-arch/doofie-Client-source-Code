use serde::{Deserialize, Serialize};
use std::path::PathBuf;

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct Shader {
    pub name: String,
    pub path: String,
}

#[tauri::command]
pub async fn get_shaders(game_dir: String) -> Result<Vec<Shader>, String> {
    let dir = PathBuf::from(format!("{}\\shaderpacks", game_dir));
    if !dir.exists() {
        std::fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
        return Ok(vec![]);
    }
    let mut shaders = vec![];
    for entry in std::fs::read_dir(&dir).map_err(|e| e.to_string())?.flatten() {
        let path = entry.path();
        let name = path.file_name().and_then(|n| n.to_str()).unwrap_or("").to_string();
        if name.ends_with(".zip") || path.is_dir() {
            shaders.push(Shader { name, path: path.to_string_lossy().to_string() });
        }
    }
    shaders.sort_by(|a, b| a.name.cmp(&b.name));
    Ok(shaders)
}

#[tauri::command]
pub async fn delete_shader(name: String, game_dir: String) -> Result<(), String> {
    let p = PathBuf::from(format!("{}\\shaderpacks\\{}", game_dir, name));
    if p.is_dir() {
        std::fs::remove_dir_all(&p).map_err(|e| e.to_string())
    } else {
        std::fs::remove_file(&p).map_err(|e| e.to_string())
    }
}
