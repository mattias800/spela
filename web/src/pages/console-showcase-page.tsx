import { useParams, useNavigate } from "react-router-dom";
import { BackButton, Skeleton, GameCardSkeleton } from "@/components/ui";
import {
  ConsoleEssentials,
  ConsoleHiddenGems,
  ConsoleGenreBreakdown,
  ConsoleTopDevelopers,
  ConsoleRecentlyPlayed,
} from "@/features/explore/components/console-showcase-sections";
import { useConsoleShowcase } from "@/hooks/use-explore";

function ShowcasePageSkeleton() {
  return (
    <div className="space-y-10" data-testid="console-showcase-skeleton">
      {/* Hero skeleton */}
      <div className="rounded-2xl overflow-hidden">
        <Skeleton className="w-full h-56" />
      </div>
      {/* Essentials shelf skeleton */}
      <div className="space-y-4">
        <Skeleton className="w-48 h-7" />
        <div className="flex gap-5 overflow-hidden">
          {Array.from({ length: 6 }, (_, i) => (
            <div key={i} className="w-40 sm:w-44 lg:w-48 flex-shrink-0">
              <GameCardSkeleton />
            </div>
          ))}
        </div>
      </div>
      {/* Hidden gems shelf skeleton */}
      <div className="space-y-4">
        <Skeleton className="w-40 h-7" />
        <div className="flex gap-5 overflow-hidden">
          {Array.from({ length: 6 }, (_, i) => (
            <div key={i} className="w-40 sm:w-44 lg:w-48 flex-shrink-0">
              <GameCardSkeleton />
            </div>
          ))}
        </div>
      </div>
      {/* Genre breakdown skeleton */}
      <div className="space-y-4">
        <Skeleton className="w-48 h-7" />
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
          {Array.from({ length: 8 }, (_, i) => (
            <Skeleton key={i} className="h-24 rounded-xl" />
          ))}
        </div>
      </div>
      {/* Developers skeleton */}
      <div className="space-y-4">
        <Skeleton className="w-64 h-7" />
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
          {Array.from({ length: 4 }, (_, i) => (
            <Skeleton key={i} className="h-28 rounded-xl" />
          ))}
        </div>
      </div>
    </div>
  );
}

export function ConsoleShowcasePage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: showcase, isLoading } = useConsoleShowcase(id ?? "");

  if (isLoading) {
    return (
      <div
        className="max-w-6xl space-y-6"
        data-testid="console-showcase-page"
      >
        <BackButton onClick={() => navigate(-1)}>Back to Explore</BackButton>
        <ShowcasePageSkeleton />
      </div>
    );
  }

  if (!showcase) {
    return (
      <div
        className="max-w-6xl space-y-6"
        data-testid="console-showcase-page"
      >
        <BackButton onClick={() => navigate(-1)}>Back to Explore</BackButton>
        <p className="text-surface-400 text-center py-20">
          Console not found
        </p>
      </div>
    );
  }

  const { console: con } = showcase;
  const colorTheme = con.colorTheme || "#6366f1";

  return (
    <div className="max-w-6xl space-y-10" data-testid="console-showcase-page">
      <BackButton onClick={() => navigate(-1)}>Back to Explore</BackButton>

      {/* Hero Section */}
      <div
        className="relative rounded-2xl overflow-hidden border border-white/[0.06]"
        data-testid="console-showcase-hero"
      >
        <div
          className="absolute inset-0"
          style={{
            background: `linear-gradient(135deg, ${colorTheme}40, ${colorTheme}10, transparent)`,
          }}
        />
        <div className="absolute inset-0 bg-gradient-to-t from-black/70 via-black/30 to-transparent" />

        <div className="relative p-8 sm:p-10 flex items-center gap-6">
          {con.logoUrl && (
            <img
              src={con.logoUrl}
              alt={`${con.name} logo`}
              className="h-16 sm:h-20 object-contain drop-shadow-lg"
              data-testid="console-logo"
            />
          )}
          <div>
            <h1
              className="text-3xl sm:text-4xl font-bold text-white"
              data-testid="console-name"
            >
              {con.name}
            </h1>
            <p
              className="mt-2 text-sm text-white/70"
              data-testid="console-stats"
            >
              {con.gameCount} {con.gameCount === 1 ? "game" : "games"} in
              library
            </p>
          </div>
        </div>
      </div>

      <ConsoleEssentials consoleId={id!} />
      <ConsoleHiddenGems consoleId={id!} />
      <ConsoleGenreBreakdown consoleId={id!} />
      <ConsoleTopDevelopers consoleId={id!} />
      <ConsoleRecentlyPlayed consoleId={id!} />
    </div>
  );
}
