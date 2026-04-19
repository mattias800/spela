import { useQuery } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";

export interface GameSearchResult {
  id: string;
  title: string;
  coverUrl?: string;
  consoleId: string;
  consoleName: string;
  developer?: string;
}

export interface ConsoleSearchResult {
  id: string;
  name: string;
  iconUrl: string;
  colorTheme: string;
  gameCount: number;
}

export interface DeveloperSearchResult {
  name: string;
  gameCount: number;
  avgRating: number;
}

export interface PublisherSearchResult {
  name: string;
  gameCount: number;
  avgRating: number;
}

export interface CollectionSearchResult {
  id: string;
  name: string;
  gameCount: number;
  username: string;
}

export interface SeriesSearchResult {
  id: string;
  name: string;
  libraryGames: number;
  totalGames: number;
}

export interface FranchiseSearchResult {
  id: string;
  name: string;
  libraryGames: number;
  totalGames: number;
}

export interface SearchResults {
  games: { results: GameSearchResult[]; total: number };
  consoles: { results: ConsoleSearchResult[]; total: number };
  developers: { results: DeveloperSearchResult[]; total: number };
  publishers: { results: PublisherSearchResult[]; total: number };
  collections: { results: CollectionSearchResult[]; total: number };
  series: { results: SeriesSearchResult[]; total: number };
  franchises: { results: FranchiseSearchResult[]; total: number };
}

export function useSearch(query: string) {
  return useQuery({
    queryKey: ["search", query],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/search", {
          params: { query: { q: query, limit: 5 } },
        }),
      ) as Promise<SearchResults>,
    enabled: query.length >= 2,
    staleTime: 30 * 1000,
  });
}
