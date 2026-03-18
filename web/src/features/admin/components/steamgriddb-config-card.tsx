import { useState } from "react";
import {
  Image,
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
import { useSteamGridDBStatus } from "@/hooks/use-admin";

interface SteamGridDBConfigCardProps {
  apiKey: string;
  onApiKeyChange: (value: string) => void;
  envConfigured?: boolean;
}

export function SteamGridDBConfigCard({
  apiKey,
  onApiKeyChange,
  envConfigured,
}: SteamGridDBConfigCardProps) {
  const { data: status } = useSteamGridDBStatus();
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
          id="steamgriddb-config"
        >
          <h2 className="text-lg font-semibold text-surface-100 flex items-center gap-2">
            <Image className="h-5 w-5 text-brand-400" />
            SteamGridDB
          </h2>
          {statusBadge}
        </div>
        <p className="text-xs text-surface-500 mt-1">
          SteamGridDB provides hero artwork, grid images, logos, and icons for
          games. This is optional — games will still have cover art from IGDB
          without it.
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
              SteamGridDB API key is set through{" "}
              <code className="px-1 py-0.5 rounded bg-surface-800 text-surface-300 font-mono">
                SPELA_STEAMGRIDDB_API_KEY
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
              onChange={(e) => onApiKeyChange(e.target.value)}
            />
          </>
        )}
      </CardContent>
    </Card>
  );
}
