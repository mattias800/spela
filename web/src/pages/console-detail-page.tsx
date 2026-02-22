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

      {/* Console header */}
      <div className="flex items-center gap-4">
        <div
          className={cn(
            "h-14 w-14 rounded-2xl bg-gradient-to-br flex items-center justify-center",
            style.gradient,
          )}
        >
          {console?.iconUrl ? (
            <img src={console.iconUrl} alt={`${consoleName} icon`} className="h-8 w-8 object-contain" />
          ) : (
            <Icon className="h-7 w-7 text-white" />
          )}
        </div>
        <div>
          <h1 className="text-3xl font-bold text-surface-100">{consoleName}</h1>
          <div className="flex items-center gap-3 mt-1">
            <p className="text-surface-400">
              {gameList.length} {gameList.length === 1 ? "game" : "games"}
            </p>
            {console?.saveStateSupport && (
              <span className="inline-flex items-center gap-1 rounded-full bg-emerald-500/15 px-2.5 py-0.5 text-xs font-medium text-emerald-400">
                <Check className="h-3 w-3" />
                Save states
              </span>
            )}
            {console?.browserPlayable && (
              <span className="inline-flex items-center gap-1 rounded-full bg-blue-500/15 px-2.5 py-0.5 text-xs font-medium text-blue-400">
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
