package scraper

import (
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/url"
	"sort"
	"time"

	"github.com/spela/server/internal/db"
)

const steamGridDBBaseURL = "https://www.steamgriddb.com/api/v2"

// SteamGridDBClient interacts with the SteamGridDB API v2.
type SteamGridDBClient struct {
	APIKey     string
	BaseURL    string
	HTTPClient *http.Client
}

// SteamGridDBImage represents an image returned by SteamGridDB.
type SteamGridDBImage struct {
	ID       int    `json:"id"`
	URL      string `json:"url"`
	Thumb    string `json:"thumb"`
	Width    int    `json:"width"`
	Height   int    `json:"height"`
	Style    string `json:"style"`
	Score    int    `json:"score"`
	Language string `json:"language"`
}

// steamGridDBSearchResult represents a game result from the search endpoint.
type steamGridDBSearchResult struct {
	ID   int    `json:"id"`
	Name string `json:"name"`
}

// steamGridDBResponse wraps the common API response structure.
type steamGridDBResponse[T any] struct {
	Success bool `json:"success"`
	Data    T    `json:"data"`
}

// NewSteamGridDBClient creates a new SteamGridDB API client.
func NewSteamGridDBClient(apiKey string) *SteamGridDBClient {
	return &SteamGridDBClient{
		APIKey:  apiKey,
		BaseURL: steamGridDBBaseURL,
		HTTPClient: &http.Client{
			Timeout: 15 * time.Second,
		},
	}
}

// doRequest performs an authenticated GET request to the SteamGridDB API.
func (c *SteamGridDBClient) doRequest(path string) ([]byte, error) {
	reqURL := c.BaseURL + path

	req, err := http.NewRequest("GET", reqURL, nil)
	if err != nil {
		return nil, fmt.Errorf("creating request: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+c.APIKey)

	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("executing request: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(io.LimitReader(resp.Body, 5*1024*1024))
	if err != nil {
		return nil, fmt.Errorf("reading response body: %w", err)
	}

	if resp.StatusCode == http.StatusNotFound {
		return nil, fmt.Errorf("not found: %s", path)
	}
	if resp.StatusCode == http.StatusUnauthorized {
		return nil, fmt.Errorf("unauthorized: invalid SteamGridDB API key")
	}
	if resp.StatusCode == http.StatusTooManyRequests {
		return nil, fmt.Errorf("rate limited by SteamGridDB")
	}
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("unexpected status %d: %s", resp.StatusCode, string(body))
	}

	return body, nil
}

// SearchGame searches SteamGridDB for a game by name and returns the best match game ID.
// The platform parameter is currently unused (search is name-based) but reserved for future filtering.
func (c *SteamGridDBClient) SearchGame(name string, platform string) (int, error) {
	encodedName := url.PathEscape(name)
	body, err := c.doRequest("/search/autocomplete/" + encodedName)
	if err != nil {
		return 0, fmt.Errorf("searching SteamGridDB for %q: %w", name, err)
	}

	var resp steamGridDBResponse[[]steamGridDBSearchResult]
	if err := json.Unmarshal(body, &resp); err != nil {
		return 0, fmt.Errorf("parsing search response: %w", err)
	}

	if !resp.Success || len(resp.Data) == 0 {
		return 0, fmt.Errorf("no results found for %q", name)
	}

	// Return the first (best) match
	return resp.Data[0].ID, nil
}

// GetHeroes returns hero images for a SteamGridDB game ID.
func (c *SteamGridDBClient) GetHeroes(gameID int) ([]SteamGridDBImage, error) {
	return c.getImages(fmt.Sprintf("/heroes/game/%d", gameID))
}

// GetGrids returns grid/cover images for a SteamGridDB game ID.
func (c *SteamGridDBClient) GetGrids(gameID int) ([]SteamGridDBImage, error) {
	return c.getImages(fmt.Sprintf("/grids/game/%d", gameID))
}

// GetLogos returns logo images for a SteamGridDB game ID.
func (c *SteamGridDBClient) GetLogos(gameID int) ([]SteamGridDBImage, error) {
	return c.getImages(fmt.Sprintf("/logos/game/%d", gameID))
}

// GetIcons returns icon images for a SteamGridDB game ID.
func (c *SteamGridDBClient) GetIcons(gameID int) ([]SteamGridDBImage, error) {
	return c.getImages(fmt.Sprintf("/icons/game/%d", gameID))
}

// getImages fetches images from a SteamGridDB endpoint and returns them sorted by score (highest first).
func (c *SteamGridDBClient) getImages(path string) ([]SteamGridDBImage, error) {
	body, err := c.doRequest(path)
	if err != nil {
		return nil, err
	}

	var resp steamGridDBResponse[[]SteamGridDBImage]
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, fmt.Errorf("parsing image response: %w", err)
	}

	if !resp.Success {
		return nil, fmt.Errorf("SteamGridDB returned unsuccessful response")
	}

	// Sort: English first, then by score descending
	images := resp.Data
	sort.Slice(images, func(i, j int) bool {
		iEn := images[i].Language == "en"
		jEn := images[j].Language == "en"
		if iEn != jEn {
			return iEn
		}
		return images[i].Score > images[j].Score
	})

	return images, nil
}

// bestImageURL returns the URL of the highest-scored image, or empty if none available.
func bestImageURL(images []SteamGridDBImage) string {
	if len(images) == 0 {
		return ""
	}
	return images[0].URL
}

// GetBestArtwork searches SteamGridDB for a game and fetches the highest-scored artwork
// of each type (hero, grid, logo, icon). Returns a GameArtwork with URLs populated.
// Returns nil if the game is not found on SteamGridDB.
func (c *SteamGridDBClient) GetBestArtwork(name string, platform string) (*db.GameArtwork, error) {
	gameID, err := c.SearchGame(name, platform)
	if err != nil {
		return nil, fmt.Errorf("searching game: %w", err)
	}

	artwork := &db.GameArtwork{
		SteamGridDBID: gameID,
	}

	// Fetch all image types, continuing on individual failures
	if heroes, err := c.GetHeroes(gameID); err != nil {
		slog.Debug("SteamGridDB: failed to fetch heroes", "gameId", gameID, "error", err)
	} else {
		artwork.HeroURL = bestImageURL(heroes)
	}

	if grids, err := c.GetGrids(gameID); err != nil {
		slog.Debug("SteamGridDB: failed to fetch grids", "gameId", gameID, "error", err)
	} else {
		artwork.GridURL = bestImageURL(grids)
	}

	if logos, err := c.GetLogos(gameID); err != nil {
		slog.Debug("SteamGridDB: failed to fetch logos", "gameId", gameID, "error", err)
	} else {
		artwork.LogoURL = bestImageURL(logos)
	}

	if icons, err := c.GetIcons(gameID); err != nil {
		slog.Debug("SteamGridDB: failed to fetch icons", "gameId", gameID, "error", err)
	} else {
		artwork.IconURL = bestImageURL(icons)
	}

	return artwork, nil
}
