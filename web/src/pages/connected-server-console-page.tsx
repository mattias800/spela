import { useParams } from "react-router-dom";
import { Network } from "lucide-react";
import { PageLayout, SectionList } from "@/components/layout";
import { EmptyState, Skeleton } from "@/components/ui";
import { GAME_GRID_CLASSES } from "@/components/game-grid";
import { RemoteGameCard } from "@/features/federation/components/remote-game-card";
import { useConnectedServerGames } from "@/hooks/use-connected-servers";
import { useConsoles } from "@/hooks/use-consoles";

// Connected-server games for one console (reached from the browse overview).
// Each game links to the remote-game page where it can be imported.
export function ConnectedServerConsolePage() {
  const { console: consoleParam = "" } = useParams<{ console: string }>();
  const { data: games, isLoading } = useConnectedServerGames(consoleParam);
  const { data: localConsoles } = useConsoles();

  const consoleName =
    (localConsoles ?? []).find((c) => c.abbreviation === consoleParam)?.name ??
    consoleParam;

  return (
    <PageLayout
      backButtonVariant="standard"
      backTo="/connected-servers"
      backLabel="Connected servers"
      title={consoleName}
      subtitle="Games available on connected servers."
    >
      <SectionList>
        {isLoading ? (
          <div className={GAME_GRID_CLASSES}>
            {Array.from({ length: 12 }, (_, i) => (
              <Skeleton key={i} className="aspect-[3/4] rounded-2xl" />
            ))}
          </div>
        ) : !games || games.length === 0 ? (
          <EmptyState
            icon={Network}
            title="No games for this console"
            description="No connected server is currently offering games for this console."
          />
        ) : (
          <div className={GAME_GRID_CLASSES} data-testid="connected-games-grid">
            {games.map((game) => (
              <RemoteGameCard key={game.key} game={game} />
            ))}
          </div>
        )}
      </SectionList>
    </PageLayout>
  );
}

export default ConnectedServerConsolePage;
