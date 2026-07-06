use directories::ProjectDirs;
use once_cell::sync::Lazy;
use reqwest::Client;
use std::path::PathBuf;
use std::sync::{OnceLock, RwLock};

/// Path to bundled app resources (set once at startup from the Tauri AppHandle).
pub static RESOURCE_DIR: OnceLock<PathBuf> = OnceLock::new();

/// Names of JARs that are bundled with the launcher and should be auto-installed
/// into every Fabric/Forge profile's mods folder before launch.
pub const BUNDLED_MODS: &[&str] = &[
    "doofie-client-1.0.0.jar",
    "nrc-client.jar",
    "nrc-compat.jar",
    "nrc-cosmetics.jar",
    "nrc-friends.jar",
    "nrc-mcreal.jar",
    "nrc-minigames.jar",
    "nrc-owolib.jar",
    "nrc-voicechat.jar",
    "fabric-language-kotlin.jar",
    "cloth-config.jar",
    "yacl.jar",
    "modmenu.jar",
    "simple-voice-chat.jar",
    "sodium.jar",
    "iris.jar",
    "lithium.jar",
    "ferritecore.jar",
    "immediatelyfast.jar",
    "entityculling.jar",
    "moreculling.jar",
    "dynamic-fps.jar",
    "skinlayers3d.jar",
    "zoomify.jar",
    "appleskin.jar",
    "mousetweaks.jar",
    "status-effect-bars.jar",
];

/// NRC mods that require a valid Doofie token — skipped for offline accounts.
pub const NRC_AUTH_MODS: &[&str] = &[
    "nrc-client.jar",
    "nrc-cosmetics.jar",
    "nrc-friends.jar",
    "nrc-mcreal.jar",
    "nrc-minigames.jar",
    "nrc-voicechat.jar",
];

pub static LAUNCHER_DIRECTORY: Lazy<ProjectDirs> =
    Lazy::new(
        || match ProjectDirs::from("gg", "doofie", "DoofieClientV3") {
            Some(proj_dirs) => proj_dirs,
            None => panic!("Failed to get application directory"),
        },
    );

pub static CUSTOM_GAME_DIR_CACHE: Lazy<RwLock<Option<Option<PathBuf>>>> = 
    Lazy::new(|| RwLock::new(None));

static APP_USER_AGENT: &str = concat!(env!("CARGO_PKG_NAME"), "/", env!("CARGO_PKG_VERSION"),);

/// HTTP Client with launcher agent
pub static HTTP_CLIENT: Lazy<Client> = Lazy::new(|| {
    let client = reqwest::ClientBuilder::new()
        .user_agent(APP_USER_AGENT)
        .build()
        .unwrap_or_else(|_| Client::new());
    client
});

// Extension trait for ProjectDirs to add meta_dir functionality
pub trait ProjectDirsExt {
    fn meta_dir(&self) -> PathBuf;
    fn root_dir(&self) -> PathBuf;
}

impl ProjectDirsExt for ProjectDirs {
    fn meta_dir(&self) -> PathBuf {
        // Check cache first
        if let Ok(guard) = CUSTOM_GAME_DIR_CACHE.read() {
            if let Some(cached_value) = guard.as_ref() {
                if let Some(custom_dir) = cached_value {
                    return custom_dir.clone();
                }
            }
        }
        
        // Fallback to standard logic
        standard_meta_dir()
    }

    fn root_dir(&self) -> PathBuf {
        if cfg!(target_os = "windows") {
            // Windows: Alte Logik (wie sie war)
            self.data_dir().parent().unwrap().to_path_buf()
        } else {
            // macOS (und andere): Setze root_dir auf data_dir
            self.data_dir().to_path_buf()
        }
    }
}

/// Returns the standard meta directory (ignores custom directory setting)
/// Used for Java and other system components that need to stay in standard location due to macos...x
pub fn standard_meta_dir() -> PathBuf {
    if cfg!(target_os = "windows") {
        LAUNCHER_DIRECTORY.data_dir().parent().unwrap().join("meta")
    } else {
        LAUNCHER_DIRECTORY.data_dir().join("meta")
    }
}

/// Update the cached custom game directory
pub fn update_custom_game_dir(path: Option<PathBuf>) {
    if let Ok(mut guard) = CUSTOM_GAME_DIR_CACHE.write() {
        *guard = Some(path);
    }
}
