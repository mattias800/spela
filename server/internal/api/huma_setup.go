package api

import (
	"context"
	"net/http"

	"github.com/danielgtaylor/huma/v2"
	"github.com/danielgtaylor/huma/v2/adapters/humagin"
	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

// init disables huma's default-nullable-array schema emission. Without
// this, every `[]T` slice on a response body emits `"type": ["array",
// "null"]` in OpenAPI (because a Go nil slice marshals to JSON null),
// which propagates to the TS / Kotlin clients as `T[] | null`.
//
// Handlers are responsible for returning non-nil slices (init an empty
// slice rather than `nil`) — huma doesn't enforce this at compile time,
// but response tests and the recording code should surface any that
// slip through.
func init() {
	huma.DefaultArrayNullable = false
}

// SetupHumaAPI attaches a huma.API instance to the gin engine. The returned
// API is used by handlers to register typed operations alongside the existing
// raw gin routes — both stacks coexist during the incremental migration to
// huma. Once all handlers are migrated this file becomes the single entry
// point for route registration.
//
// The OpenAPI spec is exposed at /api/openapi.json and the docs UI at
// /api/docs (auto-served by huma).
func SetupHumaAPI(r *gin.Engine, version string) huma.API {
	cfg := huma.DefaultConfig("Spela API", version)
	cfg.OpenAPIPath = "/api/openapi"
	cfg.DocsPath = "/api/docs"
	cfg.SchemasPath = "/api/schemas"
	cfg.Info.Description = "Self-hosted game emulation service. " +
		"This spec is generated from typed Go handlers via huma — refactoring " +
		"a request/response struct in code automatically updates the spec."
	return humagin.New(r, cfg)
}

// RegisterSystemRoutes registers system-level operations (health, version)
// on the huma API. These have no auth requirement.
func RegisterSystemRoutes(api huma.API, db *gorm.DB, version string) {
	huma.Register(api, huma.Operation{
		OperationID: "getHealth",
		Method:      http.MethodGet,
		Path:        "/api/health",
		Summary:     "Health check",
		Description: "Returns server status and version. Used by load balancers and uptime monitors.",
		Tags:        []string{"system"},
	}, func(ctx context.Context, _ *struct{}) (*HealthOutput, error) {
		status := "ok"
		sqlDB, err := db.DB()
		if err != nil {
			status = "degraded"
		} else if err := sqlDB.Ping(); err != nil {
			status = "degraded"
		}
		return &HealthOutput{
			Body: HealthResponse{
				Status:  status,
				Version: version,
			},
		}, nil
	})
}

// HealthOutput wraps HealthResponse in the huma envelope so the framework
// can attach status code, headers, and the body separately.
type HealthOutput struct {
	Body HealthResponse
}

// HealthResponse is the JSON shape returned by GET /api/health.
type HealthResponse struct {
	Status  string `json:"status" doc:"Service status: 'ok' or 'degraded'." example:"ok" enum:"ok,degraded"`
	Version string `json:"version" doc:"Build version string." example:"v0.0.206"`
}
