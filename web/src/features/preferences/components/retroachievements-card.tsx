import { useState } from "react";
import {
  Card,
  CardHeader,
  CardContent,
  Skeleton,
  Switch,
  Button,
  Input,
} from "@/components/ui";
import {
  useRAStatus,
  useLinkRA,
  useUnlinkRA,
  useRASettings,
} from "@/hooks/use-retroachievements";

export function RetroAchievementsCard() {
  const { data: status, isLoading, isError } = useRAStatus();
  const linkRA = useLinkRA();
  const unlinkRA = useUnlinkRA();
  const raSettings = useRASettings();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [linkError, setLinkError] = useState("");

  function handleLink(e: React.FormEvent) {
    e.preventDefault();
    setLinkError("");
    linkRA.mutate(
      { username, password },
      {
        onSuccess: () => {
          setUsername("");
          setPassword("");
        },
        onError: (err) => {
          setLinkError(err.message);
        },
      },
    );
  }

  function handleUnlink() {
    unlinkRA.mutate(undefined, {
      onError: (err) => {
        setLinkError(err.message);
      },
    });
  }

  function handleHardcoreToggle() {
    if (!status) return;
    raSettings.mutate({ hardcoreEnabled: !status.hardcoreEnabled });
  }

  return (
    <Card>
      <CardHeader>
        <h2 className="text-lg font-semibold text-surface-100">
          RetroAchievements
        </h2>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <div className="space-y-4">
            <Skeleton className="h-12 w-full rounded-lg" />
            <Skeleton className="h-12 w-full rounded-lg" />
          </div>
        ) : isError ? (
          <p className="text-sm text-danger-500">
            Failed to load RetroAchievements status.
          </p>
        ) : status?.linked ? (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-surface-200">
                  Linked Account
                </p>
                <p className="text-xs text-surface-400">{status.username}</p>
              </div>
              <Button
                variant="danger"
                size="sm"
                onClick={handleUnlink}
                loading={unlinkRA.isPending}
                data-testid="unlink-ra-btn"
              >
                Unlink Account
              </Button>
            </div>
            <label className="flex items-center justify-between py-3 cursor-pointer group">
              <div>
                <p className="text-sm font-medium text-surface-200 group-hover:text-surface-100 transition-colors">
                  Hardcore Mode
                </p>
                <p className="text-xs text-surface-500">
                  Disable save states and cheats for official leaderboard
                  eligibility
                </p>
              </div>
              <Switch
                checked={status.hardcoreEnabled}
                onChange={handleHardcoreToggle}
                data-testid="hardcore-toggle"
              />
            </label>
          </div>
        ) : (
          <form onSubmit={handleLink} className="space-y-4">
            <div className="space-y-3">
              <Input
                placeholder="RetroAchievements username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                data-testid="ra-username-input"
              />
              <Input
                type="password"
                placeholder="Password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                data-testid="ra-password-input"
              />
            </div>
            {linkError && <p className="text-sm text-danger-500">{linkError}</p>}
            <p className="text-xs text-surface-500">
              Your password is only used to obtain a token and is never stored.
            </p>
            <Button
              type="submit"
              variant="primary"
              size="sm"
              loading={linkRA.isPending}
              disabled={!username || !password}
              data-testid="link-ra-btn"
            >
              Link Account
            </Button>
          </form>
        )}
      </CardContent>
    </Card>
  );
}
