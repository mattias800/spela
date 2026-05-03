package scraper

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"time"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/igdb"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// igdbRateLimitBackoff is the cooldown applied after an IGDB 429 response.
// IGDB's documented rate limit is 4 req/sec with a hard cap on bursts; a
// 60-second cooldown gives the window time to reset and prevents the worker
// from burning through the remaining queue on fast-fail retries.
const igdbRateLimitBackoff = 60 * time.Second

// ScrapeWorker processes items from the scrape queue in the background.
type ScrapeWorker struct {
	db                         *gorm.DB
	queue                      *ScrapeQueue
	scraper                    *Scraper
	hub                        *ws.Hub
	onJobComplete              func()
	scraperConsecutiveFailures int
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
	// Dispatch based on queue item type.
	// Default to "scrape" for backward compatibility with items created before
	// the Type field was added (they have Type="" due to GORM zero-value).
	itemType := item.Type
	if itemType == "" {
		itemType = "scrape"
	}

	// Only "scrape" items represent user-visible scrape activity. "ra_fetch"
	// is background achievement-metadata polling — not a scrape. The UI's
	// scrape-status indicator should not fire for it (otherwise a polling
	// achievements query looks like the game is being rescraped over and
	// over). Both still go through MarkCompleted / MarkFailed for queue
	// bookkeeping; only the WS broadcast is gated.
	broadcastStatus := itemType == "scrape"

	var game db.Game
	if err := w.db.Preload("Console").First(&game, item.GameID).Error; err != nil {
		slog.Warn("scrape worker: game not found", "gameId", item.GameID, "error", err)
		w.queue.MarkFailed(item, fmt.Sprintf("game not found: %v", err))
		if broadcastStatus {
			w.broadcastScrapeStatus(item.GameID, "idle")
		}
		return
	}

	if broadcastStatus {
		w.broadcastScrapeStatus(game.ID, "scraping")
	}

	switch itemType {
	case "ra_fetch":
		if err := w.scraper.FetchRAAchievements(&game); err != nil {
			slog.Warn("scrape worker: RA fetch failed", "game", game.Title, "error", err)
			jobDone, _ := w.queue.MarkFailed(item, err.Error())
			w.broadcastProgress(item, &game, false, jobDone)
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
				w.scraperConsecutiveFailures++
				if w.scraperConsecutiveFailures >= 5 {
					db.RecordOperationalEvent(w.db, db.SystemEventInput{
						EventType: db.SystemEventScraperRepeatedErrors,
						Metadata: db.ScraperRepeatedErrorsMetadata{
							ConsecutiveFailures: w.scraperConsecutiveFailures,
							Error:               err.Error(),
							GameTitle:           game.Title,
						},
					})
				}
				jobDone, _ := w.queue.MarkFailed(item, err.Error())
				w.broadcastProgress(item, &game, false, jobDone)
				w.broadcastScrapeStatus(game.ID, "idle")
				// On IGDB rate-limit, sleep before processing the next item
				// so the rate-limit window can reset. Without this, a 429
				// storm fast-fails every remaining item in the queue.
				if errors.Is(err, igdb.ErrRateLimit) {
					slog.Warn("scrape worker: IGDB rate limit hit, backing off", "duration", igdbRateLimitBackoff)
					select {
					case <-ctx.Done():
					case <-time.After(igdbRateLimitBackoff):
					}
				}
				return
			}
			w.scraperConsecutiveFailures = 0

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
	if broadcastStatus {
		w.broadcastScrapeStatus(game.ID, "idle")
	}
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

		// Skip the progress broadcast when the job is no longer running —
		// e.g. the admin clicked "Cancel scrape" while the worker had an
		// item in flight. CancelJob marks pending items + the job as
		// "cancelled", but the in-flight item keeps running until its
		// scrape finishes; without this guard we emit a final
		// EventScrapeProgress for that item that the UI mistakes for
		// "the scrape is still going" right after Cancel was acked.
		if job.Status != "running" {
			return
		}

		w.hub.Broadcast(ws.Event{
			Type: ws.EventScrapeProgress,
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
				Type: ws.EventScrapeComplete,
				Payload: ws.ScrapeCompletePayload{
					Scraped: job.CompletedItems,
					Total:   job.TotalItems,
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
		Type: ws.EventGameScrapeStatus,
		Payload: ws.GameScrapeStatusPayload{
			GameID: gameID,
			Status: status,
		},
	})
}
