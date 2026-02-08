export interface User {
  id: string;
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
  id: string;
  name: string;
  abbreviation: string;
  extensions: string[];
  defaultCore: string;
  coverAspectRatio: number;
  colorTheme: string;
  gameCount: number;
  iconUrl?: string;
}

export interface Game {
  id: string;
  title: string;
  consoleId: string;
  consoleName: string;
  fileName: string;
  fileSize: number;
  coverUrl?: string;
  screenshotUrls?: string[];
  description?: string;
  developer?: string;
  publisher?: string;
  releaseDate?: string;
  genre?: string;
  players?: number;
  rating?: number;
  isFavorite: boolean;
  lastPlayedAt?: string;
  totalPlayTime: number;
  createdAt: string;
  updatedAt: string;
}

export interface SaveState {
  id: string;
  gameId: string;
  userId: string;
  name: string;
  screenshotUrl?: string;
  fileSize: number;
  isAutoSave: boolean;
  createdAt: string;
}

export interface ServerSettings {
  gameDirectories: string[];
  scrapeOnScan: boolean;
  allowRegistration: boolean;
  maxUploadSize: number;
  defaultScraperSource: string;
}

export interface PaginatedResponse<T> {
  data: T[];
  total: number;
  page: number;
  pageSize: number;
}

export interface GameFilters {
  search?: string;
  consoleId?: string;
  genre?: string;
  sortBy?: "title" | "releaseDate" | "lastPlayed" | "rating" | "recentlyAdded";
  sortOrder?: "asc" | "desc";
  page?: number;
  pageSize?: number;
}

export interface ScanStatus {
  isScanning: boolean;
  progress: number;
  totalFiles: number;
  processedFiles: number;
  newGamesFound: number;
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
