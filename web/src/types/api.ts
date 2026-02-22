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
  saveStateSupport: boolean;
  browserPlayable: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface GameDisc {
  discNumber: number;
  fileName: string;
  fileSize: number;
}

// GameResponse from backend responses.go DTO layer
export interface Game {
  id: string;
  title: string;
  consoleId: string;
  consoleName: string;
  fileName: string;
  fileSize: number;
  discCount: number;
  discs?: GameDisc[];
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
  achievementsWarning?: string;
  verificationStatus?: "verified" | "unverified" | "not_applicable";
  verificationTag?: string;
  coverAspectRatio: number;
  biosStatus?: "ready" | "missing" | "invalid" | "not_required";
  isFavorite: boolean;
  isInPlayLater: boolean;
  averageRating: number;
  ratingCount: number;
  userRating?: number;
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

export interface MetadataMatchesResponse {
  unscraped: Game[];
  unverified: Game[];
  incomplete: Game[];
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
  selectedTheme: string;
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
  playTimeAtUnlock: number;
}

export interface GameAchievementProgressResponse {
  raGameId: number;
  progress: GameAchievementProgress[];
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

export interface AchievementLeaderboardEntry {
  userId: string;
  username: string;
  avatarUrl?: string;
  unlockedCount: number;
  earnedPoints: number;
  lastUnlockedAt: string;
  firstUnlockedAt: string;
  isComplete: boolean;
}

export interface AchievementLeaderboard {
  raGameId: number;
  totalAchievements: number;
  leaderboard: AchievementLeaderboardEntry[];
}

export interface AchievementTimelineEntry {
  achievementRaId: number;
  title: string;
  description: string;
  points: number;
  badgeUrl: string;
  unlockedAt: string;
  isHardcore: boolean;
  playTimeAtUnlock: number;
}

export interface AchievementTimeline {
  raGameId: number;
  gameTitle: string;
  totalPlayTime: number;
  timeline: AchievementTimelineEntry[];
  totalAchievements: number;
  unlockedCount: number;
  totalPoints: number;
  earnedPoints: number;
}

export interface RecentAchievement {
  achievementRaId: number;
  title: string;
  description: string;
  points: number;
  badgeUrl: string;
  unlockedAt: string;
  isHardcore: boolean;
  playTimeAtUnlock: number;
  gameId: string;
  gameTitle: string;
  consoleName: string;
  coverUrl: string;
}

export interface UserSearchResult {
  id: string;
  username: string;
  avatarUrl?: string;
}

export interface OnlineUser {
  id: string;
  username: string;
  avatarUrl?: string;
  currentGame?: {
    id: string;
    title: string;
    coverUrl?: string;
    consoleName: string;
  };
}

export interface OnlineUsersResponse {
  users: OnlineUser[];
}

export interface ActivityEvent {
  id: string;
  userId: string;
  username: string;
  userAvatarUrl?: string;
  eventType:
    | "started_playing"
    | "favorited_game"
    | "rated_game"
    | "shared_save"
    | "queued_play_later"
    | "challenge_created"
    | "challenge_completed"
    | "challenge_record";
  gameId: string;
  gameTitle: string;
  gameCoverUrl?: string;
  gameConsoleName: string;
  metadata?: Record<string, unknown>;
  createdAt: string;
}

export interface ActivityFeedResponse {
  data: ActivityEvent[];
  total: number;
  page: number;
  pageSize: number;
}

export interface GameRating {
  id: string;
  userId: string;
  username: string;
  avatarUrl?: string;
  gameId: string;
  rating: number;
  review?: string;
  createdAt: string;
  updatedAt: string;
}

export interface GameRatingsResponse {
  data: GameRating[];
  total: number;
  page: number;
  pageSize: number;
}

export interface RatingSummary {
  averageRating: number;
  totalRatings: number;
  distribution: Record<string, number>;
}

export interface SharedSave {
  id: string;
  userId: string;
  username: string;
  avatarUrl?: string;
  gameId: string;
  name: string;
  description?: string;
  fileSize: number;
  screenshotUrl?: string;
  downloadCount: number;
  createdAt: string;
}

export interface SharedSavesResponse {
  data: SharedSave[];
  total: number;
  page: number;
  pageSize: number;
}

export interface Collection {
  id: string;
  userId: string;
  username: string;
  avatarUrl?: string;
  name: string;
  description?: string;
  isPublic: boolean;
  coverUrl?: string;
  gameCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface CollectionDetail extends Collection {
  games: Game[];
}

export interface CollectionsResponse {
  data: Collection[];
  total: number;
  page: number;
  pageSize: number;
}

export interface PublicProfileGame {
  id: string;
  title: string;
  coverUrl?: string;
  consoleName: string;
  playTime?: number;
}

export interface PublicProfile {
  id: string;
  username: string;
  avatarUrl?: string;
  memberSince: string;
  isOnline: boolean;
  currentGame?: {
    id: string;
    title: string;
    coverUrl?: string;
    consoleName: string;
  };
  totalPlayTime: number;
  gamesPlayed: number;
  favoriteGames: PublicProfileGame[];
  recentGames: PublicProfileGame[];
  topGames: PublicProfileGame[];
}

export interface Relay {
  id: string;
  name: string;
  description?: string;
  gameId: string;
  gameTitle: string;
  gameCoverUrl?: string;
  gameConsoleName: string;
  ownerId: string;
  ownerUsername: string;
  status: "active" | "paused" | "completed";
  memberCount: number;
  lastActivityAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface RelayMember {
  userId: string;
  username: string;
  avatarUrl?: string;
  role: "owner" | "member";
  joinedAt: string;
  lastPlayedAt?: string;
  isOnline: boolean;
}

export interface RelayDetail extends Relay {
  members: RelayMember[];
}

export interface RelayInvitation {
  id: string;
  relayId: string;
  relayName: string;
  gameId: string;
  gameTitle: string;
  gameCoverUrl?: string;
  gameConsoleName: string;
  inviterUsername: string;
  inviterAvatarUrl?: string;
  createdAt: string;
}

export interface RelaySave {
  id: string;
  relayId: string;
  gameId: string;
  userId: string;
  username: string;
  avatarUrl?: string;
  name: string;
  fileSize: number;
  isAuto: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface RelaysResponse {
  data: Relay[];
  total: number;
  page: number;
  pageSize: number;
}

export interface RelayInvitationsResponse {
  data: RelayInvitation[];
  total: number;
}

export interface NetplaySession {
  id: string;
  hostId: string;
  hostUsername: string;
  hostAvatarUrl: string | null;
  clientId: string | null;
  clientUsername: string | null;
  clientAvatarUrl: string | null;
  gameId: string;
  gameTitle: string;
  gameCoverUrl: string | null;
  consoleName: string;
  coverAspectRatio: number;
  status: "waiting" | "in_progress" | "ended";
  endReason: "host_left" | "client_left" | "timeout" | "completed" | null;
  inputDelay: number;
  inviteCode: string;
  createdAt: string;
  startedAt: string | null;
  endedAt: string | null;
}

export interface NetplaySessionsResponse {
  data: NetplaySession[];
  total: number;
  page: number;
  pageSize: number;
}

export interface BiosFile {
  name: string;
  size: number;
  md5: string;
  consoleId: string | null;
  consoleName: string | null;
  description: string | null;
  required: boolean;
  status: "valid" | "present" | "invalid" | "missing";
}

export interface BiosConsoleFile {
  fileName: string;
  description: string;
  required: boolean;
  md5: string;
  status: "valid" | "present" | "invalid" | "missing";
}

export interface BiosConsole {
  consoleId: string;
  consoleName: string;
  biosRequired: boolean;
  status: "ready" | "missing" | "invalid" | "not_required";
  requiredPresent: number;
  requiredTotal: number;
  optionalPresent: number;
  optionalTotal: number;
  files: BiosConsoleFile[];
}

export interface BiosResponse {
  files: BiosFile[];
  consoles: BiosConsole[];
}

export type BiosFileStatus = BiosFile["status"];
export type BiosConsoleStatus = BiosConsole["status"];

// --- Challenges ---

export type ChallengeType = "completion" | "speedrun" | "survival";
export type ChallengeDifficulty = "easy" | "medium" | "hard";
export type ChallengeStatus = "active" | "closed" | "expired";
export type AttemptStatus = "in_progress" | "completed" | "abandoned";

export interface Challenge {
  id: string;
  creatorId: string;
  creatorUsername: string;
  creatorAvatarUrl?: string;
  gameId: string;
  gameTitle: string;
  gameCoverUrl?: string;
  gameConsoleName: string;
  name: string;
  description?: string;
  type: ChallengeType;
  difficulty: ChallengeDifficulty;
  status: ChallengeStatus;
  screenshotUrl?: string;
  coreName: string;
  saveFileSize: number;
  attemptCount: number;
  completionCount: number;
  expiresAt?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ChallengesResponse {
  data: Challenge[];
  total: number;
  page: number;
  pageSize: number;
}

export interface ChallengeFilters {
  gameId?: string;
  consoleId?: string;
  difficulty?: ChallengeDifficulty;
  type?: ChallengeType;
  status?: ChallengeStatus;
  sortBy?: "newest" | "most_attempted" | "ending_soon";
  page?: number;
  pageSize?: number;
}

export interface ChallengeAttempt {
  id: string;
  challengeId: string;
  userId: string;
  username: string;
  avatarUrl?: string;
  status: AttemptStatus;
  startedAt: string;
  completedAt?: string | null;
  durationMs: number;
  isBest: boolean;
}

export interface ChallengeLeaderboardEntry {
  rank: number;
  userId: string;
  username: string;
  avatarUrl?: string;
  durationMs: number;
  completedAt: string;
}

export interface ChallengeLeaderboardResponse {
  data: ChallengeLeaderboardEntry[];
  total: number;
  page: number;
  pageSize: number;
}

export interface StartAttemptResponse {
  attemptId: string;
  startedAt: string;
}

export interface CompleteAttemptResponse {
  attempt: ChallengeAttempt;
  rank: number;
  isNewBest: boolean;
}
