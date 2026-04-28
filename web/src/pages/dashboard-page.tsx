import { useState } from "react";
import {
  Play,
  Heart,
  Clock,
  Gamepad2,
  Trophy,
  Flag,
  Sparkles,
  ChevronRight,
} from "lucide-react";
import { Link } from "react-router-dom";
import { GameCard } from "@/components/game-card";
import { ScrollShelf } from "@/components/scroll-shelf";
import { CAROUSEL_CARD_HEIGHT } from "@/lib/carousel-constants";
import { Badge, Skeleton, EmptyState, TitledSection } from "@/components/ui";
import { PageLayout, SectionList } from "@/components/layout";
import { PersonalStatsCard } from "@/features/dashboard/components/personal-stats-card";
import {
  useRecentGames,
  useFavoriteGames,
  useToggleFavorite,
  useGames,
} from "@/hooks/use-games";
import { useBiosStatus } from "@/hooks/use-bios";
import { TopRatedRow } from "@/features/dashboard/components/top-rated-row";
import { BiosWarningBanner } from "@/features/bios/components/bios-warning-banner";
import { useIgdbStatus } from "@/hooks/use-admin";
import { IgdbWarningBanner } from "@/features/admin/components/igdb-warning-banner";
import { usePlayLaterGames, useTogglePlayLater } from "@/hooks/use-play-later";
import { useRecentAchievements } from "@/hooks/use-retroachievements";
import { useAuth } from "@/hooks/use-auth";
import { ActivityFeed } from "@/features/social/components/activity-feed";
import { OnlineUsers } from "@/features/social/components/online-users";
import { ChallengeCard } from "@/features/challenges/components/challenge-card";
import { useChallenges } from "@/hooks/use-challenges";
import { formatRelativeTime } from "@/lib/format";
import type { RecentAchievement } from "@/types/api";


function RecentAchievementsSkeleton() {
  return (
    <div data-comp="RecentAchievementsSkeleton" className="flex gap-4 overflow-x-auto pb-2">
      {Array.from({ length: 4 }, (_, i) => (
        <div
          key={i}
          className="flex-shrink-0 w-48 rounded-xl bg-surface-800/50 p-4 space-y-3"
        >
          <Skeleton className="h-12 w-12 rounded" />
          <Skeleton className="h-4 w-32" />
          <Skeleton className="h-3 w-24" />
          <Skeleton className="h-3 w-16" />
        </div>
      ))}
    </div>
  );
}

function RecentAchievementCard({
  achievement,
}: {
  achievement: RecentAchievement;
}) {
  return (
    <Link
      to={`/games/${achievement.gameId}`}
      className="flex-shrink-0 w-48 rounded-xl bg-surface-800/50 p-4 hover:bg-surface-800/80 transition-colors"
      data-testid={`recent-achievement-${achievement.achievementRaId}`}
    >
      <img
        src={achievement.badgeUrl}
        alt={achievement.title}
        className="h-12 w-12 rounded"
      />
      <p className="text-sm font-medium text-surface-100 truncate mt-3">
        {achievement.title}
      </p>
      <p className="text-xs text-surface-500 truncate">
        {achievement.gameTitle}
      </p>
      <div className="flex items-center gap-2 mt-2">
        <span className="text-xs text-surface-400">
          {formatRelativeTime(achievement.unlockedAt)}
        </span>
        <Badge variant="brand" className="text-[10px] px-1.5 py-0">
          {achievement.points} pts
        </Badge>
      </div>
      {achievement.isHardcore && (
        <Badge variant="warning" className="text-[10px] px-1.5 py-0 mt-1">
          Hardcore
        </Badge>
      )}
    </Link>
  );
}

function RecentAchievementsSection() {
  const { data: achievements, isLoading } = useRecentAchievements();

  const renderRight = (
    <Link
      to="/stats"
      className="flex items-center gap-1 text-sm text-surface-400 hover:text-brand-400 transition-colors"
    >
      View all
      <ChevronRight className="h-4 w-4" />
    </Link>
  );

  if (isLoading) {
    return (
      <div data-testid="recent-achievements-section">
        <TitledSection
          title="Recent Achievements"
          icon={Trophy}
          renderRight={renderRight}
        >
          <RecentAchievementsSkeleton />
        </TitledSection>
      </div>
    );
  }

  if (!achievements || achievements.length === 0) {
    return null;
  }

  return (
    <div data-comp="RecentAchievementsSection" data-testid="recent-achievements-section">
      <TitledSection
        title="Recent Achievements"
        icon={Trophy}
        renderRight={renderRight}
      >
        <div className="flex gap-4 overflow-x-auto pb-2">
          {achievements.slice(0, 5).map((achievement) => (
            <RecentAchievementCard
              key={achievement.achievementRaId}
              achievement={achievement}
            />
          ))}
        </div>
      </TitledSection>
    </div>
  );
}

function TrendingChallengesSection() {
  const { data, isLoading } = useChallenges({
    sortBy: "most_attempted",
    pageSize: 4,
    status: "active",
  });

  const renderRight = (
    <Link
      to="/challenges"
      className="flex items-center gap-1 text-sm text-surface-400 hover:text-brand-400 transition-colors"
    >
      View all
      <ChevronRight className="h-4 w-4" />
    </Link>
  );

  if (isLoading) {
    return (
      <div data-testid="trending-challenges-section">
        <TitledSection
          title="Trending Challenges"
          icon={Flag}
          renderRight={renderRight}
        >
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-5">
            {Array.from({ length: 4 }, (_, i) => (
              <div key={i} className="space-y-3">
                <Skeleton className="aspect-[16/10] rounded-2xl" />
                <div className="px-1 space-y-1.5">
                  <Skeleton className="h-4 w-32" />
                  <Skeleton className="h-3 w-48" />
                </div>
              </div>
            ))}
          </div>
        </TitledSection>
      </div>
    );
  }

  if (!data?.data || data.data.length === 0) return null;

  return (
    <div data-comp="TrendingChallengesSection" data-testid="trending-challenges-section">
      <TitledSection
        title="Trending Challenges"
        icon={Flag}
        renderRight={renderRight}
      >
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-5">
          {data.data.slice(0, 4).map((challenge) => (
            <ChallengeCard key={challenge.id} challenge={challenge} />
          ))}
        </div>
      </TitledSection>
    </div>
  );
}

export function DashboardPage() {
  const { user, isAdmin } = useAuth();
  const { data: biosData } = useBiosStatus();
  const { data: igdbStatus } = useIgdbStatus();
  const [biosDismissed, setBiosDismissed] = useState(false);
  const [igdbDismissed, setIgdbDismissed] = useState(false);
  const hasMissingBios =
    isAdmin &&
    !biosDismissed &&
    (biosData?.consoles?.some((c) => c.status === "missing") ?? false);
  const showIgdbWarning =
    isAdmin && !igdbDismissed && igdbStatus && !igdbStatus.configured;
  const recentGames = useRecentGames();
  const favoriteGames = useFavoriteGames();
  const playLaterGames = usePlayLaterGames();
  const recentlyAddedGames = useGames({
    pageSize: 12,
    sortBy: "created_at",
    sortOrder: "desc",
  });
  const allGames = useGames({
    pageSize: 12,
    sortBy: "title",
    sortOrder: "asc",
  });
  const { toggle: handleToggleFavorite } = useToggleFavorite();
  const { toggle: handleTogglePlayLater } = useTogglePlayLater();

  const hasRecent = recentGames.data && recentGames.data.length > 0;
  const hasPlayLater = playLaterGames.data && playLaterGames.data.length > 0;
  const hasFavorites = favoriteGames.data && favoriteGames.data.length > 0;
  const hasRecentlyAdded =
    recentlyAddedGames.data &&
    recentlyAddedGames.data.data &&
    recentlyAddedGames.data.data.length > 0;
  const hasGames =
    allGames.data && allGames.data.data && allGames.data.data.length > 0;
  const isLoading =
    recentGames.isLoading || favoriteGames.isLoading || allGames.isLoading;
  const showEmptyState = !isLoading && !hasRecent && !hasFavorites && !hasGames;

  return (
    <PageLayout
      title={`Welcome back, ${user?.username}`}
      subtitle="Pick up where you left off or discover something new."
    >
      <SectionList>
      {hasMissingBios && (
        <BiosWarningBanner
          message="Some consoles are missing required BIOS files. Games may not work correctly."
          isAdmin={true}
          onDismiss={() => setBiosDismissed(true)}
        />
      )}

      {showIgdbWarning && (
        <IgdbWarningBanner
          variant="dashboard"
          onDismiss={() => setIgdbDismissed(true)}
        />
      )}

      <PersonalStatsCard />

      <RecentAchievementsSection />

      <TrendingChallengesSection />

      <TopRatedRow />

      {showEmptyState && (
        <EmptyState
          icon={Play}
          title="No games yet"
          description="Your game library is empty. Ask an admin to scan for games or add some ROMs to get started."
        />
      )}

      {(recentGames.isLoading || hasRecent) && (
        <ScrollShelf
          title="Continue Playing"
          icon={Play}
          testId="shelf-continue-playing"
          isLoading={recentGames.isLoading}
          isEmpty={!recentGames.data || recentGames.data.length === 0}
          headerRight={
            <Link to="/games" className="flex items-center gap-1 text-sm text-surface-400 hover:text-brand-400 transition-colors">
              View all<ChevronRight className="h-4 w-4" />
            </Link>
          }
        >
          {recentGames.data?.map((game) => (
            <div key={game.id} className="flex-shrink-0" role="listitem">
              <GameCard
                game={game}
                coverHeight={CAROUSEL_CARD_HEIGHT}
                onToggleFavorite={handleToggleFavorite}
                onTogglePlayLater={handleTogglePlayLater}
              />
            </div>
          ))}
        </ScrollShelf>
      )}

      {(playLaterGames.isLoading || hasPlayLater) && (
        <ScrollShelf
          title="Play Later"
          icon={Clock}
          testId="shelf-play-later"
          isLoading={playLaterGames.isLoading}
          isEmpty={!playLaterGames.data || playLaterGames.data.length === 0}
          headerRight={
            <Link to="/play-later" className="flex items-center gap-1 text-sm text-surface-400 hover:text-brand-400 transition-colors">
              View all<ChevronRight className="h-4 w-4" />
            </Link>
          }
        >
          {playLaterGames.data?.map((game) => (
            <div key={game.id} className="flex-shrink-0" role="listitem">
              <GameCard
                game={game}
                coverHeight={CAROUSEL_CARD_HEIGHT}
                onToggleFavorite={handleToggleFavorite}
                onTogglePlayLater={handleTogglePlayLater}
              />
            </div>
          ))}
        </ScrollShelf>
      )}

      {(favoriteGames.isLoading || hasFavorites) && (
        <ScrollShelf
          title="Favorites"
          icon={Heart}
          testId="shelf-favorites"
          isLoading={favoriteGames.isLoading}
          isEmpty={!favoriteGames.data || favoriteGames.data.length === 0}
          headerRight={
            <Link to="/favorites" className="flex items-center gap-1 text-sm text-surface-400 hover:text-brand-400 transition-colors">
              View all<ChevronRight className="h-4 w-4" />
            </Link>
          }
        >
          {favoriteGames.data?.map((game) => (
            <div key={game.id} className="flex-shrink-0" role="listitem">
              <GameCard
                game={game}
                coverHeight={CAROUSEL_CARD_HEIGHT}
                onToggleFavorite={handleToggleFavorite}
                onTogglePlayLater={handleTogglePlayLater}
              />
            </div>
          ))}
        </ScrollShelf>
      )}

      {(recentlyAddedGames.isLoading || hasRecentlyAdded) && (
        <ScrollShelf
          title="Recently Added"
          icon={Sparkles}
          testId="shelf-recently-added"
          isLoading={recentlyAddedGames.isLoading}
          isEmpty={!recentlyAddedGames.data?.data || recentlyAddedGames.data.data.length === 0}
          headerRight={
            <Link to="/games" className="flex items-center gap-1 text-sm text-surface-400 hover:text-brand-400 transition-colors">
              View all<ChevronRight className="h-4 w-4" />
            </Link>
          }
        >
          {recentlyAddedGames.data?.data?.map((game) => (
            <div key={game.id} className="flex-shrink-0" role="listitem">
              <GameCard
                game={game}
                coverHeight={CAROUSEL_CARD_HEIGHT}
                onToggleFavorite={handleToggleFavorite}
                onTogglePlayLater={handleTogglePlayLater}
              />
            </div>
          ))}
        </ScrollShelf>
      )}

      {!hasRecent && !hasFavorites && (allGames.isLoading || hasGames) && (
        <ScrollShelf
          title="Discover"
          icon={Gamepad2}
          testId="shelf-discover"
          isLoading={allGames.isLoading}
          isEmpty={!allGames.data?.data || allGames.data.data.length === 0}
          headerRight={
            <Link to="/games" className="flex items-center gap-1 text-sm text-surface-400 hover:text-brand-400 transition-colors">
              View all<ChevronRight className="h-4 w-4" />
            </Link>
          }
        >
          {allGames.data?.data?.map((game) => (
            <div key={game.id} className="flex-shrink-0" role="listitem">
              <GameCard
                game={game}
                coverHeight={CAROUSEL_CARD_HEIGHT}
                onToggleFavorite={handleToggleFavorite}
                onTogglePlayLater={handleTogglePlayLater}
              />
            </div>
          ))}
        </ScrollShelf>
      )}

      {/* Social widgets */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        <div className="lg:col-span-2">
          <ActivityFeed compact maxItems={5} />
        </div>
        <div className="lg:col-span-1">
          <div className="rounded-2xl bg-surface-900/50 p-5">
            <OnlineUsers />
          </div>
        </div>
      </div>
      </SectionList>
    </PageLayout>
  );
}

export default DashboardPage;
