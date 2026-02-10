import { useState } from "react";
import {
  SlidersHorizontal,
  Monitor,
  Smartphone,
  Laptop,
  Trash2,
  Pencil,
  Check,
  X,
  ChevronDown,
  ChevronRight,
} from "lucide-react";
import { Card, CardHeader, CardContent, Button, Select, Modal, Badge, EmptyState, Skeleton } from "@/components/ui";
import { useUserPreferences, useUpdatePreferences } from "@/hooks/use-preferences";
import { useDevices, useUpdateDevice, useDeleteDevice, useUpdateDevicePreferences } from "@/hooks/use-devices";
import { useConsoles } from "@/hooks/use-consoles";
import { useToast } from "@/components/ui";
import { formatRelativeTime } from "@/lib/format";
import type { Device } from "@/types/api";

const SHADER_OPTIONS: Array<{ value: string; label: string }> = [
  { value: "none", label: "None" },
  { value: "bilinear", label: "Bilinear" },
  { value: "sharp-bilinear", label: "Sharp Bilinear" },
  { value: "crt-simple", label: "CRT Simple" },
  { value: "lcd-grid", label: "LCD Grid" },
  { value: "scanlines", label: "Scanlines" },
];

function getPlatformIcon(platform: string) {
  switch (platform.toLowerCase()) {
    case "android":
      return Smartphone;
    case "macos":
    case "linux":
    case "windows":
      return Laptop;
    default:
      return Monitor;
  }
}

export function PreferencesPage() {
  const { data: preferences, isLoading: prefsLoading } = useUserPreferences();
  const updatePreferences = useUpdatePreferences();
  const { data: devices, isLoading: devicesLoading } = useDevices();
  const { data: consoles } = useConsoles();
  const updateDevice = useUpdateDevice();
  const deleteDevice = useDeleteDevice();
  const updateDevicePrefs = useUpdateDevicePreferences();
  const { toast } = useToast();

  const [deleteTarget, setDeleteTarget] = useState<Device | null>(null);
  const [editingDeviceId, setEditingDeviceId] = useState<number | null>(null);
  const [editName, setEditName] = useState("");
  const [expandedDeviceId, setExpandedDeviceId] = useState<number | null>(null);

  function handleToggle(key: "showPerformanceOverlay" | "autoSaveEnabled" | "autoLoadSaveEnabled") {
    if (!preferences) return;
    updatePreferences.mutate(
      { [key]: !preferences[key] },
      {
        onError: (err) => toast("error", err.message),
      },
    );
  }

  function handleShaderChange(shader: string) {
    updatePreferences.mutate(
      { selectedShader: shader },
      {
        onError: (err) => toast("error", err.message),
      },
    );
  }

  function handleConsoleShaderChange(consoleId: string, shader: string) {
    updatePreferences.mutate(
      { consoleShaders: { [consoleId]: shader } },
      {
        onError: (err) => toast("error", err.message),
      },
    );
  }

  function startEditDevice(device: Device) {
    setEditingDeviceId(device.id);
    setEditName(device.name);
  }

  function saveDeviceName() {
    if (editingDeviceId === null || !editName.trim()) return;
    updateDevice.mutate(
      { id: editingDeviceId, name: editName.trim() },
      {
        onSuccess: () => {
          toast("success", "Device renamed");
          setEditingDeviceId(null);
        },
        onError: (err) => toast("error", err.message),
      },
    );
  }

  function handleDeleteDevice() {
    if (!deleteTarget) return;
    deleteDevice.mutate(deleteTarget.id, {
      onSuccess: () => {
        toast("success", "Device deleted");
        setDeleteTarget(null);
        if (expandedDeviceId === deleteTarget.id) setExpandedDeviceId(null);
      },
      onError: (err) => toast("error", err.message),
    });
  }

  function handleDeviceShaderChange(deviceId: number, consoleId: string, shader: string) {
    updateDevicePrefs.mutate(
      { id: deviceId, consoleShaders: { [consoleId]: shader } },
      {
        onError: (err) => toast("error", err.message),
      },
    );
  }

  const toggles: Array<{ key: "showPerformanceOverlay" | "autoSaveEnabled" | "autoLoadSaveEnabled"; label: string; description: string }> = [
    { key: "showPerformanceOverlay", label: "Performance Overlay", description: "Show FPS and frame timing during gameplay" },
    { key: "autoSaveEnabled", label: "Auto Save", description: "Automatically save state when exiting a game" },
    { key: "autoLoadSaveEnabled", label: "Auto Load Save", description: "Automatically load the latest save state when starting a game" },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold text-surface-100">Preferences</h1>
        <p className="mt-1 text-surface-400">
          Manage your emulation settings and devices.
        </p>
      </div>

      {/* Emulation Settings */}
      <Card>
        <CardHeader>
          <h2 className="text-lg font-semibold text-surface-100">Emulation Settings</h2>
        </CardHeader>
        <CardContent>
          {prefsLoading ? (
            <div className="space-y-4">
              {Array.from({ length: 3 }, (_, i) => (
                <Skeleton key={i} className="h-12 w-full rounded-lg" />
              ))}
            </div>
          ) : (
            <div className="space-y-1">
              {toggles.map(({ key, label, description }) => (
                <label key={key} className="flex items-center justify-between py-3 cursor-pointer group">
                  <div>
                    <p className="text-sm font-medium text-surface-200 group-hover:text-surface-100 transition-colors">{label}</p>
                    <p className="text-xs text-surface-500">{description}</p>
                  </div>
                  <button
                    role="switch"
                    aria-checked={preferences?.[key] ?? false}
                    onClick={() => handleToggle(key)}
                    className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ${
                      preferences?.[key] ? "bg-brand-500" : "bg-surface-700"
                    }`}
                  >
                    <span
                      className={`pointer-events-none inline-block h-5 w-5 rounded-full bg-white shadow-lg transform transition-transform duration-200 ${
                        preferences?.[key] ? "translate-x-5" : "translate-x-0"
                      }`}
                    />
                  </button>
                </label>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Video Filters */}
      <Card>
        <CardHeader>
          <h2 className="text-lg font-semibold text-surface-100">Video Filters</h2>
        </CardHeader>
        <CardContent>
          {prefsLoading ? (
            <Skeleton className="h-10 w-64 rounded-lg" />
          ) : (
            <div className="space-y-6">
              <Select
                label="Global Default Shader"
                options={SHADER_OPTIONS}
                value={preferences?.selectedShader ?? "none"}
                onChange={(e) => handleShaderChange(e.target.value)}
              />

              {consoles && consoles.length > 0 && (
                <div>
                  <p className="text-sm font-medium text-surface-300 mb-3">Per-Console Overrides</p>
                  <div className="overflow-x-auto">
                    <table className="w-full">
                      <thead>
                        <tr className="border-b border-surface-800">
                          <th className="text-left px-3 py-2 text-xs font-semibold uppercase tracking-wider text-surface-400">Console</th>
                          <th className="text-left px-3 py-2 text-xs font-semibold uppercase tracking-wider text-surface-400">Shader</th>
                        </tr>
                      </thead>
                      <tbody>
                        {consoles.map((console) => (
                          <tr key={console.id} className="border-b border-surface-800/50">
                            <td className="px-3 py-2 text-sm text-surface-200">{console.name}</td>
                            <td className="px-3 py-2">
                              <select
                                className="rounded-lg bg-surface-900 border border-surface-700 px-2.5 py-1.5 text-sm text-surface-100 focus:outline-none focus:ring-2 focus:ring-brand-500/40 focus:border-brand-500"
                                value={preferences?.consoleShaders[console.id] ?? ""}
                                onChange={(e) => handleConsoleShaderChange(console.id, e.target.value)}
                              >
                                <option value="">Use global default</option>
                                {SHADER_OPTIONS.map((opt) => (
                                  <option key={opt.value} value={opt.value}>{opt.label}</option>
                                ))}
                              </select>
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

      {/* Devices */}
      <Card>
        <CardHeader>
          <h2 className="text-lg font-semibold text-surface-100">Devices</h2>
        </CardHeader>
        <CardContent>
          {devicesLoading ? (
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
                  <div key={device.id} className="rounded-xl border border-surface-800 bg-surface-900/50">
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
                            <button onClick={saveDeviceName} className="p-1 text-brand-400 hover:text-brand-300">
                              <Check className="h-4 w-4" />
                            </button>
                            <button onClick={() => setEditingDeviceId(null)} className="p-1 text-surface-400 hover:text-surface-300">
                              <X className="h-4 w-4" />
                            </button>
                          </div>
                        ) : (
                          <div>
                            <p className="text-sm font-medium text-surface-200 truncate">{device.name}</p>
                            <div className="flex items-center gap-2">
                              <Badge variant="default">{device.platform}</Badge>
                              <span className="text-xs text-surface-500">Last seen {formatRelativeTime(device.lastSeenAt)}</span>
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
                            >
                              <Pencil className="h-4 w-4" />
                            </button>
                            <button
                              onClick={() => setExpandedDeviceId(isExpanded ? null : device.id)}
                              className="p-1.5 rounded-lg text-surface-400 hover:text-surface-100 hover:bg-surface-800 transition-colors"
                              title="Shader overrides"
                            >
                              {isExpanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
                            </button>
                            <button
                              onClick={() => setDeleteTarget(device)}
                              className="p-1.5 rounded-lg text-surface-400 hover:text-danger-400 hover:bg-surface-800 transition-colors"
                              title="Delete device"
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
                            <div key={console.id} className="flex items-center justify-between gap-4">
                              <span className="text-sm text-surface-300">{console.name}</span>
                              <select
                                className="rounded-lg bg-surface-800 border border-surface-700 px-2.5 py-1.5 text-sm text-surface-100 focus:outline-none focus:ring-2 focus:ring-brand-500/40 focus:border-brand-500"
                                value={device.consoleShaders[console.id] ?? ""}
                                onChange={(e) => handleDeviceShaderChange(device.id, console.id, e.target.value)}
                              >
                                <option value="">Use account default</option>
                                {SHADER_OPTIONS.map((opt) => (
                                  <option key={opt.value} value={opt.value}>{opt.label}</option>
                                ))}
                              </select>
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
        </CardContent>
      </Card>

      {/* Delete Device Confirmation */}
      <Modal
        open={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        title="Delete Device"
        size="sm"
      >
        <p className="text-sm text-surface-300 mb-6">
          This will remove <strong className="text-surface-100">{deleteTarget?.name}</strong> and all its shader overrides. This action cannot be undone.
        </p>
        <div className="flex justify-end gap-3">
          <Button variant="secondary" onClick={() => setDeleteTarget(null)}>
            Cancel
          </Button>
          <Button variant="danger" onClick={handleDeleteDevice} loading={deleteDevice.isPending}>
            Delete Device
          </Button>
        </div>
      </Modal>
    </div>
  );
}
