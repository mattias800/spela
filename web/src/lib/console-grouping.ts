import type { Console } from "@/types/api";

export type ConsoleGrouping = "generation" | "manufacturer";

export interface GenerationInfo {
  label: string;
  years: string;
}

export interface GenerationGroup {
  kind: "generation";
  key: string;
  generation: number;
  info: GenerationInfo;
  consoles: Console[];
}

export interface ManufacturerGroup {
  kind: "manufacturer";
  key: string;
  makerCode: string;
  makerName: string;
  consoles: Console[];
}

export type ConsoleGroup = GenerationGroup | ManufacturerGroup;

const VARIOUS_MAKER_CODE = "various";

export function generationInfo(generation: number): GenerationInfo {
  switch (generation) {
    case 2:
      return { label: "2nd Generation", years: "1976–1982" };
    case 3:
      return { label: "3rd Generation", years: "1983–1987" };
    case 4:
      return { label: "4th Generation", years: "1987–1993" };
    case 5:
      return { label: "5th Generation", years: "1993–1998" };
    case 6:
      return { label: "6th Generation", years: "1998–2005" };
    case 7:
      return { label: "7th Generation", years: "2005–2012" };
    case 8:
      return { label: "8th Generation", years: "2012–2020" };
    case 9:
      return { label: "9th Generation", years: "2020–present" };
    case 100:
      return { label: "Home Computers", years: "1977–1995" };
    case 101:
      return { label: "Arcade", years: "1971–present" };
    default:
      return { label: "Other", years: "" };
  }
}

export function groupByGeneration(consoles: Console[]): GenerationGroup[] {
  const map = new Map<number, Console[]>();
  for (const c of consoles) {
    const gen = c.generation || 0;
    if (!map.has(gen)) map.set(gen, []);
    map.get(gen)!.push(c);
  }
  return Array.from(map.entries())
    .sort(([a], [b]) => a - b)
    .map(([gen, items]) => ({
      kind: "generation",
      key: `gen-${gen}`,
      generation: gen,
      info: generationInfo(gen),
      consoles: items,
    }));
}

/**
 * Groups consoles by their `maker.code`. Manufacturers are sorted
 * alphabetically by `maker.name` so the user can predict where each
 * row lives; the catch-all `"various"` bucket (arcade / DOS demos /
 * Amiga demos / scummvm — see seed_metadata.go) is pinned last so
 * the named manufacturers read first.
 *
 * Within each manufacturer, consoles are ordered by `releaseYear`
 * ascending to match the chronological intra-group order used by the
 * generation view. Consoles missing `maker` (rare, but possible on DB
 * rows that pre-date the maker column) are bucketed into `Various`
 * rather than dropped — keeping the grouping idempotent across data
 * states.
 */
export function groupByManufacturer(consoles: Console[]): ManufacturerGroup[] {
  const buckets = new Map<string, { name: string; consoles: Console[] }>();
  for (const c of consoles) {
    const code = c.maker?.code ?? VARIOUS_MAKER_CODE;
    const name = c.maker?.name ?? "Various";
    if (!buckets.has(code)) {
      buckets.set(code, { name, consoles: [] });
    }
    buckets.get(code)!.consoles.push(c);
  }
  for (const bucket of buckets.values()) {
    bucket.consoles.sort(
      (a, b) => (a.releaseYear ?? 0) - (b.releaseYear ?? 0),
    );
  }
  return Array.from(buckets.entries())
    .sort(([codeA, a], [codeB, b]) => {
      // Pin "various" to the end regardless of name.
      if (codeA === VARIOUS_MAKER_CODE) return 1;
      if (codeB === VARIOUS_MAKER_CODE) return -1;
      return a.name.localeCompare(b.name);
    })
    .map(([code, bucket]) => ({
      kind: "manufacturer",
      key: `maker-${code}`,
      makerCode: code,
      makerName: bucket.name,
      consoles: bucket.consoles,
    }));
}

export function groupConsoles(
  consoles: Console[],
  grouping: ConsoleGrouping,
): ConsoleGroup[] {
  return grouping === "manufacturer"
    ? groupByManufacturer(consoles)
    : groupByGeneration(consoles);
}
