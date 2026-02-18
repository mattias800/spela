import { Crown } from "lucide-react";
import { Badge } from "@/components/ui";
import { PlayerAvatar } from "@/components/player-avatar";

interface FilledSlotProps {
  username: string;
  avatarUrl: string | null;
  isHost: boolean;
  label: string;
}

export function NetplayPlayerSlot({
  username,
  avatarUrl,
  isHost,
  label,
}: FilledSlotProps) {
  return (
    <div
      className="flex items-center gap-4 px-4 py-3 rounded-xl bg-surface-900/50"
      data-testid={`netplay-player-slot-${label}`}
    >
      <PlayerAvatar
        username={username}
        avatarUrl={avatarUrl ?? undefined}
      />
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <p className="text-sm font-medium text-surface-200 truncate">
            {username}
          </p>
          {isHost && (
            <Badge variant="warning">
              <Crown className="h-3 w-3 mr-1" />
              Host
            </Badge>
          )}
        </div>
        <p className="text-xs text-surface-500">{label}</p>
      </div>
    </div>
  );
}

export function NetplayEmptySlot() {
  return (
    <div
      className="flex items-center gap-4 px-4 py-3 rounded-xl bg-surface-900/50 border border-dashed border-surface-700"
      data-testid="netplay-player-slot-empty"
    >
      <div className="h-8 w-8 rounded-full bg-surface-800 flex items-center justify-center">
        <span className="text-surface-600 text-xs">?</span>
      </div>
      <p className="text-sm text-surface-500">
        Waiting for player
        <span className="inline-flex ml-1">
          <span className="animate-pulse">.</span>
          <span className="animate-pulse" style={{ animationDelay: "0.2s" }}>.</span>
          <span className="animate-pulse" style={{ animationDelay: "0.4s" }}>.</span>
        </span>
      </p>
    </div>
  );
}
