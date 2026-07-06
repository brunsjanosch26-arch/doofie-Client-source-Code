import { create } from "zustand";

interface CinematicLaunchState {
  active: boolean;
  profileName: string;
  show: (profileName: string) => void;
  hide: () => void;
}

export const useCinematicLaunchStore = create<CinematicLaunchState>((set) => ({
  active: false,
  profileName: "",
  show: (profileName) => set({ active: true, profileName }),
  hide: () => set({ active: false }),
}));
