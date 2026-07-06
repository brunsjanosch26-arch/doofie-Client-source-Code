import { Icon } from "@iconify/react";
import { useSessionStore } from "../../store/session-store";
import { useAchievementStore, ALL_ACHIEVEMENTS } from "../../store/achievement-store";
import { useThemeStore } from "../../store/useThemeStore";
import { useRadioStore } from "../../store/radio-store";

function formatDuration(seconds: number): string {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  if (h > 0) return `${h}h ${m}m`;
  if (m > 0) return `${m}m`;
  return `${seconds}s`;
}

function MiniBarChart({ data, color }: { data: number[]; color: string }) {
  const max = Math.max(...data, 1);
  return (
    <div style={{ display: "flex", alignItems: "flex-end", gap: "4px", height: "60px" }}>
      {data.map((v, i) => (
        <div key={i} style={{
          flex: 1, borderRadius: "4px 4px 0 0",
          background: `linear-gradient(to top, ${color}80, ${color})`,
          height: `${(v / max) * 100}%`,
          minHeight: v > 0 ? "4px" : "1px",
          opacity: i === data.length - 1 ? 1 : 0.5 + (i / data.length) * 0.5,
        }} />
      ))}
    </div>
  );
}

export function DashboardTab() {
  const { sessions, getTotalPlaytime, getRecentSessions, getProfileStats } = useSessionStore();
  const { unlocked, stats } = useAchievementStore();
  const { accentColor } = useThemeStore();
  const { totalListened } = useRadioStore();

  const totalPlaytime = getTotalPlaytime();
  const recentSessions = getRecentSessions(10);
  const profileStats = getProfileStats();
  const topProfiles = Object.entries(profileStats).sort((a, b) => b[1].total - a[1].total).slice(0, 5);

  // Last 7 days bar chart data
  const last7Days = Array.from({ length: 7 }, (_, i) => {
    const day = new Date();
    day.setDate(day.getDate() - (6 - i));
    const start = new Date(day); start.setHours(0, 0, 0, 0);
    const end = new Date(day); end.setHours(23, 59, 59, 999);
    return sessions
      .filter(s => s.startTime >= start.getTime() && s.startTime <= end.getTime())
      .reduce((sum, s) => sum + s.durationSeconds, 0) / 3600;
  });

  const totalPoints = ALL_ACHIEVEMENTS.filter(a => unlocked.includes(a.id)).reduce((s, a) => s + a.points, 0);

  const statCards = [
    { label: "Gesamtspielzeit", value: formatDuration(totalPlaytime), icon: "solar:clock-circle-bold", color: accentColor.value },
    { label: "Sessions", value: sessions.length, icon: "solar:gamepad-bold", color: "#10b981" },
    { label: "Achievements", value: `${unlocked.length}/${ALL_ACHIEVEMENTS.length}`, icon: "solar:cup-star-bold", color: "#f59e0b" },
    { label: "Spielzeit gehört", value: formatDuration(totalListened), icon: "solar:music-note-bold", color: "#8b5cf6" },
  ];

  return (
    <div style={{ height: "100%", overflowY: "auto", padding: "24px", display: "flex", flexDirection: "column", gap: "20px" }}>
      {/* Header */}
      <div>
        <h1 style={{ margin: 0, fontSize: "22px", fontWeight: 800, color: "#fff", display: "flex", alignItems: "center", gap: "8px" }}>
          <Icon icon="solar:chart-2-bold" style={{ color: accentColor.value }} />
          Dashboard
        </h1>
        <p style={{ margin: "4px 0 0", fontSize: "12px", color: "rgba(255,255,255,0.4)" }}>Deine Spielstatistiken auf einen Blick</p>
      </div>

      {/* Stat cards */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: "12px" }}>
        {statCards.map(c => (
          <div key={c.label} style={{
            padding: "16px", borderRadius: "14px",
            background: `linear-gradient(135deg, ${c.color}18, ${c.color}0a)`,
            border: `1px solid ${c.color}35`,
          }}>
            <div style={{ marginBottom: "8px" }}>
              <Icon icon={c.icon} style={{ width: "24px", height: "24px", color: c.color }} />
            </div>
            <div style={{ fontSize: "22px", fontWeight: 900, color: "#fff" }}>{c.value}</div>
            <div style={{ fontSize: "11px", color: "rgba(255,255,255,0.4)", marginTop: "2px" }}>{c.label}</div>
          </div>
        ))}
      </div>

      {/* Weekly chart */}
      <div style={{ background: "rgba(255,255,255,0.04)", border: "1px solid rgba(255,255,255,0.08)", borderRadius: "14px", padding: "20px" }}>
        <div style={{ fontSize: "14px", fontWeight: 700, color: "rgba(255,255,255,0.7)", marginBottom: "16px" }}>Letzte 7 Tage (Stunden)</div>
        <MiniBarChart data={last7Days} color={accentColor.value} />
        <div style={{ display: "flex", justifyContent: "space-between", marginTop: "8px" }}>
          {["Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"].map((d, i) => {
            const offset = 6 - i;
            const day = new Date();
            day.setDate(day.getDate() - offset);
            return <div key={d} style={{ fontSize: "10px", color: "rgba(255,255,255,0.3)", flex: 1, textAlign: "center" }}>{["So","Mo","Di","Mi","Do","Fr","Sa"][day.getDay()]}</div>;
          })}
        </div>
      </div>

      {/* Top profiles */}
      {topProfiles.length > 0 && (
        <div style={{ background: "rgba(255,255,255,0.04)", border: "1px solid rgba(255,255,255,0.08)", borderRadius: "14px", padding: "20px" }}>
          <div style={{ fontSize: "14px", fontWeight: 700, color: "rgba(255,255,255,0.7)", marginBottom: "14px", display: "flex", alignItems: "center", gap: "8px" }}>
            <Icon icon="solar:gamepad-bold" style={{ color: accentColor.value, width: "16px", height: "16px" }} />
            Meistgespielt
          </div>
          {topProfiles.map(([id, p], i) => (
            <div key={id} style={{ display: "flex", alignItems: "center", gap: "12px", marginBottom: "10px" }}>
              <div style={{ width: "28px", height: "28px", borderRadius: "8px", background: `${accentColor.value}30`, display: "flex", alignItems: "center", justifyContent: "center", fontSize: "13px", fontWeight: 800, color: accentColor.light }}>
                {i + 1}
              </div>
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: "13px", fontWeight: 600, color: "#fff" }}>{p.name}</div>
                <div style={{ height: "4px", background: "rgba(255,255,255,0.08)", borderRadius: "2px", marginTop: "4px", overflow: "hidden" }}>
                  <div style={{ height: "100%", background: accentColor.value, borderRadius: "2px", width: `${(p.total / (topProfiles[0]?.[1].total || 1)) * 100}%` }} />
                </div>
              </div>
              <div style={{ fontSize: "12px", color: "rgba(255,255,255,0.5)", flexShrink: 0 }}>{formatDuration(p.total)}</div>
            </div>
          ))}
        </div>
      )}

      {/* Recent sessions */}
      {recentSessions.length > 0 && (
        <div style={{ background: "rgba(255,255,255,0.04)", border: "1px solid rgba(255,255,255,0.08)", borderRadius: "14px", padding: "20px" }}>
          <div style={{ fontSize: "14px", fontWeight: 700, color: "rgba(255,255,255,0.7)", marginBottom: "14px", display: "flex", alignItems: "center", gap: "8px" }}>
            <Icon icon="solar:history-bold" style={{ color: accentColor.value, width: "16px", height: "16px" }} />
            Letzte Sessions
          </div>
          {recentSessions.map(s => (
            <div key={s.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "8px 0", borderBottom: "1px solid rgba(255,255,255,0.05)" }}>
              <div>
                <div style={{ fontSize: "13px", fontWeight: 600, color: "#fff" }}>{s.profileName}</div>
                <div style={{ fontSize: "11px", color: "rgba(255,255,255,0.35)" }}>{new Date(s.startTime).toLocaleDateString("de-DE", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" })}</div>
              </div>
              <div style={{ fontSize: "13px", fontWeight: 700, color: accentColor.light }}>{formatDuration(s.durationSeconds)}</div>
            </div>
          ))}
        </div>
      )}

      {recentSessions.length === 0 && (
        <div style={{ textAlign: "center", padding: "40px", color: "rgba(255,255,255,0.3)" }}>
          <div style={{ marginBottom: "12px" }}>
          <Icon icon="solar:chart-2-bold" style={{ width: "48px", height: "48px", color: "rgba(255,255,255,0.2)" }} />
        </div>
          <div>Noch keine Sessions. Starte Minecraft um Statistiken zu sammeln!</div>
        </div>
      )}
    </div>
  );
}
