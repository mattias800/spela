import {
  Section,
  Skeleton,
  Switch,
} from "@/components/ui";
import type { UserPreferences } from "@/types/api";

type EmulationToggleKey =
  | "showPerformanceOverlay"
  | "autoSaveEnabled"
  | "autoLoadSaveEnabled"
  | "autoUpdateCoresEnabled";

interface EmulationSettingsCardProps {
  preferences: UserPreferences | undefined;
  isLoading: boolean;
  isSaving?: boolean;
  onToggle: (key: EmulationToggleKey) => void;
}

const TOGGLES: Array<{
  key: EmulationToggleKey;
  label: string;
  description: string;
}> = [
  {
    key: "showPerformanceOverlay",
    label: "Performance Overlay",
    description: "Show FPS and frame timing during gameplay",
  },
  {
    key: "autoSaveEnabled",
    label: "Auto Save",
    description: "Automatically save state when exiting a game",
  },
  {
    key: "autoLoadSaveEnabled",
    label: "Auto Load Save",
    description:
      "Automatically load the latest save state when starting a game",
  },
  {
    key: "autoUpdateCoresEnabled",
    // Spec keys core_upd.settings.auto_update_label / _desc — aligned
    // with the #672 core-upgrade decision tone (see
    // design-proposals/core-upgrade-decision-spec.md §"Copy library").
    // The old subtitle was technical; the new copy makes clear what
    // "off" actually means for the user.
    label: "Automatically update cores",
    description: "When off, we'll only switch cores when you say so.",
  },
];

export function EmulationSettingsCard({
  preferences,
  isLoading,
  isSaving,
  onToggle,
}: EmulationSettingsCardProps) {
  return (
    <Section>
      <div className="px-5 pt-5 pb-2">
        <h2 className="text-lg font-semibold text-surface-100">
          Emulation Settings
        </h2>
      </div>
      <div className="px-5 pb-5">
        {isLoading ? (
          <div className="space-y-4">
            {Array.from({ length: 3 }, (_, i) => (
              <Skeleton key={i} className="h-12 w-full rounded-lg" />
            ))}
          </div>
        ) : (
          <div className="space-y-1">
            {TOGGLES.map(({ key, label, description }) => (
              <label
                key={key}
                className="flex items-center justify-between py-3 cursor-pointer group"
              >
                <div>
                  <p className="text-sm font-medium text-surface-200 group-hover:text-surface-100 transition-colors">
                    {label}
                  </p>
                  <p className="text-xs text-surface-500">{description}</p>
                </div>
                <Switch
                  checked={preferences?.[key] ?? false}
                  onChange={() => onToggle(key)}
                  disabled={isSaving}
                />
              </label>
            ))}
          </div>
        )}
      </div>
    </Section>
  );
}
