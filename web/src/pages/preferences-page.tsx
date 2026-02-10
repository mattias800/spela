import { useState } from "react";
import { ShaderPreviewModal } from "@/components/shader-preview-modal";
import { EmulationSettingsCard } from "@/components/preferences/emulation-settings-card";
import { VideoFiltersCard } from "@/components/preferences/video-filters-card";
import { DevicesCard } from "@/components/preferences/devices-card";
import { DeleteDeviceModal } from "@/components/preferences/delete-device-modal";
import { useUserPreferences, useUpdatePreferences } from "@/hooks/use-preferences";
import { useDevices, useUpdateDevice, useDeleteDevice, useUpdateDevicePreferences } from "@/hooks/use-devices";
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
  const [previewModal, setPreviewModal] = useState<{ consoleId: string; shader: string } | null>(null);

  function handleToggle(key: "showPerformanceOverlay" | "autoSaveEnabled" | "autoLoadSaveEnabled") {
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

  function handleDeviceShaderChange(deviceId: number, consoleId: string, shader: string) {
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
        onPreview={(consoleId, shader) => setPreviewModal({ consoleId, shader })}
      />

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

      <ShaderPreviewModal
        open={!!previewModal}
        onClose={() => setPreviewModal(null)}
        imageUrl={previewModal ? `/api/consoles/${previewModal.consoleId}/preview-screenshot` : ""}
        shader={previewModal?.shader ?? "none"}
      />
    </div>
  );
}
