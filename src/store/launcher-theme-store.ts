import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { AccentColor } from "./useThemeStore";
import { BACKGROUND_EFFECTS } from "./background-effect-store";

export interface LauncherTheme {
  id: string;
  name: string;
  icon: string;
  description?: string;
  accentColor: AccentColor;
  backgroundEffect: string;
  backgroundImage?: string;
  cssVars?: Record<string, string>;
  unlockRequirement?: {
    type: "advent-door";
    day: number;
  };
}

export const LAUNCHER_THEMES: Record<string, LauncherTheme> = {
  aurora: {
    id: "aurora",
    name: "Aurora Borealis",
    icon: "solar:planet-bold",
    description: "Nordlichter tanzen am Himmel",
    accentColor: {
      name: "Aurora",
      value: "#00ffaa",
      hoverValue: "#00ddaa",
      shadowValue: "rgba(0,255,170,0.5)",
      light: "#44ffcc",
      dark: "#00aa77",
    },
    backgroundEffect: BACKGROUND_EFFECTS.AURORA,
    cssVars: {
      "--theme-glow": "0 0 30px rgba(0,255,170,0.4), 0 0 60px rgba(0,170,255,0.2)",
      "--theme-border": "rgba(0,255,200,0.3)",
    },
  },
  cyberpunk: {
    id: "cyberpunk",
    name: "Cyberpunk",
    icon: "solar:cpu-bolt-bold",
    description: "Neon-Dystopie, Glitch & Neonregen",
    accentColor: {
      name: "Neon Pink",
      value: "#ff0088",
      hoverValue: "#dd0077",
      shadowValue: "rgba(255,0,136,0.5)",
      light: "#ff44aa",
      dark: "#aa0055",
    },
    backgroundEffect: BACKGROUND_EFFECTS.CYBERPUNK,
    cssVars: {
      "--theme-glow": "0 0 20px rgba(255,0,136,0.5), 0 0 40px rgba(0,255,255,0.2)",
      "--theme-border": "rgba(255,0,136,0.35)",
    },
  },
  galaxy: {
    id: "galaxy",
    name: "Galaxy",
    icon: "solar:stars-bold",
    description: "Rotierende Sterngalaxie im Hintergrund",
    accentColor: {
      name: "Galaxy",
      value: "#a855f7",
      hoverValue: "#9333ea",
      shadowValue: "rgba(168,85,247,0.5)",
      light: "#c084fc",
      dark: "#7e22ce",
    },
    backgroundEffect: BACKGROUND_EFFECTS.GALAXY,
    cssVars: {
      "--theme-glow": "0 0 25px rgba(168,85,247,0.45), 0 0 50px rgba(80,60,200,0.2)",
      "--theme-border": "rgba(168,85,247,0.3)",
    },
  },
  blood_moon: {
    id: "blood_moon",
    name: "Blood Moon",
    icon: "solar:drop-bold",
    description: "Blutmond am Nachthimmel mit Glut",
    accentColor: {
      name: "Blood Red",
      value: "#cc0000",
      hoverValue: "#aa0000",
      shadowValue: "rgba(200,0,0,0.5)",
      light: "#ff3333",
      dark: "#880000",
    },
    backgroundEffect: BACKGROUND_EFFECTS.BLOOD_MOON,
    cssVars: {
      "--theme-glow": "0 0 25px rgba(200,0,0,0.5), 0 0 50px rgba(100,0,0,0.2)",
      "--theme-border": "rgba(200,0,0,0.3)",
    },
  },
  ice: {
    id: "ice",
    name: "Eiskönigin",
    icon: "solar:snowflake-bold",
    description: "Frostiger Winter mit Eiskristallen",
    accentColor: {
      name: "Ice Blue",
      value: "#7dd3fc",
      hoverValue: "#38bdf8",
      shadowValue: "rgba(125,211,252,0.5)",
      light: "#bae6fd",
      dark: "#0ea5e9",
    },
    backgroundEffect: BACKGROUND_EFFECTS.ICE,
    cssVars: {
      "--theme-glow": "0 0 25px rgba(125,211,252,0.4), 0 0 50px rgba(100,180,255,0.2)",
      "--theme-border": "rgba(125,211,252,0.3)",
    },
  },
  ocean: {
    id: "ocean",
    name: "Ocean",
    icon: "solar:water-bold",
    accentColor: {
      name: "Cyan",
      value: "#00B9E8",
      hoverValue: "#0099CC",
      shadowValue: "rgba(0, 185, 232, 0.5)",
      light: "#22d3ee",
      dark: "#0891b2",
    },
    backgroundEffect: BACKGROUND_EFFECTS.NEBULA_WAVES,
  },
  matrix: {
    id: "matrix",
    name: "Matrix",
    icon: "solar:code-bold",
    accentColor: {
      name: "Green",
      value: "#10b981",
      hoverValue: "#059669",
      shadowValue: "rgba(16, 185, 129, 0.5)",
      light: "#34d399",
      dark: "#047857",
    },
    backgroundEffect: BACKGROUND_EFFECTS.MATRIX_RAIN,
  },
  neon: {
    id: "neon",
    name: "Neon",
    icon: "solar:lightning-bold",
    accentColor: {
      name: "Purple",
      value: "#9c5fff",
      hoverValue: "#8a4aff",
      shadowValue: "rgba(156, 95, 255, 0.5)",
      light: "#a78bfa",
      dark: "#7c3aed",
    },
    backgroundEffect: BACKGROUND_EFFECTS.NEBULA_PARTICLES,
  },
  sunset: {
    id: "sunset",
    name: "Sunset",
    icon: "solar:sunset-bold",
    accentColor: {
      name: "Orange",
      value: "#f97316",
      hoverValue: "#ea580c",
      shadowValue: "rgba(249, 115, 22, 0.5)",
      light: "#fb923c",
      dark: "#c2410c",
    },
    backgroundEffect: BACKGROUND_EFFECTS.NEBULA_LIQUID_CHROME,
  },
  space: {
    id: "space",
    name: "Space",
    icon: "solar:telescope-bold",
    accentColor: {
      name: "Indigo",
      value: "#6366f1",
      hoverValue: "#4f46e5",
      shadowValue: "rgba(99, 102, 241, 0.5)",
      light: "#818cf8",
      dark: "#3730a3",
    },
    backgroundEffect: BACKGROUND_EFFECTS.NEBULA_GRID,
  },
  storm: {
    id: "storm",
    name: "Storm",
    icon: "solar:cloud-storm-bold",
    accentColor: {
      name: "Blue",
      value: "#4f8eff",
      hoverValue: "#3a7aff",
      shadowValue: "rgba(79, 142, 255, 0.5)",
      light: "#60a5fa",
      dark: "#2563eb",
    },
    backgroundEffect: BACKGROUND_EFFECTS.NEBULA_LIGHTNING,
  },
  retro: {
    id: "retro",
    name: "Retro",
    icon: "solar:gamepad-bold",
    accentColor: {
      name: "Amber",
      value: "#f59e0b",
      hoverValue: "#d97706",
      shadowValue: "rgba(245, 158, 11, 0.5)",
      light: "#fbbf24",
      dark: "#b45309",
    },
    backgroundEffect: BACKGROUND_EFFECTS.RETRO_GRID,
  },
  lava: {
    id: "lava",
    name: "Lava",
    icon: "solar:fire-bold",
    accentColor: {
      name: "Red",
      value: "#ef4444",
      hoverValue: "#dc2626",
      shadowValue: "rgba(239, 68, 68, 0.5)",
      light: "#f87171",
      dark: "#b91c1c",
    },
    backgroundEffect: BACKGROUND_EFFECTS.NEBULA_VOXELS,
  },
  minecraft: {
    id: "minecraft",
    name: "Minecraft",
    icon: "solar:pick-bold",
    accentColor: {
      name: "Teal",
      value: "#14b8a6",
      hoverValue: "#0d9488",
      shadowValue: "rgba(20, 184, 166, 0.5)",
      light: "#2dd4bf",
      dark: "#0f766e",
    },
    backgroundEffect: BACKGROUND_EFFECTS.MINECRAFT,
  },
  enchanted: {
    id: "enchanted",
    name: "Enchanted",
    icon: "solar:stars-minimalistic-bold",
    accentColor: {
      name: "Pink",
      value: "#ec4899",
      hoverValue: "#db2777",
      shadowValue: "rgba(236, 72, 153, 0.5)",
      light: "#f472b6",
      dark: "#be185d",
    },
    backgroundEffect: BACKGROUND_EFFECTS.ENCHANTMENT_PARTICLES,
  },
  christmas_theme: {
    id: "christmas_theme",
    name: "Christmas",
    icon: "solar:gift-bold",
    accentColor: {
      name: "Christmas Blue",
      value: "#4A90D9",
      hoverValue: "#3A7BC8",
      light: "#6BA3E3",
      dark: "#2E5A8A",
      shadowValue: "rgba(74, 144, 217, 0.5)",
      isCustom: true,
    },
    backgroundEffect: BACKGROUND_EFFECTS.PLAIN_BACKGROUND,
    backgroundImage: "/themes/christmas_theme.png",
    unlockRequirement: {
      type: "advent-door",
      day: 2,
    },
  },
};

interface LauncherThemeState {
  selectedThemeId: string | null;
  openedAdventDoors: number[];
  originalAccentColor: AccentColor | null;
  selectTheme: (themeId: string | null) => void;
  markAdventDoorOpened: (day: number) => void;
  isThemeUnlocked: (themeId: string) => boolean;
  setOriginalAccentColor: (color: AccentColor | null) => void;
  getSelectedTheme: () => LauncherTheme | null;
}

export const useLauncherThemeStore = create<LauncherThemeState>()(
  persist(
    (set, get) => ({
      selectedThemeId: null,
      openedAdventDoors: [],
      originalAccentColor: null,

      selectTheme: (themeId: string | null) => {
        set({ selectedThemeId: themeId });
      },

      markAdventDoorOpened: (day: number) => {
        set((state) => {
          if (state.openedAdventDoors.includes(day)) return state;
          return {
            openedAdventDoors: [...state.openedAdventDoors, day].sort((a, b) => a - b),
          };
        });
      },

      isThemeUnlocked: (themeId: string) => {
        const theme = LAUNCHER_THEMES[themeId];
        if (!theme) return false;
        if (!theme.unlockRequirement) return true;
        const { openedAdventDoors } = get();
        if (theme.unlockRequirement.type === "advent-door") {
          return openedAdventDoors.includes(theme.unlockRequirement.day);
        }
        return false;
      },

      setOriginalAccentColor: (color: AccentColor | null) => {
        set({ originalAccentColor: color });
      },

      getSelectedTheme: () => {
        const { selectedThemeId } = get();
        if (!selectedThemeId) return null;
        return LAUNCHER_THEMES[selectedThemeId] || null;
      },
    }),
    {
      name: "launcher-theme-storage",
    }
  )
);
