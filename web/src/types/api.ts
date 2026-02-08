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

// ConsoleResponse from backend responses.go DTO layer
export interface Console {
  id: string;
  name: string;
  abbreviation: string;
  extensions: string[]; // backend splits comma-separated into array
  defaultCore: string;
  coverAspectRatio: number; // backend parses "3:4" into 0.75
  colorTheme: string;
  iconUrl: string;
  gameCount: number;
  createdAt: string;
  updatedAt: string;
}

// GameResponse from backend responses.go DTO layer
export interface Game {
  id: string;
  title: string;
  consoleId: string;
  consoleName: string;
  fileName: string;
  fileSize: number;
  coverUrl?: string;
  screenshotUrls: string[];
  description?: string;
  developer?: string;
  publisher?: string;
  releaseDate?: string;
  genre?: string;
  players?: number;
  rating?: number;
  coreOverride?: string;
  scraperId?: string;
  isFavorite: boolean;
  lastPlayedAt?: string | null;
  totalPlayTime: number;
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

// Backend stores settings as flat key-value pairs
export type ServerSettingsMap = Record<string, string>;

// PaginatedResponse from backend responses.go
export interface GamesResponse {
  data: Game[];
  total: number;
  page: number;
  pageSize: number;
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
  gameId: string;
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
