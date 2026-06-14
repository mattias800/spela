import { useState } from "react";
import { Link } from "react-router-dom";
import { PageLayout, SectionList } from "@/components/layout";
import { ShaderPreviewModal } from "@/components/shader-preview-modal";
import { ThemeCard } from "@/features/preferences/components/theme-card";
import { EmulationSettingsCard } from "@/features/preferences/components/emulation-settings-card";
import { VideoFiltersCard } from "@/features/preferences/components/video-filters-card";
import { KeyMappingCard } from "@/features/preferences/components/key-mapping-card";
import { DevicesCard } from "@/features/preferences/components/devices-card";
import { DeleteDeviceModal } from "@/features/preferences/components/delete-device-modal";
import { RetroAchievementsCard } from "@/features/preferences/components/retroachievements-card";
import { StateTabNav, StateTabItem } from "@/components/ui/tab-nav";
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

type PreferencesTab = "general" | "emulation" | "video" | "controls" | "achievements" | "devices";

export function PreferencesPage() {
  const { data: preferences, isLoading: prefsLoading } = useUserPreferences();
  const updatePreferences = useUpdatePreferences();
  const { data: devices, isLoading: devicesLoading } = useDevices();
  const { data: consoles } = useConsoles();
  const updateDevice = useUpdateDevice();
  const deleteDevice = useDeleteDevice();
  const updateDevicePrefs = useUpdateDevicePreferences();
  const { toast } = useToast();

  const [activeTab, setActiveTab] = useState<PreferencesTab>("general");
  const [deleteTarget, setDeleteTarget] = useState<Device | null>(null);
  const [previewModal, setPreviewModal] = useState<{
    consoleId: string;
    shader: string;
  } | null>(null);

  function handleToggle(
    key:
      | "showPerformanceOverlay"
      | "autoSaveEnabled"
      | "autoLoadSaveEnabled"
      | "autoUpdateCoresEnabled",
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
      { consoleKeyMappings: { [consoleId]: { selectedMapping: mapping, customMapping: {}, positionMappings: {} } } },
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
    <PageLayout title="Preferences" subtitle="Manage your emulation settings and devices.">
      <SectionList>
      <StateTabNav>
        <StateTabItem active={activeTab === "general"} onClick={() => setActiveTab("general")}>
          General
        </StateTabItem>
        <StateTabItem active={activeTab === "emulation"} onClick={() => setActiveTab("emulation")}>
          Emulation
        </StateTabItem>
        <StateTabItem active={activeTab === "video"} onClick={() => setActiveTab("video")}>
          Video Filters
        </StateTabItem>
        <StateTabItem active={activeTab === "controls"} onClick={() => setActiveTab("controls")}>
          Controls
        </StateTabItem>
        <StateTabItem active={activeTab === "achievements"} onClick={() => setActiveTab("achievements")}>
          Achievements
        </StateTabItem>
        <StateTabItem active={activeTab === "devices"} onClick={() => setActiveTab("devices")}>
          Devices
        </StateTabItem>
      </StateTabNav>

      {activeTab === "general" && (
        <ThemeCard
          selectedTheme={preferences?.selectedTheme}
          isLoading={prefsLoading}
          onThemeChange={handleThemeChange}
        />
      )}

      {activeTab === "emulation" && (
        <EmulationSettingsCard
          preferences={preferences}
          isLoading={prefsLoading}
          isSaving={updatePreferences.isPending}
          onToggle={handleToggle}
        />
      )}

      {activeTab === "video" && (
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
      )}

      {activeTab === "controls" && (
        <KeyMappingCard
          preferences={preferences}
          consoles={consoles}
          isLoading={prefsLoading}
          onKeyMappingChange={handleKeyMappingChange}
          onCustomKeyMappingChange={handleCustomKeyMappingChange}
          onConsoleKeyMappingChange={handleConsoleKeyMappingChange}
        />
      )}

      {activeTab === "achievements" && (
        <RetroAchievementsCard />
      )}

      {activeTab === "devices" && (
        <>
          <DevicesCard
            devices={devices ?? undefined}
            consoles={consoles}
            isLoading={devicesLoading}
            onDelete={setDeleteTarget}
            onRename={handleRenameDevice}
            onDeviceShaderChange={handleDeviceShaderChange}
          />

          <div className="pt-2 pb-4 text-center">
            <Link
              to="/licenses"
              className="text-sm text-surface-500 hover:text-surface-300 transition-colors"
            >
              Credits & Licenses
            </Link>
          </div>
        </>
      )}

      <DeleteDeviceModal
        device={deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={handleDeleteDevice}
        isDeleting={deleteDevice.isPending}
      />

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
    </SectionList>
    </PageLayout>
  );
}

export default PreferencesPage;
