"use client";

import { useEffect, useState } from "react";
import { Icon } from "@iconify/react";
import { create } from "zustand";
import { persist } from "zustand/middleware";

const CURRENT_VERSION = "2.0.0";

const CHANGELOG = [
  { type: "new", text: "10 Skin-Posen mit Live-Vorschau in den Einstellungen" },
  { type: "new", text: "11 Launcher-Themes mit passenden Hintergründen" },
  { type: "new", text: "Capes-Browser direkt in den Einstellungen" },
  { type: "new", text: "Eigenes Hintergrundbild hochladen" },
  { type: "new", text: "News-Sidebar ausblendbar" },
  { type: "new", text: "Favoriten-Profile anpinnen" },
  { type: "new", text: "Spielzeit-Tracker pro Profil" },
  { type: "new", text: "Akzentfarbe automatisch aus Skin extrahieren" },
  { type: "fix", text: "Offline-Account startet Minecraft zuverlässig" },
  { type: "fix", text: "Skin-Viewer wird nicht mehr abgeschnitten" },
];

interface ChangelogSeenState {
  lastSeenVersion: string | null;
  setLastSeenVersion: (v: string) => void;
}

const useChangelogSeenStore = create<ChangelogSeenState>()(
  persist(
    (set) => ({
      lastSeenVersion: null,
      setLastSeenVersion: (v) => set({ lastSeenVersion: v }),
    }),
    { name: "doofie-changelog-seen" }
  )
);

export function ChangelogModal() {
  const { lastSeenVersion, setLastSeenVersion } = useChangelogSeenStore();
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (lastSeenVersion !== CURRENT_VERSION) {
      const timer = setTimeout(() => setOpen(true), 1500);
      return () => clearTimeout(timer);
    }
  }, [lastSeenVersion]);

  if (!open) return null;

  const handleClose = () => {
    setLastSeenVersion(CURRENT_VERSION);
    setOpen(false);
  };

  return (
    <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/60 backdrop-blur-sm">
      <div className="relative bg-[#0d1020] border border-white/10 rounded-2xl shadow-2xl w-full max-w-md mx-4 overflow-hidden">
        {/* Header */}
        <div className="px-6 pt-6 pb-4 border-b border-white/10">
          <div className="flex items-center justify-between">
            <div>
              <div className="text-xs text-white/40 uppercase tracking-widest mb-1">Neu in</div>
              <h2 className="font-minecraft text-2xl text-white lowercase">doofie client v{CURRENT_VERSION}</h2>
            </div>
            <button
              onClick={handleClose}
              className="text-white/40 hover:text-white transition-colors p-1"
            >
              <Icon icon="solar:close-circle-bold" className="w-6 h-6" />
            </button>
          </div>
        </div>

        {/* Changelog list */}
        <div className="px-6 py-4 space-y-2 max-h-72 overflow-y-auto">
          {CHANGELOG.map((item, i) => (
            <div key={i} className="flex items-start gap-3">
              <span className={`text-xs font-bold px-2 py-0.5 rounded mt-0.5 flex-shrink-0 ${
                item.type === "new"
                  ? "bg-green-500/15 text-green-400 border border-green-500/20"
                  : "bg-yellow-500/15 text-yellow-400 border border-yellow-500/20"
              }`}>
                {item.type === "new" ? "NEU" : "FIX"}
              </span>
              <span className="text-white/70 text-sm">{item.text}</span>
            </div>
          ))}
        </div>

        {/* Footer */}
        <div className="px-6 py-4 border-t border-white/10 flex justify-end">
          <button
            onClick={handleClose}
            className="font-minecraft lowercase text-sm px-6 py-2 rounded-lg bg-white/10 hover:bg-white/20 text-white transition-colors"
          >
            los geht's
          </button>
        </div>
      </div>
    </div>
  );
}
