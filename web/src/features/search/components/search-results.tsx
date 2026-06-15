import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Code,
  Building,
  FolderOpen,
  Layers,
  Crown,
  Star,
} from "lucide-react";
import { cn } from "@/lib/cn";
import type {
  SearchResults,
  GameSearchResult,
  ConsoleSearchResult,
  DeveloperSearchResult,
  PublisherSearchResult,
  CollectionSearchResult,
  SeriesSearchResult,
  FranchiseSearchResult,
} from "@/hooks/use-search";
import type { CatalogAvailability } from "@/generated/schemas";

// Cap the connected-servers teaser so it never crowds out local results.
const FEDERATED_LIMIT = 6;

interface SearchResultsDisplayProps {
  // Optional: federated results can render before the local search resolves.
  results?: SearchResults;
  onNavigate: (path: string) => void;
  // Games found on connected servers (not in the local library). Read-only —
  // listed for discovery; not navigable or downloadable yet.
  federatedGames?: CatalogAvailability[];
}

interface ResultSection {
  key: string;
  title: string;
  total: number;
  items: ResultItem[];
}

interface ResultItem {
  id: string;
  path: string;
  render: (highlighted: boolean) => React.ReactNode;
}

// Helper for building a section's items. Each of the 7 result types
// used to have its own 10-line `if (…length > 0) { sections.push({…}) }`
// block; this collapses them to 3-line entries in `buildSections`.
function makeSection<T>(
  key: string,
  title: string,
  bucket: { results: T[]; total: number },
  getId: (item: T) => string,
  getPath: (item: T) => string,
  renderRow: (item: T, highlighted: boolean) => React.ReactNode,
): ResultSection | null {
  if (bucket.results.length === 0) return null;
  return {
    key,
    title,
    total: bucket.total,
    items: bucket.results.map((item) => ({
      id: getId(item),
      path: getPath(item),
      render: (highlighted: boolean) => renderRow(item, highlighted),
    })),
  };
}

function buildSections(results: SearchResults): ResultSection[] {
  const sections: Array<ResultSection | null> = [
    makeSection(
      "games",
      "Games",
      results.games,
      (g) => `game-${g.id}`,
      (g) => `/games/${g.id}`,
      (g, hl) => <GameRow game={g} highlighted={hl} />,
    ),
    makeSection(
      "consoles",
      "Consoles",
      results.consoles,
      (c) => `console-${c.id}`,
      (c) => `/consoles/${c.id}`,
      (c, hl) => <ConsoleRow console={c} highlighted={hl} />,
    ),
    makeSection(
      "developers",
      "Developers",
      results.developers,
      (d) => `dev-${d.name}`,
      (d) => `/explore/developers/${encodeURIComponent(d.name)}`,
      (d, hl) => <DeveloperRow developer={d} highlighted={hl} />,
    ),
    makeSection(
      "publishers",
      "Publishers",
      results.publishers,
      (p) => `pub-${p.name}`,
      (p) => `/explore/publishers/${encodeURIComponent(p.name)}`,
      (p, hl) => <PublisherRow publisher={p} highlighted={hl} />,
    ),
    makeSection(
      "collections",
      "Collections",
      results.collections,
      (c) => `col-${c.id}`,
      (c) => `/collections/${c.id}`,
      (c, hl) => <CollectionRow collection={c} highlighted={hl} />,
    ),
    makeSection(
      "series",
      "Series",
      results.series,
      (s) => `series-${s.id}`,
      (s) => `/explore/series/${s.id}`,
      (s, hl) => <SeriesRow series={s} highlighted={hl} />,
    ),
    makeSection(
      "franchises",
      "Franchises",
      results.franchises,
      (f) => `fran-${f.id}`,
      (f) => `/explore/franchise/${f.id}`,
      (f, hl) => <FranchiseRow franchise={f} highlighted={hl} />,
    ),
  ];
  return sections.filter((s): s is ResultSection => s !== null);
}

export function SearchResultsDisplay({
  results,
  onNavigate,
  federatedGames,
}: SearchResultsDisplayProps) {
  const sections = results ? buildSections(results) : [];
  const fedGames = federatedGames ?? [];
  const allItems = sections.flatMap((s) => s.items);
  const [highlightIndex, setHighlightIndex] = useState(-1);
  const scrollRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

  // Reset highlight when results change
  useEffect(() => {
    setHighlightIndex(-1);
  }, [results]);

  const handleKeyDown = useCallback(
    (e: KeyboardEvent) => {
      if (allItems.length === 0) return;

      if (e.key === "ArrowDown") {
        e.preventDefault();
        setHighlightIndex((prev) =>
          prev < allItems.length - 1 ? prev + 1 : 0,
        );
      } else if (e.key === "ArrowUp") {
        e.preventDefault();
        setHighlightIndex((prev) =>
          prev > 0 ? prev - 1 : allItems.length - 1,
        );
      } else if (e.key === "Enter" && highlightIndex >= 0) {
        e.preventDefault();
        const item = allItems[highlightIndex];
        if (item) {
          onNavigate(item.path);
          navigate(item.path);
        }
      }
    },
    [allItems, highlightIndex, navigate, onNavigate],
  );

  useEffect(() => {
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [handleKeyDown]);

  // Scroll highlighted item into view
  useEffect(() => {
    if (highlightIndex < 0 || !scrollRef.current) return;
    const el = scrollRef.current.querySelector(
      `[data-result-index="${highlightIndex}"]`,
    );
    if (el && typeof el.scrollIntoView === "function") {
      el.scrollIntoView({ block: "nearest" });
    }
  }, [highlightIndex]);

  if (sections.length === 0 && fedGames.length === 0) return null;

  let globalIndex = 0;

  return (
    <div data-comp="SearchResultsDisplay" ref={scrollRef} className="overflow-y-auto max-h-[60vh]">
      {sections.map((section) => (
        <div key={section.key} className="py-2">
          <div className="px-4 py-1.5 flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wider text-surface-500">
              {section.title}
            </span>
            {section.total > section.items.length && (
              <span className="text-xs text-surface-500">
                {section.total} total
              </span>
            )}
          </div>
          {section.items.map((item) => {
            const currentIndex = globalIndex++;
            return (
              <button
                key={item.id}
                data-result-index={currentIndex}
                data-testid={`search-result-${item.id}`}
                className="w-full text-left px-4 py-1.5 cursor-pointer"
                onClick={() => {
                  onNavigate(item.path);
                  navigate(item.path);
                }}
                onMouseEnter={() => setHighlightIndex(currentIndex)}
              >
                {item.render(highlightIndex === currentIndex)}
              </button>
            );
          })}
        </div>
      ))}

      {fedGames.length > 0 && (
        <div className="py-2" data-testid="federated-search-section">
          <div className="px-4 py-1.5 flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wider text-surface-500">
              On connected servers
            </span>
            {fedGames.length > FEDERATED_LIMIT && (
              <span className="text-xs text-surface-500">
                {fedGames.length} total
              </span>
            )}
          </div>
          {fedGames.slice(0, FEDERATED_LIMIT).map((game) => (
            // Read-only: discovery only — not navigable or downloadable yet.
            <div
              key={game.key}
              data-testid={`federated-result-${game.key}`}
              className="px-4 py-1.5"
            >
              <FederatedGameRow game={game} />
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// Shared row primitive — every result row renders as a 3-slot flex
// container (leading icon, title/subtitle stack, right-side metadata)
// with the same highlighted/hover treatment. The 7 typed adapters
// below compose their own slots and delegate here.

interface SearchResultRowProps {
  icon: React.ReactNode;
  title: string;
  subtitle?: string;
  rightContent: React.ReactNode;
  highlighted: boolean;
}

function SearchResultRow({
  icon,
  title,
  subtitle,
  rightContent,
  highlighted,
}: SearchResultRowProps) {
  return (
    <div data-comp="SearchResultRow"
      className={cn(
        "flex items-center gap-3 rounded-lg px-2 py-1.5 transition-colors",
        highlighted ? "bg-surface-800" : "hover:bg-surface-800/50",
      )}
    >
      {icon}
      {subtitle !== undefined ? (
        <div className="min-w-0 flex-1">
          <p className="text-sm font-medium text-surface-100 truncate">{title}</p>
          <p className="text-xs text-surface-500 truncate">{subtitle}</p>
        </div>
      ) : (
        <p className="text-sm font-medium text-surface-100 flex-1 truncate">
          {title}
        </p>
      )}
      {rightContent}
    </div>
  );
}

// Small square icon badge used by the name-only result types
// (developer, publisher, collection, series, franchise).
function IconBadge({
  icon: Icon,
}: {
  icon: typeof Code;
}) {
  return (
    <div data-comp="IconBadge" className="h-8 w-8 rounded-lg bg-surface-800 flex items-center justify-center flex-shrink-0">
      <Icon className="h-4 w-4 text-surface-400" />
    </div>
  );
}

// Shared "N game(s) + optional rating" right-side renderer used by
// the developer and publisher rows. Extracted so the rating
// conditional only lives in one place.
function GameCountWithRating({
  gameCount,
  avgRating,
}: {
  gameCount: number;
  avgRating: number;
}) {
  return (
    <span data-comp="GameCountWithRating" className="text-xs text-surface-500 flex items-center gap-1.5">
      {gameCount} {gameCount === 1 ? "game" : "games"}
      {avgRating > 0 && (
        <>
          <Star className="h-3 w-3 text-warning-500 fill-warning-500" />
          {avgRating.toFixed(1)}
        </>
      )}
    </span>
  );
}

// --- Individual row components ---

function GameRow({
  game,
  highlighted,
}: {
  game: GameSearchResult;
  highlighted: boolean;
}) {
  const icon = game.coverUrl ? (
    <img
      src={game.coverUrl}
      alt=""
      className="h-10 w-8 rounded object-cover flex-shrink-0"
    />
  ) : (
    <div className="h-10 w-8 rounded bg-surface-700 flex-shrink-0" />
  );
  return (
    <SearchResultRow
      icon={icon}
      title={game.title}
      subtitle={game.developer || undefined}
      rightContent={
        <span className="text-xs font-medium px-2 py-0.5 rounded bg-surface-800 text-surface-400 uppercase flex-shrink-0">
          {game.consoleId}
        </span>
      }
      highlighted={highlighted}
    />
  );
}

// Connected-server game: discovery row. Box art is the origin server's public
// IGDB cover URL when the federated catalog carries one, else a placeholder —
// covers aren't guaranteed for every connected-server game.
function FederatedGameRow({ game }: { game: CatalogAvailability }) {
  const servers = game.originCount;
  const icon = game.cover ? (
    <img
      src={game.cover}
      alt=""
      className="h-10 w-8 rounded object-cover flex-shrink-0"
    />
  ) : (
    <div className="h-10 w-8 rounded bg-surface-700 flex-shrink-0" />
  );
  return (
    <SearchResultRow
      icon={icon}
      title={game.title}
      subtitle={`on ${servers} connected ${servers === 1 ? "server" : "servers"}`}
      rightContent={
        <span className="text-xs font-medium px-2 py-0.5 rounded bg-surface-800 text-surface-400 uppercase flex-shrink-0">
          {game.console}
        </span>
      }
      highlighted={false}
    />
  );
}

function ConsoleRow({
  console: c,
  highlighted,
}: {
  console: ConsoleSearchResult;
  highlighted: boolean;
}) {
  return (
    <SearchResultRow
      icon={
        <img
          src={c.iconUrl}
          alt=""
          className="h-6 w-6 flex-shrink-0"
          style={{ imageRendering: "pixelated" }}
        />
      }
      title={c.name}
      rightContent={
        <span className="text-xs text-surface-500">
          {c.gameCount} {c.gameCount === 1 ? "game" : "games"}
        </span>
      }
      highlighted={highlighted}
    />
  );
}

function DeveloperRow({
  developer,
  highlighted,
}: {
  developer: DeveloperSearchResult;
  highlighted: boolean;
}) {
  return (
    <SearchResultRow
      icon={<IconBadge icon={Code} />}
      title={developer.name}
      rightContent={
        <GameCountWithRating
          gameCount={developer.gameCount}
          avgRating={developer.avgRating}
        />
      }
      highlighted={highlighted}
    />
  );
}

function PublisherRow({
  publisher,
  highlighted,
}: {
  publisher: PublisherSearchResult;
  highlighted: boolean;
}) {
  return (
    <SearchResultRow
      icon={<IconBadge icon={Building} />}
      title={publisher.name}
      rightContent={
        <GameCountWithRating
          gameCount={publisher.gameCount}
          avgRating={publisher.avgRating}
        />
      }
      highlighted={highlighted}
    />
  );
}

function CollectionRow({
  collection,
  highlighted,
}: {
  collection: CollectionSearchResult;
  highlighted: boolean;
}) {
  return (
    <SearchResultRow
      icon={<IconBadge icon={FolderOpen} />}
      title={collection.name}
      subtitle={`by ${collection.username}`}
      rightContent={
        <span className="text-xs text-surface-500">
          {collection.gameCount} {collection.gameCount === 1 ? "game" : "games"}
        </span>
      }
      highlighted={highlighted}
    />
  );
}

function SeriesRow({
  series,
  highlighted,
}: {
  series: SeriesSearchResult;
  highlighted: boolean;
}) {
  return (
    <SearchResultRow
      icon={<IconBadge icon={Layers} />}
      title={series.name}
      rightContent={
        <span className="text-xs text-surface-500">
          {series.libraryGames} of {series.totalGames} in library
        </span>
      }
      highlighted={highlighted}
    />
  );
}

function FranchiseRow({
  franchise,
  highlighted,
}: {
  franchise: FranchiseSearchResult;
  highlighted: boolean;
}) {
  return (
    <SearchResultRow
      icon={<IconBadge icon={Crown} />}
      title={franchise.name}
      rightContent={
        <span className="text-xs text-surface-500">
          {franchise.libraryGames} of {franchise.totalGames} in library
        </span>
      }
      highlighted={highlighted}
    />
  );
}
