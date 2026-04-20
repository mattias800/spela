import { useState, useEffect, useCallback } from "react";
import { useUserPreferences, useUpdatePreferences } from "@/hooks/use-preferences";
import { useToast } from "@/components/ui";
import type { GameFilters } from "@/types/api";

/**
 * Applies the user's preferred regions as default filters when no regions
 * are specified in the URL, and provides a callback to save new defaults.
 */
export function useDefaultRegionFilters(
  searchParams: URLSearchParams,
  setFilters: (updater: (prev: GameFilters) => GameFilters) => void,
) {
  const { data: userPrefs } = useUserPreferences();
  const updatePrefs = useUpdatePreferences();
  const { toast } = useToast();

  const [applied, setApplied] = useState(false);

  useEffect(() => {
    if (
      userPrefs?.preferredRegions?.length &&
      !applied &&
      !searchParams.get("regions")
    ) {
      setFilters((f) => ({ ...f, regions: userPrefs.preferredRegions ?? undefined }));
      setApplied(true);
    }
  }, [userPrefs, applied, searchParams, setFilters]);

  const saveDefaultRegions = useCallback(
    (regions: string[]) => {
      updatePrefs.mutate(
        { preferredRegions: regions },
        { onSuccess: () => toast("info", "Default regions saved") },
      );
    },
    [updatePrefs, toast],
  );

  return { saveDefaultRegions };
}
