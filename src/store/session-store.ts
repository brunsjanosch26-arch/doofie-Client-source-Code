import { create } from "zustand";
import { persist } from "zustand/middleware";

export interface GameSession {
  id: string;
  profileId: string;
  profileName: string;
  startTime: number;
  endTime: number | null;
  durationSeconds: number;
}

interface SessionState {
  sessions: GameSession[];
  activeSession: GameSession | null;
  startSession: (profileId: string, profileName: string) => void;
  endSession: (profileId: string) => void;
  getTotalPlaytime: () => number;
  getRecentSessions: (limit?: number) => GameSession[];
  getProfileStats: () => Record<string, { name: string; total: number; sessions: number }>;
}

export const useSessionStore = create<SessionState>()(
  persist(
    (set, get) => ({
      sessions: [],
      activeSession: null,

      startSession: (profileId, profileName) => {
        const session: GameSession = {
          id: `${Date.now()}`,
          profileId,
          profileName,
          startTime: Date.now(),
          endTime: null,
          durationSeconds: 0,
        };
        set({ activeSession: session });
      },

      endSession: (profileId) => {
        const { activeSession, sessions } = get();
        if (!activeSession || activeSession.profileId !== profileId) return;
        const endTime = Date.now();
        const duration = Math.floor((endTime - activeSession.startTime) / 1000);
        const completed: GameSession = { ...activeSession, endTime, durationSeconds: duration };
        set({ activeSession: null, sessions: [completed, ...sessions].slice(0, 200) });
      },

      getTotalPlaytime: () => {
        const { sessions, activeSession } = get();
        const completed = sessions.reduce((sum, s) => sum + s.durationSeconds, 0);
        const current = activeSession ? Math.floor((Date.now() - activeSession.startTime) / 1000) : 0;
        return completed + current;
      },

      getRecentSessions: (limit = 20) => get().sessions.slice(0, limit),

      getProfileStats: () => {
        const stats: Record<string, { name: string; total: number; sessions: number }> = {};
        for (const s of get().sessions) {
          if (!stats[s.profileId]) stats[s.profileId] = { name: s.profileName, total: 0, sessions: 0 };
          stats[s.profileId].total += s.durationSeconds;
          stats[s.profileId].sessions++;
        }
        return stats;
      },
    }),
    { name: "doofie-sessions" }
  )
);
