import { useEffect } from "react";
import { useBackgroundEffectStore, BACKGROUND_EFFECTS } from "../store/background-effect-store";
import { useSnowEffectStore } from "../store/snow-effect-store";
// eslint-disable-next-line @typescript-eslint/no-unused-vars

function getCurrentSeason() {
  const now = new Date();
  const month = now.getMonth() + 1;
  const day = now.getDate();

  if (month === 10 && day >= 20 || month === 11 && day <= 3) return "halloween";
  if (month === 12 && day >= 1 || month === 1 && day <= 6) return "christmas";
  if (month === 2 && day === 14) return "valentines";
  if (month === 4 && day === 1) return "april_fools";
  if (month >= 12 || month <= 2) return "winter";
  if (month >= 3 && month <= 5) return "spring";
  if (month >= 6 && month <= 8) return "summer";
  return "autumn";
}

export interface SeasonInfo {
  id: string;
  name: string;
  emoji: string;
  message: string;
  autoSnow: boolean;
  suggestedEffect?: string;
}

export const SEASONS: Record<string, SeasonInfo> = {
  halloween: {
    id: "halloween",
    name: "Halloween",
    emoji: "🎃",
    message: "Gruselige Grüße! Blood Moon wartet auf dich...",
    autoSnow: false,
    suggestedEffect: BACKGROUND_EFFECTS.BLOOD_MOON,
  },
  christmas: {
    id: "christmas",
    name: "Weihnachten",
    emoji: "🎄",
    message: "Frohe Weihnachten! Schnee aktiviert.",
    autoSnow: true,
    suggestedEffect: BACKGROUND_EFFECTS.ICE,
  },
  valentines: {
    id: "valentines",
    name: "Valentinstag",
    emoji: "💝",
    message: "Happy Valentine's Day!",
    autoSnow: false,
    suggestedEffect: BACKGROUND_EFFECTS.ENCHANTMENT_PARTICLES,
  },
  april_fools: {
    id: "april_fools",
    name: "April Fools",
    emoji: "🃏",
    message: "April! April! Schau mal ins Matrix...",
    autoSnow: false,
    suggestedEffect: BACKGROUND_EFFECTS.MATRIX_RAIN,
  },
  winter: { id: "winter", name: "Winter", emoji: "❄️", message: "", autoSnow: true },
  spring: { id: "spring", name: "Frühling", emoji: "🌸", message: "", autoSnow: false },
  summer: { id: "summer", name: "Sommer", emoji: "☀️", message: "", autoSnow: false },
  autumn: { id: "autumn", name: "Herbst", emoji: "🍂", message: "", autoSnow: false },
};

const SEASONAL_SHOWN_KEY = "doofie-seasonal-shown";

export function useSeasonalEvents() {
  const setCurrentEffect = useBackgroundEffectStore(s => s.setCurrentEffect);
  const setSnowEffect = useSnowEffectStore(s => s.setSnowEffect);
  const season = getCurrentSeason();

  useEffect(() => {
    const info = SEASONS[season];
    if (!info) return;

    // Auto-snow for winter / christmas
    if (info.autoSnow) {
      setSnowEffect(true);
    }

    // Show seasonal notification once per day
    const shown = localStorage.getItem(SEASONAL_SHOWN_KEY);
    const today = new Date().toDateString();
    if (info.message && shown !== today) {
      localStorage.setItem(SEASONAL_SHOWN_KEY, today);
      // Trigger after 3 seconds
      setTimeout(() => {
        if (info.suggestedEffect) {
          // Don't auto-change effect — just note availability
        }
      }, 3000);
    }
  }, [season, setCurrentEffect, setSnowEffect]);

  return { season, seasonInfo: SEASONS[season] };
}

export { getCurrentSeason };
