import { useState, useMemo } from "react";
import {
  X,
  Search,
  Check,
  ChevronUp,
  ChevronDown,
  Trophy,
  Loader2,
} from "lucide-react";
import { cn } from "@/lib/cn";
import {
  useOwnShowcase,
  useUnlockedAchievements,
  useUpdateShowcase,
} from "@/hooks/use-showcase";
import type { ShowcaseAchievement, UnlockedAchievement } from "@/types/api";

const MAX_SHOWCASE = 5;

function getRarityColor(percent: number): string {
  if (percent < 1) return "text-purple-400";
  if (percent < 5) return "text-purple-400";
  if (percent < 20) return "text-yellow-400";
  if (percent <= 50) return "text-gray-400";
  return "text-surface-500";
}

export function ShowcaseEditor({ onClose }: { onClose: () => void }) {
  const { data: currentShowcase } = useOwnShowcase();
  const { data: unlockedData, isLoading } = useUnlockedAchievements();
  const updateMutation = useUpdateShowcase();

  const [selections, setSelections] = useState<ShowcaseAchievement[]>(
    () => currentShowcase ?? [],
  );
  const [searchQuery, setSearchQuery] = useState("");

  const selectedIds = useMemo(
    () => new Set(selections.map((s) => s.achievementRaId)),
    [selections],
  );

  const filtered = useMemo(() => {
    const all = unlockedData?.achievements ?? [];
    const sorted = [...all].sort((a, b) => a.rarityPercent - b.rarityPercent);
    if (!searchQuery.trim()) return sorted;
    const q = searchQuery.toLowerCase();
    return sorted.filter(
      (a) =>
        a.title.toLowerCase().includes(q) ||
        a.gameTitle.toLowerCase().includes(q),
    );
  }, [unlockedData, searchQuery]);

  function toggleAchievement(a: UnlockedAchievement) {
    if (selectedIds.has(a.achievementRaId)) {
      setSelections((prev) =>
        prev.filter((s) => s.achievementRaId !== a.achievementRaId),
      );
    } else if (selections.length < MAX_SHOWCASE) {
      setSelections((prev) => [
        ...prev,
        {
          achievementRaId: a.achievementRaId,
          raGameId: a.raGameId,
          showcaseOrder: prev.length,
          title: a.title,
          description: a.description,
          points: a.points,
          badgeUrl: a.badgeUrl,
          rarityPercent: a.rarityPercent,
          gameTitle: a.gameTitle,
        },
      ]);
    }
  }

  function moveSelection(index: number, direction: number) {
    const newIndex = index + direction;
    if (newIndex < 0 || newIndex >= selections.length) return;
    setSelections((prev) => {
      const list = [...prev];
      const [item] = list.splice(index, 1);
      list.splice(newIndex, 0, item);
      return list;
    });
  }

  function handleSave() {
    const entries = selections.map((s) => ({
      achievementRaId: s.achievementRaId,
      raGameId: s.raGameId,
    }));
    updateMutation.mutate(entries, { onSuccess: () => onClose() });
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60">
      <div className="bg-surface-900 rounded-2xl w-full max-w-lg mx-4 max-h-[80vh] flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between p-5 border-b border-surface-800">
          <div>
            <h2 className="text-lg font-bold text-surface-100">
              Edit Showcase
            </h2>
            <p className="text-xs text-surface-500 mt-0.5">
              {selections.length}/{MAX_SHOWCASE} selected
            </p>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg hover:bg-surface-800 text-surface-400 hover:text-surface-200 transition-colors"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Current selections */}
        {selections.length > 0 && (
          <div className="p-4 border-b border-surface-800 space-y-1.5">
            <p className="text-xs font-medium text-surface-500 uppercase tracking-wider mb-2">
              Your Showcase
            </p>
            {selections.map((entry, index) => (
              <div
                key={entry.achievementRaId}
                className="flex items-center gap-2 rounded-lg bg-surface-800/50 px-3 py-2"
              >
                <span className="flex-1 text-sm text-surface-200 truncate">
                  {entry.title || "Unknown"}
                </span>
                <span className="text-xs text-surface-500 truncate max-w-[100px]">
                  {entry.gameTitle}
                </span>
                <button
                  onClick={() => moveSelection(index, -1)}
                  disabled={index === 0}
                  className="p-0.5 text-surface-400 hover:text-surface-200 disabled:opacity-30"
                >
                  <ChevronUp className="h-4 w-4" />
                </button>
                <button
                  onClick={() => moveSelection(index, 1)}
                  disabled={index === selections.length - 1}
                  className="p-0.5 text-surface-400 hover:text-surface-200 disabled:opacity-30"
                >
                  <ChevronDown className="h-4 w-4" />
                </button>
              </div>
            ))}
          </div>
        )}

        {/* Search */}
        <div className="px-4 py-3 border-b border-surface-800">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-surface-500" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search achievements..."
              className="w-full pl-9 pr-3 py-2 rounded-lg bg-surface-800 text-sm text-surface-200 placeholder:text-surface-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
            />
          </div>
        </div>

        {/* Achievement list */}
        <div className="flex-1 overflow-y-auto p-2 min-h-0">
          {isLoading ? (
            <div className="flex items-center justify-center py-12">
              <Loader2 className="h-6 w-6 animate-spin text-brand-400" />
            </div>
          ) : filtered.length === 0 ? (
            <p className="text-center text-sm text-surface-500 py-8">
              {searchQuery
                ? "No achievements match your search"
                : "No unlocked achievements yet"}
            </p>
          ) : (
            <div className="space-y-0.5">
              {filtered.map((a) => {
                const isSelected = selectedIds.has(a.achievementRaId);
                const canSelect = isSelected || selections.length < MAX_SHOWCASE;
                return (
                  <button
                    key={a.achievementRaId}
                    onClick={() => toggleAchievement(a)}
                    disabled={!canSelect}
                    className={cn(
                      "w-full flex items-center gap-3 rounded-lg px-3 py-2.5 text-left transition-colors",
                      isSelected
                        ? "bg-brand-500/10"
                        : "hover:bg-surface-800/50",
                      !canSelect && "opacity-40 cursor-not-allowed",
                    )}
                  >
                    {/* Checkbox */}
                    <div
                      className={cn(
                        "w-5 h-5 rounded-full flex-shrink-0 flex items-center justify-center border",
                        isSelected
                          ? "bg-brand-500 border-brand-500"
                          : "border-surface-600",
                      )}
                    >
                      {isSelected && (
                        <Check className="h-3 w-3 text-white" />
                      )}
                    </div>

                    {/* Badge */}
                    <div className="w-8 h-8 rounded bg-surface-800 overflow-hidden flex-shrink-0">
                      {a.badgeUrl ? (
                        <img
                          src={a.badgeUrl}
                          alt=""
                          className="w-full h-full object-cover"
                        />
                      ) : (
                        <div className="w-full h-full flex items-center justify-center">
                          <Trophy className="h-4 w-4 text-surface-600" />
                        </div>
                      )}
                    </div>

                    {/* Info */}
                    <div className="flex-1 min-w-0">
                      <p className="text-sm text-surface-200 truncate">
                        {a.title}
                      </p>
                      <div className="flex items-center gap-2">
                        <span className="text-xs text-surface-500 truncate">
                          {a.gameTitle}
                        </span>
                        {a.rarityPercent > 0 && a.rarityPercent < 50 && (
                          <span
                            className={cn(
                              "text-xs",
                              getRarityColor(a.rarityPercent),
                            )}
                          >
                            {a.rarityPercent.toFixed(1)}%
                          </span>
                        )}
                      </div>
                    </div>

                    {/* Points */}
                    <span className="text-xs text-surface-500 flex-shrink-0">
                      {a.points} pts
                    </span>
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end gap-3 p-4 border-t border-surface-800">
          <button
            onClick={onClose}
            className="px-4 py-2 text-sm text-surface-400 hover:text-surface-200 transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={handleSave}
            disabled={updateMutation.isPending}
            className="px-4 py-2 text-sm font-medium bg-brand-500 text-white rounded-lg hover:bg-brand-600 disabled:opacity-50 transition-colors"
          >
            {updateMutation.isPending ? "Saving..." : "Save"}
          </button>
        </div>
      </div>
    </div>
  );
}
