package api

import (
	"encoding/json"
	"net"
	"net/url"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/spela/server/internal/db"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// UserHandler handles user profile and preference endpoints. All HTTP
// methods are served by huma_user*.go now; this file keeps the struct
// and a handful of package-level helpers still referenced from those
// huma handlers (and stats_handler_test.go).
type UserHandler struct {
	DB        *gorm.DB
	Hub       *ws.Hub
	JWTSecret string
}

// parsePreferredRegions splits a comma-separated region list into a trimmed slice.
func parsePreferredRegions(s string) []string {
	if s == "" {
		return []string{}
	}
	parts := strings.Split(s, ",")
	result := make([]string, 0, len(parts))
	for _, p := range parts {
		p = strings.TrimSpace(p)
		if p != "" {
			result = append(result, p)
		}
	}
	return result
}

// UpdatePreferences partially updates the current user's emulation preferences.
func (h *UserHandler) buildConsoleShaderMap(userID uint) map[string]string {
	var prefs []db.ConsoleShaderPreference
	h.DB.Where("user_id = ?", userID).Find(&prefs)

	// Batch-load console abbreviations for all referenced console IDs
	consoleIDs := make([]uint, 0, len(prefs))
	for _, p := range prefs {
		consoleIDs = append(consoleIDs, p.ConsoleID)
	}
	abbrMap := resolveConsoleAbbrs(h.DB, consoleIDs)

	m := make(map[string]string, len(prefs))
	for _, p := range prefs {
		if abbr, ok := abbrMap[p.ConsoleID]; ok {
			m[abbr] = p.Shader
		}
	}
	return m
}

// buildGameSaveStatePolicyMap queries the per-user, per-game save-
// state overrides and returns them keyed by game ID string. Layered
// on top of the per-console policy — a per-game choice wins over the
// console-level one in the resolver. See #804 phase 4b spec point (c).
func (h *UserHandler) buildGameSaveStatePolicyMap(userID uint) map[string]string {
	var prefs []db.GameSaveStatePolicy
	h.DB.Where("user_id = ?", userID).Find(&prefs)

	m := make(map[string]string, len(prefs))
	for _, p := range prefs {
		m[strconv.FormatUint(uint64(p.GameID), 10)] = string(p.Choice)
	}
	return m
}

// buildConsoleSaveStatePolicyMap queries all ConsoleSaveStatePolicy rows
// for the user and returns a map keyed by console abbreviation
// (lowercase). The map only contains consoles where the user has made
// a deliberate choice; the absence of a key means the player should
// fall back to the tier-driven default. See #804 phase 4.
func (h *UserHandler) buildConsoleSaveStatePolicyMap(userID uint) map[string]string {
	var prefs []db.ConsoleSaveStatePolicy
	h.DB.Where("user_id = ?", userID).Find(&prefs)

	consoleIDs := make([]uint, 0, len(prefs))
	for _, p := range prefs {
		consoleIDs = append(consoleIDs, p.ConsoleID)
	}
	abbrMap := resolveConsoleAbbrs(h.DB, consoleIDs)

	m := make(map[string]string, len(prefs))
	for _, p := range prefs {
		if abbr, ok := abbrMap[p.ConsoleID]; ok {
			m[abbr] = string(p.Choice)
		}
	}
	return m
}

// normalizeSaveStateChoice clamps the client-supplied opt-out value to
// the closed set the server stores. Three outcomes:
//
//	non-empty choice, clear=false → upsert this value
//	"", clear=true                → user wants the row deleted
//	"", clear=false               → unknown / garbage value, no-op
//
// Distinguishing the two empty-string outcomes matters: a misbehaving
// client sending a typo like "DISABLED " (note: that lowercases fine,
// but other typos don't) must not silently destroy an existing
// override. Only an explicit empty string means "clear me." See
// #804 phase 4 review feedback on PR #817.
func normalizeSaveStateChoice(raw string) (choice db.ConsoleSaveStateChoice, clear bool) {
	trimmed := strings.TrimSpace(raw)
	if trimmed == "" {
		return "", true
	}
	switch strings.ToLower(trimmed) {
	case string(db.ConsoleSaveStateChoiceEnabled):
		return db.ConsoleSaveStateChoiceEnabled, false
	case string(db.ConsoleSaveStateChoiceDisabled):
		return db.ConsoleSaveStateChoiceDisabled, false
	case string(db.ConsoleSaveStateChoiceAskOnce):
		return db.ConsoleSaveStateChoiceAskOnce, false
	default:
		return "", false
	}
}

// buildConsoleKeyMappingMap queries all ConsoleKeyMappingPreference rows for the user
// and returns a map keyed by console abbreviation (lowercase).
func (h *UserHandler) buildConsoleKeyMappingMap(userID uint) map[string]ConsoleKeyMappingDTO {
	var prefs []db.ConsoleKeyMappingPreference
	h.DB.Where("user_id = ?", userID).Find(&prefs)

	// Batch-load console abbreviations for all referenced console IDs
	consoleIDs := make([]uint, 0, len(prefs))
	for _, p := range prefs {
		consoleIDs = append(consoleIDs, p.ConsoleID)
	}
	abbrMap := resolveConsoleAbbrs(h.DB, consoleIDs)

	m := make(map[string]ConsoleKeyMappingDTO, len(prefs))
	for _, p := range prefs {
		if abbr, ok := abbrMap[p.ConsoleID]; ok {
			m[abbr] = ConsoleKeyMappingDTO{
				SelectedMapping: p.SelectedMapping,
				CustomMapping:   parseJSONMap(p.CustomMapping),
			}
		}
	}
	return m
}

// parseJSONMap parses a JSON string into a map[string]string, returning an empty map on error.
func parseJSONMap(s string) map[string]string {
	if s == "" {
		return map[string]string{}
	}
	var m map[string]string
	if err := json.Unmarshal([]byte(s), &m); err != nil {
		return map[string]string{}
	}
	return m
}

// privateNetworks defines the IP ranges considered internal/private.
var privateNetworks = func() []*net.IPNet {
	cidrs := []string{
		"0.0.0.0/8",       // Current network
		"10.0.0.0/8",      // Private (RFC 1918)
		"100.64.0.0/10",   // Carrier-grade NAT (RFC 6598)
		"127.0.0.0/8",     // Loopback
		"169.254.0.0/16",  // Link-local
		"172.16.0.0/12",   // Private (RFC 1918)
		"192.0.0.0/24",    // IETF protocol assignments
		"192.168.0.0/16",  // Private (RFC 1918)
		"198.18.0.0/15",   // Benchmarking (RFC 2544)
		"::1/128",         // IPv6 loopback
		"fc00::/7",        // IPv6 unique local
		"fe80::/10",       // IPv6 link-local
	}
	var nets []*net.IPNet
	for _, cidr := range cidrs {
		_, n, _ := net.ParseCIDR(cidr)
		nets = append(nets, n)
	}
	return nets
}()

// isPrivateURL returns true if the URL's hostname resolves to a private/internal IP.
func isPrivateURL(rawURL string) bool {
	parsed, err := url.Parse(rawURL)
	if err != nil {
		return true
	}
	hostname := parsed.Hostname()
	if strings.EqualFold(hostname, "localhost") {
		return true
	}
	ips, err := net.LookupIP(hostname)
	if err != nil {
		// Cannot resolve — treat as potentially private
		return true
	}
	for _, ip := range ips {
		// Normalize IPv4-mapped IPv6 addresses (e.g. ::ffff:127.0.0.1) to
		// their IPv4 form so they match the IPv4 CIDR ranges above.
		if v4 := ip.To4(); v4 != nil {
			ip = v4
		}
		for _, n := range privateNetworks {
			if n.Contains(ip) {
				return true
			}
		}
	}
	return false
}

// GetRecentGames returns the user's recently played games as a flat Game array.
func calculateStreaks(playDates []time.Time, today time.Time) (currentStreak, longestStreak int) {
	if len(playDates) == 0 {
		return 0, 0
	}

	// Normalize all dates to date-only in UTC
	dates := make([]time.Time, 0, len(playDates))
	seen := make(map[string]bool, len(playDates))
	for _, d := range playDates {
		day := d.UTC().Truncate(24 * time.Hour)
		key := day.Format("2006-01-02")
		if !seen[key] {
			seen[key] = true
			dates = append(dates, day)
		}
	}

	// Sort ascending for streak calculation
	sort.Slice(dates, func(i, j int) bool {
		return dates[i].Before(dates[j])
	})

	todayDate := today.UTC().Truncate(24 * time.Hour)
	yesterdayDate := todayDate.AddDate(0, 0, -1)

	// Calculate longest streak
	longestStreak = 1
	streak := 1
	for i := 1; i < len(dates); i++ {
		diff := dates[i].Sub(dates[i-1])
		if diff == 24*time.Hour {
			streak++
			if streak > longestStreak {
				longestStreak = streak
			}
		} else if diff > 24*time.Hour {
			streak = 1
		}
		// if diff == 0 (same day), skip — shouldn't happen with dedup
	}

	// Calculate current streak (walk back from today/yesterday)
	lastDate := dates[len(dates)-1]
	if lastDate != todayDate && lastDate != yesterdayDate {
		return 0, longestStreak
	}

	currentStreak = 1
	for i := len(dates) - 2; i >= 0; i-- {
		diff := dates[i+1].Sub(dates[i])
		if diff == 24*time.Hour {
			currentStreak++
		} else {
			break
		}
	}

	return currentStreak, longestStreak
}

// GetPlayStats returns the current user's play history for all games they've played.
// Returns a flat array of {gameId, playTime, lastPlayedAt} entries.
