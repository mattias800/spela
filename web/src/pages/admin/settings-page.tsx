import { useState, useEffect } from "react";
import { Settings, Plus, X, Key } from "lucide-react";
import { Button, Card, CardHeader, CardContent, Input, Select } from "@/components/ui";
import { useServerSettings, useUpdateSettings } from "@/hooks/use-admin";
import { useToast } from "@/components/ui";
import { Skeleton } from "@/components/ui";

export function AdminSettingsPage() {
  const { data: settings, isLoading } = useServerSettings();
  const updateSettings = useUpdateSettings();
  const { toast } = useToast();

  const [gameDirectories, setGameDirectories] = useState<string[]>([]);
  const [newDir, setNewDir] = useState("");
  const [allowRegistration, setAllowRegistration] = useState(true);
  const [scrapeOnScan, setScrapeOnScan] = useState(true);
  const [scraperSource, setScraperSource] = useState("igdb");
  const [ssUsername, setSsUsername] = useState("");
  const [ssPassword, setSsPassword] = useState("");

  useEffect(() => {
    if (settings) {
      const dirs = settings["gameDirectories"] ?? "";
      setGameDirectories(dirs ? dirs.split(",") : []);
      setAllowRegistration(settings["allowRegistration"] !== "false");
      setScrapeOnScan(settings["scrapeOnScan"] !== "false");
      setScraperSource(settings["defaultScraperSource"] ?? "igdb");
      setSsUsername(settings["screenscraper_username"] ?? "");
      setSsPassword(settings["screenscraper_password"] ?? "");
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
    updateSettings.mutate(
      {
        gameDirectories: gameDirectories.join(","),
        allowRegistration: String(allowRegistration),
        scrapeOnScan: String(scrapeOnScan),
        defaultScraperSource: scraperSource,
        screenscraper_username: ssUsername,
        screenscraper_password: ssPassword,
      },
      {
        onSuccess: () => toast("success", "Settings saved"),
        onError: (err) => toast("error", err instanceof Error ? err.message : "Unknown error"),
      },
    );
  }

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-64 w-full rounded-2xl" />
      </div>
    );
  }

  return (
    <div className="space-y-6 max-w-3xl">
      <div>
        <h1 className="text-3xl font-bold text-surface-100">Server Settings</h1>
        <p className="mt-1 text-surface-400">
          Configure server behavior and game scanning.
        </p>
      </div>

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
              <p className="text-sm font-medium text-surface-200">Allow Registration</p>
              <p className="text-xs text-surface-500">Allow new users to create accounts</p>
            </div>
            <button
              onClick={() => setAllowRegistration(!allowRegistration)}
              className={`relative w-11 h-6 rounded-full transition-colors ${
                allowRegistration ? "bg-brand-600" : "bg-surface-700"
              }`}
            >
              <span
                className={`absolute top-0.5 left-0.5 h-5 w-5 rounded-full bg-white transition-transform ${
                  allowRegistration ? "translate-x-5" : ""
                }`}
              />
            </button>
          </div>

          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-surface-200">Auto-scrape on Scan</p>
              <p className="text-xs text-surface-500">Automatically fetch metadata when scanning</p>
            </div>
            <button
              onClick={() => setScrapeOnScan(!scrapeOnScan)}
              className={`relative w-11 h-6 rounded-full transition-colors ${
                scrapeOnScan ? "bg-brand-600" : "bg-surface-700"
              }`}
            >
              <span
                className={`absolute top-0.5 left-0.5 h-5 w-5 rounded-full bg-white transition-transform ${
                  scrapeOnScan ? "translate-x-5" : ""
                }`}
              />
            </button>
          </div>

          <Select
            label="Default Scraper Source"
            options={[
              { value: "igdb", label: "IGDB" },
              { value: "thegamesdb", label: "TheGamesDB" },
              { value: "screenscraper", label: "ScreenScraper" },
            ]}
            value={scraperSource}
            onChange={(e) => setScraperSource(e.target.value)}
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <h2 className="text-lg font-semibold text-surface-100 flex items-center gap-2">
            <Key className="h-5 w-5 text-brand-400" />
            ScreenScraper Credentials
          </h2>
          <p className="text-xs text-surface-500 mt-1">
            Required for metadata scraping. Get credentials at screenscraper.fr
          </p>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              label="Username"
              placeholder="ScreenScraper username"
              value={ssUsername}
              onChange={(e) => setSsUsername(e.target.value)}
            />
            <Input
              label="Password"
              type="password"
              placeholder="ScreenScraper password"
              value={ssPassword}
              onChange={(e) => setSsPassword(e.target.value)}
            />
          </div>
        </CardContent>
      </Card>

      <div className="flex justify-end">
        <Button onClick={handleSave} loading={updateSettings.isPending} size="lg">
          Save Settings
        </Button>
      </div>
    </div>
  );
}
