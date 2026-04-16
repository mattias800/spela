package api

import (
	"fmt"
	"log/slog"
	"math"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/igdb"
	"github.com/spela/server/internal/scraper"
	"github.com/spela/server/internal/storage"
	"gorm.io/gorm"
)

// previewFallbackGames maps console abbreviations to well-known game titles
// used as fallback when no local game has a scraped screenshot.
var previewFallbackGames = map[string]string{
	"NES":    "Super Mario Bros. (World)",
	"SNES":   "Super Mario World (USA)",
	"GB":     "Super Mario Land (World)",
	"GBC":    "Pokemon - Crystal Version (USA)",
	"GBA":    "Pokemon - Emerald Version (USA, Europe)",
	"N64":    "Super Mario 64 (USA)",
	"NDS":    "New Super Mario Bros. (USA)",
	"SMS":    "Alex Kidd in Miracle World (USA, Europe)",
	"GEN":    "Sonic The Hedgehog (USA, Europe)",
	"SAT":    "Nights Into Dreams... (USA)",
	"PSX":    "Crash Bandicoot (USA)",
	"PSP":    "God of War - Chains of Olympus (USA)",
	"NEOGEO": "Metal Slug - Super Vehicle-001",
	"ARCADE": "Street Fighter II - The World Warrior (World 910522)",
	"PCE":    "Bonk's Adventure (USA)",
	"A26":    "Pitfall! - Pitfall Harry's Jungle Adventure (USA)",
	"GG":    "Sonic The Hedgehog (USA, Europe)",
	"SCD":   "Sonic CD (USA)",
	"32X":   "Knuckles' Chaotix (Japan, USA)",
	"DC":    "Sonic Adventure (USA)",
	"VB":    "Mario's Tennis (USA)",
	"3DS":   "Super Mario 3D Land",
	"GC":    "Super Smash Bros. Melee (USA)",
	"A52":   "Pac-Man (USA)",
	"A78":   "Ms. Pac-Man (USA)",
	"LYNX":  "California Games (USA, Europe)",
	"JAG":   "Tempest 2000 (World)",
	"NGP":   "SNK vs. Capcom - Card Fighters' Clash (USA, Europe)",
	"WS":    "Final Fantasy (Japan)",
	"PCFX":  "Zenki FX - Vajra Fight (Japan)",
	"CV":    "Donkey Kong (USA)",
	"PKMN":  "Pokemon Pinball Mini (USA, Europe)",
	"PS2":   "Grand Theft Auto - San Andreas (USA)",
	"C64":   "Boulder Dash (USA, Europe)",
	"DOS":   "DOOM (USA)",
	"AMIGA": "Lemmings (USA)",
	"PS3":   "The Last of Us (USA)",
	"WII":   "Super Mario Galaxy (USA)",
}

// ConsoleHandler handles console-related endpoints.
type ConsoleHandler struct {
	DB      *gorm.DB
	Storage *storage.Storage
	Scraper *scraper.Scraper
}

// ListConsoles returns all consoles with game counts.
func (h *ConsoleHandler) ListConsoles(c *gin.Context) {
	var consoles []db.Console
	if err := h.DB.Preload("HardwareMaker").Preload("MediaType").Preload("MediaType.Category").Order("generation ASC, name ASC").Find(&consoles).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch consoles"})
		return
	}

	// Attach game counts (only primary, non-pre-release games — matches what users see)
	for i := range consoles {
		var count int64
		h.DB.Model(&db.Game{}).Where("console_id = ? AND is_primary = ? AND is_pre_release = ?", consoles[i].ID, true, false).Count(&count)
		consoles[i].GameCount = int(count)
	}

	// Only return consoles that have at least one game
	result := make([]ConsoleResponse, 0, len(consoles))
	for _, con := range consoles {
		if con.GameCount > 0 {
			result = append(result, ToConsoleResponse(con))
		}
	}

	c.JSON(http.StatusOK, result)
}

// ListConsoleGames returns paginated games for a specific console.
func (h *ConsoleHandler) ListConsoleGames(c *gin.Context) {
	consoleID := c.Param("id")

	var console db.Console
	if err := h.DB.Where("LOWER(abbreviation) = LOWER(?) OR code = ?", consoleID, consoleID).First(&console).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "console not found"})
		return
	}

	query := h.DB.Where("console_id = ?", console.ID).Preload("Console")
	countQuery := h.DB.Model(&db.Game{}).Where("console_id = ?", console.ID)

	// --- Variant grouping (default: show only primaries) ---
	grouped := c.DefaultQuery("grouped", "true")
	if grouped == "true" {
		query = query.Where("is_primary = ?", true)
		countQuery = countQuery.Where("is_primary = ?", true)
	}

	// --- Hide pre-release (default: hide betas/protos/samples) ---
	hidePreRelease := c.DefaultQuery("hidePreRelease", "true")
	if hidePreRelease == "true" {
		query = query.Where("is_pre_release = ?", false)
		countQuery = countQuery.Where("is_pre_release = ?", false)
	}

	// --- Region filter ---
	if regionFilter := c.Query("region"); regionFilter != "" {
		regions := strings.Split(regionFilter, ",")
		var trimmed []string
		for _, r := range regions {
			r = strings.TrimSpace(r)
			if r != "" {
				trimmed = append(trimmed, r)
			}
		}
		if len(trimmed) > 0 {
			regionWhere := h.DB.Session(&gorm.Session{NewDB: true})
			countRegionWhere := h.DB.Session(&gorm.Session{NewDB: true})
			for i, r := range trimmed {
				cond := "LOWER(region) = LOWER(?) OR region LIKE ? ESCAPE '\\'"
				args := []interface{}{r, "%" + escapeLikePattern(r) + "%"}
				if i == 0 {
					regionWhere = regionWhere.Where(cond, args...)
					countRegionWhere = countRegionWhere.Where(cond, args...)
				} else {
					regionWhere = regionWhere.Or(cond, args...)
					countRegionWhere = countRegionWhere.Or(cond, args...)
				}
			}
			query = query.Where(regionWhere)
			countQuery = countQuery.Where(countRegionWhere)
		}
	}

	// --- Letter filter (alphabet quick-jump) ---
	if letter := c.Query("letter"); letter != "" {
		if letter == "#" {
			query = query.Where("title GLOB '[0-9]*'")
			countQuery = countQuery.Where("title GLOB '[0-9]*'")
		} else if len(letter) == 1 && ((letter[0] >= 'A' && letter[0] <= 'Z') || (letter[0] >= 'a' && letter[0] <= 'z')) {
			upper := strings.ToUpper(letter)
			lower := strings.ToLower(letter)
			query = query.Where("title LIKE ? ESCAPE '\\' OR title LIKE ? ESCAPE '\\'", lower+"%", upper+"%")
			countQuery = countQuery.Where("title LIKE ? ESCAPE '\\' OR title LIKE ? ESCAPE '\\'", lower+"%", upper+"%")
		}
	}

	// Search
	if search := c.Query("search"); search != "" {
		query = query.Where("title LIKE ? ESCAPE '\\'", "%"+escapeLikePattern(search)+"%")
		countQuery = countQuery.Where("title LIKE ? ESCAPE '\\'", "%"+escapeLikePattern(search)+"%")
	}

	// Sorting
	sortCol := c.DefaultQuery("sortBy", "title")
	sortOrder := c.DefaultQuery("sortOrder", "asc")
	allowedSorts := map[string]bool{"title": true, "created_at": true, "file_size": true, "rating": true}
	if !allowedSorts[sortCol] {
		sortCol = "title"
	}
	if sortOrder != "asc" && sortOrder != "desc" {
		sortOrder = "asc"
	}
	query = query.Order(sortCol + " " + sortOrder)

	// Pagination
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	perPage, _ := strconv.Atoi(c.DefaultQuery("pageSize", "50"))
	if page < 1 {
		page = 1
	}
	if perPage < 1 || perPage > 200 {
		perPage = 50
	}

	var total int64
	countQuery.Count(&total)

	offset := (page - 1) * perPage
	var games []db.Game
	if err := query.Offset(offset).Limit(perPage).Find(&games).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch games"})
		return
	}

	userID := getUserID(c)
	c.JSON(http.StatusOK, PaginatedResponse[GameResponse]{
		Data:     ToGameResponses(games, h.DB, userID),
		Total:    total,
		Page:     page,
		PageSize: perPage,
	})
}

// GetPreviewScreenshot returns a representative screenshot for a console.
// It serves a canonical screenshot from the LibRetro thumbnails CDN,
// cached locally after the first download.
func (h *ConsoleHandler) GetPreviewScreenshot(c *gin.Context) {
	consoleID := c.Param("id")

	var console db.Console
	if err := h.DB.Where("LOWER(abbreviation) = LOWER(?) OR code = ?", consoleID, consoleID).First(&console).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "console not found"})
		return
	}

	// Check for cached preview
	cachedPath := filepath.Join("previews", console.Abbreviation, "preview.png")
	fullCachedPath := h.Storage.ImagePath(cachedPath)
	if _, err := os.Stat(fullCachedPath); err == nil {
		c.Header("Cache-Control", "public, max-age=86400")
		c.Redirect(http.StatusFound, "/api/images/"+cachedPath)
		return
	}

	// Download from LibRetro CDN
	libRetroSystem, ok := scraper.AbbreviationToLibRetro[console.Abbreviation]
	if !ok {
		c.JSON(http.StatusNotFound, gin.H{"error": "no preview available for this console"})
		return
	}

	fallbackGame, ok := previewFallbackGames[console.Abbreviation]
	if !ok {
		c.JSON(http.StatusNotFound, gin.H{"error": "no preview available for this console"})
		return
	}

	imageURL := fmt.Sprintf("https://thumbnails.libretro.com/%s/Named_Snaps/%s.png",
		url.PathEscape(libRetroSystem),
		url.PathEscape(fallbackGame),
	)

	slog.Info("downloading preview screenshot from CDN", "console", console.Abbreviation, "url", imageURL)

	httpClient := &http.Client{Timeout: 15 * time.Second}
	resp, err := httpClient.Get(imageURL)
	if err != nil {
		slog.Warn("failed to download preview screenshot", "console", console.Abbreviation, "error", err)
		c.JSON(http.StatusNotFound, gin.H{"error": "failed to download preview"})
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		slog.Warn("CDN returned non-200 for preview", "console", console.Abbreviation, "status", resp.StatusCode)
		c.JSON(http.StatusNotFound, gin.H{"error": "preview not available from CDN"})
		return
	}

	// Cache the downloaded image
	savedPath, err := h.Storage.WriteImage(cachedPath, resp.Body)
	if err != nil {
		slog.Warn("failed to cache preview screenshot", "console", console.Abbreviation, "error", err)
		// Serve directly from CDN body if caching fails - but body is already consumed.
		// Re-download is expensive; return error.
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to cache preview"})
		return
	}

	c.Header("Cache-Control", "public, max-age=86400")
	c.Redirect(http.StatusFound, "/api/images/"+savedPath)
}


// GetConsoleIcon serves the embedded PNG icon for a console.
func (h *ConsoleHandler) GetConsoleIcon(c *gin.Context) {
	consoleID := c.Param("id")

	var console db.Console
	if err := h.DB.Where("LOWER(abbreviation) = LOWER(?) OR code = ?", consoleID, consoleID).First(&console).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "console not found"})
		return
	}

	filename := strings.ToLower(console.Abbreviation) + ".png"
	data, err := consoleIcons.ReadFile("static/console-icons/" + filename)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "icon not available for this console"})
		return
	}

	c.Header("Cache-Control", "public, max-age=604800")
	c.Data(http.StatusOK, "image/png", data)
}

// GetConsoleLogo serves the embedded SVG logo for a console.
func (h *ConsoleHandler) GetConsoleLogo(c *gin.Context) {
	consoleID := c.Param("id")

	var console db.Console
	if err := h.DB.Where("LOWER(abbreviation) = LOWER(?) OR code = ?", consoleID, consoleID).First(&console).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "console not found"})
		return
	}

	filename := strings.ToLower(console.Abbreviation) + ".svg"
	data, err := consoleLogos.ReadFile("static/console-logos/" + filename)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "logo not available for this console"})
		return
	}

	// Inline CSS styles so SVG renderers without CSS support (e.g. Coil on JVM) show colors.
	processed := inlineSvgStyles(string(data))

	c.Header("Cache-Control", "public, max-age=604800")
	c.Data(http.StatusOK, "image/svg+xml", []byte(processed))
}

// inlineSvgStyles parses CSS <style> blocks in SVGs and replaces class="stN"
// with direct SVG presentation attributes (fill="...", fill-rule="...", etc).
// This ensures SVG renderers without CSS support (e.g. Coil3 on JVM) show colors.
func inlineSvgStyles(svg string) string {
	// Extract class→CSS declarations from <style> blocks: .st0{fill:#FFFFFF;} etc.
	classRe := regexp.MustCompile(`\.([a-zA-Z][\w-]*)\s*\{([^}]+)\}`)
	styles := make(map[string]string) // class name → raw CSS declarations
	for _, m := range classRe.FindAllStringSubmatch(svg, -1) {
		styles[m[1]] = strings.TrimSpace(m[2])
	}
	if len(styles) == 0 {
		return svg
	}

	// Parse CSS declarations into SVG attributes: "fill:#FFF; fill-rule:evenodd" → `fill="#FFF" fill-rule="evenodd"`
	propRe := regexp.MustCompile(`([\w-]+)\s*:\s*([^;]+)`)

	// Replace class="stN" with direct SVG attributes.
	attrRe := regexp.MustCompile(`\bclass="([^"]+)"`)
	result := attrRe.ReplaceAllStringFunc(svg, func(match string) string {
		sub := attrRe.FindStringSubmatch(match)
		if len(sub) < 2 {
			return match
		}
		classes := strings.Fields(sub[1])
		var attrs []string
		for _, cls := range classes {
			css, ok := styles[cls]
			if !ok {
				continue
			}
			for _, prop := range propRe.FindAllStringSubmatch(css, -1) {
				attrs = append(attrs, fmt.Sprintf(`%s="%s"`, strings.TrimSpace(prop[1]), strings.TrimSpace(prop[2])))
			}
		}
		if len(attrs) == 0 {
			return match
		}
		return strings.Join(attrs, " ")
	})

	// Remove the <style> block since styles are now inline.
	styleBlockRe := regexp.MustCompile(`(?s)<style[^>]*>.*?</style>`)
	result = styleBlockRe.ReplaceAllString(result, "")

	return result
}

// GetConsoleLogoPng serves a pre-rendered PNG version of the console logo.
// Generated from SVGs via scripts/generate-logo-pngs.sh.
func (h *ConsoleHandler) GetConsoleLogoPng(c *gin.Context) {
	consoleID := c.Param("id")

	var console db.Console
	if err := h.DB.Where("LOWER(abbreviation) = LOWER(?) OR code = ?", consoleID, consoleID).First(&console).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "console not found"})
		return
	}

	filename := strings.ToLower(console.Abbreviation) + ".png"
	data, err := consoleLogosPng.ReadFile("static/console-logos-png/" + filename)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "logo not available for this console"})
		return
	}

	c.Header("Cache-Control", "public, max-age=604800")
	c.Data(http.StatusOK, "image/png", data)
}

// TopRatedGameResponse is the API response for a top-rated IGDB game.
type TopRatedGameResponse struct {
	Rank        int     `json:"rank"`
	Name        string  `json:"name"`
	CoverUrl    string  `json:"coverUrl"`
	IGDBCriticsRating float64 `json:"igdbCriticsRating"`
	LocalGameId *string `json:"localGameId"`
	ConsoleName string  `json:"consoleName"`
}

// topRatedStaleness is how long cached top-rated data is considered fresh.
const topRatedStaleness = 7 * 24 * time.Hour

// maxTimeToBeatSeconds caps time-to-beat values at ~10000 hours to filter out
// bogus IGDB data (e.g. World Cup 98 listed at 567890 hours).
const maxTimeToBeatSeconds = 10000 * 3600

// demoConsoleAbbreviations lists consoles that should be excluded from top lists.
var demoConsoleAbbreviations = []string{"ADEMO", "DDEMO"}

// GetTopRated returns the top-rated IGDB games for a console.
// Results are cached in the local DB and refreshed when stale (>7 days).
func (h *ConsoleHandler) GetTopRated(c *gin.Context) {
	consoleID := c.Param("id")

	var console db.Console
	if err := h.DB.Where("LOWER(abbreviation) = LOWER(?) OR code = ?", consoleID, consoleID).First(&console).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "console not found"})
		return
	}

	abbr := strings.ToUpper(console.Abbreviation)
	igdbPlatformIDs, ok := igdb.AbbreviationToIGDBPlatform[abbr]
	if !ok {
		c.JSON(http.StatusOK, []TopRatedGameResponse{})
		return
	}

	// Check local DB for cached top-rated games
	var cached []db.TopRatedGame
	h.DB.Where("console_id = ?", console.ID).Order("rank asc").Find(&cached)

	fresh := len(cached) > 0 && time.Since(cached[0].UpdatedAt) < topRatedStaleness

	// Lazily configure IGDB client if credentials are available but client isn't set up yet
	if h.Scraper != nil && h.Scraper.IGDBClient == nil {
		clientID, clientSecret := igdbCredentials(h.DB)
		if clientID != "" && clientSecret != "" {
			h.Scraper.IGDBClient = igdb.NewClient(clientID, clientSecret)
		}
	}

	if !fresh && h.Scraper != nil && h.Scraper.IGDBClient != nil && h.Scraper.IGDBClient.IsConfigured() {
		// Fetch a larger pool from IGDB, then apply Bayesian weighting to match
		// the IGDB website's ranking (which favors well-known, highly-rated games
		// over obscure titles with few but high ratings).
		topGames, err := h.Scraper.IGDBClient.GetTopGames(igdbPlatformIDs, 100)
		if err != nil {
			slog.Warn("failed to fetch top-rated games from IGDB", "console", abbr, "error", err)
			// Fall through to serve stale data if available
		} else {
			ranked := bayesianRank(topGames, 25)
			// Upsert in background — image downloads are slow, serve stale data now
			go h.upsertTopRatedGames(console.ID, ranked)
			// Don't re-read — serve existing cached data immediately
		}
	}

	c.JSON(http.StatusOK, h.buildTopRatedResponses(cached, false))
}

// GetTopRatedGlobal returns the top 20 top-rated IGDB games across all consoles.
// The same IGDB game can appear under multiple consoles (e.g. Super Metroid on
// SNES, Wii, 3DS). We deduplicate by igdb_game_id, preferring the console entry
// that has a matching local library game so the card shows as "available".
func (h *ConsoleHandler) GetTopRatedGlobal(c *gin.Context) {
	// Only consider consoles that have at least one library game.
	var libraryConsoleIDs []uint
	h.DB.Model(&db.Game{}).Where("deleted_at IS NULL").Distinct("console_id").Pluck("console_id", &libraryConsoleIDs)

	var all []db.TopRatedGame
	if len(libraryConsoleIDs) == 0 {
		c.JSON(http.StatusOK, []TopRatedGameResponse{})
		return
	}
	h.DB.Where("console_id IN ?", libraryConsoleIDs).Order("total_rating desc").Find(&all)

	// Build a set of (igdb_game_id → console_id) pairs that have a local game match,
	// so we can prefer those entries during dedup.
	localConsoles := make(map[int]uint) // igdb_game_id → preferred console_id
	for _, tr := range all {
		if _, seen := localConsoles[tr.IGDBGameID]; seen {
			continue
		}
		var count int64
		scraperID := fmt.Sprintf("igdb:%d", tr.IGDBGameID)
		h.DB.Model(&db.Game{}).Where(
			"(scraper_id = ? OR LOWER(title) = LOWER(?)) AND console_id = ?",
			scraperID, tr.Name, tr.ConsoleID,
		).Count(&count)
		if count > 0 {
			localConsoles[tr.IGDBGameID] = tr.ConsoleID
		}
	}

	// Deduplicate: for each igdb_game_id, prefer the entry whose console has a
	// local library match; otherwise keep the highest-rated (first encountered).
	best := make(map[int]db.TopRatedGame) // igdb_game_id → best entry
	order := make([]int, 0)               // insertion order for stable iteration
	for _, tr := range all {
		existing, seen := best[tr.IGDBGameID]
		if !seen {
			best[tr.IGDBGameID] = tr
			order = append(order, tr.IGDBGameID)
		} else if prefConsole, ok := localConsoles[tr.IGDBGameID]; ok && tr.ConsoleID == prefConsole && existing.ConsoleID != prefConsole {
			// Swap in the entry that matches the local library
			best[tr.IGDBGameID] = tr
		}
	}

	deduped := make([]db.TopRatedGame, 0, 20)
	for _, igdbID := range order {
		deduped = append(deduped, best[igdbID])
		if len(deduped) >= 20 {
			break
		}
	}

	c.JSON(http.StatusOK, h.buildTopRatedResponses(deduped, true))
}

// TopListGameResponse is the API response for a top-rated IGDB game that is
// available locally on the server.
type TopListGameResponse struct {
	Rank        int     `json:"rank"`
	GameId      string  `json:"gameId"`
	Name        string  `json:"name"`
	CoverUrl    string  `json:"coverUrl"`
	ConsoleName string  `json:"consoleName"`
	ConsoleId   string  `json:"consoleId"`
	IGDBCriticsRating float64 `json:"igdbCriticsRating"`
}

// GetTopListAvailable returns IGDB audience top-rated games that have a matching
// local game on the server, sorted by total rating descending, limited to 50 results.
// Uses total_rating (combined user+critic) rather than user_rating alone, since
// IGDB's user-only rating field is often null for games that do have ratings.
func (h *ConsoleHandler) GetTopListAvailable(c *gin.Context) {
	h.getTopListByRating(c, "top_rated_games.total_rating", "top_rated_games.total_rating > 0")
}

// GetTopListCritics returns IGDB critic top-rated games that have a matching
// local game on the server, sorted by critic rating descending, limited to 50 results.
func (h *ConsoleHandler) GetTopListCritics(c *gin.Context) {
	h.getTopListByRating(c, "top_rated_games.critic_rating", "top_rated_games.critic_rating > 0")
}

func (h *ConsoleHandler) getTopListByRating(c *gin.Context, ratingColumn string, ratingFilter string) {
	type row struct {
		GameID      uint
		Name        string
		CoverURL    string
		ConsoleName string
		ConsoleAbbr string
		Rating      float64
	}

	q := h.DB.
		Table("top_rated_games").
		Select(`MIN(games.id) AS game_id,
				top_rated_games.name AS name,
				MIN(games.cover_url) AS cover_url,
				consoles.name AS console_name,
				consoles.abbreviation AS console_abbr,
				`+ratingColumn+` AS rating`).
		Joins("JOIN games ON (games.scraper_id = ('igdb:' || CAST(top_rated_games.igdb_game_id AS TEXT)) OR LOWER(games.title) = LOWER(top_rated_games.name)) AND games.console_id = top_rated_games.console_id AND games.deleted_at IS NULL AND games.is_primary = true").
		Joins("JOIN consoles ON consoles.id = top_rated_games.console_id AND consoles.deleted_at IS NULL").
		Where("top_rated_games.deleted_at IS NULL AND "+ratingFilter).
		Where("consoles.abbreviation NOT IN ?", demoConsoleAbbreviations)

	// Per-console filter when accessed via /consoles/:id/top-lists/*
	if consoleParam := c.Param("id"); consoleParam != "" {
		q = q.Where("LOWER(consoles.abbreviation) = LOWER(?)", consoleParam)
	}

	var rows []row
	err := q.
		Group("top_rated_games.id, top_rated_games.name, consoles.name, consoles.abbreviation, "+ratingColumn).
		Order(ratingColumn + " DESC").
		Limit(50).
		Find(&rows).Error
	if err != nil {
		slog.Error("failed to fetch top list", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch top list"})
		return
	}

	result := make([]TopListGameResponse, len(rows))
	for i, r := range rows {
		result[i] = TopListGameResponse{
			Rank:        i + 1,
			GameId:      fmt.Sprintf("%d", r.GameID),
			Name:        r.Name,
			CoverUrl:    resolveImageURL(r.CoverURL),
			ConsoleName: r.ConsoleName,
			ConsoleId:   strings.ToLower(r.ConsoleAbbr),
			IGDBCriticsRating: r.Rating,
		}
	}

	c.JSON(http.StatusOK, result)
}

// LongestGameResponse is the API response for a game in the "longest games" top list.
type LongestGameResponse struct {
	Rank                 int    `json:"rank"`
	GameId               string `json:"gameId"`
	Name                 string `json:"name"`
	CoverUrl             string `json:"coverUrl"`
	ConsoleName          string `json:"consoleName"`
	ConsoleId            string `json:"consoleId"`
	TimeToBeatNormally   int    `json:"timeToBeatNormally"`
	TimeToBeatHastily    int    `json:"timeToBeatHastily"`
	TimeToBeatCompletely int    `json:"timeToBeatCompletely"`
}

// GetTopListLongest returns games sorted by time_to_beat_normally descending.
// Only games with a positive time_to_beat_normally value are included, limited to 50 results.
func (h *ConsoleHandler) GetTopListLongest(c *gin.Context) {
	type row struct {
		GameID               uint
		Title                string
		CoverURL             string
		ConsoleName          string
		ConsoleAbbr          string
		TimeToBeatNormally   int
		TimeToBeatHastily    int
		TimeToBeatCompletely int
	}

	var rows []row
	q := h.DB.
		Table("games").
		Select(`games.id AS game_id,
				games.title,
				games.cover_url,
				consoles.name AS console_name,
				consoles.abbreviation AS console_abbr,
				games.time_to_beat_normally,
				games.time_to_beat_hastily,
				games.time_to_beat_completely`).
		Joins("JOIN consoles ON consoles.id = games.console_id AND consoles.deleted_at IS NULL").
		Where("games.time_to_beat_normally > 0 AND games.time_to_beat_normally <= ? AND games.deleted_at IS NULL AND games.is_primary = true", maxTimeToBeatSeconds).
		Where("consoles.abbreviation NOT IN ?", demoConsoleAbbreviations)

	// Per-console filter when accessed via /consoles/:id/top-lists/*
	if consoleParam := c.Param("id"); consoleParam != "" {
		q = q.Where("LOWER(consoles.abbreviation) = LOWER(?)", consoleParam)
	}

	err := q.
		Order("games.time_to_beat_normally DESC").
		Limit(50).
		Find(&rows).Error
	if err != nil {
		slog.Error("failed to fetch longest games list", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch longest games list"})
		return
	}

	result := make([]LongestGameResponse, len(rows))
	for i, r := range rows {
		result[i] = LongestGameResponse{
			Rank:                 i + 1,
			GameId:               fmt.Sprintf("%d", r.GameID),
			Name:                 r.Title,
			CoverUrl:             resolveImageURL(r.CoverURL),
			ConsoleName:          r.ConsoleName,
			ConsoleId:            strings.ToLower(r.ConsoleAbbr),
			TimeToBeatNormally:   r.TimeToBeatNormally,
			TimeToBeatHastily:    r.TimeToBeatHastily,
			TimeToBeatCompletely: r.TimeToBeatCompletely,
		}
	}

	c.JSON(http.StatusOK, result)
}

// buildTopRatedResponses converts cached TopRatedGame rows into API responses,
// cross-referencing with local games for cover art and game IDs.
// When rerank is true, ranks are assigned by position (1-based) instead of
// using the stored rank — used by the global endpoint which sorts by rating.
func (h *ConsoleHandler) buildTopRatedResponses(cached []db.TopRatedGame, rerank bool) []TopRatedGameResponse {
	result := make([]TopRatedGameResponse, 0, len(cached))
	for i, tr := range cached {
		rank := tr.Rank
		if rerank {
			rank = i + 1
		}
		// Resolve console name
		var consoleName string
		var console db.Console
		if err := h.DB.Select("name").First(&console, tr.ConsoleID).Error; err == nil {
			consoleName = console.Name
		}

		resp := TopRatedGameResponse{
			Rank:        rank,
			Name:        tr.Name,
			IGDBCriticsRating: tr.TotalRating,
			ConsoleName: consoleName,
		}

		// Check for local game match — prefer scraper_id (stable IGDB FK),
		// fall back to title match for games scraped before scraper_id existed.
		var localGame db.Game
		scraperID := fmt.Sprintf("igdb:%d", tr.IGDBGameID)
		if err := h.DB.Where(
			"(scraper_id = ? OR LOWER(title) = LOWER(?)) AND console_id = ?",
			scraperID, tr.Name, tr.ConsoleID,
		).First(&localGame).Error; err == nil {
			id := fmt.Sprintf("%d", localGame.ID)
			resp.LocalGameId = &id
			if localGame.CoverURL != "" {
				resp.CoverUrl = resolveImageURL(localGame.CoverURL)
			}
		}
		// Fallback to locally downloaded top-rated cover
		if resp.CoverUrl == "" && tr.CoverLocalPath != "" {
			resp.CoverUrl = resolveImageURL(tr.CoverLocalPath)
		}
		// Fallback to IGDB cover URL if local download hasn't completed
		if resp.CoverUrl == "" && tr.CoverImageID != "" {
			resp.CoverUrl = igdb.ImageURL(tr.CoverImageID, "cover_big")
		}

		result = append(result, resp)
	}
	return result
}

// upsertTopRatedGames inserts or updates top-rated games for a console.
func (h *ConsoleHandler) upsertTopRatedGames(consoleID uint, games []igdb.TopGame) {
	for i, g := range games {
		coverImageID := ""
		if g.Cover != nil {
			coverImageID = g.Cover.ImageID
		}

		// Download cover image locally
		coverLocalPath := ""
		if coverImageID != "" && h.Scraper != nil {
			coverURL := igdb.ImageURL(coverImageID, "cover_big")
			subpath := fmt.Sprintf("top-rated/%d/cover.jpg", g.ID)
			coverLocalPath = h.Scraper.DownloadExternalImage(coverURL, subpath)
		}

		tr := db.TopRatedGame{
			ConsoleID:         consoleID,
			IGDBGameID:        g.ID,
			Name:              g.Name,
			CoverImageID:      coverImageID,
			CoverLocalPath:    coverLocalPath,
			TotalRating:       g.TotalRating,
			TotalRatingCount:  g.TotalRatingCount,
			UserRating:        g.UserRating,
			UserRatingCount:   g.UserRatingCount,
			CriticRating:      g.CriticRating,
			CriticRatingCount: g.CriticRatingCount,
			Rank:              i + 1,
		}

		// Upsert: update if exists, create if not
		var existing db.TopRatedGame
		err := h.DB.Where("console_id = ? AND igdb_game_id = ?", consoleID, g.ID).First(&existing).Error
		if err == nil {
			updates := map[string]interface{}{
				"name":                tr.Name,
				"cover_image_id":      tr.CoverImageID,
				"total_rating":        tr.TotalRating,
				"total_rating_count":  tr.TotalRatingCount,
				"user_rating":         tr.UserRating,
				"user_rating_count":   tr.UserRatingCount,
				"critic_rating":       tr.CriticRating,
				"critic_rating_count": tr.CriticRatingCount,
				"rank":                tr.Rank,
			}
			if coverLocalPath != "" {
				updates["cover_local_path"] = coverLocalPath
			}
			h.DB.Model(&existing).Updates(updates)
		} else {
			h.DB.Create(&tr)
		}
	}

	// Remove old entries that are no longer in the top list
	igdbIDs := make([]int, len(games))
	for i, g := range games {
		igdbIDs[i] = g.ID
	}
	if len(igdbIDs) > 0 {
		h.DB.Where("console_id = ? AND igdb_game_id NOT IN ?", consoleID, igdbIDs).Delete(&db.TopRatedGame{})
	}
}

// bayesianRank applies a Bayesian weighted rating to a pool of IGDB games and
// returns the top N. This matches the IGDB website's ranking behaviour, which
// penalises games with very few ratings even if their raw score is high.
//
// Formula: weighted = (v/(v+m)) * R + (m/(v+m)) * C
//   - v = number of votes for this game
//   - m = minimum votes required for a meaningful ranking (25th percentile)
//   - R = game's average rating
//   - C = mean rating across the entire pool
func bayesianRank(games []igdb.TopGame, topN int) []igdb.TopGame {
	if len(games) == 0 {
		return games
	}

	// Compute mean rating (C) across the pool
	var sumRating float64
	counts := make([]int, len(games))
	for i, g := range games {
		sumRating += g.TotalRating
		counts[i] = g.TotalRatingCount
	}
	C := sumRating / float64(len(games))

	// m = 25th percentile of rating counts — ensures only reasonably well-known
	// games can rank highly.
	sort.Ints(counts)
	m := float64(counts[len(counts)/4])
	if m < 10 {
		m = 10
	}

	type scored struct {
		game   igdb.TopGame
		weight float64
	}
	scored_games := make([]scored, len(games))
	for i, g := range games {
		v := float64(g.TotalRatingCount)
		R := g.TotalRating
		w := (v/(v+m))*R + (m/(v+m))*C
		scored_games[i] = scored{game: g, weight: w}
	}

	sort.Slice(scored_games, func(i, j int) bool {
		return scored_games[i].weight > scored_games[j].weight
	})

	n := int(math.Min(float64(topN), float64(len(scored_games))))
	result := make([]igdb.TopGame, n)
	for i := 0; i < n; i++ {
		result[i] = scored_games[i].game
	}
	return result
}

// getUserID extracts the authenticated user's ID from the context.
func getUserID(c *gin.Context) uint {
	userID, _ := c.Get("userId")
	uid, _ := userID.(uint)
	return uid
}
