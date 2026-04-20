import { useQuery } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";
import type {
  SearchResponse,
  SearchCollectionResult,
  SearchCompanyResult,
  SearchConsoleResult,
  SearchFranchiseResult,
  SearchGameResult,
  SearchSeriesResult,
} from "@/generated/schemas";

// Re-export generated search types under historical names used by
// `features/search/components/search-results.tsx`. Consumers only read a
// subset of fields; the generated shapes are structurally supersets of
// the previous hand-written interfaces, with every field non-null after
// the omitempty sweep (empty strings for missing optional values).
export type SearchResults = SearchResponse;
export type GameSearchResult = SearchGameResult;
export type ConsoleSearchResult = SearchConsoleResult;
export type DeveloperSearchResult = SearchCompanyResult;
export type PublisherSearchResult = SearchCompanyResult;
export type CollectionSearchResult = SearchCollectionResult;
export type SeriesSearchResult = SearchSeriesResult;
export type FranchiseSearchResult = SearchFranchiseResult;

export function useSearch(query: string) {
  return useQuery({
    queryKey: ["search", query],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/search", {
          params: { query: { q: query, limit: 5 } },
        }),
      ),
    enabled: query.length >= 2,
    staleTime: 30 * 1000,
  });
}
