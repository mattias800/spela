package api

import (
	"fmt"
	"log/slog"
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/igdb"
)

// BackfillImagesResponse is the result of a backfill-images operation.
type BackfillImagesResponse struct {
	ArtworkDownloaded  int `json:"artworkDownloaded"`
	TopRatedDownloaded int `json:"topRatedDownloaded"`
	SimilarDownloaded  int `json:"similarDownloaded"`
	GalleryDownloaded  int `json:"galleryDownloaded"`
	CompanyDownloaded  int `json:"companyDownloaded"`
	Errors             int `json:"errors"`
}

// BackfillImages downloads external image URLs (IGDB, SteamGridDB) and
// replaces them with locally cached copies. This is an admin-only endpoint
// intended for one-time migration after upgrading.
// POST /api/admin/backfill-images
func (h *AdminHandler) BackfillImages(c *gin.Context) {
	if h.Scraper == nil {
		c.JSON(http.StatusServiceUnavailable, gin.H{"error": "scraper not configured"})
		return
	}

	result := BackfillImagesResponse{}

	// 1. GameArtwork — SteamGridDB CDN URLs
	var artworks []db.GameArtwork
	h.DB.Where("hero_url LIKE 'http%' OR grid_url LIKE 'http%' OR logo_url LIKE 'http%' OR icon_url LIKE 'http%'").Find(&artworks)
	for _, a := range artworks {
		gameIDStr := fmt.Sprintf("%d", a.GameID)
		var game db.Game
		if err := h.DB.Preload("Console").First(&game, a.GameID).Error; err != nil {
			result.Errors++
			continue
		}
		consoleAbbr := strings.ToLower(game.Console.Abbreviation)
		updates := map[string]interface{}{}

		if strings.HasPrefix(a.HeroURL, "http") {
			if path := h.Scraper.DownloadExternalImage(a.HeroURL, fmt.Sprintf("%s/%s/artwork-hero.jpg", consoleAbbr, gameIDStr)); path != "" {
				updates["hero_url"] = path
				result.ArtworkDownloaded++
			}
		}
		if strings.HasPrefix(a.GridURL, "http") {
			if path := h.Scraper.DownloadExternalImage(a.GridURL, fmt.Sprintf("%s/%s/artwork-grid.jpg", consoleAbbr, gameIDStr)); path != "" {
				updates["grid_url"] = path
				result.ArtworkDownloaded++
			}
		}
		if strings.HasPrefix(a.LogoURL, "http") {
			if path := h.Scraper.DownloadExternalImage(a.LogoURL, fmt.Sprintf("%s/%s/artwork-logo.png", consoleAbbr, gameIDStr)); path != "" {
				updates["logo_url"] = path
				result.ArtworkDownloaded++
			}
		}
		if strings.HasPrefix(a.IconURL, "http") {
			if path := h.Scraper.DownloadExternalImage(a.IconURL, fmt.Sprintf("%s/%s/artwork-icon.png", consoleAbbr, gameIDStr)); path != "" {
				updates["icon_url"] = path
				result.ArtworkDownloaded++
			}
		}
		if len(updates) > 0 {
			h.DB.Model(&a).Updates(updates)
		}
	}

	// 2. TopRatedGame — download covers where CoverLocalPath is empty
	var topRated []db.TopRatedGame
	h.DB.Where("cover_image_id != '' AND (cover_local_path = '' OR cover_local_path IS NULL)").Find(&topRated)
	for _, tr := range topRated {
		coverURL := igdb.ImageURL(tr.CoverImageID, "cover_big")
		subpath := fmt.Sprintf("top-rated/%d/cover.jpg", tr.IGDBGameID)
		if path := h.Scraper.DownloadExternalImage(coverURL, subpath); path != "" {
			h.DB.Model(&tr).Update("cover_local_path", path)
			result.TopRatedDownloaded++
		}
	}

	// 3. SimilarGame — download covers where CoverLocalPath is empty
	var similar []db.SimilarGame
	h.DB.Where("cover_image_id != '' AND (cover_local_path = '' OR cover_local_path IS NULL)").Find(&similar)
	for _, sg := range similar {
		coverURL := igdb.ImageURL(sg.CoverImageID, "cover_big")
		subpath := fmt.Sprintf("similar/%d/%d/cover.jpg", sg.GameID, sg.IGDBGameID)
		if path := h.Scraper.DownloadExternalImage(coverURL, subpath); path != "" {
			h.DB.Model(&sg).Update("cover_local_path", path)
			result.SimilarDownloaded++
		}
	}

	// 4. GameArtworkImage — download where LocalPath is empty
	var artworkImages []db.GameArtworkImage
	h.DB.Where("igdb_image_id != '' AND (local_path = '' OR local_path IS NULL)").Preload("Game").Find(&artworkImages)
	// We need game+console info; batch lookup
	for i, ai := range artworkImages {
		var game db.Game
		if err := h.DB.Preload("Console").First(&game, ai.GameID).Error; err != nil {
			result.Errors++
			continue
		}
		consoleAbbr := strings.ToLower(game.Console.Abbreviation)
		artworkURL := igdb.ImageURL(ai.IGDBImageID, "screenshot_big")
		subpath := fmt.Sprintf("%s/%d/artwork_%d.jpg", consoleAbbr, game.ID, i)
		if path := h.Scraper.DownloadExternalImage(artworkURL, subpath); path != "" {
			h.DB.Model(&ai).Update("local_path", path)
			result.GalleryDownloaded++
		}
	}

	// 5. Company — download logos where LogoURL is external
	var companies []db.Company
	h.DB.Where("logo_url LIKE 'http%'").Find(&companies)
	for _, co := range companies {
		subpath := fmt.Sprintf("companies/%d/logo.png", co.IGDBCompanyID)
		if path := h.Scraper.DownloadExternalImage(co.LogoURL, subpath); path != "" {
			h.DB.Model(&co).Update("logo_url", path)
			result.CompanyDownloaded++
		}
	}

	slog.Info("backfill-images complete",
		"artwork", result.ArtworkDownloaded,
		"topRated", result.TopRatedDownloaded,
		"similar", result.SimilarDownloaded,
		"gallery", result.GalleryDownloaded,
		"company", result.CompanyDownloaded,
		"errors", result.Errors,
	)

	c.JSON(http.StatusOK, result)
}
