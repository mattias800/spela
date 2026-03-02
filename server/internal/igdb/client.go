package igdb

import (
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"
)

// API base URLs (variables for testability).
var (
	twitchTokenURL = "https://id.twitch.tv/oauth2/token"
	igdbAPIBase    = "https://api.igdb.com/v4"
	igdbImageBase  = "https://images.igdb.com/igdb/image/upload"
)

// AbbreviationToIGDBPlatform maps Spela console abbreviations to IGDB platform IDs.
var AbbreviationToIGDBPlatform = map[string]int{
	"NES":    18,
	"SNES":   19,
	"GB":     33,
	"GBC":    22,
	"GBA":    24,
	"N64":    4,
	"NDS":    20,
	"SMS":    64,
	"GEN":    29,
	"SAT":    32,
	"PSX":    7,
	"PSP":    38,
	"NEOGEO": 80,
	"PCE":    86,
	"A26":    59,
	"GG":     35,
	"SCD":    78,
	"32X":    30,
	"DC":     23,
	"VB":     87,
	"3DS":    37,
	"GC":     21,
	"PS2":    8,
	"C64":    15,
	"AMIGA":  16,
	"DOS":    13,
	"A52":    66,
	"A78":    60,
	"LYNX":   61,
	"JAG":    62,
	"NGP":    120,
	"WS":     57,
	"CV":     68,
	"ARCADE": 52,
}

// oauthToken holds a Twitch OAuth token with its expiration.
type oauthToken struct {
	AccessToken string
	ExpiresAt   time.Time
}

// Client is an IGDB API client with Twitch OAuth and rate limiting.
type Client struct {
	ClientID     string
	ClientSecret string
	HTTPClient   *http.Client

	mu    sync.Mutex
	token *oauthToken

	// Rate limiting: max 4 requests/second
	rateTicker  *time.Ticker
	rateLimiter <-chan time.Time
}

// NewClient creates a new IGDB client with the given credentials.
func NewClient(clientID, clientSecret string) *Client {
	ticker := time.NewTicker(250 * time.Millisecond) // 4 req/s
	return &Client{
		ClientID:     clientID,
		ClientSecret: clientSecret,
		HTTPClient: &http.Client{
			Timeout: 15 * time.Second,
		},
		rateTicker:  ticker,
		rateLimiter: ticker.C,
	}
}

// Close releases resources held by the client.
func (c *Client) Close() {
	if c.rateTicker != nil {
		c.rateTicker.Stop()
	}
}

// IsConfigured returns whether IGDB credentials are set.
func (c *Client) IsConfigured() bool {
	return c.ClientID != "" && c.ClientSecret != ""
}

// authenticate obtains or refreshes the OAuth token.
func (c *Client) authenticate() error {
	c.mu.Lock()
	defer c.mu.Unlock()

	// Token still valid (with 1 hour buffer)
	if c.token != nil && time.Now().Add(time.Hour).Before(c.token.ExpiresAt) {
		return nil
	}

	return c.fetchToken()
}

// fetchToken fetches a new OAuth token from Twitch. Must be called with mu held.
func (c *Client) fetchToken() error {
	params := url.Values{
		"client_id":     {c.ClientID},
		"client_secret": {c.ClientSecret},
		"grant_type":    {"client_credentials"},
	}

	resp, err := c.HTTPClient.PostForm(twitchTokenURL, params)
	if err != nil {
		return fmt.Errorf("requesting Twitch OAuth token: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return fmt.Errorf("reading Twitch OAuth response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("Twitch OAuth returned %d: %s", resp.StatusCode, string(body))
	}

	var tokenResp struct {
		AccessToken string `json:"access_token"`
		ExpiresIn   int    `json:"expires_in"`
		TokenType   string `json:"token_type"`
	}
	if err := json.Unmarshal(body, &tokenResp); err != nil {
		return fmt.Errorf("decoding Twitch OAuth response: %w", err)
	}

	c.token = &oauthToken{
		AccessToken: tokenResp.AccessToken,
		ExpiresAt:   time.Now().Add(time.Duration(tokenResp.ExpiresIn) * time.Second),
	}

	slog.Info("IGDB: obtained OAuth token", "expiresIn", tokenResp.ExpiresIn)
	return nil
}

// TestCredentials validates IGDB credentials by attempting a token exchange.
func (c *Client) TestCredentials(clientID, clientSecret string) error {
	params := url.Values{
		"client_id":     {clientID},
		"client_secret": {clientSecret},
		"grant_type":    {"client_credentials"},
	}

	resp, err := c.HTTPClient.PostForm(twitchTokenURL, params)
	if err != nil {
		return fmt.Errorf("connecting to Twitch: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("authentication failed (HTTP %d): %s", resp.StatusCode, string(body))
	}

	return nil
}

// Game represents an IGDB game search result.
type Game struct {
	ID                int              `json:"id"`
	Name              string           `json:"name"`
	Summary           string           `json:"summary"`
	Cover             *Image           `json:"cover"`
	Screenshots       []Image          `json:"screenshots"`
	Genres            []Genre          `json:"genres"`
	InvolvedCompanies []InvolvedCompany `json:"involved_companies"`
	FirstReleaseDate  int64            `json:"first_release_date"`
	AggregatedRating  float64          `json:"aggregated_rating"`
	GameModes         []GameMode       `json:"game_modes"`
}

// Image represents an IGDB image with an image_id for URL construction.
type Image struct {
	ID      int    `json:"id"`
	ImageID string `json:"image_id"`
}

// Genre represents an IGDB genre.
type Genre struct {
	ID   int    `json:"id"`
	Name string `json:"name"`
}

// InvolvedCompany represents a company's involvement with a game.
type InvolvedCompany struct {
	Company   Company `json:"company"`
	Developer bool    `json:"developer"`
	Publisher bool    `json:"publisher"`
}

// Company represents an IGDB company.
type Company struct {
	ID   int    `json:"id"`
	Name string `json:"name"`
}

// GameMode represents an IGDB game mode.
type GameMode struct {
	ID   int    `json:"id"`
	Name string `json:"name"`
}

// ImageURL constructs a full image URL from an image_id and size.
// Common sizes: t_thumb, t_cover_small, t_cover_big, t_screenshot_big, t_screenshot_huge
func ImageURL(imageID, size string) string {
	return fmt.Sprintf("%s/t_%s/%s.jpg", igdbImageBase, size, imageID)
}

// SearchGame searches IGDB for a game by name and platform.
func (c *Client) SearchGame(name string, platformID int) ([]Game, error) {
	if err := c.authenticate(); err != nil {
		return nil, fmt.Errorf("IGDB authentication: %w", err)
	}

	// Wait for rate limiter
	<-c.rateLimiter

	query := fmt.Sprintf(
		`search "%s"; fields name,summary,cover.image_id,screenshots.image_id,genres.name,involved_companies.company.name,involved_companies.developer,involved_companies.publisher,first_release_date,aggregated_rating,game_modes.name; where platforms = (%d); limit 5;`,
		escapeQuery(name), platformID,
	)

	slog.Info("IGDB search request", "name", name, "platformID", platformID, "query", query)

	c.mu.Lock()
	token := c.token.AccessToken
	c.mu.Unlock()

	req, err := http.NewRequest("POST", igdbAPIBase+"/games", strings.NewReader(query))
	if err != nil {
		return nil, fmt.Errorf("creating IGDB request: %w", err)
	}
	req.Header.Set("Client-ID", c.ClientID)
	req.Header.Set("Authorization", "Bearer "+token)

	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("calling IGDB API: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("reading IGDB response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("IGDB API returned %d: %s", resp.StatusCode, string(body))
	}

	var games []Game
	if err := json.Unmarshal(body, &games); err != nil {
		return nil, fmt.Errorf("decoding IGDB response: %w", err)
	}

	gameNames := make([]string, len(games))
	for i, g := range games {
		gameNames[i] = fmt.Sprintf("%s (id:%d)", g.Name, g.ID)
	}
	slog.Info("IGDB search response", "name", name, "resultCount", len(games), "results", gameNames)

	return games, nil
}

// SearchGameExact queries IGDB for a game by exact name match and platform.
// Uses a "where name" clause instead of the "search" keyword to avoid text-search
// relevance ranking which can omit the exact game (e.g. "Super Mario 64" text search
// returns the unreleased sequel but not the original).
func (c *Client) SearchGameExact(name string, platformID int) ([]Game, error) {
	if err := c.authenticate(); err != nil {
		return nil, fmt.Errorf("IGDB authentication: %w", err)
	}

	// Wait for rate limiter
	<-c.rateLimiter

	// Case-insensitive exact match using ~ operator
	query := fmt.Sprintf(
		`fields name,summary,cover.image_id,screenshots.image_id,genres.name,involved_companies.company.name,involved_companies.developer,involved_companies.publisher,first_release_date,aggregated_rating,game_modes.name; where name ~ "%s" & platforms = (%d); limit 5;`,
		escapeQuery(name), platformID,
	)

	slog.Info("IGDB exact search request", "name", name, "platformID", platformID, "query", query)

	c.mu.Lock()
	token := c.token.AccessToken
	c.mu.Unlock()

	req, err := http.NewRequest("POST", igdbAPIBase+"/games", strings.NewReader(query))
	if err != nil {
		return nil, fmt.Errorf("creating IGDB request: %w", err)
	}
	req.Header.Set("Client-ID", c.ClientID)
	req.Header.Set("Authorization", "Bearer "+token)

	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("calling IGDB API: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("reading IGDB response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("IGDB API returned %d: %s", resp.StatusCode, string(body))
	}

	var games []Game
	if err := json.Unmarshal(body, &games); err != nil {
		return nil, fmt.Errorf("decoding IGDB response: %w", err)
	}

	gameNames := make([]string, len(games))
	for i, g := range games {
		gameNames[i] = fmt.Sprintf("%s (id:%d)", g.Name, g.ID)
	}
	slog.Info("IGDB exact search response", "name", name, "resultCount", len(games), "results", gameNames)

	return games, nil
}

// GetGameByID fetches a single game from IGDB by its ID.
// Returns the full game data with the same fields as SearchGame.
// Returns nil if the game is not found.
func (c *Client) GetGameByID(igdbID int) (*Game, error) {
	if err := c.authenticate(); err != nil {
		return nil, fmt.Errorf("IGDB authentication: %w", err)
	}

	// Wait for rate limiter
	<-c.rateLimiter

	query := fmt.Sprintf(
		`fields name,summary,cover.image_id,screenshots.image_id,genres.name,involved_companies.company.name,involved_companies.developer,involved_companies.publisher,first_release_date,aggregated_rating,game_modes.name; where id = %d; limit 1;`,
		igdbID,
	)

	slog.Info("IGDB get game by ID request", "igdbID", igdbID, "query", query)

	c.mu.Lock()
	token := c.token.AccessToken
	c.mu.Unlock()

	req, err := http.NewRequest("POST", igdbAPIBase+"/games", strings.NewReader(query))
	if err != nil {
		return nil, fmt.Errorf("creating IGDB request: %w", err)
	}
	req.Header.Set("Client-ID", c.ClientID)
	req.Header.Set("Authorization", "Bearer "+token)

	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("calling IGDB API: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("reading IGDB response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("IGDB API returned %d: %s", resp.StatusCode, string(body))
	}

	var games []Game
	if err := json.Unmarshal(body, &games); err != nil {
		return nil, fmt.Errorf("decoding IGDB response: %w", err)
	}

	if len(games) == 0 {
		slog.Info("IGDB get game by ID: not found", "igdbID", igdbID)
		return nil, nil
	}

	slog.Info("IGDB get game by ID response", "igdbID", igdbID, "name", games[0].Name)
	return &games[0], nil
}

// TopGame represents an IGDB top-rated game result.
type TopGame struct {
	ID               int    `json:"id"`
	Name             string `json:"name"`
	Cover            *Image `json:"cover"`
	TotalRating      float64 `json:"total_rating"`
	TotalRatingCount int     `json:"total_rating_count"`
}

// GetTopGames fetches the top-rated games for a given IGDB platform.
func (c *Client) GetTopGames(platformID int, limit int) ([]TopGame, error) {
	if err := c.authenticate(); err != nil {
		return nil, fmt.Errorf("IGDB authentication: %w", err)
	}

	// Wait for rate limiter
	<-c.rateLimiter

	query := fmt.Sprintf(
		`fields name, cover.image_id, total_rating, total_rating_count; where platforms = (%d) & total_rating != null & total_rating_count > 5; sort total_rating desc; limit %d;`,
		platformID, limit,
	)

	slog.Info("IGDB top games request", "platformID", platformID, "limit", limit)

	c.mu.Lock()
	token := c.token.AccessToken
	c.mu.Unlock()

	req, err := http.NewRequest("POST", igdbAPIBase+"/games", strings.NewReader(query))
	if err != nil {
		return nil, fmt.Errorf("creating IGDB request: %w", err)
	}
	req.Header.Set("Client-ID", c.ClientID)
	req.Header.Set("Authorization", "Bearer "+token)

	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("calling IGDB API: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("reading IGDB response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("IGDB API returned %d: %s", resp.StatusCode, string(body))
	}

	var games []TopGame
	if err := json.Unmarshal(body, &games); err != nil {
		return nil, fmt.Errorf("decoding IGDB response: %w", err)
	}

	slog.Info("IGDB top games response", "platformID", platformID, "resultCount", len(games))

	return games, nil
}

// SimilarGame represents an IGDB similar game result.
type SimilarGame struct {
	ID          int     `json:"id"`
	Name        string  `json:"name"`
	Cover       *Image  `json:"cover"`
	TotalRating float64 `json:"total_rating"`
}

// GetSimilarGames fetches similar games for a given IGDB game ID.
func (c *Client) GetSimilarGames(igdbGameID int) ([]SimilarGame, error) {
	if err := c.authenticate(); err != nil {
		return nil, fmt.Errorf("IGDB authentication: %w", err)
	}

	// Wait for rate limiter
	<-c.rateLimiter

	// First, get the similar_games IDs from the source game
	query := fmt.Sprintf(
		`fields similar_games; where id = %d;`,
		igdbGameID,
	)

	slog.Info("IGDB similar games lookup", "igdbGameID", igdbGameID)

	c.mu.Lock()
	token := c.token.AccessToken
	c.mu.Unlock()

	req, err := http.NewRequest("POST", igdbAPIBase+"/games", strings.NewReader(query))
	if err != nil {
		return nil, fmt.Errorf("creating IGDB request: %w", err)
	}
	req.Header.Set("Client-ID", c.ClientID)
	req.Header.Set("Authorization", "Bearer "+token)

	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("calling IGDB API: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("reading IGDB response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("IGDB API returned %d: %s", resp.StatusCode, string(body))
	}

	var parentGames []struct {
		SimilarGames []int `json:"similar_games"`
	}
	if err := json.Unmarshal(body, &parentGames); err != nil {
		return nil, fmt.Errorf("decoding IGDB similar_games response: %w", err)
	}

	if len(parentGames) == 0 || len(parentGames[0].SimilarGames) == 0 {
		return nil, nil
	}

	// Fetch details for the similar game IDs
	<-c.rateLimiter

	ids := parentGames[0].SimilarGames
	if len(ids) > 20 {
		ids = ids[:20]
	}

	idStrs := make([]string, len(ids))
	for i, id := range ids {
		idStrs[i] = fmt.Sprintf("%d", id)
	}

	detailQuery := fmt.Sprintf(
		`fields name, cover.image_id, total_rating; where id = (%s); limit %d;`,
		strings.Join(idStrs, ","), len(ids),
	)

	c.mu.Lock()
	token = c.token.AccessToken
	c.mu.Unlock()

	req2, err := http.NewRequest("POST", igdbAPIBase+"/games", strings.NewReader(detailQuery))
	if err != nil {
		return nil, fmt.Errorf("creating IGDB detail request: %w", err)
	}
	req2.Header.Set("Client-ID", c.ClientID)
	req2.Header.Set("Authorization", "Bearer "+token)

	resp2, err := c.HTTPClient.Do(req2)
	if err != nil {
		return nil, fmt.Errorf("calling IGDB API for similar game details: %w", err)
	}
	defer resp2.Body.Close()

	body2, err := io.ReadAll(resp2.Body)
	if err != nil {
		return nil, fmt.Errorf("reading IGDB detail response: %w", err)
	}

	if resp2.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("IGDB API returned %d: %s", resp2.StatusCode, string(body2))
	}

	var games []SimilarGame
	if err := json.Unmarshal(body2, &games); err != nil {
		return nil, fmt.Errorf("decoding IGDB similar games detail: %w", err)
	}

	slog.Info("IGDB similar games response", "igdbGameID", igdbGameID, "resultCount", len(games))

	return games, nil
}

// escapeQuery sanitizes user input for IGDB Apicalypse queries.
// Escapes double quotes and removes semicolons to prevent query injection.
func escapeQuery(s string) string {
	s = strings.ReplaceAll(s, ";", "")
	s = strings.ReplaceAll(s, `\`, `\\`)
	s = strings.ReplaceAll(s, `"`, `\"`)
	return s
}

// CleanGameName strips file extensions, region tags, revision tags,
// disc indicators, and bracket/parentheses content from a ROM filename.
func CleanGameName(fileName string) string {
	// Remove file extension
	if idx := strings.LastIndex(fileName, "."); idx > 0 {
		fileName = fileName[:idx]
	}

	// Remove parenthesized and bracketed content
	result := fileName
	for {
		cleaned := removeEnclosed(result, '(', ')')
		cleaned = removeEnclosed(cleaned, '[', ']')
		if cleaned == result {
			break
		}
		result = cleaned
	}

	return strings.TrimSpace(result)
}

// removeEnclosed removes the first occurrence of text enclosed in the given delimiters.
func removeEnclosed(s string, open, close byte) string {
	start := strings.IndexByte(s, open)
	if start < 0 {
		return s
	}
	end := strings.IndexByte(s[start:], close)
	if end < 0 {
		return s
	}
	return s[:start] + s[start+end+1:]
}
