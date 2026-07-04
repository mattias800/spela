export interface GamePlatformTarget {
  gameId: string;
  consoleId: string;
  consoleName: string;
  isPreferred: boolean;
  isCurrent?: boolean;
}

interface GamePlatformSource {
  id: string;
  consoleId: string;
  consoleName: string;
  platforms?: readonly GamePlatformTarget[];
}

const COMPACT_LABEL_MAX_LENGTH = 12;
const COMPACT_ID_MIN_LENGTH = 2;
const COMPACT_ID_MAX_LENGTH = 6;

export function getGamePlatformTargets(
  game: GamePlatformSource,
): GamePlatformTarget[] {
  const fallback: GamePlatformTarget = {
    gameId: game.id,
    consoleId: game.consoleId,
    consoleName: game.consoleName,
    isPreferred: true,
  };
  const platforms = game.platforms ?? [];
  const source = platforms.length > 0 ? platforms : [fallback];
  const withCurrent = source.some((platform) => platform.gameId === game.id)
    ? source
    : [{ ...fallback, isPreferred: false }, ...source];
  const seen = new Set<string>();
  const targets = withCurrent.reduce<GamePlatformTarget[]>((items, platform) => {
    if (seen.has(platform.gameId)) return items;
    seen.add(platform.gameId);
    items.push(platform);
    return items;
  }, []);
  const currentIndex = targets.findIndex(
    (platform) => platform.gameId === game.id,
  );
  const preferredIndex = targets.findIndex((platform) => platform.isPreferred);
  let selectedIndex = 0;
  if (preferredIndex >= 0) {
    selectedIndex = preferredIndex;
  } else if (currentIndex >= 0) {
    selectedIndex = currentIndex;
  }

  return targets.map((platform, index) => ({
    ...platform,
    isPreferred: index === selectedIndex,
    isCurrent: platform.gameId === game.id,
  }));
}

export function compactPlatformLabel(platform: GamePlatformTarget): string {
  const name = platform.consoleName || platform.consoleId.toUpperCase();
  if (name.length <= COMPACT_LABEL_MAX_LENGTH) return name;

  const id = platform.consoleId.toUpperCase();
  const compactId =
    id.length >= COMPACT_ID_MIN_LENGTH &&
    id.length <= COMPACT_ID_MAX_LENGTH &&
    /^[A-Z0-9-]+$/.test(id);
  return compactId ? id : name;
}
