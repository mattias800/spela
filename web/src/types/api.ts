export interface User {
  id: string;
  username: string;
  email: string;
  role: "owner" | "admin" | "user";
  disabled: boolean;
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
  emulatorJsCore?: string; // EmulatorJS system identifier for browser play
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
  scrapeAttempts: number;
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

export interface Device {
  id: number;
  userId: number;
  deviceUuid: string;
  name: string;
  platform: string;
  lastSeenAt: string;
  createdAt: string;
  updatedAt: string;
  consoleShaders: Record<string, string>;
}

export interface ConsoleKeyMapping {
  selectedMapping: string;
  customMapping?: Record<string, string>;
}

export interface UserPreferences {
  showPerformanceOverlay: boolean;
  autoSaveEnabled: boolean;
  autoLoadSaveEnabled: boolean;
  selectedShader: string;
  consoleShaders: Record<string, string>;
  selectedKeyMapping: string;
  customKeyMapping: Record<string, string>;
  consoleKeyMappings: Record<string, ConsoleKeyMapping>;
  raLinked: boolean;
  raUsername: string;
  raHardcoreEnabled: boolean;
}

export interface RAStatus {
  linked: boolean;
  username: string;
  hardcoreEnabled: boolean;
}

export interface RALinkRequest {
  username: string;
  password: string;
}

export interface RASettingsRequest {
  hardcoreEnabled: boolean;
}

export interface Achievement {
  id: number;
  title: string;
  description: string;
  points: number;
  badgeUrl: string;
  type: string;
}

export interface GameAchievements {
  raGameId: number;
  totalCount: number;
  totalPoints: number;
  achievements: Achievement[];
}

export interface GameAchievementProgress {
  achievementId: number;
  unlockedAt: string;
  isHardcore: boolean;
}

export interface GameStats {
  totalPlayers: number;
  totalPlayTime: number;
  averagePlayTime: number;
  topPlayers: GameStatsPlayer[];
}

export interface GameStatsPlayer {
  userId: string;
  username: string;
  avatarUrl?: string;
  playTime: number;
}

export interface MostPlayedGame {
  game: Game;
  totalPlayers: number;
  totalPlayTime: number;
}

export interface MostPlayedGamesResponse {
  games: MostPlayedGame[];
}

export interface ActivePlayer {
  userId: string;
  username: string;
  avatarUrl?: string;
  totalPlayTime: number;
  gamesPlayed: number;
  lastPlayed: string;
}

export interface MostActivePlayersResponse {
  players: ActivePlayer[];
}

export interface UserStats {
  totalPlayTime: number;
  gamesPlayed: number;
  currentStreak: number;
  longestStreak: number;
  mostPlayedGame: Game | null;
  mostPlayedGameTime: number;
  lastPlayedAt: string | null;
}
