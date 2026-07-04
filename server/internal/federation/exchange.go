package federation

import (
	"crypto/rand"
	"encoding/hex"
	"log/slog"
	"time"

	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// ExchangeRetentionWindow bounds the high-volume federation exchange ledger.
const ExchangeRetentionWindow = 30 * 24 * time.Hour

// NewRequestID returns a short random correlation id for one logical federation
// operation. It is logged on both ends and propagated via X-Spela-Request-Id so
// a single exchange can be traced across servers (#1350).
func NewRequestID() string {
	b := make([]byte, 12)
	_, _ = rand.Read(b)
	return hex.EncodeToString(b)
}

// ExchangeRecord is the parameter bag for recording a federation interaction.
// Direction/Status use the db.Exchange* constants.
type ExchangeRecord struct {
	RequestID       string
	PeerFingerprint string
	PeerName        string
	Direction       string
	Operation       string
	DataClass       string
	MaxHops         int
	Status          string
	HTTPStatus      int
	ItemCount       int
	Bytes           int64
	StartedAt       time.Time
	FinishedAt      time.Time
	Error           string
}

// RecordExchange persists one ledger row and emits a structured slog line. It
// also updates the peer's health (last contact/success/error, reachability) so
// the admin peers view reflects reality. Best-effort: a DB failure is logged but
// never blocks the caller — observability must not break federation.
func RecordExchange(database *gorm.DB, rec ExchangeRecord) {
	if rec.FinishedAt.IsZero() {
		rec.FinishedAt = time.Now()
	}
	if rec.StartedAt.IsZero() {
		rec.StartedAt = rec.FinishedAt
	}
	durationMs := rec.FinishedAt.Sub(rec.StartedAt).Milliseconds()

	logExchange(rec, durationMs)

	row := db.FederationExchange{
		RequestID:       rec.RequestID,
		PeerFingerprint: rec.PeerFingerprint,
		PeerName:        rec.PeerName,
		Direction:       rec.Direction,
		Operation:       rec.Operation,
		DataClass:       rec.DataClass,
		MaxHops:         rec.MaxHops,
		Status:          rec.Status,
		HTTPStatus:      rec.HTTPStatus,
		ItemCount:       rec.ItemCount,
		Bytes:           rec.Bytes,
		DurationMs:      durationMs,
		StartedAt:       rec.StartedAt,
		FinishedAt:      rec.FinishedAt,
		Error:           rec.Error,
	}
	if err := database.Create(&row).Error; err != nil {
		slog.Warn("federation: failed to persist exchange", "request_id", rec.RequestID, "error", err)
	}

	updatePeerHealth(database, rec)
}

// PruneExpiredExchanges deletes ledger rows older than ExchangeRetentionWindow.
func PruneExpiredExchanges(database *gorm.DB, now time.Time) int64 {
	result := database.Where("started_at < ?", now.Add(-ExchangeRetentionWindow)).
		Delete(&db.FederationExchange{})
	return result.RowsAffected
}

// updatePeerHealth folds the exchange outcome into the peer's health columns.
func updatePeerHealth(database *gorm.DB, rec ExchangeRecord) {
	if rec.PeerFingerprint == "" {
		return
	}
	now := rec.FinishedAt
	updates := map[string]any{"last_contact_at": now}
	switch rec.Status {
	case db.ExchangeOK:
		updates["last_success_at"] = now
		updates["reachable"] = true
	case db.ExchangeRejected:
		// We DID communicate with the peer — they're reachable — but the
		// request was refused (bad signature / policy / nonce). Record the
		// error without flipping reachability to false.
		updates["last_error"] = truncate(rec.Error, 512)
		updates["last_error_at"] = now
		updates["reachable"] = true
	default: // ExchangeError: failed to reach the peer / transport or remote error
		updates["last_error"] = truncate(rec.Error, 512)
		updates["last_error_at"] = now
		updates["reachable"] = false
	}
	// Best-effort; only touches an existing peer row.
	if err := database.Model(&db.FederationPeer{}).
		Where("fingerprint = ?", rec.PeerFingerprint).
		Updates(updates).Error; err != nil {
		slog.Warn("federation: failed to update peer health", "peer", ShortFingerprint(rec.PeerFingerprint), "error", err)
	}
}

func logExchange(rec ExchangeRecord, durationMs int64) {
	args := []any{
		"component", "federation",
		"request_id", rec.RequestID,
		"peer", ShortFingerprint(rec.PeerFingerprint),
		"direction", rec.Direction,
		"op", rec.Operation,
		"status", rec.Status,
		"duration_ms", durationMs,
	}
	if rec.DataClass != "" {
		args = append(args, "data_class", rec.DataClass)
	}
	if rec.ItemCount != 0 {
		args = append(args, "items", rec.ItemCount)
	}
	if rec.Bytes != 0 {
		args = append(args, "bytes", rec.Bytes)
	}
	if rec.Error != "" {
		args = append(args, "error", rec.Error)
	}
	if rec.Status == db.ExchangeOK {
		slog.Info("federation-exchange", args...)
	} else {
		slog.Warn("federation-exchange", args...)
	}
}

func truncate(s string, max int) string {
	if len(s) <= max {
		return s
	}
	return s[:max]
}
