import { useState } from "react";
import { FileSearch, Check, ChevronRight, AlertTriangle } from "lucide-react";
import { Button, Card, Badge, EmptyState } from "@/components/ui";
import { useMetadataMatches, useUpdateGameMetadata } from "@/hooks/use-admin";
import { useToast } from "@/components/ui";
import { cn } from "@/lib/cn";
import type { MetadataMatch, MetadataSuggestion } from "@/types/api";

export function MetadataFixPage() {
  const { data: matches, isLoading } = useMetadataMatches();
  const updateMetadata = useUpdateGameMetadata();
  const { toast } = useToast();
  const [currentIndex, setCurrentIndex] = useState(0);

  if (isLoading) {
    return (
      <div className="space-y-6 max-w-5xl">
        <div className="h-8 w-48 rounded-lg bg-surface-800 animate-pulse" />
        <div className="h-96 rounded-2xl bg-surface-900 animate-pulse" />
      </div>
    );
  }

  if (!matches || matches.length === 0) {
    return (
      <div className="space-y-6">
        <div>
          <h1 className="text-3xl font-bold text-surface-100">Metadata Fix</h1>
          <p className="mt-1 text-surface-400">
            Review and fix mismatched game metadata.
          </p>
        </div>
        <EmptyState
          icon={FileSearch}
          title="All metadata looks correct"
          description="No metadata mismatches found. Run a scrape to check for new matches."
        />
      </div>
    );
  }

  const current = matches[currentIndex]!;

  function handleApply(match: MetadataMatch, suggestion: MetadataSuggestion) {
    updateMetadata.mutate(
      {
        gameId: match.gameId,
        metadata: {
          title: suggestion.title,
          coverUrl: suggestion.coverUrl,
          description: suggestion.description,
          developer: suggestion.developer,
          publisher: suggestion.publisher,
          releaseDate: suggestion.releaseDate,
          genre: suggestion.genre,
        },
      },
      {
        onSuccess: () => {
          toast("success", `Metadata updated for "${suggestion.title}"`);
          if (matches && currentIndex < matches.length - 1) {
            setCurrentIndex((i) => i + 1);
          }
        },
        onError: (err) => toast("error", err instanceof Error ? err.message : "Unknown error"),
      },
    );
  }

  function handleSkip() {
    if (matches && currentIndex < matches.length - 1) {
      setCurrentIndex((i) => i + 1);
    }
  }

  return (
    <div className="space-y-6 max-w-5xl">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-surface-100">Metadata Fix</h1>
          <p className="mt-1 text-surface-400">
            Review {matches.length} game{matches.length !== 1 ? "s" : ""} with
            potential metadata issues.
          </p>
        </div>
        <Badge variant="warning">
          {currentIndex + 1} / {matches.length}
        </Badge>
      </div>

      {/* Current item */}
      <Card className="p-6">
        <div className="flex items-start gap-6 mb-6">
          {/* Current state */}
          <div className="flex-1">
            <p className="text-xs font-semibold uppercase tracking-wider text-surface-500 mb-3">
              Current
            </p>
            <div className="flex gap-4">
              <div className="h-32 w-24 rounded-xl overflow-hidden bg-surface-800 flex-shrink-0 border border-surface-700">
                {current.currentCoverUrl ? (
                  <img
                    src={current.currentCoverUrl}
                    alt={current.currentTitle}
                    className="h-full w-full object-cover"
                  />
                ) : (
                  <div className="h-full w-full flex items-center justify-center">
                    <AlertTriangle className="h-6 w-6 text-warning-500" />
                  </div>
                )}
              </div>
              <div>
                <h3 className="text-lg font-semibold text-surface-100">
                  {current.currentTitle}
                </h3>
                <p className="text-sm text-surface-500 mt-1">Game ID: {current.gameId}</p>
              </div>
            </div>
          </div>

          <ChevronRight className="h-6 w-6 text-surface-600 mt-12 flex-shrink-0" />

          {/* Suggestions */}
          <div className="flex-1">
            <p className="text-xs font-semibold uppercase tracking-wider text-surface-500 mb-3">
              Suggestions
            </p>
          </div>
        </div>

        {/* Suggestion cards */}
        <div className="space-y-3">
          {current.suggestions.map((suggestion, i) => (
            <SuggestionCard
              key={i}
              suggestion={suggestion}
              onApply={() => handleApply(current, suggestion)}
              isApplying={updateMetadata.isPending}
            />
          ))}
        </div>

        {/* Navigation */}
        <div className="flex justify-between mt-6 pt-4 border-t border-surface-800">
          <Button
            variant="secondary"
            onClick={() => setCurrentIndex((i) => Math.max(0, i - 1))}
            disabled={currentIndex === 0}
          >
            Previous
          </Button>
          <Button variant="ghost" onClick={handleSkip}>
            Skip
          </Button>
        </div>
      </Card>
    </div>
  );
}

function SuggestionCard({
  suggestion,
  onApply,
  isApplying,
}: {
  suggestion: MetadataSuggestion;
  onApply: () => void;
  isApplying: boolean;
}) {
  const confidenceColor =
    suggestion.confidence >= 0.8
      ? "text-success-500"
      : suggestion.confidence >= 0.5
        ? "text-warning-500"
        : "text-danger-500";

  return (
    <div className="flex items-center gap-4 p-4 rounded-xl bg-surface-800/30 border border-surface-800 hover:border-surface-700 transition-colors">
      <div className="h-20 w-15 rounded-lg overflow-hidden bg-surface-800 flex-shrink-0">
        {suggestion.coverUrl ? (
          <img
            src={suggestion.coverUrl}
            alt={suggestion.title}
            className="h-full w-full object-cover"
          />
        ) : (
          <div className="h-full w-full flex items-center justify-center text-surface-600 text-xs">
            N/A
          </div>
        )}
      </div>

      <div className="flex-1 min-w-0 space-y-1">
        <h4 className="text-sm font-semibold text-surface-100 truncate">
          {suggestion.title}
        </h4>
        <div className="flex flex-wrap gap-2 text-xs text-surface-400">
          {suggestion.developer && <span>{suggestion.developer}</span>}
          {suggestion.releaseDate && <span>{suggestion.releaseDate}</span>}
          {suggestion.genre && <span>{suggestion.genre}</span>}
        </div>
        <div className="flex items-center gap-2">
          <Badge>{suggestion.source}</Badge>
          <span className={cn("text-xs font-medium", confidenceColor)}>
            {Math.round(suggestion.confidence * 100)}% match
          </span>
        </div>
      </div>

      <Button size="sm" onClick={onApply} loading={isApplying}>
        <Check className="h-4 w-4" />
        Apply
      </Button>
    </div>
  );
}
