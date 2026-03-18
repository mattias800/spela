import { useState } from "react";
import { CheckCircle2, ChevronDown, ChevronRight } from "lucide-react";
import { Button, Input } from "@/components/ui";
import { useSteamGridDBStatus, useUpdateSettings } from "@/hooks/use-admin";

interface SteamGridDBStepProps {
  onSkip: () => void;
  onSave: () => void;
}

export function SteamGridDBStep({ onSkip, onSave }: SteamGridDBStepProps) {
  const { data: status, isLoading } = useSteamGridDBStatus();
  const [apiKey, setApiKey] = useState("");
  const [saving, setSaving] = useState(false);
  const [instructionsExpanded, setInstructionsExpanded] = useState(true);
  const updateSettings = useUpdateSettings();

  if (!isLoading && status?.configured && status?.source === "env") {
    return (
      <div className="space-y-6">
        <div>
          <h2 className="text-xl font-semibold text-surface-100">
            SteamGridDB Artwork
          </h2>
          <p className="mt-1 text-sm text-surface-400">
            SteamGridDB provides hero artwork, grid images, logos, and icons.
          </p>
        </div>

        <div className="rounded-xl bg-success-500/10 border border-success-500/30 px-4 py-3">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="h-4 w-4 text-success-500" />
            <span className="text-sm font-medium text-success-500">
              SteamGridDB is already configured via environment variables.
            </span>
          </div>
        </div>

        <div className="flex items-center justify-end pt-2">
          <Button onClick={onSave}>Continue</Button>
        </div>
      </div>
    );
  }

  async function handleSaveAndContinue() {
    setSaving(true);
    try {
      await updateSettings.mutateAsync({
        steamgriddb_api_key: apiKey,
      });
      onSave();
    } catch {
      // Silently continue — the key will be validated when artwork is fetched
      onSave();
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-semibold text-surface-100">
          SteamGridDB Artwork
        </h2>
        <p className="mt-1 text-sm text-surface-400">
          SteamGridDB provides hero banners, grid images, logos, and icons for
          games. This step is optional — games will still have cover art from
          IGDB without it.
        </p>
      </div>

      <div>
        <button
          type="button"
          onClick={() => setInstructionsExpanded((prev) => !prev)}
          className="flex items-center gap-1.5 text-sm font-medium text-surface-300 hover:text-surface-100 transition-colors"
        >
          {instructionsExpanded ? (
            <ChevronDown className="h-4 w-4" />
          ) : (
            <ChevronRight className="h-4 w-4" />
          )}
          How to get your API key
        </button>
        {instructionsExpanded && (
          <ol className="mt-2 ml-6 list-decimal text-sm text-surface-400 space-y-1">
            <li>
              Go to{" "}
              <a
                href="https://www.steamgriddb.com/profile/preferences/api"
                target="_blank"
                rel="noopener noreferrer"
                className="text-brand-400 hover:text-brand-300 underline"
              >
                SteamGridDB API Preferences
              </a>
            </li>
            <li>Sign in with your Steam account</li>
            <li>Copy the API key from the preferences page</li>
          </ol>
        )}
      </div>

      <Input
        label="API Key"
        type="password"
        placeholder="SteamGridDB API Key"
        value={apiKey}
        onChange={(e) => setApiKey(e.target.value)}
      />

      <div className="flex items-center justify-between pt-2">
        <Button variant="secondary" onClick={onSkip}>
          Skip
        </Button>
        <Button
          onClick={handleSaveAndContinue}
          loading={saving}
          disabled={!apiKey}
        >
          Save & Continue
        </Button>
      </div>
    </div>
  );
}
