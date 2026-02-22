import { FileSearch, ScanSearch, AlertTriangle, CheckCircle } from "lucide-react";
import { Button, Card, Badge, EmptyState } from "@/components/ui";
import { useMetadataMatches, useScrapeGame } from "@/hooks/use-admin";
import { useToast } from "@/components/ui";
import type { Game } from "@/types/api";

function GameRow({
  game,
  onScrape,
  isScraping,
}: {
  game: Game;
  onScrape: () => void;
  isScraping: boolean;
}) {
  return (
    <div className="flex items-center gap-4 p-4 rounded-xl bg-surface-800/30 border border-surface-800">
      <div className="h-16 w-12 rounded-lg overflow-hidden bg-surface-800 flex-shrink-0">
        {game.coverUrl ? (
          <img
            src={game.coverUrl}
            alt={game.title}
            className="h-full w-full object-cover"
          />
        ) : (
          <div className="h-full w-full flex items-center justify-center">
            <AlertTriangle className="h-4 w-4 text-warning-500" />
          </div>
        )}
      </div>

      <div className="flex-1 min-w-0 space-y-1">
        <h4 className="text-sm font-semibold text-surface-100 truncate">
          {game.title}
        </h4>
        <div className="flex flex-wrap gap-2 text-xs text-surface-400">
          <span>{game.consoleName}</span>
          {game.developer && <span>{game.developer}</span>}
          {game.genre && <span>{game.genre}</span>}
        </div>
      </div>

      {game.scraperId ? (
        <Badge variant="success">Scraped</Badge>
      ) : (
        <Badge variant="warning">Unscraped</Badge>
      )}

      <Button
        size="sm"
        variant="secondary"
        onClick={onScrape}
        loading={isScraping}
      >
        <ScanSearch className="h-4 w-4" />
        Scrape
      </Button>
    </div>
  );
}

export function MetadataFixPage() {
  const { data, isLoading } = useMetadataMatches();
  const scrapeGame = useScrapeGame();
  const { toast } = useToast();

  if (isLoading) {
    return (
      <div className="space-y-6 max-w-5xl">
        <div className="h-8 w-48 rounded-lg bg-surface-800 animate-pulse" />
        <div className="h-96 rounded-2xl bg-surface-900 animate-pulse" />
      </div>
    );
  }

  const scraped = data?.scraped ?? [];
  const unscraped = data?.unscraped ?? [];

  if (scraped.length === 0 && unscraped.length === 0) {
    return (
      <div className="space-y-6">
        <div>
          <h1 className="text-3xl font-bold text-surface-100">
            Metadata Review
          </h1>
          <p className="mt-1 text-surface-400">
            Review and fix game metadata.
          </p>
        </div>
        <EmptyState
          icon={FileSearch}
          title="No games found"
          description="Scan your library first to detect games, then scrape for metadata."
        />
      </div>
    );
  }

  function handleScrape(game: Game) {
    scrapeGame.mutate(game.id, {
      onSuccess: () => toast("success", `Metadata updated for "${game.title}"`),
      onError: (err) =>
        toast("error", err instanceof Error ? err.message : "Scrape failed"),
    });
  }

  return (
    <div className="space-y-6 max-w-5xl">
      <div>
        <h1 className="text-3xl font-bold text-surface-100">
          Metadata Review
        </h1>
        <p className="mt-1 text-surface-400">
          Review game metadata. Scrape individual games to fetch or update cover
          art and descriptions.
        </p>
      </div>

      <div className="flex gap-3 text-sm text-surface-400">
        <span className="flex items-center gap-1.5">
          <CheckCircle className="h-4 w-4 text-success-500" />
          {scraped.length} scraped
        </span>
        <span className="flex items-center gap-1.5">
          <AlertTriangle className="h-4 w-4 text-warning-500" />
          {unscraped.length} unscraped
        </span>
      </div>

      {unscraped.length > 0 && (
        <Card className="p-6">
          <h2 className="text-lg font-semibold text-surface-100 mb-4 flex items-center gap-2">
            <AlertTriangle className="h-5 w-5 text-warning-500" />
            Unscraped Games
            <Badge variant="warning">{unscraped.length}</Badge>
          </h2>
          <div className="space-y-2">
            {unscraped.map((game) => (
              <GameRow
                key={game.id}
                game={game}
                onScrape={() => handleScrape(game)}
                isScraping={scrapeGame.isPending}
              />
            ))}
          </div>
        </Card>
      )}

      {scraped.length > 0 && (
        <Card className="p-6">
          <h2 className="text-lg font-semibold text-surface-100 mb-4 flex items-center gap-2">
            <CheckCircle className="h-5 w-5 text-success-500" />
            Scraped Games
            <Badge variant="success">{scraped.length}</Badge>
          </h2>
          <div className="space-y-2">
            {scraped.map((game) => (
              <GameRow
                key={game.id}
                game={game}
                onScrape={() => handleScrape(game)}
                isScraping={scrapeGame.isPending}
              />
            ))}
          </div>
        </Card>
      )}
    </div>
  );
}
