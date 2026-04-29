import { useState, useMemo } from "react";
import { Search } from "lucide-react";
import { Switch, Input, Button, Skeleton } from "@/components/ui";
import { useGameCheats } from "@/hooks/use-cheats";

interface SessionCheatSelectorProps {
  gameId: string;
  enabledIndices: number[];
  onToggleCheat: (index: number, enabled: boolean) => void;
  onSelectAll: () => void;
  onDeselectAll: () => void;
}

export function SessionCheatSelector({
  gameId,
  enabledIndices,
  onToggleCheat,
  onSelectAll,
  onDeselectAll,
}: SessionCheatSelectorProps) {
  const { data: cheats, isLoading } = useGameCheats(gameId);
  const [filter, setFilter] = useState("");

  const enabledSet = useMemo(() => new Set(enabledIndices), [enabledIndices]);

  const filteredCheats = useMemo(() => {
    if (!cheats) return [];
    if (!filter.trim()) return cheats;
    const q = filter.toLowerCase();
    return cheats.filter(
      (c) =>
        c.description.toLowerCase().includes(q) ||
        c.code.toLowerCase().includes(q),
    );
  }, [cheats, filter]);

  if (isLoading) {
    return (
      <div className="mt-4 space-y-2">
        {Array.from({ length: 3 }, (_, i) => (
          <Skeleton key={i} className="h-12 w-full rounded-lg" />
        ))}
      </div>
    );
  }

  if (!cheats || cheats.length === 0) {
    return (
      <p className="text-sm text-surface-500 mt-2">
        No cheat codes available for this game.
      </p>
    );
  }

  return (
    <div data-comp="SessionCheatSelector" className="mt-4 space-y-3">
      <div className="flex items-center gap-2">
        <div className="flex-1">
          <Input
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            placeholder="Filter cheats..."
            leftIcon={<Search className="h-4 w-4" />}
            data-testid="cheat-filter-input"
          />
        </div>
        <Button
          variant="ghost"
          size="sm"
          onClick={onSelectAll}
          data-testid="select-all-cheats"
        >
          Select All
        </Button>
        <Button
          variant="ghost"
          size="sm"
          onClick={onDeselectAll}
          data-testid="deselect-all-cheats"
        >
          Deselect All
        </Button>
      </div>

      <div className="text-xs text-surface-500">
        {enabledIndices.length} of {cheats.length} enabled
        {filter && ` (showing ${filteredCheats.length})`}
      </div>

      <div className="max-h-80 overflow-y-auto space-y-1 pr-1">
        {filteredCheats.map((cheat) => (
          <label
            key={cheat.index}
            className="flex items-center gap-3 rounded-lg bg-surface-800/50 px-3 py-2.5 cursor-pointer hover:bg-surface-800/80 transition-colors"
            data-testid={`cheat-row-${cheat.index}`}
          >
            <Switch
              checked={enabledSet.has(cheat.index)}
              onChange={(checked) => onToggleCheat(cheat.index, checked)}
              aria-label={`Toggle ${cheat.description}`}
            />
            <div className="flex-1 min-w-0">
              <p className="text-sm text-surface-200 truncate">
                {cheat.description}
              </p>
              <p className="text-xs font-mono text-surface-500 truncate">
                {cheat.code}
              </p>
            </div>
          </label>
        ))}
      </div>
    </div>
  );
}
