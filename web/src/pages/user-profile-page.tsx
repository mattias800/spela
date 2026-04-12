import { useParams, Link } from "react-router-dom";
import { PageLayout, SectionList } from "@/components/layout";
import { User, Clock, Calendar, Gamepad2, Heart, Trophy } from "lucide-react";
import { usePublicProfile } from "@/hooks/use-public-profile";
import { useUserPlayHeatmap } from "@/hooks/use-play-heatmap";
import { PlayerAvatar } from "@/components/player-avatar";
import { PlayHeatmap } from "@/components/play-heatmap";
import { AchievementShowcase } from "@/features/user-profile/components/achievement-showcase";
import { Badge, Skeleton, EmptyState } from "@/components/ui";
import type { PublicProfileGame } from "@/types/api";

function formatPlayTime(seconds: number): string {
  if (seconds < 60) return "< 1 min";
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  if (hours > 0) return `${hours}h ${minutes}m`;
  return `${minutes}m`;
}

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString(undefined, {
    year: "numeric",
    month: "long",
  });
}

function ProfileGameCard({ game }: { game: PublicProfileGame }) {
  const coverUrl = game.coverUrl || "";
  return (
    <Link
      to={`/games/${game.id}`}
      className="group flex items-center gap-3 rounded-xl px-3 py-2.5 hover:bg-surface-800/50 transition-colors"
    >
      {coverUrl ? (
        <img
          src={coverUrl}
          alt={game.title}
          className="h-12 w-9 rounded object-cover flex-shrink-0"
        />
      ) : (
        <div className="h-12 w-9 rounded bg-surface-700 flex-shrink-0" />
      )}
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-surface-200 truncate group-hover:text-brand-400 transition-colors">
          {game.title}
        </p>
        <div className="flex items-center gap-2">
          <span className="text-xs text-surface-500">{game.consoleName}</span>
          {game.playTime != null && game.playTime > 0 && (
            <span className="text-xs text-surface-500">
              {formatPlayTime(game.playTime)}
            </span>
          )}
        </div>
      </div>
    </Link>
  );
}

function ProfileSection({
  title,
  icon: Icon,
  children,
}: {
  title: string;
  icon: typeof Heart;
  children: React.ReactNode;
}) {
  return (
    <div className="rounded-2xl bg-surface-900/50 p-5">
      <div className="flex items-center gap-2.5 mb-4">
        <Icon className="h-5 w-5 text-brand-400" />
        <h2 className="text-lg font-bold text-surface-100">{title}</h2>
      </div>
      {children}
    </div>
  );
}

function ProfileSkeleton() {
  return (
    <div className="space-y-8">
      <div className="flex items-center gap-6">
        <Skeleton className="h-20 w-20 rounded-full" />
        <div className="space-y-2">
          <Skeleton className="h-8 w-48" />
          <Skeleton className="h-4 w-32" />
        </div>
      </div>
      <div className="grid grid-cols-2 gap-4">
        <Skeleton className="h-24 rounded-2xl" />
        <Skeleton className="h-24 rounded-2xl" />
      </div>
    </div>
  );
}

export function UserProfilePage() {
  const { id } = useParams<{ id: string }>();
  const { data: profile, isLoading, error } = usePublicProfile(id || "");
  const { data: heatmapData, isLoading: isLoadingHeatmap } =
    useUserPlayHeatmap(id || "");

  if (isLoading) {
    return (
      <PageLayout>
        <SectionList className="max-w-4xl mx-auto">
          <ProfileSkeleton />
        </SectionList>
      </PageLayout>
    );
  }

  if (error || !profile) {
    return (
      <EmptyState
        icon={User}
        title="User not found"
        description="This user profile doesn't exist or is unavailable."
      />
    );
  }

  return (
    <PageLayout>
      <SectionList className="max-w-4xl mx-auto">
      {/* Header */}
      <div className="flex items-start gap-6">
        <div className="relative">
          <PlayerAvatar
            username={profile.username}
            avatarUrl={profile.avatarUrl}
            size="lg"
          />
          {profile.isOnline && (
            <span className="absolute -bottom-1 -right-1 h-5 w-5 rounded-full bg-green-500 border-3 border-surface-950" />
          )}
        </div>
        <div className="flex-1">
          <div className="flex items-center gap-3">
            <h1 className="text-3xl font-bold text-surface-100">
              {profile.username}
            </h1>
            {profile.isOnline && <Badge variant="brand">Online</Badge>}
          </div>
          <p className="text-surface-400 mt-1">
            Member since {formatDate(profile.memberSince)}
          </p>
          {profile.isOnline && profile.currentGame && (
            <Link
              to={`/games/${profile.currentGame.id}`}
              className="flex items-center gap-2 mt-2 text-sm text-surface-300 hover:text-brand-400 transition-colors"
            >
              <Gamepad2 className="h-4 w-4 text-brand-400" />
              Playing {profile.currentGame.title}
              <Badge variant="brand" className="text-[10px] px-1.5 py-0">
                {profile.currentGame.consoleName}
              </Badge>
            </Link>
          )}
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 gap-4">
        <div className="rounded-2xl bg-surface-900/50 p-5 text-center">
          <p className="text-3xl font-bold text-brand-400">
            {formatPlayTime(profile.totalPlayTime)}
          </p>
          <p className="text-sm text-surface-400 mt-1">Total Play Time</p>
        </div>
        <div className="rounded-2xl bg-surface-900/50 p-5 text-center">
          <p className="text-3xl font-bold text-brand-400">
            {profile.gamesPlayed}
          </p>
          <p className="text-sm text-surface-400 mt-1">Games Played</p>
        </div>
      </div>

      {/* Activity Heatmap */}
      {(isLoadingHeatmap || (heatmapData && heatmapData.length > 0)) && (
        <section>
          <div className="flex items-center gap-2.5 mb-4">
            <Calendar className="h-5 w-5 text-brand-400" />
            <h2 className="text-lg font-bold text-surface-100">Activity</h2>
          </div>

          {isLoadingHeatmap ? (
            <Skeleton className="h-[140px] w-full rounded-xl" />
          ) : (
            <PlayHeatmap data={heatmapData ?? []} />
          )}
        </section>
      )}

      {/* Achievement Showcase */}
      {id && <AchievementShowcase userId={id} />}

      {/* Game sections */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {profile.topGames.length > 0 && (
          <ProfileSection title="Most Played" icon={Trophy}>
            <div className="space-y-1">
              {profile.topGames.map((game) => (
                <ProfileGameCard key={game.id} game={game} />
              ))}
            </div>
          </ProfileSection>
        )}

        {profile.favoriteGames.length > 0 && (
          <ProfileSection title="Favorites" icon={Heart}>
            <div className="space-y-1">
              {profile.favoriteGames.map((game) => (
                <ProfileGameCard key={game.id} game={game} />
              ))}
            </div>
          </ProfileSection>
        )}

        {profile.recentGames.length > 0 && (
          <ProfileSection title="Recently Played" icon={Clock}>
            <div className="space-y-1">
              {profile.recentGames.map((game) => (
                <ProfileGameCard key={game.id} game={game} />
              ))}
            </div>
          </ProfileSection>
        )}
      </div>
    </SectionList>
    </PageLayout>
  );
}
