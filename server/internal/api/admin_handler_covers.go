package api

// CoverOption represents a single available cover art source.
type CoverOption struct {
	Source       string `json:"source"`
	URL          string `json:"url"`
	Label        string `json:"label,omitempty"`
	LibRetroName string `json:"libretroName,omitempty"`
}
