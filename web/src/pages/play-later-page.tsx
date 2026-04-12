import { Clock, ChevronUp, ChevronDown, X, AlertCircle } from "lucide-react";
import { Link } from "react-router-dom";
import { Button, Section, Skeleton, EmptyState } from "@/components/ui";
import { PageLayout, SectionList } from "@/components/layout";
import {
  usePlayLaterGames,
  useRemoveFromPlayLater,
  useReorderPlayLater,
} from "@/hooks/use-play-later";
import { CoverImage } from "@/components/cover-image";
import type { Game } from "@/types/api";

function PlayLaterSkeleton() {
  return (
    <div className="space-y-2">
      {Array.from({ length: 5 }, (_, i) => (
        <div
          key={i}
          className="flex items-center gap-4 rounded-xl bg-surface-900/50 p-4"
        >
          <Skeleton className="h-20 w-14 rounded-lg flex-shrink-0" />
          <div className="flex-1 space-y-2">
            <Skeleton className="h-5 w-48" />
            <Skeleton className="h-4 w-24" />
          </div>
          <div className="flex gap-1">
            <Skeleton className="h-8 w-8 rounded" />
            <Skeleton className="h-8 w-8 rounded" />
            <Skeleton className="h-8 w-8 rounded" />
          </div>
        </div>
      ))}
    </div>
  );
}

function PlayLaterItem({
  game,
  index,
  total,
  onMoveUp,
  onMoveDown,
  onRemove,
}: {
  game: Game;
  index: number;
  total: number;
  onMoveUp: () => void;
  onMoveDown: () => void;
  onRemove: () => void;
}) {
  return (
    <Section hover className="flex items-center gap-4 p-4">
      <Link to={`/games/${game.id}`} className="flex-shrink-0">
        <CoverImage src={game.coverUrl} alt={game.title} className="h-20 w-14 rounded-lg" />
      </Link>

      <div className="flex-1 min-w-0">
        <Link
          to={`/games/${game.id}`}
          className="text-sm font-semibold text-surface-100 hover:text-brand-400 transition-colors truncate block"
        >
          {game.title}
        </Link>
        {game.consoleName && (
          <p className="text-xs text-surface-500 mt-1">{game.consoleName}</p>
        )}
      </div>

      <div className="flex items-center gap-1 flex-shrink-0">
        <button
          onClick={onMoveUp}
          disabled={index === 0}
          className="p-1.5 rounded-lg text-surface-400 hover:text-surface-100 hover:bg-surface-800 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
          title="Move up"
        >
          <ChevronUp className="h-4 w-4" />
        </button>
        <button
          onClick={onMoveDown}
          disabled={index === total - 1}
          className="p-1.5 rounded-lg text-surface-400 hover:text-surface-100 hover:bg-surface-800 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
          title="Move down"
        >
          <ChevronDown className="h-4 w-4" />
        </button>
        <button
          onClick={onRemove}
          className="p-1.5 rounded-lg text-surface-400 hover:text-danger-400 hover:bg-danger-500/10 transition-colors"
          title="Remove from queue"
        >
          <X className="h-4 w-4" />
        </button>
      </div>
    </Section>
  );
}

export function PlayLaterPage() {
  const { data: games, isLoading, isError, refetch } = usePlayLaterGames();
  const removeFromPlayLater = useRemoveFromPlayLater();
  const reorderPlayLater = useReorderPlayLater();

  function handleMoveUp(index: number) {
    if (!games || index === 0) return;
    const newOrder = [...games];
    [newOrder[index - 1], newOrder[index]] = [
      newOrder[index],
      newOrder[index - 1],
    ];
    reorderPlayLater.mutate(newOrder.map((g) => g.id));
  }

  function handleMoveDown(index: number) {
    if (!games || index >= games.length - 1) return;
    const newOrder = [...games];
    [newOrder[index], newOrder[index + 1]] = [
      newOrder[index + 1],
      newOrder[index],
    ];
    reorderPlayLater.mutate(newOrder.map((g) => g.id));
  }

  function handleRemove(gameId: string) {
    removeFromPlayLater.mutate(gameId);
  }

  return (
    <PageLayout>
      <SectionList>
      <div>
        <h1 className="text-3xl font-bold text-surface-100">Play Later</h1>
        <p className="mt-1 text-surface-400">
          Your backlog — games you want to play next.
        </p>
      </div>

      {isLoading ? (
        <PlayLaterSkeleton />
      ) : isError ? (
        <EmptyState
          icon={AlertCircle}
          title="Failed to load queue"
          description="Something went wrong loading your Play Later queue."
          action={
            <Button variant="secondary" size="sm" onClick={() => refetch()}>
              Try again
            </Button>
          }
        />
      ) : !games || games.length === 0 ? (
        <EmptyState
          icon={Clock}
          title="Your Play Later queue is empty"
          description="Click the clock icon on any game to add it."
        />
      ) : (
        <div className="space-y-2">
          {games.map((game, index) => (
            <PlayLaterItem
              key={game.id}
              game={game}
              index={index}
              total={games.length}
              onMoveUp={() => handleMoveUp(index)}
              onMoveDown={() => handleMoveDown(index)}
              onRemove={() => handleRemove(game.id)}
            />
          ))}
        </div>
      )}
    </SectionList>
    </PageLayout>
  );
}
