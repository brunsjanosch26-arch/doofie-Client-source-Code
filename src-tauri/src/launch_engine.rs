// Prism Launcher-inspired launch engine for Minecraft Java Edition.
// Handles: version JSON merging, Java detection, classpath/natives, logging config, argument building.

use std::collections::HashMap;
use std::io::{BufRead, Read};
use std::path::{Path, PathBuf};
use std::process::Stdio;
use serde_json::Value;
use tauri::{AppHandle, Emitter};

// ─────────────────────────────────────────────────────────────────────────────
//  Public config + entry point
// ─────────────────────────────────────────────────────────────────────────────

pub struct LaunchConfig {
    pub version: String,
    pub game_dir: String,
    pub instance_dir: String,
    pub java_path: Option<String>,
    pub username: String,
    pub uuid: String,
    pub access_token: String,
    pub ram_gb: u32,
    pub extra_jvm_args: String,
}

pub async fn launch(cfg: LaunchConfig, app: AppHandle) -> Result<String, String> {
    let versions_dir = PathBuf::from(format!("{}\\versions", cfg.game_dir));
    let libs_dir     = PathBuf::from(format!("{}\\libraries", cfg.game_dir));

    // 1. Load + merge version JSON (resolves inheritsFrom chain)
    emit_log(&app, "[LAUNCHER] Lade Version-Konfiguration...");
    let ver_json = load_merged_version(&cfg.version, &versions_dir)?;

    // 2. Ensure all asset objects exist (crash cause when missing)
    emit_log(&app, "[LAUNCHER] Prüfe Assets...");
    ensure_assets_downloaded(&ver_json, &cfg.game_dir, &app).await;

    // 3. Determine correct Java
    emit_log(&app, "[LAUNCHER] Suche Java-Installation...");
    let java_path = resolve_java(&cfg.java_path, &ver_json)?;
    emit_log(&app, &format!("[LAUNCHER] Verwende Java: {}", java_path));

    // 4. Classpath + native JARs
    let (mut cp_parts, native_jars) = collect_libraries(&ver_json, &libs_dir);

    // Client JAR (from the base MC version, stored in _clientVer)
    let client_ver = ver_json["_clientVer"].as_str().unwrap_or(&cfg.version).to_string();
    let client_jar = PathBuf::from(format!(
        "{}\\versions\\{}\\{}.jar",
        cfg.game_dir, client_ver, client_ver
    ));
    if client_jar.exists() {
        cp_parts.push(client_jar.to_string_lossy().to_string());
    }
    if cp_parts.is_empty() {
        return Err("Classpath leer – Version nicht vollständig installiert. Bitte erneut herunterladen.".to_string());
    }
    let classpath = cp_parts.join(";");

    // 4. Extract native libraries (DLLs) from native JARs
    let natives_dir = PathBuf::from(format!(
        "{}\\versions\\{}\\natives",
        cfg.game_dir, client_ver
    ));
    std::fs::create_dir_all(&natives_dir).ok();
    extract_natives(&native_jars, &natives_dir);

    // 5. Logging configuration (log4j2 XML, prevents some startup crashes)
    let log_arg = ensure_logging_config(&ver_json, &cfg.game_dir).await;

    // 6. Ensure instance subdirectories
    let assets_dir    = format!("{}\\assets", cfg.game_dir);
    let asset_index   = ver_json["assetIndex"]["id"].as_str().unwrap_or("legacy").to_string();
    let main_class    = ver_json["mainClass"].as_str()
        .unwrap_or("net.minecraft.client.main.Main")
        .to_string();
    let is_offline    = cfg.access_token == "0" || cfg.access_token.is_empty();

    for sub in &["mods","saves","config","screenshots","logs","resourcepacks","shaderpacks"] {
        std::fs::create_dir_all(format!("{}\\{}", cfg.instance_dir, sub)).ok();
    }

    // 7. Variable substitution map
    let mut vars: HashMap<&'static str, String> = HashMap::new();
    vars.insert("natives_directory",  natives_dir.to_string_lossy().to_string());
    vars.insert("launcher_name",      "cookie-client".into());
    vars.insert("launcher_version",   "1.0".into());
    vars.insert("classpath",          classpath.clone());
    vars.insert("auth_player_name",   cfg.username.clone());
    vars.insert("version_name",       cfg.version.clone());
    vars.insert("game_directory",     cfg.instance_dir.clone());
    vars.insert("assets_root",        assets_dir.clone());
    vars.insert("assets_index_name",  asset_index.clone());
    vars.insert("auth_uuid",          if cfg.uuid.is_empty() { "00000000000000000000000000000000".into() } else { cfg.uuid.clone() });
    vars.insert("auth_access_token",  if is_offline { "0".into() } else { cfg.access_token.clone() });
    vars.insert("user_type",          if is_offline { "legacy".into() } else { "msa".into() });
    vars.insert("version_type",       ver_json["type"].as_str().unwrap_or("release").into());
    vars.insert("clientid",           String::new());
    vars.insert("auth_xuid",          String::new());
    vars.insert("resolution_width",   "854".into());
    vars.insert("resolution_height",  "480".into());

    // 8. Build command
    let mut cmd = std::process::Command::new(&java_path);

    // Memory (always first)
    cmd.arg(format!("-Xmx{}G", cfg.ram_gb))
       .arg(format!("-Xms{}G", std::cmp::max(1, cfg.ram_gb / 2)));

    // User-defined extra JVM args
    for arg in cfg.extra_jvm_args.split_whitespace() {
        if !arg.is_empty() { cmd.arg(arg); }
    }

    // Logging config arg (e.g. -Dlog4j.configurationFile=...)
    if let Some(la) = &log_arg {
        cmd.arg(subst(la, &vars));
    }

    // JVM args from version.json (new format 1.13+) or fallback
    let has_new_args = ver_json["arguments"].is_object();
    if has_new_args {
        if let Some(jvm) = ver_json["arguments"]["jvm"].as_array() {
            apply_args(&mut cmd, jvm, &vars);
        }
    } else {
        // Old format: manually set natives path + classpath
        cmd.arg(format!("-Djava.library.path={}", natives_dir.to_string_lossy()))
           .arg("-cp").arg(&classpath);
    }

    // Main class
    cmd.arg(&main_class);

    // Game args from version.json or fallback to minecraftArguments string
    if has_new_args {
        if let Some(game) = ver_json["arguments"]["game"].as_array() {
            apply_args(&mut cmd, game, &vars);
        }
    } else if let Some(mc_args) = ver_json["minecraftArguments"].as_str() {
        for arg in mc_args.split_whitespace() {
            cmd.arg(subst(arg, &vars));
        }
    }

    // 9. Spawn (no console window on Windows)
    #[cfg(target_os = "windows")]
    {
        use std::os::windows::process::CommandExt;
        cmd.creation_flags(0x08000000); // CREATE_NO_WINDOW
    }

    let mut child = cmd
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .map_err(|e| format!("Start fehlgeschlagen: {}", e))?;

    emit_log(&app, &format!(
        "[LAUNCHER] Minecraft {} gestartet | Java: {} | RAM: {}G",
        cfg.version, java_path, cfg.ram_gb
    ));

    // 10. Stream stdout / stderr to frontend
    let stdout = child.stdout.take().unwrap();
    let stderr = child.stderr.take().unwrap();

    let app_out = app.clone();
    std::thread::spawn(move || {
        for line in std::io::BufReader::new(stdout).lines().flatten() {
            let _ = app_out.emit("game_log", line);
        }
    });

    let app_err = app.clone();
    std::thread::spawn(move || {
        for line in std::io::BufReader::new(stderr).lines().flatten() {
            let _ = app_err.emit("game_log", format!("[ERR] {}", line));
        }
    });

    std::thread::spawn(move || {
        if let Ok(status) = child.wait() {
            let code = status.code().unwrap_or(-1);
            let _ = app.emit("game_log", format!("[LAUNCHER] Prozess beendet (exit code: {})", code));
            let _ = app.emit("game_stopped", code);
        }
    });

    Ok("Minecraft gestartet".to_string())
}

fn emit_log(app: &AppHandle, msg: &str) {
    let _ = app.emit("game_log", msg);
}

// ─────────────────────────────────────────────────────────────────────────────
//  Version JSON loading + merging
// ─────────────────────────────────────────────────────────────────────────────

/// Loads a version JSON and recursively resolves inheritsFrom, returning a fully merged JSON.
pub fn load_merged_version(version: &str, versions_dir: &Path) -> Result<Value, String> {
    let path = versions_dir.join(version).join(format!("{}.json", version));
    let content = std::fs::read_to_string(&path)
        .map_err(|e| format!("Version '{}' nicht gefunden: {}", version, e))?;
    let json: Value = serde_json::from_str(&content)
        .map_err(|e| format!("JSON-Fehler in '{}': {}", version, e))?;

    if let Some(base_id) = json["inheritsFrom"].as_str().map(String::from) {
        let base = load_merged_version(&base_id, versions_dir)
            .map_err(|e| format!("Basis-Version '{}' nicht installiert: {}", base_id, e))?;
        let mut merged = merge_version_json(base, json)?;
        // Preserve base's client-JAR id for later use
        if merged.get("_clientVer").is_none() {
            if let Some(obj) = merged.as_object_mut() {
                obj.insert("_clientVer".into(), Value::String(base_id));
            }
        }
        Ok(merged)
    } else {
        let mut json = json;
        if let Some(obj) = json.as_object_mut() {
            let id = obj.get("id").and_then(|v| v.as_str()).unwrap_or(version).to_string();
            obj.entry("_clientVer").or_insert(Value::String(id));
        }
        Ok(json)
    }
}

/// Merges overlay into base. Scalar fields: overlay wins. Lists: base first, overlay appended.
fn merge_version_json(base: Value, overlay: Value) -> Result<Value, String> {
    let mut m = match base { Value::Object(o) => o, _ => return Ok(overlay) };
    let ov    = match overlay { Value::Object(o) => o, _ => return Ok(Value::Object(m)) };

    for (k, v) in ov {
        match k.as_str() {
            "libraries" => {
                let base_libs = m.get("libraries").and_then(|v| v.as_array()).cloned().unwrap_or_default();
                let ov_libs   = v.as_array().cloned().unwrap_or_default();
                let mut combined = base_libs;
                combined.extend(ov_libs);
                m.insert(k, Value::Array(combined));
            }
            "arguments" => {
                let Value::Object(ov_args) = v else { continue };
                let mut base_args = m.get("arguments").and_then(|v| v.as_object()).cloned()
                    .unwrap_or_default();
                for (section, sv) in ov_args {
                    let base_sec = base_args.get(&section).and_then(|v| v.as_array()).cloned().unwrap_or_default();
                    let ov_sec   = sv.as_array().cloned().unwrap_or_default();
                    let mut combined = base_sec;
                    combined.extend(ov_sec);
                    base_args.insert(section, Value::Array(combined));
                }
                m.insert("arguments".into(), Value::Object(base_args));
            }
            "inheritsFrom" | "_clientVer" => {} // never propagate these
            _ => { m.insert(k, v); }
        }
    }
    Ok(Value::Object(m))
}

// ─────────────────────────────────────────────────────────────────────────────
//  Rule evaluation
// ─────────────────────────────────────────────────────────────────────────────

fn rules_allow_on_windows(rules: &Value) -> bool {
    let arr = match rules.as_array() {
        Some(a) if !a.is_empty() => a,
        _ => return true,
    };
    let mut allowed = false;
    for rule in arr {
        let action = rule["action"].as_str().unwrap_or("disallow");
        if rule.get("features").map(|f| f.is_object()).unwrap_or(false) { continue; }
        if rule["os"].is_null() || !rule["os"].is_object() {
            allowed = action == "allow";
        } else {
            let name = rule["os"]["name"].as_str().unwrap_or("");
            if name == "windows" || name.is_empty() { allowed = action == "allow"; }
        }
    }
    allowed
}

// ─────────────────────────────────────────────────────────────────────────────
//  Library collection
// ─────────────────────────────────────────────────────────────────────────────

/// Converts a Maven coordinate to a relative JAR path.
/// e.g. "net.fabricmc:fabric-loader:0.19.3" → "net/fabricmc/fabric-loader/0.19.3/fabric-loader-0.19.3.jar"
fn maven_to_path(name: &str) -> Option<String> {
    let parts: Vec<&str> = name.splitn(4, ':').collect();
    if parts.len() < 3 { return None; }
    let group    = parts[0].replace('.', "/");
    let artifact = parts[1];
    let version  = parts[2];
    let filename = if let Some(clf) = parts.get(3) {
        format!("{}-{}-{}.jar", artifact, version, clf)
    } else {
        format!("{}-{}.jar", artifact, version)
    };
    Some(format!("{}/{}/{}/{}", group, artifact, version, filename))
}

/// Returns (classpath_entries, native_jar_paths).
/// Handles both standard Mojang format (downloads.artifact.path) and
/// Fabric/Forge maven-coordinate-only format (name field only).
fn collect_libraries(ver_json: &Value, libs_dir: &Path) -> (Vec<String>, Vec<PathBuf>) {
    let mut cp      = vec![];
    let mut natives = vec![];

    let libs = match ver_json["libraries"].as_array() {
        Some(l) => l,
        None => return (cp, natives),
    };

    for lib in libs {
        if !rules_allow_on_windows(&lib["rules"]) { continue; }

        // Try explicit artifact path; fall back to deriving from maven coordinates.
        // Fabric libraries only have a "name" field and no downloads.artifact block.
        let rel_opt = lib["downloads"]["artifact"]["path"].as_str()
            .map(String::from)
            .or_else(|| lib["name"].as_str().and_then(maven_to_path));

        if let Some(rel) = rel_opt {
            let path = libs_dir.join(&rel);
            if path.exists() {
                if rel.contains("natives") { natives.push(path.clone()); }
                let s = path.to_string_lossy().to_string();
                if !cp.contains(&s) { cp.push(s); }
            }
        }

        // Old-style native classifiers (pre 1.19)
        let nat_key = lib["natives"]["windows"].as_str().unwrap_or("natives-windows");
        if let Some(rel) = lib["downloads"]["classifiers"][nat_key]["path"].as_str() {
            let path = libs_dir.join(rel);
            if path.exists() {
                natives.push(path.clone());
                let s = path.to_string_lossy().to_string();
                if !cp.contains(&s) { cp.push(s); }
            }
        }
    }

    (cp, natives)
}

/// Extracts .dll / .so / .dylib files from native JARs (ZIP archives) into natives_dir.
fn extract_natives(jars: &[PathBuf], natives_dir: &Path) {
    for jar in jars {
        let Ok(file)    = std::fs::File::open(jar)        else { continue };
        let Ok(mut zip) = zip::ZipArchive::new(file)       else { continue };
        for i in 0..zip.len() {
            let Ok(mut entry) = zip.by_index(i) else { continue };
            let name = entry.name().to_string();
            if !name.ends_with(".dll") && !name.ends_with(".so") && !name.ends_with(".dylib") { continue; }
            let filename = name.split('/').last().unwrap_or(&name);
            let dest = natives_dir.join(filename);
            if dest.exists() { continue; }
            let mut buf = vec![];
            if entry.read_to_end(&mut buf).is_ok() {
                std::fs::write(&dest, &buf).ok();
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Java detection
// ─────────────────────────────────────────────────────────────────────────────

fn resolve_java(configured: &Option<String>, ver_json: &Value) -> Result<String, String> {
    // 1. Explicitly configured path
    if let Some(p) = configured {
        if !p.is_empty() && PathBuf::from(p).exists() {
            return Ok(p.clone());
        }
    }

    // 2. Required major version from version.json
    let required = ver_json["javaVersion"]["majorVersion"].as_u64().map(|v| v as u32);
    if let Some(major) = required {
        if let Some(path) = find_java_for_major(major) {
            return Ok(path);
        }
        // Warn but fall through to any Java
        // (user might have compatible version installed differently)
    }

    // 3. Search well-known paths for any recent Java
    let fallbacks = [
        "C:\\Program Files\\Java\\jdk-21\\bin\\java.exe",
        "C:\\Program Files\\Java\\jdk-17\\bin\\java.exe",
        "C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.0+0\\bin\\java.exe",
        "C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.0+0\\bin\\java.exe",
        "C:\\Program Files\\Microsoft\\jdk-21.0.0+0\\bin\\java.exe",
        "C:\\Program Files\\Microsoft\\jdk-17.0.0+0\\bin\\java.exe",
    ];
    for p in &fallbacks {
        if PathBuf::from(p).exists() { return Ok(p.to_string()); }
    }

    // 4. PATH java
    which::which("java")
        .map(|p| p.to_string_lossy().to_string())
        .map_err(|_| {
            let major = required.unwrap_or(21);
            format!(
                "Java {} nicht gefunden. Bitte von https://adoptium.net installieren.",
                major
            )
        })
}

fn find_java_for_major(major: u32) -> Option<String> {
    let bases = [
        "C:\\Program Files\\Java",
        "C:\\Program Files\\Eclipse Adoptium",
        "C:\\Program Files\\Microsoft",
        "C:\\Program Files (x86)\\Java",
        "C:\\Program Files\\BellSoft",
        "C:\\Program Files\\Amazon Corretto",
        "C:\\Program Files\\Zulu",
    ];
    for base in &bases {
        if let Some(p) = search_dir_for_java(Path::new(base), major) {
            return Some(p);
        }
    }
    // JAVA_HOME
    if let Ok(home) = std::env::var("JAVA_HOME") {
        let exe = PathBuf::from(&home).join("bin\\java.exe");
        if exe.exists() && get_java_major(&exe) == Some(major) {
            return Some(exe.to_string_lossy().to_string());
        }
    }
    // PATH
    if let Ok(p) = which::which("java") {
        if get_java_major(&p) == Some(major) {
            return Some(p.to_string_lossy().to_string());
        }
    }
    None
}

fn search_dir_for_java(base: &Path, major: u32) -> Option<String> {
    if !base.exists() { return None; }
    let prefixes = [
        format!("jdk-{}", major),
        format!("jdk{}", major),
        format!("temurin-{}", major),
        format!("corretto-{}", major),
    ];
    if let Ok(entries) = std::fs::read_dir(base) {
        for e in entries.flatten() {
            let name = e.file_name().to_string_lossy().to_lowercase();
            if prefixes.iter().any(|p| name.starts_with(p)) {
                let exe = e.path().join("bin\\java.exe");
                if exe.exists() { return Some(exe.to_string_lossy().to_string()); }
            }
        }
    }
    None
}

fn get_java_major(exe: &Path) -> Option<u32> {
    let out = std::process::Command::new(exe).arg("-version").output().ok()?;
    parse_java_major(&String::from_utf8_lossy(&out.stderr))
}

fn parse_java_major(text: &str) -> Option<u32> {
    for line in text.lines() {
        if !line.contains("version") { continue; }
        if let (Some(s), Some(rest)) = (line.find('"'), line.find('"').map(|i| &line[i+1..])) {
            let _ = s; // suppress unused warning
            if let Some(e) = rest.find('"') {
                let ver = &rest[..e];
                let parts: Vec<&str> = ver.split('.').collect();
                if let Some(first) = parts.first() {
                    if let Ok(n) = first.parse::<u32>() {
                        // Old 1.x format (e.g. "1.8.0_xx" → major 8)
                        if n == 1 {
                            return parts.get(1).and_then(|v| v.parse::<u32>().ok());
                        }
                        return Some(n);
                    }
                }
            }
        }
    }
    None
}

// ─────────────────────────────────────────────────────────────────────────────
//  Asset object downloading
// ─────────────────────────────────────────────────────────────────────────────

/// Downloads missing asset objects (textures, sounds, …) referenced by the asset index.
/// Minecraft crashes at startup if these files are absent.
async fn ensure_assets_downloaded(ver_json: &Value, game_dir: &str, app: &AppHandle) {
    let index_id  = ver_json["assetIndex"]["id"].as_str().unwrap_or_default();
    let index_url = ver_json["assetIndex"]["url"].as_str().unwrap_or_default();
    let index_dir = PathBuf::from(format!("{}\\assets\\indexes", game_dir));
    let index_path = index_dir.join(format!("{}.json", index_id));

    // Download index if missing
    if !index_path.exists() && !index_url.is_empty() {
        std::fs::create_dir_all(&index_dir).ok();
        if let Ok(client) = reqwest::Client::builder().timeout(std::time::Duration::from_secs(30)).build() {
            if let Ok(resp) = client.get(index_url).send().await {
                if let Ok(bytes) = resp.bytes().await {
                    std::fs::write(&index_path, &bytes).ok();
                }
            }
        }
    }

    let content   = match std::fs::read_to_string(&index_path) { Ok(c) => c, Err(_) => return };
    let index_json: Value = match serde_json::from_str(&content) { Ok(j) => j, Err(_) => return };
    let objects   = match index_json["objects"].as_object() { Some(o) => o, None => return };
    let obj_dir   = PathBuf::from(format!("{}\\assets\\objects", game_dir));

    // Collect missing objects
    let missing: Vec<(String, PathBuf)> = objects.values().filter_map(|obj| {
        let hash = obj["hash"].as_str()?;
        if hash.len() < 2 { return None; }
        let prefix = &hash[..2];
        let path   = obj_dir.join(prefix).join(hash);
        if path.exists() { return None; }
        Some((format!("https://resources.download.minecraft.net/{}/{}", prefix, hash), path))
    }).collect();

    if missing.is_empty() { return; }

    let total = missing.len();
    let _ = app.emit("game_log", format!("[LAUNCHER] Lade {} fehlende Assets herunter...", total));

    let client = match reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(30))
        .build() { Ok(c) => c, Err(_) => return };

    let mut done = 0usize;
    for chunk in missing.chunks(32) {
        let mut handles = vec![];
        for (url, path) in chunk {
            let c = client.clone();
            let u = url.clone();
            let p = path.clone();
            handles.push(tokio::task::spawn(async move {
                if let Some(parent) = p.parent() { std::fs::create_dir_all(parent).ok(); }
                if let Ok(resp) = c.get(&u).send().await {
                    if let Ok(bytes) = resp.bytes().await { std::fs::write(&p, bytes).ok(); }
                }
            }));
        }
        for h in handles { h.await.ok(); }
        done += chunk.len();
        if done % 320 == 0 || done == total {
            let _ = app.emit("game_log", format!("[LAUNCHER] Assets: {}/{}", done, total));
        }
    }
    let _ = app.emit("game_log", format!("[LAUNCHER] Assets vollständig ({} Dateien geladen)", total));
}

// ─────────────────────────────────────────────────────────────────────────────
//  Log4j2 logging configuration
// ─────────────────────────────────────────────────────────────────────────────

async fn ensure_logging_config(ver_json: &Value, game_dir: &str) -> Option<String> {
    let client  = &ver_json["logging"]["client"];
    let file_id = client["file"]["id"].as_str()?;
    let url     = client["file"]["url"].as_str()?;
    let arg_tpl = client["argument"].as_str()?;

    let dir  = PathBuf::from(format!("{}\\assets\\log_configs", game_dir));
    std::fs::create_dir_all(&dir).ok()?;
    let dest = dir.join(file_id);

    if !dest.exists() {
        let http = reqwest::Client::builder()
            .timeout(std::time::Duration::from_secs(30))
            .build().ok()?;
        let bytes = http.get(url).send().await.ok()?.bytes().await.ok()?;
        std::fs::write(&dest, &bytes).ok()?;
    }

    dest.exists()
        .then(|| arg_tpl.replace("${path}", &dest.to_string_lossy()))
}

// ─────────────────────────────────────────────────────────────────────────────
//  Argument building
// ─────────────────────────────────────────────────────────────────────────────

fn subst(s: &str, vars: &HashMap<&'static str, String>) -> String {
    let mut out = s.to_string();
    for (k, v) in vars { out = out.replace(&format!("${{{}}}", k), v); }
    out
}

fn apply_args(cmd: &mut std::process::Command, args: &[Value], vars: &HashMap<&'static str, String>) {
    for arg in args {
        if let Some(s) = arg.as_str() {
            cmd.arg(subst(s, vars));
        } else if arg.is_object() {
            if !rules_allow_on_windows(&arg["rules"]) { continue; }
            let val = &arg["value"];
            if let Some(s) = val.as_str() {
                cmd.arg(subst(s, vars));
            } else if let Some(arr) = val.as_array() {
                for v in arr {
                    if let Some(s) = v.as_str() { cmd.arg(subst(s, vars)); }
                }
            }
        }
    }
}
