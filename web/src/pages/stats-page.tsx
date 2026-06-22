import { BarChart3, Calendar } from "lucide-react";
import { PageLayout, SectionList } from "@/components/layout";
import { Skeleton } from "@/components/ui";
import { PlayHeatmap } from "@/components/play-heatmap";
import { useMyPlayHeatmap } from "@/hooks/use-play-heatmap";
import { useExplorerBadges, useCompletionistMap } from "@/hooks/use-explore";
import { ExplorerBadgesSection } from "@/features/explore/components/explorer-badges";
import { CompletionistMapSection } from "@/features/explore/components/completionist-map";
import { MostPlayedStatsSection } from "@/features/stats/components/most-played-stats-section";
import { MostActivePlayersStatsSection } from "@/features/stats/components/most-active-players-stats-section";

export function StatsPage() {
  const { data: heatmapData, isLoading: isLoadingHeatmap } = useMyPlayHeatmap();
  const { data: badgesData, isLoading: isLoadingBadges } = useExplorerBadges();
  const { data: completionistData, isLoading: isLoadingCompletionist } =
    useCompletionistMap();

  return (
    <PageLayout
      title="Stats"
      subtitle="See how you stack up against other players."
      icon={BarChart3}
    >
      <SectionList className="max-w-5xl">
        {/* Your Activity Heatmap */}
        {(isLoadingHeatmap || (heatmapData && heatmapData.length > 0)) && (
          <section>
            <div className="flex items-center gap-2.5 mb-5">
              <Calendar className="h-5 w-5 text-brand-400" />
              <h2 className="text-xl font-bold text-surface-100">
                Your Activity
              </h2>
            </div>

            {isLoadingHeatmap ? (
              <Skeleton className="h-[140px] w-full rounded-xl" />
            ) : (
              <PlayHeatmap data={heatmapData ?? []} />
            )}
          </section>
        )}

        {/* Explorer Badges */}
        <ExplorerBadgesSection
          badges={badgesData?.badges ?? undefined}
          isLoading={isLoadingBadges}
        />

        {/* Completionist Map */}
        <CompletionistMapSection
          data={completionistData}
          isLoading={isLoadingCompletionist}
        />

        {/* Most-played games — local or across connected servers (self-contained). */}
        <MostPlayedStatsSection />

        {/* Most-active players — local or across connected servers (self-contained). */}
        <MostActivePlayersStatsSection />
      </SectionList>
    </PageLayout>
  );
}

export default StatsPage;
