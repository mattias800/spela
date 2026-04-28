package igdb

import (
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/url"
	"regexp"
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

// AbbreviationToIGDBPlatform maps Spela console abbreviations to one or more
// IGDB platform IDs. Most consoles have a single ID because IGDB merged the
// regional variants under one hardware-family entry (NES covers Famicom,
// Mega Drive/Genesis is id 29, Master System/Mark III is id 64, TG16/PC Engine
// is id 86, etc.). The SNES is the one exception: IGDB filed the Japanese
// Super Famicom under a separate platform id (58), so Japan-only releases
// like Alcahest (IGDB id 3651) are NOT reachable through SNES id 19 alone.
// Mapping SNES to both [19, 58] causes the search query to include the
// Japanese variant as well.
var AbbreviationToIGDBPlatform = map[string][]int{
	"NES":    {18},
	"SNES":   {19, 58}, // SNES + Super Famicom (Japan); see package doc above
	"GB":     {33},
	"GBC":    {22},
	"GBA":    {24},
	"N64":    {4},
	"NDS":    {20},
	"SMS":    {64},
	"GEN":    {29},
	"SAT":    {32},
	"PSX":    {7},
	"PSP":    {38},
	"NEOGEO": {80},
	"NEOCD":  {136}, // Neo Geo CD
	"PCE":    {86},
	"PCECD":  {150}, // TurboGrafx-CD / PC Engine CD
	"A26":    {59},
	"GG":     {35},
	"SCD":    {78},
	"32X":    {30},
	"DC":     {23},
	"VB":     {87},
	"3DS":    {37},
	"GC":     {21},
	"PS2":    {8},
	"C64":    {15},
	"AMIGA":  {16},
	"ACD32":  {114},
	// ADEMO (Amiga Demos) intentionally omitted — demoscene productions
	// don't exist in IGDB and would match against commercial games.
	"DOS": {13},
	// DDEMO (DOS Demos) intentionally omitted — same reason as ADEMO.
	"A52":    {66},
	"A78":    {60},
	"LYNX":   {61},
	"JAG":    {62},
	"NGP":    {120},
	"WS":     {57},
	"CV":     {68},
	"PCFX":   {274},
	"PKMN":   {166},
	"MSX1":   {27},
	"MSX2":   {53},
	"ARCADE": {52},
	"PS3":    {9},
	"PS4":    {48},
	"PS5":    {167},
	"XBOX":   {11},
	"X360":   {12},
	"XONE":   {49},
	"XSX":    {169},
	"3DO":    {50},
	"CDI":    {117},
	"WII":    {5},
	"WIIU":   {41},
	"NSW":    {130},
	"C128":   {15}, // shares IGDB platform with C64
	"PET":    {90},
	"PLUS4":  {94},
	"VIC20":  {71},
}

// IGDBPlatformsFor returns the IGDB platform IDs to constrain a search by
// for [consoleAbbrev]. Most consoles are a direct lookup in
// [AbbreviationToIGDBPlatform]; SCUMMVM is the exception.
//
// SCUMMVM games are runtime-emulated adventure games that originally
// shipped on a mix of DOS, Mac, Amiga, Atari ST, and Apple II. IGDB has
// no "ScummVM" platform, so we sniff the game title (e.g. "The Secret
// of Monkey Island (CD DOS VGA)") for an explicit platform hint. When a
// hint is present we narrow to that platform; otherwise we fall back to
// searching across the four most common ScummVM source platforms — DOS,
// Amiga, Mac, Atari ST. IGDB's `platforms = (X,Y,Z)` filter is an "any
// of" match, so a wider set just improves recall.
//
// Returns an empty slice when the console has no IGDB mapping at all.
func IGDBPlatformsFor(consoleAbbrev, hintText string) []int {
	if consoleAbbrev == "SCUMMVM" {
		return scummvmPlatformsFromHint(hintText)
	}
	return AbbreviationToIGDBPlatform[consoleAbbrev]
}

// scummvmPlatformsFromHint inspects [hint] (typically the game title or
// folder name) for an explicit platform marker and returns matching IGDB
// platform IDs. When no marker is found, returns the canonical
// ScummVM-source platform set [DOS, Amiga, Mac, Atari ST].
func scummvmPlatformsFromHint(hint string) []int {
	const (
		dosID     = 13
		macID     = 14
		amigaID   = 16
		atariSTID = 63
		appleIIID = 75
	)
	lower := strings.ToLower(hint)
	switch {
	case strings.Contains(lower, "dos"),
		strings.Contains(lower, "vga"),
		strings.Contains(lower, "ega"),
		strings.Contains(lower, "(pc)"),
		strings.Contains(lower, " pc "):
		return []int{dosID}
	case strings.Contains(lower, "amiga"):
		return []int{amigaID}
	case strings.Contains(lower, "macintosh"),
		strings.Contains(lower, "(mac)"),
		strings.Contains(lower, " mac "):
		return []int{macID}
	case strings.Contains(lower, "atari st"):
		return []int{atariSTID}
	case strings.Contains(lower, "apple ii"),
		strings.Contains(lower, "iigs"):
		return []int{appleIIID}
	default:
		return []int{dosID, amigaID, macID, atariSTID}
	}
}

// scummvmGameTitles maps ScummVM gameids (the contents of `*.scummvm`
// marker files, e.g. `monkey`, `gob1`, `indy3`, `ite`) to canonical game
// titles searchable on IGDB.
//
// ScummVM games on disk are folders; the convention used by RetroArch /
// EmulationStation / Recalbox / LaunchBox is that the folder contains a
// `<gameid>.scummvm` marker whose content is the gameid. When the folder
// name is also just the gameid (common — most users don't rename the
// folder), the scanner-derived title is "gob1" or "ite", which IGDB
// can't match. This map gives the scraper a real title to search for.
//
// The mapping is intentionally narrow — only the gameids users actually
// drop in. Add entries as needed; missing entries fall through and the
// scraper uses the original title (which works for descriptive folder
// names like "The Secret of Monkey Island (CD DOS VGA)").
//
// Source: ScummVM's per-engine detection_tables.h files, cross-checked
// against IGDB title spelling so the search hits cleanly.
var scummvmGameTitles = map[string]string{
	// LucasArts SCUMM
	"atlantis": "Indiana Jones and the Fate of Atlantis",
	"dig":      "The Dig",
	"ft":       "Full Throttle",
	"indy3":    "Indiana Jones and the Last Crusade: The Graphic Adventure",
	"loom":     "Loom",
	"maniac":   "Maniac Mansion",
	"monkey":   "The Secret of Monkey Island",
	"monkey2":  "Monkey Island 2: LeChuck's Revenge",
	"samnmax":  "Sam & Max Hit the Road",
	"tentacle": "Day of the Tentacle",
	"zak":      "Zak McKracken and the Alien Mindbenders",

	// Sierra AGI / SCI handled by other engines, but a few canonical IDs
	// users commonly drop in:
	"kq1":   "King's Quest: Quest for the Crown",
	"kq2":   "King's Quest II: Romancing the Throne",
	"kq3":   "King's Quest III: To Heir Is Human",
	"kq4":   "King's Quest IV: The Perils of Rosella",
	"sq1":   "Space Quest: The Sarien Encounter",
	"sq2":   "Space Quest II: Vohaul's Revenge",
	"lsl1":  "Leisure Suit Larry in the Land of the Lounge Lizards",

	// Coktel Vision (Gob* engine)
	"gob1":  "Gobliiins",
	"gob2":  "Gobliins 2: The Prince of Buffoon",
	"gob3":  "Goblins Quest 3",
	"woodruff": "The Bizarre Adventures of Woodruff and the Schnibble",

	// SAGA engine
	"ite": "Inherit the Earth: Quest for the Orb",
	"ihnm": "I Have No Mouth, and I Must Scream",

	// Westwood / Cyan / Revolution etc.
	"kyra1": "The Legend of Kyrandia",
	"kyra2": "The Legend of Kyrandia: Hand of Fate",
	"kyra3": "The Legend of Kyrandia: Malcolm's Revenge",
	"sky":   "Beneath a Steel Sky",
	"bs1":   "Broken Sword: The Shadow of the Templars",
	"bs2":   "Broken Sword II: The Smoking Mirror",

	// Misc
	"simon1": "Simon the Sorcerer",
	"simon2": "Simon the Sorcerer II: The Lion, the Witch and the Wardrobe",
	"toon":   "Toonstruck",
}

// reScummvmGameID matches a string that looks like a ScummVM gameid:
// short, lowercase alphanumerics + optional digits + optional underscores
// or hyphens, no spaces, no parentheses. Used to decide whether the
// fallback title-as-search-term path or the gameid-resolver path applies.
//
// Examples it matches: gob1, monkey, monkey2, samnmax, kyra1, indy3, ite.
// Examples it doesn't: "The Secret of Monkey Island (CD DOS VGA)",
// "Loom (FM-Towns)", "Day of the Tentacle".
var reScummvmGameID = regexp.MustCompile(`^[a-z][a-z0-9_-]{1,15}$`)

// LookupScummvmGameTitle returns the canonical IGDB-searchable title for a
// ScummVM [gameid] (e.g. "gob1" → "Gobliiins"), or empty string when the
// id isn't in the curated map.
//
// [gameid] is matched case-insensitively after trimming surrounding
// whitespace. When the input doesn't match the gameid pattern (e.g. it's
// a real descriptive title), this returns "" and the caller should keep
// using the original title.
func LookupScummvmGameTitle(gameid string) string {
	id := strings.ToLower(strings.TrimSpace(gameid))
	if !reScummvmGameID.MatchString(id) {
		return ""
	}
	return scummvmGameTitles[id]
}

// formatPlatformList renders a list of platform IDs as an IGDB query tuple:
// [19, 58] → "(19, 58)". This matches IGDB's syntax for matching a game
// where any of the listed platforms is present.
func formatPlatformList(platformIDs []int) string {
	parts := make([]string, len(platformIDs))
	for i, id := range platformIDs {
		parts[i] = fmt.Sprintf("%d", id)
	}
	return "(" + strings.Join(parts, ", ") + ")"
}

// SearchNameVariants returns a deduplicated list of search-name candidates
// derived from the cleaned ROM title. It handles the common mismatch between
// No-Intro ROM naming ("Title - Subtitle") and IGDB naming ("Title: Subtitle")
// by generating both separator styles. Callers pass the list to the IGDB
// exact-match query which ORs over all variants in a single round trip.
//
// The input is always included verbatim; the variants are additional forms.
// Order is stable so tests can pin expectations.
func SearchNameVariants(name string) []string {
	out := []string{name}
	seen := map[string]bool{name: true}
	add := func(s string) {
		if s != "" && !seen[s] {
			seen[s] = true
			out = append(out, s)
		}
	}
	// " - " ↔ ": " — No-Intro vs IGDB. Applied to the FIRST occurrence only;
	// multi-dash names ("Tom Clancy's Rainbow Six - Rogue Spear - Black Thorn")
	// fan out combinatorially otherwise and most real cases only have one
	// title/subtitle boundary.
	if strings.Contains(name, " - ") {
		add(strings.Replace(name, " - ", ": ", 1))
	}
	if strings.Contains(name, ": ") {
		add(strings.Replace(name, ": ", " - ", 1))
	}
	return out
}

// buildExactMatchWhereClause returns the IGDB `where` subclause that matches
// any of the given name variants against EITHER the primary `name` field
// or the `alternative_names.name` field. Alternative names are how IGDB
// stores regional/localized titles (e.g. "Broken Sword: The Shadow of the
// Templars" is an alternative_name for game id 616 "Circle of Blood"), so
// searching only `name` misses a lot of region-mismatched ROM lookups.
//
// Example output for ["Broken Sword - …", "Broken Sword: …"]:
//
//	(name ~ "Broken Sword - …" | name ~ "Broken Sword: …" |
//	 alternative_names.name ~ "Broken Sword - …" |
//	 alternative_names.name ~ "Broken Sword: …")
func buildExactMatchWhereClause(nameVariants []string) string {
	parts := make([]string, 0, len(nameVariants)*2)
	for _, v := range nameVariants {
		parts = append(parts, fmt.Sprintf(`name ~ "%s"`, escapeQuery(v)))
	}
	for _, v := range nameVariants {
		parts = append(parts, fmt.Sprintf(`alternative_names.name ~ "%s"`, escapeQuery(v)))
	}
	return "(" + strings.Join(parts, " | ") + ")"
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

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("Twitch OAuth returned %d: %s", resp.StatusCode, string(body))
	}

	var tokenResp struct {
		AccessToken string `json:"access_token"`
		ExpiresIn   int    `json:"expires_in"`
		TokenType   string `json:"token_type"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&tokenResp); err != nil {
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
	Storyline         string           `json:"storyline"`
	Cover             *Image           `json:"cover"`
	Screenshots       []Image          `json:"screenshots"`
	Genres            []Genre          `json:"genres"`
	InvolvedCompanies []InvolvedCompany `json:"involved_companies"`
	FirstReleaseDate  int64            `json:"first_release_date"`
	AggregatedRating  float64          `json:"aggregated_rating"`
	TotalRating       float64          `json:"total_rating"`
	TotalRatingCount  int              `json:"total_rating_count"`
	IGDBRating        float64          `json:"rating"`
	IGDBRatingCount   int              `json:"rating_count"`
	GameModes         []GameMode       `json:"game_modes"`
	ReleaseDates      []ReleaseDate    `json:"release_dates"`
	TimeToBeat        *TimeToBeat      `json:"time_to_beat"`
}

// TimeToBeat represents IGDB time-to-beat data in seconds.
type TimeToBeat struct {
	Hastily    int `json:"hastily"`
	Normally   int `json:"normally"`
	Completely int `json:"completely"`
}

// ReleaseDate represents an IGDB release date with region info.
type ReleaseDate struct {
	ID       int           `json:"id"`
	Date     int64         `json:"date"`
	Region   int           `json:"region"`
	Human    string        `json:"human"`
	Platform *ReleasePlatform `json:"platform"`
}

// ReleasePlatform is the platform info within a release date.
type ReleasePlatform struct {
	ID   int    `json:"id"`
	Name string `json:"name"`
}

// RegionName maps IGDB release date region codes to human-readable names.
// See https://api-docs.igdb.com/#region
func RegionName(regionCode int) string {
	switch regionCode {
	case 1:
		return "Europe"
	case 2:
		return "North America"
	case 3:
		return "Australia"
	case 4:
		return "New Zealand"
	case 5:
		return "Japan"
	case 6:
		return "China"
	case 7:
		return "Asia"
	case 8:
		return "Worldwide"
	case 9:
		return "Korea"
	case 10:
		return "Brazil"
	default:
		return ""
	}
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
	Logo *Image `json:"logo"`
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

// SearchGame searches IGDB for a game by name, accepting one or more platform
// IDs. The platform filter is expressed as `platforms = (id1, id2, ...)` so
// games that match ANY of the listed platforms are returned. Callers that
// only have a single id can pass a single-element slice.
func (c *Client) SearchGame(name string, platformIDs []int) ([]Game, error) {
	if err := c.authenticate(); err != nil {
		return nil, fmt.Errorf("IGDB authentication: %w", err)
	}
	if len(platformIDs) == 0 {
		return nil, fmt.Errorf("IGDB search: no platform IDs supplied")
	}

	// Wait for rate limiter
	<-c.rateLimiter

	query := fmt.Sprintf(
		`search "%s"; fields name,summary,storyline,cover.image_id,screenshots.image_id,genres.name,involved_companies.company.name,involved_companies.company.logo.image_id,involved_companies.developer,involved_companies.publisher,first_release_date,aggregated_rating,total_rating,total_rating_count,rating,rating_count,game_modes.name,release_dates.date,release_dates.region,release_dates.platform.name,release_dates.human; where platforms = %s; limit 5;`,
		escapeQuery(name), formatPlatformList(platformIDs),
	)

	slog.Info("IGDB search request", "name", name, "platformIDs", platformIDs, "query", query)

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

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("IGDB API returned %d: %s", resp.StatusCode, string(body))
	}

	var games []Game
	if err := json.NewDecoder(resp.Body).Decode(&games); err != nil {
		return nil, fmt.Errorf("decoding IGDB response: %w", err)
	}

	gameNames := make([]string, len(games))
	for i, g := range games {
		gameNames[i] = fmt.Sprintf("%s (id:%d)", g.Name, g.ID)
	}
	slog.Info("IGDB search response", "name", name, "resultCount", len(games), "results", gameNames)

	return games, nil
}

// SearchGameExact queries IGDB for a game by exact name match, accepting one
// or more platform IDs. Uses a "where" clause over both the primary `name`
// field and `alternative_names.name` (regional/localized titles), with
// separator-style variants (" - " vs ": ") auto-expanded so No-Intro ROM
// filenames match IGDB's colon-separated titles. A single query ORs all
// combinations so the whole lookup is one round trip.
func (c *Client) SearchGameExact(name string, platformIDs []int) ([]Game, error) {
	if err := c.authenticate(); err != nil {
		return nil, fmt.Errorf("IGDB authentication: %w", err)
	}
	if len(platformIDs) == 0 {
		return nil, fmt.Errorf("IGDB exact search: no platform IDs supplied")
	}

	// Wait for rate limiter
	<-c.rateLimiter

	variants := SearchNameVariants(name)
	whereNames := buildExactMatchWhereClause(variants)

	// Case-insensitive exact match using ~ operator over name + alt names.
	query := fmt.Sprintf(
		`fields name,summary,storyline,cover.image_id,screenshots.image_id,genres.name,involved_companies.company.name,involved_companies.company.logo.image_id,involved_companies.developer,involved_companies.publisher,first_release_date,aggregated_rating,total_rating,total_rating_count,rating,rating_count,game_modes.name,release_dates.date,release_dates.region,release_dates.platform.name,release_dates.human; where %s & platforms = %s; limit 5;`,
		whereNames, formatPlatformList(platformIDs),
	)

	slog.Info("IGDB exact search request", "name", name, "variants", variants, "platformIDs", platformIDs, "query", query)

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

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("IGDB API returned %d: %s", resp.StatusCode, string(body))
	}

	var games []Game
	if err := json.NewDecoder(resp.Body).Decode(&games); err != nil {
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
		`fields name,summary,storyline,cover.image_id,screenshots.image_id,genres.name,involved_companies.company.name,involved_companies.company.logo.image_id,involved_companies.developer,involved_companies.publisher,first_release_date,aggregated_rating,total_rating,total_rating_count,rating,rating_count,game_modes.name,release_dates.date,release_dates.region,release_dates.platform.name,release_dates.human; where id = %d; limit 1;`,
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

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("IGDB API returned %d: %s", resp.StatusCode, string(body))
	}

	var games []Game
	if err := json.NewDecoder(resp.Body).Decode(&games); err != nil {
		return nil, fmt.Errorf("decoding IGDB response: %w", err)
	}

	if len(games) == 0 {
		slog.Info("IGDB get game by ID: not found", "igdbID", igdbID)
		return nil, nil
	}

	slog.Info("IGDB get game by ID response", "igdbID", igdbID, "name", games[0].Name)
	return &games[0], nil
}

// GetTimeToBeat fetches time-to-beat data for a game from the separate
// /v4/game_time_to_beats endpoint. Returns nil if no data exists for the game.
func (c *Client) GetTimeToBeat(igdbGameID int) (*TimeToBeat, error) {
	if err := c.authenticate(); err != nil {
		return nil, fmt.Errorf("IGDB authentication: %w", err)
	}

	<-c.rateLimiter

	query := fmt.Sprintf(
		`fields hastily,normally,completely; where game_id = %d; limit 1;`,
		igdbGameID,
	)

	slog.Info("IGDB get time to beat request", "igdbGameID", igdbGameID)

	c.mu.Lock()
	token := c.token.AccessToken
	c.mu.Unlock()

	req, err := http.NewRequest("POST", igdbAPIBase+"/game_time_to_beats", strings.NewReader(query))
	if err != nil {
		return nil, fmt.Errorf("creating IGDB time to beat request: %w", err)
	}
	req.Header.Set("Client-ID", c.ClientID)
	req.Header.Set("Authorization", "Bearer "+token)

	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("calling IGDB API for time to beat: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("IGDB API returned %d: %s", resp.StatusCode, string(body))
	}

	var results []TimeToBeat
	if err := json.NewDecoder(resp.Body).Decode(&results); err != nil {
		return nil, fmt.Errorf("decoding IGDB time to beat response: %w", err)
	}

	if len(results) == 0 {
		slog.Info("IGDB get time to beat: no data", "igdbGameID", igdbGameID)
		return nil, nil
	}

	slog.Info("IGDB get time to beat response", "igdbGameID", igdbGameID,
		"hastily", results[0].Hastily, "normally", results[0].Normally, "completely", results[0].Completely)

	return &results[0], nil
}

// TopGame represents an IGDB top-rated game result.
type TopGame struct {
	ID                    int     `json:"id"`
	Name                  string  `json:"name"`
	Cover                 *Image  `json:"cover"`
	TotalRating           float64 `json:"total_rating"`
	TotalRatingCount      int     `json:"total_rating_count"`
	UserRating            float64 `json:"rating"`
	UserRatingCount       int     `json:"rating_count"`
	CriticRating          float64 `json:"aggregated_rating"`
	CriticRatingCount     int     `json:"aggregated_rating_count"`
}

// GetTopGames fetches the top-rated games for the given IGDB platform(s).
// Only includes games that have both user ratings (rating_count > 5) AND
// at least one critic/aggregated rating, to filter out games with inflated
// user-only scores. Accepts multiple platform IDs so the SNES top list
// includes Japan-only releases living under Super Famicom (58) alongside
// the international SNES (19).
func (c *Client) GetTopGames(platformIDs []int, limit int) ([]TopGame, error) {
	if err := c.authenticate(); err != nil {
		return nil, fmt.Errorf("IGDB authentication: %w", err)
	}
	if len(platformIDs) == 0 {
		return nil, fmt.Errorf("IGDB top games: no platform IDs supplied")
	}

	// Wait for rate limiter
	<-c.rateLimiter

	query := fmt.Sprintf(
		`fields name, cover.image_id, total_rating, total_rating_count, rating, rating_count, aggregated_rating, aggregated_rating_count; where platforms = %s & total_rating != null & total_rating_count > 5 & aggregated_rating != null; sort total_rating desc; limit %d;`,
		formatPlatformList(platformIDs), limit,
	)

	slog.Info("IGDB top games request", "platformIDs", platformIDs, "limit", limit)

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

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("IGDB API returned %d: %s", resp.StatusCode, string(body))
	}

	var games []TopGame
	if err := json.NewDecoder(resp.Body).Decode(&games); err != nil {
		return nil, fmt.Errorf("decoding IGDB response: %w", err)
	}

	slog.Info("IGDB top games response", "platformIDs", platformIDs, "resultCount", len(games))

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

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("IGDB API returned %d: %s", resp.StatusCode, string(body))
	}

	var parentGames []struct {
		SimilarGames []int `json:"similar_games"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&parentGames); err != nil {
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

	if resp2.StatusCode != http.StatusOK {
		body2, _ := io.ReadAll(resp2.Body)
		return nil, fmt.Errorf("IGDB API returned %d: %s", resp2.StatusCode, string(body2))
	}

	var games []SimilarGame
	if err := json.NewDecoder(resp2.Body).Decode(&games); err != nil {
		return nil, fmt.Errorf("decoding IGDB similar games detail: %w", err)
	}

	slog.Info("IGDB similar games response", "igdbGameID", igdbGameID, "resultCount", len(games))

	return games, nil
}

// CompanyDetail holds extended metadata about a company from IGDB.
type CompanyDetail struct {
	ID           int    `json:"id"`
	Name         string `json:"name"`
	Description  string `json:"description"`
	LogoImageID  string `json:"logo_image_id"`
	Country      int    `json:"country"`
	StartDate    int64  `json:"start_date"`     // Unix timestamp
	WebsiteURL   string `json:"website_url"`    // official website
	WikipediaURL string `json:"wikipedia_url"`
}

// CompanyLogoURL constructs a full logo URL from an image_id.
func CompanyLogoURL(imageID string) string {
	if imageID == "" {
		return ""
	}
	return fmt.Sprintf("%s/t_logo_med/%s.png", igdbImageBase, imageID)
}

// GetCompanyByID fetches detailed company information from IGDB by company ID.
// Returns name, description, logo, country, founding date, and websites.
// Returns nil, nil if the company is not found.
func (c *Client) GetCompanyByID(igdbID int) (*CompanyDetail, error) {
	if err := c.authenticate(); err != nil {
		return nil, fmt.Errorf("IGDB authentication: %w", err)
	}

	<-c.rateLimiter

	query := fmt.Sprintf(
		`fields name,description,logo.image_id,country,start_date,websites.url,websites.category; where id = %d; limit 1;`,
		igdbID,
	)

	slog.Info("IGDB get company request", "igdbID", igdbID)

	c.mu.Lock()
	token := c.token.AccessToken
	c.mu.Unlock()

	req, err := http.NewRequest("POST", igdbAPIBase+"/companies", strings.NewReader(query))
	if err != nil {
		return nil, fmt.Errorf("creating IGDB company request: %w", err)
	}
	req.Header.Set("Client-ID", c.ClientID)
	req.Header.Set("Authorization", "Bearer "+token)

	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("calling IGDB API for company: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("IGDB API returned %d: %s", resp.StatusCode, string(body))
	}

	var results []struct {
		ID          int    `json:"id"`
		Name        string `json:"name"`
		Description string `json:"description"`
		Logo        *struct {
			ImageID string `json:"image_id"`
		} `json:"logo"`
		Country   int   `json:"country"`
		StartDate int64 `json:"start_date"`
		Websites  []struct {
			URL      string `json:"url"`
			Category int    `json:"category"`
		} `json:"websites"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&results); err != nil {
		return nil, fmt.Errorf("decoding IGDB company response: %w", err)
	}

	if len(results) == 0 {
		slog.Info("IGDB get company: not found", "igdbID", igdbID)
		return nil, nil
	}

	r := results[0]
	detail := &CompanyDetail{
		ID:          r.ID,
		Name:        r.Name,
		Description: r.Description,
		Country:     r.Country,
		StartDate:   r.StartDate,
	}

	if r.Logo != nil {
		detail.LogoImageID = r.Logo.ImageID
	}

	// Extract official website (category 1) and Wikipedia (category 3)
	for _, w := range r.Websites {
		switch w.Category {
		case 1:
			if detail.WebsiteURL == "" {
				detail.WebsiteURL = w.URL
			}
		case 3:
			if detail.WikipediaURL == "" {
				detail.WikipediaURL = w.URL
			}
		}
	}

	slog.Info("IGDB get company response", "igdbID", igdbID, "name", detail.Name)
	return detail, nil
}

// SearchCompanyByName searches IGDB for companies matching the given name.
// Returns the first exact match (case-insensitive) or nil if not found.
func (c *Client) SearchCompanyByName(name string) (*CompanyDetail, error) {
	if err := c.authenticate(); err != nil {
		return nil, fmt.Errorf("IGDB authentication: %w", err)
	}

	<-c.rateLimiter

	query := fmt.Sprintf(
		`fields name,description,logo.image_id,country,start_date,websites.url,websites.category; where name ~ "%s"; limit 5;`,
		escapeQuery(name),
	)

	slog.Info("IGDB search company by name request", "name", name)

	c.mu.Lock()
	token := c.token.AccessToken
	c.mu.Unlock()

	req, err := http.NewRequest("POST", igdbAPIBase+"/companies", strings.NewReader(query))
	if err != nil {
		return nil, fmt.Errorf("creating IGDB company search request: %w", err)
	}
	req.Header.Set("Client-ID", c.ClientID)
	req.Header.Set("Authorization", "Bearer "+token)

	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("calling IGDB API for company search: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("IGDB API returned %d: %s", resp.StatusCode, string(body))
	}

	var results []struct {
		ID          int    `json:"id"`
		Name        string `json:"name"`
		Description string `json:"description"`
		Logo        *struct {
			ImageID string `json:"image_id"`
		} `json:"logo"`
		Country   int   `json:"country"`
		StartDate int64 `json:"start_date"`
		Websites  []struct {
			URL      string `json:"url"`
			Category int    `json:"category"`
		} `json:"websites"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&results); err != nil {
		return nil, fmt.Errorf("decoding IGDB company search response: %w", err)
	}

	if len(results) == 0 {
		slog.Info("IGDB search company by name: not found", "name", name)
		return nil, nil
	}

	// Pick the first result (case-insensitive exact match preferred)
	r := results[0]
	for _, candidate := range results {
		if strings.EqualFold(candidate.Name, name) {
			r = candidate
			break
		}
	}

	detail := &CompanyDetail{
		ID:          r.ID,
		Name:        r.Name,
		Description: r.Description,
		Country:     r.Country,
		StartDate:   r.StartDate,
	}

	if r.Logo != nil {
		detail.LogoImageID = r.Logo.ImageID
	}

	for _, w := range r.Websites {
		switch w.Category {
		case 1:
			if detail.WebsiteURL == "" {
				detail.WebsiteURL = w.URL
			}
		case 3:
			if detail.WikipediaURL == "" {
				detail.WikipediaURL = w.URL
			}
		}
	}

	slog.Info("IGDB search company by name response", "name", name, "matchedName", detail.Name, "igdbID", detail.ID)
	return detail, nil
}

// --- Enrichment types for Phase 2 Explore ---

// GameEnrichment holds extended metadata fetched from IGDB for a single game.
type GameEnrichment struct {
	Themes             []EnrichmentNamedItem `json:"themes"`
	Keywords           []EnrichmentNamedItem `json:"keywords"`
	PlayerPerspectives []EnrichmentNamedItem `json:"player_perspectives"`
	Franchises         []int                 `json:"franchises"`       // raw franchise IDs
	CollectionID       *int                  `json:"collection_id"`    // IGDB collection (series) ID, nil if none
	Artworks           []ArtworkData         `json:"artworks"`
	Videos             []VideoData           `json:"videos"`
	LanguageSupports   []LanguageSupportData `json:"language_supports"`
	AgeRatings         []AgeRatingData       `json:"age_ratings"`
}

// VideoData holds an IGDB video entry (typically YouTube).
type VideoData struct {
	VideoID string `json:"video_id"`
	Name    string `json:"name"`
}

// LanguageSupportData holds an IGDB language support entry.
type LanguageSupportData struct {
	Language            NameWrapper `json:"language"`
	LanguageSupportType NameWrapper `json:"language_support_type"`
}

// NameWrapper is a simple struct for IGDB nested name fields.
type NameWrapper struct {
	Name string `json:"name"`
}

// AgeRatingData holds an IGDB age rating entry.
type AgeRatingData struct {
	Category int `json:"category"` // 1=ESRB, 2=PEGI
	Rating   int `json:"rating"`   // IGDB enum value
}

// AgeRatingLabel returns a human-readable label for an IGDB age rating.
func AgeRatingLabel(category, rating int) string {
	if category == 1 { // ESRB
		switch rating {
		case 6:
			return "RP"
		case 7:
			return "EC"
		case 8:
			return "E"
		case 9:
			return "E10+"
		case 10:
			return "T"
		case 11:
			return "M"
		case 12:
			return "AO"
		}
	}
	if category == 2 { // PEGI
		switch rating {
		case 1:
			return "PEGI 3"
		case 2:
			return "PEGI 7"
		case 3:
			return "PEGI 12"
		case 4:
			return "PEGI 16"
		case 5:
			return "PEGI 18"
		}
	}
	return ""
}

// AgeRatingCategoryName returns the human-readable category name.
func AgeRatingCategoryName(category int) string {
	switch category {
	case 1:
		return "ESRB"
	case 2:
		return "PEGI"
	default:
		return ""
	}
}



// EnrichmentNamedItem holds an IGDB entity with an ID and name.
type EnrichmentNamedItem struct {
	ID   int    `json:"id"`
	Name string `json:"name"`
}

// ArtworkData holds IGDB artwork image metadata.
type ArtworkData struct {
	ImageID string `json:"image_id"`
	Width   int    `json:"width"`
	Height  int    `json:"height"`
}

// FranchiseData holds IGDB franchise details.
type FranchiseData struct {
	ID      int    `json:"id"`
	Name    string `json:"name"`
	GameIDs []int  `json:"games"`
}

// CollectionData holds IGDB collection (series) details.
type CollectionData struct {
	ID      int    `json:"id"`
	Name    string `json:"name"`
	GameIDs []int  `json:"games"`
}

// CollectionGameInfo holds basic info about a game within a collection.
type CollectionGameInfo struct {
	ID       int    `json:"id"`
	Name     string `json:"name"`
	CoverImageID string `json:"cover_image_id"`
}

// GetGameEnrichment fetches extended metadata for a game: themes, keywords,
// player perspectives, franchise IDs, collection ID, and artworks.
// Returns nil, nil if the game is not found.
func (c *Client) GetGameEnrichment(igdbID int) (*GameEnrichment, error) {
	if err := c.authenticate(); err != nil {
		return nil, fmt.Errorf("IGDB authentication: %w", err)
	}

	<-c.rateLimiter

	query := fmt.Sprintf(
		`fields themes.name,keywords.name,player_perspectives.name,franchises,collection,artworks.image_id,artworks.width,artworks.height,videos.video_id,videos.name,language_supports.language.name,language_supports.language_support_type.name,age_ratings.category,age_ratings.rating; where id = %d; limit 1;`,
		igdbID,
	)

	slog.Info("IGDB get game enrichment request", "igdbID", igdbID)

	c.mu.Lock()
	token := c.token.AccessToken
	c.mu.Unlock()

	req, err := http.NewRequest("POST", igdbAPIBase+"/games", strings.NewReader(query))
	if err != nil {
		return nil, fmt.Errorf("creating IGDB enrichment request: %w", err)
	}
	req.Header.Set("Client-ID", c.ClientID)
	req.Header.Set("Authorization", "Bearer "+token)

	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("calling IGDB API for enrichment: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("IGDB API returned %d: %s", resp.StatusCode, string(body))
	}

	var results []struct {
		Themes             []EnrichmentNamedItem `json:"themes"`
		Keywords           []EnrichmentNamedItem `json:"keywords"`
		PlayerPerspectives []EnrichmentNamedItem `json:"player_perspectives"`
		Franchises         []int                 `json:"franchises"`
		Collection         *int                  `json:"collection"`
		Artworks           []struct {
			ImageID string `json:"image_id"`
			Width   int    `json:"width"`
			Height  int    `json:"height"`
		} `json:"artworks"`
		Videos []struct {
			VideoID string `json:"video_id"`
			Name    string `json:"name"`
		} `json:"videos"`
		LanguageSupports []struct {
			Language            NameWrapper `json:"language"`
			LanguageSupportType NameWrapper `json:"language_support_type"`
		} `json:"language_supports"`
		AgeRatings []struct {
			Category int `json:"category"`
			Rating   int `json:"rating"`
		} `json:"age_ratings"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&results); err != nil {
		return nil, fmt.Errorf("decoding IGDB enrichment response: %w", err)
	}

	if len(results) == 0 {
		slog.Info("IGDB get game enrichment: not found", "igdbID", igdbID)
		return nil, nil
	}

	r := results[0]
	enrichment := &GameEnrichment{
		Themes:             r.Themes,
		Keywords:           r.Keywords,
		PlayerPerspectives: r.PlayerPerspectives,
		Franchises:         r.Franchises,
		CollectionID:       r.Collection,
	}

	for _, a := range r.Artworks {
		enrichment.Artworks = append(enrichment.Artworks, ArtworkData{
			ImageID: a.ImageID,
			Width:   a.Width,
			Height:  a.Height,
		})
	}
	for _, v := range r.Videos {
		enrichment.Videos = append(enrichment.Videos, VideoData{
			VideoID: v.VideoID,
			Name:    v.Name,
		})
	}
	for _, ls := range r.LanguageSupports {
		enrichment.LanguageSupports = append(enrichment.LanguageSupports, LanguageSupportData{
			Language:            ls.Language,
			LanguageSupportType: ls.LanguageSupportType,
		})
	}
	for _, ar := range r.AgeRatings {
		enrichment.AgeRatings = append(enrichment.AgeRatings, AgeRatingData{
			Category: ar.Category,
			Rating:   ar.Rating,
		})
	}

	slog.Info("IGDB get game enrichment response", "igdbID", igdbID,
		"themes", len(enrichment.Themes), "keywords", len(enrichment.Keywords),
		"perspectives", len(enrichment.PlayerPerspectives),
		"franchises", len(enrichment.Franchises),
		"artworks", len(enrichment.Artworks),
		"videos", len(enrichment.Videos),
		"languageSupports", len(enrichment.LanguageSupports),
		"ageRatings", len(enrichment.AgeRatings))

	return enrichment, nil
}

// GetCollection fetches an IGDB collection (series) by ID.
// Returns the collection name and the list of game IDs.
// Returns nil, nil if the collection is not found.
func (c *Client) GetCollection(collectionID int) (*CollectionData, error) {
	if err := c.authenticate(); err != nil {
		return nil, fmt.Errorf("IGDB authentication: %w", err)
	}

	<-c.rateLimiter

	query := fmt.Sprintf(
		`fields name,games; where id = %d; limit 1;`,
		collectionID,
	)

	slog.Info("IGDB get collection request", "collectionID", collectionID)

	c.mu.Lock()
	token := c.token.AccessToken
	c.mu.Unlock()

	req, err := http.NewRequest("POST", igdbAPIBase+"/collections", strings.NewReader(query))
	if err != nil {
		return nil, fmt.Errorf("creating IGDB collection request: %w", err)
	}
	req.Header.Set("Client-ID", c.ClientID)
	req.Header.Set("Authorization", "Bearer "+token)

	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("calling IGDB API for collection: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("IGDB API returned %d: %s", resp.StatusCode, string(body))
	}

	var results []CollectionData
	if err := json.NewDecoder(resp.Body).Decode(&results); err != nil {
		return nil, fmt.Errorf("decoding IGDB collection response: %w", err)
	}

	if len(results) == 0 {
		slog.Info("IGDB get collection: not found", "collectionID", collectionID)
		return nil, nil
	}

	slog.Info("IGDB get collection response", "collectionID", collectionID,
		"name", results[0].Name, "games", len(results[0].GameIDs))

	return &results[0], nil
}

// GetFranchise fetches an IGDB franchise by ID.
// Returns the franchise name and the list of game IDs.
// Returns nil, nil if the franchise is not found.
func (c *Client) GetFranchise(franchiseID int) (*FranchiseData, error) {
	if err := c.authenticate(); err != nil {
		return nil, fmt.Errorf("IGDB authentication: %w", err)
	}

	<-c.rateLimiter

	query := fmt.Sprintf(
		`fields name,games; where id = %d; limit 1;`,
		franchiseID,
	)

	slog.Info("IGDB get franchise request", "franchiseID", franchiseID)

	c.mu.Lock()
	token := c.token.AccessToken
	c.mu.Unlock()

	req, err := http.NewRequest("POST", igdbAPIBase+"/franchises", strings.NewReader(query))
	if err != nil {
		return nil, fmt.Errorf("creating IGDB franchise request: %w", err)
	}
	req.Header.Set("Client-ID", c.ClientID)
	req.Header.Set("Authorization", "Bearer "+token)

	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("calling IGDB API for franchise: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("IGDB API returned %d: %s", resp.StatusCode, string(body))
	}

	var results []FranchiseData
	if err := json.NewDecoder(resp.Body).Decode(&results); err != nil {
		return nil, fmt.Errorf("decoding IGDB franchise response: %w", err)
	}

	if len(results) == 0 {
		slog.Info("IGDB get franchise: not found", "franchiseID", franchiseID)
		return nil, nil
	}

	slog.Info("IGDB get franchise response", "franchiseID", franchiseID,
		"name", results[0].Name, "games", len(results[0].GameIDs))

	return &results[0], nil
}

// GetCollectionGames fetches basic info for games within a collection.
// Returns name and cover for each game ID provided.
func (c *Client) GetCollectionGames(gameIDs []int) ([]CollectionGameInfo, error) {
	if len(gameIDs) == 0 {
		return nil, nil
	}

	if err := c.authenticate(); err != nil {
		return nil, fmt.Errorf("IGDB authentication: %w", err)
	}

	// Process in batches of 50 (IGDB limit)
	var allGames []CollectionGameInfo
	for i := 0; i < len(gameIDs); i += 50 {
		end := i + 50
		if end > len(gameIDs) {
			end = len(gameIDs)
		}
		batch := gameIDs[i:end]

		<-c.rateLimiter

		idStrs := make([]string, len(batch))
		for j, id := range batch {
			idStrs[j] = fmt.Sprintf("%d", id)
		}

		query := fmt.Sprintf(
			`fields name,cover.image_id; where id = (%s); limit %d;`,
			strings.Join(idStrs, ","), len(batch),
		)

		c.mu.Lock()
		token := c.token.AccessToken
		c.mu.Unlock()

		req, err := http.NewRequest("POST", igdbAPIBase+"/games", strings.NewReader(query))
		if err != nil {
			return nil, fmt.Errorf("creating IGDB collection games request: %w", err)
		}
		req.Header.Set("Client-ID", c.ClientID)
		req.Header.Set("Authorization", "Bearer "+token)

		resp, err := c.HTTPClient.Do(req)
		if err != nil {
			return nil, fmt.Errorf("calling IGDB API for collection games: %w", err)
		}

		if resp.StatusCode != http.StatusOK {
			body, _ := io.ReadAll(resp.Body)
			resp.Body.Close()
			return nil, fmt.Errorf("IGDB API returned %d: %s", resp.StatusCode, string(body))
		}

		var games []struct {
			ID    int    `json:"id"`
			Name  string `json:"name"`
			Cover *Image `json:"cover"`
		}
		err = json.NewDecoder(resp.Body).Decode(&games)
		resp.Body.Close()
		if err != nil {
			return nil, fmt.Errorf("decoding IGDB collection games response: %w", err)
		}

		for _, g := range games {
			info := CollectionGameInfo{
				ID:   g.ID,
				Name: g.Name,
			}
			if g.Cover != nil {
				info.CoverImageID = g.Cover.ImageID
			}
			allGames = append(allGames, info)
		}
	}

	return allGames, nil
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
// For titles with alternate names separated by " ~ " (e.g.,
// "Puzzle Bobble ~ Bust-A-Move"), uses the first (primary) title.
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

	result = strings.TrimSpace(result)

	// Handle tilde-separated alternate titles (No-Intro naming convention).
	// Use the first (primary) title for search.
	if idx := strings.Index(result, " ~ "); idx > 0 {
		result = result[:idx]
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
