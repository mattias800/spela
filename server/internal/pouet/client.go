package pouet

import (
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/url"
	"strings"
	"time"
)

// API base URL (variable for testability).
var apiBase = "https://api.pouet.net/v1"

// AbbreviationToPouetPlatforms maps Spela console abbreviations to Pouet platform IDs.
// Pouet has no platform filter on search, so we filter client-side.
var AbbreviationToPouetPlatforms = map[string][]int{
	"ADEMO": {73, 71, 79}, // Amiga OCS/ECS, Amiga AGA, Amiga PPC/RTG
	"DDEMO": {67, 69},     // MS-Dos, MS-Dos/gus
}

// Client is a Pouet.net API client. No authentication required.
type Client struct {
	HTTPClient *http.Client
	BaseURL    string
	rateTick   <-chan time.Time
}

// NewClient creates a new Pouet API client with a conservative rate limiter.
func NewClient() *Client {
	ticker := time.NewTicker(500 * time.Millisecond)
	return &Client{
		HTTPClient: &http.Client{Timeout: 15 * time.Second},
		BaseURL:    apiBase,
		rateTick:   ticker.C,
	}
}

// Group represents a demoscene group/crew.
type Group struct {
	ID      string `json:"id"`
	Name    string `json:"name"`
	Acronym string `json:"acronym"`
	Web     string `json:"web"`
}

// Platform represents a Pouet platform entry.
type Platform struct {
	Name string `json:"name"`
	Slug string `json:"slug"`
}

// Placement represents a competition placement at a demoparty.
type Placement struct {
	Party     Party  `json:"party"`
	Compo     string `json:"compo"`
	Ranking   string `json:"ranking"`
	Year      string `json:"year"`
	CompoName string `json:"compo_name"`
}

// Party represents a demoparty/event.
type Party struct {
	ID   string `json:"id"`
	Name string `json:"name"`
}

// Prod represents a Pouet production (demo, intro, etc.).
type Prod struct {
	ID         string              `json:"id"`
	Name       string              `json:"name"`
	Types      []string            `json:"types"`
	Groups     []Group             `json:"groups"`
	Platforms  map[string]Platform `json:"platforms"`
	Screenshot string              `json:"screenshot"`
	Download   string              `json:"download"`
	Placings   []Placement         `json:"placings"`
	VoteUp   json.Number `json:"voteup"`
	VotePig  json.Number `json:"votepig"`
	VoteDown json.Number `json:"votedown"`

	ReleaseDate string `json:"releaseDate"`

	// Full prod fields (only from GetProd, not search)
	Party     *Party `json:"party"`
	PartyYear string `json:"party_year"`
	PartyComp string `json:"party_compo_name"`
	Rank      string `json:"party_place"`
}

// searchResponse is the API response for search queries.
type searchResponse struct {
	Results []Prod `json:"results"`
}

// prodResponse is the API response for single prod queries.
type prodResponse struct {
	Prod Prod `json:"prod"`
}

// SearchProd searches Pouet for productions matching the query.
func (c *Client) SearchProd(query string) ([]Prod, error) {
	<-c.rateTick

	u := fmt.Sprintf("%s/search/prod/?q=%s", c.BaseURL, url.QueryEscape(query))
	slog.Debug("pouet search", "query", query, "url", u)

	resp, err := c.HTTPClient.Get(u)
	if err != nil {
		return nil, fmt.Errorf("pouet search request: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("pouet search: HTTP %d", resp.StatusCode)
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("pouet search: reading body: %w", err)
	}

	var result searchResponse
	if err := json.Unmarshal(body, &result); err != nil {
		return nil, fmt.Errorf("pouet search: parsing JSON: %w", err)
	}

	slog.Debug("pouet search results", "query", query, "count", len(result.Results))
	return result.Results, nil
}

// GetProd fetches a single production by ID with full details.
func (c *Client) GetProd(id string) (*Prod, error) {
	<-c.rateTick

	u := fmt.Sprintf("%s/prod/?id=%s", c.BaseURL, url.QueryEscape(id))

	resp, err := c.HTTPClient.Get(u)
	if err != nil {
		return nil, fmt.Errorf("pouet get prod: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("pouet get prod: HTTP %d", resp.StatusCode)
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("pouet get prod: reading body: %w", err)
	}

	var result prodResponse
	if err := json.Unmarshal(body, &result); err != nil {
		return nil, fmt.Errorf("pouet get prod: parsing JSON: %w", err)
	}

	return &result.Prod, nil
}

// FilterByPlatform filters productions to only those matching any of the given platform IDs.
func FilterByPlatform(prods []Prod, platformIDs []int) []Prod {
	idSet := make(map[string]bool)
	for _, id := range platformIDs {
		idSet[fmt.Sprintf("%d", id)] = true
	}

	var filtered []Prod
	for _, p := range prods {
		for platID := range p.Platforms {
			if idSet[platID] {
				filtered = append(filtered, p)
				break
			}
		}
	}
	return filtered
}

// CleanDemoName extracts a clean production name from a demo filename.
// Demo filenames typically look like:
//   - "State of the Art (Phenomena) [1992].adf"
//   - "Second_Reality.zip"
//   - "Batman Rises (Batman Group) A.adf"
func CleanDemoName(fileName string) string {
	// Remove extension
	name := fileName
	if idx := strings.LastIndex(name, "."); idx > 0 {
		name = name[:idx]
	}

	// Remove trailing disc indicators (A, B, _1, _2)
	for _, suffix := range []string{" A", " B", " C", " D", "_1", "_2", "_3", "_4"} {
		name = strings.TrimSuffix(name, suffix)
	}

	// Remove bracketed content [year], [tags]
	for {
		start := strings.Index(name, "[")
		if start < 0 {
			break
		}
		end := strings.Index(name[start:], "]")
		if end < 0 {
			break
		}
		name = name[:start] + name[start+end+1:]
	}

	// Replace underscores with spaces
	name = strings.ReplaceAll(name, "_", " ")

	// Trim whitespace
	name = strings.TrimSpace(name)

	return name
}

// GroupNames returns a formatted string of group names from a production.
func GroupNames(prod *Prod) string {
	if len(prod.Groups) == 0 {
		return ""
	}
	names := make([]string, len(prod.Groups))
	for i, g := range prod.Groups {
		names[i] = g.Name
	}
	return strings.Join(names, " & ")
}

// PartyInfo returns a formatted party placement string.
func PartyInfo(prod *Prod) string {
	if len(prod.Placings) == 0 {
		if prod.Party != nil && prod.Party.Name != "" {
			info := prod.Party.Name
			if prod.PartyYear != "" {
				info += " " + prod.PartyYear
			}
			if prod.Rank != "" {
				info += ", " + ordinal(prod.Rank) + " place"
			}
			return info
		}
		return ""
	}

	// Use the first/most notable placement
	p := prod.Placings[0]
	info := p.Party.Name
	if p.Year != "" {
		info += " " + p.Year
	}
	if p.Ranking != "" && p.Ranking != "0" {
		info += ", " + ordinal(p.Ranking) + " place"
	}
	if p.CompoName != "" {
		info += " (" + p.CompoName + ")"
	}
	return info
}

// TypesString returns production types as a comma-separated string.
func TypesString(prod *Prod) string {
	if len(prod.Types) == 0 {
		return "demo"
	}
	return strings.Join(prod.Types, ", ")
}

// Rating converts Pouet voting to a 0-100 score.
func Rating(prod *Prod) float64 {
	up, _ := prod.VoteUp.Int64()
	pig, _ := prod.VotePig.Int64()
	down, _ := prod.VoteDown.Int64()
	total := up + pig + down
	if total == 0 {
		return 0
	}
	// Weighted: up=1, pig=0.5, down=0
	score := (float64(up) + float64(pig)*0.5) / float64(total) * 100
	return score
}

func ordinal(s string) string {
	switch s {
	case "1":
		return "1st"
	case "2":
		return "2nd"
	case "3":
		return "3rd"
	default:
		return s + "th"
	}
}
