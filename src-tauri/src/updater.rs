use serde::{Deserialize, Serialize};

const CURRENT_VERSION: &str = "2.0.0";

#[derive(Debug, Serialize, Deserialize)]
pub struct UpdateInfo {
    pub current_version: String,
    pub latest_version: String,
    pub has_update: bool,
    pub changelog: Option<String>,
}

#[tauri::command]
pub async fn check_for_updates() -> Result<UpdateInfo, String> {
    Ok(UpdateInfo {
        current_version: CURRENT_VERSION.to_string(),
        latest_version: CURRENT_VERSION.to_string(),
        has_update: false,
        changelog: None,
    })
}
