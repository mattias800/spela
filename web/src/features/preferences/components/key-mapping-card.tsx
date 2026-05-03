import { Section, Select, Skeleton } from "@/components/ui";
import { cn } from "@/lib/cn";
import { ControllerVisual } from "@/features/preferences/components/controller-visual";
import { CustomKeyMappingEditor } from "@/features/preferences/components/custom-key-mapping-editor";
import {
  KEY_MAPPING_MODES,
  getPresetMapping,
  PRESET_ARROWS_LEFT,
  type KeyMappingMode,
} from "@/lib/key-mapping-constants";
import type { UserPreferences, Console, ConsoleKeyMapping } from "@/types/api";

interface KeyMappingCardProps {
  preferences: UserPreferences | undefined;
  consoles: Console[] | undefined;
  isLoading: boolean;
  onKeyMappingChange: (mapping: string) => void;
  onCustomKeyMappingChange: (customMapping: Record<string, string>) => void;
  onConsoleKeyMappingChange: (
    consoleId: string,
    mapping: string,
    customMapping?: Record<string, string>,
  ) => void;
}

export function KeyMappingCard({
  preferences,
  consoles,
  isLoading,
  onKeyMappingChange,
  onCustomKeyMappingChange,
  onConsoleKeyMappingChange,
}: KeyMappingCardProps) {
  const selectedMode = (preferences?.selectedKeyMapping ??
    "arrows-left") as KeyMappingMode;
  const customMapping = preferences?.customKeyMapping ?? {};

  const activeMapping =
    selectedMode === "custom"
      ? { ...PRESET_ARROWS_LEFT, ...customMapping }
      : getPresetMapping(selectedMode);

  return (
    <Section>
      <div className="px-5 pt-5 pb-2">
        <h2 className="text-lg font-semibold text-surface-100">Key Mapping</h2>
      </div>
      <div className="px-5 pb-5">
        {isLoading ? (
          <Skeleton className="h-10 w-64 rounded-lg" />
        ) : (
          <div className="space-y-6">
            {/* Mode selector */}
            <div>
              <p className="text-sm font-medium text-surface-300 mb-3">
                Global Default
              </p>
              <div className="flex gap-2">
                {KEY_MAPPING_MODES.map((mode) => (
                  <button
                    key={mode.value}
                    type="button"
                    onClick={() => onKeyMappingChange(mode.value)}
                    className={cn(
                      "px-4 py-2 rounded-lg text-sm font-medium transition-all",
                      selectedMode === mode.value
                        ? "bg-brand-500/20 text-brand-300 ring-2 ring-brand-400"
                        : "bg-surface-800 text-surface-300 hover:bg-surface-700 hover:text-surface-100",
                    )}
                  >
                    {mode.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Visual or editor */}
            {selectedMode === "custom" ? (
              <CustomKeyMappingEditor
                mapping={{ ...PRESET_ARROWS_LEFT, ...customMapping }}
                onChange={onCustomKeyMappingChange}
              />
            ) : (
              <ControllerVisual mapping={activeMapping} />
            )}

            {/* Per-console overrides */}
            {consoles && consoles.length > 0 && (
              <div>
                <p className="text-sm font-medium text-surface-300 mb-3">
                  Per-Console Overrides
                </p>
                <div className="overflow-x-auto">
                  <table className="w-full">
                    <thead>
                      <tr className="border-b border-surface-800">
                        <th className="text-left px-3 py-2 text-xs font-semibold uppercase tracking-wider text-secondary">
                          Console
                        </th>
                        <th className="text-left px-3 py-2 text-xs font-semibold uppercase tracking-wider text-secondary">
                          Key Mapping
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {consoles.map((console) => {
                        const consoleKM: ConsoleKeyMapping | undefined =
                          preferences?.consoleKeyMappings[console.id];
                        return (
                          <tr
                            key={console.id}
                            className="border-b border-surface-800/50"
                          >
                            <td className="px-3 py-2 text-sm text-surface-200">
                              {console.name}
                            </td>
                            <td className="px-3 py-2">
                              <Select
                                value={consoleKM?.selectedMapping ?? ""}
                                onChange={(e) =>
                                  onConsoleKeyMappingChange(
                                    console.id,
                                    e.target.value,
                                  )
                                }
                                options={[
                                  { value: "", label: "Use global default" },
                                  ...KEY_MAPPING_MODES,
                                ]}
                              />
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </Section>
  );
}
