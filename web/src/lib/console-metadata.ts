// Per-console accent colour table used by [ConsoleBadge] in places
// where the surrounding code only has a console abbreviation/code
// (e.g. a game card showing its console badge — the game DTO carries
// `consoleId`/`consoleAbbreviation` but not `colorTheme`).
//
// **Source of truth.** The authoritative console accent colour is
// `Console.colorTheme` from the server, seeded in
// `server/internal/db/database.go`. Components that already receive a
// full Console (or ConsoleHighlight) — `ConsoleCard`, `ConsoleHeroBanner`,
// the Explore "Browse by Console" strip — read `colorTheme` directly
// and bypass this table entirely (#1167).
//
// This table is therefore a *fallback* keyed by abbreviation for the
// badge case only. The values here SHOULD match the server's seed; if
// they drift, edit `database.go` first and propagate the same hex
// here. A follow-up task should remove this table by plumbing
// `colorTheme` through to ConsoleBadge call sites.
interface ConsoleStyle {
  color: string;
}

const consoleStyles: Record<string, ConsoleStyle> = {
  nes: { color: "#e60012" },
  snes: { color: "#7b7db5" },
  gb: { color: "#8bac0f" },
  gbc: { color: "#319795" },
  gba: { color: "#667eea" },
  n64: { color: "#48bb78" },
  nds: { color: "#a0aec0" },
  sms: { color: "#4299e1" },
  genesis: { color: "#171717" },
  saturn: { color: "#718096" },
  psx: { color: "#a0aec0" },
  psp: { color: "#4a5568" },
  neogeo: { color: "#ecc94b" },
  neocd: { color: "#ecc94b" },
  arcade: { color: "#f6ad55" },
  tg16: { color: "#ed8936" },
  pcecd: { color: "#ed8936" },
  atari2600: { color: "#1e293b" },
  gg: { color: "#2563eb" },
  scd: { color: "#334155" },
  "32x": { color: "#1e293b" },
  dc: { color: "#f97316" },
  vb: { color: "#991b1b" },
  "3ds": { color: "#0c4a6e" },
  gc: { color: "#6f5fa6" },
  a52: { color: "#b45309" },
  a78: { color: "#1e293b" },
  lynx: { color: "#ca8a04" },
  jag: { color: "#b91c1c" },
  ngp: { color: "#6b7280" },
  ws: { color: "#4f46e5" },
  pcfx: { color: "#0d9488" },
  cv: { color: "#0284c7" },
  pkmn: { color: "#facc15" },
  ps2: { color: "#1e40af" },
  c64: { color: "#6366f1" },
  dos: { color: "#15803d" },
  amiga: { color: "#dc2626" },
  acd32: { color: "#6c5eb5" },
  ps3: { color: "#003087" },
  ps4: { color: "#003087" },
  ps5: { color: "#003087" },
  xbox: { color: "#107c10" },
  x360: { color: "#107c10" },
  xone: { color: "#107c10" },
  xsx: { color: "#107c10" },
  wii: { color: "#c0c0c0" },
  wiiu: { color: "#009ac7" },
  nsw: { color: "#e60012" },
  "3do": { color: "#c0c0c0" },
  c128: { color: "#6c5eb5" },
  pet: { color: "#6c5eb5" },
  plus4: { color: "#6c5eb5" },
  vic20: { color: "#6c5eb5" },
  cdi: { color: "#006633" },
  msx1: { color: "#4a86c8" },
  msx2: { color: "#4a86c8" },
  channelf: { color: "#cc6600" },
  odyssey2: { color: "#b8860b" },
  intellivision: { color: "#8b6914" },
  vectrex: { color: "#333333" },
  "sg-1000": { color: "#0060a8" },
  sgx: { color: "#ed8936" },
  fds: { color: "#e60012" },
  gw: { color: "#c0392b" },
  atari800: { color: "#8b4513" },
  atarist: { color: "#8b4513" },
  vita: { color: "#003087" },
};

// Map backend abbreviations to our internal keys
const abbreviationAliases: Record<string, string> = {
  gen: "genesis",
  md: "genesis",
  sat: "saturn",
  pce: "tg16",
  a26: "atari2600",
  mame: "arcade",
  chaf: "channelf",
  o2: "odyssey2",
  intv: "intellivision",
  vec: "vectrex",
  sg1k: "sg-1000",
  a800: "atari800",
};

const defaultStyle: ConsoleStyle = {
  color: "#adb5bd",
};

export function getConsoleStyle(abbreviation: string): ConsoleStyle {
  const key = abbreviation.toLowerCase();
  return (
    consoleStyles[key] ??
    consoleStyles[abbreviationAliases[key] ?? ""] ??
    defaultStyle
  );
}
