import { useState } from "react";
import { Flag } from "lucide-react";
import {
  EmptyState,
  Skeleton,
  Select,
  StateTabNav,
  StateTabItem,
} from "@/components/ui";
import { Button } from "@/components/ui";
import { ChallengeCard } from "@/features/challenges/components/challenge-card";
import { useChallenges, useMyChallenges } from "@/hooks/use-challenges";
import { useConsoles } from "@/hooks/use-consoles";
import type { ChallengeFilters, ChallengeDifficulty } from "@/types/api";

type Tab = "popular" | "recent" | "mine";

function ChallengeCardSkeleton() {
  return (
    <div className="space-y-3">
      <Skeleton className="aspect-[16/10] rounded-2xl" />
      <div className="px-1 space-y-1.5">
        <Skeleton className="h-4 w-32" />
        <Skeleton className="h-3 w-48" />
        <Skeleton className="h-3 w-24" />
      </div>
    </div>
  );
}

function ChallengeGridSkeleton() {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-5">
      {Array.from({ length: 8 }, (_, i) => (
        <ChallengeCardSkeleton key={i} />
      ))}
    </div>
  );
}

export function ChallengesPage() {
  const [activeTab, setActiveTab] = useState<Tab>("popular");
  const [page, setPage] = useState(1);
  const pageSize = 20;

  const [consoleFilter, setConsoleFilter] = useState("");
  const [difficultyFilter, setDifficultyFilter] = useState("");

  const { data: consoles } = useConsoles();

  const filters: ChallengeFilters = {
    page,
    pageSize,
    status: "active",
    sortBy: activeTab === "popular" ? "most_attempted" : "newest",
    ...(consoleFilter ? { consoleId: consoleFilter } : {}),
    ...(difficultyFilter
      ? { difficulty: difficultyFilter as ChallengeDifficulty }
      : {}),
  };

  const browseChallenges = useChallenges(
    activeTab !== "mine" ? filters : { page: 1, pageSize: 1 },
  );
  const myChallenges = useMyChallenges(
    activeTab === "mine" ? page : 1,
    pageSize,
  );

  const data = activeTab === "mine" ? myChallenges.data : browseChallenges.data;
  const isLoading =
    activeTab === "mine" ? myChallenges.isLoading : browseChallenges.isLoading;
  const challenges = data?.data ?? [];

  function handleTabChange(tab: Tab) {
    setActiveTab(tab);
    setPage(1);
  }

  const consoleOptions = [
    { value: "", label: "All Consoles" },
    ...(consoles?.map((c) => ({ value: c.id, label: c.name })) ?? []),
  ];

  const difficultyOptions = [
    { value: "", label: "All Difficulties" },
    { value: "easy", label: "Easy" },
    { value: "medium", label: "Medium" },
    { value: "hard", label: "Hard" },
  ];

  return (
    <div className="max-w-5xl space-y-6">
      <div>
        <h1 className="text-3xl font-bold text-surface-100 flex items-center gap-3">
          <Flag className="h-8 w-8 text-brand-400" />
          Challenges
        </h1>
        <p className="mt-1 text-surface-400">
          Compete with other players on community-created challenges.
        </p>
      </div>

      {/* Tabs */}
      <StateTabNav>
        <StateTabItem
          active={activeTab === "popular"}
          onClick={() => handleTabChange("popular")}
        >
          Popular
        </StateTabItem>
        <StateTabItem
          active={activeTab === "recent"}
          onClick={() => handleTabChange("recent")}
        >
          Recent
        </StateTabItem>
        <StateTabItem
          active={activeTab === "mine"}
          onClick={() => handleTabChange("mine")}
        >
          My Challenges
        </StateTabItem>
      </StateTabNav>

      {/* Filters (not shown for "mine" tab) */}
      {activeTab !== "mine" && (
        <div className="flex flex-wrap items-center gap-3">
          <Select
            value={consoleFilter}
            onChange={(e) => {
              setConsoleFilter(e.target.value);
              setPage(1);
            }}
            options={consoleOptions}
          />
          <Select
            value={difficultyFilter}
            onChange={(e) => {
              setDifficultyFilter(e.target.value);
              setPage(1);
            }}
            options={difficultyOptions}
          />
        </div>
      )}

      {/* Content */}
      {isLoading ? (
        <ChallengeGridSkeleton />
      ) : challenges.length === 0 ? (
        <EmptyState
          icon={Flag}
          title={
            activeTab === "mine"
              ? "No challenges created"
              : "No challenges yet"
          }
          description={
            activeTab === "mine"
              ? "Create a challenge from a save state in the player app or from a game's detail page."
              : "Try adjusting your filters, or check back later."
          }
        />
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-5">
          {challenges.map((challenge) => (
            <ChallengeCard key={challenge.id} challenge={challenge} />
          ))}
        </div>
      )}

      {data && data.total > pageSize && (() => {
        const totalPages = Math.ceil(data.total / pageSize);
        return (
          <div className="flex items-center justify-center gap-4 pt-4">
            <Button
              variant="secondary"
              size="sm"
              disabled={page <= 1}
              onClick={() => setPage(page - 1)}
            >
              Previous
            </Button>
            <span className="text-sm text-surface-400">
              Page {page} of {totalPages}
            </span>
            <Button
              variant="secondary"
              size="sm"
              disabled={page >= totalPages}
              onClick={() => setPage(page + 1)}
            >
              Next
            </Button>
          </div>
        );
      })()}
    </div>
  );
}
