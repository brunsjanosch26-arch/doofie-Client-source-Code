import { Icon } from "@iconify/react";
import { ALL_ACHIEVEMENTS, useAchievementStore } from "../../store/achievement-store";
import { useThemeStore } from "../../store/useThemeStore";
import { cn } from "../../lib/utils";

const CATEGORY_ICONS: Record<string, string> = {
  launcher: "solar:rocket-bold",
  collection: "solar:box-bold",
  social: "solar:global-bold",
  minecraft: "solar:pick-bold",
  secret: "solar:eye-bold",
};

const CATEGORY_LABELS: Record<string, string> = {
  launcher: "Launcher",
  collection: "Sammlung",
  social: "Social",
  minecraft: "Minecraft",
  secret: "Geheim",
};

export function AchievementsTab() {
  const { unlocked, stats } = useAchievementStore();
  const { accentColor } = useThemeStore();
  const totalPoints = ALL_ACHIEVEMENTS.filter(a => unlocked.includes(a.id)).reduce((s, a) => s + a.points, 0);
  const maxPoints = ALL_ACHIEVEMENTS.reduce((s, a) => s + a.points, 0);
  const categories = ["launcher", "collection", "social", "minecraft", "secret"] as const;

  return (
    <div style={{ padding: "24px", height: "100%", overflowY: "auto", display: "flex", flexDirection: "column", gap: "24px" }}>
      {/* Header */}
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
        <div>
          <h1 style={{ fontSize: "24px", fontWeight: 800, color: "#fff", margin: 0 }}>Achievements</h1>
          <p style={{ color: "rgba(255,255,255,0.4)", margin: "4px 0 0", fontSize: "13px" }}>
            {unlocked.length}/{ALL_ACHIEVEMENTS.length} freigeschaltet
          </p>
        </div>
        <div style={{
          textAlign: "right",
          background: `linear-gradient(135deg, ${accentColor.dark}33, ${accentColor.value}22)`,
          border: `1px solid ${accentColor.value}40`,
          borderRadius: "12px", padding: "12px 20px",
        }}>
          <div style={{ fontSize: "26px", fontWeight: 900, color: accentColor.light }}>{totalPoints}</div>
          <div style={{ fontSize: "11px", color: "rgba(255,255,255,0.4)" }}>von {maxPoints} Punkten</div>
        </div>
      </div>

      {/* Progress bar */}
      <div style={{ background: "rgba(255,255,255,0.06)", borderRadius: "8px", height: "8px", overflow: "hidden" }}>
        <div style={{
          height: "100%", borderRadius: "8px",
          background: `linear-gradient(90deg, ${accentColor.dark}, ${accentColor.value}, ${accentColor.light})`,
          width: `${(unlocked.length / ALL_ACHIEVEMENTS.length) * 100}%`,
          transition: "width 0.8s ease",
          boxShadow: `0 0 12px ${accentColor.value}80`,
        }} />
      </div>

      {/* Stats row */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: "12px" }}>
        {[
          { label: "Gesamtspielzeit", value: `${Math.floor(stats.totalPlaytime / 3600)}h`, icon: "solar:clock-circle-bold" },
          { label: "Profile", value: stats.profilesCreated, icon: "solar:folder-bold" },
          { label: "Mods", value: stats.modsInstalled, icon: "solar:settings-minimalistic-bold" },
          { label: "Starts", value: stats.launchCount, icon: "solar:rocket-bold" },
        ].map(s => (
          <div key={s.label} style={{
            background: "rgba(255,255,255,0.04)", border: "1px solid rgba(255,255,255,0.08)",
            borderRadius: "12px", padding: "12px", textAlign: "center",
          }}>
            <div style={{ marginBottom: "4px", display: "flex", justifyContent: "center" }}>
              <Icon icon={s.icon} style={{ width: "22px", height: "22px", color: accentColor.value }} />
            </div>
            <div style={{ fontSize: "20px", fontWeight: 800, color: "#fff" }}>{s.value}</div>
            <div style={{ fontSize: "11px", color: "rgba(255,255,255,0.35)" }}>{s.label}</div>
          </div>
        ))}
      </div>

      {/* Achievement categories */}
      {categories.map(cat => {
        const items = ALL_ACHIEVEMENTS.filter(a => a.category === cat);
        return (
          <div key={cat}>
            <h2 style={{ fontSize: "14px", fontWeight: 700, color: "rgba(255,255,255,0.5)", margin: "0 0 12px", textTransform: "uppercase", letterSpacing: "1.5px", display: "flex", alignItems: "center", gap: "8px" }}>
              <Icon icon={CATEGORY_ICONS[cat]} style={{ width: "14px", height: "14px", color: accentColor.value }} />
              {CATEGORY_LABELS[cat]}
            </h2>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(2, 1fr)", gap: "8px" }}>
              {items.map(a => {
                const isUnlocked = unlocked.includes(a.id);
                const isHidden = a.hidden && !isUnlocked;
                return (
                  <div
                    key={a.id}
                    style={{
                      display: "flex", alignItems: "center", gap: "12px",
                      padding: "12px 14px", borderRadius: "12px",
                      background: isUnlocked
                        ? `linear-gradient(135deg, ${accentColor.dark}28, ${accentColor.value}15)`
                        : "rgba(255,255,255,0.03)",
                      border: isUnlocked
                        ? `1px solid ${accentColor.value}40`
                        : "1px solid rgba(255,255,255,0.06)",
                      transition: "all 0.3s",
                      opacity: isHidden ? 0.4 : 1,
                    }}
                  >
                    <div style={{
                      width: "40px", height: "40px", borderRadius: "10px", flexShrink: 0,
                      display: "flex", alignItems: "center", justifyContent: "center",
                      background: isUnlocked ? `linear-gradient(135deg, ${accentColor.dark}, ${accentColor.value})` : "rgba(255,255,255,0.06)",
                      boxShadow: isUnlocked ? `0 0 15px ${accentColor.value}50` : undefined,
                      filter: isHidden ? "grayscale(1)" : undefined,
                    }}>
                      <Icon
                        icon={isHidden ? "solar:question-circle-bold" : a.icon}
                        style={{ width: "20px", height: "20px", color: isUnlocked ? "#fff" : "rgba(255,255,255,0.4)" }}
                      />
                    </div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontSize: "13px", fontWeight: 700, color: isUnlocked ? "#fff" : "rgba(255,255,255,0.5)", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                        {isHidden ? "???" : a.name}
                      </div>
                      <div style={{ fontSize: "11px", color: "rgba(255,255,255,0.35)", marginTop: "1px" }}>
                        {isHidden ? "Noch nicht entdeckt" : a.description}
                      </div>
                    </div>
                    <div style={{ fontSize: "11px", fontWeight: 700, color: isUnlocked ? accentColor.light : "rgba(255,255,255,0.2)", flexShrink: 0 }}>
                      {a.points}p
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        );
      })}
    </div>
  );
}
