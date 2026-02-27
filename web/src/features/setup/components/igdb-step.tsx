import { useState } from "react";
import {
  CheckCircle2,
  AlertCircle,
  ChevronDown,
  ChevronRight,
} from "lucide-react";
import { Button, Input, Badge } from "@/components/ui";
import { useTestIgdbCredentials, useUpdateSettings } from "@/hooks/use-admin";

interface IgdbStepProps {
  onSkip: () => void;
  onSave: () => void;
}

export function IgdbStep({ onSkip, onSave }: IgdbStepProps) {
  const [clientId, setClientId] = useState("");
  const [clientSecret, setClientSecret] = useState("");
  const [testStatus, setTestStatus] = useState<"idle" | "success" | "error">(
    "idle",
  );
  const [testError, setTestError] = useState("");
  const [saving, setSaving] = useState(false);
  const [instructionsExpanded, setInstructionsExpanded] = useState(true);
  const testMutation = useTestIgdbCredentials();
  const updateSettings = useUpdateSettings();

  function handleTest() {
    setTestStatus("idle");
    testMutation.mutate(
      { clientId, clientSecret },
      {
        onSuccess: (data) => {
          if (data.success) {
            setTestStatus("success");
          } else {
            setTestStatus("error");
            setTestError(data.error ?? "Authentication failed");
          }
        },
        onError: () => {
          setTestStatus("error");
          setTestError("Connection failed");
        },
      },
    );
  }

  async function handleSaveAndContinue() {
    setSaving(true);
    try {
      await updateSettings.mutateAsync({
        igdb_client_id: clientId,
        igdb_client_secret: clientSecret,
      });
      onSave();
    } catch {
      setTestStatus("error");
      setTestError("Failed to save settings");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-semibold text-surface-100">
          IGDB Metadata
        </h2>
        <p className="mt-1 text-sm text-surface-400">
          IGDB provides game descriptions, cover art, and metadata. This step is
          optional but recommended.
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
          How to get your credentials
        </button>
        {instructionsExpanded && (
          <ol className="mt-2 ml-6 list-decimal text-sm text-surface-400 space-y-1">
            <li>
              Go to the{" "}
              <a
                href="https://dev.twitch.tv/console"
                target="_blank"
                rel="noopener noreferrer"
                className="text-brand-400 hover:text-brand-300 underline"
              >
                Twitch Developer Console
              </a>
            </li>
            <li>
              Register a new application (any name, any OAuth redirect URL)
            </li>
            <li>Copy the Client ID from the application details</li>
            <li>Generate a new Client Secret and copy it</li>
          </ol>
        )}
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <Input
          label="Client ID"
          placeholder="Twitch Client ID"
          value={clientId}
          onChange={(e) => {
            setClientId(e.target.value);
            setTestStatus("idle");
          }}
        />
        <Input
          label="Client Secret"
          type="password"
          placeholder="Twitch Client Secret"
          value={clientSecret}
          onChange={(e) => {
            setClientSecret(e.target.value);
            setTestStatus("idle");
          }}
        />
      </div>

      <div className="flex items-center gap-3">
        <Button
          variant="secondary"
          onClick={handleTest}
          loading={testMutation.isPending}
          disabled={!clientId || !clientSecret || testMutation.isPending}
        >
          Test Connection
        </Button>
        {testStatus === "success" && (
          <Badge variant="success" className="gap-1.5">
            <CheckCircle2 className="h-3.5 w-3.5" />
            Authenticated
          </Badge>
        )}
        {testStatus === "error" && (
          <Badge variant="danger" className="gap-1.5">
            <AlertCircle className="h-3.5 w-3.5" />
            {testError}
          </Badge>
        )}
      </div>

      <div className="flex items-center justify-between pt-2">
        <Button variant="secondary" onClick={onSkip}>
          Skip
        </Button>
        <Button
          onClick={handleSaveAndContinue}
          loading={saving}
          disabled={testStatus !== "success"}
        >
          Save & Continue
        </Button>
      </div>
    </div>
  );
}
