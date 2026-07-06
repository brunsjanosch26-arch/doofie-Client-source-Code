import { create } from "zustand";
import { persist } from "zustand/middleware";

interface PinnedProfilesState {
  pinnedIds: string[];
  togglePin: (id: string) => void;
  isPinned: (id: string) => boolean;
}

export const usePinnedProfilesStore = create<PinnedProfilesState>()(
  persist(
    (set, get) => ({
      pinnedIds: [],
      togglePin: (id) => {
        set((state) => ({
          pinnedIds: state.pinnedIds.includes(id)
            ? state.pinnedIds.filter((p) => p !== id)
            : [...state.pinnedIds, id],
        }));
      },
      isPinned: (id) => get().pinnedIds.includes(id),
    }),
    { name: "doofie-pinned-profiles" }
  )
);
