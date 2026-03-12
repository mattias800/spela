import { useEffect } from "react";

export function useHotkey(
  key: string,
  callback: () => void,
  options?: { meta?: boolean; ctrl?: boolean },
) {
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key.toLowerCase() !== key.toLowerCase()) return;
      const needsMeta = options?.meta ?? false;
      const needsCtrl = options?.ctrl ?? false;

      // When both meta and ctrl are requested, accept either modifier.
      // This supports Cmd+K on Mac and Ctrl+K on Windows/Linux.
      if (needsMeta && needsCtrl) {
        if (!e.metaKey && !e.ctrlKey) return;
      } else {
        if (needsMeta && !e.metaKey) return;
        if (needsCtrl && !e.ctrlKey) return;
      }

      e.preventDefault();
      callback();
    };

    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [key, callback, options?.meta, options?.ctrl]);
}
