import { create } from "zustand";
import { persist } from "zustand/middleware";

export interface RadioStation {
  id: string;
  name: string;
  genre: string;
  url: string;
  icon: string;
  color: string;
}

export const STATIONS: RadioStation[] = [
  { id: "lofi", name: "Lofi Hip Hop", genre: "Chill", url: "https://streams.ilovemusic.de/iloveradio17.mp3", icon: "solar:music-note-2-bold", color: "#8b5cf6" },
  { id: "jazz", name: "Smooth Jazz", genre: "Jazz", url: "https://streaming.radio.co/s4c6b9e05e/listen", icon: "mdi:saxophone", color: "#f59e0b" },
  { id: "chillhop", name: "Chillhop", genre: "Chill", url: "https://streams.ilovemusic.de/iloveradio2.mp3", icon: "solar:leaf-bold", color: "#10b981" },
  { id: "synthwave", name: "Synthwave", genre: "Electronic", url: "https://streams.ilovemusic.de/iloveradio21.mp3", icon: "solar:city-bold", color: "#ec4899" },
  { id: "classical", name: "Klassik", genre: "Classical", url: "https://streams.ilovemusic.de/iloveradio5.mp3", icon: "mdi:violin", color: "#3b82f6" },
];

interface RadioState {
  isOpen: boolean;
  isPlaying: boolean;
  currentStationId: string;
  volume: number;
  totalListened: number;
  setOpen: (open: boolean) => void;
  setPlaying: (playing: boolean) => void;
  setStation: (id: string) => void;
  setVolume: (v: number) => void;
  addListened: (seconds: number) => void;
}

export const useRadioStore = create<RadioState>()(
  persist(
    (set) => ({
      isOpen: false,
      isPlaying: false,
      currentStationId: "lofi",
      volume: 0.6,
      totalListened: 0,
      setOpen: (isOpen) => set({ isOpen }),
      setPlaying: (isPlaying) => set({ isPlaying }),
      setStation: (currentStationId) => set({ currentStationId }),
      setVolume: (volume) => set({ volume }),
      addListened: (seconds) => set(s => ({ totalListened: s.totalListened + seconds })),
    }),
    { name: "doofie-radio" }
  )
);
