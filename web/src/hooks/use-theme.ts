import { useEffect } from "react";
import { useUserPreferences } from "./use-preferences";
import { THEME_OPTIONS } from "@/lib/theme-constants";

export function useTheme() {
  const { data: preferences } = useUserPreferences();
  const theme = preferences?.selectedTheme ?? "default-dark";

  useEffect(() => {
    const htmlEl = document.documentElement;
    htmlEl.setAttribute("data-theme", theme);

    const themeOption = THEME_OPTIONS.find((t) => t.value === theme);
    if (themeOption) {
      htmlEl.style.colorScheme = themeOption.colorScheme;
    }
  }, [theme]);

  return theme;
}
