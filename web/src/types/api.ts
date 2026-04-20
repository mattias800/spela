// --- Generated type re-exports (source of truth: server/openapi.json) ---
//
// These aliases point to the schemas in `web/src/generated/api.d.ts` — which
// is produced by openapi-typescript from the huma OpenAPI spec. The server's
// typed Go handlers are the source of truth. Do not edit these aliases
// manually; to refresh them:
//
//   1. cd server && ./dump-openapi ../web/src/generated/openapi.json
//   2. cd web && npx openapi-typescript src/generated/openapi.json -o src/generated/api.d.ts
//
// Callers should prefer the generated names (e.g. `GameResponse`,
// `UserResponse`) over the hand-written types below. The hand-written types
// are kept for historical/compat reasons and will be pruned as call sites
// migrate — a name only exists below because the generated schema has a
// different name OR the type is frontend-only (e.g. discriminated unions the
// frontend composes locally, filter objects used only with query strings).
import type { components } from "@/generated/api";

type Schemas = components["schemas"];

export type ConsoleResponse = Schemas["ConsoleResponse"];
export type Core = Schemas["Core"];
export type CoverOption = Schemas["CoverOption"];
export type GameCoversResponse = Schemas["GameCoversResponse"];
export type GameHeroesResponse = Schemas["GameHeroesResponse"];
export type GameResponse = Schemas["GameResponse"];
export type HeroOption = Schemas["HeroOption"];
export type KeywordResponse = Schemas["KeywordResponse"];
export type ScrapeStatusCountsResponse = Schemas["ScrapeStatusCountsResponse"];
export type ThemeResponse = Schemas["ThemeResponse"];
export type UserResponse = Schemas["UserResponse"];

// --- End of generated type re-exports ---

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

export type RateLimitStatus = Schemas["RateLimitResponse"];

// System event audit log (admin-only). Mirrors the SystemEvent model on
// the server. The metadata blob is per-event-type and may include things like
// failedCount, lockedUntil, consecutiveFailures, etc.
export type SecurityEventType =
  | "login_success"
  | "login_failed"
  | "login_locked"
  | "login_blocked"
  | "account_locked"
  | "revoked_token_used"
  | "disabled_account_token"
  | "token_user_missing"
  | "stale_token_version";

export type OperationalEventType =
  | "ra_circuit_breaker_tripped"
  | "scraper_repeated_errors"
  | "rom_file_missing"
  | "api_credentials_invalid"
  | "emulatorjs_load_failed";

export type SystemEventType = SecurityEventType | OperationalEventType;

export type SystemEventTypeLike = SystemEventType | (string & {});

export type SystemEventCategoryCode = "security" | "operational";

export interface SystemEvent {
  id: number;
  createdAt: string;
  categoryCode: SystemEventCategoryCode;
  categoryName: string;
  eventType: SystemEventTypeLike;
  reason?: string;
  username?: string;
  userId?: number;
  ip?: string;
  path?: string;
  metadata?: Record<string, unknown>;
  metadataRaw?: string;
  dismissedAt?: string | null;
}

export interface SystemEventsListResponse {
  data: SystemEvent[];
  total: number;
  page: number;
  pageSize: number;
}

export interface SystemEventsListFilters {
  page?: number;
  pageSize?: number;
  eventType?: SystemEventType[];
  category?: SystemEventCategoryCode;
  username?: string;
  ip?: string;
  since?: string;
  dismissed?: boolean;
}

export interface SystemEventTypeInfo {
  type: string;
  category: SystemEventCategoryCode;
}

export type DeletedUser = Schemas["DeletedUserResponse"];

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  user: User;
}

export type HardwareMaker = Schemas["HardwareMakerResponse"];

export type MediaTypeCategory = Schemas["MediaTypeCategoryResponse"];

export type MediaType = Schemas["MediaTypeResponse"];

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
  generation: number;
  iconUrl: string;
  logoUrl: string;
  gameCount: number;
  saveStateSupport: boolean;
  browserPlayable: boolean;
  playable: boolean;
  code: string;
  maker: HardwareMaker | null;
  mediaType: MediaType | null;
  releaseYear: number | null;
  unitsSold: number | null;
  summary: string | null;
  createdAt: string;
  updatedAt: string;
}

export type GameDisc = Schemas["DiscResponse"];

// GameVariant from backend — represents a variant in the game detail response
export interface GameVariant {
  id: string;
  title: string;
  fileName: string;
  region: string;
  revision: string;
  tags: string;
  isPreRelease: boolean;
  fileSize: number;
  verificationStatus: string;
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
  heroUrl?: string;
  description?: string;
  developer?: string;
  publisher?: string;
  releaseDate?: string;
  genre?: string;
  gameModes?: string;
  storyline?: string;
  totalRating?: number;
  totalRatingCount?: number;
  igdbUserRating?: number;
  igdbUserRatingCount?: number;
  timeToBeatHastily?: number;
  timeToBeatNormally?: number;
  timeToBeatCompletely?: number;
  releaseDates?: ReleaseDateInfo[];
  videos?: VideoInfo[];
  languageSupports?: LanguageSupportInfo[];
  ageRatings?: AgeRatingInfo[];
  players?: number;
  igdbCriticsRating?: number;
  coreOverride?: string;
  scraperId?: string;
  scrapeAttempts: number;
  achievementsWarning?: string;
  verificationStatus?: "verified" | "unverified" | "not_applicable";
  verificationTag?: string;
  region?: string;
  revision?: string;
  tags?: string;
  partyInfo?: string;
  isPreRelease?: boolean;
  variantCount?: number;
  groupKey?: string;
  variants?: GameVariant[];
  parentGame?: { id: string; title: string; coverUrl: string };
  romHacks?: { id: string; title: string; coverUrl: string }[];
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

export type ReleaseDateInfo = Schemas["ReleaseDateResponse"];

export type VideoInfo = Schemas["VideoResponse"];

export type LanguageSupportInfo = Schemas["LanguageSupportResponse"];

export type AgeRatingInfo = Schemas["AgeRatingResponse"];

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
  regions?: string[];
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
  // Variant grouping / pre-release
  grouped?: boolean;
  hidePreRelease?: boolean;
  // Alphabet quick-jump
  letter?: string;
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

export type IgdbSearchResult = Schemas["IGDBSearchResult"];

export type Device = Schemas["DeviceResponse"];

export type ConsoleKeyMapping = Schemas["ConsoleKeyMappingDTO"];

export interface UserPreferences {
  showPerformanceOverlay: boolean;
  autoSaveEnabled: boolean;
  autoLoadSaveEnabled: boolean;
  selectedShader: string;
  consoleShaders: Record<string, string>;
  selectedKeyMapping: string;
  customKeyMapping: Record<string, string>;
  consoleKeyMappings: Record<string, ConsoleKeyMapping>;
  preferredRegions: string[];
  raLinked: boolean;
  raUsername: string;
  raHardcoreEnabled: boolean;
  selectedTheme: string;
}

export type RAStatus = Schemas["RAStatusResponse"];

export interface RALinkRequest {
  username: string;
  password: string;
}

export interface RASettingsRequest {
  hardcoreEnabled: boolean;
}

export type Achievement = Schemas["Achievement"];

export interface GameAchievements {
  status?: "pending";
  raGameId: number;
  totalCount: number;
  totalPoints: number;
  achievements: Achievement[];
}

export type GameAchievementProgress = Schemas["RAProgressEntry"];

export interface GameAchievementProgressResponse {
  raGameId: number;
  progress: GameAchievementProgress[];
}

export type ShowcaseAchievement = Schemas["ShowcaseEntryResponse"];

export type UnlockedAchievement = Schemas["RAUnlockedAchievementResponse"];

export type GameStatsPlayer = Schemas["GameStatsTopPlayer"];

export interface MostPlayedGame {
  game: Game;
  totalPlayers: number;
  totalPlayTime: number;
}

export interface ActivePlayer {
  userId: string;
  username: string;
  avatarUrl?: string;
  totalPlayTime: number;
  gamesPlayed: number;
  lastPlayed: string;
}

export type AchievementLeaderboard = Schemas["AchievementLeaderboardResponse"];

export type AchievementTimelineEntry = Schemas["RATimelineEntryResponse"];

export type AchievementTimeline = Schemas["AchievementTimelineResponse"];

export type RecentAchievement = Schemas["RARecentAchievementResponse"];

export type UserSearchResult = Schemas["UserSearchResult"];

export type UserSearchResponse = Schemas["PaginatedResponseUserSearchResult"];

export type OnlineUser = Schemas["OnlineUserResponse"];

export type ActivityEvent = Schemas["ActivityEventResponse"];

export type ActivityFeedResponse = Schemas["PaginatedResponseActivityEventResponse"];

export type GameRating = Schemas["GameRatingResponse"];

export type SharedSave = Schemas["SharedSaveResponse"];

export type Collection = Schemas["CollectionResponse"];

export type PublicProfileGame = Schemas["PublicProfileGame"];

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
  consoleId: string;
  coverAspectRatio: number;
  status: "waiting" | "in_progress" | "ended";
  endReason: "host_left" | "client_left" | "timeout" | "completed" | "admin_deleted" | null;
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

export type BiosFile = Schemas["BiosFileResponse"];

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

// --- Top Lists ---

export type TopListGame = Schemas["TopListGameResponse"];

export type TopRatedGame = Schemas["TopRatedGameResponse"];

export type LongestGame = Schemas["LongestGameResponse"];

export type DailyPlayActivity = Schemas["HeatmapEntry"];

// --- Session Saves & Cheats ---

export type SessionSave = Schemas["SessionSaveResponse"];

export type CoreCompatibilityEntry = Schemas["CoreCompatibilityEntry"];

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

export type ChallengeLeaderboardEntry = Schemas["ChallengeLeaderboardEntry"];

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

// --- Replace ROM ---

export type ReplaceROMResult = Schemas["ReplaceROMResult"];

export interface ReplaceROMResponse {
  game: Game;
  replacementResult: ReplaceROMResult;
}

// --- Staged Uploads ---

export type StagedUploadStatus =
  | "pending_console"
  | "pending_scrape"
  | "ready"
  | "duplicate"
  | "accepted"
  | "rejected";

export type PossibleConsole = Schemas["PossibleConsoleResponse"];

// --- Themes & Keywords ---

// Theme / Keyword are exact structural matches of the generated
// ThemeResponse / KeywordResponse; aliasing them unblocks typedApi migration
// of useThemes / useKeywords (no consumer changes needed).
export type Theme = Schemas["ThemeResponse"];
export type Keyword = Schemas["KeywordResponse"];

// --- Series & Franchises ---

export interface FeaturedSeries {
  id: string;
  name: string;
  libraryGames: number;
  totalGames: number;
  consoleCount: number;
  heroUrl: string;
}

export type SeriesConsole = Schemas["SeriesConsoleInfo"];

export interface SeriesGame {
  igdbGameId: number;
  name: string;
  inLibrary: boolean;
  localGameId: string | null;
  coverUrl: string | null;
  releaseDate: string | null;
  igdbCriticsRating: number;
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

export type ScreenshotItem = Schemas["ScreenshotItem"];

export interface ScreenshotGalleryResponse {
  screenshots: ScreenshotItem[];
  page: number;
  totalPages: number;
  totalCount: number;
}

export type ArtworkItem = Schemas["ArtworkItem"];

export interface ArtworkGalleryResponse {
  artworks: ArtworkItem[];
  page: number;
  totalPages: number;
  totalCount: number;
}

export type CoverItem = Schemas["CoverItem"];

export interface CoverGalleryResponse {
  covers: CoverItem[];
  page: number;
  totalPages: number;
  totalCount: number;
}

// --- Console Showcase ---

export type GenreCount = Schemas["GenreCount"];

export interface ConsoleShowcase {
  console: Console;
  essentials: Game[];
  hiddenGems: Game[];
  recentlyAdded: Game[];
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

export type FeaturedGame = Schemas["FeaturedGameResponse"];

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

export type DeveloperSummary = Schemas["DeveloperSummary"];
export type DeveloperListResponse = Schemas["DeveloperListResponse"];
export type PlatformCount = Schemas["PlatformCount"];
export type NameCount = Schemas["NameCount"];

export interface EntityUserStats {
  totalPlayTime: number;
  gamesPlayed: number;
  favoriteCount: number;
  mostPlayedGame: Game | null;
}

export type CompanyInfo = Schemas["CompanyInfo"];

export type ActiveYears = Schemas["ActiveYears"];

export type RatingDistribution = Schemas["RatingDistribution"];

export type TimelineGame = Schemas["TimelineGame"];
export type TimelineEntry = Schemas["TimelineEntry"];
export type RelatedDeveloper = Schemas["RelatedDeveloper"];
export type RelatedPublisher = Schemas["RelatedPublisher"];

export interface DeveloperDetailResponse {
  name: string;
  gameCount: number;
  avgRating: number;
  consoles: string[];
  games: Game[];
  heroUrl?: string;
  topGames?: Game[];
  genreBreakdown?: GenreCount[];
  platformBreakdown?: PlatformCount[];
  userStats?: EntityUserStats;
  publishers?: NameCount[];
  companyInfo?: CompanyInfo;
  activeYears?: ActiveYears;
  ratingDistribution?: RatingDistribution;
  primaryGenre?: string;
  timeline?: TimelineEntry[];
  relatedDevelopers?: RelatedDeveloper[];
}

export interface PublisherDetailResponse {
  name: string;
  gameCount: number;
  avgRating: number;
  consoles: string[];
  games: Game[];
  heroUrl?: string;
  topGames?: Game[];
  genreBreakdown?: GenreCount[];
  platformBreakdown?: PlatformCount[];
  userStats?: EntityUserStats;
  developers?: NameCount[];
  companyInfo?: CompanyInfo;
  activeYears?: ActiveYears;
  ratingDistribution?: RatingDistribution;
  primaryGenre?: string;
  timeline?: TimelineEntry[];
  relatedPublishers?: RelatedPublisher[];
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
  igdbCriticsRating: number;
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
  igdbCriticsRating: number;
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

export type WizardOption = Schemas["WizardOption"];

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

export type ExplorerBadge = Schemas["ExplorerBadge"];

export interface ExplorerBadgesResponse {
  badges: ExplorerBadge[];
}

export type CompletionistConsole = Schemas["CompletionistConsole"];

export interface CompletionistMapResponse {
  consoles: CompletionistConsole[];
  totalGames: number;
  totalPlayed: number;
  overallPct: number;
}

export type StorageConsoleBreakdown = Schemas["SessionStorageConsoleBreakdown"];

