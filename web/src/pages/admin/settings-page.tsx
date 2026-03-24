import { useState, useEffect } from "react";
import {
  Button,
  Card,
  CardHeader,
  CardContent,
  Select,
  Switch,
} from "@/components/ui";
import {
  useServerSettings,
  useUpdateSettings,
  useIgdbStatus,
  useSteamGridDBStatus,
  useRAStatus,
} from "@/hooks/use-admin";
import { useToast } from "@/components/ui";
import { Skeleton } from "@/components/ui";
import { IgdbConfigCard } from "@/features/admin/components/igdb-config-card";
import { IgdbWarningBanner } from "@/features/admin/components/igdb-warning-banner";
import { SteamGridDBConfigCard } from "@/features/admin/components/steamgriddb-config-card";
import { RAConfigCard } from "@/features/admin/components/ra-config-card";

export function AdminSettingsPage() {
  const { data: settings, isLoading } = useServerSettings();
  const { data: igdbStatus } = useIgdbStatus();
  const { data: steamgriddbStatus } = useSteamGridDBStatus();
  const { data: raStatus } = useRAStatus();
  const updateSettings = useUpdateSettings();
  const { toast } = useToast();

  const [allowRegistration, setAllowRegistration] = useState(true);
  const [scrapeOnScan, setScrapeOnScan] = useState(true);
  const [biosAutoDownload, setBiosAutoDownload] = useState(true);
  const [defaultRegion, setDefaultRegion] = useState("USA");
  const [hidePreReleaseDefault, setHidePreReleaseDefault] = useState(true);
  const [igdbClientId, setIgdbClientId] = useState("");
  const [igdbClientSecret, setIgdbClientSecret] = useState("");
  const [steamgriddbApiKey, setSteamgriddbApiKey] = useState("");
  const [raApiKey, setRaApiKey] = useState("");

  useEffect(() => {
    if (settings) {
      setAllowRegistration(settings["registration_enabled"] !== "false");
      setScrapeOnScan(settings["scrapeOnScan"] !== "false");
      setBiosAutoDownload(settings["bios_auto_download"] !== "false");
      setDefaultRegion(settings["default_region"] || "USA");
      setHidePreReleaseDefault(settings["hide_pre_release_default"] !== "false");
      setIgdbClientId(settings["igdb_client_id"] ?? "");
      setIgdbClientSecret(settings["igdb_client_secret"] ?? "");
      setSteamgriddbApiKey(settings["steamgriddb_api_key"] ?? "");
      setRaApiKey(settings["ra_api_key"] ?? "");
    }
  }, [settings]);

  function handleSave() {
    const payload: Record<string, string> = {
      registration_enabled: String(allowRegistration),
      scrapeOnScan: String(scrapeOnScan),
      bios_auto_download: String(biosAutoDownload),
      default_region: defaultRegion,
      hide_pre_release_default: String(hidePreReleaseDefault),
    };
    // Only send IGDB credentials when they're managed via the UI, not env vars
    if (!igdbEnvConfigured) {
      payload.igdb_client_id = igdbClientId;
      payload.igdb_client_secret = igdbClientSecret;
    }
    if (!steamgriddbEnvConfigured) {
      payload.steamgriddb_api_key = steamgriddbApiKey;
    }
    if (!raEnvConfigured) {
      payload.ra_api_key = raApiKey;
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
  const steamgriddbEnvConfigured = steamgriddbStatus?.source === "env";
  const raEnvConfigured = raStatus?.source === "env";

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

          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-surface-200">
                Auto-download BIOS on startup
              </p>
              <p className="text-xs text-surface-500">
                Automatically download missing BIOS files when the server starts
              </p>
            </div>
            <Switch
              checked={biosAutoDownload}
              onChange={setBiosAutoDownload}
            />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <h2 className="text-lg font-semibold text-surface-100">Library Defaults</h2>
          <p className="text-xs text-surface-500 mt-1">
            Server-wide defaults for game library display and scraping.
          </p>
        </CardHeader>
        <CardContent className="space-y-4">
          <Select
            id="default-region"
            label="Default Region"
            value={defaultRegion}
            onChange={(e) => setDefaultRegion(e.target.value)}
            options={[
              { value: "USA", label: "USA" },
              { value: "Europe", label: "Europe" },
              { value: "World", label: "World" },
              { value: "Japan", label: "Japan" },
            ]}
          />

          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-surface-200">
                Hide Pre-release by Default
              </p>
              <p className="text-xs text-surface-500">
                Hide betas, prototypes, and samples in game listings by default
              </p>
            </div>
            <Switch
              checked={hidePreReleaseDefault}
              onChange={setHidePreReleaseDefault}
            />
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

      <SteamGridDBConfigCard
        apiKey={steamgriddbApiKey}
        onApiKeyChange={setSteamgriddbApiKey}
        envConfigured={steamgriddbEnvConfigured}
      />

      <RAConfigCard
        apiKey={raApiKey}
        onApiKeyChange={setRaApiKey}
        envConfigured={raEnvConfigured}
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
