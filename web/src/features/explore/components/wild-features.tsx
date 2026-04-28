import { type ReactNode, useRef } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Wand2, Dices } from "lucide-react";
import { Skeleton } from "@/components/ui";
import { useSurpriseGame } from "@/hooks/use-explore";
import { cn } from "@/lib/cn";

interface FeatureCardProps {
  icon: ReactNode;
  title: string;
  description: string;
  colorAccent: "amber" | "purple";
  testId: string;
}

const accentClasses = {
  amber: {
    gradient: "from-amber-900/40 via-surface-900 to-amber-800/20",
    border: "hover:border-amber-500/50",
    shadow: "hover:shadow-amber-500/10",
    title: "group-hover:text-amber-300",
  },
  purple: {
    gradient: "from-purple-900/40 via-surface-900 to-purple-800/20",
    border: "hover:border-purple-500/50",
    shadow: "hover:shadow-purple-500/10",
    title: "group-hover:text-purple-300",
  },
};

function FeatureCard({
  icon,
  title,
  description,
  colorAccent,
  testId,
}: FeatureCardProps) {
  const accent = accentClasses[colorAccent];
  return (
    <div data-comp="FeatureCard"
      data-testid={testId}
      className={cn(
        "flex items-start gap-4 rounded-xl border border-surface-800 bg-gradient-to-br p-6",
        accent.gradient,
      )}
    >
      {icon}
      <div>
        <h3
          className={cn(
            "text-lg font-bold text-surface-100 transition-colors",
            accent.title,
          )}
        >
          {title}
        </h3>
        <p className="text-sm text-surface-400 mt-1">{description}</p>
      </div>
    </div>
  );
}

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
    <section data-comp="WildFeaturesSection" data-testid="wild-features">
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
          className={cn(
            "group relative text-left transition-all",
            "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
            "disabled:opacity-70 disabled:cursor-wait",
            "rounded-xl hover:shadow-lg",
            accentClasses.amber.border,
            accentClasses.amber.shadow,
          )}
        >
          <FeatureCard
            icon={
              <span
                className={cn(
                  "text-4xl transition-transform duration-500",
                  isSurpriseFetching
                    ? "animate-spin"
                    : "group-hover:rotate-[720deg] group-hover:scale-110",
                )}
                aria-hidden="true"
              >
                <Dices className="h-10 w-10 text-amber-400" />
              </span>
            }
            title={
              isSurpriseFetching ? "Rolling the dice..." : "I'm Feeling Lucky"
            }
            description="Jump straight into a random game from your library"
            colorAccent="amber"
            testId="lucky-card-inner"
          />
        </button>

        {/* Decision Wizard */}
        <Link
          to="/explore/wizard"
          data-testid="wizard-cta"
          className={cn(
            "group relative text-left transition-all",
            "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
            "rounded-xl hover:shadow-lg",
            accentClasses.purple.border,
            accentClasses.purple.shadow,
          )}
        >
          <FeatureCard
            icon={
              <Wand2 className="h-10 w-10 text-purple-400 transition-transform group-hover:rotate-12 group-hover:scale-110" />
            }
            title="Decision Wizard"
            description="Answer 3 questions to find your perfect game"
            colorAccent="purple"
            testId="wizard-card-inner"
          />
        </Link>
      </div>
    </section>
  );
}

export function WildFeaturesSkeleton() {
  return (
    <section data-comp="WildFeaturesSkeleton">
      <Skeleton className="w-56 h-7 mb-5" />
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <Skeleton className="h-28 rounded-xl" />
        <Skeleton className="h-28 rounded-xl" />
      </div>
    </section>
  );
}
