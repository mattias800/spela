package api

import (
	"strconv"
	"strings"
	"time"
)

// --- Phase 11: Temporal Discovery ---
//
// The gin handlers GetOnThisDay, GetBestOfYear, GetYourAnniversaries, and
// GetDecades have been migrated to huma — see huma_explore_temporal.go. Only
// the wire-format types and the shared date-parsing helpers remain here
// because the huma handlers and the test suite still depend on them.

// OnThisDayResponse is the API response for the on-this-day endpoint.
type OnThisDayResponse struct {
	Date  string         `json:"date"`
	Games []GameResponse `json:"games"`
}

// BestOfYearResponse is the API response for the best-of-year endpoint.
type BestOfYearResponse struct {
	Year  int            `json:"year"`
	Games []GameResponse `json:"games"`
}

// AnniversaryItem represents a game the user played roughly N years ago.
type AnniversaryItem struct {
	Game     GameResponse `json:"game"`
	YearsAgo int          `json:"yearsAgo"`
	PlayedAt time.Time    `json:"playedAt"`
}

// AnniversariesResponse is the API response for the your-anniversaries endpoint.
type AnniversariesResponse struct {
	Anniversaries []AnniversaryItem `json:"anniversaries"`
}

// DecadesResponse is the API response for the decades endpoint.
type DecadesResponse struct {
	Decade string         `json:"decade"`
	Label  string         `json:"label"`
	Games  []GameResponse `json:"games"`
}

// decadeRange maps a decade string to its year range.
var decadeRange = map[string][2]int{
	"80s": {1980, 1989},
	"90s": {1990, 1999},
	"00s": {2000, 2009},
}

// decadeLabel maps a decade string to a display label.
var decadeLabel = map[string]string{
	"80s": "The 80s",
	"90s": "The 90s",
	"00s": "The 00s",
}

// parseReleaseDateMonthDay attempts to extract month and day from a release_date string.
// Supports formats like "2000-03-10", "March 10, 2000", "Mar 10, 2000".
// Returns (month, day, true) on success, or (0, 0, false) if unparseable or year-only.
func parseReleaseDateMonthDay(releaseDate string) (time.Month, int, bool) {
	releaseDate = strings.TrimSpace(releaseDate)
	if releaseDate == "" {
		return 0, 0, false
	}

	// Try ISO format: "2000-03-10" or "2000-3-10"
	if len(releaseDate) >= 10 && releaseDate[4] == '-' {
		t, err := time.Parse("2006-01-02", releaseDate[:10])
		if err == nil {
			return t.Month(), t.Day(), true
		}
	}

	// Try "January 2, 2006" / "Jan 2, 2006" formats
	layouts := []string{
		"January 2, 2006",
		"Jan 2, 2006",
		"January 02, 2006",
		"Jan 02, 2006",
		"2 January 2006",
		"02 January 2006",
	}
	for _, layout := range layouts {
		t, err := time.Parse(layout, releaseDate)
		if err == nil {
			return t.Month(), t.Day(), true
		}
	}

	return 0, 0, false
}

// parseReleaseDateYear attempts to extract the year from a release_date string.
// Supports "2000-03-10", "March 10, 2000", "1996", etc.
func parseReleaseDateYear(releaseDate string) (int, bool) {
	releaseDate = strings.TrimSpace(releaseDate)
	if releaseDate == "" {
		return 0, false
	}

	// Try ISO format first
	if len(releaseDate) >= 4 {
		year, err := strconv.Atoi(releaseDate[:4])
		if err == nil && year >= 1970 && year <= 2100 {
			return year, true
		}
	}

	// Try text formats like "March 10, 2000"
	layouts := []string{
		"January 2, 2006",
		"Jan 2, 2006",
		"January 02, 2006",
		"Jan 02, 2006",
	}
	for _, layout := range layouts {
		t, err := time.Parse(layout, releaseDate)
		if err == nil {
			return t.Year(), true
		}
	}

	// Try plain 4-digit year at end (e.g. "Q4 1996" — just grab trailing year)
	parts := strings.Fields(releaseDate)
	for i := len(parts) - 1; i >= 0; i-- {
		year, err := strconv.Atoi(parts[i])
		if err == nil && year >= 1970 && year <= 2100 {
			return year, true
		}
	}

	return 0, false
}
