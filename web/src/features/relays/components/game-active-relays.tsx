import { Link } from "react-router-dom";
import { Repeat, Users } from "lucide-react";
import { Badge, Card, Skeleton } from "@/components/ui";
import { useGameRelays } from "@/hooks/use-relays";
import { formatRelativeTime } from "@/lib/format";

const statusVariant: Record<string, "success" | "warning" | "default"> = {
  active: "success",
  paused: "warning",
  completed: "default",
};

function GameActiveRelaysSkeleton() {
  return (
    <div className="space-y-2">
      {Array.from({ length: 2 }, (_, i) => (
        <div
          key={i}
          className="flex items-center gap-4 px-4 py-3 rounded-xl bg-surface-800/30"
        >
          <Skeleton className="h-5 w-5 rounded" />
          <div className="flex-1 space-y-1.5">
            <Skeleton className="h-4 w-40" />
            <Skeleton className="h-3 w-24" />
          </div>
          <Skeleton className="h-6 w-16 rounded-full" />
        </div>
      ))}
    </div>
  );
}

interface GameActiveRelaysProps {
  gameId: string;
}

export function GameActiveRelays({ gameId }: GameActiveRelaysProps) {
  const { data: relays, isLoading } = useGameRelays(gameId);

  if (isLoading) {
    return (
      <Card className="p-6">
        <div className="flex items-center gap-2.5 mb-4">
          <Repeat className="h-5 w-5 text-brand-400" />
          <h2 className="text-lg font-semibold text-surface-100">Active Relays</h2>
        </div>
        <GameActiveRelaysSkeleton />
      </Card>
    );
  }

  if (!relays || relays.length === 0) {
    return null;
  }

  return (
    <Card className="p-6">
      <div className="flex items-center gap-2.5 mb-4">
        <Repeat className="h-5 w-5 text-brand-400" />
        <h2 className="text-lg font-semibold text-surface-100">Active Relays</h2>
        <span className="text-sm text-surface-500">({relays.length})</span>
      </div>

      <div className="space-y-2">
        {relays.map((relay) => (
          <Link
            key={relay.id}
            to={`/relays/${relay.id}`}
            className="flex items-center gap-4 px-4 py-3 rounded-xl bg-surface-800/30 hover:bg-surface-800/50 transition-colors"
            data-testid={`game-relay-${relay.id}`}
          >
            <Repeat className="h-5 w-5 text-brand-400 flex-shrink-0" />
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-surface-200 truncate">
                {relay.name}
              </p>
              <div className="flex items-center gap-2 text-xs text-surface-500">
                <span>{relay.ownerUsername}</span>
                <span>&middot;</span>
                <span>
                  <Users className="inline h-3 w-3 mr-0.5" />
                  {relay.memberCount}
                </span>
                <span>&middot;</span>
                <span>{formatRelativeTime(relay.lastActivityAt)}</span>
              </div>
            </div>
            <Badge
              variant={statusVariant[relay.status] ?? "default"}
              className="capitalize"
            >
              {relay.status}
            </Badge>
          </Link>
        ))}
      </div>
    </Card>
  );
}
