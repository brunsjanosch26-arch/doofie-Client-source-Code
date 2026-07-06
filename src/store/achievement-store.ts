import { create } from "zustand";
import { persist } from "zustand/middleware";

export interface Achievement {
  id: string;
  name: string;
  description: string;
  icon: string;
  category: "launcher" | "social" | "minecraft" | "collection" | "secret";
  points: number;
  hidden?: boolean;
  condition?: (stats: AchievementStats) => boolean;
}

export interface AchievementStats {
  totalPlaytime: number;
  profilesCreated: number;
  modsInstalled: number;
  themesUsed: string[];
  launchCount: number;
  skinsUploaded: number;
  screenshotsTaken: number;
  serversAdded: number;
  radioListened: number;
}

export const ALL_ACHIEVEMENTS: Achievement[] = [
  // Launcher
  { id: "first_launch", name: "Willkommen!", description: "Doofie Client zum ersten Mal gestartet", icon: "solar:rocket-bold", category: "launcher", points: 10 },
  { id: "hour_played", name: "Stundenlang", description: "1 Stunde im Launcher verbracht", icon: "solar:clock-circle-bold", category: "launcher", points: 20, condition: s => s.totalPlaytime >= 3600 },
  { id: "10h_played", name: "Zeitvertreib", description: "10 Stunden Gesamtspielzeit", icon: "solar:hourglass-bold", category: "launcher", points: 50, condition: s => s.totalPlaytime >= 36000 },
  { id: "100h_played", name: "Hardcore-Spieler", description: "100 Stunden Gesamtspielzeit", icon: "solar:medal-ribbon-bold", category: "launcher", points: 200, condition: s => s.totalPlaytime >= 360000 },
  { id: "launch_10", name: "Stammgast", description: "Launcher 10 Mal geöffnet", icon: "solar:refresh-circle-bold", category: "launcher", points: 15, condition: s => s.launchCount >= 10 },
  { id: "launch_50", name: "Alter Hase", description: "Launcher 50 Mal geöffnet", icon: "solar:running-round-bold", category: "launcher", points: 40, condition: s => s.launchCount >= 50 },
  // Collection
  { id: "first_theme", name: "Stylist", description: "Erstes Theme aktiviert", icon: "solar:palette-bold", category: "collection", points: 15, condition: s => s.themesUsed.length >= 1 },
  { id: "all_themes", name: "Modeschöpfer", description: "Alle Themes ausprobiert", icon: "solar:slider-minimalistic-bold", category: "collection", points: 100, condition: s => s.themesUsed.length >= 15 },
  { id: "first_profile", name: "Neuanfang", description: "Erstes Profil erstellt", icon: "solar:folder-bold", category: "collection", points: 10, condition: s => s.profilesCreated >= 1 },
  { id: "five_profiles", name: "Sammler", description: "5 Profile erstellt", icon: "solar:folder-open-bold", category: "collection", points: 30, condition: s => s.profilesCreated >= 5 },
  { id: "first_mod", name: "Modder", description: "Ersten Mod installiert", icon: "solar:settings-minimalistic-bold", category: "collection", points: 15, condition: s => s.modsInstalled >= 1 },
  { id: "mod_pack", name: "Pack-Builder", description: "20 Mods installiert", icon: "solar:box-bold", category: "collection", points: 60, condition: s => s.modsInstalled >= 20 },
  { id: "skin_changer", name: "Neues Ich", description: "Ersten eigenen Skin hochgeladen", icon: "solar:user-bold", category: "collection", points: 20, condition: s => s.skinsUploaded >= 1 },
  // Social
  { id: "radio_on", name: "DJ Doofie", description: "Doofie Radio zum ersten Mal gehört", icon: "solar:music-note-bold", category: "social", points: 15, condition: s => s.radioListened >= 1 },
  { id: "radio_fan", name: "Radio-Fan", description: "1 Stunde Radio gehört", icon: "solar:music-note-2-bold", category: "social", points: 40, condition: s => s.radioListened >= 3600 },
  { id: "server_added", name: "Multiplayer", description: "Ersten Server hinzugefügt", icon: "solar:global-bold", category: "social", points: 15, condition: s => s.serversAdded >= 1 },
  // Minecraft
  { id: "screenshot", name: "Fotograf", description: "Screenshot aufgenommen", icon: "solar:camera-bold", category: "minecraft", points: 10, condition: s => s.screenshotsTaken >= 1 },
  { id: "screenshot_pro", name: "Profi-Fotograf", description: "50 Screenshots aufgenommen", icon: "solar:camera-add-bold", category: "minecraft", points: 50, condition: s => s.screenshotsTaken >= 50 },
  // Secret
  { id: "secret_theme", name: "???", description: "Finde das geheime Theme", icon: "solar:eye-bold", category: "secret", points: 500, hidden: true, condition: s => s.themesUsed.includes("blood_moon") && s.totalPlaytime >= 7200 },
  { id: "night_owl", name: "Nachteule", description: "???", icon: "solar:moon-stars-bold", category: "secret", points: 100, hidden: true },
];

interface AchievementState {
  unlocked: string[];
  stats: AchievementStats;
  pendingUnlock: Achievement | null;
  unlock: (id: string) => void;
  clearPending: () => void;
  updateStats: (patch: Partial<AchievementStats>) => void;
  checkAll: () => void;
}

const DEFAULT_STATS: AchievementStats = {
  totalPlaytime: 0,
  profilesCreated: 0,
  modsInstalled: 0,
  themesUsed: [],
  launchCount: 0,
  skinsUploaded: 0,
  screenshotsTaken: 0,
  serversAdded: 0,
  radioListened: 0,
};

export const useAchievementStore = create<AchievementState>()(
  persist(
    (set, get) => ({
      unlocked: [],
      stats: DEFAULT_STATS,
      pendingUnlock: null,

      unlock: (id) => {
        const { unlocked } = get();
        if (unlocked.includes(id)) return;
        const achievement = ALL_ACHIEVEMENTS.find(a => a.id === id);
        if (!achievement) return;
        set({ unlocked: [...unlocked, id], pendingUnlock: achievement });
      },

      clearPending: () => set({ pendingUnlock: null }),

      updateStats: (patch) => {
        set(s => ({ stats: { ...s.stats, ...patch } }));
        get().checkAll();
      },

      checkAll: () => {
        const { stats, unlocked } = get();
        for (const a of ALL_ACHIEVEMENTS) {
          if (!unlocked.includes(a.id) && a.condition && a.condition(stats)) {
            get().unlock(a.id);
            break; // one at a time for sequential popups
          }
        }
      },
    }),
    { name: "doofie-achievements" }
  )
);
