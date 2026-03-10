import { useRef } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Wand2, Dices } from "lucide-react";
import { Skeleton } from "@/components/ui";
import { useSurpriseGame } from "@/hooks/use-explore";

export function WildFeaturesSection() {
  const navigate = useNavigate();
  const { refetch: fetchSurprise, isFetching: isSurpriseFetching } =
    useSurpriseGame();
  const navigatedRef = useRef(false);

  async function handleSurprise() {
    navigatedRef.current = false;
    const result = await fetchSurprise();
    if (result?.data?.id && !navigatedRef.current) {
      navigatedRef.current = true;
      navigate(`/games/${result.data.id}`);
    }
  }

  return (
    <section data-testid="wild-features">
      <h2 className="text-xl font-bold text-surface-100 mb-5">
        Feeling Adventurous?
      </h2>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        {/* I'm Feeling Lucky */}
        <button
          onClick={handleSurprise}
          disabled={isSurpriseFetching}
          aria-busy={isSurpriseFetching}
          data-testid="lucky-button"
          className="group relative overflow-hidden rounded-xl border border-surface-800 bg-gradient-to-br from-amber-900/40 via-surface-900 to-amber-800/20 p-6 text-left transition-all hover:border-amber-500/50 hover:shadow-lg hover:shadow-amber-500/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 disabled:opacity-70 disabled:cursor-wait"
        >
          <div className="flex items-start gap-4">
            <span
              className={`text-4xl transition-transform duration-500 ${isSurpriseFetching ? "animate-spin" : "group-hover:rotate-[720deg] group-hover:scale-110"}`}
              aria-hidden="true"
            >
              <Dices className="h-10 w-10 text-amber-400" />
            </span>
            <div>
              <h3 className="text-lg font-bold text-surface-100 group-hover:text-amber-300 transition-colors">
                {isSurpriseFetching
                  ? "Rolling the dice..."
                  : "I'm Feeling Lucky"}
              </h3>
              <p className="text-sm text-surface-400 mt-1">
                Jump straight into a random game from your library
              </p>
            </div>
          </div>
        </button>

        {/* Decision Wizard */}
        <Link
          to="/explore/wizard"
          data-testid="wizard-cta"
          className="group relative overflow-hidden rounded-xl border border-surface-800 bg-gradient-to-br from-purple-900/40 via-surface-900 to-purple-800/20 p-6 text-left transition-all hover:border-purple-500/50 hover:shadow-lg hover:shadow-purple-500/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
        >
          <div className="flex items-start gap-4">
            <Wand2 className="h-10 w-10 text-purple-400 transition-transform group-hover:rotate-12 group-hover:scale-110" />
            <div>
              <h3 className="text-lg font-bold text-surface-100 group-hover:text-purple-300 transition-colors">
                Decision Wizard
              </h3>
              <p className="text-sm text-surface-400 mt-1">
                Answer 3 questions to find your perfect game
              </p>
            </div>
          </div>
        </Link>
      </div>
    </section>
  );
}

export function WildFeaturesSkeleton() {
  return (
    <section>
      <Skeleton className="w-56 h-7 mb-5" />
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <Skeleton className="h-28 rounded-xl" />
        <Skeleton className="h-28 rounded-xl" />
      </div>
    </section>
  );
}
