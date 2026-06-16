package db

import "time"

// ImportJob tracks importing a game from a connected federation server into the
// local library: download the ROM from a connected server, drop it into the
// console's game dir, ingest it as a local Game, then scrape its metadata.
//
// Imports can be long-running (some ROMs are multi-GB), so progress is tracked
// at byte granularity and surfaced in the UI like the scan/scrape jobs.
type ImportJob struct {
	ID        uint      `gorm:"primarykey" json:"id"`
	CreatedAt time.Time `json:"createdAt"`
	UpdatedAt time.Time `json:"updatedAt"`
	// Status: pending -> downloading -> ingesting -> scraping -> completed | failed
	Status string `gorm:"size:32;not null;default:'pending';index" json:"status"`
	// Key is the cross-server game identity (igdb:<id> or crc:<crc32>).
	Key             string `gorm:"size:255;not null;index" json:"key"`
	Title           string `gorm:"size:255" json:"title"`
	Console         string `gorm:"size:32" json:"console"` // console abbreviation, e.g. "SNES"
	BytesDownloaded int64  `json:"bytesDownloaded"`
	TotalBytes      int64  `json:"totalBytes"` // 0 when the source didn't report a size
	ErrorMessage    string `gorm:"size:512" json:"errorMessage"`
	// GameID is the resulting local game once ingested; nil until then.
	GameID            *uint      `json:"gameId"`
	RequestedByUserID uint       `gorm:"index" json:"requestedByUserId"`
	StartedAt         *time.Time `json:"startedAt"`
	CompletedAt       *time.Time `json:"completedAt"`
}
