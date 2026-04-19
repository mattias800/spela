package api

import (
	"context"
	"fmt"
	"log/slog"
	"math"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
	"time"

	"github.com/danielgtaylor/huma/v2"
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

// resolvePreviewScreenshotPath returns the /api/images/... redirect path for
// a console's canonical LibRetro screenshot, downloading + caching it from
// the LibRetro thumbnails CDN the first time. Used by the huma download
// handler in huma_downloads.go; the gin handler has been removed.
func (h *ConsoleHandler) resolvePreviewScreenshotPath(_ context.Context, consoleID string) (string, error) {
	var console db.Console
	if err := h.DB.Where("LOWER(abbreviation) = LOWER(?) OR code = ?", consoleID, consoleID).First(&console).Error; err != nil {
		return "", huma.Error404NotFound("console not found")
	}

	cachedPath := filepath.Join("previews", console.Abbreviation, "preview.png")
	fullCachedPath := h.Storage.ImagePath(cachedPath)
	if _, err := os.Stat(fullCachedPath); err == nil {
		return "/api/images/" + cachedPath, nil
	}

	libRetroSystem, ok := scraper.AbbreviationToLibRetro[console.Abbreviation]
	if !ok {
		return "", huma.Error404NotFound("no preview available for this console")
	}
	fallbackGame, ok := previewFallbackGames[console.Abbreviation]
	if !ok {
		return "", huma.Error404NotFound("no preview available for this console")
	}

	imageURL := fmt.Sprintf(
		"https://thumbnails.libretro.com/%s/Named_Snaps/%s.png",
		url.PathEscape(libRetroSystem),
		url.PathEscape(fallbackGame),
	)
	slog.Info("downloading preview screenshot from CDN", "console", console.Abbreviation, "url", imageURL)

	httpClient := &http.Client{Timeout: 15 * time.Second}
	resp, err := httpClient.Get(imageURL)
	if err != nil {
		slog.Warn("failed to download preview screenshot", "console", console.Abbreviation, "error", err)
		return "", huma.Error404NotFound("failed to download preview")
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		slog.Warn("CDN returned non-200 for preview", "console", console.Abbreviation, "status", resp.StatusCode)
		return "", huma.Error404NotFound("preview not available from CDN")
	}

	savedPath, err := h.Storage.WriteImage(cachedPath, resp.Body)
	if err != nil {
		slog.Warn("failed to cache preview screenshot", "console", console.Abbreviation, "error", err)
		return "", huma.Error500InternalServerError("failed to cache preview")
	}
	return "/api/images/" + savedPath, nil
}


// GetConsoleIcon has been migrated to huma — see HumaGetConsoleIcon in
// huma_downloads.go.

// GetConsoleLogo has been migrated to huma — see HumaGetConsoleLogo in
// huma_downloads.go.

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

// GetConsoleLogoPng has been migrated to huma — see HumaGetConsoleLogoPng in
// huma_downloads.go.

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
