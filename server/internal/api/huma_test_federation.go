package api

import (
	"context"
	"net/http"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
)

// TestSeedCatalogInput seeds one connected-server catalog snapshot row.
type TestSeedCatalogInput struct {
	Body struct {
		OriginFingerprint string `json:"originFingerprint" doc:"Origin server fingerprint (must differ from self to count as remote)."`
		Key               string `json:"key" doc:"Cross-server game key, e.g. igdb:1022."`
		Title             string `json:"title"`
		Console           string `json:"console"`
	}
}

type TestSeedCatalogResponse struct {
	Seeded bool `json:"seeded"`
}

type TestSeedCatalogOutput struct {
	Body TestSeedCatalogResponse
}

// RegisterTestFederationRoute wires POST /api/test/federation/seed-catalog,
// which inserts a single connected-server catalog snapshot so E2E tests can
// exercise federated discovery without standing up a second server. Test-mode
// only — registered alongside /api/test/reset and never exposed in production.
func RegisterTestFederationRoute(api huma.API, h *TestHandler) {
	huma.Register(api, huma.Operation{
		OperationID: "testSeedFederationCatalog",
		Method:      http.MethodPost,
		Path:        "/api/test/federation/seed-catalog",
		Summary:     "Seed a connected-server catalog entry (E2E test only)",
		Description: "Inserts a FederationCatalogSnapshot row so federated discovery can be tested without a second server. Only registered with SPELA_TEST_MODE=true.",
		Tags:        []string{"test"},
	}, h.HumaSeedFederationCatalog)
}

func (h *TestHandler) HumaSeedFederationCatalog(_ context.Context, in *TestSeedCatalogInput) (*TestSeedCatalogOutput, error) {
	// Idempotent: clear any rows from a prior seed so repeated runs stay clean
	// (the reset endpoint intentionally leaves federation tables untouched).
	if err := h.DB.Where("source_peer_fingerprint = ?", "e2e-test-peer").Delete(&db.FederationCatalogSnapshot{}).Error; err != nil {
		return nil, huma.Error500InternalServerError("seed cleanup failed")
	}
	row := db.FederationCatalogSnapshot{
		SourcePeerFingerprint: "e2e-test-peer",
		OriginFingerprint:     in.Body.OriginFingerprint,
		Hops:                  1,
		Key:                   in.Body.Key,
		Title:                 in.Body.Title,
		Console:               in.Body.Console,
		FetchedAt:             time.Now(),
	}
	if err := h.DB.Create(&row).Error; err != nil {
		return nil, huma.Error500InternalServerError("seed failed")
	}
	return &TestSeedCatalogOutput{Body: TestSeedCatalogResponse{Seeded: true}}, nil
}
