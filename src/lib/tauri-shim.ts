// Replaces @tauri-apps/api/core via Vite alias when not running inside Tauri.
// When window.__TAURI_INTERNALS__ exists (desktop app), we forward to Tauri IPC directly.
// Otherwise we return mock data so the web preview doesn't crash.

const internals = () => (window as any).__TAURI_INTERNALS__;

// ── Mock data for all commands ────────────────────────────────────────────────

const MOCK_LAUNCHER_CONFIG = {
  version: 1,
  is_experimental: false,
  auto_check_updates: true,
  concurrent_downloads: 5,
  enable_discord_presence: false,
  check_beta_channel: false,
  profile_grouping_criterion: null,
  open_logs_after_starting: false,
  concurrent_io_limit: 10,
  hooks: { pre_launch: null, wrapper: null, post_exit: null },
  hide_on_process_start: false,
  global_memory_settings: { min: 512, max: 2048 },
  global_custom_jvm_args: null,
  custom_game_directory: null,
  enable_analytics: false,
  use_browser_based_login: false,
  cache_natives_extraction: true,
  referral_state: null,
  last_played_profile: null,
  pack_rollout_override: "auto",
};

const MOCK_DATA: Record<string, unknown> = {
  get_launcher_config: MOCK_LAUNCHER_CONFIG,
  set_launcher_config: MOCK_LAUNCHER_CONFIG,
  get_app_version: "2.0.0",
  list_profiles: [],
  search_profiles: [],
  get_profile: null,
  create_profile: "preview-profile-id",
  update_profile: null,
  delete_profile: null,
  repair_profile: null,
  launch_profile: null,
  abort_profile_launch: null,
  copy_profile: null,
  export_profile: null,
  get_accounts: [],
  get_active_account: null,
  set_active_account: null,
  remove_account: null,
  begin_login: null,
  cancel_login: null,
  add_offline_account: null,
  get_processes: [],
  kill_minecraft: null,
  stop_process: null,
  open_log_window: null,
  get_process_log_cursor: { lines: [], cursor: 0 },
  fetch_crash_report: null,
  check_crash_log_command: null,
  get_user_skin_data: null,
  get_all_skins: [],
  get_active_skin: null,
  get_face_avatar: null,
  get_starlight_skin_render: null,
  apply_skin_from_base64: null,
  update_skin_properties: null,
  add_skin_locally: null,
  remove_skin: null,
  upload_skin: null,
  reset_skin: null,
  get_java_info_command: null,
  get_system_os_info: { os: "windows", version: "11", arch: "x64" },
  get_launcher_directory: "/preview",
  get_image_preview: null,
  confirm_auth_bridge: null,
  get_norisk_versions_config: { stable: [], beta: [] },
  refresh_permissions: [],
  has_permission: false,
  get_launch_manifest: null,
  get_local_resourcepacks: [],
  get_local_shaderpacks: [],
  get_local_datapacks: [],
  get_custom_mods: [],
  upload_profile_images: null,
  check_content_install_status: { installed: false, version: null },
  batch_check_content_install_status: [],
  load_items: { items: [], total: 0 },
  get_cape_data: null,
  get_all_capes: [],
  get_available_capes: [],
  equip_cape: null,
  unequip_cape: null,
  get_doofie_user: null,
  get_norisk_user: null,
  get_flagsmith_flags: {},
  track_event: null,
  open_url: null,
  open_directory: null,
  get_migration_info: null,
  migrate_profile: null,
  get_news: [],
  submit_feedback: null,
  check_for_updates: null,
  install_update: null,
  get_tester_queue_count: 0,
  open_tester_window: null,
  get_child_protection_status: { enabled: false, age_verified: true },
  set_child_protection_status: null,
  get_notification_list: [],
  mark_notification_read: null,
  refresh_standard_versions: [],
  refresh_norisk_packs: [],
  get_norisk_packs: [],
  get_standard_versions: [],
  get_blocked_mods_config: [],
  get_pack_rollout_config: null,
  get_pack_fallback_config: null,
  get_doofie_token: null,
  get_doofie_data: null,
  open_external_url: null,
  open_directory_command: null,
  get_friends: [],
  get_friend_requests: [],
  send_friend_request: null,
  accept_friend_request: null,
  decline_friend_request: null,
  remove_friend: null,
  get_launcher_news: [],
  check_for_java: null,
  get_java_installations: [],
  list_screenshots: [],
  is_profile_launching: false,
  check_for_group_migration: { direction: "None" },
};

async function mockInvoke<T>(cmd: string, _args?: unknown): Promise<T> {
  const val = MOCK_DATA[cmd];
  if (val !== undefined) {
    return (typeof val === "function" ? (val as () => T)() : val) as T;
  }
  console.warn(`[WebPreview] Unhandled mock command: "${cmd}" — returning null`);
  return null as T;
}

// ── Public API ────────────────────────────────────────────────────────────────

export function invoke<T>(
  cmd: string,
  args?: Record<string, unknown>,
): Promise<T> {
  const t = internals();
  if (t) return t.invoke(cmd, args) as Promise<T>;
  return mockInvoke<T>(cmd, args);
}

export function convertFileSrc(filePath: string, protocol = "asset"): string {
  const t = internals();
  if (t) return t.convertFileSrc(filePath, protocol);
  return filePath;
}

export function transformCallback<T>(
  _callback?: (response: T) => void,
  _once = false,
): number {
  return 0;
}

export class Channel<T = unknown> {
  id = 0;
  onmessage(_handler: (response: T) => void) {}
  toJSON() {
    return `__TAURI_CHANNEL__${this.id}`;
  }
}

export function addPluginListener(
  _plugin: string,
  _event: string,
  _cb: (payload: unknown) => void,
): Promise<() => void> {
  return Promise.resolve(() => {});
}
