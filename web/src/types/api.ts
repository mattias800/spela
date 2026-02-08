export interface User {
  id: number;
  username: string;
  email: string;
  role: "admin" | "user";
  avatarUrl?: string;
  createdAt: string;
  updatedAt: string;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  user: User;
}

export interface Console {
  id: number;
  name: string;
  abbreviation: string;
  extensions: string; // comma-separated
  defaultCore: string;
  coverAspect: string; // e.g. "3:4"
  colorTheme: string;
  gameCount?: number;
  createdAt: string;
  updatedAt: string;
}

export interface Game {
  id: number;
  title: string;
  consoleId: number;
  console?: Console;
  fileName: string;
  fileSize: number;
  coverUrl?: string;
  screenshotUrl?: string;
  description?: string;
  developer?: string;
  publisher?: string;
  releaseDate?: string;
  genre?: string;
  players?: number;
  rating?: number;
  coreOverride?: string;
  createdAt: string;
  updatedAt: string;
}

export interface SaveState {
  id: number;
  gameId: number;
  userId: number;
  name: string;
  fileSize: number;
  isAuto: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface PlayHistory {
  id: number;
  userId: number;
  gameId: number;
  game?: Game;
  lastPlayed: string;
  playTime: number;
  createdAt: string;
  updatedAt: string;
}

export interface Favorite {
  id: number;
  userId: number;
  gameId: number;
  game?: Game;
  createdAt: string;
}

// Backend stores settings as flat key-value pairs
export type ServerSettingsMap = Record<string, string>;

export interface GamesResponse {
  games: Game[];
  total: number;
  page: number;
  perPage: number;
}

export interface ConsoleGamesResponse {
  console: Console;
  games: Game[];
}

export interface GameFilters {
  search?: string;
  consoleId?: string;
  genre?: string;
  sortBy?: "title" | "created_at" | "file_size" | "rating";
  sortOrder?: "asc" | "desc";
  page?: number;
  pageSize?: number;
}

export interface MetadataMatch {
  gameId: number;
  currentTitle: string;
  currentCoverUrl?: string;
  suggestions: MetadataSuggestion[];
}

export interface MetadataSuggestion {
  source: string;
  title: string;
  coverUrl?: string;
  description?: string;
  developer?: string;
  publisher?: string;
  releaseDate?: string;
  genre?: string;
  confidence: number;
}
