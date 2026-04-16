import { useParams } from "react-router-dom";
import { AlertCircle, Library } from "lucide-react";
import { GameCard } from "@/components/game-card";
import { GameGrid } from "@/components/game-grid";
import {
  Button,
  GameCardSkeleton,
  EmptyState,
} from "@/components/ui";
import { PageLayout, SectionList } from "@/components/layout";
import { useMoods, useMoodGames } from "@/hooks/use-explore";
import { useToggleFavorite } from "@/hooks/use-games";
import { useTogglePlayLater } from "@/hooks/use-play-later";
import { buildGradient } from "@/features/explore/utils/gradient";

export function ExploreMoodPage() {
  const { mood: moodId } = useParams<{ mood: string }>();
  const { toggle: handleToggleFavorite } = useToggleFavorite();
  const { toggle: handleTogglePlayLater } = useTogglePlayLater();

  const { data: moods } = useMoods();
  const { data: games, isLoading, isError, refetch } = useMoodGames(moodId);

  const mood = moods?.find((m) => m.id === moodId);
  const moodName = mood?.name ?? "Mood";
  const moodDescription = mood?.description ?? "";
  const moodIcon = mood?.icon ?? "";
  const moodGradient = mood?.gradient ?? [];

  return (
    <PageLayout backButtonVariant="standard" backTo="/explore" backLabel="Explore" data-testid="mood-results-page">
      <SectionList>

      {/* Mood banner header */}
      <div
        className="relative rounded-2xl overflow-hidden p-6 sm:p-8"
        style={{ background: buildGradient(moodGradient) }}
      >
        <div className="absolute inset-0 bg-gradient-to-t from-black/40 via-transparent to-white/[0.04] pointer-events-none" />
        <div className="relative flex items-center gap-4">
          {moodIcon && (
            <span className="text-4xl sm:text-5xl leading-none">
              {moodIcon}
            </span>
          )}
          <div>
            <h1 className="text-2xl sm:text-3xl font-bold text-white">
              {moodName}
            </h1>
            {moodDescription && (
              <p className="mt-1 text-sm text-white/70">{moodDescription}</p>
            )}
          </div>
        </div>
      </div>

      {/* Games grid */}
      {isLoading ? (
        <GameGrid>
          {Array.from({ length: 12 }, (_, i) => (
            <GameCardSkeleton key={i} />
          ))}
        </GameGrid>
      ) : isError ? (
        <EmptyState
          icon={AlertCircle}
          title="Something went wrong"
          description="Could not load games for this mood."
          action={
            <Button variant="primary" onClick={() => refetch()}>
              Try again
            </Button>
          }
        />
      ) : !games || games.length === 0 ? (
        <EmptyState
          icon={Library}
          title="No games found"
          description="No games match this mood yet."
        />
      ) : (
        <GameGrid>
          {games.map((game) => (
            <GameCard
              key={game.id}
              game={game}
              showConsoleBadge
              onToggleFavorite={handleToggleFavorite}
              onTogglePlayLater={handleTogglePlayLater}
            />
          ))}
        </GameGrid>
      )}
    </SectionList>
    </PageLayout>
  );
}

export default ExploreMoodPage;
