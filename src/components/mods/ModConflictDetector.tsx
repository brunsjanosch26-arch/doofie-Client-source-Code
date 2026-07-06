import { useThemeStore } from "../../store/useThemeStore";
import { Icon } from "@iconify/react";

interface Mod { id: string; name: string; version?: string; }

interface Conflict {
  type: "incompatible" | "duplicate" | "outdated" | "missing-dep";
  severity: "error" | "warning" | "info";
  message: string;
  mods: string[];
  fix?: string;
}

const KNOWN_CONFLICTS: Array<{ ids: string[]; message: string; fix: string }> = [
  { ids: ["optifine", "sodium"], message: "OptiFine & Sodium sind inkompatibel", fix: "Verwende Iris statt OptiFine für Shader mit Sodium" },
  { ids: ["optifine", "iris"], message: "OptiFine & Iris können kollidieren", fix: "Entferne OptiFine wenn du Iris verwendest" },
  { ids: ["lithium", "phosphor"], message: "Lithium enthält bereits Phosphor-Optimierungen", fix: "Phosphor ist in Lithium integriert – entferne Phosphor" },
  { ids: ["fabric-api", "forge"], message: "Fabric und Forge sind inkompatibel", fix: "Verwende entweder Fabric oder Forge, nicht beide" },
];

function analyzeConflicts(mods: Mod[]): Conflict[] {
  const conflicts: Conflict[] = [];
  const ids = mods.map(m => m.id.toLowerCase());
  const names = mods.map(m => m.name.toLowerCase());

  // Known incompatibilities
  for (const conflict of KNOWN_CONFLICTS) {
    const foundIds = conflict.ids.filter(id => ids.some(m => m.includes(id)) || names.some(n => n.includes(id)));
    if (foundIds.length >= 2) {
      conflicts.push({ type: "incompatible", severity: "error", message: conflict.message, mods: foundIds, fix: conflict.fix });
    }
  }

  // Duplicate mods
  const seen = new Map<string, number>();
  for (const m of mods) {
    const key = m.name.toLowerCase().replace(/[-_ ]/g, "");
    seen.set(key, (seen.get(key) ?? 0) + 1);
  }
  for (const [key, count] of seen) {
    if (count > 1) {
      conflicts.push({ type: "duplicate", severity: "warning", message: `"${key}" scheint doppelt installiert zu sein`, mods: [key], fix: "Entferne die Duplikate" });
    }
  }

  return conflicts;
}

interface ModConflictDetectorProps {
  mods: Mod[];
  compact?: boolean;
}

export function ModConflictDetector({ mods, compact = false }: ModConflictDetectorProps) {
  const { accentColor } = useThemeStore();
  const conflicts = analyzeConflicts(mods);
  const errors = conflicts.filter(c => c.severity === "error");
  const warnings = conflicts.filter(c => c.severity === "warning");

  if (conflicts.length === 0) {
    return (
      <div style={{ display: "flex", alignItems: "center", gap: "8px", padding: compact ? "8px 12px" : "12px 16px", borderRadius: "10px", background: "rgba(16,185,129,0.1)", border: "1px solid rgba(16,185,129,0.3)" }}>
        <Icon icon="solar:check-circle-bold" style={{ color: "#10b981", fontSize: "18px", flexShrink: 0 }} />
        <span style={{ fontSize: "13px", color: "#10b981", fontWeight: 600 }}>Keine Konflikte erkannt</span>
      </div>
    );
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
      {/* Summary */}
      <div style={{ display: "flex", gap: "8px" }}>
        {errors.length > 0 && (
          <div style={{ display: "flex", alignItems: "center", gap: "6px", padding: "6px 12px", borderRadius: "8px", background: "rgba(239,68,68,0.15)", border: "1px solid rgba(239,68,68,0.3)" }}>
            <Icon icon="solar:danger-triangle-bold" style={{ color: "#ef4444", fontSize: "14px" }} />
            <span style={{ fontSize: "12px", color: "#ef4444", fontWeight: 700 }}>{errors.length} Fehler</span>
          </div>
        )}
        {warnings.length > 0 && (
          <div style={{ display: "flex", alignItems: "center", gap: "6px", padding: "6px 12px", borderRadius: "8px", background: "rgba(245,158,11,0.15)", border: "1px solid rgba(245,158,11,0.3)" }}>
            <Icon icon="solar:info-circle-bold" style={{ color: "#f59e0b", fontSize: "14px" }} />
            <span style={{ fontSize: "12px", color: "#f59e0b", fontWeight: 700 }}>{warnings.length} Warnungen</span>
          </div>
        )}
      </div>

      {/* Details */}
      {!compact && conflicts.map((c, i) => (
        <div key={i} style={{
          padding: "12px 14px", borderRadius: "10px",
          background: c.severity === "error" ? "rgba(239,68,68,0.08)" : "rgba(245,158,11,0.08)",
          border: `1px solid ${c.severity === "error" ? "rgba(239,68,68,0.25)" : "rgba(245,158,11,0.25)"}`,
        }}>
          <div style={{ display: "flex", alignItems: "flex-start", gap: "8px" }}>
            <Icon
              icon={c.severity === "error" ? "solar:danger-triangle-bold" : "solar:info-circle-bold"}
              style={{ color: c.severity === "error" ? "#ef4444" : "#f59e0b", fontSize: "16px", flexShrink: 0, marginTop: "1px" }}
            />
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: "13px", fontWeight: 700, color: "#fff" }}>{c.message}</div>
              {c.fix && (
                <div style={{ fontSize: "12px", color: "rgba(255,255,255,0.5)", marginTop: "4px" }}>
                  <Icon icon="solar:lightbulb-bold" className="w-3.5 h-3.5 inline-block mr-1" />{c.fix}
                </div>
              )}
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}
