use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::path::PathBuf;
use tauri::{Emitter, Manager, WebviewUrl, WebviewWindowBuilder};
use uuid::Uuid;

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct Account {
    pub id: String,
    pub username: String,
    pub uuid: String,
    pub account_type: String,
    pub is_active: bool,
    pub access_token: Option<String>,
    pub refresh_token: Option<String>,
    pub skin_url: Option<String>,
    #[serde(default)]
    pub expires_at: Option<i64>, // Unix timestamp seconds
}

fn accounts_path(game_dir: &str) -> PathBuf {
    PathBuf::from(format!("{}\\accounts.json", game_dir))
}

fn load(game_dir: &str) -> Vec<Account> {
    let p = accounts_path(game_dir);
    if !p.exists() { return vec![]; }
    serde_json::from_str(&std::fs::read_to_string(p).unwrap_or_default()).unwrap_or_default()
}

fn save(game_dir: &str, accounts: &[Account]) -> Result<(), String> {
    let p = accounts_path(game_dir);
    if let Some(parent) = p.parent() {
        std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
    }
    std::fs::write(&p, serde_json::to_string_pretty(accounts).map_err(|e| e.to_string())?)
        .map_err(|e| e.to_string())
}

fn now_unix() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs() as i64
}

fn is_expired(account: &Account) -> bool {
    match account.expires_at {
        Some(exp) => now_unix() >= exp - 300, // refresh 5 min early
        None => account.account_type == "Microsoft", // unknown → refresh to be safe
    }
}

// ── Basic commands ────────────────────────────────────────────────────────────

#[tauri::command]
pub async fn get_accounts(game_dir: String) -> Result<Vec<Account>, String> {
    Ok(load(&game_dir))
}

#[tauri::command]
pub async fn add_offline_account(username: String, game_dir: String) -> Result<(), String> {
    let mut accounts = load(&game_dir);
    for a in &mut accounts { a.is_active = false; }
    accounts.push(Account {
        id: Uuid::new_v4().to_string(),
        uuid: Uuid::new_v4().to_string(),
        username,
        account_type: "Offline".to_string(),
        is_active: true,
        access_token: None,
        refresh_token: None,
        skin_url: None,
        expires_at: None,
    });
    save(&game_dir, &accounts)
}

#[tauri::command]
pub async fn set_active_account(id: String, game_dir: String) -> Result<(), String> {
    let mut accounts = load(&game_dir);
    for a in &mut accounts { a.is_active = a.id == id; }
    save(&game_dir, &accounts)
}

#[tauri::command]
pub async fn remove_account(id: String, game_dir: String) -> Result<(), String> {
    let mut accounts = load(&game_dir);
    accounts.retain(|a| a.id != id);
    save(&game_dir, &accounts)
}

// ── Token Refresh ─────────────────────────────────────────────────────────────

/// Called on every app start. Silently refreshes expired Microsoft tokens.
#[tauri::command]
pub async fn refresh_accounts(game_dir: String) -> Result<Vec<Account>, String> {
    let mut accounts = load(&game_dir);
    let mut changed = false;

    for account in &mut accounts {
        if account.account_type != "Microsoft" { continue; }
        if !is_expired(account) { continue; }

        let refresh_token = match &account.refresh_token {
            Some(t) => t.clone(),
            None => continue,
        };

        match do_token_refresh(&refresh_token).await {
            Ok((new_mc_token, new_refresh, new_skin, new_uuid, new_name, expires)) => {
                account.access_token = Some(new_mc_token);
                account.refresh_token = Some(new_refresh);
                account.skin_url = new_skin;
                account.uuid = new_uuid;
                account.username = new_name;
                account.expires_at = Some(expires);
                changed = true;
            }
            Err(e) => {
                eprintln!("Token refresh failed for {}: {}", account.username, e);
            }
        }
    }

    if changed {
        save(&game_dir, &accounts)?;
    }

    Ok(accounts)
}

async fn do_token_refresh(refresh_token: &str) -> Result<(String, String, Option<String>, String, String, i64), String> {
    let client = reqwest::Client::new();

    // 1. Refresh MS token
    let ms: MsTokenResp = client
        .post("https://login.live.com/oauth20_token.srf")
        .form(&[
            ("client_id", CLIENT_ID),
            ("refresh_token", refresh_token),
            ("grant_type", "refresh_token"),
            ("scope", "XboxLive.signin offline_access"),
        ])
        .send()
        .await
        .map_err(|e| format!("MS refresh failed: {e}"))?
        .json()
        .await
        .map_err(|e| format!("MS refresh parse failed: {e}"))?;

    if let Some(err) = ms.error {
        return Err(format!("{err}: {}", ms.error_description.unwrap_or_default()));
    }

    let ms_token = ms.access_token.ok_or("No MS access token in refresh")?;
    let new_refresh = ms.refresh_token.ok_or("No new refresh token")?;
    let expires_at = now_unix() + ms.expires_in.unwrap_or(86400) as i64;

    // 2-4. Xbox → XSTS → Minecraft (reuse existing logic)
    let (mc_token, uuid, username, skin_url) = xbox_to_minecraft(&client, &ms_token).await?;

    Ok((mc_token, new_refresh, skin_url, uuid, username, expires_at))
}

// ── Microsoft Login via WebviewWindow ────────────────────────────────────────

const CLIENT_ID: &str = "00000000402b5328";
const REDIRECT_URI: &str = "https://login.live.com/oauth20_desktop.srf";

fn build_auth_url() -> String {
    format!(
        "https://login.live.com/oauth20_authorize.srf\
        ?client_id={}\
        &redirect_uri={}\
        &scope=XboxLive.signin%20offline_access\
        &response_type=code\
        &prompt=select_account",
        CLIENT_ID,
        urlencoding::encode(REDIRECT_URI),
    )
}

#[tauri::command]
pub async fn start_microsoft_webview_login(
    game_dir: String,
    app: tauri::AppHandle,
) -> Result<(), String> {
    if let Some(old) = app.get_webview_window("signin") {
        let _ = old.close();
        tokio::time::sleep(std::time::Duration::from_millis(400)).await;
    }

    let auth_url: url::Url = build_auth_url()
        .parse()
        .map_err(|e: url::ParseError| e.to_string())?;

    let (win_tx, win_rx) = std::sync::mpsc::channel::<Result<tauri::WebviewWindow, String>>();

    let app_mt = app.clone();
    app.run_on_main_thread(move || {
        let result = WebviewWindowBuilder::new(
            &app_mt,
            "signin",
            WebviewUrl::External(auth_url),
        )
        .title("Sign in with Microsoft")
        .inner_size(490.0, 660.0)
        .resizable(false)
        .always_on_top(true)
        .center()
        .build()
        .map_err(|e| format!("Could not open login window: {e}"));

        if let Ok(ref win) = result {
            let _ = win.set_focus();
        }
        let _ = win_tx.send(result);
    })
    .map_err(|e| format!("run_on_main_thread failed: {e}"))?;

    let win = tokio::task::spawn_blocking(move || {
        win_rx
            .recv_timeout(std::time::Duration::from_secs(10))
            .map_err(|_| "Timed out waiting for login window".to_string())?
    })
    .await
    .map_err(|e| format!("spawn_blocking panicked: {e}"))??;

    let app2 = app.clone();
    tauri::async_runtime::spawn(async move {
        poll_for_redirect(win, app2, game_dir).await;
    });

    Ok(())
}

async fn poll_for_redirect(win: tauri::WebviewWindow, app: tauri::AppHandle, game_dir: String) {
    let start = std::time::Instant::now();
    loop {
        tokio::time::sleep(std::time::Duration::from_millis(150)).await;

        if start.elapsed() > std::time::Duration::from_secs(600) {
            let _ = win.close();
            let _ = app.emit("ms_login_error", "Login timed out".to_string());
            return;
        }

        if app.get_webview_window("signin").is_none() {
            let _ = app.emit("ms_login_error", "Login cancelled".to_string());
            return;
        }

        let current_url = match win.url() {
            Ok(u) => u,
            Err(_) => continue,
        };

        if !current_url.as_str().starts_with(REDIRECT_URI) { continue; }

        let _ = win.close();

        if let Some(err) = current_url.query_pairs().find(|(k, _)| k == "error").map(|(_, v)| v.to_string()) {
            let _ = app.emit("ms_login_error", format!("Login cancelled: {err}"));
            return;
        }

        let code = match current_url.query_pairs().find(|(k, _)| k == "code").map(|(_, v)| v.to_string()) {
            Some(c) => c,
            None => { let _ = app.emit("ms_login_error", "No auth code received".to_string()); return; }
        };

        match exchange_code(&code, &game_dir).await {
            Ok(_) => { let _ = app.emit("ms_login_complete", ()); }
            Err(e) => { let _ = app.emit("ms_login_error", e); }
        }
        return;
    }
}

// ── Token exchange (shared) ───────────────────────────────────────────────────

#[derive(Deserialize)]
struct MsTokenResp {
    access_token: Option<String>,
    refresh_token: Option<String>,
    expires_in: Option<i64>,
    error: Option<String>,
    error_description: Option<String>,
}

#[derive(Deserialize)]
struct XblResp {
    #[serde(rename = "Token")]
    token: String,
    #[serde(rename = "DisplayClaims")]
    display_claims: XblClaims,
}

#[derive(Deserialize)]
struct XblClaims {
    xui: Vec<HashMap<String, String>>,
}

#[derive(Deserialize)]
struct McTokenResp {
    access_token: String,
}

#[derive(Deserialize)]
struct McProfile {
    id: String,
    name: String,
    skins: Vec<McSkin>,
}

#[derive(Deserialize)]
struct McSkin {
    url: String,
    state: String,
}

async fn xbox_to_minecraft(
    client: &reqwest::Client,
    ms_token: &str,
) -> Result<(String, String, String, Option<String>), String> {
    let xbl: XblResp = client
        .post("https://user.auth.xboxlive.com/user/authenticate")
        .json(&serde_json::json!({
            "Properties": { "AuthMethod": "RPS", "SiteName": "user.auth.xboxlive.com", "RpsTicket": format!("d={}", ms_token) },
            "RelyingParty": "http://auth.xboxlive.com",
            "TokenType": "JWT"
        }))
        .send().await.map_err(|e| format!("XBL failed: {e}"))?
        .json().await.map_err(|e| format!("XBL parse: {e}"))?;

    let uhs = xbl.display_claims.xui.first()
        .and_then(|c| c.get("uhs")).cloned()
        .ok_or("Missing UHS")?;

    let xsts: XblResp = client
        .post("https://xsts.auth.xboxlive.com/xsts/authorize")
        .json(&serde_json::json!({
            "Properties": { "SandboxId": "RETAIL", "UserTokens": [xbl.token] },
            "RelyingParty": "rp://api.minecraftservices.com/",
            "TokenType": "JWT"
        }))
        .send().await.map_err(|e| format!("XSTS failed: {e}"))?
        .json().await.map_err(|e| format!("XSTS parse: {e}"))?;

    let mc: McTokenResp = client
        .post("https://api.minecraftservices.com/authentication/login_with_xbox")
        .json(&serde_json::json!({ "identityToken": format!("XBL3.0 x={};{}", uhs, xsts.token) }))
        .send().await.map_err(|e| format!("MC auth failed: {e}"))?
        .json().await.map_err(|e| format!("MC token parse: {e}"))?;

    let profile: McProfile = client
        .get("https://api.minecraftservices.com/minecraft/profile")
        .bearer_auth(&mc.access_token)
        .send().await.map_err(|e| format!("Profile failed: {e}"))?
        .json().await.map_err(|e| format!("Profile parse: {e}"))?;

    let skin_url = profile.skins.iter().find(|s| s.state == "ACTIVE").map(|s| s.url.clone());

    Ok((mc.access_token, profile.id, profile.name, skin_url))
}

async fn exchange_code(code: &str, game_dir: &str) -> Result<(), String> {
    let client = reqwest::Client::new();

    let ms: MsTokenResp = client
        .post("https://login.live.com/oauth20_token.srf")
        .form(&[
            ("client_id", CLIENT_ID),
            ("code", code),
            ("grant_type", "authorization_code"),
            ("redirect_uri", REDIRECT_URI),
            ("scope", "XboxLive.signin offline_access"),
        ])
        .send().await.map_err(|e| format!("MS token failed: {e}"))?
        .json().await.map_err(|e| format!("MS token parse: {e}"))?;

    if let Some(err) = ms.error {
        return Err(format!("{err}: {}", ms.error_description.unwrap_or_default()));
    }

    let ms_token = ms.access_token.ok_or("No MS access token")?;
    let expires_at = now_unix() + ms.expires_in.unwrap_or(86400);

    let (mc_token, uuid, username, skin_url) = xbox_to_minecraft(&client, &ms_token).await?;

    let mut accounts = load(game_dir);
    for a in &mut accounts { a.is_active = false; }
    accounts.push(Account {
        id: Uuid::new_v4().to_string(),
        uuid,
        username,
        account_type: "Microsoft".to_string(),
        is_active: true,
        access_token: Some(mc_token),
        refresh_token: ms.refresh_token,
        skin_url,
        expires_at: Some(expires_at),
    });

    save(game_dir, &accounts)
}
