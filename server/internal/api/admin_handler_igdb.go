package api

// IGDBSearchResult represents a single IGDB game candidate for admin match correction.
type IGDBSearchResult struct {
	IGDBID      int    `json:"igdbId"`
	Name        string `json:"name"`
	CoverURL    string `json:"coverUrl,omitempty"`
	ReleaseYear int    `json:"releaseYear,omitempty"`
	Developer   string `json:"developer,omitempty"`
	Summary     string `json:"summary,omitempty"`
}
