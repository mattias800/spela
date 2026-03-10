import { useRef } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Skeleton } from "@/components/ui";
import { useSurpriseGame } from "@/hooks/use-explore";
import { buildGradient } from "@/features/explore/utils/gradient";
import type { MoodDefinition } from "@/types/api";

interface MoodPickerProps {
  moods: MoodDefinition[] | undefined;
  isLoading: boolean;
}

function MoodPickerSkeleton() {
  return (
    <section data-testid="mood-picker-skeleton">
      <Skeleton className="w-72 h-7 mb-5" />
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
        {Array.from({ length: 6 }, (_, i) => (
          <Skeleton key={i} className="h-32 rounded-xl" />
        ))}
      </div>
    </section>
  );
}

export function MoodPicker({ moods, isLoading }: MoodPickerProps) {
  const navigate = useNavigate();
  const { refetch: fetchSurprise, isFetching: isSurpriseFetching } = useSurpriseGame();
  const navigatedRef = useRef(false);

  async function handleSurprise() {
    navigatedRef.current = false;
    const result = await fetchSurprise();
    if (result?.data?.id && !navigatedRef.current) {
      navigatedRef.current = true;
      navigate(`/games/${result.data.id}`);
    }
  }

  if (isLoading) {
    return <MoodPickerSkeleton />;
  }

  if (!moods || moods.length === 0) {
    return null;
  }

  return (
    <section data-testid="mood-picker">
      <h2 className="text-xl font-bold text-surface-100 mb-5">
        What are you in the mood for?
      </h2>

      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4" role="list" aria-label="What are you in the mood for?">
        {moods.map((mood) => (
          <Link
            key={mood.id}
            to={`/explore/mood/${mood.id}`}
            data-testid={`mood-card-${mood.id}`}
            className="group/mood block rounded-xl overflow-hidden shadow-md transition-all duration-200 hover:scale-[1.02] hover:brightness-110 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 focus-visible:ring-offset-surface-950"
            style={{ background: buildGradient(mood.gradient) }}
          >
            <div className="relative h-32 flex flex-col justify-end p-4">
              {/* Subtle overlay for text readability */}
              <div className="absolute inset-0 bg-gradient-to-t from-black/40 via-transparent to-white/[0.04] pointer-events-none" />

              <div className="relative">
                <span className="text-2xl leading-none" aria-hidden="true">{mood.icon}</span>
                <h3 className="text-base font-bold text-white leading-tight mt-1.5 group-hover/mood:text-brand-200 transition-colors">
                  {mood.name}
                </h3>
                <p className="text-xs text-white/70 mt-0.5 line-clamp-2">
                  {mood.description}
                </p>
              </div>
            </div>
          </Link>
        ))}

        {/* Surprise Me card */}
        <button
          data-testid="surprise-me-button"
          onClick={handleSurprise}
          disabled={isSurpriseFetching}
          aria-busy={isSurpriseFetching}
          className="group/mood block rounded-xl overflow-hidden shadow-md transition-all duration-200 hover:scale-[1.02] hover:brightness-110 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 focus-visible:ring-offset-surface-950 text-left cursor-pointer disabled:opacity-70 disabled:cursor-wait"
          style={{ background: "linear-gradient(135deg, #880E4F, #E91E63)" }}
        >
          <div className="relative h-32 flex flex-col justify-end p-4">
            <div className="absolute inset-0 bg-gradient-to-t from-black/40 via-transparent to-white/[0.04] pointer-events-none" />

            <div className="relative">
              <span className="text-2xl leading-none" aria-hidden="true">{isSurpriseFetching ? "⏳" : "🎲"}</span>
              <h3 className="text-base font-bold text-white leading-tight mt-1.5 group-hover/mood:text-brand-200 transition-colors">
                {isSurpriseFetching ? "Finding a game…" : "Surprise Me"}
              </h3>
              <p className="text-xs text-white/70 mt-0.5">
                Pick a random game from your library
              </p>
            </div>
          </div>
        </button>
      </div>
    </section>
  );
}
