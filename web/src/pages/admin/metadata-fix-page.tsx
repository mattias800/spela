import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { PageLayout, SectionList } from "@/components/layout";
import {
  FileSearch,
  ScanSearch,
  AlertTriangle,
  ShieldQuestion,
  ImageOff,
} from "lucide-react";
import {
  Button,
  Section,
  Badge,
  EmptyState,
  StateTabNav,
  StateTabItem,
} from "@/components/ui";
import { useMetadataMatches, useScrapeGame } from "@/hooks/use-admin";
import { useToast } from "@/components/ui";
import type { Game } from "@/types/api";

type Tab = "unscraped" | "unverified" | "incomplete";

function GameRow({
  game,
  onScrape,
  isScraping,
}: {
  game: Game;
  onScrape: () => void;
  isScraping: boolean;
}) {
  const navigate = useNavigate();

  return (
    <div
      className="flex items-center gap-4 p-4 rounded-xl bg-surface-800/30 border border-surface-800 hover:border-surface-700 transition-colors cursor-pointer"
      onClick={() => navigate(`/games/${game.id}`)}
    >
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

      <Button
        size="sm"
        variant="secondary"
        onClick={(e) => {
          e.stopPropagation();
          onScrape();
        }}
        loading={isScraping}
        icon={<ScanSearch className="h-4 w-4" />}
      >
        Scrape
      </Button>
    </div>
  );
}

export function MetadataFixPage() {
  const { data, isLoading } = useMetadataMatches();
  const scrapeGame = useScrapeGame();
  const { toast } = useToast();
  const [tab, setTab] = useState<Tab>("unscraped");
  const [scrapingGameId, setScrapingGameId] = useState<string | null>(null);

  if (isLoading) {
    return (
      <PageLayout>
        <SectionList className="max-w-5xl">
          <div className="h-8 w-48 rounded-lg bg-surface-800 animate-pulse" />
          <div className="h-96 rounded-2xl bg-surface-900 animate-pulse" />
        </SectionList>
      </PageLayout>
    );
  }

  const unscraped = data?.unscraped ?? [];
  const unverified = data?.unverified ?? [];
  const incomplete = data?.incomplete ?? [];

  function handleScrape(game: Game) {
    setScrapingGameId(game.id);
    scrapeGame.mutate(game.id, {
      onSuccess: () => {
        toast("success", `Metadata updated for "${game.title}"`);
        setScrapingGameId(null);
      },
      onError: (err) => {
        toast("error", err instanceof Error ? err.message : "Scrape failed");
        setScrapingGameId(null);
      },
    });
  }

  const games =
    tab === "unscraped"
      ? unscraped
      : tab === "unverified"
        ? unverified
        : incomplete;

  return (
    <PageLayout title="Metadata Review" subtitle="Review games that need attention. Click a game to view its details.">
      <SectionList className="max-w-5xl">
      <StateTabNav>
        <StateTabItem active={tab === "unscraped"} onClick={() => setTab("unscraped")}>
          <ScanSearch className="h-4 w-4" />
          Unscraped
          <Badge variant="warning">{unscraped.length}</Badge>
        </StateTabItem>
        <StateTabItem active={tab === "incomplete"} onClick={() => setTab("incomplete")}>
          <ImageOff className="h-4 w-4" />
          Incomplete
          <Badge variant="warning">{incomplete.length}</Badge>
        </StateTabItem>
        <StateTabItem active={tab === "unverified"} onClick={() => setTab("unverified")}>
          <ShieldQuestion className="h-4 w-4" />
          Unverified
          <Badge variant="warning">{unverified.length}</Badge>
        </StateTabItem>
      </StateTabNav>

      {games.length === 0 ? (
        <EmptyState
          icon={FileSearch}
          title={
            tab === "unscraped"
              ? "All games have been scraped"
              : tab === "incomplete"
                ? "No incomplete games"
                : "All games are verified"
          }
          description={
            tab === "unscraped"
              ? "Every game in your library has metadata. Nice!"
              : tab === "incomplete"
                ? "All scraped games have full IGDB metadata."
                : "Every game matches a known good dump."
          }
        />
      ) : (
        <Section className="p-6">
          <div className="space-y-2">
            {games.map((game) => (
              <GameRow
                key={game.id}
                game={game}
                onScrape={() => handleScrape(game)}
                isScraping={scrapingGameId === game.id}
              />
            ))}
          </div>
        </Section>
      )}
    </SectionList>
    </PageLayout>
  );
}
