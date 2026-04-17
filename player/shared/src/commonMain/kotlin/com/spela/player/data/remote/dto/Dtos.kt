package com.spela.player.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto,
)

@Serializable
data class RefreshRequest(
    val refreshToken: String,
)

// UserDto replaced by com.spela.client.models.UserResponse (see
// player/shared-api/). Aliased here so AuthResponse.user — which lives
// in the hand-written auth DTO block — keeps compiling unchanged. The
// generated UserResponse adds @Required createdAt/updatedAt Instants
// and @Required disabled/pendingApproval booleans the domain model
// ignores.
typealias UserDto = com.spela.client.models.UserResponse

@Serializable
data class HardwareMakerDto(
    val code: String,
    val name: String,
)

@Serializable
data class MediaTypeCategoryDto(
    val code: String,
    val name: String,
)

@Serializable
data class MediaTypeDto(
    val code: String,
    val name: String,
    val category: MediaTypeCategoryDto,
)

/** Matches ConsoleResponse in responses.go */
@Serializable
data class ConsoleDto(
    val id: String,
    val name: String,
    val abbreviation: String,
    val code: String = "",
    val extensions: List<String> = emptyList(),
    val defaultCore: String = "",
    val coverAspectRatio: Double = 0.75,
    val colorTheme: String = "#6366f1",
    val iconUrl: String = "",
    val logoUrl: String = "",
    val logoPngUrl: String = "",
    val gameCount: Int = 0,
    val saveStateSupport: Boolean = true,
    val browserPlayable: Boolean = false,
    val playable: Boolean = true,
    val generation: Int = 0,
    val maker: HardwareMakerDto? = null,
    val mediaType: MediaTypeDto? = null,
    val releaseYear: Int? = null,
    val unitsSold: Long? = null,
    val summary: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

// GameDto / GameDiscDto / GameVariantDto / ParentGameDto / RomHackGameDto
// replaced by generated counterparts in com.spela.client.models — see
// player/shared-api/. Aliased here so the 39 places that nest these types
// (other DTOs, paginated wrappers, etc.) keep compiling unchanged.
//
// Consumers that read raw fields on the DTO will see the generated types'
// primary shape (Long / Instant / non-null strings where the server sends
// empty). Domain translation in DtoMappers.kt handles the conversions
// (Long->Int, Instant->String, empty-string -> null). New code should
// prefer the generated names directly.
typealias GameDto = com.spela.client.models.GameResponse
typealias GameDiscDto = com.spela.client.models.DiscResponse
typealias GameVariantDto = com.spela.client.models.VariantResponse
typealias ParentGameDto = com.spela.client.models.ParentGameResponse
typealias RomHackGameDto = com.spela.client.models.RomHackGameResponse
typealias GameListResponse = com.spela.client.models.PaginatedResponseGameResponse

@Serializable
data class SaveStateDto(
    val id: Long,
    val userId: Long = 0,
    val gameId: Long = 0,
    val name: String,
    val fileSize: Long = 0,
    val isAuto: Boolean = false,
    val coreName: String? = null,
    val notes: String? = null,
    val screenshotUrl: String? = null,
    val slot: Int? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

// LibretroCoreDto replaced by com.spela.client.models.Core (see
// player/shared-api/). Generated Core has @Required createdAt/updatedAt
// Instants that the {coreName: "..."} fallback in getRecommendedCore
// can't satisfy — that method returns the domain type directly.

// ConsoleKeyMappingDto replaced by com.spela.client.models.ConsoleKeyMappingDTO
// (generator uses "DTO" uppercase). The generated type has
// customMapping: Map<String,String>? — the mapper applies .orEmpty() when
// converting to the domain ConsoleKeyMappingPref.

// GameKeyMappingDto / UpdateGameKeyMappingRequest replaced by
// com.spela.client.models.GameKeyMappingResponse and
// com.spela.client.models.UpdateGameKeyMappingRequest
// (see player/shared-api/).

// UserPreferencesDto / UpdatePreferencesRequest replaced by
// com.spela.client.models.UserPreferencesResponse and
// com.spela.client.models.UpdatePreferencesRequest (see player/shared-api/).
// Repository builds the update payload with named args since every field on
// the generated request is nullable.

// Devices — DeviceDto / RegisterDeviceRequest / UpdateDevicePreferencesRequest
// replaced by com.spela.client.models.DeviceResponse /
// RegisterDeviceRequest / UpdateDevicePreferencesRequest (see
// player/shared-api/). DeviceDto is typealiased because the settings UI
// references it directly; the generated DeviceResponse has `lastSeenAt`
// typed as Instant rather than the hand-written String, so consumers
// must format via `.toString()`.
typealias DeviceDto = com.spela.client.models.DeviceResponse

// Shared Saves

// SharedSaveStateDto / SharedSavesResponse replaced by
// com.spela.client.models.SharedSaveResponse /
// PaginatedResponseSharedSaveResponse (see player/shared-api/).

// Ratings

// RateGameRequest / GameRatingDto / GameRatingsResponse / RatingSummaryDto
// replaced by CreateOrUpdateRatingRequest / GameRatingResponse /
// PaginatedResponseGameRatingResponse / RatingSummaryResponse in
// com.spela.client.models (see player/shared-api/).

// Social

// Social DTOs replaced by generated counterparts:
//   OnlineUserGameDto     -> OnlineUserGameResponse
//   OnlineUserDto         -> OnlineUserResponse
//   OnlineUsersResponse   -> OnlineUsersResponse (generated; same name)
//   ActivityEventDto      -> ActivityEventResponse
//   ActivityFeedResponse  -> PaginatedResponseActivityEventResponse
// Noteworthy: hand-written ActivityEventDto used `userAvatarUrl` and
// `gameConsoleName` where the server sends `avatarUrl` and
// `consoleName`. The hand-written names were dead; new mapper reads
// the real fields.

/** Wrapper for GET /api/games/:id/core when core is not in DB */
@Serializable
data class CoreNameResponse(
    val coreName: String,
)

// RetroAchievements

// RA DTOs replaced by generated counterparts in
// com.spela.client.models — see player/shared-api/:
//   RAStatusDto           -> RAStatusResponse
//   RALinkRequestDto      -> LinkRAAccountRequest
//   RATokenResponseDto    -> RATokenResponse
//   RASettingsRequestDto  -> UpdateRASettingsRequest

// Public Profile

// Public profile + heatmap DTOs replaced:
//   PublicProfileGameDto  -> PublicProfileGame
//   PublicProfileDto      -> PublicProfileResponse
//   HeatmapEntryDto       -> HeatmapEntry
// (all in com.spela.client.models)

// Shared Session

@Serializable
data class SharedSessionDto(
    val id: String,
    val name: String,
    val description: String = "",
    val gameId: String,
    val gameTitle: String = "",
    val gameCoverUrl: String? = null,
    val gameConsoleName: String = "",
    val ownerId: String,
    val ownerUsername: String = "",
    val status: String = "active",
    val memberCount: Int = 0,
    val activeUserId: String? = null,
    val lastActivityAt: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Serializable
data class SharedSessionDetailDto(
    val id: String,
    val name: String,
    val description: String = "",
    val gameId: String,
    val gameTitle: String = "",
    val gameCoverUrl: String? = null,
    val gameConsoleName: String = "",
    val ownerId: String,
    val ownerUsername: String = "",
    val status: String = "active",
    val memberCount: Int = 0,
    val activeUserId: String? = null,
    val lastActivityAt: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val members: List<SharedSessionMemberDto> = emptyList(),
)

@Serializable
data class SharedSessionMemberDto(
    val userId: String,
    val username: String,
    val avatarUrl: String? = null,
    val role: String = "member",
    val joinedAt: String = "",
    val lastPlayedAt: String? = null,
    val isOnline: Boolean = false,
)

@Serializable
data class SharedSessionInvitationDto(
    val id: String,
    val sharedSessionId: String,
    val sharedSessionName: String = "",
    val gameId: String = "",
    val gameTitle: String = "",
    val gameCoverUrl: String? = null,
    val gameConsoleName: String = "",
    val inviterUsername: String = "",
    val inviterAvatarUrl: String? = null,
    val createdAt: String = "",
)

@Serializable
data class SharedSessionSaveDto(
    val id: Long,
    val sharedSessionId: String = "",
    val gameId: Long = 0,
    val userId: Long = 0,
    val username: String = "",
    val avatarUrl: String? = null,
    val name: String,
    val fileSize: Long = 0,
    val isAuto: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Serializable
data class SharedSessionsResponse(
    val data: List<SharedSessionDto> = emptyList(),
    val total: Long = 0,
    val page: Int = 1,
    val pageSize: Int = 20,
)

@Serializable
data class SharedSessionInvitationsResponse(
    val data: List<SharedSessionInvitationDto> = emptyList(),
    val total: Long = 0,
)

@Serializable
data class CreateSharedSessionRequest(
    val name: String,
    val gameId: String,
    val description: String = "",
)

@Serializable
data class InviteToSharedSessionRequest(
    val username: String,
)

@Serializable
data class TakeTurnResponse(
    val turnToken: String,
)

@Serializable
data class SharedSessionInvitationCountResponse(
    val count: Int = 0,
)

// BIOS — BiosFileDto / BiosConsoleDto / BiosConsoleFileDto /
// BiosStatusResponse replaced by:
//   com.spela.client.models.BiosFileResponse
//   com.spela.client.models.ConsoleBiosStatus
//   com.spela.client.models.ConsoleFileStatus
//   com.spela.client.models.BiosListResponse
// (see player/shared-api/).

// Collections

// Collection DTOs replaced by generated counterparts:
//   CollectionDto                  -> CollectionResponse
//   CollectionDetailDto            -> CollectionDetailResponse
//   CollectionsResponse            -> PaginatedResponseCollectionResponse
//   CreateCollectionRequest        -> CreateCollectionRequest (same name, generated)
//   UpdateCollectionRequest        -> UpdateCollectionRequest (same name, generated)
//   AddGameToCollectionRequest     -> AddGameToCollectionRequest (same name, generated; gameId is Long? in spec)
// (all in com.spela.client.models)

// Stats

// MostPlayedGameDto / MostPlayedResponse replaced by
// com.spela.client.models.MostPlayedEntry and MostPlayedResponse
// (generated; same name).

// ActivePlayerDto / MostActivePlayersResponse replaced by
// com.spela.client.models.ActivePlayerEntry and MostActivePlayersResponse
// (generated; same name).

// Challenges

// Challenge DTOs replaced by generated counterparts in
// com.spela.client.models — see player/shared-api/:
//   ChallengeDto                  -> ChallengeResponse
//   ChallengesResponse            -> PaginatedResponseChallengeResponse
//   ChallengeAttemptDto           -> ChallengeAttemptResponse
//   ChallengeLeaderboardEntryDto  -> ChallengeLeaderboardEntry
//   ChallengeLeaderboardResponse  -> PaginatedResponseChallengeLeaderboardEntry
//
// Noteworthy: the hand-written ChallengeDto used `creatorAvatarUrl`
// and `gameConsoleName` where the server actually sends `creatorAvatar`
// and `consoleName`. The hand-written names were effectively dead —
// kotlinx-serialization fell back to defaults. The new mapper maps
// the real fields.

// Netplay

// Netplay DTOs replaced by generated counterparts in
// com.spela.client.models — see player/shared-api/:
//   NetplaySessionDto             -> NetplaySessionResponse
//   NetplaySessionsResponse       -> PaginatedResponseNetplaySessionResponse
//   CreateNetplaySessionRequest   -> CreateNetplaySessionRequest (generated; same name)
//   JoinByInviteCodeRequest       -> JoinByInviteCodeRequest (generated; same name)
//   UpdateNetplaySettingsRequest  -> UpdateNetplaySettingsRequest (generated; same name)
//   SendNetplayInviteRequest      -> NetplayInviteUserRequest

// UserSearchResultDto / UserSearchResponse replaced by
// com.spela.client.models.UserSearchResult /
// PaginatedResponseUserSearchResult (see player/shared-api/).

// Game Stats

// TopPlayerDto / GameStatsDto replaced by
// com.spela.client.models.GameStatsTopPlayer and GameStatsResponse.

// Game Achievements (RetroAchievements)

@Serializable
data class GameAchievementDto(
    val id: Long = 0,
    val title: String = "",
    val description: String = "",
    val points: Int = 0,
    val badgeUrl: String? = null,
    val type: String? = null,
    val displayOrder: Int? = null,
    val rarityPercent: Double = 0.0,
)

@Serializable
data class GameAchievementsResponse(
    val raGameId: Long? = null,
    val title: String = "",
    val achievements: List<GameAchievementDto> = emptyList(),
    val totalCount: Int = 0,
    val totalPoints: Int = 0,
)

// Achievement Progress

@Serializable
data class AchievementProgressEntryDto(
    val achievementId: Long = 0,
    val unlockedAt: String? = null,
    val isHardcore: Boolean = false,
    val playTimeAtUnlock: Long? = null,
)

@Serializable
data class AchievementProgressResponse(
    val raGameId: Long? = null,
    val progress: List<AchievementProgressEntryDto> = emptyList(),
)

// Achievement Timeline + Leaderboard DTOs replaced by generated
// counterparts in com.spela.client.models:
//   AchievementTimelineEntryDto      -> RATimelineEntryResponse
//   AchievementTimelineResponse      -> AchievementTimelineResponse (same name)
//   AchievementLeaderboardEntryDto   -> RALeaderboardEntryResponse
//   AchievementLeaderboardResponse   -> AchievementLeaderboardResponse (same name)

// User Stats

// UserStatsDto replaced by com.spela.client.models.UserStatsResponse.

// Recent + Showcase + Unlocked achievement DTOs replaced by generated
// counterparts in com.spela.client.models:
//   RecentAchievementDto           -> RARecentAchievementResponse
//   RecentAchievementsResponse     -> RecentAchievementsResponse (same name)
//   ShowcaseAchievementDto         -> ShowcaseEntryResponse
//   UnlockedAchievementDto         -> RAUnlockedAchievementResponse
//   UnlockedAchievementsResponse   -> UnlockedAchievementsResponse (same name)

// ShowcaseUpdateEntry replaced by com.spela.client.models.ShowcaseEntryInput.

// Top Rated

// TopRatedGameDto / TopListGameDto / LongestGameDto replaced by
// com.spela.client.models.TopRatedGameResponse / TopListGameResponse /
// LongestGameResponse (see player/shared-api/).
//
// Noteworthy: the hand-written TopRatedGameDto and TopListGameDto used
// `rating` but the server actually sends `igdbCriticsRating` — the
// hand-written field was always 0.0. The new mapper pulls the real
// rating.

// Similar Games

@Serializable
data class SimilarGameDto(
    val igdbGameId: Int = 0,
    val name: String,
    val coverUrl: String? = null,
    val rating: Double = 0.0,
    val localGameId: String? = null,
)

// Developer Games — DeveloperGameDto replaced by
// com.spela.client.models.DeveloperGameResponse (see player/shared-api/).
// Noteworthy: hand-written DTO used `id`/`title`/`consoleName` but the
// server actually emits `localGameId`/`name` (and never emits consoleName
// on this endpoint). The mapper renames accordingly.

// Save Data (SRAM)

// Game Sessions — GameSessionDto / CreateSessionRequest / UpdateSessionRequest
// replaced by com.spela.client.models.GameSessionResponse /
// CreateSessionRequest / UpdateSessionRequest (see player/shared-api/).
// The generated response models memberCount as Long (mapper converts to
// Int), lastPlayedAt as Instant? (mapper converts to String via
// .toString()), and memberUsernames / memberAvatars as List<String>?
// (mapper applies .orEmpty()). DuplicateSessionRequest is used in-place
// for the duplicate-session call.

// Session Cheats — SessionCheatConfigDto / UpdateSessionCheatsRequest
// replaced by com.spela.client.models.SessionCheatsResponse /
// UpdateSessionCheatsRequest. The server spec models enabledIndices as
// List<Long>? — the API client converts Int⇄Long at the boundary and
// the mapper converts to List<Int> for the domain.

// Cheats — CheatDto replaced by com.spela.client.models.GameCheatResponse
// (see player/shared-api/). Server sends id/index as Long; the repository
// stores them directly without domain conversion.

// Explore

@Serializable
data class FeaturedGameDto(
    val gameId: String,
    val title: String,
    val heroUrl: String? = null,
    val logoUrl: String? = null,
    val consoleName: String = "",
    val consoleAbbreviation: String = "",
    val consoleColor: String = "",
    val rating: Double = 0.0,
    val genre: String = "",
    val isFavorite: Boolean = false,
    val isPlayLater: Boolean = false,
)

@Serializable
data class ExploreRowDto(
    val id: String,
    val title: String,
    val games: List<GameDto>,
)

@Serializable
data class ExploreRowsResponseDto(
    val rows: List<ExploreRowDto>,
)

// Themes & Keywords — ThemeDto / KeywordDto replaced by
// com.spela.client.models.ThemeResponse / KeywordResponse
// (see player/shared-api/).

// Series & Franchise

@Serializable
data class FeaturedSeriesDto(
    val id: String,
    val name: String,
    val libraryGames: Int = 0,
    val totalGames: Int = 0,
    val consoleCount: Int = 0,
    val heroUrl: String? = null,
)

// SeriesDetailDto / SeriesConsoleDto / SeriesGameDto / GameSeriesLinkDto /
// GameFranchiseLinkDto replaced by generated counterparts:
//   SeriesDetailDto       -> SeriesDetailResponse (also FranchiseDetailResponse
//                            for /api/franchises/:id — same shape aside from
//                            igdbCollectionId vs igdbFranchiseId)
//   SeriesConsoleDto      -> SeriesConsoleInfo
//   SeriesGameDto         -> SeriesGameResponse
//   GameSeriesLinkDto     -> GameSeriesResponse
//   GameFranchiseLinkDto  -> GameFranchiseResponse (server sends
//                            totalGames + libraryGames; hand-written DTO
//                            only had gameCount — mapper picks totalGames)
//
// Noteworthy: hand-written SeriesGameDto used `rating` but the server
// sends `igdbCriticsRating` — the hand-written field was always 0.0.
// The new mapper pulls the real rating.

@Serializable
data class MoodDefinitionDto(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val gradient: List<String>,
)

// For You (Personalized Recommendations)

@Serializable
data class ForYouRowDto(
    val type: String,
    val title: String,
    val sourceGame: GameDto? = null,
    val genre: String? = null,
    val games: List<GameDto>,
)

@Serializable
data class ForYouResponseDto(
    val rows: List<ForYouRowDto>,
)

// TasteProfileDto / TasteBreakdownDto / ConsoleBreakdownDto replaced by
// com.spela.client.models.TasteProfileResponse / TasteProfileGenre /
// TasteProfileTheme / TasteProfileConsole (see player/shared-api/).
// The generated types use Long for gameCount — the mapper converts to Int
// for the domain. Generated lists are nullable (Required but nullable in
// spec), so the mapper applies .orEmpty() when flattening.

@Serializable
data class PlayersLikeYouResponseDto(
    val games: List<GameDto>,
    val similarUsersCount: Int,
)

// Developer / Publisher

@Serializable
data class CompanyInfoDto(
    val logoUrl: String? = null,
    val description: String? = null,
    val foundedYear: Int? = null,
    val country: String? = null,
    val websiteUrl: String? = null,
    val wikipediaUrl: String? = null,
)

@Serializable
data class DeveloperSummaryDto(
    val name: String,
    val gameCount: Int,
    val avgRating: Double,
    val consoles: List<String>,
)

@Serializable
data class DeveloperListResponseDto(
    val developers: List<DeveloperSummaryDto>,
)

@Serializable
data class DeveloperDetailPlatformBreakdownDto(
    val consoleName: String = "",
    val consoleId: String = "",
    val count: Int = 0,
)

@Serializable
data class DeveloperDetailUserStatsDto(
    val totalPlayTime: Long = 0,
    val gamesPlayed: Int = 0,
    val favoriteCount: Int = 0,
    val mostPlayedGame: GameDto? = null,
)

@Serializable
data class DeveloperDetailPublisherDto(
    val name: String = "",
    val count: Int = 0,
)

@Serializable
data class ActiveYearsDto(
    val first: Int = 0,
    val last: Int = 0,
)

@Serializable
data class RatingDistributionDto(
    val excellent: Int = 0,
    val good: Int = 0,
    val average: Int = 0,
    val poor: Int = 0,
    val unrated: Int = 0,
)

@Serializable
data class TimelineGameDto(
    val id: String = "",
    val title: String = "",
    val coverUrl: String? = null,
    val rating: Double = 0.0,
)

@Serializable
data class TimelineEntryDto(
    val year: Int = 0,
    val games: List<TimelineGameDto> = emptyList(),
)

@Serializable
data class RelatedDeveloperDto(
    val name: String = "",
    val gameCount: Int = 0,
    val sharedPublishers: List<String> = emptyList(),
)

@Serializable
data class DeveloperDetailResponseDto(
    val name: String,
    val gameCount: Int,
    val avgRating: Double,
    val consoles: List<String>,
    val heroUrl: String? = null,
    val companyInfo: CompanyInfoDto? = null,
    val topGames: List<GameDto> = emptyList(),
    val genreBreakdown: List<GenreCountDto> = emptyList(),
    val platformBreakdown: List<DeveloperDetailPlatformBreakdownDto> = emptyList(),
    val userStats: DeveloperDetailUserStatsDto? = null,
    val publishers: List<DeveloperDetailPublisherDto> = emptyList(),
    val games: List<GameDto>,
    val activeYears: ActiveYearsDto? = null,
    val ratingDistribution: RatingDistributionDto? = null,
    val primaryGenre: String? = null,
    val timeline: List<TimelineEntryDto> = emptyList(),
    val relatedDevelopers: List<RelatedDeveloperDto> = emptyList(),
)

@Serializable
data class DeveloperSpotlightResponseDto(
    val name: String,
    val gameCount: Int,
    val avgRating: Double,
    val consoles: List<String>,
    val topGames: List<GameDto>,
    val heroUrl: String = "",
)

// Console Showcase

@Serializable
data class GenreCountDto(
    val name: String,
    val gameCount: Int,
)

@Serializable
data class ConsoleShowcaseDto(
    val console: ConsoleDto,
    val essentials: List<GameDto>,
    val hiddenGems: List<GameDto>,
    val genreBreakdown: List<GenreCountDto>,
    val topDevelopers: List<DeveloperSummaryDto>,
    val recentlyPlayed: List<GameDto>,
)

@Serializable
data class ConsoleHighlightDto(
    val id: String,
    val name: String,
    val colorTheme: String,
    val iconUrl: String,
    val logoUrl: String,
    val gameCount: Int,
    val topGame: GameDto? = null,
)

@Serializable
data class ConsoleHighlightsResponseDto(
    val consoles: List<ConsoleHighlightDto>,
)

// Artwork Gallery

@Serializable
data class ArtworkItemDto(
    val url: String,
    val width: Int,
    val height: Int,
    val gameId: String,
    val gameTitle: String,
    val consoleName: String,
    val consoleAbbreviation: String,
    val consoleColor: String,
)

@Serializable
data class ArtworkGalleryResponseDto(
    val artworks: List<ArtworkItemDto>,
    val page: Int,
    val totalPages: Int,
    val totalCount: Int,
)

// Screenshot Gallery

@Serializable
data class ScreenshotItemDto(
    val url: String,
    val gameId: String,
    val gameTitle: String,
    val consoleName: String,
    val consoleAbbreviation: String,
    val consoleColor: String,
)

@Serializable
data class ScreenshotGalleryResponseDto(
    val screenshots: List<ScreenshotItemDto>,
    val page: Int,
    val totalPages: Int,
    val totalCount: Int,
)

// --- Phase 10: Social & Community Discovery ---

@Serializable
data class TrendingGameDto(
    val game: GameDto,
    val playersThisWeek: Int,
)

@Serializable
data class TrendingResponseDto(
    val games: List<TrendingGameDto>,
)

@Serializable
data class CommunityTopGameDto(
    val game: GameDto,
    val avgRating: Double,
    val ratingCount: Int,
)

@Serializable
data class CommunityTopResponseDto(
    val games: List<CommunityTopGameDto>,
)

@Serializable
data class CultClassicGameDto(
    val game: GameDto,
    val communityRating: Double,
    val igdbCriticsRating: Double,
    val ratingCount: Int,
)

@Serializable
data class CultClassicsResponseDto(
    val games: List<CultClassicGameDto>,
)

@Serializable
data class RecentReviewItemDto(
    val game: GameDto,
    val rating: Int,
    val review: String,
    val reviewerName: String,
    val reviewedAt: String,
)

@Serializable
data class RecentlyReviewedResponseDto(
    val reviews: List<RecentReviewItemDto>,
)

@Serializable
data class ActiveNowItemDto(
    val game: GameDto,
    val activeSessions: Int,
    val activeChallenges: Int,
)

@Serializable
data class ActiveNowResponseDto(
    val games: List<ActiveNowItemDto>,
)

// --- Phase 11: Temporal Discovery ---

@Serializable
data class OnThisDayResponseDto(
    val date: String,
    val games: List<GameDto>,
)

@Serializable
data class BestOfYearResponseDto(
    val year: Int,
    val games: List<GameDto>,
)

@Serializable
data class AnniversaryItemDto(
    val game: GameDto,
    val yearsAgo: Int,
    val playedAt: String,
)

@Serializable
data class YourAnniversariesResponseDto(
    val anniversaries: List<AnniversaryItemDto>,
)

@Serializable
data class DecadeResponseDto(
    val decade: String,
    val label: String,
    val games: List<GameDto>,
)

// --- Phase 12: Achievement & Challenge-Driven Discovery ---

@Serializable
data class AchievementGameItemDto(
    val game: GameDto,
    val totalAchievements: Int,
    val avgCompletion: Float,
    val playersAttempted: Int,
    val playersCompleted: Int,
)

@Serializable
data class EasyToCompleteResponseDto(
    val games: List<AchievementGameItemDto>,
)

@Serializable
data class HardestGamesResponseDto(
    val games: List<AchievementGameItemDto>,
)

@Serializable
data class AlmostDoneGameDto(
    val game: GameDto,
    val unlockedCount: Int,
    val totalCount: Int,
    val completionPercent: Float,
)

@Serializable
data class AlmostDoneResponseDto(
    val games: List<AlmostDoneGameDto>,
)

@Serializable
data class FreshChallengeGameDto(
    val game: GameDto,
    val totalAchievements: Int,
    val totalPoints: Int,
)

@Serializable
data class FreshChallengesResponseDto(
    val games: List<FreshChallengeGameDto>,
)

@Serializable
data class ExploreChallengeDto(
    val id: String,
    val creatorUsername: String,
    val gameId: String,
    val gameTitle: String,
    val gameCoverUrl: String? = null,
    val consoleName: String? = null,
    val name: String,
    val description: String? = null,
    val type: String,
    val difficulty: String,
    val attemptCount: Int,
    val completionCount: Int,
    val expiresAt: String? = null,
    val createdAt: String,
)

@Serializable
data class ActiveChallengesResponseDto(
    val challenges: List<ExploreChallengeDto>,
)

// --- Phase 13: Advanced Search & Saved Searches ---

// SavedSearchDto / CreateSavedSearchRequest replaced by
// com.spela.client.models.SavedSearchResponse / SavedSearchRequest.

// --- Phase 14: Wild Features — Wizard, Badges, Completionist Map ---

@Serializable
data class WizardOptionDto(
    val id: String,
    val label: String,
    val description: String = "",
    val imageUrl: String = "",
)

@Serializable
data class WizardStepDto(
    val step: Int,
    val title: String,
    val type: String,
    val options: List<WizardOptionDto>,
)

@Serializable
data class WizardResponseDto(
    val steps: List<WizardStepDto>,
)

@Serializable
data class WizardResultsResponseDto(
    val games: List<GameDto>,
    val title: String,
)

// ExplorerBadgeDto and ExplorerBadgesResponseDto replaced by
// com.spela.client.models.ExplorerBadge and
// com.spela.client.models.ExplorerBadgesResponse
// (see player/shared-api/).

// CompletionistConsoleDto and CompletionistMapResponseDto moved to
// com.spela.client.models.CompletionistConsole and
// com.spela.client.models.CompletionistMapResponse — see
// player/shared-api/ (generated from the OpenAPI spec).

// --- Global Search ---

@Serializable
data class GlobalSearchGameResultDto(
    val id: String,
    val title: String,
    val coverUrl: String? = null,
    val consoleName: String = "",
    val consoleId: String = "",
    val developer: String? = null,
    val genre: String? = null,
    val coverAspectRatio: Double = 0.75,
)

@Serializable
data class GlobalSearchConsoleResultDto(
    val id: String,
    val name: String,
    val iconUrl: String = "",
    val gameCount: Int = 0,
    val colorTheme: String = "#6366f1",
)

@Serializable
data class GlobalSearchDeveloperResultDto(
    val name: String,
    val gameCount: Int = 0,
    val avgRating: Double = 0.0,
)

@Serializable
data class GlobalSearchPublisherResultDto(
    val name: String,
    val gameCount: Int = 0,
    val avgRating: Double = 0.0,
)

@Serializable
data class GlobalSearchCollectionResultDto(
    val id: String,
    val name: String,
    val gameCount: Int = 0,
    val coverUrl: String? = null,
    val username: String = "",
)

@Serializable
data class GlobalSearchSeriesResultDto(
    val id: String,
    val name: String,
    val totalGames: Int = 0,
    val libraryGames: Int = 0,
)

@Serializable
data class GlobalSearchFranchiseResultDto(
    val id: String,
    val name: String,
    val totalGames: Int = 0,
    val libraryGames: Int = 0,
)

@Serializable
data class GlobalSearchCategoryDto<T>(
    val results: List<T> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class GlobalSearchResponseDto(
    val games: GlobalSearchCategoryDto<GlobalSearchGameResultDto> = GlobalSearchCategoryDto(),
    val consoles: GlobalSearchCategoryDto<GlobalSearchConsoleResultDto> = GlobalSearchCategoryDto(),
    val developers: GlobalSearchCategoryDto<GlobalSearchDeveloperResultDto> = GlobalSearchCategoryDto(),
    val publishers: GlobalSearchCategoryDto<GlobalSearchPublisherResultDto> = GlobalSearchCategoryDto(),
    val collections: GlobalSearchCategoryDto<GlobalSearchCollectionResultDto> = GlobalSearchCategoryDto(),
    val series: GlobalSearchCategoryDto<GlobalSearchSeriesResultDto> = GlobalSearchCategoryDto(),
    val franchises: GlobalSearchCategoryDto<GlobalSearchFranchiseResultDto> = GlobalSearchCategoryDto(),
)
