// Prevents additional console window on Windows in release
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod account;
mod launch_engine;
mod launcher;
mod minecraft;
mod modloader;
mod modpack;
mod profile;
mod shader;
mod updater;

use std::path::PathBuf;
use std::sync::{Arc, Mutex};

fn main() {
    let game_dir = std::env::var("APPDATA")
        .map(|d| PathBuf::from(format!("{}\\DoofieClient", d)))
        .unwrap_or_else(|_| PathBuf::from("DoofieClient"));

    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_fs::init())
        .plugin(tauri_plugin_dialog::init())
        .manage(launcher::LauncherState {
            game_dir: Arc::new(Mutex::new(game_dir)),
        })
        .invoke_handler(tauri::generate_handler![
            // launcher
            launcher::get_game_directory,
            launcher::set_game_directory,
            launcher::get_installed_versions,
            launcher::get_mod_files,
            launcher::get_profile_mod_files,
            launcher::apply_profile_mods,
            launcher::launch_minecraft,
            launcher::launch_with_profile,
            launcher::open_game_folder,
            launcher::get_config,
            launcher::save_config,
            launcher::get_instance_dir,
            launcher::setup_client_pack,
            // minecraft
            minecraft::get_available_versions,
            minecraft::download_version,
            minecraft::get_java_path,
            minecraft::set_java_path,
            // profile
            profile::create_profile,
            profile::delete_profile,
            profile::get_profiles,
            // account
            account::get_accounts,
            account::add_offline_account,
            account::set_active_account,
            account::remove_account,
            account::start_microsoft_webview_login,
            account::refresh_accounts,
            // shader
            shader::get_shaders,
            shader::delete_shader,
            // modloader
            modloader::get_fabric_versions,
            modloader::install_fabric,
            modloader::ensure_fabric,
            // modpack
            modpack::install_modpack,
            // updater
            updater::check_for_updates,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
