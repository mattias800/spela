package api

import (
	"fmt"
	"log/slog"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"math"
	"regexp"
	"sort"
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
	if err := h.DB.Find(&consoles).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch consoles"})
		return
	}

	// Attach game counts
	for i := range consoles {
		var count int64
		h.DB.Model(&db.Game{}).Where("console_id = ?", consoles[i].ID).Count(&count)
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

// ListConsoleGames returns games for a specific console as a flat Game array.
func (h *ConsoleHandler) ListConsoleGames(c *gin.Context) {
	consoleID := c.Param("id")

	var console db.Console
	if err := h.DB.Where("LOWER(abbreviation) = LOWER(?)", consoleID).First(&console).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "console not found"})
		return
	}

	var games []db.Game
	if err := h.DB.Where("console_id = ?", console.ID).
		Preload("Console").
		Order("title asc").
		Find(&games).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch games"})
		return
	}

	userID := getUserID(c)
	c.JSON(http.StatusOK, ToGameResponses(games, h.DB, userID))
}

// GetPreviewScreenshot returns a representative screenshot for a console.
// It serves a canonical screenshot from the LibRetro thumbnails CDN,
// cached locally after the first download.
func (h *ConsoleHandler) GetPreviewScreenshot(c *gin.Context) {
	consoleID := c.Param("id")

	var console db.Console
	if err := h.DB.Where("LOWER(abbreviation) = LOWER(?)", consoleID).First(&console).Error; err != nil {
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
	if err := h.DB.Where("LOWER(abbreviation) = LOWER(?)", consoleID).First(&console).Error; err != nil {
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
	if err := h.DB.Where("LOWER(abbreviation) = LOWER(?)", consoleID).First(&console).Error; err != nil {
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
	if err := h.DB.Where("LOWER(abbreviation) = LOWER(?)", consoleID).First(&console).Error; err != nil {
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
	Rating      float64 `json:"rating"`
	LocalGameId *string `json:"localGameId"`
}

// topRatedStaleness is how long cached top-rated data is considered fresh.
const topRatedStaleness = 7 * 24 * time.Hour

// GetTopRated returns the top-rated IGDB games for a console.
// Results are cached in the local DB and refreshed when stale (>7 days).
func (h *ConsoleHandler) GetTopRated(c *gin.Context) {
	consoleID := c.Param("id")

	var console db.Console
	if err := h.DB.Where("LOWER(abbreviation) = LOWER(?)", consoleID).First(&console).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "console not found"})
		return
	}

	abbr := strings.ToUpper(console.Abbreviation)
	igdbPlatformID, ok := igdb.AbbreviationToIGDBPlatform[abbr]
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
		topGames, err := h.Scraper.IGDBClient.GetTopGames(igdbPlatformID, 100)
		if err != nil {
			slog.Warn("failed to fetch top-rated games from IGDB", "console", abbr, "error", err)
			// Fall through to serve stale data if available
		} else {
			ranked := bayesianRank(topGames, 25)
			h.upsertTopRatedGames(console.ID, ranked)
			// Re-read from DB to get consistent data
			h.DB.Where("console_id = ?", console.ID).Order("rank asc").Find(&cached)
		}
	}

	// Cross-reference with local games
	result := make([]TopRatedGameResponse, 0, len(cached))
	for _, tr := range cached {
		coverUrl := ""
		if tr.CoverImageID != "" {
			coverUrl = igdb.ImageURL(tr.CoverImageID, "cover_big")
		}

		resp := TopRatedGameResponse{
			Rank:     tr.Rank,
			Name:     tr.Name,
			CoverUrl: coverUrl,
			Rating:   tr.TotalRating,
		}

		// Check for local game match by case-insensitive title
		var localGame db.Game
		if err := h.DB.Where("console_id = ? AND LOWER(title) = LOWER(?)", console.ID, tr.Name).First(&localGame).Error; err == nil {
			id := fmt.Sprintf("%d", localGame.ID)
			resp.LocalGameId = &id
		}

		result = append(result, resp)
	}

	c.JSON(http.StatusOK, result)
}

// GetTopRatedGlobal returns the top 20 top-rated IGDB games across all consoles.
// Results are pulled from the existing cached top-rated data and sorted by rating.
func (h *ConsoleHandler) GetTopRatedGlobal(c *gin.Context) {
	var cached []db.TopRatedGame
	h.DB.Order("total_rating desc").Limit(20).Find(&cached)

	result := make([]TopRatedGameResponse, 0, len(cached))
	for i, tr := range cached {
		coverUrl := ""
		if tr.CoverImageID != "" {
			coverUrl = igdb.ImageURL(tr.CoverImageID, "cover_big")
		}

		resp := TopRatedGameResponse{
			Rank:     i + 1,
			Name:     tr.Name,
			CoverUrl: coverUrl,
			Rating:   tr.TotalRating,
		}

		// Check for local game match by case-insensitive title, scoped to the same console
		var localGame db.Game
		if err := h.DB.Where("LOWER(title) = LOWER(?) AND console_id = ?", tr.Name, tr.ConsoleID).First(&localGame).Error; err == nil {
			id := fmt.Sprintf("%d", localGame.ID)
			resp.LocalGameId = &id
		}

		result = append(result, resp)
	}

	c.JSON(http.StatusOK, result)
}

// upsertTopRatedGames inserts or updates top-rated games for a console.
func (h *ConsoleHandler) upsertTopRatedGames(consoleID uint, games []igdb.TopGame) {
	for i, g := range games {
		coverImageID := ""
		if g.Cover != nil {
			coverImageID = g.Cover.ImageID
		}

		tr := db.TopRatedGame{
			ConsoleID:        consoleID,
			IGDBGameID:       g.ID,
			Name:             g.Name,
			CoverImageID:     coverImageID,
			TotalRating:      g.TotalRating,
			TotalRatingCount: g.TotalRatingCount,
			Rank:             i + 1,
		}

		// Upsert: update if exists, create if not
		var existing db.TopRatedGame
		err := h.DB.Where("console_id = ? AND igdb_game_id = ?", consoleID, g.ID).First(&existing).Error
		if err == nil {
			h.DB.Model(&existing).Updates(map[string]interface{}{
				"name":               tr.Name,
				"cover_image_id":     tr.CoverImageID,
				"total_rating":       tr.TotalRating,
				"total_rating_count": tr.TotalRatingCount,
				"rank":               tr.Rank,
			})
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
