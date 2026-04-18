package api

// --- Phase 9: Visual Browsing — Gallery & Art Modes ---
//
// The gin handlers GetScreenshotGallery, GetArtworkGallery, and GetCoverGallery
// have been migrated to huma — see huma_explore_gallery.go. Only the shared
// wire-format types remain here because they are still referenced by the huma
// handlers and by the existing test suite.

// ScreenshotItem represents a single screenshot in the gallery response.
type ScreenshotItem struct {
	URL          string `json:"url"`
	GameID       string `json:"gameId"`
	GameTitle    string `json:"gameTitle"`
	ConsoleName  string `json:"consoleName"`
	ConsoleAbbr  string `json:"consoleAbbreviation"`
	ConsoleColor string `json:"consoleColor"`
}

// ScreenshotGalleryResponse is the paginated response for the screenshot gallery.
type ScreenshotGalleryResponse struct {
	Screenshots []ScreenshotItem `json:"screenshots"`
	Page        int              `json:"page"`
	TotalPages  int              `json:"totalPages"`
	TotalCount  int              `json:"totalCount"`
}

// ArtworkItem represents a single IGDB artwork image in the gallery response.
type ArtworkItem struct {
	URL          string `json:"url"`
	Width        int    `json:"width"`
	Height       int    `json:"height"`
	GameID       string `json:"gameId"`
	GameTitle    string `json:"gameTitle"`
	ConsoleName  string `json:"consoleName"`
	ConsoleAbbr  string `json:"consoleAbbreviation"`
	ConsoleColor string `json:"consoleColor"`
}

// ArtworkGalleryResponse is the paginated response for the IGDB artwork gallery.
type ArtworkGalleryResponse struct {
	Artworks   []ArtworkItem `json:"artworks"`
	Page       int           `json:"page"`
	TotalPages int           `json:"totalPages"`
	TotalCount int           `json:"totalCount"`
}

// CoverItem represents a single cover in the cover gallery response.
type CoverItem struct {
	CoverURL          string  `json:"coverUrl"`
	GameID            string  `json:"gameId"`
	GameTitle         string  `json:"gameTitle"`
	ConsoleName       string  `json:"consoleName"`
	ConsoleAbbr       string  `json:"consoleAbbreviation"`
	ConsoleColor      string  `json:"consoleColor"`
	IGDBCriticsRating float64 `json:"igdbCriticsRating"`
	CoverAspectRatio  float64 `json:"coverAspectRatio"`
}

// CoverGalleryResponse is the paginated response for the cover gallery.
type CoverGalleryResponse struct {
	Covers     []CoverItem `json:"covers"`
	Page       int         `json:"page"`
	TotalPages int         `json:"totalPages"`
	TotalCount int         `json:"totalCount"`
}
