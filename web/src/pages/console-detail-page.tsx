import { useState, useMemo } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { Library, Check, Globe } from "lucide-react";
import { GameCard } from "@/components/game-card";
import { GameGrid } from "@/components/game-grid";
import {
  BackButton,
  GameCardSkeleton,
  EmptyState,
  SearchInput,
} from "@/components/ui";
import { useConsoles, useConsoleGames } from "@/hooks/use-consoles";
import { useToggleFavorite } from "@/hooks/use-games";
import { useTogglePlayLater } from "@/hooks/use-play-later";
import { useBiosStatus } from "@/hooks/use-bios";
import { useAuth } from "@/hooks/use-auth";
import { BiosWarningBanner } from "@/features/bios/components/bios-warning-banner";
import { getConsoleStyle } from "@/lib/console-metadata";
import { cn } from "@/lib/cn";

export function ConsoleDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: games, isLoading } = useConsoleGames(id ?? "");
  const { data: consoles } = useConsoles();
  const { toggle: handleToggleFavorite } = useToggleFavorite();
  const { toggle: handleTogglePlayLater } = useTogglePlayLater();
  const [search, setSearch] = useState("");
  const { data: biosData } = useBiosStatus();
  const { isAdmin } = useAuth();

  // Find console info from the consoles list
  const console = consoles?.find((c) => c.id === id);

  const consoleName = console?.name ?? "Console";
  const consoleAbbr = console?.abbreviation ?? id ?? "";
  const gameList = games ?? [];
  const style = getConsoleStyle(consoleAbbr);
  const Icon = style.icon;

  const biosConsole = biosData?.consoles.find((c) => c.consoleId === id);
  const showBiosWarning =
    biosConsole?.status === "missing" && biosConsole.biosRequired;
  const missingBiosFiles =
    biosConsole?.files
      .filter((f) => f.status === "missing" && f.required)
      .map((f) => f.fileName) ?? [];

  const filteredGames = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return gameList;
    return gameList.filter((g) => g.title.toLowerCase().includes(q));
  }, [gameList, search]);

  return (
    <div className="space-y-6">
      {/* Back button */}
      <BackButton onClick={() => navigate(-1)}>Back to Consoles</BackButton>

      {/* Console hero banner */}
      <div
        className={cn(
          "relative overflow-hidden rounded-2xl border border-white/[0.06]",
          "bg-gradient-to-br",
          style.gradient,
        )}
      >
        {/* Background watermark icon for depth */}
        <div className="absolute -right-8 -top-8 opacity-[0.07] pointer-events-none">
          {console?.iconUrl ? (
            <img
              src={console.iconUrl}
              alt=""
              aria-hidden="true"
              className="h-56 w-56 object-contain"
            />
          ) : (
            <Icon className="h-56 w-56 text-white" />
          )}
        </div>

        {/* Subtle noise/texture overlay */}
        <div className="absolute inset-0 bg-gradient-to-t from-black/30 via-transparent to-white/[0.04] pointer-events-none" />

        {/* Content */}
        <div className="relative flex flex-col items-center px-6 py-10 md:py-12">
          {/* Logo / Title */}
          {console?.logoUrl ? (
            <img
              src={console.logoUrl}
              alt={consoleName}
              className="max-h-20 md:max-h-24 w-auto object-contain drop-shadow-[0_2px_12px_rgba(0,0,0,0.4)]"
              onError={(e) => {
                // Fall back to text if logo fails to load
                const target = e.currentTarget;
                target.style.display = "none";
                const fallback = target.nextElementSibling;
                if (fallback) fallback.classList.remove("hidden");
              }}
            />
          ) : null}
          <h1
            className={cn(
              "text-4xl md:text-5xl font-bold text-white tracking-tight drop-shadow-lg",
              console?.logoUrl && "hidden",
            )}
          >
            {consoleName}
          </h1>

          {/* Metadata row */}
          <div className="flex flex-wrap items-center justify-center gap-3 mt-4">
            <span className="text-sm font-medium text-white/70">
              {gameList.length} {gameList.length === 1 ? "game" : "games"}
            </span>
            {console?.saveStateSupport && (
              <span className="inline-flex items-center gap-1.5 rounded-full bg-white/10 backdrop-blur-sm px-3 py-1 text-xs font-medium text-white/90">
                <Check className="h-3 w-3" />
                Save states
              </span>
            )}
            {console?.browserPlayable && (
              <span className="inline-flex items-center gap-1.5 rounded-full bg-white/10 backdrop-blur-sm px-3 py-1 text-xs font-medium text-white/90">
                <Globe className="h-3 w-3" />
                Browser play
              </span>
            )}
          </div>
        </div>
      </div>

      {/* BIOS warning */}
      {showBiosWarning && (
        <BiosWarningBanner
          message={`Missing BIOS: ${consoleName} requires firmware files to play games.`}
          isAdmin={isAdmin}
          missingFiles={missingBiosFiles}
        />
      )}

      {/* Search */}
      {gameList.length > 5 && (
        <SearchInput
          placeholder={`Search ${consoleName} games...`}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="max-w-sm"
        />
      )}

      {/* Games grid */}
      {isLoading ? (
        <GameGrid>
          {Array.from({ length: 12 }, (_, i) => (
            <GameCardSkeleton
              key={i}
              aspectRatio={console?.coverAspectRatio}
            />
          ))}
        </GameGrid>
      ) : filteredGames.length === 0 ? (
        <EmptyState
          icon={Library}
          title={search.trim() ? "No matching games" : "No games found"}
          description={
            search.trim()
              ? `No games matching "${search.trim()}" for this console.`
              : "No games have been detected for this console yet."
          }
        />
      ) : (
        <GameGrid>
          {filteredGames.map((game) => (
            <GameCard
              key={game.id}
              game={game}
              aspectRatio={console?.coverAspectRatio}
              onToggleFavorite={handleToggleFavorite}
              onTogglePlayLater={handleTogglePlayLater}
            />
          ))}
        </GameGrid>
      )}
    </div>
  );
}
