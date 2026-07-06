import { useEffect, useRef } from "react";
import { useLauncherThemeStore, LAUNCHER_THEMES } from "../store/launcher-theme-store";
import { useThemeStore, ACCENT_COLORS } from "../store/useThemeStore";
import { useBackgroundEffectStore } from "../store/background-effect-store";

export function useLauncherTheme() {
  const debugFlag = true;

  const {
    selectedThemeId,
    openedAdventDoors,
    originalAccentColor,
    selectTheme,
    markAdventDoorOpened,
    isThemeUnlocked,
    setOriginalAccentColor,
    getSelectedTheme,
  } = useLauncherThemeStore();

  const { accentColor, setAccentColor } = useThemeStore();
  const { setCurrentEffect } = useBackgroundEffectStore();
  const isApplyingTheme = useRef(false);

  useEffect(() => {
    if (isApplyingTheme.current) return;

    const selectedTheme = getSelectedTheme();

    if (selectedTheme) {
      if (!originalAccentColor) {
        setOriginalAccentColor(accentColor);
      }

      if (accentColor.value !== selectedTheme.accentColor.value) {
        isApplyingTheme.current = true;
        setAccentColor(selectedTheme.accentColor);
        setTimeout(() => { isApplyingTheme.current = false; }, 100);
      }

      setCurrentEffect(selectedTheme.backgroundEffect);

      // Inject theme-specific CSS variables
      const root = document.documentElement;
      if (selectedTheme.cssVars) {
        for (const [key, val] of Object.entries(selectedTheme.cssVars)) {
          root.style.setProperty(key, val);
        }
      }
      root.style.setProperty("--theme-accent", selectedTheme.accentColor.value);
      root.style.setProperty("--theme-accent-shadow", selectedTheme.accentColor.shadowValue);
      root.setAttribute("data-theme", selectedTheme.id);
    } else {
      if (originalAccentColor) {
        const presetColor = Object.values(ACCENT_COLORS).find(
          (c) => c.value === originalAccentColor.value
        );
        if (presetColor || originalAccentColor.isCustom) {
          isApplyingTheme.current = true;
          setAccentColor(originalAccentColor);
          setTimeout(() => { isApplyingTheme.current = false; }, 100);
        }
        setOriginalAccentColor(null);
      }
      // Remove theme CSS variables
      const root = document.documentElement;
      root.style.removeProperty("--theme-glow");
      root.style.removeProperty("--theme-border");
      root.style.removeProperty("--theme-accent");
      root.style.removeProperty("--theme-accent-shadow");
      root.removeAttribute("data-theme");
    }
  }, [selectedThemeId, originalAccentColor, accentColor.value, setAccentColor, setOriginalAccentColor, getSelectedTheme, setCurrentEffect]);

  const toggleTheme = (themeId: string) => {
    if (selectedThemeId === themeId) {
      selectTheme(null);
    } else {
      if (debugFlag || isThemeUnlocked(themeId)) {
        selectTheme(themeId);
      }
    }
  };

  return {
    selectedThemeId,
    selectedTheme: getSelectedTheme(),
    openedAdventDoors,
    isThemeActive: selectedThemeId !== null,
    themes: LAUNCHER_THEMES,
    toggleTheme,
    markAdventDoorOpened,
    isThemeUnlocked,
  };
}
