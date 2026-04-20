package api

// IGDBSearchResult represents a single IGDB game candidate for admin match correction.
type IGDBSearchResult struct {
	IGDBID      int    `json:"igdbId"`
	Name        string `json:"name"`
	CoverURL    string `json:"coverUrl"`
	ReleaseYear int    `json:"releaseYear"`
	Developer   string `json:"developer"`
	Summary     string `json:"summary"`
}
