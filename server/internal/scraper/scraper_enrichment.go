package scraper

import (
	"fmt"
	"log/slog"
	"strconv"
	"strings"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/igdb"
)

func (s *Scraper) enrichGameMetadata(game *db.Game, igdbGameID int) {
	if s.IGDBClient == nil || !s.IGDBClient.IsConfigured() {
		return
	}

	enrichment, err := s.IGDBClient.GetGameEnrichment(igdbGameID)
	if err != nil {
		slog.Warn("IGDB enrichment failed, skipping", "game", game.Title, "igdbId", igdbGameID, "error", err)
		return
	}
	if enrichment == nil {
		return
	}

	s.storeEnrichmentData(game, enrichment, igdbGameID)
}

// storeEnrichmentData persists enrichment data to the DB, replacing any existing data.
// All deletes and inserts are wrapped in a single transaction to reduce SQLite fsync overhead.
func (s *Scraper) storeEnrichmentData(game *db.Game, enrichment *igdb.GameEnrichment, igdbGameID int) {
	tx := s.DB.Begin()
	defer func() {
		if r := recover(); r != nil {
			tx.Rollback()
		}
	}()

	// Delete old enrichment data to prevent duplicates on re-scrape
	tx.Where("game_id = ?", game.ID).Delete(&db.GameTheme{})
	tx.Where("game_id = ?", game.ID).Delete(&db.GameKeyword{})
	tx.Where("game_id = ?", game.ID).Delete(&db.GamePlayerPerspective{})
	tx.Where("game_id = ?", game.ID).Delete(&db.GameFranchise{})
	tx.Where("game_id = ?", game.ID).Delete(&db.GameArtworkImage{})
	tx.Where("game_id = ?", game.ID).Delete(&db.GameVideo{})
	tx.Where("game_id = ?", game.ID).Delete(&db.GameLanguageSupport{})
	tx.Where("game_id = ?", game.ID).Delete(&db.GameAgeRating{})

	// Batch insert themes
	if len(enrichment.Themes) > 0 {
		themes := make([]db.GameTheme, 0, len(enrichment.Themes))
		for _, t := range enrichment.Themes {
			themes = append(themes, db.GameTheme{
				GameID:      game.ID,
				IGDBThemeID: t.ID,
				Name:        t.Name,
			})
		}
		tx.CreateInBatches(&themes, 100)
	}

	// Batch insert keywords
	if len(enrichment.Keywords) > 0 {
		keywords := make([]db.GameKeyword, 0, len(enrichment.Keywords))
		for _, k := range enrichment.Keywords {
			keywords = append(keywords, db.GameKeyword{
				GameID:        game.ID,
				IGDBKeywordID: k.ID,
				Name:          k.Name,
			})
		}
		tx.CreateInBatches(&keywords, 100)
	}

	// Batch insert player perspectives
	if len(enrichment.PlayerPerspectives) > 0 {
		perspectives := make([]db.GamePlayerPerspective, 0, len(enrichment.PlayerPerspectives))
		for _, p := range enrichment.PlayerPerspectives {
			perspectives = append(perspectives, db.GamePlayerPerspective{
				GameID:            game.ID,
				IGDBPerspectiveID: p.ID,
				Name:              p.Name,
			})
		}
		tx.CreateInBatches(&perspectives, 100)
	}

	// Store franchises (reuse name from existing DB entries when possible)
	for _, fID := range enrichment.Franchises {
		var franchiseName string
		var existing db.GameFranchise
		if err := s.DB.Where("igdb_franchise_id = ?", fID).First(&existing).Error; err == nil {
			// Reuse the cached franchise name
			franchiseName = existing.FranchiseName
			tx.Create(&db.GameFranchise{
				GameID:          game.ID,
				IGDBFranchiseID: fID,
				FranchiseName:   franchiseName,
			})
		} else if s.IGDBClient != nil {
			// Fetch franchise name from IGDB
			franchise, fetchErr := s.IGDBClient.GetFranchise(fID)
			if fetchErr != nil {
				slog.Warn("failed to fetch franchise from IGDB", "franchiseId", fID, "error", fetchErr)
				continue
			}
			if franchise != nil {
				franchiseName = franchise.Name
				tx.Create(&db.GameFranchise{
					GameID:          game.ID,
					IGDBFranchiseID: fID,
					FranchiseName:   franchiseName,
				})
			}
		}
		if franchiseName != "" {
			s.handleFranchiseForGame(game, fID, franchiseName)
		}
	}

	// Store artworks — download images outside the transaction, insert records inside.
	var artworkConsole db.Console
	s.DB.First(&artworkConsole, game.ConsoleID)
	consoleAbbr := strings.ToLower(artworkConsole.Abbreviation)
	gameIDStr := strconv.FormatUint(uint64(game.ID), 10)

	for _, a := range enrichment.Artworks {
		if a.ImageID == "" {
			continue
		}
		localPath := ""
		artworkURL := igdb.ImageURL(a.ImageID, "screenshot_big")
		subpath := fmt.Sprintf("%s/%s/artwork_%s.jpg", consoleAbbr, gameIDStr, a.ImageID)
		if path := s.DownloadExternalImage(artworkURL, subpath); path != "" {
			localPath = path
		}
		tx.Create(&db.GameArtworkImage{
			GameID:      game.ID,
			IGDBImageID: a.ImageID,
			LocalPath:   localPath,
			Width:       a.Width,
			Height:      a.Height,
		})
	}

	// Batch insert videos
	if len(enrichment.Videos) > 0 {
		videos := make([]db.GameVideo, 0, len(enrichment.Videos))
		for _, v := range enrichment.Videos {
			if v.VideoID == "" {
				continue
			}
			videos = append(videos, db.GameVideo{
				GameID:  game.ID,
				VideoID: v.VideoID,
				Name:    v.Name,
			})
		}
		if len(videos) > 0 {
			tx.CreateInBatches(&videos, 100)
		}
	}

	// Batch insert language supports
	if len(enrichment.LanguageSupports) > 0 {
		langSupports := make([]db.GameLanguageSupport, 0, len(enrichment.LanguageSupports))
		for _, ls := range enrichment.LanguageSupports {
			if ls.Language.Name == "" || ls.LanguageSupportType.Name == "" {
				continue
			}
			langSupports = append(langSupports, db.GameLanguageSupport{
				GameID:      game.ID,
				Language:    ls.Language.Name,
				SupportType: ls.LanguageSupportType.Name,
			})
		}
		if len(langSupports) > 0 {
			tx.CreateInBatches(&langSupports, 100)
		}
	}

	// Batch insert age ratings
	if len(enrichment.AgeRatings) > 0 {
		ageRatings := make([]db.GameAgeRating, 0, len(enrichment.AgeRatings))
		for _, ar := range enrichment.AgeRatings {
			categoryName := igdb.AgeRatingCategoryName(ar.Category)
			label := igdb.AgeRatingLabel(ar.Category, ar.Rating)
			if categoryName == "" || label == "" {
				continue
			}
			ageRatings = append(ageRatings, db.GameAgeRating{
				GameID:   game.ID,
				Category: categoryName,
				Rating:   label,
			})
		}
		if len(ageRatings) > 0 {
			tx.CreateInBatches(&ageRatings, 100)
		}
	}

	// Commit the enrichment transaction (all deletes + inserts in one fsync)
	if err := tx.Commit().Error; err != nil {
		slog.Warn("failed to commit enrichment transaction", "game", game.Title, "error", err)
	}

	// Handle series (IGDB collection) — may involve external IGDB calls, runs outside tx
	if enrichment.CollectionID != nil {
		s.handleSeriesForGame(game, *enrichment.CollectionID)
	}
}

// handleSeriesForGame creates or reuses a GameSeries for the IGDB collection
// and links the current game to it via GameSeriesEntry.
func (s *Scraper) handleSeriesForGame(game *db.Game, igdbCollectionID int) {
	var series db.GameSeries
	if err := s.DB.Where("igdb_collection_id = ?", igdbCollectionID).First(&series).Error; err != nil {
		// Series doesn't exist yet — fetch from IGDB
		if s.IGDBClient == nil {
			return
		}
		collectionData, fetchErr := s.IGDBClient.GetCollection(igdbCollectionID)
		if fetchErr != nil {
			slog.Warn("failed to fetch collection from IGDB", "collectionId", igdbCollectionID, "error", fetchErr)
			return
		}
		if collectionData == nil {
			return
		}
		series = db.GameSeries{
			IGDBCollectionID: igdbCollectionID,
			Name:             collectionData.Name,
		}
		if err := s.DB.Create(&series).Error; err != nil {
			slog.Warn("failed to create game series", "name", collectionData.Name, "error", err)
			return
		}
	}

	// Create/update the entry for this local game
	igdbID := 0
	if _, parseErr := fmt.Sscanf(game.ScraperID, "igdb:%d", &igdbID); parseErr != nil || igdbID == 0 {
		return
	}

	var entry db.GameSeriesEntry
	result := s.DB.Where("series_id = ? AND igdb_game_id = ?", series.ID, igdbID).First(&entry)
	if result.Error != nil {
		// Create new entry
		gameID := game.ID
		s.DB.Create(&db.GameSeriesEntry{
			SeriesID:   series.ID,
			GameID:     &gameID,
			IGDBGameID: igdbID,
			Name:       game.Title,
		})
	} else {
		// Update existing entry to link to local game
		if entry.GameID == nil {
			gameID := game.ID
			s.DB.Model(&entry).Update("game_id", &gameID)
		}
	}
}

// handleFranchiseForGame creates or reuses a GameFranchiseGroup for the IGDB franchise
// and links the current game to it via GameFranchiseEntry.
func (s *Scraper) handleFranchiseForGame(game *db.Game, igdbFranchiseID int, franchiseName string) {
	var group db.GameFranchiseGroup
	if err := s.DB.Where("igdb_franchise_id = ?", igdbFranchiseID).First(&group).Error; err != nil {
		// Group doesn't exist yet — create it
		group = db.GameFranchiseGroup{
			IGDBFranchiseID: igdbFranchiseID,
			Name:            franchiseName,
		}
		if err := s.DB.Create(&group).Error; err != nil {
			slog.Warn("failed to create franchise group", "name", franchiseName, "error", err)
			return
		}
	}

	// Create/update the entry for this local game
	igdbID := 0
	if _, parseErr := fmt.Sscanf(game.ScraperID, "igdb:%d", &igdbID); parseErr != nil || igdbID == 0 {
		return
	}

	var entry db.GameFranchiseEntry
	result := s.DB.Where("franchise_group_id = ? AND igdb_game_id = ?", group.ID, igdbID).First(&entry)
	if result.Error != nil {
		// Create new entry
		gameID := game.ID
		s.DB.Create(&db.GameFranchiseEntry{
			FranchiseGroupID: group.ID,
			GameID:           &gameID,
			IGDBGameID:       igdbID,
			Name:             game.Title,
		})
	} else {
		// Update existing entry to link to local game
		if entry.GameID == nil {
			gameID := game.ID
			s.DB.Model(&entry).Update("game_id", &gameID)
		}
	}
}

// EnrichGameOnly fetches and stores enrichment data for a game that already has
// an IGDB match. Unlike ScrapeGame, it does NOT re-download cover art, screenshots,
// or any other basic metadata. Used by the backfill endpoint.
func (s *Scraper) EnrichGameOnly(game *db.Game) error {
	if s.IGDBClient == nil || !s.IGDBClient.IsConfigured() {
		return fmt.Errorf("IGDB client is not configured")
	}

	// Extract IGDB ID from scraper ID
	var igdbID int
	if _, err := fmt.Sscanf(game.ScraperID, "igdb:%d", &igdbID); err != nil || igdbID == 0 {
		return fmt.Errorf("game %q has no valid IGDB scraper ID: %s", game.Title, game.ScraperID)
	}

	enrichment, err := s.IGDBClient.GetGameEnrichment(igdbID)
	if err != nil {
		return fmt.Errorf("fetching enrichment for %q (igdb:%d): %w", game.Title, igdbID, err)
	}
	if enrichment == nil {
		return nil // game not found on IGDB — not an error
	}

	s.storeEnrichmentData(game, enrichment, igdbID)
	return nil
}

// PopulateSeriesEntries fetches all games in a series from IGDB and populates
// GameSeriesEntry rows (with GameID null for non-library games).
// This is called during backfill to power "You own 8 of 15 games" displays.
func (s *Scraper) PopulateSeriesEntries(series *db.GameSeries) error {
	if s.IGDBClient == nil || !s.IGDBClient.IsConfigured() {
		return fmt.Errorf("IGDB client is not configured")
	}

	collectionData, err := s.IGDBClient.GetCollection(series.IGDBCollectionID)
	if err != nil {
		return fmt.Errorf("fetching collection %d: %w", series.IGDBCollectionID, err)
	}
	if collectionData == nil || len(collectionData.GameIDs) == 0 {
		return nil
	}

	// Fetch game names and covers from IGDB
	gameInfos, err := s.IGDBClient.GetCollectionGames(collectionData.GameIDs)
	if err != nil {
		return fmt.Errorf("fetching collection game details: %w", err)
	}

	// Build a map of IGDB game ID -> local game ID
	var localGames []db.Game
	s.DB.Select("id, scraper_id").Where("scraper_id LIKE 'igdb:%'").Find(&localGames)
	localMap := make(map[int]uint) // igdbID -> local game ID
	for _, g := range localGames {
		var igdbID int
		if _, parseErr := fmt.Sscanf(g.ScraperID, "igdb:%d", &igdbID); parseErr == nil && igdbID > 0 {
			localMap[igdbID] = g.ID
		}
	}

	// Upsert entries
	for _, info := range gameInfos {
		var entry db.GameSeriesEntry
		result := s.DB.Where("series_id = ? AND igdb_game_id = ?", series.ID, info.ID).First(&entry)
		if result.Error != nil {
			// Create new entry
			newEntry := db.GameSeriesEntry{
				SeriesID:     series.ID,
				IGDBGameID:   info.ID,
				Name:         info.Name,
				CoverImageID: info.CoverImageID,
			}
			if localID, ok := localMap[info.ID]; ok {
				newEntry.GameID = &localID
			}
			s.DB.Create(&newEntry)
		} else {
			// Update existing entry
			updates := map[string]interface{}{
				"name":           info.Name,
				"cover_image_id": info.CoverImageID,
			}
			if localID, ok := localMap[info.ID]; ok {
				updates["game_id"] = localID
			}
			s.DB.Model(&entry).Updates(updates)
		}
	}

	return nil
}

// PopulateFranchiseEntries fetches all games in a franchise from IGDB and populates
// GameFranchiseEntry rows (with GameID null for non-library games).
func (s *Scraper) PopulateFranchiseEntries(group *db.GameFranchiseGroup) error {
	if s.IGDBClient == nil || !s.IGDBClient.IsConfigured() {
		return fmt.Errorf("IGDB client is not configured")
	}

	franchiseData, err := s.IGDBClient.GetFranchise(group.IGDBFranchiseID)
	if err != nil {
		return fmt.Errorf("fetching franchise %d: %w", group.IGDBFranchiseID, err)
	}
	if franchiseData == nil || len(franchiseData.GameIDs) == 0 {
		return nil
	}

	// Fetch game names and covers from IGDB (reuses the collection games endpoint)
	gameInfos, err := s.IGDBClient.GetCollectionGames(franchiseData.GameIDs)
	if err != nil {
		return fmt.Errorf("fetching franchise game details: %w", err)
	}

	// Build a map of IGDB game ID -> local game ID
	var localGames []db.Game
	s.DB.Select("id, scraper_id").Where("scraper_id LIKE 'igdb:%'").Find(&localGames)
	localMap := make(map[int]uint)
	for _, g := range localGames {
		var igdbID int
		if _, parseErr := fmt.Sscanf(g.ScraperID, "igdb:%d", &igdbID); parseErr == nil && igdbID > 0 {
			localMap[igdbID] = g.ID
		}
	}

	// Upsert entries
	for _, info := range gameInfos {
		var entry db.GameFranchiseEntry
		result := s.DB.Where("franchise_group_id = ? AND igdb_game_id = ?", group.ID, info.ID).First(&entry)
		if result.Error != nil {
			newEntry := db.GameFranchiseEntry{
				FranchiseGroupID: group.ID,
				IGDBGameID:       info.ID,
				Name:             info.Name,
				CoverImageID:     info.CoverImageID,
			}
			if localID, ok := localMap[info.ID]; ok {
				newEntry.GameID = &localID
			}
			s.DB.Create(&newEntry)
		} else {
			updates := map[string]interface{}{
				"name":           info.Name,
				"cover_image_id": info.CoverImageID,
			}
			if localID, ok := localMap[info.ID]; ok {
				updates["game_id"] = localID
			}
			s.DB.Model(&entry).Updates(updates)
		}
	}

	return nil
}
