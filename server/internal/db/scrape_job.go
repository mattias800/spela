package db

import (
	"time"
)

// ScrapeJob represents a bulk scraping operation that can be paused and resumed.
type ScrapeJob struct {
	ID             uint       `gorm:"primarykey" json:"id"`
	CreatedAt      time.Time  `json:"createdAt"`
	UpdatedAt      time.Time  `json:"updatedAt"`
	Status         string     `gorm:"size:32;not null;default:'pending';index" json:"status"` // pending, running, completed, cancelled
	Mode           string     `gorm:"size:32;not null" json:"mode"`                           // new, all, fallback
	SourceFilter   string     `gorm:"size:64" json:"sourceFilter"`
	StatusFilter   string     `gorm:"size:64" json:"statusFilter"`
	ConsoleFilter  string     `gorm:"size:64" json:"consoleFilter"`
	TotalItems     int        `json:"totalItems"`
	CompletedItems int        `json:"completedItems"`
	FailedItems    int        `json:"failedItems"`
	VerifiedItems  int        `json:"verifiedItems"`
	StartedAt      *time.Time `json:"startedAt"`
	CompletedAt    *time.Time `json:"completedAt"`
}

// ScrapeQueueItem represents a single game queued for scraping.
type ScrapeQueueItem struct {
	ID           uint       `gorm:"primarykey" json:"id"`
	CreatedAt    time.Time  `gorm:"index:idx_queue_dequeue,priority:3" json:"createdAt"`
	JobID        *uint      `gorm:"index" json:"jobId"`
	GameID       uint       `gorm:"not null" json:"gameId"`
	Type         string     `gorm:"size:32;not null;default:'scrape'" json:"type"` // "scrape" = full metadata scrape, "ra_fetch" = RetroAchievements only
	Priority     int        `gorm:"not null;default:0;index:idx_queue_dequeue,priority:2,sort:desc" json:"priority"` // 0 = bulk, 100 = manual
	Status       string     `gorm:"size:32;not null;default:'pending';index:idx_queue_dequeue,priority:1" json:"status"` // pending, in_progress, completed, failed, cancelled
	ErrorMessage string     `gorm:"size:512" json:"errorMessage"`
	CompletedAt  *time.Time `json:"completedAt"`
}
