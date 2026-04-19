package com.spela.player.data.remote.dto

import kotlinx.serialization.Serializable

// Auth — LoginRequest / RegisterRequest / RefreshRequest / AuthResponse
// replaced by generated counterparts in com.spela.client.models:
//   LoginRequest    -> AuthLoginRequest
//   RegisterRequest -> AuthRegisterRequest
//   RefreshRequest  -> AuthRefreshRequest
//   AuthResponse    -> AuthLoginResponse (login + refresh + setup)
//                      AuthRegisterResponse (register; has `pending` branch
//                      when the account awaits admin approval — tokens are
//                      nullable in that case; repository throws when pending)
//
// UserDto replaced by com.spela.client.models.UserResponse — aliased
// below because presentation-layer code still references the name.

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

// SaveStateDto replaced by com.spela.client.models.SessionSaveResponse
// (see player/shared-api/). The generated type has `id: String`, no
// `gameId` field (derivable from session context), `createdAt: Instant`
// (no String parsing needed), and `slot: Long?` (mapper converts to Int?).

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

// Shared Session — hand-written DTOs replaced by generated counterparts in
// com.spela.client.models. Aliased below so existing call sites keep working.
//
// Silent-bug field renames vs. the old hand-written DTOs (these were never
// actually delivered by the server, so the DTO fields were always their
// Kotlin defaults — empty string / null / false):
//   SharedSessionDto.description             -> not sent (always "")
//   SharedSessionDto.gameConsoleName         -> server sends `consoleName`
//   SharedSessionDto.lastActivityAt          -> not sent; mapper uses updatedAt
//   SharedSessionDetailDto.description       -> not sent
//   SharedSessionDetailDto.gameConsoleName   -> server sends `consoleName`
//   SharedSessionDetailDto.lastActivityAt    -> not sent; mapper uses updatedAt
//   SharedSessionMemberDto.lastPlayedAt      -> not sent
//   SharedSessionMemberDto.isOnline          -> not sent; mapper defaults false
//   SharedSessionInvitationDto.gameId        -> not sent
//   SharedSessionInvitationDto.gameCoverUrl  -> not sent
//   SharedSessionInvitationDto.gameConsoleName -> not sent
//   SharedSessionInvitationDto.inviterAvatarUrl -> not sent
//   SharedSessionSaveDto.gameId / .userId / .avatarUrl -> not sent
//
// CreateSharedSessionRequest.description was also never read by the server.

typealias SharedSessionDto = com.spela.client.models.SharedSessionResponse
typealias SharedSessionDetailDto = com.spela.client.models.SharedSessionDetailResponse
typealias SharedSessionMemberDto = com.spela.client.models.SharedSessionMemberResponse
typealias SharedSessionInvitationDto = com.spela.client.models.SharedSessionInviteResponse
typealias SharedSessionSaveDto = com.spela.client.models.SharedSessionSaveResponse
typealias CreateSharedSessionRequest = com.spela.client.models.CreateSharedSessionRequest
typealias InviteToSharedSessionRequest = com.spela.client.models.InviteToSharedSessionRequest
typealias TakeTurnResponse = com.spela.client.models.SharedSessionTakeTurnResponse
typealias SharedSessionInvitationCountResponse = com.spela.client.models.SharedSessionInviteCountResponse

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

// Game Achievements (RetroAchievements) DTOs replaced by generated
// counterparts in com.spela.client.models:
//   GameAchievementDto              -> Achievement
//   GameAchievementsResponse        -> GameAchievementsResponse (same name)
//   AchievementProgressEntryDto     -> RAProgressEntry
//   AchievementProgressResponse     -> GameAchievementProgressResponse

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

// Similar Games — SimilarGameDto replaced by
// com.spela.client.models.SimilarGameResponse.
//
// Noteworthy: hand-written DTO used `rating` but the server emits
// `igdbCriticsRating` — silent 0.0 in the similar-games card rating.
// The new mapper pulls the real field.

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

// Explore — FeaturedGameDto / ExploreRowDto / ExploreRowsResponseDto
// replaced by com.spela.client.models.FeaturedGameResponse /
// ExploreRowResponse / ExploreRowsResponse.
//
// Noteworthy: hand-written FeaturedGameDto used `rating` but the server
// sends `igdbCriticsRating` — the hand-written field was always 0.0 and
// the Explore hero's rating badge was silently dead. The new mapper
// pulls the real field. heroUrl/logoUrl are @Required non-nullable in
// the generated type (server sends empty strings); the mapper converts
// "" back to null so the existing "no hero art" UX still works.

// Themes & Keywords — ThemeDto / KeywordDto replaced by
// com.spela.client.models.ThemeResponse / KeywordResponse
// (see player/shared-api/).

// Series & Franchise

// FeaturedSeriesDto replaced by com.spela.client.models.FeaturedSeriesResponse.

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

// MoodDefinitionDto replaced by com.spela.client.models.MoodResponse.

// ForYouRowDto / ForYouResponseDto replaced by
// com.spela.client.models.ForYouRowResponse / ForYouResponse.
// Both `rows` and per-row `games` are typed `List<...>?` on the
// generated side even though server-marked @Required; the mapper
// applies .orEmpty().

// TasteProfileDto / TasteBreakdownDto / ConsoleBreakdownDto replaced by
// com.spela.client.models.TasteProfileResponse / TasteProfileGenre /
// TasteProfileTheme / TasteProfileConsole (see player/shared-api/).
// The generated types use Long for gameCount — the mapper converts to Int
// for the domain. Generated lists are nullable (Required but nullable in
// spec), so the mapper applies .orEmpty() when flattening.

// PlayersLikeYouResponseDto replaced by
// com.spela.client.models.PlayersLikeYouResponse (similarUsersCount is
// Long — mapper converts to Int).

// Developer / Publisher
//
// CompanyInfoDto / DeveloperSummaryDto / DeveloperListResponseDto /
// DeveloperDetailPlatformBreakdownDto / DeveloperDetailUserStatsDto /
// DeveloperDetailPublisherDto / ActiveYearsDto / RatingDistributionDto /
// TimelineGameDto / TimelineEntryDto / RelatedDeveloperDto /
// DeveloperDetailResponseDto / DeveloperSpotlightResponseDto replaced by
// generated counterparts in com.spela.client.models:
//   CompanyInfo / DeveloperSummary / DeveloperListResponse /
//   PlatformCount / EntityUserStats / NameCount / ActiveYears /
//   RatingDistribution / TimelineGame / TimelineEntry /
//   RelatedDeveloper / DeveloperDetailResponse / DeveloperSpotlightResponse
//
// Noteworthy: hand-written TimelineGameDto used `rating` but the server
// emits `igdbCriticsRating` — another silent 0.0 field. EntityUserStats
// has mostPlayedGame as @Required non-nullable (server ships an empty
// Game placeholder when the user has no play history on the dev); the
// mapper converts the empty id back to null.

// Console Showcase — GenreCountDto / ConsoleShowcaseDto /
// ConsoleHighlightDto / ConsoleHighlightsResponseDto replaced by
// com.spela.client.models.GenreCount / ConsoleShowcaseResponse /
// ConsoleHighlight / ConsoleHighlightsResponse.
//
// Noteworthy: generated ConsoleShowcaseResponse adds a `recentlyAdded`
// field that the hand-written DTO ignored — the domain model does not
// consume it yet. ConsoleHighlight.topGame is @Required on the generated
// type but the underlying server field is *GameResponse (nullable); if
// the spec is corrected, we'll handle null at runtime in the mapper.

// Artwork / Screenshot Gallery — ArtworkItemDto /
// ArtworkGalleryResponseDto / ScreenshotItemDto /
// ScreenshotGalleryResponseDto replaced by
// com.spela.client.models.ArtworkItem / ArtworkGalleryResponse /
// ScreenshotItem / ScreenshotGalleryResponse. Width/height are Long
// on the wire — mapper converts to Int.

// --- Phase 10: Social & Community Discovery ---
//
// TrendingGameDto / TrendingResponseDto / CommunityTopGameDto /
// CommunityTopResponseDto / CultClassicGameDto / CultClassicsResponseDto /
// RecentReviewItemDto / RecentlyReviewedResponseDto / ActiveNowItemDto /
// ActiveNowResponseDto replaced by generated counterparts in
// com.spela.client.models:
//   TrendingGameResponse / TrendingResponse / CommunityTopGame /
//   CommunityTopResponse / CultClassicGame / CultClassicsResponse /
//   RecentReviewItem / RecentlyReviewedResponse / ActiveNowItem /
//   ActiveNowResponse
//
// Most numeric fields are Long on the wire — mappers convert to Int.
// RecentReviewItem.reviewedAt is kotlinx.datetime.Instant — mapper
// stringifies via .toString().

// --- Phase 11: Temporal Discovery ---
//
// OnThisDayResponseDto / BestOfYearResponseDto / AnniversaryItemDto /
// YourAnniversariesResponseDto / DecadeResponseDto replaced by
// com.spela.client.models.OnThisDayResponse / BestOfYearResponse /
// AnniversaryItem / AnniversariesResponse / DecadesResponse. playedAt is
// a kotlinx.datetime.Instant — mapper stringifies via .toString().

// --- Phase 12: Achievement & Challenge-Driven Discovery ---
//
// AchievementGameItemDto / EasyToCompleteResponseDto /
// HardestGamesResponseDto / AlmostDoneGameDto / AlmostDoneResponseDto /
// FreshChallengeGameDto / FreshChallengesResponseDto / ExploreChallengeDto /
// ActiveChallengesResponseDto replaced by generated counterparts in
// com.spela.client.models:
//   AchievementGameResponse / EasyToCompleteResponse /
//   HardestGamesResponse / AlmostDoneGame / AlmostDoneResponse /
//   FreshChallengeGame / FreshChallengesResponse / ExploreChallengeResponse /
//   ActiveChallengesResponse
//
// Generated numeric fields are Long — mappers convert to Int. Float
// fields (avgCompletion, completionPercent) are kotlin.Double in the
// generated type — the hand-written DTO and domain both used Float, the
// mapper keeps the existing Float precision via .toFloat(). ExploreChallenge
// createdAt / expiresAt are Instants — stringified.

// --- Phase 13: Advanced Search & Saved Searches ---

// SavedSearchDto / CreateSavedSearchRequest replaced by
// com.spela.client.models.SavedSearchResponse / SavedSearchRequest.

// --- Phase 14: Wild Features — Wizard, Badges, Completionist Map ---
//
// WizardOptionDto / WizardStepDto / WizardResponseDto /
// WizardResultsResponseDto replaced by com.spela.client.models.WizardOption
// / WizardStep / WizardResponse / WizardResultsResponse. Step is Long —
// mapper converts to Int. Description / imageUrl are nullable.

// ExplorerBadgeDto and ExplorerBadgesResponseDto replaced by
// com.spela.client.models.ExplorerBadge and
// com.spela.client.models.ExplorerBadgesResponse
// (see player/shared-api/).

// CompletionistConsoleDto and CompletionistMapResponseDto moved to
// com.spela.client.models.CompletionistConsole and
// com.spela.client.models.CompletionistMapResponse — see
// player/shared-api/ (generated from the OpenAPI spec).

// Global search DTOs replaced by generated counterparts in
// com.spela.client.models — GlobalSearchResponseDto -> SearchResponse,
// GlobalSearchGameResultDto -> SearchGameResult, and so on. The
// developers/publishers category elements share SearchCompanyResult on
// the wire; DtoMappers handles the split into the two domain types.
