import {
  Card,
  CardHeader,
  CardContent,
  Skeleton,
  Switch,
} from "@/components/ui";
import type { UserPreferences } from "@/types/api";

interface EmulationSettingsCardProps {
  preferences: UserPreferences | undefined;
  isLoading: boolean;
  isSaving?: boolean;
  onToggle: (
    key: "showPerformanceOverlay" | "autoSaveEnabled" | "autoLoadSaveEnabled",
  ) => void;
}

const TOGGLES: Array<{
  key: "showPerformanceOverlay" | "autoSaveEnabled" | "autoLoadSaveEnabled";
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
];

export function EmulationSettingsCard({
  preferences,
  isLoading,
  isSaving,
  onToggle,
}: EmulationSettingsCardProps) {
  return (
    <Card>
      <CardHeader>
        <h2 className="text-lg font-semibold text-surface-100">
          Emulation Settings
        </h2>
      </CardHeader>
      <CardContent>
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
      </CardContent>
    </Card>
  );
}
