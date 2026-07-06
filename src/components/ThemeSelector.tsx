"use client";

import { Icon } from "@iconify/react";
import { cn } from "../lib/utils";
import { useLauncherTheme } from "../hooks/useLauncherTheme";
import { LAUNCHER_THEMES } from "../store/launcher-theme-store";

interface ThemeSelectorProps {
  disabled?: boolean;
}

const EFFECT_LABELS: Record<string, string> = {
  "nebula-waves": "Wellen",
  "matrix-rain": "Matrix",
  "nebula-particles": "Partikel",
  "nebula-liquid-chrome": "Chrome",
  "nebula-grid": "Grid",
  "nebula-lightning": "Blitze",
  "retro-grid": "Retro",
  "nebula-voxels": "Voxels",
  "minecraft": "Minecraft",
  "enchantment-particles": "Magie",
  "plain-background": "Schlicht",
  "aurora": "Aurora",
  "cyberpunk": "Cyberpunk",
  "galaxy": "Galaxie",
  "blood-moon": "Blutmond",
  "ice": "Eis",
};

export function ThemeSelector({ disabled }: ThemeSelectorProps) {
  const { selectedThemeId, toggleTheme, isThemeUnlocked } = useLauncherTheme();
  const themes = Object.values(LAUNCHER_THEMES);

  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
      {themes.map((theme) => {
        const isUnlocked = isThemeUnlocked(theme.id);
        const isSelected = selectedThemeId === theme.id;
        const effectLabel = EFFECT_LABELS[theme.backgroundEffect] ?? theme.backgroundEffect;

        return (
          <button
            key={theme.id}
            onClick={() => {
              if (!disabled && isUnlocked) toggleTheme(theme.id);
            }}
            disabled={disabled || !isUnlocked}
            className={cn(
              "group relative flex flex-col items-start gap-0 rounded-2xl border-2 transition-all duration-300 text-left overflow-hidden",
              isSelected
                ? "border-white/50 shadow-2xl scale-[1.02]"
                : "border-[#ffffff14] bg-black/30",
              !isUnlocked
                ? "opacity-40 cursor-not-allowed grayscale"
                : disabled
                  ? "opacity-40 cursor-not-allowed"
                  : "hover:border-[#ffffff30] hover:scale-[1.01] cursor-pointer"
            )}
            style={{
              boxShadow: isSelected
                ? `0 0 0 2px ${theme.accentColor.value}60, 0 0 30px ${theme.accentColor.value}40, 0 8px 32px rgba(0,0,0,0.5)`
                : undefined,
            }}
          >
            {/* Animated color preview banner */}
            <div
              className="w-full relative overflow-hidden"
              style={{ height: "72px" }}
            >
              {/* Base gradient */}
              <div
                className="absolute inset-0 transition-all duration-500"
                style={{
                  background: `linear-gradient(135deg, ${theme.accentColor.dark} 0%, ${theme.accentColor.value} 50%, ${theme.accentColor.light} 100%)`,
                }}
              />

              {/* Animated shimmer */}
              <div
                className={cn(
                  "absolute inset-0 opacity-0 transition-opacity duration-300",
                  !isUnlocked || disabled ? "" : "group-hover:opacity-100"
                )}
                style={{
                  background: `linear-gradient(105deg, transparent 30%, rgba(255,255,255,0.25) 50%, transparent 70%)`,
                  animation: "shimmer 2s infinite",
                }}
              />

              {/* Glow overlay when selected */}
              {isSelected && (
                <div
                  className="absolute inset-0"
                  style={{
                    background: `radial-gradient(ellipse at 50% 100%, ${theme.accentColor.value}50 0%, transparent 70%)`,
                    animation: "pulse-glow 2s ease-in-out infinite",
                  }}
                />
              )}

              {/* Icon */}
              <div
                className="absolute inset-0 flex items-center justify-center"
                style={{ filter: "drop-shadow(0 2px 8px rgba(0,0,0,0.5))" }}
              >
                <Icon icon={theme.icon} className="w-8 h-8" style={{ color: theme.accentColor.light }} />
              </div>

              {/* Selected badge */}
              {isSelected && (
                <div
                  className="absolute top-2 right-2 flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-bold"
                  style={{
                    background: "rgba(0,0,0,0.6)",
                    color: theme.accentColor.light,
                    border: `1px solid ${theme.accentColor.value}60`,
                    backdropFilter: "blur(4px)",
                  }}
                >
                  <Icon icon="solar:check-circle-bold" className="w-3 h-3" />
                  Aktiv
                </div>
              )}

              {/* Lock */}
              {!isUnlocked && (
                <div className="absolute top-2 right-2 w-7 h-7 rounded-full bg-black/60 flex items-center justify-center border border-white/20">
                  <Icon icon="solar:lock-keyhole-bold" className="w-4 h-4 text-white/50" />
                </div>
              )}
            </div>

            {/* Info section */}
            <div
              className="w-full p-3 flex flex-col gap-1 relative"
              style={{
                background: isSelected
                  ? `linear-gradient(180deg, ${theme.accentColor.dark}18 0%, rgba(0,0,0,0.3) 100%)`
                  : "rgba(0,0,0,0.25)",
              }}
            >
              {/* Name + effect */}
              <div className="flex items-center justify-between gap-1">
                <span
                  className="font-minecraft-ten text-sm leading-tight"
                  style={{ color: isSelected ? theme.accentColor.light : "rgba(255,255,255,0.85)" }}
                >
                  {theme.name}
                </span>
                <span
                  className="text-[10px] px-1.5 py-0.5 rounded font-semibold shrink-0"
                  style={{
                    background: `${theme.accentColor.value}22`,
                    color: theme.accentColor.light,
                    border: `1px solid ${theme.accentColor.value}40`,
                  }}
                >
                  {effectLabel}
                </span>
              </div>

              {/* Description */}
              {theme.description && (
                <span className="text-[11px] text-white/45 leading-snug line-clamp-2">
                  {theme.description}
                </span>
              )}

              {/* Color dots */}
              <div className="flex items-center gap-1 mt-1">
                {[theme.accentColor.dark, theme.accentColor.value, theme.accentColor.light].map((c, i) => (
                  <div
                    key={i}
                    className="rounded-full"
                    style={{
                      width: i === 1 ? "12px" : "8px",
                      height: i === 1 ? "12px" : "8px",
                      background: c,
                      boxShadow: isSelected && i === 1 ? `0 0 8px ${c}` : undefined,
                    }}
                  />
                ))}
              </div>
            </div>
          </button>
        );
      })}

      <style>{`
        @keyframes shimmer {
          0% { transform: translateX(-100%); }
          100% { transform: translateX(200%); }
        }
        @keyframes pulse-glow {
          0%, 100% { opacity: 0.6; }
          50% { opacity: 1; }
        }
      `}</style>
    </div>
  );
}
