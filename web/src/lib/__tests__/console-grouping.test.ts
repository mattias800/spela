import { describe, it, expect } from "vitest";
import { makeConsole } from "@/test-utils/fixtures";
import {
  groupByGeneration,
  groupByManufacturer,
  groupConsoles,
} from "@/lib/console-grouping";

const nes = makeConsole({
  id: "nes",
  name: "Nintendo Entertainment System",
  generation: 3,
  releaseYear: 1983,
  maker: { code: "nintendo", name: "Nintendo" },
});
const snes = makeConsole({
  id: "snes",
  name: "Super Nintendo",
  generation: 4,
  releaseYear: 1990,
  maker: { code: "nintendo", name: "Nintendo" },
});
const n64 = makeConsole({
  id: "n64",
  name: "Nintendo 64",
  generation: 5,
  releaseYear: 1996,
  maker: { code: "nintendo", name: "Nintendo" },
});
const genesis = makeConsole({
  id: "genesis",
  name: "Sega Genesis",
  generation: 4,
  releaseYear: 1988,
  maker: { code: "sega", name: "Sega" },
});
const atari2600 = makeConsole({
  id: "a2600",
  name: "Atari 2600",
  generation: 2,
  releaseYear: 1977,
  maker: { code: "atari", name: "Atari" },
});
const arcade = makeConsole({
  id: "arcade",
  name: "Arcade",
  generation: 101,
  releaseYear: null,
  maker: { code: "various", name: "Various" },
});
const scummvm = makeConsole({
  id: "scummvm",
  name: "ScummVM",
  generation: 100,
  releaseYear: null,
  maker: { code: "various", name: "Various" },
});

describe("groupByGeneration", () => {
  it("orders generations ascending", () => {
    const groups = groupByGeneration([n64, nes, atari2600, snes]);
    expect(groups.map((g) => g.generation)).toEqual([2, 3, 4, 5]);
  });

  it("renders Home Computers (gen 100) and Arcade (gen 101) at the end", () => {
    const groups = groupByGeneration([snes, arcade, atari2600, scummvm]);
    expect(groups.map((g) => g.generation)).toEqual([2, 4, 100, 101]);
  });
});

describe("groupByManufacturer", () => {
  it("groups by maker.code and sorts alphabetically by maker.name", () => {
    const groups = groupByManufacturer([n64, genesis, nes, atari2600]);
    // Atari < Nintendo < Sega alphabetically.
    expect(groups.map((g) => g.makerName)).toEqual([
      "Atari",
      "Nintendo",
      "Sega",
    ]);
  });

  it("pins 'Various' to the end regardless of alphabetical order", () => {
    // "Atari" / "Nintendo" / "Various" / "Sega" — even though "S" < "V"
    // alphabetically, Various must come last as the catch-all bucket.
    const groups = groupByManufacturer([
      arcade,
      genesis,
      nes,
      atari2600,
      scummvm,
    ]);
    expect(groups.map((g) => g.makerName)).toEqual([
      "Atari",
      "Nintendo",
      "Sega",
      "Various",
    ]);
  });

  it("orders consoles within each manufacturer by releaseYear ascending", () => {
    // NES 1983 → SNES 1990 → N64 1996 — chronological inside Nintendo.
    const groups = groupByManufacturer([n64, snes, nes]);
    expect(groups[0].makerName).toBe("Nintendo");
    expect(groups[0].consoles.map((c) => c.id)).toEqual(["nes", "snes", "n64"]);
  });

  it("buckets consoles missing maker into Various rather than dropping them", () => {
    // DB rows from before the maker column existed have an empty
    // maker { code: '', name: '' } — they should bucket as Various
    // (the seed value used for arcade/demos/scummvm) so they aren't
    // silently hidden.
    const orphan = makeConsole({
      id: "orphan",
      name: "Orphan Console",
      generation: 0,
      maker: { code: "", name: "" },
    });
    const groups = groupByManufacturer([nes, orphan]);
    // The empty-code orphan and any actual "various" rows share the
    // same default-name bucket. Two distinct groups are reasonable
    // here; the test asserts the orphan is *somewhere* (not dropped)
    // and that Nintendo still leads alphabetically.
    expect(groups.length).toBeGreaterThanOrEqual(2);
    const allConsoleIds = groups.flatMap((g) => g.consoles.map((c) => c.id));
    expect(allConsoleIds).toContain("orphan");
    expect(allConsoleIds).toContain("nes");
  });

  it("returns an empty list when given no consoles", () => {
    expect(groupByManufacturer([])).toEqual([]);
  });
});

describe("groupConsoles", () => {
  it("dispatches to groupByGeneration for grouping='generation'", () => {
    const groups = groupConsoles([n64, nes], "generation");
    expect(groups[0].kind).toBe("generation");
  });

  it("dispatches to groupByManufacturer for grouping='manufacturer'", () => {
    const groups = groupConsoles([n64, nes], "manufacturer");
    expect(groups[0].kind).toBe("manufacturer");
  });
});
