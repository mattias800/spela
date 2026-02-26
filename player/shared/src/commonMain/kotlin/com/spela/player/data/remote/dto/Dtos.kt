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

@Serializable
data class UserDto(
    val id: String,
    val username: String,
    val email: String = "",
    val role: String,
    val avatarUrl: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** Matches ConsoleResponse in responses.go */
@Serializable
data class ConsoleDto(
    val id: String,
    val name: String,
    val abbreviation: String,
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
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class GameDiscDto(
    val discNumber: Int,
    val fileName: String,
    val fileSize: Long,
)

/** Matches GameResponse in responses.go - enriched with consoleName, isFavorite, etc. */
@Serializable
data class GameDto(
    val id: String,
    val title: String,
    val consoleId: String,
    val consoleName: String = "",
    val coverAspectRatio: Double = 0.75,
    val fileName: String = "",
    val fileSize: Long = 0,
    val coverUrl: String? = null,
    val screenshotUrls: List<String> = emptyList(),
    val description: String? = null,
    val developer: String? = null,
    val publisher: String? = null,
    val releaseDate: String? = null,
    val genre: String? = null,
    val players: Int = 0,
    val rating: Double = 0.0,
    val averageRating: Double = 0.0,
    val ratingCount: Long = 0,
    val userRating: Int? = null,
    val coreOverride: String? = null,
    val scraperId: String? = null,
    val scrapeAttempts: Int = 0,
    val isFavorite: Boolean = false,
    val isInPlayLater: Boolean = false,
    val lastPlayedAt: String? = null,
    val totalPlayTime: Long = 0,
    val discCount: Int = 0,
    val discs: List<GameDiscDto> = emptyList(),
    val achievementsWarning: String? = null,
    val verificationStatus: String? = null,
    val verificationTag: String? = null,
    val region: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** Wrapper for GET /api/games which returns {data, total, page, pageSize} */
@Serializable
data class GameListResponse(
    val data: List<GameDto>,
    val total: Long,
    val page: Int,
    val pageSize: Int,
)

@Serializable
data class SaveStateDto(
    val id: Long,
    val userId: Long = 0,
    val gameId: Long,
    val name: String,
    val fileSize: Long = 0,
    val isAuto: Boolean = false,
    val notes: String? = null,
    val screenshotUrl: String? = null,
    val slot: Int? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class StorageUsageDto(
    val totalBytes: Long,
    val games: List<GameStorageUsageDto>,
)

@Serializable
data class GameStorageUsageDto(
    val gameId: Long,
    val gameTitle: String,
    val totalBytes: Long,
    val saveStateBytes: Long = 0,
    val sramBytes: Long = 0,
)

@Serializable
data class LibretroCoreDto(
    val id: Long,
    val name: String,
    val displayName: String = "",
    val description: String? = null,
    val version: String? = null,
    val platforms: String = "",
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class ConsoleKeyMappingDto(
    val selectedMapping: String = "",
    val customMapping: Map<String, String> = emptyMap(),
)

@Serializable
data class GameKeyMappingDto(
    val customMapping: Map<String, String> = emptyMap(),
)

@Serializable
data class UpdateGameKeyMappingRequest(
    val customMapping: Map<String, String>,
)

@Serializable
data class UserPreferencesDto(
    val showPerformanceOverlay: Boolean = false,
    val autoSaveEnabled: Boolean = true,
    val autoLoadSaveEnabled: Boolean = true,
    val selectedShader: String = "none",
    val selectedTheme: String = "default-dark",
    val consoleShaders: Map<String, String> = emptyMap(),
    val raLinked: Boolean = false,
    val raUsername: String = "",
    val raHardcoreEnabled: Boolean = false,
    val selectedKeyMapping: String = "default",
    val customKeyMapping: Map<String, String> = emptyMap(),
    val consoleKeyMappings: Map<String, ConsoleKeyMappingDto> = emptyMap(),
)

@Serializable
data class UpdatePreferencesRequest(
    val showPerformanceOverlay: Boolean? = null,
    val autoSaveEnabled: Boolean? = null,
    val autoLoadSaveEnabled: Boolean? = null,
    val selectedShader: String? = null,
    val selectedTheme: String? = null,
    val consoleShaders: Map<String, String>? = null,
    val selectedKeyMapping: String? = null,
    val customKeyMapping: Map<String, String>? = null,
    val consoleKeyMappings: Map<String, ConsoleKeyMappingDto>? = null,
)

// Devices

@Serializable
data class RegisterDeviceRequest(
    val deviceUuid: String,
    val name: String,
    val platform: String,
)

@Serializable
data class DeviceDto(
    val id: Long,
    val deviceUuid: String,
    val name: String,
    val platform: String,
    val lastSeenAt: String = "",
    val consoleShaders: Map<String, String> = emptyMap(),
)

@Serializable
data class UpdateDevicePreferencesRequest(
    val consoleShaders: Map<String, String>,
)

// Shared Saves

@Serializable
data class SharedSaveStateDto(
    val id: String,
    val userId: String,
    val username: String,
    val avatarUrl: String? = null,
    val gameId: String,
    val name: String,
    val description: String = "",
    val fileSize: Long = 0,
    val screenshotUrl: String? = null,
    val downloadCount: Int = 0,
    val createdAt: String = "",
)

@Serializable
data class SharedSavesResponse(
    val data: List<SharedSaveStateDto> = emptyList(),
    val total: Long = 0,
    val page: Int = 1,
    val pageSize: Int = 20,
)

// Ratings

@Serializable
data class RateGameRequest(
    val rating: Int,
    val review: String = "",
)

@Serializable
data class GameRatingDto(
    val id: String,
    val userId: String,
    val username: String,
    val avatarUrl: String? = null,
    val gameId: String,
    val rating: Int,
    val review: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Serializable
data class GameRatingsResponse(
    val data: List<GameRatingDto> = emptyList(),
    val total: Long = 0,
    val page: Int = 1,
    val pageSize: Int = 20,
)

@Serializable
data class RatingSummaryDto(
    val averageRating: Double = 0.0,
    val totalRatings: Long = 0,
    val distribution: Map<String, Int> = emptyMap(),
)

// Social

@Serializable
data class OnlineUserGameDto(
    val id: String,
    val title: String,
    val coverUrl: String? = null,
    val consoleName: String = "",
)

@Serializable
data class OnlineUserDto(
    val id: String,
    val username: String,
    val avatarUrl: String? = null,
    val currentGame: OnlineUserGameDto? = null,
)

@Serializable
data class OnlineUsersResponse(
    val users: List<OnlineUserDto> = emptyList(),
)

@Serializable
data class ActivityEventDto(
    val id: String,
    val userId: String,
    val username: String,
    val userAvatarUrl: String? = null,
    val eventType: String,
    val gameId: String? = null,
    val gameTitle: String? = null,
    val gameCoverUrl: String? = null,
    val gameConsoleName: String? = null,
    val metadata: String? = null,
    val createdAt: String = "",
)

@Serializable
data class ActivityFeedResponse(
    val data: List<ActivityEventDto> = emptyList(),
    val total: Long = 0,
    val page: Int = 1,
    val pageSize: Int = 20,
)

/** Wrapper for GET /api/games/:id/core when core is not in DB */
@Serializable
data class CoreNameResponse(
    val coreName: String,
)

// RetroAchievements

@Serializable
data class RAStatusDto(
    val linked: Boolean = false,
    val username: String = "",
    val hardcoreEnabled: Boolean = false,
)

@Serializable
data class RALinkRequestDto(
    val username: String,
    val password: String,
)

@Serializable
data class RATokenResponseDto(
    val username: String,
    val token: String,
)

@Serializable
data class RASettingsRequestDto(
    val hardcoreEnabled: Boolean,
)

// Public Profile

@Serializable
data class PublicProfileGameDto(
    val id: String,
    val title: String,
    val coverUrl: String? = null,
    val consoleName: String = "",
    val playTime: Long = 0,
)

@Serializable
data class PublicProfileDto(
    val id: String,
    val username: String,
    val avatarUrl: String? = null,
    val memberSince: String = "",
    val isOnline: Boolean = false,
    val currentGame: OnlineUserGameDto? = null,
    val totalPlayTime: Long = 0,
    val gamesPlayed: Long = 0,
    val favoriteGames: List<PublicProfileGameDto> = emptyList(),
    val recentGames: List<PublicProfileGameDto> = emptyList(),
    val topGames: List<PublicProfileGameDto> = emptyList(),
)

// Relay

@Serializable
data class RelayDto(
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
data class RelayDetailDto(
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
    val members: List<RelayMemberDto> = emptyList(),
)

@Serializable
data class RelayMemberDto(
    val userId: String,
    val username: String,
    val avatarUrl: String? = null,
    val role: String = "member",
    val joinedAt: String = "",
    val lastPlayedAt: String? = null,
    val isOnline: Boolean = false,
)

@Serializable
data class RelayInvitationDto(
    val id: String,
    val relayId: String,
    val relayName: String = "",
    val gameId: String = "",
    val gameTitle: String = "",
    val gameCoverUrl: String? = null,
    val gameConsoleName: String = "",
    val inviterUsername: String = "",
    val inviterAvatarUrl: String? = null,
    val createdAt: String = "",
)

@Serializable
data class RelaySaveDto(
    val id: Long,
    val relayId: String = "",
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
data class RelaysResponse(
    val data: List<RelayDto> = emptyList(),
    val total: Long = 0,
    val page: Int = 1,
    val pageSize: Int = 20,
)

@Serializable
data class RelayInvitationsResponse(
    val data: List<RelayInvitationDto> = emptyList(),
    val total: Long = 0,
)

@Serializable
data class CreateRelayRequest(
    val name: String,
    val gameId: String,
    val description: String = "",
)

@Serializable
data class InviteToRelayRequest(
    val username: String,
)

@Serializable
data class TakeTurnResponse(
    val turnToken: String,
)

@Serializable
data class RelayInvitationCountResponse(
    val count: Int = 0,
)

// BIOS

@Serializable
data class BiosFileDto(
    val name: String,
    val size: Long,
    val md5: String? = null,
    val consoleId: String? = null,
    val consoleName: String? = null,
    val description: String? = null,
    val required: Boolean = false,
    val status: String = "present",
)

@Serializable
data class BiosConsoleDto(
    val consoleId: String,
    val consoleName: String,
    val biosRequired: Boolean = false,
    val status: String = "not_required",
    val requiredPresent: Int = 0,
    val requiredTotal: Int = 0,
    val optionalPresent: Int = 0,
    val optionalTotal: Int = 0,
    val files: List<BiosConsoleFileDto> = emptyList(),
)

@Serializable
data class BiosConsoleFileDto(
    val fileName: String,
    val description: String = "",
    val required: Boolean = false,
    val md5: String? = null,
    val status: String = "missing",
)

@Serializable
data class BiosStatusResponse(
    val files: List<BiosFileDto> = emptyList(),
    val consoles: List<BiosConsoleDto> = emptyList(),
)

// Collections

@Serializable
data class CollectionDto(
    val id: String,
    val userId: String,
    val username: String,
    val avatarUrl: String? = null,
    val name: String,
    val description: String? = null,
    val isPublic: Boolean = false,
    val coverUrl: String? = null,
    val gameCount: Int = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class CollectionDetailDto(
    val id: String,
    val userId: String,
    val username: String,
    val avatarUrl: String? = null,
    val name: String,
    val description: String? = null,
    val isPublic: Boolean = false,
    val coverUrl: String? = null,
    val gameCount: Int = 0,
    val games: List<GameDto> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class CollectionsResponse(
    val data: List<CollectionDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 20,
)

@Serializable
data class CreateCollectionRequest(
    val name: String,
    val description: String? = null,
    val isPublic: Boolean = false,
)

@Serializable
data class UpdateCollectionRequest(
    val name: String? = null,
    val description: String? = null,
    val isPublic: Boolean? = null,
)

@Serializable
data class AddGameToCollectionRequest(val gameId: Int)

// Stats

@Serializable
data class MostPlayedGameDto(
    val game: GameDto,
    val totalPlayers: Int = 0,
    val totalPlayTime: Long = 0,
)

@Serializable
data class MostPlayedResponse(
    val games: List<MostPlayedGameDto> = emptyList(),
)

@Serializable
data class ActivePlayerDto(
    val userId: String,
    val username: String,
    val avatarUrl: String? = null,
    val totalPlayTime: Long = 0,
    val gamesPlayed: Int = 0,
    val lastPlayed: String? = null,
)

@Serializable
data class MostActivePlayersResponse(
    val players: List<ActivePlayerDto> = emptyList(),
)

// Challenges

@Serializable
data class ChallengeDto(
    val id: String,
    val creatorId: String,
    val creatorUsername: String = "",
    val creatorAvatarUrl: String? = null,
    val gameId: String,
    val gameTitle: String = "",
    val gameCoverUrl: String? = null,
    val gameConsoleName: String = "",
    val name: String,
    val description: String = "",
    val type: String = "completion",
    val difficulty: String = "medium",
    val status: String = "active",
    val screenshotUrl: String? = null,
    val coreName: String = "",
    val saveFileSize: Long = 0,
    val attemptCount: Int = 0,
    val completionCount: Int = 0,
    val bestTimeMs: Long? = null,
    val expiresAt: String? = null,
    val createdAt: String = "",
)

@Serializable
data class ChallengesResponse(
    val data: List<ChallengeDto> = emptyList(),
    val total: Long = 0,
    val page: Int = 1,
    val pageSize: Int = 20,
)

@Serializable
data class ChallengeAttemptDto(
    val id: String,
    val challengeId: String,
    val userId: String,
    val username: String = "",
    val avatarUrl: String? = null,
    val status: String = "in_progress",
    val startedAt: String = "",
    val completedAt: String? = null,
    val durationMs: Long = 0,
    val isBest: Boolean = false,
)

@Serializable
data class ChallengeLeaderboardEntryDto(
    val rank: Int,
    val userId: String,
    val username: String = "",
    val avatarUrl: String? = null,
    val durationMs: Long = 0,
    val completedAt: String = "",
    val isCurrentUser: Boolean = false,
)

@Serializable
data class ChallengeLeaderboardResponse(
    val data: List<ChallengeLeaderboardEntryDto> = emptyList(),
    val total: Long = 0,
    val page: Int = 1,
    val pageSize: Int = 50,
)

// Netplay

@Serializable
data class NetplaySessionDto(
    val id: String,
    val gameId: String,
    val gameTitle: String = "",
    val gameCoverUrl: String? = null,
    val consoleName: String = "",
    val coverAspectRatio: Float = 0.75f,
    val hostId: String,
    val hostUsername: String = "",
    val hostAvatarUrl: String? = null,
    val clientId: String? = null,
    val clientUsername: String? = null,
    val clientAvatarUrl: String? = null,
    val status: String = "waiting",
    val inputDelay: Int = 3,
    val inviteCode: String = "",
    val endReason: String? = null,
    val createdAt: String = "",
    val startedAt: String? = null,
    val endedAt: String? = null,
)

@Serializable
data class NetplaySessionsResponse(
    val data: List<NetplaySessionDto> = emptyList(),
    val total: Long = 0,
    val page: Int = 1,
    val pageSize: Int = 20,
)

@Serializable
data class CreateNetplaySessionRequest(
    val gameId: String,
    val inputDelay: Int = 3,
)

@Serializable
data class JoinByInviteCodeRequest(
    val code: String,
)

@Serializable
data class UpdateNetplaySettingsRequest(
    val inputDelay: Int,
)

// Game Stats

@Serializable
data class TopPlayerDto(
    val userId: String,
    val username: String,
    val avatarUrl: String? = null,
    val playTime: Long = 0,
)

@Serializable
data class GameStatsDto(
    val totalPlayers: Int = 0,
    val totalPlayTime: Long = 0,
    val averagePlayTime: Long = 0,
    val topPlayers: List<TopPlayerDto> = emptyList(),
)

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

// Achievement Timeline

@Serializable
data class AchievementTimelineEntryDto(
    val achievementRaId: Long = 0,
    val title: String = "",
    val description: String = "",
    val points: Int = 0,
    val badgeUrl: String? = null,
    val unlockedAt: String = "",
    val isHardcore: Boolean = false,
    val playTimeAtUnlock: Long? = null,
)

@Serializable
data class AchievementTimelineResponse(
    val raGameId: Long? = null,
    val gameTitle: String = "",
    val totalPlayTime: Long = 0,
    val timeline: List<AchievementTimelineEntryDto> = emptyList(),
    val totalAchievements: Int = 0,
    val unlockedCount: Int = 0,
    val totalPoints: Int = 0,
    val earnedPoints: Int = 0,
)

// Achievement Leaderboard

@Serializable
data class AchievementLeaderboardEntryDto(
    val userId: String = "",
    val username: String = "",
    val avatarUrl: String? = null,
    val unlockedCount: Int = 0,
    val earnedPoints: Int = 0,
    val firstUnlockedAt: String? = null,
    val lastUnlockedAt: String? = null,
)

@Serializable
data class AchievementLeaderboardResponse(
    val raGameId: Long? = null,
    val totalAchievements: Int = 0,
    val leaderboard: List<AchievementLeaderboardEntryDto> = emptyList(),
)

// User Stats

@Serializable
data class UserStatsDto(
    val totalPlayTime: Long = 0,
    val gamesPlayed: Long = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val mostPlayedGame: GameDto? = null,
    val mostPlayedGameTime: Long = 0,
    val lastPlayedAt: String? = null,
)

// Recent Achievements

@Serializable
data class RecentAchievementDto(
    val achievementRaId: Long = 0,
    val title: String = "",
    val description: String = "",
    val points: Int = 0,
    val badgeUrl: String? = null,
    val unlockedAt: String = "",
    val isHardcore: Boolean = false,
    val playTimeAtUnlock: Long? = null,
    val gameId: String = "",
    val gameTitle: String = "",
    val consoleName: String = "",
    val coverUrl: String? = null,
)

@Serializable
data class RecentAchievementsResponse(
    val achievements: List<RecentAchievementDto> = emptyList(),
)

// Top Rated

@Serializable
data class TopRatedGameDto(
    val rank: Int,
    val name: String,
    val coverUrl: String? = null,
    val rating: Double = 0.0,
    val localGameId: String? = null,
)

// Similar Games

@Serializable
data class SimilarGameDto(
    val name: String,
    val coverUrl: String? = null,
    val rating: Double = 0.0,
    val localGameId: String? = null,
)

// Developer Games

@Serializable
data class DeveloperGameDto(
    val id: String,
    val title: String,
    val coverUrl: String? = null,
    val consoleName: String = "",
)

// Save Data (SRAM)

@Serializable
data class SaveDataDto(
    val id: Long = 0,
    val userId: Long = 0,
    val gameId: Long = 0,
    val name: String = "",
    val fileSize: Long = 0,
    val isActive: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String = "",
)
