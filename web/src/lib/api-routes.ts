/**
 * Typed API route definitions derived from server/internal/api/router.go.
 *
 * Every path used with api.get/post/put/delete/upload must match one of these
 * patterns. The compiler will reject typos and references to removed endpoints.
 */

// Allow optional ?query suffix on any route
type WithQuery<T extends string> = T | `${T}?${string}`;

// ── GET ──────────────────────────────────────────────────────────────────────

export type ApiGetPath = WithQuery<
  // Health
  | "/health"

  // Auth
  | "/auth/setup-status"

  // Consoles
  | "/consoles"
  | `/consoles/${string}/games`
  | `/consoles/${string}/top-rated`
  | "/top-rated"

  // Games
  | "/games"
  | `/games/${string}`
  | `/games/${string}/download`
  | `/games/${string}/discs/${string}/download`
  | `/games/${string}/stats`
  | `/games/${string}/cheats`
  | `/games/${string}/similar`
  | `/games/${string}/developer-games`
  | `/games/${string}/core`
  | `/games/${string}/shared-sessions`
  | `/games/${string}/challenges`

  // Game sessions
  | `/games/${string}/sessions`
  | `/sessions/${string}`
  | `/sessions/${string}/saves`
  | `/sessions/${string}/saves/auto`
  | `/sessions/${string}/saves/slots`
  | `/sessions/${string}/saves/slot/${string}`
  | `/sessions/${string}/saves/${string}`
  | `/sessions/${string}/sram`
  | `/sessions/${string}/cheats`

  // Ratings
  | `/games/${string}/ratings`
  | `/games/${string}/ratings/summary`
  | `/games/${string}/ratings/mine`

  // Shared saves
  | `/games/${string}/shared-saves`
  | `/games/${string}/shared-saves/${string}/download`

  // Cores & BIOS
  | "/cores"
  | `/cores/${string}/download`
  | "/bios"
  | `/bios/${string}`

  // Stats
  | "/stats/most-played"
  | "/stats/most-active-players"

  // User
  | "/user/profile"
  | "/user/preferences"
  | "/user/stats"
  | "/user/recent"
  | "/user/favorites"
  | "/user/play-stats"
  | "/user/play-heatmap"
  | "/user/play-later"
  | "/user/devices"
  | `/user/devices/${string}/preferences`
  | `/user/games/${string}/keymapping`
  | "/user/challenges"
  | "/user/shared-session-invites"
  | "/user/shared-session-invites/count"
  | "/user/ra/status"
  | "/user/ra/token"
  | "/user/achievements/recent"

  // Shared Sessions
  | "/shared-sessions"
  | `/shared-sessions/${string}`
  | `/shared-sessions/${string}/saves`
  | `/shared-sessions/${string}/saves/auto`
  | `/shared-sessions/${string}/saves/${string}`

  // Netplay
  | "/netplay/sessions"
  | `/netplay/sessions/${string}`

  // Social
  | "/social/online"
  | "/social/activity"
  | `/users/search`
  | `/users/${string}/profile`
  | `/users/${string}/play-heatmap`

  // Collections
  | "/collections"
  | "/collections/public"
  | `/collections/${string}`

  // Top Lists
  | "/top-lists/top-rated"

  // Challenges
  | "/challenges"
  | `/challenges/${string}`
  | `/challenges/${string}/save/download`
  | `/challenges/${string}/screenshot`
  | `/challenges/${string}/attempts/mine`
  | `/challenges/${string}/leaderboard`

  // RetroAchievements
  | `/games/${string}/achievements`
  | `/games/${string}/achievements/progress`
  | `/games/${string}/achievements/timeline`
  | `/games/${string}/achievements/leaderboard`

  // Admin
  | "/admin/users"
  | "/admin/users/deleted"
  | `/admin/users/${string}/rate-limit`
  | `/admin/users/${string}/devices`
  | "/admin/settings"
  | "/admin/stats"
  | "/admin/scrape/status"
  | `/admin/games/${string}/covers`
  | `/admin/games/${string}/igdb-search`
  | "/admin/metadata-matches"
  | "/admin/igdb/status"
  | "/admin/cheats/stats"
  | "/admin/uploads"

  // WebSocket
  | "/ws"
>;

// ── POST ─────────────────────────────────────────────────────────────────────

export type ApiPostPath = WithQuery<
  // Auth
  | "/auth/login"
  | "/auth/register"
  | "/auth/setup"
  | "/auth/refresh"
  | "/auth/logout"

  // Games
  | `/games/${string}/scrape-if-needed`
  | `/games/${string}/play-time`
  | `/games/${string}/sessions`
  | `/games/${string}/sessions/from-shared-save/${string}`
  | `/games/${string}/shared-saves`
  | `/games/${string}/ratings`

  // Sessions
  | `/sessions/${string}/saves`
  | `/sessions/${string}/saves/auto`
  | `/sessions/${string}/sram`
  | `/sessions/${string}/play-time`
  | `/sessions/${string}/duplicate`

  // User
  | `/user/favorites/${string}`
  | `/user/play-later/${string}`
  | "/user/devices"
  | "/user/ra/link"
  | `/user/shared-session-invites/${string}/accept`
  | `/user/shared-session-invites/${string}/decline`

  // Collections
  | "/collections"
  | `/collections/${string}/games`

  // Shared Sessions
  | "/shared-sessions"
  | `/shared-sessions/${string}/invites`
  | `/shared-sessions/${string}/leave`
  | `/shared-sessions/${string}/take-turn`
  | `/shared-sessions/${string}/release-turn`
  | `/shared-sessions/${string}/heartbeat`
  | `/shared-sessions/${string}/saves`
  | `/shared-sessions/${string}/saves/auto`

  // Netplay
  | "/netplay/sessions"
  | "/netplay/sessions/join"
  | `/netplay/sessions/${string}/leave`

  // Challenges
  | "/challenges"
  | `/challenges/${string}/attempts/start`
  | `/challenges/${string}/attempts/${string}/complete`
  | `/challenges/${string}/attempts/${string}/abandon`

  // Admin
  | "/admin/users"
  | `/admin/games/${string}/scrape`
  | `/admin/games/${string}/metadata`
  | `/admin/games/${string}/igdb-match`
  | "/admin/games/scan"
  | "/admin/scrape"
  | "/admin/bios"
  | "/admin/bios/download"
  | "/admin/igdb/test"
  | "/admin/cheats/import"
  | "/admin/uploads"
  | `/admin/uploads/${string}/console`
  | `/admin/uploads/${string}/scrape`
  | "/admin/uploads/scrape"
  | `/admin/uploads/${string}/accept`
  | `/admin/uploads/${string}/reject`
  | "/admin/uploads/accept-all"
  | "/admin/uploads/reject-all"
>;

// ── PUT ──────────────────────────────────────────────────────────────────────

export type ApiPutPath = WithQuery<
  // User
  | "/user/profile"
  | "/user/password"
  | "/user/preferences"
  | `/user/devices/${string}`
  | `/user/devices/${string}/preferences`
  | "/user/play-later/reorder"
  | "/user/ra/settings"
  | `/user/games/${string}/keymapping`

  // Sessions
  | `/sessions/${string}`
  | `/sessions/${string}/saves/${string}`
  | `/sessions/${string}/saves/slot/${string}`
  | `/sessions/${string}/cheats`

  // Collections
  | `/collections/${string}`

  // Shared Sessions
  | `/shared-sessions/${string}`
  | `/shared-sessions/${string}/saves/${string}/rename`

  // Netplay
  | `/netplay/sessions/${string}/settings`

  // Challenges
  | `/challenges/${string}`

  // Admin
  | `/admin/users/${string}`
  | "/admin/settings"
  | `/admin/games/${string}/covers`
  | `/admin/games/${string}/verification-tag`
>;

// ── DELETE ────────────────────────────────────────────────────────────────────

export type ApiDeletePath = WithQuery<
  // Games
  | `/games/${string}/play-time`
  | `/games/${string}/ratings`
  | `/games/${string}/shared-saves/${string}`

  // Sessions
  | `/sessions/${string}`
  | `/sessions/${string}/saves/${string}`
  | `/sessions/${string}/play-time`

  // User
  | `/user/favorites/${string}`
  | `/user/play-later/${string}`
  | `/user/devices/${string}`
  | "/user/ra/link"
  | `/user/games/${string}/keymapping`

  // Collections
  | `/collections/${string}`
  | `/collections/${string}/games/${string}`

  // Shared Sessions
  | `/shared-sessions/${string}`
  | `/shared-sessions/${string}/members/${string}`
  | `/shared-sessions/${string}/saves/${string}`

  // Netplay
  | `/netplay/sessions/${string}`

  // Challenges
  | `/challenges/${string}`

  // Admin
  | `/admin/users/${string}`
  | `/admin/users/${string}/permanent`
  | `/admin/users/${string}/rate-limit`
  | `/admin/bios/${string}`
  | "/admin/uploads"
>;
