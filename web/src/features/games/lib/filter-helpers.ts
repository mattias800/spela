import type { GameFilters } from "@/types/api";
import type { ChipPickerOption } from "@/components/ui";

// ─── Static option tables ─────────────────────────────────────────
//
// These used to live inline in `advanced-filter-panel.tsx`. Moved
// here so future i18n / test fixtures can import them directly.

export const REGION_OPTIONS: ChipPickerOption[] = [
  "USA", "Europe", "Japan", "World", "Korea", "Brazil",
  "France", "Germany", "Spain", "Italy", "Australia",
  "Canada", "China", "Asia",
].map((r) => ({ value: r, label: r }));

export const GENRE_OPTIONS: ChipPickerOption[] = [
  "Action", "Adventure", "RPG", "Platformer", "Puzzle",
  "Racing", "Shooter", "Sports", "Strategy", "Fighting",
  "Simulation", "Beat 'em up", "Arcade", "Horror",
].map((g) => ({ value: g, label: g }));

export const PLAY_STATUS_OPTIONS: Array<{
  value: GameFilters["playStatus"];
  label: string;
}> = [
  { value: undefined, label: "Any" },
  { value: "unplayed", label: "Unplayed" },
  { value: "played", label: "Played" },
  { value: "favorited", label: "Favorited" },
  { value: "play-later", label: "Play Later" },
];

// ─── Filter counting + serialization ──────────────────────────────

export function countActiveFilters(filters: GameFilters): number {
  let count = 0;
  if (filters.consoles?.length) count++;
  if (filters.regions?.length) count++;
  if (filters.genres?.length) count++;
  if (filters.themes?.length) count++;
  if (filters.keywords?.length) count++;
  if (filters.perspectives?.length) count++;
  if (filters.developer) count++;
  if (filters.publisher) count++;
  if (filters.yearMin != null) count++;
  if (filters.yearMax != null) count++;
  if (filters.ratingMin != null) count++;
  if (filters.ratingMax != null) count++;
  if (filters.playStatus) count++;
  return count;
}

/** Serialize a `GameFilters` into the saved-search wire format. */
export function filtersToRecord(
  f: GameFilters,
): Record<string, string | number> {
  const rec: Record<string, string | number> = {};
  if (f.consoles?.length) rec.consoles = f.consoles.join(",");
  if (f.regions?.length) rec.regions = f.regions.join(",");
  if (f.genres?.length) rec.genres = f.genres.join(",");
  if (f.themes?.length) rec.themes = f.themes.join(",");
  if (f.keywords?.length) rec.keywords = f.keywords.join(",");
  if (f.developer) rec.developer = f.developer;
  if (f.publisher) rec.publisher = f.publisher;
  if (f.yearMin != null) rec.yearMin = f.yearMin;
  if (f.yearMax != null) rec.yearMax = f.yearMax;
  if (f.ratingMin != null) rec.ratingMin = f.ratingMin;
  if (f.ratingMax != null) rec.ratingMax = f.ratingMax;
  if (f.playStatus) rec.playStatus = f.playStatus;
  if (f.search) rec.search = f.search;
  return rec;
}

/** Convert a saved-search filter record back to `GameFilters`. */
export function savedSearchToFilters(
  record: Record<string, string | number>,
  base: GameFilters,
): GameFilters {
  return {
    ...base,
    consoles: record.consoles ? String(record.consoles).split(",") : undefined,
    regions: record.regions ? String(record.regions).split(",") : undefined,
    genres: record.genres ? String(record.genres).split(",") : undefined,
    themes: record.themes ? String(record.themes).split(",") : undefined,
    keywords: record.keywords ? String(record.keywords).split(",") : undefined,
    developer: record.developer ? String(record.developer) : undefined,
    publisher: record.publisher ? String(record.publisher) : undefined,
    yearMin: record.yearMin != null ? Number(record.yearMin) : undefined,
    yearMax: record.yearMax != null ? Number(record.yearMax) : undefined,
    ratingMin: record.ratingMin != null ? Number(record.ratingMin) : undefined,
    ratingMax: record.ratingMax != null ? Number(record.ratingMax) : undefined,
    playStatus: record.playStatus as GameFilters["playStatus"],
    search: record.search ? String(record.search) : base.search,
    page: 1,
  };
}
