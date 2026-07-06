import { useState } from "react";
import { Icon } from "@iconify/react";
import { useThemeStore } from "../../store/useThemeStore";
import { useMinecraftAuthStore } from "../../store/minecraft-auth-store";

export function ProfileSync() {
  const { accentColor } = useThemeStore();
  const { activeAccount } = useMinecraftAuthStore();
  const [linked, setLinked] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [lastSync, setLastSync] = useState<Date | null>(null);
  const [email, setEmail] = useState("");

  const handleLink = async () => {
    if (!email.trim()) return;
    setSyncing(true);
    await new Promise(r => setTimeout(r, 1500));
    setLinked(true);
    setSyncing(false);
    setLastSync(new Date());
  };

  const handleSync = async () => {
    setSyncing(true);
    await new Promise(r => setTimeout(r, 2000));
    setSyncing(false);
    setLastSync(new Date());
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
      {/* Status card */}
      <div style={{
        padding: "20px", borderRadius: "14px",
        background: linked ? `linear-gradient(135deg, ${accentColor.value}15, ${accentColor.dark}10)` : "rgba(255,255,255,0.04)",
        border: `1px solid ${linked ? accentColor.value + "40" : "rgba(255,255,255,0.08)"}`,
      }}>
        <div style={{ display: "flex", alignItems: "center", gap: "14px" }}>
          <div style={{
            width: "48px", height: "48px", borderRadius: "12px", flexShrink: 0,
            background: linked ? `linear-gradient(135deg, ${accentColor.dark}, ${accentColor.value})` : "rgba(255,255,255,0.06)",
            display: "flex", alignItems: "center", justifyContent: "center", fontSize: "22px",
            boxShadow: linked ? `0 0 20px ${accentColor.value}40` : undefined,
          }}>
            <Icon icon={linked ? "solar:cloud-check-bold" : "solar:link-bold"} style={{ width: "24px", height: "24px", color: "#fff" }} />
          </div>
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: "15px", fontWeight: 800, color: "#fff" }}>
              {linked ? "Doofie Cloud verbunden" : "Noch nicht verbunden"}
            </div>
            <div style={{ fontSize: "12px", color: "rgba(255,255,255,0.4)", marginTop: "2px" }}>
              {linked
                ? `Verknüpft als ${email} · ${lastSync ? `Zuletzt sync: ${lastSync.toLocaleTimeString()}` : ""}`
                : "Verknüpfe deinen Account für Cloud-Sync"}
            </div>
          </div>
          <div style={{ width: "10px", height: "10px", borderRadius: "50%", background: linked ? "#10b981" : "#666", boxShadow: linked ? "0 0 10px #10b981" : undefined, flexShrink: 0 }} />
        </div>
      </div>

      {/* Link form */}
      {!linked ? (
        <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
          <div style={{ fontSize: "12px", color: "rgba(255,255,255,0.5)" }}>E-Mail-Adresse für Doofie Cloud:</div>
          <input
            value={email} onChange={e => setEmail(e.target.value)}
            placeholder="deine@email.de"
            style={{ padding: "10px 14px", borderRadius: "10px", background: "rgba(255,255,255,0.05)", border: "1px solid rgba(255,255,255,0.1)", color: "#fff", fontSize: "13px", outline: "none" }}
          />
          <button onClick={handleLink} disabled={syncing || !email.trim()} style={{
            padding: "10px", borderRadius: "10px", border: "none", cursor: "pointer",
            background: email.trim() ? `linear-gradient(135deg, ${accentColor.dark}, ${accentColor.value})` : "rgba(255,255,255,0.06)",
            color: "#fff", fontWeight: 700, fontSize: "13px",
            boxShadow: email.trim() ? `0 0 20px ${accentColor.value}40` : undefined,
          }}>
            {syncing ? "Verbinde..." : "Account verknüpfen"}
          </button>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
          {/* Sync options */}
          {[
            { label: "Profile & Einstellungen", icon: "solar:folder-bold", synced: true },
            { label: "Theme-Auswahl", icon: "solar:palette-bold", synced: true },
            { label: "Achievements", icon: "solar:cup-star-bold", synced: true },
            { label: "Screenshot-Galerie", icon: "solar:camera-bold", synced: false },
          ].map(item => (
            <div key={item.label} style={{ display: "flex", alignItems: "center", gap: "12px", padding: "10px 14px", borderRadius: "10px", background: "rgba(255,255,255,0.04)" }}>
              <Icon icon={item.icon} style={{ width: "18px", height: "18px", color: "rgba(255,255,255,0.7)" }} />
              <span style={{ flex: 1, fontSize: "13px", color: "rgba(255,255,255,0.8)" }}>{item.label}</span>
              <div style={{ width: "8px", height: "8px", borderRadius: "50%", background: item.synced ? "#10b981" : "#444" }} />
            </div>
          ))}

          <button onClick={handleSync} disabled={syncing} style={{
            marginTop: "6px", padding: "10px", borderRadius: "10px", border: "none", cursor: "pointer",
            background: `linear-gradient(135deg, ${accentColor.dark}, ${accentColor.value})`,
            color: "#fff", fontWeight: 700, fontSize: "13px",
            animation: syncing ? "spin 1s linear infinite" : undefined,
          }}>
            <span style={{ display: "inline-flex", alignItems: "center", gap: "6px", justifyContent: "center", width: "100%" }}><Icon icon="solar:refresh-bold" style={{ width: "14px", height: "14px" }} />{syncing ? " Synchronisiere..." : " Jetzt synchronisieren"}</span>
          </button>

          <button onClick={() => { setLinked(false); setEmail(""); }} style={{
            padding: "8px", borderRadius: "10px", border: "1px solid rgba(255,255,255,0.1)", background: "transparent", color: "rgba(255,255,255,0.4)", cursor: "pointer", fontSize: "12px",
          }}>Account trennen</button>
        </div>
      )}

      {/* Features */}
      <div style={{ fontSize: "11px", color: "rgba(255,255,255,0.25)", lineHeight: "1.6", padding: "10px", borderRadius: "8px", background: "rgba(255,255,255,0.02)" }}>
        Cloud-Sync speichert Profile, Einstellungen und Themes sicher auf Doofie-Servern.
        Greife von jedem Gerät auf deine Konfiguration zu. (Beta – Daten werden nach 90 Tagen Inaktivität gelöscht.)
      </div>
    </div>
  );
}
