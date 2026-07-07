import { create } from "zustand";
import { persist } from "zustand/middleware";

export enum BACKGROUND_EFFECTS {
  NONE = "none",
  MATRIX_RAIN = "matrix-rain",
  ENCHANTMENT_PARTICLES = "enchantment-particles",
  NEBULA_WAVES = "nebula-waves",
  NEBULA_PARTICLES = "nebula-particles",
  NEBULA_GRID = "nebula-grid",
  NEBULA_VOXELS = "nebula-voxels",
  NEBULA_LIGHTNING = "nebula-lightning",
  NEBULA_LIQUID_CHROME = "nebula-liquid-chrome",
  RETRO_GRID = "retro-grid",
  PLAIN_BACKGROUND = "plain-background",
  MINECRAFT = "minecraft",
  CUSTOM_IMAGE = "custom-image",
  AURORA = "aurora",
  CYBERPUNK = "cyberpunk",
  GALAXY = "galaxy",
  BLOOD_MOON = "blood-moon",
  ICE = "ice",
  LATEST_SCREENSHOT = "latest-screenshot",
}

interface BackgroundEffectState {
  currentEffect: string;
  customImagePath: string | null;
  setCurrentEffect: (effect: string) => void;
  setCustomImagePath: (path: string | null) => void;
}

export const useBackgroundEffectStore = create<BackgroundEffectState>()(
  persist(
    (set) => ({
      currentEffect: BACKGROUND_EFFECTS.MINECRAFT,
      customImagePath: null,
      setCurrentEffect: (effect) => set({ currentEffect: effect }),
      setCustomImagePath: (path) => set({ customImagePath: path }),
    }),
    {
      name: "doofie-background-effect-storage",
    },
  ),
);
