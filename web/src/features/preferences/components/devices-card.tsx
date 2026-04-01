import { useState } from "react";
import {
  SlidersHorizontal,
  Trash2,
  Pencil,
  Check,
  X,
  ChevronDown,
  ChevronRight,
} from "lucide-react";
import {
  Section,
  Badge,
  EmptyState,
  Select,
  Skeleton,
} from "@/components/ui";
import { formatRelativeTime } from "@/lib/format";
import { getPlatformIcon } from "@/lib/platform-icons";
import { SHADER_OPTIONS } from "@/lib/shader-constants";
import type { Device, Console } from "@/types/api";

interface DevicesCardProps {
  devices: Device[] | undefined;
  consoles: Console[] | undefined;
  isLoading: boolean;
  onDelete: (device: Device) => void;
  onRename: (id: number, name: string) => void;
  onDeviceShaderChange: (
    deviceId: number,
    consoleId: string,
    shader: string,
  ) => void;
}

export function DevicesCard({
  devices,
  consoles,
  isLoading,
  onDelete,
  onRename,
  onDeviceShaderChange,
}: DevicesCardProps) {
  const [editingDeviceId, setEditingDeviceId] = useState<number | null>(null);
  const [editName, setEditName] = useState("");
  const [expandedDeviceId, setExpandedDeviceId] = useState<number | null>(null);

  function startEditDevice(device: Device) {
    setEditingDeviceId(device.id);
    setEditName(device.name);
  }

  function saveDeviceName() {
    if (editingDeviceId === null || !editName.trim()) return;
    onRename(editingDeviceId, editName.trim());
    setEditingDeviceId(null);
  }

  return (
    <Section>
      <div className="px-5 pt-5 pb-2">
        <h2 className="text-lg font-semibold text-surface-100">Devices</h2>
      </div>
      <div className="px-5 pb-5">
        {isLoading ? (
          <div className="space-y-3">
            {Array.from({ length: 2 }, (_, i) => (
              <Skeleton key={i} className="h-16 w-full rounded-lg" />
            ))}
          </div>
        ) : !devices || devices.length === 0 ? (
          <EmptyState
            icon={SlidersHorizontal}
            title="No devices registered"
            description="Connect with the Spela player app to register a device."
          />
        ) : (
          <div className="space-y-2">
            {devices.map((device) => {
              const PlatformIcon = getPlatformIcon(device.platform);
              const isExpanded = expandedDeviceId === device.id;
              const isEditing = editingDeviceId === device.id;

              return (
                <div
                  key={device.id}
                  className="rounded-xl border border-surface-800 bg-surface-900/50"
                >
                  <div className="flex items-center gap-3 px-4 py-3">
                    <div className="h-9 w-9 rounded-lg bg-surface-800 flex items-center justify-center">
                      <PlatformIcon className="h-4 w-4 text-surface-400" />
                    </div>
                    <div className="flex-1 min-w-0">
                      {isEditing ? (
                        <div className="flex items-center gap-2">
                          <input
                            className="flex-1 rounded-md bg-surface-800 border border-surface-600 px-2 py-1 text-sm text-surface-100 focus:outline-none focus:ring-2 focus:ring-brand-500/40"
                            value={editName}
                            onChange={(e) => setEditName(e.target.value)}
                            onKeyDown={(e) => {
                              if (e.key === "Enter") saveDeviceName();
                              if (e.key === "Escape") setEditingDeviceId(null);
                            }}
                            autoFocus
                          />
                          <button
                            onClick={saveDeviceName}
                            className="p-1 text-brand-400 hover:text-brand-300"
                            aria-label="Save name"
                          >
                            <Check className="h-4 w-4" />
                          </button>
                          <button
                            onClick={() => setEditingDeviceId(null)}
                            className="p-1 text-surface-400 hover:text-surface-300"
                            aria-label="Cancel editing"
                          >
                            <X className="h-4 w-4" />
                          </button>
                        </div>
                      ) : (
                        <div>
                          <p className="text-sm font-medium text-surface-200 truncate">
                            {device.name}
                          </p>
                          <div className="flex items-center gap-2">
                            <Badge variant="default">{device.platform}</Badge>
                            <span className="text-xs text-surface-500">
                              Last seen {formatRelativeTime(device.lastSeenAt)}
                            </span>
                          </div>
                        </div>
                      )}
                    </div>
                    <div className="flex items-center gap-1">
                      {!isEditing && (
                        <>
                          <button
                            onClick={() => startEditDevice(device)}
                            className="p-1.5 rounded-lg text-surface-400 hover:text-surface-100 hover:bg-surface-800 transition-colors"
                            title="Rename device"
                            aria-label="Rename device"
                          >
                            <Pencil className="h-4 w-4" />
                          </button>
                          <button
                            onClick={() =>
                              setExpandedDeviceId(isExpanded ? null : device.id)
                            }
                            className="p-1.5 rounded-lg text-surface-400 hover:text-surface-100 hover:bg-surface-800 transition-colors"
                            title="Shader overrides"
                            aria-label="Shader overrides"
                          >
                            {isExpanded ? (
                              <ChevronDown className="h-4 w-4" />
                            ) : (
                              <ChevronRight className="h-4 w-4" />
                            )}
                          </button>
                          <button
                            onClick={() => onDelete(device)}
                            className="p-1.5 rounded-lg text-surface-400 hover:text-danger-400 hover:bg-surface-800 transition-colors"
                            title="Delete device"
                            aria-label="Delete device"
                          >
                            <Trash2 className="h-4 w-4" />
                          </button>
                        </>
                      )}
                    </div>
                  </div>

                  {isExpanded && consoles && consoles.length > 0 && (
                    <div className="border-t border-surface-800 px-4 py-3">
                      <p className="text-xs font-semibold uppercase tracking-wider text-surface-400 mb-2">
                        Device Shader Overrides
                      </p>
                      <div className="space-y-2">
                        {consoles.map((console) => (
                          <div
                            key={console.id}
                            className="flex items-center justify-between gap-4"
                          >
                            <span className="text-sm text-surface-300">
                              {console.name}
                            </span>
                            <Select
                              value={device.consoleShaders[console.id] ?? ""}
                              onChange={(e) =>
                                onDeviceShaderChange(
                                  device.id,
                                  console.id,
                                  e.target.value,
                                )
                              }
                              options={[
                                { value: "", label: "Use account default" },
                                ...SHADER_OPTIONS,
                              ]}
                            />
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>
    </Section>
  );
}
