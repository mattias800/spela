import { useMemo } from "react";
import { Link } from "react-router-dom";
import { Network } from "lucide-react";
import { PageLayout, SectionList } from "@/components/layout";
import { EmptyState, Skeleton } from "@/components/ui";
import { Card } from "@/components/ui/card";
import { ConsoleBadge } from "@/components/console-badge";
import { FriendsPlayingNow } from "@/features/federation/components/friends-playing-now";
import { useConnectedServerConsoles } from "@/hooks/use-connected-servers";
import { useConsoles } from "@/hooks/use-consoles";

// Browse overview for games that live only on connected servers. Lists the
// consoles that have connected-server games (with counts); each links to a
// per-console game grid. Parallel-worlds: this is separate from the local
// library / Consoles page.
export function ConnectedServersPage() {
  const { data: consoles, isLoading } = useConnectedServerConsoles();
  const { data: localConsoles } = useConsoles();

  const nameByAbbr = useMemo(() => {
    const m = new Map<string, string>();
    for (const c of localConsoles ?? []) m.set(c.abbreviation, c.name);
    return m;
  }, [localConsoles]);

  return (
    <PageLayout
      title="Connected servers"
      subtitle="Browse games available on servers you're connected to. Import one to add it to your library."
    >
      <SectionList>
        <FriendsPlayingNow />
        {isLoading ? (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 6 }, (_, i) => (
              // Height matches a populated card (p-5 + one text-sm row ≈ 4.5rem).
              <Skeleton key={i} className="h-[4.5rem] rounded-2xl" />
            ))}
          </div>
        ) : !consoles || consoles.length === 0 ? (
          <EmptyState
            icon={Network}
            title="No connected-server games"
            description="Games offered by servers you're connected to will appear here. Pair a connected server (admin → Federation) to start discovering games."
          />
        ) : (
          <div
            className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3"
            data-testid="connected-consoles-grid"
          >
            {consoles.map((c) => (
              <Link
                key={c.console}
                to={`/connected-servers/${encodeURIComponent(c.console)}`}
                data-testid={`connected-console-${c.console}`}
              >
                <Card
                  hover
                  className="flex items-center justify-between gap-3 p-5"
                >
                  <div className="flex min-w-0 items-center gap-3">
                    <ConsoleBadge code={c.console} />
                    <span className="truncate text-sm font-medium text-surface-200">
                      {nameByAbbr.get(c.console) ?? c.console}
                    </span>
                  </div>
                  <span className="flex-shrink-0 text-sm text-surface-500">
                    {c.count} {c.count === 1 ? "game" : "games"}
                  </span>
                </Card>
              </Link>
            ))}
          </div>
        )}
      </SectionList>
    </PageLayout>
  );
}

export default ConnectedServersPage;
