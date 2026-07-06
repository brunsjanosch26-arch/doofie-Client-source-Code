import { create } from "zustand";
import { persist } from "zustand/middleware";

interface PlaytimeState {
  sessions: Record<string, number>; // profileId -> total seconds
  activeSession: { profileId: string; startedAt: number } | null;
  startSession: (profileId: string) => void;
  endSession: (profileId: string) => void;
  getPlaytime: (profileId: string) => number;
  formatPlaytime: (seconds: number) => string;
}

export const usePlaytimeStore = create<PlaytimeState>()(
  persist(
    (set, get) => ({
      sessions: {},
      activeSession: null,

      startSession: (profileId) => {
        set({ activeSession: { profileId, startedAt: Date.now() } });
      },

      endSession: (profileId) => {
        const { activeSession, sessions } = get();
        if (!activeSession || activeSession.profileId !== profileId) return;
        const elapsed = Math.floor((Date.now() - activeSession.startedAt) / 1000);
        set({
          activeSession: null,
          sessions: {
            ...sessions,
            [profileId]: (sessions[profileId] ?? 0) + elapsed,
          },
        });
      },

      getPlaytime: (profileId) => get().sessions[profileId] ?? 0,

      formatPlaytime: (seconds) => {
        if (seconds < 60) return `${seconds}s`;
        const h = Math.floor(seconds / 3600);
        const m = Math.floor((seconds % 3600) / 60);
        if (h === 0) return `${m}m`;
        return `${h}h ${m}m`;
      },
    }),
    { name: "doofie-playtime" }
  )
);
