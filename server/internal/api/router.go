package api

import (
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/igdb"
	"github.com/spela/server/internal/retroachievements"
	"github.com/spela/server/internal/scanner"
	"github.com/spela/server/internal/scraper"
	"github.com/spela/server/internal/storage"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// Config holds configuration for the API router.
type Config struct {
	DB            *gorm.DB
	JWTSecret     string
	EncryptionKey []byte // separate AES key; falls back to DeriveEncryptionKey(JWTSecret) if nil
	GameDirs      []string
	Storage       *storage.Storage
	Scanner       *scanner.Scanner
	Scraper       *scraper.Scraper
	Hub           *ws.Hub
	NetplayHub    *ws.NetplayHub
	CoreDir       string
	FrontendDir   string // path to Vite dist/ output; empty = disabled
	CORSOrigins                  []string
	RAClient                     *retroachievements.RAClient // optional; defaults to production RA client
	ChallengeAttemptRateLimitSec int                         // 0 = disabled; default 30 in production
	Version                      string
	TestMode                     bool // when true, registers POST /api/test/reset for E2E test isolation
}

// NewRouter creates and configures the Gin router with all endpoints.
// NewRouter creates a configured gin.Engine with all routes and middleware.
// The returned cleanup function stops background goroutines (rate limiter cleanup).
// In production the cleanup is optional (goroutines run for process lifetime),
// but tests must call it to avoid goroutine leaks.
func NewRouter(cfg Config) (*gin.Engine, func()) {
	r := gin.Default()

	// Only trust proxies on private/loopback networks (Docker internal, localhost).
	r.SetTrustedProxies([]string{"10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8", "::1/128"})

	// Security headers
	r.Use(func(c *gin.Context) {
		// COOP/COEP — required for SharedArrayBuffer (EmulatorJS threaded cores)
		c.Header("Cross-Origin-Opener-Policy", "same-origin")
		c.Header("Cross-Origin-Embedder-Policy", "credentialless")
		// Prevent MIME type sniffing
		c.Header("X-Content-Type-Options", "nosniff")
		// Prevent clickjacking — allow same-origin framing for the emulator iframe
		c.Header("X-Frame-Options", "SAMEORIGIN")
		// Minimal referrer info to external sites
		c.Header("Referrer-Policy", "strict-origin-when-cross-origin")
		// Content Security Policy — permissive enough for EmulatorJS (blob workers,
		// WASM eval, blob URL fetches for cores, CDN version check)
		c.Header("Content-Security-Policy", "default-src 'self'; script-src 'self' 'unsafe-eval' blob:; style-src 'self' 'unsafe-inline'; img-src 'self' https: data:; connect-src 'self' wss: ws: blob: https://cdn.emulatorjs.org; worker-src 'self' blob:; frame-ancestors 'self'")
		// HSTS — instruct browsers to always use HTTPS. Safe even behind a reverse
		// proxy; browsers only honour this header on HTTPS responses.
		c.Header("Strict-Transport-Security", "max-age=63072000; includeSubDomains")
		// Deny access to sensitive browser features
		c.Header("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()")
		c.Next()
	})

	// Global request body size limit for JSON endpoints (1 MB).
	// File upload endpoints (multipart) are excluded and use their own limits.
	r.Use(BodySizeLimiter(MaxJSONBodySize))

	// CORS - configurable origins; empty = reject all cross-origin requests (no CORS headers sent)
	corsOrigins := cfg.CORSOrigins
	if len(corsOrigins) > 0 {
		allowCreds := true
		for _, o := range corsOrigins {
			if o == "*" {
				allowCreds = false
				break
			}
		}
		r.Use(cors.New(cors.Config{
			AllowOrigins:     corsOrigins,
			AllowMethods:     []string{"GET", "POST", "PUT", "DELETE", "OPTIONS"},
			AllowHeaders:     []string{"Origin", "Content-Type", "Authorization", "X-Turn-Token"},
			ExposeHeaders:    []string{"Content-Length"},
			AllowCredentials: allowCreds,
			MaxAge:           12 * time.Hour,
		}))
	}

	// Serve images — save screenshots require auth + ownership; everything else is public.
	// Per-IP rate limit (#1327): this route is unauthenticated and, on a cache
	// miss, triggers a DB lookup + outbound CDN fetch + disk write. The limit
	// is generous so a normal grid of covers loads fine (rate.Limiter bursts up
	// to the limit) while bounding sustained anonymous amplification.
	imageLimiter := NewRateLimiter(600, time.Minute)
	imageHandler := &ImageHandler{ImageDir: cfg.Storage.ImageDir, JWTSecret: cfg.JWTSecret, DB: cfg.DB, Scraper: cfg.Scraper}
	r.GET("/api/images/*filepath", imageLimiter.RateLimit(), imageHandler.ServeImage)

	// Bootstrap the huma API. Registered handlers below run alongside the raw
	// gin handlers that follow — the two stacks coexist during the migration.
	// The OpenAPI spec is exposed at /api/openapi and Swagger UI at /api/docs.
	humaAPI := SetupHumaAPI(r, cfg.Version)

	// Health check (public, no auth required)
	RegisterSystemRoutes(humaAPI, cfg.DB, cfg.Version)

	// Forward declarations for huma route registration — the concrete
	// registrations are done below once handler instances and the shared
	// per-user rate limiter are constructed. We keep the migrated routes
	// grouped together so it is easy to see what has moved off raw gin.

	// Rate limiter for auth endpoints (login, register, setup)
	authLimiter := NewRateLimiter(120, time.Minute)

	// Separate higher-limit rate limiter for token refresh (called frequently during normal use)
	refreshLimiter := NewRateLimiter(300, time.Minute)

	// Rate limiter for file download endpoints (prevents bandwidth abuse)
	downloadLimiter := NewRateLimiter(30, time.Minute)

	// Rate limiter for file upload endpoints (prevents storage abuse)
	uploadLimiter := NewRateLimiter(30, time.Minute)

	// Global per-user rate limiter for authenticated API endpoints (prevents abuse from compromised accounts).
	// In test mode, use a much higher limit to avoid spurious 429s during E2E tests.
	userRateLimit := 300
	if cfg.TestMode {
		userRateLimit = 10000
	}
	userLimiter := NewRateLimiter(userRateLimit, time.Minute)

	// Handlers
	authHandler := &AuthHandler{DB: cfg.DB, JWTSecret: cfg.JWTSecret}
	gameHandler := &GameHandler{
		DB:       cfg.DB,
		Scanner:  cfg.Scanner,
		Storage:  cfg.Storage,
		Hub:      cfg.Hub,
		GameDirs: cfg.GameDirs,
		Scraper:  cfg.Scraper,
	}
	consoleHandler := &ConsoleHandler{DB: cfg.DB, Storage: cfg.Storage, Scraper: cfg.Scraper}
	makerHandler := &MakerHandler{DB: cfg.DB}
	userHandler := &UserHandler{DB: cfg.DB, Hub: cfg.Hub, JWTSecret: cfg.JWTSecret}
	deviceHandler := &DeviceHandler{DB: cfg.DB}
	statsHandler := &StatsHandler{DB: cfg.DB}
	adminHandler := &AdminHandler{DB: cfg.DB, Scraper: cfg.Scraper, Hub: cfg.Hub, Storage: cfg.Storage}
	igdbHandler := &IGDBHandler{DB: cfg.DB}
	coreHandler := &CoreHandler{DB: cfg.DB, CoreDir: cfg.CoreDir}
	raClient := cfg.RAClient
	if raClient == nil {
		raClient = retroachievements.NewRAClient()
	}
	encryptionKey := cfg.EncryptionKey
	if len(encryptionKey) == 0 {
		encryptionKey = auth.DeriveEncryptionKey(cfg.JWTSecret)
	}
	// Make the effective key available to the secret-server-setting
	// encrypt/decrypt helpers (#1318).
	setServerSettingsKey(encryptionKey)
	socialHandler := &SocialHandler{DB: cfg.DB, Hub: cfg.Hub}
	ratingHandler := &RatingHandler{DB: cfg.DB, Hub: cfg.Hub}
	sharedSaveHandler := &SharedSaveHandler{DB: cfg.DB, Storage: cfg.Storage, Hub: cfg.Hub}
	collectionHandler := &CollectionHandler{DB: cfg.DB, Hub: cfg.Hub}
	playLaterHandler := &PlayLaterHandler{DB: cfg.DB, Hub: cfg.Hub}
	sharedSessionHandler := &SharedSessionHandler{DB: cfg.DB, Storage: cfg.Storage, Hub: cfg.Hub}
	netplayHandler := &NetplayHandler{DB: cfg.DB, Hub: cfg.Hub, NetplayHub: cfg.NetplayHub}
	var raQueue *scraper.ScrapeQueue
	var raAPIKey string
	if cfg.Scraper != nil {
		raQueue = cfg.Scraper.Queue
		raAPIKey = cfg.Scraper.RAAPIKey
	}
	raHandler := &RAHandler{
		DB: cfg.DB, RAClient: raClient, GameDir: cfg.GameDirs[0],
		EncryptionKey: encryptionKey, Queue: raQueue, RAAPIKey: raAPIKey,
	}
	biosHandler := &BiosHandler{Storage: cfg.Storage, DB: cfg.DB, Hub: cfg.Hub}
	gameKeyMappingHandler := &GameKeyMappingHandler{DB: cfg.DB}
	challengeHandler := NewChallengeHandler(cfg.DB, cfg.Storage, cfg.Hub)
	challengeHandler.AttemptRateLimitSeconds = cfg.ChallengeAttemptRateLimitSec
	sessionHandler := &SessionHandler{DB: cfg.DB, Storage: cfg.Storage}
	artworkHandler := &ArtworkHandler{DB: cfg.DB}
	var igdbClient *igdb.Client
	if cfg.Scraper != nil {
		igdbClient = cfg.Scraper.IGDBClient
	}
	exploreHandler := &ExploreHandler{DB: cfg.DB, IGDBClient: igdbClient, Scraper: cfg.Scraper}
	savedSearchHandler := &SavedSearchHandler{DB: cfg.DB}
	enrichmentHandler := &EnrichmentHandler{DB: cfg.DB, Scraper: cfg.Scraper, Hub: cfg.Hub}
	discoveryHandler := &GameDiscoveryHandler{DB: cfg.DB, Scraper: cfg.Scraper}
	searchHandler := &SearchHandler{DB: cfg.DB}
	setupHandler := &SetupHandler{
		DB:            cfg.DB,
		JWTSecret:     cfg.JWTSecret,
		EncryptionKey: cfg.EncryptionKey,
		GameDirs:      cfg.GameDirs,
		Storage:       cfg.Storage,
	}
	romHackHandler := &RomHackHandler{
		DB:       cfg.DB,
		GameDirs: cfg.GameDirs,
	}
	stagingDir := filepath.Join(cfg.GameDirs[0], "staging")
	uploadHandler := &UploadHandler{
		DB:         cfg.DB,
		Storage:    cfg.Storage,
		Scraper:    cfg.Scraper,
		GameDirs:   cfg.GameDirs,
		StagingDir: stagingDir,
	}

	// Register huma-migrated operations. These run through the huma adapter
	// on top of the gin engine — they cohabit with the raw gin routes below
	// during the incremental migration. Each registration function attaches
	// the auth + per-user rate-limit middleware equivalent to the gin
	// protected group below.
	RegisterMakerRoutes(humaAPI, makerHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterCoreRoutes(humaAPI, coreHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterStatsRoutes(humaAPI, statsHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterConsoleRoutes(humaAPI, consoleHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterUserRoutes(humaAPI, userHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterUserMutationRoutes(humaAPI, userHandler, cfg.JWTSecret, cfg.DB, userLimiter, authLimiter)
	RegisterArtworkRoutes(humaAPI, artworkHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterDiscoveryRoutes(humaAPI, discoveryHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterGameRoutes(humaAPI, gameHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterRatingRoutes(humaAPI, ratingHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterFavoriteRoutes(humaAPI, userHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterPlayLaterRoutes(humaAPI, playLaterHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterSearchRoutes(humaAPI, searchHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterSocialRoutes(humaAPI, socialHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterSessionRoutes(humaAPI, sessionHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterSessionSaveUploadRoutes(humaAPI, sessionHandler, cfg.JWTSecret, cfg.DB, userLimiter, uploadLimiter)
	RegisterSharedSaveRoutes(humaAPI, sharedSaveHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterSharedSessionRoutes(humaAPI, sharedSessionHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterSharedUploadRoutes(humaAPI, sharedSaveHandler, sharedSessionHandler, cfg.JWTSecret, cfg.DB, userLimiter, uploadLimiter)
	RegisterNetplayRoutes(humaAPI, netplayHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterChallengeRoutes(humaAPI, challengeHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterChallengeCreateRoute(humaAPI, challengeHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterDownloadRoutes(humaAPI, coreHandler, biosHandler, sessionHandler, sharedSaveHandler, sharedSessionHandler, consoleHandler, gameHandler, challengeHandler, cfg.JWTSecret, cfg.DB, userLimiter, downloadLimiter)
	RegisterAdminRoutes(humaAPI, adminHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	adminSystemEventHandler := &SystemEventHandler{DB: cfg.DB}
	RegisterSystemEventRoutes(humaAPI, adminSystemEventHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterEnrichmentRoutes(humaAPI, enrichmentHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterRARoutes(humaAPI, raHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterBiosRoutes(humaAPI, biosHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	showcaseHandler := &AchievementShowcaseHandler{DB: cfg.DB}
	RegisterAchievementShowcaseRoutes(humaAPI, showcaseHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterGameAchievementsRoute(humaAPI, raHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterDeviceRoutes(humaAPI, deviceHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterScraperAdminRoutes(humaAPI, adminHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterCheatAdminRoutes(humaAPI, adminHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	emulatorErrorHandler := &SystemEventHandler{DB: cfg.DB}
	RegisterUserExtraRoutes(humaAPI, userHandler, statsHandler, gameKeyMappingHandler, emulatorErrorHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterMiscRoutes(humaAPI, sessionHandler, savedSearchHandler, adminHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterCollectionRoutes(humaAPI, collectionHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterAdminGameRoutes(humaAPI, gameHandler, adminHandler, igdbHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterUploadAdminRoutes(humaAPI, uploadHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterAdminMultipartRoutes(humaAPI, biosHandler, gameHandler, romHackHandler, uploadHandler, cfg.JWTSecret, cfg.DB, userLimiter, uploadLimiter)
	RegisterSocialExtraRoutes(humaAPI, socialHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterBlockRoutes(humaAPI, socialHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterProfileRoutes(humaAPI, exploreHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterExploreFeaturedRoutes(humaAPI, exploreHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterExploreForYouRoutes(humaAPI, exploreHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterExploreDeveloperRoutes(humaAPI, exploreHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterExploreConsoleRoutes(humaAPI, exploreHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterExploreGalleryRoutes(humaAPI, exploreHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterExploreCommunityRoutes(humaAPI, exploreHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterExploreTemporalRoutes(humaAPI, exploreHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterExploreChallengeRoutes(humaAPI, exploreHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterExploreWizardRoutes(humaAPI, exploreHandler, cfg.JWTSecret, cfg.DB, userLimiter)
	RegisterAuthRoutes(humaAPI, authHandler, authLimiter, refreshLimiter)
	RegisterAuthProtectedRoutes(humaAPI, authHandler, cfg.JWTSecret, cfg.DB)
	RegisterSetupDiagnosticsRoutes(humaAPI, setupHandler)

	// Public auth routes — login/register/setup/refresh/setup-status have been
	// migrated to huma (see RegisterAuthRoutes above). Rate limiting (per-IP
	// auth/refresh limiters) is applied via IPRateLimitMiddleware inside the
	// huma registration.

	// Setup diagnostics — migrated to huma (see RegisterSetupDiagnosticsRoutes above).
	// Public during first-time setup (no users exist), admin-only afterwards.

	// Logout — migrated to huma (see RegisterAuthProtectedRoutes above).

	// Console images (icon/logo/logo.png/preview-screenshot) and the branding
	// logo have been migrated to huma — see RegisterDownloadRoutes above.

	// Protected routes
	api := r.Group("/api")
	api.Use(AuthMiddleware(cfg.JWTSecret, cfg.DB))
	api.Use(userLimiter.UserRateLimit())
	{
		// Search — migrated to huma (see RegisterSearchRoutes above).

		// Makers — migrated to huma (see RegisterMakerRoutes above).

		// Consoles — migrated to huma (see RegisterConsoleRoutes above). Covers
		// the list, paginated games, top-rated cache, and both global + per-
		// console top-list paths.

		// Top Lists — migrated to huma as part of RegisterConsoleRoutes above.

		// Explore — all endpoints migrated to huma. See:
		//   RegisterExploreFeaturedRoutes   (featured/rows/series/moods/surprise)
		//   RegisterExploreForYouRoutes     (for-you/players-like-you)
		//   RegisterExploreDeveloperRoutes  (developers/publishers detail + spotlight)
		//   RegisterExploreConsoleRoutes    (console showcase + highlights)
		//   RegisterExploreGalleryRoutes    (screenshots/artwork/covers)
		//   RegisterExploreCommunityRoutes  (trending/community-top/cult-classics/recently-reviewed/active-now)
		//   RegisterExploreTemporalRoutes   (on-this-day/best-of-year/your-anniversaries/decades)
		//   RegisterExploreChallengeRoutes  (easy-to-complete/hardest-games/almost-done/fresh-challenges/active-challenges)
		//   RegisterExploreWizardRoutes     (wizard + wizard/results)

		// Games — fully migrated to huma. JSON endpoints in RegisterGameRoutes
		// above; download + tar/zip streaming endpoints in RegisterDownloadRoutes
		// above (downloadGame / downloadGameWithFilename / downloadGameDisc).
		// /games/:id/artwork — migrated to huma (see RegisterArtworkRoutes above).
		// /games/:id/similar + /games/:id/developer-games — migrated to huma
		// (see RegisterDiscoveryRoutes above).
		// /games/:id/series + /games/:id/franchises — migrated to huma
		// (see RegisterEnrichmentRoutes above).

		// Game Sessions — fully migrated to huma. JSON + multipart uploads in
		// RegisterSessionRoutes / RegisterSessionSaveUploadRoutes above;
		// binary downloads in RegisterDownloadRoutes above.

		// Ratings — migrated to huma (see RegisterRatingRoutes above).

		// Shared saves — fully migrated to huma (see RegisterSharedSaveRoutes
		// / RegisterSharedUploadRoutes / RegisterDownloadRoutes above).

		// Cores — fully migrated to huma (listCores, getRecommendedCore,
		// downloadCore in RegisterCoreRoutes / RegisterGameRoutes /
		// RegisterDownloadRoutes above).

		// BIOS files — fully migrated to huma (RegisterBiosRoutes for
		// metadata + RegisterDownloadRoutes for the binary serve).

		// Stats — most-played + most-active-players migrated to huma
		// (see RegisterStatsRoutes above). Heatmap endpoints migrated to
		// huma via RegisterUserExtraRoutes.

		// User
		// /user/profile (GET + PUT), /user/preferences (GET + PUT),
		// /user/password (PUT) — migrated to huma (see RegisterUserRoutes +
		// RegisterUserMutationRoutes above).
		// /user/stats, /user/play-stats, /user/play-heatmap, /user/recent
		// migrated to huma (see RegisterUserExtraRoutes above).
		// /user/taste-profile, /user/explorer-badges, /user/completionist-map —
		// migrated to huma (see RegisterProfileRoutes above).
		// /user/favorites (GET + POST + DELETE) — migrated to huma
		// (see RegisterFavoriteRoutes above).

		// Storage management — migrated to huma (see RegisterMiscRoutes above).

		// Per-game key mappings — migrated to huma (see RegisterUserExtraRoutes above).

		// Saved Searches — migrated to huma (see RegisterMiscRoutes above).

		// Play Later — read + add + remove + reorder migrated to huma
		// (see RegisterPlayLaterRoutes above).

		// Devices — migrated to huma (see RegisterDeviceRoutes above).

		// Collections — migrated to huma (see RegisterCollectionRoutes above).

		// Shared Sessions — fully migrated to huma
		// (RegisterSharedSessionRoutes / RegisterSharedUploadRoutes /
		// RegisterDownloadRoutes above).

		// Netplay — sessions + invite endpoints migrated to huma
		// (see RegisterNetplayRoutes above). Only the WebSocket upgrade stays
		// on raw gin.
		netplay := api.Group("/netplay")
		{
			netplay.GET("/sessions/:id/ws", netplayHandler.HandleWebSocket)
		}

		// Social — migrated to huma (see RegisterSocialRoutes and
		// RegisterSocialExtraRoutes above).
		// /users/:id/play-heatmap — migrated to huma (see RegisterUserExtraRoutes above).

		// Challenges — fully migrated to huma. JSON endpoints in
		// RegisterChallengeRoutes above; the multipart create upload in
		// RegisterChallengeCreateRoute above; save + screenshot downloads in
		// RegisterDownloadRoutes above.

		// Enrichment: Themes, Keywords, Series, Franchises — migrated to huma
		// (see RegisterEnrichmentRoutes above).

		// RetroAchievements — link/unlink/status/settings/token + per-game
		// progress/timeline/leaderboard + user recent/unlocked migrated to
		// huma (see RegisterRARoutes above). GetGameAchievements is
		// registered via RegisterGameAchievementsRoute above and uses huma's
		// dynamic-status pattern to return 200 for resolved data or 202 when
		// a fetch is queued.

		// Achievement Showcase — migrated to huma
		// (see RegisterAchievementShowcaseRoutes above).

		// Client error reporting — migrated to huma
		// (see RegisterUserExtraRoutes above).

		// Admin routes — all endpoints migrated to huma. The four multipart
		// uploads (bios, replace-rom, rom-hacks, uploads) live in
		// RegisterAdminMultipartRoutes; the JSON-only admin endpoints live in
		// RegisterAdminRoutes / RegisterAdminGameRoutes /
		// RegisterUploadAdminRoutes / RegisterScraperAdminRoutes /
		// RegisterCheatAdminRoutes / RegisterSystemEventRoutes /
		// RegisterDeviceRoutes / RegisterEnrichmentRoutes /
		// RegisterMiscRoutes above.

		// WebSocket
		api.GET("/ws", cfg.Hub.HandleWebSocket)
	}

	// Test-only endpoint for E2E test isolation (only registered when SPELA_TEST_MODE=true).
	// No auth required — the route is intentionally unauthenticated so E2E tests
	// can call it without first logging in.
	if cfg.TestMode {
		testHandler := &TestHandler{DB: cfg.DB}
		RegisterTestRoute(humaAPI, testHandler)
	}

	// Serve frontend static files when configured (unified single-container deployment)
	if cfg.FrontendDir != "" {
		r.NoRoute(serveFrontend(cfg.FrontendDir))
	}

	cleanup := func() {
		authLimiter.Close()
		refreshLimiter.Close()
		downloadLimiter.Close()
		uploadLimiter.Close()
		userLimiter.Close()
		imageLimiter.Close()
	}

	return r, cleanup
}

// serveFrontend returns a Gin handler that serves static files from the given
// directory with SPA fallback (unknown paths serve index.html). Hashed assets
// under /assets/ get long-lived cache headers.
func serveFrontend(frontendDir string) gin.HandlerFunc {
	fs := http.Dir(frontendDir)
	fileServer := http.FileServer(fs)

	// Issue #1118: lock the SPA file server to a containment-checked
	// resolution of frontendDir. filepath.Clean does not strip `..` from a
	// relative path, and the previous code stat'd one path while
	// http.FileServer resolved a different one — Gin's URL normalisation
	// happened to keep them in sync, but a routing or middleware change
	// could break the only barrier. Resolve once, prefix-check, refuse if
	// it escapes.
	absRoot, _ := filepath.Abs(frontendDir)

	return func(c *gin.Context) {
		reqPath := c.Request.URL.Path

		// Never intercept API or WebSocket routes
		if strings.HasPrefix(reqPath, "/api/") {
			c.JSON(http.StatusNotFound, ErrorResponse{Error: "not found"})
			return
		}

		// Try to serve the file directly
		filePath := filepath.Join(frontendDir, filepath.Clean(reqPath))
		absPath, absErr := filepath.Abs(filePath)
		if absErr != nil || (absPath != absRoot && !strings.HasPrefix(absPath, absRoot+string(filepath.Separator))) {
			// Resolved path escaped frontendDir — refuse outright,
			// don't fall through to SPA index.
			c.Status(http.StatusNotFound)
			return
		}
		if info, err := os.Stat(filePath); err == nil && !info.IsDir() {
			// Hashed assets get immutable cache headers
			if strings.HasPrefix(reqPath, "/assets/") {
				c.Header("Cache-Control", "public, immutable, max-age=604800")
			}
			fileServer.ServeHTTP(c.Writer, c.Request)
			return
		}

		// Static asset paths must resolve to real files — never fall through
		// to SPA. This prevents the SPA fallback from returning index.html
		// (200 OK) for missing files, which breaks EmulatorJS CDN fallback
		// (it checks for HTTP errors, not content type).
		if strings.HasPrefix(reqPath, "/emulatorjs/") || strings.HasPrefix(reqPath, "/assets/") {
			c.Status(http.StatusNotFound)
			return
		}

		// SPA fallback: serve index.html for any non-file path
		c.File(filepath.Join(frontendDir, "index.html"))
	}
}

// ImageHandler serves images with access control for save screenshots.
type ImageHandler struct {
	ImageDir  string
	JWTSecret string
	DB        *gorm.DB
	Scraper   *scraper.Scraper
}

// ServeImage serves image files. Paths under save-screenshots/ require auth + ownership.
func (h *ImageHandler) ServeImage(c *gin.Context) {
	reqPath := c.Param("filepath")
	// Strip leading slash from the wildcard param
	reqPath = strings.TrimPrefix(reqPath, "/")

	// Resolve the absolute path and ensure it stays within ImageDir.
	// Use EvalSymlinks to prevent symlink-based escapes.
	absImageDir, _ := filepath.Abs(h.ImageDir)
	if realDir, err := filepath.EvalSymlinks(absImageDir); err == nil {
		absImageDir = realDir
	}
	absPath, _ := filepath.Abs(filepath.Join(h.ImageDir, reqPath))
	if _, statErr := os.Stat(absPath); statErr == nil {
		realPath, err := filepath.EvalSymlinks(absPath)
		if err != nil {
			c.JSON(http.StatusForbidden, ErrorResponse{Error: "access denied"})
			return
		}
		absPath = realPath
	}
	if !strings.HasPrefix(absPath, absImageDir+string(filepath.Separator)) && absPath != absImageDir {
		c.JSON(http.StatusForbidden, ErrorResponse{Error: "access denied"})
		return
	}

	// Save screenshots require authentication and ownership check
	if strings.HasPrefix(reqPath, "save-screenshots/") {
		// Require auth (token from header or query param)
		var token string
		header := c.GetHeader("Authorization")
		if header != "" {
			hParts := strings.SplitN(header, " ", 2)
			if len(hParts) == 2 && hParts[0] == "Bearer" {
				token = hParts[1]
			}
		}
		if token == "" {
			token = c.Query("token")
		}
		if token == "" {
			c.JSON(http.StatusUnauthorized, ErrorResponse{Error: "authentication required"})
			return
		}

		claims, err := auth.ValidateAccessToken(token, h.JWTSecret)
		if err != nil {
			c.JSON(http.StatusUnauthorized, ErrorResponse{Error: "invalid or expired token"})
			return
		}

		// Reject revoked tokens and disabled/changed users (same checks as AuthMiddleware)
		if IsTokenBlacklisted(h.DB, token) {
			c.JSON(http.StatusUnauthorized, ErrorResponse{Error: "token has been revoked"})
			return
		}
		var user db.User
		if err := h.DB.Select("id", "disabled", "token_version").First(&user, claims.UserID).Error; err != nil {
			c.JSON(http.StatusUnauthorized, ErrorResponse{Error: "user not found"})
			return
		}
		if user.Disabled {
			c.JSON(http.StatusForbidden, ErrorResponse{Error: "account is disabled"})
			return
		}
		if claims.TokenVersion != user.TokenVersion {
			c.JSON(http.StatusUnauthorized, ErrorResponse{Error: "token has been invalidated"})
			return
		}

		parts := strings.SplitN(reqPath, "/", 4)

		// Legacy format: save-screenshots/user_{id}/...
		if len(parts) >= 2 && strings.HasPrefix(parts[1], "user_") {
			pathUserIDStr := strings.TrimPrefix(parts[1], "user_")
			pathUserID, parseErr := strconv.ParseUint(pathUserIDStr, 10, 64)
			if parseErr != nil {
				c.JSON(http.StatusForbidden, ErrorResponse{Error: "access denied"})
				return
			}
			if uint64(claims.UserID) != pathUserID && claims.Role != "admin" && claims.Role != "owner" {
				c.JSON(http.StatusForbidden, ErrorResponse{Error: "access denied"})
				return
			}
		} else if len(parts) >= 3 && parts[1] == "sessions" && strings.HasPrefix(parts[2], "session_") {
			// Session format: save-screenshots/sessions/session_{id}/...
			sessionIDStr := strings.TrimPrefix(parts[2], "session_")
			sessionID, parseErr := strconv.ParseUint(sessionIDStr, 10, 64)
			if parseErr != nil {
				c.JSON(http.StatusForbidden, ErrorResponse{Error: "access denied"})
				return
			}
			var session db.GameSession
			if err := h.DB.Select("id", "owner_id").First(&session, sessionID).Error; err != nil {
				c.JSON(http.StatusNotFound, ErrorResponse{Error: "session not found"})
				return
			}
			if uint64(claims.UserID) != uint64(session.OwnerID) && claims.Role != "admin" && claims.Role != "owner" {
				c.JSON(http.StatusForbidden, ErrorResponse{Error: "access denied"})
				return
			}
		} else {
			c.JSON(http.StatusForbidden, ErrorResponse{Error: "access denied"})
			return
		}
	}

	// If the file doesn't exist and it's a boxart-libretro.png, try on-demand
	// download from LibRetro (no rate limit). This handles games that haven't
	// been fully scraped yet — their CoverURL is set during scraping but the
	// image download may have failed or not happened.
	if _, err := os.Stat(absPath); os.IsNotExist(err) && strings.HasSuffix(reqPath, "/boxart-libretro.png") {
		if path := h.tryOnDemandLibRetroCover(reqPath); path != "" {
			newAbs, _ := filepath.Abs(filepath.Join(h.ImageDir, path))
			// Re-validate path stays within ImageDir after on-demand download
			if strings.HasPrefix(newAbs, absImageDir+string(filepath.Separator)) {
				absPath = newAbs
			}
		}
	}

	c.File(absPath)
}

// tryOnDemandLibRetroCover attempts to download a LibRetro cover on-demand
// when the requested image doesn't exist locally. Parses the request path
// to extract console abbreviation and game ID, looks up the game, and tries
// to download the cover from LibRetro.
// Returns the local path on success, or empty string on failure.
func (h *ImageHandler) tryOnDemandLibRetroCover(reqPath string) string {
	if h.Scraper == nil {
		return ""
	}

	// Parse path: {consoleAbbr}/{gameID}/boxart-libretro.png
	parts := strings.Split(reqPath, "/")
	if len(parts) < 3 {
		return ""
	}
	gameIDStr := parts[len(parts)-2]

	// Parse to uint to prevent SQL injection via GORM's First() footgun
	gameID, err := strconv.ParseUint(gameIDStr, 10, 64)
	if err != nil {
		return ""
	}

	var game db.Game
	if err := h.DB.Preload("Console").First(&game, gameID).Error; err != nil {
		return ""
	}

	consoleAbbr := strings.ToLower(game.Console.Abbreviation)
	subpath := fmt.Sprintf("%s/%d/boxart-libretro.png", consoleAbbr, gameID)

	system, ok := scraper.AbbreviationToLibRetro[strings.ToUpper(consoleAbbr)]
	if !ok {
		return ""
	}

	// tryDownloadImage is unexported; use downloadLibRetroImage via the scraper
	path := h.Scraper.DownloadLibRetroBoxart(system, game.Title, subpath)
	if path != "" {
		// Update the game's cover URL if it was empty
		if game.CoverURL == "" || game.LibRetroCoverURL == "" {
			updates := map[string]interface{}{"lib_retro_cover_url": path}
			if game.CoverURL == "" {
				updates["cover_url"] = path
			}
			h.DB.Model(&game).Updates(updates)
		}
		slog.Info("on-demand LibRetro cover downloaded", "game", game.Title, "path", path)
	}
	return path
}
