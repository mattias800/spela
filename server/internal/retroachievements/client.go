package retroachievements

import (
	"crypto/md5"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"time"
)

// RAClient wraps the RetroAchievements web API.
type RAClient struct {
	BaseURL    string
	HTTPClient *http.Client
}

// NewRAClient creates a new RA API client with default settings.
func NewRAClient() *RAClient {
	return &RAClient{
		BaseURL: "https://retroachievements.org",
		HTTPClient: &http.Client{
			Timeout:   15 * time.Second,
			Transport: &uaTransport{base: http.DefaultTransport},
		},
	}
}

// uaTransport sets a User-Agent header on every request.
// The default Go UA ("Go-http-client/2.0") triggers Cloudflare bot detection.
type uaTransport struct {
	base http.RoundTripper
}

func (t *uaTransport) RoundTrip(req *http.Request) (*http.Response, error) {
	req.Header.Set("User-Agent", "Spela/1.0 (game metadata scraper; +https://github.com/mattias800/spela)")
	return t.base.RoundTrip(req)
}

// Achievement represents a single RA achievement.
type Achievement struct {
	ID            uint    `json:"id"`
	Title         string  `json:"title"`
	Description   string  `json:"description"`
	Points        int     `json:"points"`
	BadgeURL      string  `json:"badgeUrl"`
	Type          string  `json:"type"`
	RarityPercent float64 `json:"rarityPercent"`
}

// GameInfo contains achievement data for a game.
type GameInfo struct {
	ID           uint          `json:"id"`
	Title        string        `json:"title"`
	Achievements []Achievement `json:"achievements"`
	TotalCount   int           `json:"totalCount"`
	TotalPoints  int           `json:"totalPoints"`
}

// UserProgress tracks a user's unlocked achievements.
type UserProgress struct {
	AchievementID uint   `json:"achievementId"`
	UnlockedAt    string `json:"unlockedAt"`
	IsHardcore    bool   `json:"isHardcore"`
}

// ComputeMD5 computes the MD5 hash of a file and returns it as a hex string.
// This is a shared utility used by both the API handler and the scraper.
func ComputeMD5(path string) (string, error) {
	f, err := os.Open(path)
	if err != nil {
		return "", fmt.Errorf("opening file for hash: %w", err)
	}
	defer f.Close()

	h := md5.New()
	if _, err := io.Copy(h, f); err != nil {
		return "", fmt.Errorf("computing file hash: %w", err)
	}

	return hex.EncodeToString(h.Sum(nil)), nil
}

// GetGameExtended fetches game achievements using the public Web API.
// Requires a server-level API key (not a user token).
func (c *RAClient) GetGameExtended(apiKey string, raGameID uint) (*GameInfo, error) {
	u := fmt.Sprintf("%s/API/API_GetGameExtended.php?y=%s&i=%d",
		c.BaseURL, url.QueryEscape(apiKey), raGameID)

	resp, err := c.HTTPClient.Get(u)
	if err != nil {
		return nil, fmt.Errorf("calling RA game extended API: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("reading RA game extended response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("RA game extended returned status %d: %s", resp.StatusCode, string(body))
	}

	// The RA API returns a flat object with an Achievements map.
	// Note: GetGameExtended returns type as a string ("progression", "win_condition", "missable", null)
	// while GetGameInfoAndUserProgress returns type as an int (3=core, 5=unofficial).
	// We use json.RawMessage to handle both formats.
	var raw struct {
		ID                         uint   `json:"ID"`
		Title                      string `json:"Title"`
		NumDistinctPlayersCasual   int    `json:"NumDistinctPlayersCasual"`
		NumDistinctPlayersHardcore int    `json:"NumDistinctPlayersHardcore"`
		Achievements               map[string]struct {
			ID                 uint             `json:"ID"`
			Title              string           `json:"Title"`
			Description        string           `json:"Description"`
			Points             int              `json:"Points"`
			BadgeName          string           `json:"BadgeName"`
			Type               json.RawMessage  `json:"type"`
			NumAwarded         int              `json:"NumAwarded"`
			NumAwardedHardcore int              `json:"NumAwardedHardcore"`
		} `json:"Achievements"`
	}
	if err := json.Unmarshal(body, &raw); err != nil {
		return nil, fmt.Errorf("parsing RA game extended response: %w", err)
	}

	gameInfo := &GameInfo{
		ID:    raw.ID,
		Title: raw.Title,
	}

	totalPoints := 0

	numDistinctPlayers := raw.NumDistinctPlayersCasual
	if raw.NumDistinctPlayersHardcore > numDistinctPlayers {
		numDistinctPlayers = raw.NumDistinctPlayersHardcore
	}

	for _, a := range raw.Achievements {
		// Parse type: string ("progression", "win_condition", "missable", null) from GetGameExtended
		achType := "core"
		if len(a.Type) > 0 {
			var typeInt int
			if json.Unmarshal(a.Type, &typeInt) == nil && typeInt == 5 {
				achType = "unofficial"
			}
			// String types are all "core" variants (progression, win_condition, etc.)
		}

		badgeURL := ""
		if a.BadgeName != "" {
			badgeURL = fmt.Sprintf("https://media.retroachievements.org/Badge/%s.png", a.BadgeName)
		}

		rarityPercent := 0.0
		if numDistinctPlayers > 0 {
			rarityPercent = float64(a.NumAwarded) / float64(numDistinctPlayers) * 100.0
		}

		gameInfo.Achievements = append(gameInfo.Achievements, Achievement{
			ID:            a.ID,
			Title:         a.Title,
			Description:   a.Description,
			Points:        a.Points,
			BadgeURL:      badgeURL,
			Type:          achType,
			RarityPercent: rarityPercent,
		})

		if achType == "core" {
			totalPoints += a.Points
		}
	}

	gameInfo.TotalCount = len(gameInfo.Achievements)
	gameInfo.TotalPoints = totalPoints

	return gameInfo, nil
}

// LoginWithPassword calls the RA API to exchange username+password for a token.
func (c *RAClient) LoginWithPassword(username, password string) (string, error) {
	params := url.Values{
		"r": {"login"},
		"u": {username},
		"p": {password},
	}

	resp, err := c.HTTPClient.PostForm(c.BaseURL+"/dorequest.php", params)
	if err != nil {
		return "", fmt.Errorf("calling RA login API: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", fmt.Errorf("reading RA login response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("RA login returned status %d: %s", resp.StatusCode, string(body))
	}

	var result struct {
		Success bool   `json:"Success"`
		Token   string `json:"Token"`
		Error   string `json:"Error"`
	}
	if err := json.Unmarshal(body, &result); err != nil {
		return "", fmt.Errorf("parsing RA login response: %w", err)
	}

	if !result.Success {
		errMsg := result.Error
		if errMsg == "" {
			errMsg = "invalid credentials"
		}
		return "", fmt.Errorf("RA login failed: %s", errMsg)
	}

	if result.Token == "" {
		return "", fmt.Errorf("RA login returned empty token")
	}

	return result.Token, nil
}

// GetGameIDFromHash looks up a game's RA ID by its ROM MD5 hash.
func (c *RAClient) GetGameIDFromHash(hash string) (uint, error) {
	u := fmt.Sprintf("%s/dorequest.php?r=gameid&m=%s", c.BaseURL, url.QueryEscape(hash))

	resp, err := c.HTTPClient.Get(u)
	if err != nil {
		return 0, fmt.Errorf("calling RA gameid API: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return 0, fmt.Errorf("reading RA gameid response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		// Truncate body to avoid dumping entire HTML pages (e.g., Cloudflare 403 blocks) into logs
		bodyStr := string(body)
		if len(bodyStr) > 200 {
			bodyStr = bodyStr[:200] + "..."
		}
		return 0, fmt.Errorf("RA gameid returned status %d: %s", resp.StatusCode, bodyStr)
	}

	var result struct {
		Success bool   `json:"Success"`
		GameID  uint   `json:"GameID"`
		Error   string `json:"Error"`
	}
	if err := json.Unmarshal(body, &result); err != nil {
		return 0, fmt.Errorf("parsing RA gameid response: %w", err)
	}

	if !result.Success {
		return 0, fmt.Errorf("RA gameid lookup failed: %s", result.Error)
	}

	if result.GameID == 0 {
		return 0, fmt.Errorf("no RA game found for hash %s", hash)
	}

	return result.GameID, nil
}

// GetGameInfoAndUserProgress fetches game achievements and user progress from RA.
func (c *RAClient) GetGameInfoAndUserProgress(username, token string, raGameID uint) (*GameInfo, []UserProgress, error) {
	u := fmt.Sprintf("%s/API/API_GetGameInfoAndUserProgress.php?z=%s&y=%s&g=%d",
		c.BaseURL, url.QueryEscape(username), url.QueryEscape(token), raGameID)

	resp, err := c.HTTPClient.Get(u)
	if err != nil {
		return nil, nil, fmt.Errorf("calling RA game info API: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, nil, fmt.Errorf("reading RA game info response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, nil, fmt.Errorf("RA game info returned status %d: %s", resp.StatusCode, string(body))
	}

	// The RA API returns a flat object with an Achievements map
	var raw struct {
		ID                        uint   `json:"ID"`
		Title                     string `json:"Title"`
		NumDistinctPlayersCasual  int    `json:"NumDistinctPlayersCasual"`
		NumDistinctPlayersHardcore int   `json:"NumDistinctPlayersHardcore"`
		Achievements map[string]struct {
			ID                 uint   `json:"ID"`
			Title              string `json:"Title"`
			Description        string `json:"Description"`
			Points             int    `json:"Points"`
			BadgeName          string `json:"BadgeName"`
			Type               int    `json:"type"` // 3 = core, 5 = unofficial
			NumAwarded         int    `json:"NumAwarded"`
			NumAwardedHardcore int    `json:"NumAwardedHardcore"`
			DateEarned         string `json:"DateEarned"`
			DateEarnedHardcore string `json:"DateEarnedHardcore"`
		} `json:"Achievements"`
	}
	if err := json.Unmarshal(body, &raw); err != nil {
		return nil, nil, fmt.Errorf("parsing RA game info response: %w", err)
	}

	gameInfo := &GameInfo{
		ID:    raw.ID,
		Title: raw.Title,
	}

	var progress []UserProgress
	totalPoints := 0

	numDistinctPlayers := raw.NumDistinctPlayersCasual
	if raw.NumDistinctPlayersHardcore > numDistinctPlayers {
		numDistinctPlayers = raw.NumDistinctPlayersHardcore
	}

	for _, a := range raw.Achievements {
		achType := "core"
		if a.Type == 5 {
			achType = "unofficial"
		}

		badgeURL := ""
		if a.BadgeName != "" {
			badgeURL = fmt.Sprintf("https://media.retroachievements.org/Badge/%s.png", a.BadgeName)
		}

		rarityPercent := 0.0
		if numDistinctPlayers > 0 {
			rarityPercent = float64(a.NumAwarded) / float64(numDistinctPlayers) * 100.0
		}

		gameInfo.Achievements = append(gameInfo.Achievements, Achievement{
			ID:            a.ID,
			Title:         a.Title,
			Description:   a.Description,
			Points:        a.Points,
			BadgeURL:      badgeURL,
			Type:          achType,
			RarityPercent: rarityPercent,
		})

		if achType == "core" {
			totalPoints += a.Points
		}

		// Check if user has earned this achievement
		if a.DateEarnedHardcore != "" {
			progress = append(progress, UserProgress{
				AchievementID: a.ID,
				UnlockedAt:    a.DateEarnedHardcore,
				IsHardcore:    true,
			})
		} else if a.DateEarned != "" {
			progress = append(progress, UserProgress{
				AchievementID: a.ID,
				UnlockedAt:    a.DateEarned,
				IsHardcore:    false,
			})
		}
	}

	gameInfo.TotalCount = len(gameInfo.Achievements)
	gameInfo.TotalPoints = totalPoints

	return gameInfo, progress, nil
}

