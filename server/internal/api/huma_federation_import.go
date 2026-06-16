package api

import (
	"context"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
)

// --- Start an import -------------------------------------------------------

type StartImportInput struct {
	Body struct {
		Key     string `json:"key" doc:"Cross-server game key (igdb:<id> or crc:<crc32>)."`
		Title   string `json:"title" maxLength:"255"`
		Console string `json:"console" doc:"Console abbreviation, e.g. SNES."`
	}
}
type StartImportOutput struct {
	Body struct {
		Job db.ImportJob `json:"job"`
	}
}

// HumaStartImport queues an import of a connected-server game into the local
// library. Allowed for admins/owners always, and for other users only when an
// admin has granted them the import capability.
func (h *FederationHandler) HumaStartImport(ctx context.Context, in *StartImportInput) (*StartImportOutput, error) {
	if !h.canImport(ctx) {
		return nil, huma.Error403Forbidden("you don't have permission to import games")
	}
	if in.Body.Key == "" || in.Body.Console == "" {
		return nil, huma.Error400BadRequest("key and console are required")
	}
	if h.Imports == nil {
		return nil, huma.Error503ServiceUnavailable("imports are not available on this server")
	}
	job, err := h.Imports.Enqueue(in.Body.Key, in.Body.Title, in.Body.Console, UserIDFromContext(ctx))
	if err != nil {
		return nil, huma.Error500InternalServerError("failed to queue import")
	}
	out := &StartImportOutput{}
	out.Body.Job = *job
	return out, nil
}

// --- List imports (status + progress) --------------------------------------

type ListImportsInput struct {
	Limit int `query:"limit"`
}
type ListImportsOutput struct {
	Body struct {
		Imports []db.ImportJob `json:"imports"`
	}
}

func (h *FederationHandler) HumaListImports(ctx context.Context, in *ListImportsInput) (*ListImportsOutput, error) {
	// Import history can reveal what games exist on connected servers, so it's
	// limited to users who may import (admins/owners, or granted users).
	if !h.canImport(ctx) {
		return nil, huma.Error403Forbidden("you don't have permission to view imports")
	}
	limit := in.Limit
	if limit <= 0 || limit > 200 {
		limit = 50
	}
	var jobs []db.ImportJob
	if err := h.DB.Order("created_at DESC").Limit(limit).Find(&jobs).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to list imports")
	}
	out := &ListImportsOutput{}
	out.Body.Imports = jobs
	return out, nil
}

// canImport reports whether the authenticated user may import: admins/owners
// always; other users only when granted the CanImportGames capability.
func (h *FederationHandler) canImport(ctx context.Context) bool {
	if db.IsAdminOrOwner(UserRoleFromContext(ctx)) {
		return true
	}
	var u db.User
	if err := h.DB.Select("can_import_games").First(&u, UserIDFromContext(ctx)).Error; err != nil {
		return false
	}
	return u.CanImportGames
}
