try:
    import minecraft_launcher_lib
except ImportError:
    import subprocess as _sp
    import sys as _sys
    print("[LAUNCHER] Installiere minecraft-launcher-lib...", flush=True)
    _sp.check_call([_sys.executable, "-m", "pip", "install", "minecraft-launcher-lib"])
    import minecraft_launcher_lib

import subprocess
import threading
import json
import os
from urllib.request import urlopen

minecraft_directory = minecraft_launcher_lib.utils.get_minecraft_directory()
release_versions = ["1.21.4"]
all_versions = ["1.21.4"]
fabric_loader_versions = ["0.16.10"]
forge_loader_versions = []
neoforge_loader_versions = []
versions_loaded = False
log_callback = None
state_callback = None
running_games = {}
running_games_lock = threading.Lock()

def set_log_callback(callback):
    global log_callback
    log_callback = callback

def set_state_callback(callback):
    global state_callback
    state_callback = callback

def notify_state_changed(instance_name):
    if state_callback is not None:
        state_callback(instance_name)

def log(message):
    print(message, flush=True)
    if log_callback is not None:
        log_callback(message)

def ensure_game_directory(instance_directory):
    if instance_directory == "":
        instance_directory = os.path.join(os.getcwd(), "instances", "default")
    instance_directory = os.path.abspath(instance_directory)
    os.makedirs(instance_directory, exist_ok=True)
    os.makedirs(os.path.join(instance_directory, "saves"), exist_ok=True)
    os.makedirs(os.path.join(instance_directory, "mods"), exist_ok=True)
    os.makedirs(os.path.join(instance_directory, "resourcepacks"), exist_ok=True)
    os.makedirs(os.path.join(instance_directory, "shaderpacks"), exist_ok=True)
    return instance_directory

def get_launch_options(username, uuid, access_token, instance_directory, ram=4096, demo=False):
    ram = max(512, int(ram))
    options = {
        "username": username,
        "uuid": uuid,
        "token": access_token,
        "gameDirectory": instance_directory,
        "jvmArguments": [f"-Xmx{ram}M", f"-Xms{min(512, ram)}M"]
    }
    if demo:
        options["demo"] = True
    return options

def start_process(launch_command, instance_directory, instance_name):
    creationflags = 0
    if hasattr(subprocess, "CREATE_NO_WINDOW"):
        creationflags = subprocess.CREATE_NO_WINDOW

    process = subprocess.Popen(
        launch_command,
        cwd=instance_directory,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        stdin=subprocess.DEVNULL,
        text=True,
        encoding="utf-8",
        errors="replace",
        creationflags=creationflags
    )
    with running_games_lock:
        running_games[instance_name] = process
    notify_state_changed(instance_name)
    threading.Thread(target=pipe_process_output, args=(process, instance_name), daemon=True).start()
    return process

def pipe_process_output(process, instance_name):
    try:
        if process.stdout is not None:
            for line in process.stdout:
                line = line.rstrip()
                if line:
                    log(line)
        return_code = process.wait()
        log(f"Minecraft closed with exit code {return_code}.")
    except Exception as e:
        log(f"Could not read Minecraft output: {e}")
    finally:
        with running_games_lock:
            if running_games.get(instance_name) is process:
                running_games.pop(instance_name, None)
        notify_state_changed(instance_name)

def is_game_running(instance_name):
    with running_games_lock:
        process = running_games.get(instance_name)
    if process is None:
        return False
    if process.poll() is None:
        return True
    with running_games_lock:
        if running_games.get(instance_name) is process:
            running_games.pop(instance_name, None)
    notify_state_changed(instance_name)
    return False

def kill_game(instance_name):
    with running_games_lock:
        process = running_games.get(instance_name)
    if process is None:
        notify_state_changed(instance_name)
        return False
    try:
        if process.poll() is None:
            log(f"Killing {instance_name}.")
            process.terminate()
            try:
                process.wait(timeout=8)
            except subprocess.TimeoutExpired:
                process.kill()
    except Exception as e:
        log(f"Could not kill {instance_name}: {e}")
        return False
    finally:
        with running_games_lock:
            if running_games.get(instance_name) is process:
                running_games.pop(instance_name, None)
        notify_state_changed(instance_name)
    return True

def refresh_versions():
    global release_versions, all_versions, fabric_loader_versions, forge_loader_versions, neoforge_loader_versions, versions_loaded
    try:
        version_list = minecraft_launcher_lib.utils.get_version_list()
        releases = []
        versions = []
        for version in version_list:
            version_id = version.get("id", "")
            version_type = version.get("type", "")
            if version_id == "":
                continue
            versions.append(version_id)
            if version_type == "release":
                releases.append(version_id)

        if releases:
            release_versions = releases
        if versions:
            all_versions = versions
        loaders = fetch_fabric_loader_versions()
        if loaders:
            fabric_loader_versions = loaders
        forge_loaders = fetch_forge_versions()
        if forge_loaders:
            forge_loader_versions = forge_loaders
        neoforge_loaders = fetch_neoforge_versions()
        if neoforge_loaders:
            neoforge_loader_versions = neoforge_loaders
        versions_loaded = True
    except Exception as e:
        print(f"Could not update Minecraft versions: {e}")

def call_first_available(module, function_names, *args):
    for function_name in function_names:
        if not hasattr(module, function_name):
            continue
        try:
            return getattr(module, function_name)(*args)
        except Exception:
            pass
    return None

def fetch_fabric_loader_versions():
    for function_name in ["get_all_loader_versions", "get_loader_versions"]:
        if not hasattr(minecraft_launcher_lib.fabric, function_name):
            continue
        try:
            loader_data = getattr(minecraft_launcher_lib.fabric, function_name)()
            versions = parse_loader_versions(loader_data)
            if versions:
                return versions
        except Exception:
            pass

    try:
        with urlopen("https://meta.fabricmc.net/v2/versions/loader", timeout=8) as response:
            loader_data = json.loads(response.read().decode("utf-8"))
        return parse_loader_versions(loader_data)
    except Exception as e:
        print(f"Could not update Fabric loader versions: {e}")
        return []

def parse_loader_versions(loader_data):
    versions = []
    for loader in loader_data:
        version = ""
        if isinstance(loader, str):
            version = loader
        elif isinstance(loader, dict):
            if isinstance(loader.get("loader"), dict):
                version = loader["loader"].get("version", "")
            if version == "":
                version = loader.get("version", loader.get("id", ""))

        if version and version not in versions:
            versions.append(version)
    return versions

def fetch_forge_versions():
    forge = getattr(minecraft_launcher_lib, "forge", None)
    if forge is not None:
        loader_data = call_first_available(forge, ["list_forge_versions", "get_forge_version_list", "get_all_versions"])
        versions = parse_loader_versions(loader_data or [])
        if versions:
            return versions

    try:
        with urlopen("https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml", timeout=8) as response:
            loader_data = response.read().decode("utf-8")
        return parse_maven_xml_versions(loader_data)
    except Exception as e:
        print(f"Could not update Forge versions: {e}")
        return []

def fetch_neoforge_versions():
    neoforge = getattr(minecraft_launcher_lib, "neoforge", None)
    if neoforge is not None:
        loader_data = call_first_available(neoforge, ["list_neoforge_versions", "get_neoforge_versions", "get_neoforge_version_list", "get_all_versions"])
        versions = parse_loader_versions(loader_data or [])
        if versions:
            return versions

    try:
        with urlopen("https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml", timeout=8) as response:
            loader_data = response.read().decode("utf-8")
        return parse_maven_xml_versions(loader_data)
    except Exception as e:
        print(f"Could not update NeoForge versions: {e}")
        return []

def parse_maven_xml_versions(loader_data):
    versions = []
    start = 0
    while True:
        open_tag = loader_data.find("<version>", start)
        close_tag = loader_data.find("</version>", open_tag)
        if open_tag == -1 or close_tag == -1:
            break
        version = loader_data[open_tag + len("<version>"):close_tag].strip()
        if version and version not in versions:
            versions.append(version)
        start = close_tag + len("</version>")
    versions.reverse()
    return versions

def refresh_versions_async():
    threading.Thread(target=refresh_versions, daemon=True).start()

def get_versions(show_all=False):
    if show_all:
        return all_versions
    return release_versions

def get_fabric_loader_versions():
    return fabric_loader_versions

def get_loader_versions(loader, minecraft_version=""):
    if loader == "Fabric":
        return fabric_loader_versions
    if loader == "Forge":
        return filter_mod_loader_versions(forge_loader_versions, minecraft_version)
    if loader == "NeoForge":
        return filter_mod_loader_versions(neoforge_loader_versions, minecraft_version)
    return []

def filter_mod_loader_versions(versions, minecraft_version):
    if not minecraft_version:
        return versions
    filtered = []
    for version in versions:
        if version == minecraft_version or version.startswith(minecraft_version + "-"):
            filtered.append(version)
    return filtered if filtered else versions

def run_game(username="NAME", uuid="UUID", access_token="NONE", version="26.1", modded=["Vanilla", ""], instance_directory="", ram=4096, demo=False, instance_name=""):
    if instance_name == "":
        instance_name = instance_directory or version
    if is_game_running(instance_name):
        log(f"{instance_name} is already running.")
        return False
    instance_directory = ensure_game_directory(instance_directory)
    log(f"Starting {modded[0]} {version}.")
    log(f"Working directory: {instance_directory}")
    log(f"RAM: {int(ram)} MB")
    if demo:
        log("Demo mode enabled.")
    if modded[0] == "Vanilla":
        threading.Thread(target=lambda: _run_game_vanilla(username, uuid, access_token, version, instance_directory, ram, demo, instance_name), daemon=True).start()
    elif modded[0] == "Fabric":
        threading.Thread(target=lambda: _run_game_fabric(username, uuid, access_token, version, modded[1], instance_directory, ram, demo, instance_name), daemon=True).start()
    elif modded[0] == "Forge":
        threading.Thread(target=lambda: _run_game_forge(username, uuid, access_token, version, modded[1], instance_directory, ram, demo, instance_name), daemon=True).start()
    elif modded[0] == "NeoForge":
        threading.Thread(target=lambda: _run_game_neoforge(username, uuid, access_token, version, modded[1], instance_directory, ram, demo, instance_name), daemon=True).start()
    return True

# Vanilla Instance
def _run_game_vanilla(username, uuid, access_token, version, instance_directory, ram, demo, instance_name):
    options = get_launch_options(username, uuid, access_token, instance_directory, ram, demo)

    found = 0
    for i in minecraft_launcher_lib.utils.get_installed_versions(minecraft_directory):
        if i["id"] == version:
            found = 1
            launch_command = minecraft_launcher_lib.command.get_minecraft_command(
                version,
                minecraft_directory,
                options
            )
            start_process(launch_command, instance_directory, instance_name)
    if found == 0:
        log(f"Installing Minecraft {version}.")
        minecraft_launcher_lib.install.install_minecraft_version(version, minecraft_directory)
        launch_command = minecraft_launcher_lib.command.get_minecraft_command(
            version,
            minecraft_directory,
            options
        )
        start_process(launch_command, instance_directory, instance_name)

# Fabric Instance
def _run_game_fabric(username, uuid, access_token, version, loader, instance_directory, ram, demo, instance_name):
    try:
        _run_game_fabric_inner(username, uuid, access_token, version, loader, instance_directory, ram, demo, instance_name)
    except Exception as e:
        log(f"[LAUNCHER] ERROR: Fabric-Start fehlgeschlagen für {version}: {e}")
        notify_state_changed(instance_name)

def _run_game_fabric_inner(username, uuid, access_token, version, loader, instance_directory, ram, demo, instance_name):
    options = get_launch_options(username, uuid, access_token, instance_directory, ram, demo)

    # Auto-select latest stable Fabric loader if none specified
    if not loader:
        loaders = fetch_fabric_loader_versions()
        loader = loaders[0] if loaders else "0.16.10"
        log(f"Fabric loader auto-selected: {loader}")

    try:
        supported = minecraft_launcher_lib.fabric.is_minecraft_version_supported(version)
        if not supported:
            log(f"[LAUNCHER] Warnung: Fabric-Versionscheck für {version} negativ, fahre trotzdem fort.")
    except Exception:
        pass  # check-Fehler ignorieren, weitermachen

    try:
        minecraft_launcher_lib.fabric.install_fabric(
            minecraft_version=version,
            minecraft_directory=minecraft_directory,
            loader_version=loader
        )
    except Exception as e:
        log(f"Fabric install warning: {e}")

    installed_versions = minecraft_launcher_lib.utils.get_installed_versions(minecraft_directory)

    fabric_version_id = None
    for v in installed_versions:
        vid = v.get("id", "")
        if vid == f"fabric-loader-{loader}-{version}":
            fabric_version_id = vid
            break

    if fabric_version_id is None:
        raise RuntimeError(f"Fabric-Version fabric-loader-{loader}-{version} nicht gefunden")

    launch_command = minecraft_launcher_lib.command.get_minecraft_command(
        fabric_version_id,
        minecraft_directory,
        options
    )

    start_process(launch_command, instance_directory, instance_name)

# Forge Instance
def _run_game_forge(username, uuid, access_token, version, loader, instance_directory, ram, demo, instance_name):
    try:
        _run_game_forge_inner(username, uuid, access_token, version, loader, instance_directory, ram, demo, instance_name)
    except Exception as e:
        log(f"[LAUNCHER] ERROR: Forge-Start fehlgeschlagen für {version}: {e}")
        notify_state_changed(instance_name)

def _run_game_forge_inner(username, uuid, access_token, version, loader, instance_directory, ram, demo, instance_name):
    forge = getattr(minecraft_launcher_lib, "forge", None)
    if forge is None:
        log("Forge is not supported by this minecraft_launcher_lib installation.")
        return

    forge_version = loader
    if forge_version == "":
        forge_version = call_first_available(forge, ["find_forge_version"], version) or ""
    if forge_version == "":
        log(f"No Forge loader version found for Minecraft {version}.")
        return

    log(f"Installing Forge {forge_version}.")
    installed = False
    for function_name in ["install_forge_version", "install_forge"]:
        if not hasattr(forge, function_name):
            continue
        try:
            getattr(forge, function_name)(forge_version, minecraft_directory)
            installed = True
            break
        except TypeError:
            try:
                getattr(forge, function_name)(minecraft_version=version, minecraft_directory=minecraft_directory, forge_version=forge_version)
                installed = True
                break
            except Exception:
                pass
        except Exception as e:
            log(f"Forge install failed: {e}")

    if not installed:
        log("No compatible Forge install function was found.")
        return

    version_id = find_installed_loader_version(["forge"], forge_version, version)
    if version_id == "":
        version_id = forge_version
    start_installed_version(username, uuid, access_token, version_id, instance_directory, ram, demo, instance_name)

# NeoForge Instance
def _run_game_neoforge(username, uuid, access_token, version, loader, instance_directory, ram, demo, instance_name):
    try:
        _run_game_neoforge_inner(username, uuid, access_token, version, loader, instance_directory, ram, demo, instance_name)
    except Exception as e:
        log(f"[LAUNCHER] ERROR: NeoForge-Start fehlgeschlagen für {version}: {e}")
        notify_state_changed(instance_name)

def _run_game_neoforge_inner(username, uuid, access_token, version, loader, instance_directory, ram, demo, instance_name):
    neoforge = getattr(minecraft_launcher_lib, "neoforge", None)
    if neoforge is None:
        log("NeoForge is not supported by this minecraft_launcher_lib installation.")
        return

    neoforge_version = loader
    if neoforge_version == "":
        log(f"No NeoForge loader version selected for Minecraft {version}.")
        return

    log(f"Installing NeoForge {neoforge_version}.")
    installed = False
    for function_name in ["install_neoforge_version", "install_neoforge"]:
        if not hasattr(neoforge, function_name):
            continue
        try:
            getattr(neoforge, function_name)(neoforge_version, minecraft_directory)
            installed = True
            break
        except TypeError:
            try:
                getattr(neoforge, function_name)(minecraft_version=version, minecraft_directory=minecraft_directory, neoforge_version=neoforge_version)
                installed = True
                break
            except Exception:
                pass
        except Exception as e:
            log(f"NeoForge install failed: {e}")

    if not installed:
        log("No compatible NeoForge install function was found.")
        return

    version_id = find_installed_loader_version(["neoforge", "neo-forge"], neoforge_version, version)
    if version_id == "":
        version_id = neoforge_version
    start_installed_version(username, uuid, access_token, version_id, instance_directory, ram, demo, instance_name)

def find_installed_loader_version(keywords, loader_version, minecraft_version):
    installed_versions = minecraft_launcher_lib.utils.get_installed_versions(minecraft_directory)
    for installed in installed_versions:
        version_id = installed.get("id", "")
        version_id_lower = version_id.lower()
        if loader_version in version_id and any(keyword in version_id_lower for keyword in keywords):
            return version_id
    for installed in installed_versions:
        version_id = installed.get("id", "")
        version_id_lower = version_id.lower()
        if minecraft_version in version_id and any(keyword in version_id_lower for keyword in keywords):
            return version_id
    return ""

def start_installed_version(username, uuid, access_token, version_id, instance_directory, ram, demo, instance_name):
    options = get_launch_options(username, uuid, access_token, instance_directory, ram, demo)
    launch_command = minecraft_launcher_lib.command.get_minecraft_command(
        version_id,
        minecraft_directory,
        options
    )
    start_process(launch_command, instance_directory, instance_name)

def get_full_versions():
    return get_versions(True)


if __name__ == "__main__":
    import sys
    import time

    try:
        raw = sys.stdin.readline().lstrip('﻿').strip()
        if not raw:
            print("[LAUNCHER] ERROR: Keine Parameter erhalten", flush=True)
            sys.exit(1)
        params = json.loads(raw)
    except Exception as e:
        print(f"[LAUNCHER] ERROR: Parameter-Fehler: {e}", flush=True)
        sys.exit(1)

    action = params.get("action", "run_game")

    if action == "run_game":
        modded_raw = params.get("modded", ["Vanilla", ""])
        if len(modded_raw) < 2:
            modded_raw = [modded_raw[0], ""] if modded_raw else ["Vanilla", ""]

        inst_name = params.get("instance_name", "")
        version   = params.get("version", "1.21.4")

        # Event: gesetzt wenn Spiel tatsächlich startet oder ein Fehler auftritt
        _game_ready = threading.Event()
        _launch_error = [None]

        original_state_callback = state_callback

        def _on_state(name):
            if name == (inst_name or params.get("instance_directory", "") or version):
                _game_ready.set()
            if original_state_callback:
                original_state_callback(name)

        set_state_callback(_on_state)

        success = run_game(
            username=params.get("username", "Player"),
            uuid=params.get("uuid", ""),
            access_token=params.get("access_token", "0"),
            version=version,
            modded=modded_raw,
            instance_directory=params.get("instance_directory", ""),
            ram=params.get("ram", 4096),
            demo=params.get("demo", False),
            instance_name=inst_name,
        )

        if not success:
            print("[LAUNCHER] ERROR: Spiel läuft bereits oder Start fehlgeschlagen", flush=True)
            sys.exit(1)

        # Resolve actual instance name used inside run_game
        if not inst_name:
            inst_name = params.get("instance_directory", "") or version

        # Warte bis Spiel tatsächlich läuft (Download kann Minuten dauern) oder Fehler
        print("[LAUNCHER] Warte auf Spielstart (ggf. Download)...", flush=True)
        _game_ready.wait(timeout=600)  # max 10 Minuten für Download + Start

        # Block until Minecraft exits so the Rust process can detect game_stopped
        while is_game_running(inst_name):
            time.sleep(0.5)

        print("[LAUNCHER] Spiel beendet.", flush=True)
