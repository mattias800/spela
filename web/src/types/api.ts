export interface User {
  id: string;
  username: string;
  email: string;
  role: "owner" | "admin" | "user";
  disabled: boolean;
  pendingApproval: boolean;
  avatarUrl?: string;
  createdAt: string;
  updatedAt: string;
}

export interface RateLimitStatus {
  failedCount: number;
  lockedUntil: string | null;
  isLockedOut: boolean;
}

export interface DeletedUser {
  id: string;
  username: string;
  email: string;
  role: "owner" | "admin" | "user";
  disabled: boolean;
  createdAt: string;
  deletedAt: string;
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
  logoUrl: string;
  gameCount: number;
  saveStateSupport: boolean;
  browserPlayable: boolean;
  playable: boolean;
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
  region?: string;
  coverAspectRatio: number;
  playable: boolean;
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
  // Multi-select filters (comma-separated when sent to API)
  consoles?: string[];
  genres?: string[];
  themes?: string[];
  keywords?: string[];
  perspectives?: string[];
  // Text search filters
  developer?: string;
  publisher?: string;
  // Range filters
  yearMin?: number;
  yearMax?: number;
  ratingMin?: number;
  ratingMax?: number;
  // Play status
  playStatus?: "unplayed" | "played" | "favorited" | "play-later";
  sortBy?: "title" | "created_at" | "file_size" | "rating" | "release_date";
  sortOrder?: "asc" | "desc";
  page?: number;
  pageSize?: number;
}

export interface SavedSearch {
  id: string;
  name: string;
  filters: Record<string, string | number>;
  createdAt: string;
}

export interface MetadataMatchesResponse {
  unscraped: Game[];
  unverified: Game[];
  incomplete: Game[];
}

export interface IgdbSearchResult {
  igdbId: number;
  name: string;
  coverUrl?: string;
  releaseYear?: number;
  developer?: string;
  summary?: string;
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

export interface UserSearchResponse {
  data: UserSearchResult[];
  total: number;
  page: number;
  pageSize: number;
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

export interface SharedSession {
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
  activeUserId: string | null;
  activeUsername?: string;
  turnTakenAt: string | null;
  memberCount: number;
  sessionId?: string | null;
  lastActivityAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface SharedSessionMember {
  userId: string;
  username: string;
  avatarUrl?: string;
  role: "owner" | "member";
  joinedAt: string;
  lastPlayedAt?: string;
  isOnline: boolean;
}

export interface SharedSessionDetail extends SharedSession {
  members: SharedSessionMember[];
}

export interface SharedSessionInvitation {
  id: string;
  sharedSessionId: string;
  sharedSessionName: string;
  gameId: string;
  gameTitle: string;
  gameCoverUrl?: string;
  gameConsoleName: string;
  inviterUsername: string;
  inviterAvatarUrl?: string;
  createdAt: string;
}

export interface SharedSessionSave {
  id: string;
  sharedSessionId: string;
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

export interface SharedSessionsResponse {
  data: SharedSession[];
  total: number;
  page: number;
  pageSize: number;
}

export interface SharedSessionInvitationsResponse {
  data: SharedSessionInvitation[];
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

export interface NetplayInvite {
  id: string;
  netplaySessionId: string;
  inviterId: string;
  inviterUsername: string;
  inviterAvatarUrl: string | null;
  inviteeId: string;
  inviteeUsername: string;
  inviteeAvatarUrl: string | null;
  gameId: string;
  gameTitle: string;
  gameCoverUrl: string | null;
  consoleName: string;
  hostUsername: string;
  inputDelay: number;
  status: "pending" | "accepted" | "declined" | "expired";
  createdAt: string;
}

export interface NetplayInvitesResponse {
  data: NetplayInvite[];
  total: number;
}

export interface BiosFile {
  name: string;
  size: number;
  md5: string;
  expectedMd5?: string;
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

// --- Top Lists ---

export interface TopListGame {
  rank: number;
  gameId: string;
  name: string;
  coverUrl: string;
  consoleName: string;
  consoleId: string;
  rating: number;
}

export interface PlayStatsEntry {
  gameId: number;
  playTime: number;
  lastPlayedAt: string;
}

export interface DailyPlayActivity {
  date: string;
  playTime: number;
}

// --- Session Saves & Cheats ---

export interface SessionSave {
  id: string;
  sessionId: string;
  name: string;
  fileSize: number;
  screenshotUrl: string | null;
  isAuto: boolean;
  isCurrent: boolean;
  coreName: string | null;
  coreMatch: boolean | null;
  currentCore: string | null;
  notes: string | null;
  slot: number | null;
  createdAt: string;
}

export interface CoreCompatibilityEntry {
  consoleId: string;
  consoleName: string;
  nativeCore: string;
  webCore: string;
  matched: boolean;
}

export interface CoreCompatibilityResponse {
  consoles: CoreCompatibilityEntry[];
}

export interface SessionCheatConfig {
  cheatsEnabled: boolean;
  enabledIndices: number[];
}

// --- Game Sessions ---

export interface GameSession {
  id: string;
  gameId: string;
  name: string;
  lastPlayedAt: string | null;
  lastPlayedByUsername: string | null;
  totalPlayTime: number;
  screenshotUrl: string | null;
  cheatsEnabled: boolean;
  isSharedSession: boolean;
  sharedSessionId?: string;
  memberCount: number;
  memberUsernames: string[];
  memberAvatars: string[] | null;
  createdAt: string;
  updatedAt: string;
}

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

// --- Staged Uploads ---

export type StagedUploadStatus =
  | "pending_console"
  | "pending_scrape"
  | "ready"
  | "duplicate"
  | "accepted"
  | "rejected";

export interface PossibleConsole {
  id: string;
  name: string;
}

// --- Themes & Keywords ---

export interface Theme {
  id: string;
  name: string;
  gameCount: number;
}

export interface Keyword {
  id: string;
  name: string;
  gameCount: number;
}

// --- Series & Franchises ---

export interface FeaturedSeries {
  id: string;
  name: string;
  libraryGames: number;
  totalGames: number;
  consoleCount: number;
  heroUrl: string;
}

export interface SeriesConsole {
  abbreviation: string;
  name: string;
  color: string;
  gameCount: number;
}

export interface SeriesGame {
  igdbGameId: number;
  name: string;
  inLibrary: boolean;
  localGameId: string | null;
  coverUrl: string | null;
  releaseDate: string | null;
  rating: number;
  consoleAbbreviation: string;
  consoleName: string;
  consoleColor: string;
}

export interface SeriesDetail {
  id: string;
  name: string;
  heroUrl: string;
  consoles: SeriesConsole[];
  libraryGames: number;
  totalGames: number;
  games: SeriesGame[];
}

export interface GameSeriesLink {
  id: string;
  name: string;
  totalGames: number;
  libraryGames: number;
}

export interface GameFranchiseLink {
  id: string;
  name: string;
  totalGames: number;
  libraryGames: number;
}

export interface FranchiseDetail {
  id: string;
  igdbFranchiseId: number;
  name: string;
  heroUrl: string;
  consoles: SeriesConsole[];
  libraryGames: number;
  totalGames: number;
  games: SeriesGame[];
}

// --- Visual Browsing / Gallery ---

export interface ScreenshotItem {
  url: string;
  gameId: string;
  gameTitle: string;
  consoleName: string;
  consoleAbbreviation: string;
  consoleColor: string;
}

export interface ScreenshotGalleryResponse {
  screenshots: ScreenshotItem[];
  page: number;
  totalPages: number;
  totalCount: number;
}

export interface ArtworkItem {
  url: string;
  width: number;
  height: number;
  gameId: string;
  gameTitle: string;
  consoleName: string;
  consoleAbbreviation: string;
  consoleColor: string;
}

export interface ArtworkGalleryResponse {
  artworks: ArtworkItem[];
  page: number;
  totalPages: number;
  totalCount: number;
}

export interface CoverItem {
  coverUrl: string;
  gameId: string;
  gameTitle: string;
  consoleName: string;
  consoleAbbreviation: string;
  consoleColor: string;
  rating: number;
  coverAspectRatio: number;
}

export interface CoverGalleryResponse {
  covers: CoverItem[];
  page: number;
  totalPages: number;
  totalCount: number;
}

// --- Console Showcase ---

export interface GenreCount {
  name: string;
  gameCount: number;
}

export interface ConsoleShowcase {
  console: Console;
  essentials: Game[];
  hiddenGems: Game[];
  genreBreakdown: GenreCount[];
  topDevelopers: DeveloperSummary[];
  recentlyPlayed: Game[];
}

export interface ConsoleHighlight {
  id: string;
  name: string;
  colorTheme: string;
  iconUrl: string;
  logoUrl: string;
  gameCount: number;
  topGame: Game | null;
}

export interface ConsoleHighlightsResponse {
  consoles: ConsoleHighlight[];
}

// --- Moods ---

export interface MoodDefinition {
  id: string;
  name: string;
  description: string;
  icon: string;
  gradient: string[];
}

// --- Explore ---

export interface FeaturedGame {
  gameId: string;
  title: string;
  heroUrl: string;
  logoUrl: string | null;
  consoleAbbreviation: string;
  consoleColor: string;
  rating: number;
  genre: string;
  isFavorite: boolean;
  isPlayLater: boolean;
}

export interface ExploreRow {
  id: string;
  title: string;
  games: Game[];
}

export interface ExploreRowsResponse {
  rows: ExploreRow[];
}

// --- For You / Personalized Recommendations ---

/** GameSummary is a full Game object when returned by recommendation endpoints */
export type GameSummary = Game;

export interface ForYouRow {
  type: "because_you_played" | "more_genre" | "unfinished" | "expand_horizons";
  title: string;
  sourceGame?: GameSummary;
  genre?: string;
  games: GameSummary[];
}

export interface ForYouResponse {
  rows: ForYouRow[];
}

export interface TasteBreakdown {
  name: string;
  percentage: number;
  playTime: number;
  gameCount: number;
}

export interface ConsoleBreakdown {
  name: string;
  abbreviation: string;
  playTime: number;
  gameCount: number;
}

export interface TasteProfile {
  totalPlayTime: number;
  genres: TasteBreakdown[];
  themes: TasteBreakdown[];
  topConsoles: ConsoleBreakdown[];
}

export interface PlayersLikeYouResponse {
  games: GameSummary[];
  similarUsersCount: number;
}

// --- Developers & Publishers ---

export interface DeveloperSummary {
  name: string;
  gameCount: number;
  avgRating: number;
  consoles: string[];
}

export interface DeveloperListResponse {
  developers: DeveloperSummary[];
}

export interface DeveloperDetailResponse {
  name: string;
  gameCount: number;
  avgRating: number;
  consoles: string[];
  games: Game[];
}

export interface PublisherDetailResponse {
  name: string;
  gameCount: number;
  avgRating: number;
  consoles: string[];
  games: Game[];
}

export interface DeveloperSpotlightResponse {
  name: string;
  gameCount: number;
  avgRating: number;
  consoles: string[];
  topGames: Game[];
  heroUrl: string;
}

export interface StagedUpload {
  id: string;
  fileName: string;
  originalFileName: string;
  fileSize: number;
  consoleId: string;
  consoleName: string;
  possibleConsoles: PossibleConsole[];
  status: StagedUploadStatus;
  title: string;
  coverUrl: string;
  rating: number;
  verificationStatus: string;
  crc32: string;
  canonicalName: string;
}

// --- Phase 10: Social & Community Discovery ---

export interface TrendingGame {
  game: Game;
  playersThisWeek: number;
}

export interface TrendingResponse {
  games: TrendingGame[];
}

export interface CommunityTopGame {
  game: Game;
  avgRating: number;
  ratingCount: number;
}

export interface CommunityTopResponse {
  games: CommunityTopGame[];
}

export interface CultClassicGame {
  game: Game;
  communityRating: number;
  igdbRating: number;
  ratingCount: number;
}

export interface CultClassicsResponse {
  games: CultClassicGame[];
}

export interface RecentReviewItem {
  game: Game;
  rating: number;
  review: string;
  reviewerName: string;
  reviewedAt: string;
}

export interface RecentlyReviewedResponse {
  reviews: RecentReviewItem[];
}

export interface ActiveNowItem {
  game: Game;
  activeSessions: number;
  activeChallenges: number;
}

export interface ActiveNowResponse {
  games: ActiveNowItem[];
}

// --- Phase 11: Temporal Discovery ---

export interface OnThisDayResponse {
  date: string;
  games: Game[];
}

export interface BestOfYearResponse {
  year: number;
  games: Game[];
}

export interface AnniversaryItem {
  game: Game;
  yearsAgo: number;
  playedAt: string;
}

export interface YourAnniversariesResponse {
  anniversaries: AnniversaryItem[];
}

export interface DecadeResponse {
  decade: string;
  label: string;
  games: Game[];
}

// --- Phase 12: Achievement & Challenge Discovery ---

export interface AchievementGameItem {
  game: Game;
  totalAchievements: number;
  avgCompletion: number;
  playersAttempted: number;
  playersCompleted: number;
}

export interface EasyToCompleteResponse {
  games: AchievementGameItem[];
}

export interface HardestGamesResponse {
  games: AchievementGameItem[];
}

export interface AlmostDoneGame {
  game: Game;
  unlockedCount: number;
  totalCount: number;
  completionPercent: number;
}

export interface AlmostDoneResponse {
  games: AlmostDoneGame[];
}

export interface FreshChallengeGame {
  game: Game;
  totalAchievements: number;
  totalPoints: number;
}

export interface FreshChallengesResponse {
  games: FreshChallengeGame[];
}

export interface ExploreChallenge {
  id: string;
  creatorUsername: string;
  gameId: string;
  gameTitle: string;
  gameCoverUrl?: string;
  consoleName?: string;
  name: string;
  description?: string;
  type: ChallengeType;
  difficulty: ChallengeDifficulty;
  attemptCount: number;
  completionCount: number;
  expiresAt?: string | null;
  createdAt: string;
}

export interface ActiveChallengesResponse {
  challenges: ExploreChallenge[];
}

// --- Phase 14: Wild Features — Wizard, Badges, Completionist Map ---

export interface WizardOption {
  id: string;
  label: string;
  description?: string;
  imageUrl?: string;
}

export interface WizardStep {
  step: number;
  title: string;
  type: string;
  options: WizardOption[];
}

export interface WizardResponse {
  steps: WizardStep[];
}

export interface WizardResultsResponse {
  games: Game[];
  title: string;
}

export interface ExplorerBadge {
  id: string;
  name: string;
  description: string;
  icon: string;
  earned: boolean;
  progress: number;
  target: number;
}

export interface ExplorerBadgesResponse {
  badges: ExplorerBadge[];
}

export interface CompletionistConsole {
  id: string;
  name: string;
  totalGames: number;
  playedGames: number;
  percentage: number;
}

export interface CompletionistMapResponse {
  consoles: CompletionistConsole[];
  totalGames: number;
  totalPlayed: number;
  overallPct: number;
}
