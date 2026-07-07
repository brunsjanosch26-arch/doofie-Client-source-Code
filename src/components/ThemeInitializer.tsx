"use client";

import { useEffect } from "react";
import { useThemeStore } from "../store/useThemeStore";
import { useLauncherTheme } from "../hooks/useLauncherTheme";
import * as ConfigService from "../services/launcher-config-service";

export function ThemeInitializer() {
  const applyAccentColorToDOM = useThemeStore(
    (state) => state.applyAccentColorToDOM,
  );
  const applyBorderRadiusToDOM = useThemeStore(
    (state) => state.applyBorderRadiusToDOM,
  );
  const accentColor = useThemeStore((state) => state.accentColor);
  useLauncherTheme();

  useEffect(() => {
    applyAccentColorToDOM();
    applyBorderRadiusToDOM();
  }, [applyAccentColorToDOM, applyBorderRadiusToDOM]);

  // Theme-Handshake: Akzentfarbe in die Launcher-Config schreiben,
  // damit sie beim Launch als -Ddoofie.accent an die Ingame-Mod geht
  useEffect(() => {
    const syncAccent = async () => {
      try {
        const config = await ConfigService.getLauncherConfig();
        const hex = accentColor.value.replace("#", "");
        if (config.accent_color !== hex) {
          await ConfigService.setLauncherConfig({ ...config, accent_color: hex });
        }
      } catch (e) {
        // Web-Preview oder Backend nicht verfuegbar — kein Problem
      }
    };
    syncAccent();
  }, [accentColor.value]);

  return null;
}
