package api

// HeroOption represents a single available hero art image from SteamGridDB.
type HeroOption struct {
	URL   string `json:"url"`
	Thumb string `json:"thumb"`
	ID    int    `json:"id"`
}
