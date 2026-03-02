import { useState, useEffect, useRef } from "react";
import { Search, Check, AlertTriangle } from "lucide-react";
import { Modal, SearchInput, Badge, Skeleton, useToast } from "@/components/ui";
import { useIgdbSearch, useApplyIgdbMatch, useIgdbStatus } from "@/hooks/use-admin";
import { cn } from "@/lib/cn";
import type { IgdbSearchResult } from "@/types/api";

interface ScrapeMatchModalProps {
  gameId: string;
  currentTitle: string;
  currentScraperId?: string;
  open: boolean;
  onClose: () => void;
}

export function ScrapeMatchModal({
  gameId,
  currentTitle,
  currentScraperId,
  open,
  onClose,
}: ScrapeMatchModalProps) {
  const [searchInput, setSearchInput] = useState(currentTitle);
  const [debouncedQuery, setDebouncedQuery] = useState(currentTitle);
  const inputRef = useRef<HTMLInputElement>(null);
  const { toast } = useToast();

  const { data: igdbStatus } = useIgdbStatus();
  const { data: results, isLoading, isError } = useIgdbSearch(gameId, debouncedQuery);
  const applyMatch = useApplyIgdbMatch();

  // Reset search when modal opens with a new game
  useEffect(() => {
    if (open) {
      setSearchInput(currentTitle);
      setDebouncedQuery(currentTitle);
    }
  }, [open, currentTitle]);

  // Debounce search input
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedQuery(searchInput);
    }, 400);
    return () => clearTimeout(timer);
  }, [searchInput]);

  // Focus input when modal opens
  useEffect(() => {
    if (open) {
      // Small delay to allow modal animation
      const timer = setTimeout(() => inputRef.current?.focus(), 100);
      return () => clearTimeout(timer);
    }
  }, [open]);

  function handleSelect(result: IgdbSearchResult) {
    if (applyMatch.isPending) return;
    applyMatch.mutate(
      { gameId, igdbId: result.igdbId },
      {
        onSuccess: () => {
          toast("success", `Matched to "${result.name}"`);
          onClose();
        },
        onError: () => {
          toast("error", "Failed to apply match");
        },
      },
    );
  }

  const currentIgdbId = currentScraperId?.replace("igdb:", "") ?? null;

  const igdbNotConfigured =
    igdbStatus && igdbStatus.status !== "connected";

  return (
    <Modal open={open} onClose={onClose} title="Fix Scrape Match" size="lg">
      <div className="space-y-4">
        <SearchInput
          ref={inputRef}
          value={searchInput}
          onChange={(e) => setSearchInput(e.currentTarget.value)}
          placeholder="Search IGDB..."
          data-testid="igdb-search-input"
        />

        {igdbNotConfigured ? (
          <div className="flex items-center gap-3 rounded-xl bg-warning-500/10 border border-warning-500/20 px-4 py-3">
            <AlertTriangle className="h-5 w-5 text-warning-500 flex-shrink-0" />
            <p className="text-sm text-surface-300">
              IGDB is not configured. Set up credentials in Admin Settings to
              search for games.
            </p>
          </div>
        ) : (
          <div
            className="max-h-96 overflow-y-auto -mx-6 px-6 space-y-1"
            data-testid="igdb-search-results"
          >
            {isLoading && debouncedQuery.length >= 2 && (
              <SearchResultsSkeleton />
            )}

            {isError && (
              <p className="py-8 text-sm text-danger-400 text-center">
                Failed to search IGDB. Please try again.
              </p>
            )}

            {!isLoading && !isError && results && results.length === 0 && (
              <div className="flex flex-col items-center py-8">
                <Search className="h-8 w-8 text-surface-600 mb-2" />
                <p className="text-sm text-surface-400">
                  No results found on IGDB
                </p>
              </div>
            )}

            {!isLoading &&
              results?.map((result) => (
                <IgdbResultItem
                  key={result.igdbId}
                  result={result}
                  isCurrentMatch={
                    currentIgdbId != null &&
                    String(result.igdbId) === currentIgdbId
                  }
                  isApplying={applyMatch.isPending}
                  onSelect={() => handleSelect(result)}
                />
              ))}

            {debouncedQuery.length < 2 && !isLoading && (
              <p className="py-8 text-sm text-surface-500 text-center">
                Type at least 2 characters to search
              </p>
            )}
          </div>
        )}
      </div>
    </Modal>
  );
}

function IgdbResultItem({
  result,
  isCurrentMatch,
  isApplying,
  onSelect,
}: {
  result: IgdbSearchResult;
  isCurrentMatch: boolean;
  isApplying: boolean;
  onSelect: () => void;
}) {
  return (
    <button
      onClick={onSelect}
      disabled={isApplying || isCurrentMatch}
      data-testid={`igdb-result-${result.igdbId}`}
      className={cn(
        "w-full flex items-start gap-3 rounded-xl px-3 py-3 text-left transition-colors",
        isCurrentMatch
          ? "bg-brand-500/10 border border-brand-500/30 cursor-default"
          : "hover:bg-surface-800 cursor-pointer border border-transparent",
        isApplying && !isCurrentMatch && "opacity-50 cursor-not-allowed",
      )}
    >
      {/* Cover thumbnail */}
      <div className="w-12 flex-shrink-0 rounded-lg overflow-hidden bg-surface-800">
        {result.coverUrl ? (
          <img
            src={result.coverUrl}
            alt={result.name}
            className="w-full"
            style={{ aspectRatio: 3 / 4 }}
            loading="lazy"
          />
        ) : (
          <div
            className="w-full bg-surface-800 flex items-center justify-center"
            style={{ aspectRatio: 3 / 4 }}
          >
            <span className="text-lg font-bold text-surface-700">
              {result.name.charAt(0)}
            </span>
          </div>
        )}
      </div>

      {/* Info */}
      <div className="flex-1 min-w-0 space-y-1">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-surface-100 truncate">
            {result.name}
          </span>
          {isCurrentMatch && (
            <Badge variant="brand" className="flex-shrink-0">
              <Check className="h-3 w-3 mr-0.5" />
              Current
            </Badge>
          )}
        </div>
        <div className="flex items-center gap-2 text-xs text-surface-400">
          {result.releaseYear && <span>{result.releaseYear}</span>}
          {result.developer && (
            <>
              {result.releaseYear && (
                <span className="text-surface-600">&middot;</span>
              )}
              <span className="truncate">{result.developer}</span>
            </>
          )}
        </div>
        {result.summary && (
          <p className="text-xs text-surface-500 line-clamp-2">
            {result.summary}
          </p>
        )}
      </div>
    </button>
  );
}

function SearchResultsSkeleton() {
  return (
    <>
      {Array.from({ length: 5 }, (_, i) => (
        <div key={i} className="flex items-start gap-3 px-3 py-3">
          <Skeleton
            className="w-12 flex-shrink-0 rounded-lg"
            style={{ aspectRatio: 3 / 4 }}
          />
          <div className="flex-1 space-y-2">
            <Skeleton className="h-4 w-3/5" />
            <Skeleton className="h-3 w-2/5" />
            <Skeleton className="h-3 w-full" />
          </div>
        </div>
      ))}
    </>
  );
}
