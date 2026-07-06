import { useThemeStore } from "../../store/useThemeStore";
import { Icon } from "@iconify/react";

export function PerformanceTab() {
  const { accentColor } = useThemeStore();

  return (
    <div style={{ height: "100%", overflowY: "auto", padding: "24px", display: "flex", flexDirection: "column", gap: "20px" }}>
      <div>
        <h1 style={{ margin: 0, fontSize: "22px", fontWeight: 800, color: "#fff", display: "flex", alignItems: "center", gap: "8px" }}>
          <Icon icon="solar:cpu-bolt-bold" style={{ color: accentColor.value }} />
          Performance
        </h1>
        <p style={{ margin: "4px 0 0", fontSize: "12px", color: "rgba(255,255,255,0.4)" }}>JVM-Einstellungen, RAM-Optimierung & Diagnose</p>
      </div>

      <div style={{ background: "rgba(255,255,255,0.04)", border: "1px solid rgba(255,255,255,0.08)", borderRadius: "14px", padding: "24px", display: "flex", flexDirection: "column", alignItems: "center", gap: "16px" }}>
        <Icon icon="solar:cpu-bolt-bold" style={{ width: "48px", height: "48px", color: accentColor.value, opacity: 0.5 }} />
        <div style={{ textAlign: "center" }}>
          <div style={{ fontSize: "16px", fontWeight: 700, color: "#fff" }}>Performance Monitor</div>
          <div style={{ fontSize: "13px", color: "rgba(255,255,255,0.4)", marginTop: "6px" }}>Starte Minecraft um Live-Metriken zu sehen.</div>
        </div>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px" }}>
        {[
          { label: "RAM-Nutzung", value: "—", icon: "solar:server-bold", color: "#10b981" },
          { label: "CPU-Nutzung", value: "—", icon: "solar:cpu-bold", color: "#f59e0b" },
          { label: "FPS-Schätzung", value: "—", icon: "solar:chart-2-bold", color: accentColor.value },
          { label: "GC-Pausen", value: "—", icon: "solar:refresh-bold", color: "#8b5cf6" },
        ].map(c => (
          <div key={c.label} style={{
            padding: "16px", borderRadius: "12px",
            background: `linear-gradient(135deg, ${c.color}12, ${c.color}06)`,
            border: `1px solid ${c.color}25`,
            display: "flex", alignItems: "center", gap: "12px",
          }}>
            <Icon icon={c.icon} style={{ width: "24px", height: "24px", color: c.color, flexShrink: 0 }} />
            <div>
              <div style={{ fontSize: "20px", fontWeight: 900, color: "#fff" }}>{c.value}</div>
              <div style={{ fontSize: "11px", color: "rgba(255,255,255,0.4)" }}>{c.label}</div>
            </div>
          </div>
        ))}
      </div>

      <div style={{ background: "rgba(255,255,255,0.03)", border: "1px solid rgba(255,255,255,0.07)", borderRadius: "14px", padding: "20px" }}>
        <div style={{ fontSize: "14px", fontWeight: 700, color: "rgba(255,255,255,0.7)", marginBottom: "14px", display: "flex", alignItems: "center", gap: "8px" }}>
          <Icon icon="solar:settings-minimalistic-bold" style={{ color: accentColor.value, width: "16px", height: "16px" }} />
          JVM-Optimierung
        </div>
        <p style={{ color: "rgba(255,255,255,0.4)", fontSize: "13px", margin: 0 }}>
          Optimierte JVM-Flags werden automatisch basierend auf deiner RAM-Zuweisung berechnet. Öffne die Profil-Einstellungen um sie anzuwenden.
        </p>
      </div>
    </div>
  );
}
