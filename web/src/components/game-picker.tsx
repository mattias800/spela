import { useState } from "react";
import { Gamepad2 } from "lucide-react";
import { Button, SearchInput } from "@/components/ui";
import { useGames } from "@/hooks/use-games";
import type { Game } from "@/types/api";

interface GamePickerProps {
  selectedGame: Game | null;
  onSelect: (game: Game) => void;
  onClear: () => void;
  filterFn?: (games: Game[]) => Game[];
  emptyMessage?: string;
}

export function GamePicker({
  selectedGame,
  onSelect,
  onClear,
  filterFn,
  emptyMessage = "No games found",
}: GamePickerProps) {
  const [gameSearch, setGameSearch] = useState("");

  const { data: gamesData } = useGames({
    search: gameSearch,
    pageSize: 8,
  });

  const games = gamesData?.data ?? [];
  const displayedGames = filterFn ? filterFn(games) : games;
  const showResults = gameSearch.length > 0 && !selectedGame;

  return (
    <div data-comp="GamePicker" className="space-y-1.5">
      <label className="block text-sm font-medium text-surface-300">
        Game
      </label>
      {selectedGame ? (
        <div className="flex items-center gap-3 px-3.5 py-2.5 rounded-lg bg-surface-900 border border-surface-700">
          {selectedGame.coverUrl ? (
            <img
              src={selectedGame.coverUrl}
              alt={selectedGame.title}
              className="h-10 w-8 rounded object-cover"
            />
          ) : (
            <div className="h-10 w-8 rounded bg-surface-800 flex items-center justify-center">
              <Gamepad2 className="h-4 w-4 text-surface-600" />
            </div>
          )}
          <div className="flex-1 min-w-0">
            <p className="text-sm font-medium text-surface-100 truncate">
              {selectedGame.title}
            </p>
            <p className="text-xs text-surface-500">
              {selectedGame.consoleName}
            </p>
          </div>
          <Button
            variant="ghost"
            size="sm"
            type="button"
            onClick={() => {
              onClear();
              setGameSearch("");
            }}
          >
            Change
          </Button>
        </div>
      ) : (
        <div className="relative">
          <SearchInput
            value={gameSearch}
            onChange={(e) => setGameSearch(e.target.value)}
            placeholder="Search for a game..."
          />
          {showResults && (
            <div className="absolute z-10 top-full left-0 right-0 mt-1 rounded-lg bg-surface-900 border border-surface-700 shadow-xl max-h-64 overflow-y-auto">
              {displayedGames.length === 0 ? (
                <p className="px-3 py-3 text-sm text-surface-500">
                  {emptyMessage}
                </p>
              ) : (
                displayedGames.map((game) => (
                  <button
                    key={game.id}
                    type="button"
                    className="flex items-center gap-3 w-full px-3 py-2.5 hover:bg-surface-800 transition-colors text-left focus-visible:outline-none focus-visible:bg-surface-800 focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-brand-500"
                    onClick={() => {
                      onSelect(game);
                      setGameSearch("");
                    }}
                  >
                    {game.coverUrl ? (
                      <img
                        src={game.coverUrl}
                        alt={game.title}
                        className="h-10 w-8 rounded object-cover flex-shrink-0"
                      />
                    ) : (
                      <div className="h-10 w-8 rounded bg-surface-800 flex items-center justify-center flex-shrink-0">
                        <Gamepad2 className="h-4 w-4 text-surface-600" />
                      </div>
                    )}
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-surface-100 truncate">
                        {game.title}
                      </p>
                      <p className="text-xs text-surface-500">
                        {game.consoleName}
                      </p>
                    </div>
                  </button>
                ))
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
