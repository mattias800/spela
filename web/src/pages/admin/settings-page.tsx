import { useState, useEffect } from "react";
import { Settings, Plus, X } from "lucide-react";
import {
  Button,
  Card,
  CardHeader,
  CardContent,
  Input,
  Switch,
} from "@/components/ui";
import {
  useServerSettings,
  useUpdateSettings,
  useIgdbStatus,
} from "@/hooks/use-admin";
import { useToast } from "@/components/ui";
import { Skeleton } from "@/components/ui";
import { IgdbConfigCard } from "@/features/admin/components/igdb-config-card";
import { IgdbWarningBanner } from "@/features/admin/components/igdb-warning-banner";

export function AdminSettingsPage() {
  const { data: settings, isLoading } = useServerSettings();
  const { data: igdbStatus } = useIgdbStatus();
  const updateSettings = useUpdateSettings();
  const { toast } = useToast();

  const [gameDirectories, setGameDirectories] = useState<string[]>([]);
  const [newDir, setNewDir] = useState("");
  const [allowRegistration, setAllowRegistration] = useState(true);
  const [scrapeOnScan, setScrapeOnScan] = useState(true);
  const [igdbClientId, setIgdbClientId] = useState("");
  const [igdbClientSecret, setIgdbClientSecret] = useState("");

  useEffect(() => {
    if (settings) {
      const dirs = settings["gameDirectories"] ?? "";
      setGameDirectories(dirs ? dirs.split(",") : []);
      setAllowRegistration(settings["registration_enabled"] !== "false");
      setScrapeOnScan(settings["scrapeOnScan"] !== "false");
      setIgdbClientId(settings["igdb_client_id"] ?? "");
      setIgdbClientSecret(settings["igdb_client_secret"] ?? "");
    }
  }, [settings]);

  function handleAddDir() {
    const dir = newDir.trim();
    if (dir && !gameDirectories.includes(dir)) {
      setGameDirectories((prev) => [...prev, dir]);
      setNewDir("");
    }
  }

  function handleRemoveDir(index: number) {
    setGameDirectories((prev) => prev.filter((_, i) => i !== index));
  }

  function handleSave() {
    const payload: Record<string, string> = {
      gameDirectories: gameDirectories.join(","),
      registration_enabled: String(allowRegistration),
      scrapeOnScan: String(scrapeOnScan),
    };
    // Only send IGDB credentials when they're managed via the UI, not env vars
    if (!igdbEnvConfigured) {
      payload.igdb_client_id = igdbClientId;
      payload.igdb_client_secret = igdbClientSecret;
    }
    updateSettings.mutate(payload, {
      onSuccess: () => toast("success", "Settings saved"),
      onError: (err) =>
        toast("error", err instanceof Error ? err.message : "Unknown error"),
    });
  }

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-64 w-full rounded-2xl" />
      </div>
    );
  }

  const igdbNotConfigured = igdbStatus && !igdbStatus.configured;
  const igdbEnvConfigured = igdbStatus?.source === "env";

  return (
    <div className="space-y-6 max-w-3xl">
      <div>
        <h1 className="text-3xl font-bold text-surface-100">Server Settings</h1>
        <p className="mt-1 text-surface-400">
          Configure server behavior and game scanning.
        </p>
      </div>

      {igdbNotConfigured && <IgdbWarningBanner variant="settings" />}

      <Card>
        <CardHeader>
          <h2 className="text-lg font-semibold text-surface-100 flex items-center gap-2">
            <Settings className="h-5 w-5 text-brand-400" />
            Game Directories
          </h2>
        </CardHeader>
        <CardContent className="space-y-3">
          {gameDirectories.map((dir, i) => (
            <div key={i} className="flex items-center gap-2">
              <code className="flex-1 px-3 py-2 rounded-lg bg-surface-800 text-sm text-surface-200 font-mono">
                {dir}
              </code>
              <button
                onClick={() => handleRemoveDir(i)}
                className="p-2 rounded-lg text-surface-400 hover:text-danger-500 hover:bg-danger-500/10 transition-colors"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
          ))}
          <div className="flex items-center gap-2">
            <Input
              placeholder="/path/to/games"
              value={newDir}
              onChange={(e) => setNewDir(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  e.preventDefault();
                  handleAddDir();
                }
              }}
              className="flex-1"
            />
            <Button variant="secondary" onClick={handleAddDir}>
              <Plus className="h-4 w-4" />
              Add
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <h2 className="text-lg font-semibold text-surface-100">General</h2>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-surface-200">
                Allow Registration
              </p>
              <p className="text-xs text-surface-500">
                Allow new users to create accounts
              </p>
            </div>
            <Switch
              checked={allowRegistration}
              onChange={setAllowRegistration}
            />
          </div>

          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-surface-200">
                Auto-scrape on Scan
              </p>
              <p className="text-xs text-surface-500">
                Automatically fetch metadata when scanning
              </p>
            </div>
            <Switch checked={scrapeOnScan} onChange={setScrapeOnScan} />
          </div>
        </CardContent>
      </Card>

      <IgdbConfigCard
        clientId={igdbClientId}
        onClientIdChange={setIgdbClientId}
        clientSecret={igdbClientSecret}
        onClientSecretChange={setIgdbClientSecret}
        envConfigured={igdbEnvConfigured}
      />

      <div className="flex justify-end">
        <Button
          onClick={handleSave}
          loading={updateSettings.isPending}
          size="lg"
        >
          Save Settings
        </Button>
      </div>
    </div>
  );
}
