package api

import (
	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/retroachievements"
	"github.com/spela/server/internal/scraper"
	"gorm.io/gorm"
)

// RAHandler handles RetroAchievements endpoints. All request handlers are on
// huma_ra.go (and huma_achievements.go); this file only carries the struct
// plus two helpers shared between those handler methods.
type RAHandler struct {
	DB            *gorm.DB
	RAClient      *retroachievements.RAClient
	GameDir       string
	EncryptionKey []byte                // AES-256 key for encrypting RA tokens at rest
	Queue         *scraper.ScrapeQueue  // Scrape queue for async RA fetch jobs
	RAAPIKey      string                // Server-level RA API key; empty = auto-fetch disabled
}

// decryptRAToken decrypts the RA token from a credential record.
// Handles both encrypted and legacy plaintext values transparently.
func (h *RAHandler) decryptRAToken(cred *db.RetroAchievementCredential) (string, error) {
	return auth.Decrypt(cred.RAToken, h.EncryptionKey)
}

// computeMD5 delegates to the shared implementation in the retroachievements package.
func computeMD5(path string) (string, error) {
	return retroachievements.ComputeMD5(path)
}
