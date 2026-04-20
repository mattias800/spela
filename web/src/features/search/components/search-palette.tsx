import { useState, useRef, useEffect, useCallback } from "react";
import { Search, Loader2, Clock, X } from "lucide-react";
import { cn } from "@/lib/cn";
import { useSearch } from "@/hooks/use-search";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { useHotkey } from "@/hooks/use-hotkey";
import { useRecentSearches } from "@/hooks/use-recent-searches";
import { SearchResultsDisplay } from "./search-results";

export function SearchPalette() {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const debouncedQuery = useDebouncedValue(query.trim(), 250);
  const inputRef = useRef<HTMLInputElement>(null);
  const overlayRef = useRef<HTMLDivElement>(null);
  const { recentSearches, addRecent, removeRecent, clearRecent } =
    useRecentSearches();

  const { data: results, isLoading, isFetching } = useSearch(debouncedQuery);

  const openPalette = useCallback(() => setOpen(true), []);
  const closePalette = useCallback(() => {
    setOpen(false);
    setQuery("");
  }, []);

  // Cmd+K / Ctrl+K to open
  useHotkey("k", openPalette, { meta: true, ctrl: true });

  // Listen for external open requests (e.g. from sidebar search button)
  useEffect(() => {
    const handler = () => setOpen(true);
    window.addEventListener("open-search-palette", handler);
    return () => window.removeEventListener("open-search-palette", handler);
  }, []);

  // Auto-focus input when opened
  useEffect(() => {
    if (open) {
      // Small delay to ensure DOM is ready
      requestAnimationFrame(() => inputRef.current?.focus());
    }
  }, [open]);

  // Lock body scroll when open
  useEffect(() => {
    if (open) {
      document.body.style.overflow = "hidden";
    } else {
      document.body.style.overflow = "";
    }
    return () => {
      document.body.style.overflow = "";
    };
  }, [open]);

  // Close on Escape
  useEffect(() => {
    if (!open) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === "Escape") closePalette();
    };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [open, closePalette]);

  const handleNavigate = useCallback(
    (path: string) => {
      void path;
      if (query.trim()) {
        addRecent(query.trim());
      }
      closePalette();
    },
    [query, addRecent, closePalette],
  );

  const handleRecentClick = useCallback(
    (term: string) => {
      setQuery(term);
    },
    [],
  );

  const hasResults =
    results &&
    ((results.games.results?.length ?? 0) > 0 ||
      (results.consoles.results?.length ?? 0) > 0 ||
      (results.developers.results?.length ?? 0) > 0 ||
      (results.publishers.results?.length ?? 0) > 0 ||
      (results.collections.results?.length ?? 0) > 0 ||
      (results.series.results?.length ?? 0) > 0 ||
      (results.franchises.results?.length ?? 0) > 0);

  if (!open) return null;

  return (
    <div
      ref={overlayRef}
      className="fixed inset-0 z-50 flex items-start justify-center pt-[15vh] px-4"
      onClick={(e) => {
        if (e.target === overlayRef.current) closePalette();
      }}
      data-testid="search-palette"
    >
      {/* Backdrop */}
      <div className="fixed inset-0 bg-black/60 backdrop-blur-sm" />

      {/* Panel */}
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Search"
        className={cn(
          "relative w-full max-w-[640px] rounded-2xl bg-surface-900 border border-surface-800 shadow-2xl",
          "animate-in fade-in zoom-in-95 duration-200",
        )}
      >
        {/* Search input */}
        <div className="flex items-center gap-3 px-4 border-b border-surface-800">
          <Search className="h-5 w-5 text-surface-500 flex-shrink-0" />
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search games, consoles, developers..."
            className="flex-1 bg-transparent py-4 text-base text-surface-100 placeholder:text-surface-500 focus:outline-none"
            aria-label="Search input"
          />
          {isFetching && (
            <Loader2 className="h-4 w-4 text-surface-500 animate-spin flex-shrink-0" data-testid="search-loading" />
          )}
          <kbd className="hidden sm:inline-flex items-center gap-0.5 rounded border border-surface-700 bg-surface-800 px-1.5 py-0.5 text-xs text-surface-500">
            Esc
          </kbd>
        </div>

        {/* Content area */}
        <div className="min-h-[60px]">
          {/* Empty query — show recent searches or prompt */}
          {!query.trim() && (
            <div className="px-4 py-4">
              {recentSearches.length > 0 ? (
                <div>
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xs font-semibold uppercase tracking-wider text-surface-500">
                      Recent searches
                    </span>
                    <button
                      onClick={clearRecent}
                      className="text-xs text-surface-500 hover:text-surface-300 transition-colors"
                    >
                      Clear all
                    </button>
                  </div>
                  <div className="space-y-0.5">
                    {recentSearches.map((term) => (
                      <div
                        key={term}
                        className="flex items-center gap-2 group"
                      >
                        <button
                          className="flex items-center gap-2 flex-1 px-2 py-1.5 rounded-lg text-sm text-surface-300 hover:bg-surface-800/50 transition-colors text-left"
                          onClick={() => handleRecentClick(term)}
                        >
                          <Clock className="h-3.5 w-3.5 text-surface-500" />
                          {term}
                        </button>
                        <button
                          onClick={() => removeRecent(term)}
                          className="p-1 rounded text-surface-600 hover:text-surface-300 opacity-0 group-hover:opacity-100 transition-all"
                          aria-label={`Remove ${term}`}
                        >
                          <X className="h-3.5 w-3.5" />
                        </button>
                      </div>
                    ))}
                  </div>
                </div>
              ) : (
                <p className="text-sm text-surface-500 text-center py-4">
                  Start typing to search...
                </p>
              )}
            </div>
          )}

          {/* Short query hint */}
          {query.trim().length > 0 && query.trim().length < 2 && (
            <p className="text-sm text-surface-500 text-center py-6">
              Type at least 2 characters
            </p>
          )}

          {/* Loading state (only show when no existing results and fetching) */}
          {debouncedQuery.length >= 2 && isLoading && (
            <div className="flex items-center justify-center py-8">
              <Loader2 className="h-5 w-5 text-surface-500 animate-spin" data-testid="search-loading" />
            </div>
          )}

          {/* No results */}
          {debouncedQuery.length >= 2 &&
            !isLoading &&
            results &&
            !hasResults && (
              <div className="flex flex-col items-center py-8">
                <Search className="h-8 w-8 text-surface-600 mb-2" />
                <p className="text-sm text-surface-500">
                  No results for &ldquo;{debouncedQuery}&rdquo;
                </p>
              </div>
            )}

          {/* Results */}
          {results && hasResults && (
            <SearchResultsDisplay
              results={results}
              onNavigate={handleNavigate}
            />
          )}
        </div>
      </div>
    </div>
  );
}
