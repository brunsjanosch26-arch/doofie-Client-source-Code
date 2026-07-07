"use client";
import { useEffect, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { CustomImageBackground } from "./CustomImageBackground";
import { MinecraftBackground } from "./MinecraftBackground";

interface ScreenshotFile {
  name: string;
  path: string;
  dataUrl?: string;
  modified?: number;
}

/**
 * Live-Wallpaper: Der neueste Screenshot aus der eigenen Welt
 * wird als Launcher-Hintergrund verwendet.
 */
export function LatestScreenshotBackground() {
  const [path, setPath] = useState<string | null>(null);
  const [dataUrl, setDataUrl] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const load = async () => {
      try {
        const dir = await invoke<string>("get_launcher_directory").catch(() => null);
        const files = await invoke<ScreenshotFile[]>("list_screenshots", { directory: dir }).catch(() => null);
        if (files && files.length > 0) {
          const latest = [...files].sort((a, b) => (b.modified ?? 0) - (a.modified ?? 0))[0];
          if (latest.dataUrl?.startsWith("data:")) setDataUrl(latest.dataUrl);
          else setPath(latest.path);
          return;
        }
      } catch {
        // ignorieren, Fallback unten
      }
      setFailed(true);
    };
    load();
  }, []);

  if (failed) return <MinecraftBackground />;
  if (dataUrl) {
    return (
      <div className="absolute inset-0" style={{ pointerEvents: "none", zIndex: 0 }}>
        <div className="absolute inset-0" style={{ backgroundImage: `url('${dataUrl}')`, backgroundSize: "cover", backgroundPosition: "center" }} />
        <div className="absolute inset-0" style={{ background: "linear-gradient(to bottom, rgba(0,0,0,0.55) 0%, rgba(0,0,0,0.35) 50%, rgba(0,0,0,0.6) 100%)" }} />
      </div>
    );
  }
  if (path) return <CustomImageBackground imagePath={path} />;
  return null;
}
