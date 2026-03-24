import { useState } from "react";
import {
  Trophy,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
} from "lucide-react";
import {
  Badge,
  Card,
  CardHeader,
  CardContent,
  Input,
} from "@/components/ui";
import { useRAStatus } from "@/hooks/use-admin";

interface RAConfigCardProps {
  apiKey: string;
  onApiKeyChange: (value: string) => void;
  envConfigured?: boolean;
}

export function RAConfigCard({
  apiKey,
  onApiKeyChange,
  envConfigured,
}: RAConfigCardProps) {
  const { data: status } = useRAStatus();
  const [instructionsExpanded, setInstructionsExpanded] = useState(!apiKey);

  const statusBadge = (() => {
    if (!status) return null;
    if (status.configured) {
      return <Badge variant="success">Connected</Badge>;
    }
    return <Badge variant="warning">Not configured</Badge>;
  })();

  return (
    <Card>
      <CardHeader>
        <div
          className="flex items-center justify-between"
          id="ra-config"
        >
          <h2 className="text-lg font-semibold text-surface-100 flex items-center gap-2">
            <Trophy className="h-5 w-5 text-brand-400" />
            RetroAchievements
          </h2>
          {statusBadge}
        </div>
        <p className="text-xs text-surface-500 mt-1">
          RetroAchievements provides achievement data for retro games. When
          configured, achievement lists are fetched automatically during game
          scanning and shown to all users.
        </p>
      </CardHeader>
      <CardContent className="space-y-4">
        {envConfigured ? (
          <div className="rounded-xl bg-surface-800/50 border border-surface-700/50 p-4">
            <div className="flex items-center gap-2">
              <CheckCircle2 className="h-4 w-4 text-success-500 flex-shrink-0" />
              <p className="text-sm text-surface-200">
                Configured via environment variables
              </p>
            </div>
            <p className="mt-1.5 ml-6 text-xs text-surface-500">
              RA API key is set through{" "}
              <code className="px-1 py-0.5 rounded bg-surface-800 text-surface-300 font-mono">
                SPELA_RA_API_KEY
              </code>
              . To change it, update your environment and restart the server.
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
                How to get your API key
              </button>
              {instructionsExpanded && (
                <ol className="mt-2 ml-6 list-decimal text-sm text-surface-400 space-y-1">
                  <li>
                    Go to{" "}
                    <a
                      href="https://retroachievements.org/controlpanel.php"
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-brand-400 hover:text-brand-300 underline"
                    >
                      RetroAchievements Control Panel
                    </a>
                  </li>
                  <li>Sign in with your RA account</li>
                  <li>Find the &quot;Keys&quot; section and copy your Web API Key</li>
                </ol>
              )}
            </div>

            <Input
              label="Web API Key"
              type="password"
              placeholder="RetroAchievements Web API Key"
              value={apiKey}
              onChange={(e) => onApiKeyChange(e.target.value)}
            />
          </>
        )}
      </CardContent>
    </Card>
  );
}
