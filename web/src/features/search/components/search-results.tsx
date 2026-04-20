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

interface SearchResultsDisplayProps {
  results: SearchResults;
  onNavigate: (path: string) => void;
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

function buildSections(results: SearchResults): ResultSection[] {
  const sections: ResultSection[] = [];

  const games = results.games.results ?? [];
  if (games.length > 0) {
    sections.push({
      key: "games",
      title: "Games",
      total: results.games.total,
      items: games.map((g) => ({
        id: `game-${g.id}`,
        path: `/games/${g.id}`,
        render: (highlighted: boolean) => <GameRow game={g} highlighted={highlighted} />,
      })),
    });
  }

  const consoles = results.consoles.results ?? [];
  if (consoles.length > 0) {
    sections.push({
      key: "consoles",
      title: "Consoles",
      total: results.consoles.total,
      items: consoles.map((c) => ({
        id: `console-${c.id}`,
        path: `/consoles/${c.id}`,
        render: (highlighted: boolean) => <ConsoleRow console={c} highlighted={highlighted} />,
      })),
    });
  }

  const developers = results.developers.results ?? [];
  if (developers.length > 0) {
    sections.push({
      key: "developers",
      title: "Developers",
      total: results.developers.total,
      items: developers.map((d) => ({
        id: `dev-${d.name}`,
        path: `/explore/developers/${encodeURIComponent(d.name)}`,
        render: (highlighted: boolean) => <DeveloperRow developer={d} highlighted={highlighted} />,
      })),
    });
  }

  const publishers = results.publishers.results ?? [];
  if (publishers.length > 0) {
    sections.push({
      key: "publishers",
      title: "Publishers",
      total: results.publishers.total,
      items: publishers.map((p) => ({
        id: `pub-${p.name}`,
        path: `/explore/publishers/${encodeURIComponent(p.name)}`,
        render: (highlighted: boolean) => <PublisherRow publisher={p} highlighted={highlighted} />,
      })),
    });
  }

  const collections = results.collections.results ?? [];
  if (collections.length > 0) {
    sections.push({
      key: "collections",
      title: "Collections",
      total: results.collections.total,
      items: collections.map((c) => ({
        id: `col-${c.id}`,
        path: `/collections/${c.id}`,
        render: (highlighted: boolean) => <CollectionRow collection={c} highlighted={highlighted} />,
      })),
    });
  }

  const series = results.series.results ?? [];
  if (series.length > 0) {
    sections.push({
      key: "series",
      title: "Series",
      total: results.series.total,
      items: series.map((s) => ({
        id: `series-${s.id}`,
        path: `/explore/series/${s.id}`,
        render: (highlighted: boolean) => <SeriesRow series={s} highlighted={highlighted} />,
      })),
    });
  }

  const franchises = results.franchises.results ?? [];
  if (franchises.length > 0) {
    sections.push({
      key: "franchises",
      title: "Franchises",
      total: results.franchises.total,
      items: franchises.map((f) => ({
        id: `fran-${f.id}`,
        path: `/explore/franchise/${f.id}`,
        render: (highlighted: boolean) => <FranchiseRow franchise={f} highlighted={highlighted} />,
      })),
    });
  }

  return sections;
}

export function SearchResultsDisplay({
  results,
  onNavigate,
}: SearchResultsDisplayProps) {
  const sections = buildSections(results);
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

  if (sections.length === 0) return null;

  let globalIndex = 0;

  return (
    <div ref={scrollRef} className="overflow-y-auto max-h-[60vh]">
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
    </div>
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
  return (
    <div
      className={cn(
        "flex items-center gap-3 rounded-lg px-2 py-1.5 transition-colors",
        highlighted ? "bg-surface-800" : "hover:bg-surface-800/50",
      )}
    >
      {game.coverUrl ? (
        <img
          src={game.coverUrl}
          alt=""
          className="h-10 w-8 rounded object-cover flex-shrink-0"
        />
      ) : (
        <div className="h-10 w-8 rounded bg-surface-700 flex-shrink-0" />
      )}
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium text-surface-100 truncate">
          {game.title}
        </p>
        {game.developer && (
          <p className="text-xs text-surface-500 truncate">{game.developer}</p>
        )}
      </div>
      <span className="text-xs font-medium px-2 py-0.5 rounded bg-surface-800 text-surface-400 uppercase flex-shrink-0">
        {game.consoleId}
      </span>
    </div>
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
    <div
      className={cn(
        "flex items-center gap-3 rounded-lg px-2 py-1.5 transition-colors",
        highlighted ? "bg-surface-800" : "hover:bg-surface-800/50",
      )}
    >
      <img
        src={c.iconUrl}
        alt=""
        className="h-6 w-6 flex-shrink-0"
        style={{ imageRendering: "pixelated" }}
      />
      <p className="text-sm font-medium text-surface-100 flex-1 truncate">
        {c.name}
      </p>
      <span className="text-xs text-surface-500">
        {c.gameCount} {c.gameCount === 1 ? "game" : "games"}
      </span>
    </div>
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
    <div
      className={cn(
        "flex items-center gap-3 rounded-lg px-2 py-1.5 transition-colors",
        highlighted ? "bg-surface-800" : "hover:bg-surface-800/50",
      )}
    >
      <div className="h-8 w-8 rounded-lg bg-surface-800 flex items-center justify-center flex-shrink-0">
        <Code className="h-4 w-4 text-surface-400" />
      </div>
      <p className="text-sm font-medium text-surface-100 flex-1 truncate">
        {developer.name}
      </p>
      <span className="text-xs text-surface-500 flex items-center gap-1.5">
        {developer.gameCount} {developer.gameCount === 1 ? "game" : "games"}
        {developer.avgRating > 0 && (
          <>
            <Star className="h-3 w-3 text-warning-500 fill-warning-500" />
            {developer.avgRating.toFixed(1)}
          </>
        )}
      </span>
    </div>
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
    <div
      className={cn(
        "flex items-center gap-3 rounded-lg px-2 py-1.5 transition-colors",
        highlighted ? "bg-surface-800" : "hover:bg-surface-800/50",
      )}
    >
      <div className="h-8 w-8 rounded-lg bg-surface-800 flex items-center justify-center flex-shrink-0">
        <Building className="h-4 w-4 text-surface-400" />
      </div>
      <p className="text-sm font-medium text-surface-100 flex-1 truncate">
        {publisher.name}
      </p>
      <span className="text-xs text-surface-500 flex items-center gap-1.5">
        {publisher.gameCount} {publisher.gameCount === 1 ? "game" : "games"}
        {publisher.avgRating > 0 && (
          <>
            <Star className="h-3 w-3 text-warning-500 fill-warning-500" />
            {publisher.avgRating.toFixed(1)}
          </>
        )}
      </span>
    </div>
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
    <div
      className={cn(
        "flex items-center gap-3 rounded-lg px-2 py-1.5 transition-colors",
        highlighted ? "bg-surface-800" : "hover:bg-surface-800/50",
      )}
    >
      <div className="h-8 w-8 rounded-lg bg-surface-800 flex items-center justify-center flex-shrink-0">
        <FolderOpen className="h-4 w-4 text-surface-400" />
      </div>
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium text-surface-100 truncate">
          {collection.name}
        </p>
        <p className="text-xs text-surface-500 truncate">
          by {collection.username}
        </p>
      </div>
      <span className="text-xs text-surface-500">
        {collection.gameCount} {collection.gameCount === 1 ? "game" : "games"}
      </span>
    </div>
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
    <div
      className={cn(
        "flex items-center gap-3 rounded-lg px-2 py-1.5 transition-colors",
        highlighted ? "bg-surface-800" : "hover:bg-surface-800/50",
      )}
    >
      <div className="h-8 w-8 rounded-lg bg-surface-800 flex items-center justify-center flex-shrink-0">
        <Layers className="h-4 w-4 text-surface-400" />
      </div>
      <p className="text-sm font-medium text-surface-100 flex-1 truncate">
        {series.name}
      </p>
      <span className="text-xs text-surface-500">
        {series.libraryGames} of {series.totalGames} in library
      </span>
    </div>
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
    <div
      className={cn(
        "flex items-center gap-3 rounded-lg px-2 py-1.5 transition-colors",
        highlighted ? "bg-surface-800" : "hover:bg-surface-800/50",
      )}
    >
      <div className="h-8 w-8 rounded-lg bg-surface-800 flex items-center justify-center flex-shrink-0">
        <Crown className="h-4 w-4 text-surface-400" />
      </div>
      <p className="text-sm font-medium text-surface-100 flex-1 truncate">
        {franchise.name}
      </p>
      <span className="text-xs text-surface-500">
        {franchise.libraryGames} of {franchise.totalGames} in library
      </span>
    </div>
  );
}
