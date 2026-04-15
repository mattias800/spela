package scraper

import (
	"context"
	"fmt"
	"log/slog"
	"time"

	"github.com/spela/server/internal/db"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// ScrapeWorker processes items from the scrape queue in the background.
type ScrapeWorker struct {
	db            *gorm.DB
	queue         *ScrapeQueue
	scraper       *Scraper
	hub           *ws.Hub
	onJobComplete func()
}

// NewScrapeWorker creates a new worker.
func NewScrapeWorker(database *gorm.DB, queue *ScrapeQueue, s *Scraper, hub *ws.Hub, onJobComplete func()) *ScrapeWorker {
	return &ScrapeWorker{
		db:            database,
		queue:         queue,
		scraper:       s,
		hub:           hub,
		onJobComplete: onJobComplete,
	}
}

// Run starts the worker loop. It blocks until ctx is cancelled.
func (w *ScrapeWorker) Run(ctx context.Context) {
	slog.Info("scrape worker started")

	// Recover from interrupted state (server crash / hard kill)
	if count, err := w.queue.ResetInProgressItems(); err != nil {
		slog.Error("failed to reset in-progress scrape items", "error", err)
	} else if count > 0 {
		slog.Info("reset interrupted scrape items to pending", "count", count)
	}

	for {
		select {
		case <-ctx.Done():
			slog.Info("scrape worker shutting down")
			return
		default:
		}

		item, err := w.queue.Dequeue()
		if err != nil {
			slog.Error("failed to dequeue scrape item", "error", err)
			if !w.sleepOrShutdown(ctx, 5*time.Second) {
				return
			}
			continue
		}

		if item == nil {
			if !w.sleepOrShutdown(ctx, 2*time.Second) {
				return
			}
			continue
		}

		w.processItem(ctx, item)
	}
}

// sleepOrShutdown sleeps for d or returns false if ctx is cancelled.
func (w *ScrapeWorker) sleepOrShutdown(ctx context.Context, d time.Duration) bool {
	select {
	case <-ctx.Done():
		return false
	case <-time.After(d):
		return true
	}
}

func (w *ScrapeWorker) processItem(ctx context.Context, item *db.ScrapeQueueItem) {
	var game db.Game
	if err := w.db.Preload("Console").First(&game, item.GameID).Error; err != nil {
		slog.Warn("scrape worker: game not found", "gameId", item.GameID, "error", err)
		w.queue.MarkFailed(item, fmt.Sprintf("game not found: %v", err))
		w.broadcastScrapeStatus(item.GameID, "idle")
		return
	}

	w.broadcastScrapeStatus(game.ID, "scraping")

	// Dispatch based on queue item type.
	// Default to "scrape" for backward compatibility with items created before
	// the Type field was added (they have Type="" due to GORM zero-value).
	itemType := item.Type
	if itemType == "" {
		itemType = "scrape"
	}

	switch itemType {
	case "ra_fetch":
		if err := w.scraper.FetchRAAchievements(&game); err != nil {
			slog.Warn("scrape worker: RA fetch failed", "game", game.Title, "error", err)
			jobDone, _ := w.queue.MarkFailed(item, err.Error())
			w.broadcastProgress(item, &game, false, jobDone)
			w.broadcastScrapeStatus(game.ID, "idle")
			return
		}

	default: // "scrape" — full metadata scrape
		// Variant group propagation for 'new' mode jobs
		propagated := false
		if item.JobID != nil {
			var job db.ScrapeJob
			if err := w.db.First(&job, *item.JobID).Error; err == nil {
				if job.Mode == "new" && game.GroupKey != "" {
					if w.scraper.propagateGroupMetadata(&game) {
						propagated = true
					}
				}
			}
		}

		if !propagated {
			if err := w.scraper.ScrapeGame(&game); err != nil {
				slog.Warn("scrape worker: scrape failed", "game", game.Title, "error", err)
				jobDone, _ := w.queue.MarkFailed(item, err.Error())
				w.broadcastProgress(item, &game, false, jobDone)
				w.broadcastScrapeStatus(game.ID, "idle")
				return
			}

			// Propagate metadata to unscraped siblings in the same variant group
			if game.GroupKey != "" {
				w.scraper.propagateToGroup(&game)
			}
		}
	}

	verified := game.VerificationStatus == "verified"
	if verified && item.JobID != nil {
		w.db.Model(&db.ScrapeJob{}).Where("id = ?", *item.JobID).
			Update("verified_items", gorm.Expr("verified_items + 1"))
	}

	jobDone, _ := w.queue.MarkCompleted(item)
	w.broadcastProgress(item, &game, verified, jobDone)
	w.broadcastScrapeStatus(game.ID, "idle")
}

func (w *ScrapeWorker) broadcastProgress(item *db.ScrapeQueueItem, game *db.Game, verified bool, jobDone bool) {
	if w.hub == nil {
		return
	}

	if item.JobID != nil {
		var job db.ScrapeJob
		if err := w.db.First(&job, *item.JobID).Error; err != nil {
			return
		}

		w.hub.Broadcast(ws.Event{
			Type: "scrape_progress",
			Payload: ScrapeProgress{
				Current:     job.CompletedItems + job.FailedItems,
				Total:       job.TotalItems,
				GameID:      game.ID,
				GameName:    game.Title,
				ConsoleName: game.Console.Name,
				ConsoleAbbr: game.Console.Abbreviation,
				Successes:   job.CompletedItems,
				Failures:    job.FailedItems,
				Verified:    job.VerifiedItems,
			},
		})

		if jobDone {
			w.hub.Broadcast(ws.Event{
				Type: "scrape_complete",
				Payload: map[string]interface{}{
					"scraped": job.CompletedItems,
					"total":   job.TotalItems,
				},
			})
			if w.onJobComplete != nil {
				w.onJobComplete()
			}
		}
	}

}

func (w *ScrapeWorker) broadcastScrapeStatus(gameID uint, status string) {
	if w.hub == nil {
		return
	}
	w.hub.Broadcast(ws.Event{
		Type: "game_scrape_status",
		Payload: map[string]interface{}{
			"gameId": gameID,
			"status": status,
		},
	})
}
