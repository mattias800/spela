import { Eye } from "lucide-react";
import { ShaderPreview } from "@/components/shader-preview";
import {
  Card,
  CardHeader,
  CardContent,
  Select,
  Skeleton,
} from "@/components/ui";
import { SHADER_OPTIONS } from "@/lib/shader-constants";
import type { UserPreferences, Console } from "@/types/api";

interface VideoFiltersCardProps {
  preferences: UserPreferences | undefined;
  consoles: Console[] | undefined;
  isLoading: boolean;
  onShaderChange: (shader: string) => void;
  onConsoleShaderChange: (consoleId: string, shader: string) => void;
  onPreview: (consoleId: string, shader: string) => void;
}

export function VideoFiltersCard({
  preferences,
  consoles,
  isLoading,
  onShaderChange,
  onConsoleShaderChange,
  onPreview,
}: VideoFiltersCardProps) {
  return (
    <Card>
      <CardHeader>
        <h2 className="text-lg font-semibold text-surface-100">
          Video Filters
        </h2>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <Skeleton className="h-10 w-64 rounded-lg" />
        ) : (
          <div className="space-y-6">
            <Select
              label="Global Default Shader"
              options={SHADER_OPTIONS}
              value={preferences?.selectedShader ?? "none"}
              onChange={(e) => onShaderChange(e.target.value)}
            />

            {consoles && consoles.length > 0 && (
              <div className="mt-4">
                <p className="text-sm font-medium text-surface-300 mb-2">
                  Preview
                </p>
                <ShaderPreview
                  imageUrl={`/api/consoles/${consoles[0].id}/preview-screenshot`}
                  shader={preferences?.selectedShader ?? "none"}
                  onClick={() =>
                    consoles[0] &&
                    onPreview(
                      consoles[0].id,
                      preferences?.selectedShader ?? "none",
                    )
                  }
                  className="max-w-sm"
                />
              </div>
            )}

            {consoles && consoles.length > 0 && (
              <div>
                <p className="text-sm font-medium text-surface-300 mb-3">
                  Per-Console Overrides
                </p>
                <div className="overflow-x-auto">
                  <table className="w-full">
                    <thead>
                      <tr className="border-b border-surface-800">
                        <th className="text-left px-3 py-2 text-xs font-semibold uppercase tracking-wider text-surface-400">
                          Console
                        </th>
                        <th className="text-left px-3 py-2 text-xs font-semibold uppercase tracking-wider text-surface-400">
                          Shader
                        </th>
                        <th className="text-left px-3 py-2 text-xs font-semibold uppercase tracking-wider text-surface-400">
                          Preview
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {consoles.map((console) => (
                        <tr
                          key={console.id}
                          className="border-b border-surface-800/50"
                        >
                          <td className="px-3 py-2 text-sm text-surface-200">
                            {console.name}
                          </td>
                          <td className="px-3 py-2">
                            <Select
                              value={
                                preferences?.consoleShaders[console.id] ?? ""
                              }
                              onChange={(e) =>
                                onConsoleShaderChange(
                                  console.id,
                                  e.target.value,
                                )
                              }
                              options={[
                                { value: "", label: "Use global default" },
                                ...SHADER_OPTIONS,
                              ]}
                            />
                          </td>
                          <td className="px-3 py-2">
                            <button
                              onClick={() =>
                                onPreview(
                                  console.id,
                                  preferences?.consoleShaders[console.id] ||
                                    preferences?.selectedShader ||
                                    "none",
                                )
                              }
                              className="p-1.5 rounded-lg text-surface-400 hover:text-brand-400 hover:bg-surface-800 transition-colors"
                              title="Preview shader"
                            >
                              <Eye className="h-4 w-4" />
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
