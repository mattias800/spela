package api

import (
	"context"
	"net/http"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// ListMakersInput is the input for GET /api/makers.
type ListMakersInput struct{}

// ListMakersOutput wraps the list body for the huma response envelope.
type ListMakersOutput struct {
	Body []MakerDetailResponse
}

// GetMakerInput is the input for GET /api/makers/{code}.
type GetMakerInput struct {
	Code string `path:"code" doc:"Hardware maker code, e.g. 'nintendo'." example:"nintendo"`
}

// GetMakerOutput wraps the single-maker body for the huma response envelope.
type GetMakerOutput struct {
	Body MakerDetailResponse
}

// RegisterMakerRoutes wires the maker endpoints into the huma API. The
// per-user rate limiter is applied after auth so behaviour matches the raw
// gin protected group.
func RegisterMakerRoutes(api huma.API, h *MakerHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)

	huma.Register(api, huma.Operation{
		OperationID: "listMakers",
		Method:      http.MethodGet,
		Path:        "/api/makers",
		Summary:     "List hardware makers",
		Description: "Returns hardware makers (Nintendo, Sega, etc.) that have at least one console with games in the library.",
		Tags:        []string{"makers"},
		Middlewares: huma.Middlewares{requireAuth, rateLimit},
		Security:    []map[string][]string{{"bearer": {}}},
	}, h.HumaListMakers)

	huma.Register(api, huma.Operation{
		OperationID: "getMaker",
		Method:      http.MethodGet,
		Path:        "/api/makers/{code}",
		Summary:     "Get hardware maker by code",
		Description: "Returns a single hardware maker along with the consoles it produces that currently have at least one game in the library.",
		Tags:        []string{"makers"},
		Middlewares: huma.Middlewares{requireAuth, rateLimit},
		Security:    []map[string][]string{{"bearer": {}}},
	}, h.HumaGetMaker)
}

// HumaListMakers is the huma handler for GET /api/makers.
func (h *MakerHandler) HumaListMakers(_ context.Context, _ *ListMakersInput) (*ListMakersOutput, error) {
	var makers []db.HardwareMaker
	if err := h.DB.Order("name ASC").Find(&makers).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch makers")
	}

	result := make([]MakerDetailResponse, 0, len(makers))
	for _, maker := range makers {
		// Count consoles that have at least one primary, non-pre-release game.
		var consoleCount int64
		h.DB.Model(&db.Console{}).
			Where("hardware_maker_id = ? AND id IN (?)",
				maker.ID,
				h.DB.Model(&db.Game{}).
					Select("DISTINCT console_id").
					Where("is_primary = ? AND is_pre_release = ?", true, false),
			).Count(&consoleCount)

		if consoleCount > 0 {
			result = append(result, MakerDetailResponse{
				Code:         maker.Code,
				Name:         maker.Name,
				ConsoleCount: int(consoleCount),
			})
		}
	}

	return &ListMakersOutput{Body: result}, nil
}

// HumaGetMaker is the huma handler for GET /api/makers/{code}.
func (h *MakerHandler) HumaGetMaker(_ context.Context, in *GetMakerInput) (*GetMakerOutput, error) {
	var maker db.HardwareMaker
	if err := h.DB.Where("code = ?", in.Code).First(&maker).Error; err != nil {
		return nil, huma.Error404NotFound("maker not found")
	}

	// Load all consoles for this maker with preloaded relationships.
	var consoles []db.Console
	h.DB.Where("hardware_maker_id = ?", maker.ID).
		Preload("HardwareMaker").
		Preload("MediaType").
		Preload("MediaType.Category").
		Order("generation ASC, name ASC").
		Find(&consoles)

	// Attach game counts (only primary, non-pre-release games).
	for i := range consoles {
		var count int64
		h.DB.Model(&db.Game{}).
			Where("console_id = ? AND is_primary = ? AND is_pre_release = ?", consoles[i].ID, true, false).
			Count(&count)
		consoles[i].GameCount = int(count)
	}

	// Only include consoles that actually have games.
	consoleResponses := make([]ConsoleResponse, 0, len(consoles))
	for _, con := range consoles {
		if con.GameCount > 0 {
			consoleResponses = append(consoleResponses, ToConsoleResponse(con))
		}
	}

	resp := MakerDetailResponse{
		Code:         maker.Code,
		Name:         maker.Name,
		ConsoleCount: len(consoleResponses),
		Consoles:     consoleResponses,
	}

	return &GetMakerOutput{Body: resp}, nil
}
