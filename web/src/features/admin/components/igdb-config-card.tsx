import { useState } from "react";
import {
  Globe,
  CheckCircle2,
  AlertCircle,
  ChevronDown,
  ChevronRight,
} from "lucide-react";
import {
  Badge,
  Button,
  Section,
  Input,
} from "@/components/ui";
import { useTestIgdbCredentials, useIgdbStatus } from "@/hooks/use-admin";

interface IgdbConfigCardProps {
  clientId: string;
  onClientIdChange: (value: string) => void;
  clientSecret: string;
  onClientSecretChange: (value: string) => void;
  envConfigured?: boolean;
}

export function IgdbConfigCard({
  clientId,
  onClientIdChange,
  clientSecret,
  onClientSecretChange,
  envConfigured,
}: IgdbConfigCardProps) {
  const { data: igdbStatus } = useIgdbStatus();
  const testMutation = useTestIgdbCredentials();
  const [testStatus, setTestStatus] = useState<"idle" | "success" | "error">(
    "idle",
  );
  const [testError, setTestError] = useState("");
  const [instructionsExpanded, setInstructionsExpanded] = useState(
    !clientId && !clientSecret,
  );

  function handleTest() {
    setTestStatus("idle");
    testMutation.mutate(
      { clientId, clientSecret },
      {
        onSuccess: (data) => {
          if (data?.success) {
            setTestStatus("success");
          } else {
            setTestStatus("error");
            setTestError(data?.error ?? "Authentication failed");
          }
        },
        onError: () => {
          setTestStatus("error");
          setTestError("Connection failed");
        },
      },
    );
  }

  function handleClientIdChange(value: string) {
    onClientIdChange(value);
    setTestStatus("idle");
  }

  function handleClientSecretChange(value: string) {
    onClientSecretChange(value);
    setTestStatus("idle");
  }

  const statusBadge = (() => {
    if (!igdbStatus) return null;
    switch (igdbStatus.status) {
      case "connected":
        return <Badge variant="success">Connected</Badge>;
      case "error":
        return <Badge variant="warning">Connection issue</Badge>;
      case "not_configured":
      default:
        return <Badge variant="warning">Not configured</Badge>;
    }
  })();

  return (
    <Section>
      <div className="px-5 pt-5 pb-2">
        <div className="flex items-center justify-between" id="igdb-config">
          <h2 className="text-lg font-semibold text-surface-100 flex items-center gap-2">
            <Globe className="h-5 w-5 text-brand-400" />
            IGDB Configuration
          </h2>
          {statusBadge}
        </div>
        <p className="text-xs text-surface-500 mt-1">
          Spela uses IGDB (Internet Game Database) to fetch game metadata, cover
          art, and descriptions. IGDB requires Twitch developer credentials to
          authenticate.
        </p>
      </div>
      <div className="px-5 pb-5 space-y-4">
        {envConfigured ? (
          <div className="rounded-xl bg-surface-800/50 border border-surface-700/50 p-4">
            <div className="flex items-center gap-2">
              <CheckCircle2 className="h-4 w-4 text-success-500 flex-shrink-0" />
              <p className="text-sm text-surface-200">
                Configured via environment variables
              </p>
            </div>
            <p className="mt-1.5 ml-6 text-xs text-surface-500">
              IGDB credentials are set through{" "}
              <code className="px-1 py-0.5 rounded bg-surface-800 text-surface-300 font-mono">
                SPELA_IGDB_CLIENT_ID
              </code>{" "}
              and{" "}
              <code className="px-1 py-0.5 rounded bg-surface-800 text-surface-300 font-mono">
                SPELA_IGDB_CLIENT_SECRET
              </code>
              . To change them, update your environment and restart the server.
            </p>
          </div>
        ) : (
          <>
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
                    Register a new application (any name, any OAuth redirect)
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
                onChange={(e) => handleClientIdChange(e.target.value)}
              />
              <Input
                label="Client Secret"
                type="password"
                placeholder="Twitch Client Secret"
                value={clientSecret}
                onChange={(e) => handleClientSecretChange(e.target.value)}
              />
            </div>

            <div className="flex items-center gap-3">
              <Button
                variant="secondary"
                onClick={handleTest}
                loading={testMutation.isPending}
                disabled={
                  !clientId || !clientSecret || testMutation.isPending
                }
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
          </>
        )}
      </div>
    </Section>
  );
}
