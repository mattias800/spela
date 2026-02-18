import { useState } from "react";
import { Link } from "react-router-dom";
import { ShaderPreviewModal } from "@/components/shader-preview-modal";
import { ThemeCard } from "@/features/preferences/components/theme-card";
import { EmulationSettingsCard } from "@/features/preferences/components/emulation-settings-card";
import { VideoFiltersCard } from "@/features/preferences/components/video-filters-card";
import { KeyMappingCard } from "@/features/preferences/components/key-mapping-card";
import { DevicesCard } from "@/features/preferences/components/devices-card";
import { DeleteDeviceModal } from "@/features/preferences/components/delete-device-modal";
import { RetroAchievementsCard } from "@/features/preferences/components/retroachievements-card";
import {
  useUserPreferences,
  useUpdatePreferences,
} from "@/hooks/use-preferences";
import {
  useDevices,
  useUpdateDevice,
  useDeleteDevice,
  useUpdateDevicePreferences,
} from "@/hooks/use-devices";
import { useConsoles } from "@/hooks/use-consoles";
import { useToast } from "@/components/ui";
import type { Device } from "@/types/api";

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
  const [previewModal, setPreviewModal] = useState<{
    consoleId: string;
    shader: string;
  } | null>(null);

  function handleToggle(
    key: "showPerformanceOverlay" | "autoSaveEnabled" | "autoLoadSaveEnabled",
  ) {
    if (!preferences) return;
    updatePreferences.mutate(
      { [key]: !preferences[key] },
      { onError: (err) => toast("error", err.message) },
    );
  }

  function handleShaderChange(shader: string) {
    updatePreferences.mutate(
      { selectedShader: shader },
      { onError: (err) => toast("error", err.message) },
    );
  }

  function handleConsoleShaderChange(consoleId: string, shader: string) {
    updatePreferences.mutate(
      { consoleShaders: { [consoleId]: shader } },
      { onError: (err) => toast("error", err.message) },
    );
  }

  function handleRenameDevice(id: number, name: string) {
    updateDevice.mutate(
      { id, name },
      {
        onSuccess: () => toast("success", "Device renamed"),
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
      },
      onError: (err) => toast("error", err.message),
    });
  }

  function handleKeyMappingChange(mapping: string) {
    updatePreferences.mutate(
      { selectedKeyMapping: mapping },
      { onError: (err) => toast("error", err.message) },
    );
  }

  function handleCustomKeyMappingChange(customMapping: Record<string, string>) {
    updatePreferences.mutate(
      { selectedKeyMapping: "custom", customKeyMapping: customMapping },
      { onError: (err) => toast("error", err.message) },
    );
  }

  function handleConsoleKeyMappingChange(consoleId: string, mapping: string) {
    updatePreferences.mutate(
      { consoleKeyMappings: { [consoleId]: { selectedMapping: mapping } } },
      { onError: (err) => toast("error", err.message) },
    );
  }

  function handleThemeChange(theme: string) {
    updatePreferences.mutate(
      { selectedTheme: theme },
      { onError: (err) => toast("error", err.message) },
    );
  }

  function handleDeviceShaderChange(
    deviceId: number,
    consoleId: string,
    shader: string,
  ) {
    updateDevicePrefs.mutate(
      { id: deviceId, consoleShaders: { [consoleId]: shader } },
      { onError: (err) => toast("error", err.message) },
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold text-surface-100">Preferences</h1>
        <p className="mt-1 text-surface-400">
          Manage your emulation settings and devices.
        </p>
      </div>

      <ThemeCard
        selectedTheme={preferences?.selectedTheme}
        isLoading={prefsLoading}
        onThemeChange={handleThemeChange}
      />

      <EmulationSettingsCard
        preferences={preferences}
        isLoading={prefsLoading}
        onToggle={handleToggle}
      />

      <VideoFiltersCard
        preferences={preferences}
        consoles={consoles}
        isLoading={prefsLoading}
        onShaderChange={handleShaderChange}
        onConsoleShaderChange={handleConsoleShaderChange}
        onPreview={(consoleId, shader) =>
          setPreviewModal({ consoleId, shader })
        }
      />

      <KeyMappingCard
        preferences={preferences}
        consoles={consoles}
        isLoading={prefsLoading}
        onKeyMappingChange={handleKeyMappingChange}
        onCustomKeyMappingChange={handleCustomKeyMappingChange}
        onConsoleKeyMappingChange={handleConsoleKeyMappingChange}
      />

      <RetroAchievementsCard />

      <DevicesCard
        devices={devices}
        consoles={consoles}
        isLoading={devicesLoading}
        onDelete={setDeleteTarget}
        onRename={handleRenameDevice}
        onDeviceShaderChange={handleDeviceShaderChange}
      />

      <DeleteDeviceModal
        device={deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={handleDeleteDevice}
        isDeleting={deleteDevice.isPending}
      />

      <div className="pt-2 pb-4 text-center">
        <Link
          to="/licenses"
          className="text-sm text-surface-500 hover:text-surface-300 transition-colors"
        >
          Credits & Licenses
        </Link>
      </div>

      <ShaderPreviewModal
        open={!!previewModal}
        onClose={() => setPreviewModal(null)}
        imageUrl={
          previewModal
            ? `/api/consoles/${previewModal.consoleId}/preview-screenshot`
            : ""
        }
        shader={previewModal?.shader ?? "none"}
      />
    </div>
  );
}
