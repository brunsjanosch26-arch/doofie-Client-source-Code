import { useEffect, useState } from "react";
import { Icon } from "@iconify/react";
import { invoke } from "@tauri-apps/api/core";
import { useThemeStore } from "../../store/useThemeStore";
import { useAchievementStore } from "../../store/achievement-store";

interface ScreenshotFile {
  name: string;
  path: string;
  dataUrl?: string;
  size: number;
  modified: number;
}

export function ScreenshotGallery() {
  const { accentColor } = useThemeStore();
  const { updateStats, stats } = useAchievementStore();
  const [screenshots, setScreenshots] = useState<ScreenshotFile[]>([]);
  const [selected, setSelected] = useState<ScreenshotFile | null>(null);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState("");

  useEffect(() => {
    loadScreenshots();
  }, []);

  const loadScreenshots = async () => {
    setLoading(true);
    try {
      const dir = await invoke<string>("get_launcher_directory").catch(() => null);
      const files = await invoke<ScreenshotFile[]>("list_screenshots", { directory: dir }).catch(() => null);
      if (files && files.length > 0) {
        setScreenshots(files);
        updateStats({ screenshotsTaken: files.length });
      } else {
        // Mock screenshots for browser preview
        setScreenshots(getMockScreenshots());
      }
    } catch {
      setScreenshots(getMockScreenshots());
    }
    setLoading(false);
  };

  const getMockScreenshots = (): ScreenshotFile[] =>
    Array.from({ length: 9 }, (_, i) => ({
      name: `2024-12-${String(i + 1).padStart(2, "0")}_screenshot.png`,
      path: `/screenshots/${189 + i}.png`,
      dataUrl: `/screenshots/${189 + i}.png`,
      size: 1200000 + i * 100000,
      modified: Date.now() - i * 86400000,
    }));

  const filtered = screenshots.filter(s => s.name.toLowerCase().includes(searchTerm.toLowerCase()));

  const formatSize = (bytes: number) => `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  const formatDate = (ts: number) => new Date(ts).toLocaleDateString("de-DE");

  return (
    <div style={{ height: "100%", display: "flex", flexDirection: "column", overflow: "hidden" }}>
      {/* Header */}
      <div style={{ padding: "20px 24px 16px", borderBottom: "1px solid rgba(255,255,255,0.06)", flexShrink: 0 }}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "12px" }}>
          <div>
            <h1 style={{ margin: 0, fontSize: "22px", fontWeight: 800, color: "#fff", display: "flex", alignItems: "center", gap: "8px" }}><Icon icon="solar:camera-bold" style={{ width: "22px", height: "22px" }} /> Screenshots</h1>
            <p style={{ margin: "4px 0 0", fontSize: "12px", color: "rgba(255,255,255,0.4)" }}>
              {screenshots.length} Screenshot{screenshots.length !== 1 ? "s" : ""} gefunden
            </p>
          </div>
          <button onClick={loadScreenshots} style={{
            padding: "8px 16px", borderRadius: "10px", border: `1px solid ${accentColor.value}40`,
            background: `${accentColor.value}15`, color: accentColor.light, cursor: "pointer", fontSize: "13px", fontWeight: 600,
          }}>
            <span style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}><Icon icon="solar:refresh-bold" style={{ width: "14px", height: "14px" }} /> Aktualisieren</span>
          </button>
        </div>
        <input
          value={searchTerm} onChange={e => setSearchTerm(e.target.value)}
          placeholder="Screenshots suchen..."
          style={{
            width: "100%", padding: "10px 14px", borderRadius: "10px",
            background: "rgba(255,255,255,0.05)", border: "1px solid rgba(255,255,255,0.1)",
            color: "#fff", fontSize: "13px", outline: "none", boxSizing: "border-box",
          }}
        />
      </div>

      {/* Grid */}
      <div style={{ flex: 1, overflowY: "auto", padding: "16px 24px" }}>
        {loading ? (
          <div style={{ display: "flex", alignItems: "center", justifyContent: "center", height: "200px", color: "rgba(255,255,255,0.3)", fontSize: "14px" }}>
            Lade Screenshots...
          </div>
        ) : filtered.length === 0 ? (
          <div style={{ textAlign: "center", padding: "60px 20px", color: "rgba(255,255,255,0.3)" }}>
            <div style={{ marginBottom: "12px", display: "flex", justifyContent: "center" }}><Icon icon="solar:camera-minimalistic-bold" style={{ width: "48px", height: "48px", opacity: 0.4 }} /></div>
            <div style={{ fontSize: "16px", fontWeight: 600 }}>Keine Screenshots gefunden</div>
            <div style={{ fontSize: "13px", marginTop: "8px" }}>Drücke F2 in Minecraft um Screenshots zu machen</div>
          </div>
        ) : (
          <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: "12px" }}>
            {filtered.map((s, i) => (
              <div
                key={i}
                onClick={() => setSelected(s)}
                style={{
                  borderRadius: "12px", overflow: "hidden", cursor: "pointer",
                  border: "2px solid rgba(255,255,255,0.08)",
                  transition: "all 0.2s", aspectRatio: "16/9",
                  position: "relative", background: "rgba(0,0,0,0.3)",
                }}
                onMouseEnter={e => {
                  (e.currentTarget as HTMLDivElement).style.borderColor = `${accentColor.value}60`;
                  (e.currentTarget as HTMLDivElement).style.transform = "scale(1.02)";
                }}
                onMouseLeave={e => {
                  (e.currentTarget as HTMLDivElement).style.borderColor = "rgba(255,255,255,0.08)";
                  (e.currentTarget as HTMLDivElement).style.transform = "scale(1)";
                }}
              >
                <img
                  src={s.dataUrl ?? s.path}
                  alt={s.name}
                  style={{ width: "100%", height: "100%", objectFit: "cover" }}
                  onError={e => { (e.target as HTMLImageElement).style.display = "none"; }}
                />
                <div style={{
                  position: "absolute", bottom: 0, left: 0, right: 0,
                  padding: "8px", background: "linear-gradient(transparent, rgba(0,0,0,0.8))",
                  fontSize: "10px", color: "rgba(255,255,255,0.6)",
                }}>
                  {formatDate(s.modified)}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Lightbox */}
      {selected && (
        <div
          onClick={() => setSelected(null)}
          style={{
            position: "fixed", inset: 0, zIndex: 99999,
            background: "rgba(0,0,0,0.95)", display: "flex", flexDirection: "column",
            alignItems: "center", justifyContent: "center", padding: "24px",
          }}
        >
          <div onClick={e => e.stopPropagation()} style={{ maxWidth: "90vw", maxHeight: "80vh", position: "relative" }}>
            <img src={selected.dataUrl ?? selected.path} alt={selected.name}
              style={{ maxWidth: "100%", maxHeight: "75vh", borderRadius: "12px", objectFit: "contain", display: "block" }} />
            <div style={{
              position: "absolute", top: "12px", right: "12px",
              background: "rgba(0,0,0,0.7)", borderRadius: "8px", padding: "4px 10px",
              fontSize: "12px", color: "rgba(255,255,255,0.6)",
            }}>
              {formatSize(selected.size)}
            </div>
          </div>
          <div style={{ marginTop: "16px", display: "flex", gap: "12px" }}>
            <span style={{ fontSize: "13px", color: "rgba(255,255,255,0.5)" }}>{selected.name}</span>
            <button onClick={() => setSelected(null)} style={{
              padding: "6px 16px", borderRadius: "8px", border: "none",
              background: "rgba(255,255,255,0.1)", color: "#fff", cursor: "pointer",
            }}>Schließen</button>
          </div>
        </div>
      )}
    </div>
  );
}
