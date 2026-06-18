import { Link } from "react-router-dom";
import { Gamepad2, ChevronRight } from "lucide-react";
import { Card } from "@/components/ui/card";
import { EmptyState, Skeleton } from "@/components/ui";
import { PlayerAvatar } from "@/components/player-avatar";
import { useFederationPresence } from "@/hooks/use-federation-presence";

// FriendsPlayingNow is a fully self-contained, movable widget: it owns its data
// (useFederationPresence), renders its own surface + header + every state, and
// takes no props. Drop `<FriendsPlayingNow />` anywhere — it needs nothing from
// its parent — so we can relocate it freely as the UX is iterated on.
//
// It shows live presence on OTHER connected servers (hop >= 1). Local players
// (hop 0, which the aggregate also returns) are surfaced by the local
// "Online Now" widget elsewhere, so they're filtered out here to match this
// section's "across connected servers" framing.
function PlayingNowSkeleton() {
  return (
    <div data-comp="PlayingNowSkeleton" className="space-y-3" data-testid="friends-playing-now-skeleton">
      {Array.from({ length: 3 }, (_, i) => (
        <div key={i} className="flex items-center gap-3 px-3 py-2">
          <Skeleton className="h-8 w-8 rounded-full" />
          <div className="flex-1 space-y-1">
            <Skeleton className="h-4 w-24" />
            <Skeleton className="h-3 w-32" />
          </div>
        </div>
      ))}
    </div>
  );
}

export function FriendsPlayingNow() {
  const { data, isLoading } = useFederationPresence();
  const playing = (data ?? []).filter((p) => p.hops >= 1);

  return (
    <div data-comp="FriendsPlayingNow" data-testid="friends-playing-now">
      <Card className="p-5">
        <div className="mb-4 flex items-center gap-2.5">
          <Gamepad2 className="h-5 w-5 text-brand-400" />
          <h2 className="text-lg font-bold text-surface-100">
            Playing now across connected servers
          </h2>
          {playing.length > 0 && (
            <span className="rounded-full bg-brand-400/10 px-2 py-0.5 text-xs font-medium text-brand-400">
              {playing.length}
            </span>
          )}
        </div>

        {isLoading && <PlayingNowSkeleton />}

        {!isLoading && playing.length === 0 && (
          <EmptyState
            icon={Gamepad2}
            title="No one playing right now"
            description="When people on your connected servers are playing, they'll show up here."
            className="py-8"
          />
        )}

        {!isLoading && playing.length > 0 && (
          <div className="space-y-1">
            {playing.map((p, i) => (
              <Link
                key={`${p.serverName}-${p.username}-${p.gameKey}-${i}`}
                to={`/remote-games/${encodeURIComponent(p.gameKey)}`}
                className="group flex items-center gap-3 rounded-xl px-3 py-2.5 transition-colors hover:bg-surface-800/50"
                data-testid="friends-playing-now-row"
              >
                <div className="relative flex-shrink-0">
                  <PlayerAvatar username={p.username} />
                  <span className="absolute -bottom-0.5 -right-0.5 h-3 w-3 rounded-full border-2 border-surface-900 bg-green-500" />
                </div>
                <div className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium text-surface-200">
                    {p.username}
                  </span>
                  <span className="flex items-center gap-1.5">
                    <Gamepad2 className="h-3 w-3 flex-shrink-0 text-brand-400" />
                    <span className="truncate text-xs text-surface-400 transition-colors group-hover:text-brand-400">
                      {p.gameTitle}
                    </span>
                  </span>
                </div>
                <span className="hidden flex-shrink-0 text-xs text-surface-500 sm:block">
                  on {p.serverName || "a connected server"}
                </span>
                <ChevronRight className="h-4 w-4 flex-shrink-0 text-surface-600 transition-colors group-hover:text-surface-400" />
              </Link>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
}
