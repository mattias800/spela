package api

import (
	"strings"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/federation"
	"gorm.io/gorm"
)

func recordFederationSystemEvent(database *gorm.DB, eventType, reason, ip, path string, meta db.FederationEventMetadata) {
	db.RecordOperationalEvent(database, db.SystemEventInput{
		EventType: eventType,
		Reason:    reason,
		IP:        ip,
		Path:      path,
		Metadata:  meta,
	})
}

func isFederationPeerUnreachableError(err string) bool {
	if err == "" {
		return false
	}
	lower := strings.ToLower(err)
	transportMarkers := []string{
		"connection refused",
		"connection reset",
		"connection timed out",
		"client.timeout",
		"context deadline exceeded",
		"i/o timeout",
		"network is unreachable",
		"no route to host",
		"no such host",
		"temporary failure in name resolution",
	}
	for _, marker := range transportMarkers {
		if strings.Contains(lower, marker) {
			return true
		}
	}
	return false
}

func federationFailureReason(reason, peerFingerprint string) string {
	if peerFingerprint == "" {
		return reason
	}
	return reason + ":" + federation.ShortFingerprint(peerFingerprint)
}
