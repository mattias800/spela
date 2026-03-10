import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Wand2, ArrowRight, ArrowLeft, Sparkles, Library } from "lucide-react";
import { GameCard } from "@/components/game-card";
import { GameGrid } from "@/components/game-grid";
import {
  BackButton,
  Button,
  GameCardSkeleton,
  EmptyState,
  Skeleton,
} from "@/components/ui";
import { useWizardSteps, useWizardResults } from "@/hooks/use-explore";
import { useToggleFavorite } from "@/hooks/use-games";
import { useTogglePlayLater } from "@/hooks/use-play-later";
import { cn } from "@/lib/cn";
import type { WizardOption } from "@/types/api";

export function ExploreWizardPage() {
  const navigate = useNavigate();
  const { data: wizardData, isLoading: isStepsLoading } = useWizardSteps();
  const { toggle: handleToggleFavorite } = useToggleFavorite();
  const { toggle: handleTogglePlayLater } = useTogglePlayLater();

  const [currentStep, setCurrentStep] = useState(0);
  const [selections, setSelections] = useState<Record<string, string>>({});

  const steps = wizardData?.steps ?? [];
  const step = steps[currentStep];
  const isComplete = currentStep >= steps.length && steps.length > 0;

  const mood = selections["mood"] ?? "";
  const era = selections["era"] ?? "";
  const vibe = selections["vibe"] ?? "";

  const {
    data: resultsData,
    isLoading: isResultsLoading,
    refetch: refetchResults,
  } = useWizardResults(mood, era, vibe, isComplete);

  function handleSelect(option: WizardOption) {
    if (!step) return;
    setSelections((prev) => ({ ...prev, [step.type]: option.id }));
    setCurrentStep((s) => s + 1);
  }

  function handleBack() {
    if (currentStep > 0) {
      setCurrentStep((s) => s - 1);
    }
  }

  function handleRestart() {
    setSelections({});
    setCurrentStep(0);
  }

  if (isStepsLoading) {
    return (
      <div className="space-y-6">
        <BackButton onClick={() => navigate("/explore")}>
          Back to Explore
        </BackButton>
        <Skeleton className="h-10 w-72" />
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {Array.from({ length: 5 }, (_, i) => (
            <Skeleton key={i} className="h-24 rounded-xl" />
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-8" data-testid="wizard-page">
      <BackButton onClick={() => navigate("/explore")}>
        Back to Explore
      </BackButton>

      {/* Header */}
      <div className="flex items-center gap-3">
        <Wand2 className="h-8 w-8 text-brand-400" />
        <div>
          <h1 className="text-3xl font-bold text-surface-100">
            Decision Wizard
          </h1>
          <p className="mt-1 text-surface-400">
            Answer 3 questions and we'll find the perfect game for you.
          </p>
        </div>
      </div>

      {/* Progress bar */}
      {steps.length > 0 && (
        <div className="flex gap-2" aria-label="Wizard progress">
          {steps.map((s, i) => (
            <div
              key={s.step}
              className={cn(
                "h-2 flex-1 rounded-full transition-colors",
                i < currentStep
                  ? "bg-brand-500"
                  : i === currentStep
                    ? "bg-brand-500/50"
                    : "bg-surface-800",
              )}
            />
          ))}
        </div>
      )}

      {/* Step content or results */}
      {!isComplete && step ? (
        <div>
          <h2 className="text-2xl font-bold text-surface-100 mb-6">
            {step.title}
          </h2>

          <div
            className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4"
            role="radiogroup"
            aria-label={step.title}
          >
            {step.options.map((option) => (
              <button
                key={option.id}
                onClick={() => handleSelect(option)}
                data-testid={`wizard-option-${option.id}`}
                className="group text-left rounded-xl border border-surface-800 bg-surface-900/50 p-5 transition-all hover:border-brand-500/50 hover:bg-surface-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
              >
                <h3 className="text-lg font-bold text-surface-100 group-hover:text-brand-300 transition-colors">
                  {option.label}
                </h3>
                {option.description && (
                  <p className="text-sm text-surface-400 mt-1">
                    {option.description}
                  </p>
                )}
              </button>
            ))}
          </div>

          {currentStep > 0 && (
            <Button
              variant="ghost"
              size="sm"
              onClick={handleBack}
              icon={<ArrowLeft className="h-4 w-4" />}
              className="mt-6"
            >
              Back
            </Button>
          )}
        </div>
      ) : isComplete ? (
        <div>
          {/* Results header */}
          <div className="flex items-center justify-between mb-6">
            <div className="flex items-center gap-3">
              <Sparkles className="h-6 w-6 text-brand-400" />
              <h2 className="text-2xl font-bold text-surface-100">
                {resultsData?.title ?? "Your Perfect Picks"}
              </h2>
            </div>
            <div className="flex gap-3">
              <Button
                variant="secondary"
                size="sm"
                onClick={() => refetchResults()}
              >
                <ArrowRight className="h-4 w-4 mr-1" />
                Shuffle
              </Button>
              <Button variant="secondary" size="sm" onClick={handleRestart}>
                <Wand2 className="h-4 w-4 mr-1" />
                Start Over
              </Button>
            </div>
          </div>

          {/* Results grid */}
          {isResultsLoading ? (
            <GameGrid>
              {Array.from({ length: 5 }, (_, i) => (
                <GameCardSkeleton key={i} />
              ))}
            </GameGrid>
          ) : !resultsData?.games || resultsData.games.length === 0 ? (
            <EmptyState
              icon={Library}
              title="No games found"
              description="No games match your preferences. Try different choices!"
              action={
                <Button variant="primary" onClick={handleRestart}>
                  Try Again
                </Button>
              }
            />
          ) : (
            <GameGrid>
              {resultsData.games.map((game) => (
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
        </div>
      ) : null}
    </div>
  );
}
