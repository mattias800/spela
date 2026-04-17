package api

import (
	"context"
	"log/slog"
	"net/http"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// --- Inputs / outputs ---

// GetWizardStepsInput is the input for GET /api/explore/wizard.
type GetWizardStepsInput struct{}

// GetWizardStepsOutput wraps the wizard steps response.
type GetWizardStepsOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         WizardResponse
}

// GetWizardResultsInput is the input for GET /api/explore/wizard/results.
type GetWizardResultsInput struct {
	Mood string `query:"mood" doc:"Chosen mood (action|chill|story|challenge|fun)."`
	Era  string `query:"era" doc:"Chosen era (80s|early90s|late90s|2000s|any)."`
	Vibe string `query:"vibe" doc:"Chosen vibe (solo|multiplayer|short|long|any)."`
}

// GetWizardResultsOutput wraps the wizard results response.
type GetWizardResultsOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         WizardResultsResponse
}

// RegisterExploreWizardRoutes wires the decision-wizard endpoints.
func RegisterExploreWizardRoutes(api huma.API, h *ExploreHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "getWizardSteps",
		Method:      http.MethodGet,
		Path:        "/api/explore/wizard",
		Summary:     "Get decision wizard steps",
		Description: "Returns the static decision wizard configuration (steps + options).",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetWizardSteps)

	huma.Register(api, huma.Operation{
		OperationID: "getWizardResults",
		Method:      http.MethodGet,
		Path:        "/api/explore/wizard/results",
		Summary:     "Get wizard recommendations",
		Description: "Returns 5 game recommendations based on the wizard's mood/era/vibe selections.",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetWizardResults)
}

// --- Handlers ---

// HumaGetWizardSteps is the huma handler for GET /api/explore/wizard.
func (h *ExploreHandler) HumaGetWizardSteps(_ context.Context, _ *GetWizardStepsInput) (*GetWizardStepsOutput, error) {
	steps := []WizardStep{
		{
			Step:  1,
			Title: "What are you in the mood for?",
			Type:  "mood",
			Options: []WizardOption{
				{ID: "action", Label: "Action & Excitement", Description: "Fast-paced thrills"},
				{ID: "chill", Label: "Chill & Relaxing", Description: "Laid-back vibes"},
				{ID: "story", Label: "Deep Story", Description: "Rich narrative experiences"},
				{ID: "challenge", Label: "A Real Challenge", Description: "Test your skills"},
				{ID: "fun", Label: "Pure Fun", Description: "Simple pick-up-and-play"},
			},
		},
		{
			Step:  2,
			Title: "Pick an era",
			Type:  "era",
			Options: []WizardOption{
				{ID: "80s", Label: "The 80s", Description: "Birth of console gaming"},
				{ID: "early90s", Label: "Early 90s", Description: "16-bit golden age"},
				{ID: "late90s", Label: "Late 90s", Description: "3D revolution begins"},
				{ID: "2000s", Label: "The 2000s", Description: "Handheld renaissance"},
				{ID: "any", Label: "Any Era", Description: "Surprise me"},
			},
		},
		{
			Step:  3,
			Title: "Refine your vibe",
			Type:  "vibe",
			Options: []WizardOption{
				{ID: "solo", Label: "Solo Adventure", Description: "Just me and the game"},
				{ID: "multiplayer", Label: "Multiplayer", Description: "Games with friends"},
				{ID: "short", Label: "Quick Session", Description: "Short and sweet"},
				{ID: "long", Label: "Deep Dive", Description: "Hours of content"},
				{ID: "any", Label: "Anything Goes", Description: "No preference"},
			},
		},
	}

	return &GetWizardStepsOutput{
		CacheControl: "private, max-age=300",
		Body:         WizardResponse{Steps: steps},
	}, nil
}

// HumaGetWizardResults is the huma handler for GET /api/explore/wizard/results.
func (h *ExploreHandler) HumaGetWizardResults(ctx context.Context, in *GetWizardResultsInput) (*GetWizardResultsOutput, error) {
	userID := UserIDFromContext(ctx)

	query := h.DB.Preload("Console").Where("cover_url != '' AND is_primary = true")

	switch in.Mood {
	case "action":
		query = query.Where("genre IN ?", []string{"Action", "Shooter", "Fighting", "Beat 'em up"})
	case "chill":
		query = query.Where("genre IN ?", []string{"Puzzle", "Simulation", "Sports"})
	case "story":
		query = query.Where("genre IN ?", []string{"RPG", "Adventure"})
	case "challenge":
		query = query.Where("genre IN ?", []string{"Action", "Platformer", "Shooter"}).
			Where("rating >= 75")
	case "fun":
		query = query.Where("genre IN ?", []string{"Platformer", "Arcade", "Racing", "Puzzle"})
	}

	switch in.Era {
	case "80s":
		query = query.Where("release_date >= '1980' AND release_date < '1990'")
	case "early90s":
		query = query.Where("release_date >= '1990' AND release_date < '1995'")
	case "late90s":
		query = query.Where("release_date >= '1995' AND release_date < '2000'")
	case "2000s":
		query = query.Where("release_date >= '2000' AND release_date < '2010'")
	}

	switch in.Vibe {
	case "solo":
		query = query.Where("(players IS NULL OR players = 0 OR players = 1)")
	case "multiplayer":
		query = query.Where("players > 1")
	case "short":
		query = query.Where("genre NOT IN ?", []string{"RPG"})
	case "long":
		query = query.Where("genre IN ?", []string{"RPG", "Adventure", "Strategy"})
	}

	var games []db.Game
	if err := query.Where("rating > 0").
		Order("rating DESC, RANDOM()").
		Limit(5).
		Find(&games).Error; err != nil {
		slog.Error("failed to fetch wizard results", "error", err)
		return nil, huma.Error500InternalServerError("failed to fetch wizard results")
	}

	if len(games) < 3 {
		relaxed := h.DB.Preload("Console").Where("cover_url != '' AND is_primary = true")
		switch in.Mood {
		case "action":
			relaxed = relaxed.Where("genre IN ?", []string{"Action", "Shooter", "Fighting", "Beat 'em up", "Platformer"})
		case "story":
			relaxed = relaxed.Where("genre IN ?", []string{"RPG", "Adventure"})
		}
		relaxed.Order("RANDOM()").Limit(5).Find(&games)
	}

	title := "Your Perfect Picks"
	if in.Mood != "" {
		titles := map[string]string{
			"action":    "Action-Packed Picks",
			"chill":     "Chill & Relaxing",
			"story":     "Story-Driven Adventures",
			"challenge": "Challenge Accepted",
			"fun":       "Pure Fun Picks",
		}
		if t, ok := titles[in.Mood]; ok {
			title = t
		}
	}

	return &GetWizardResultsOutput{
		CacheControl: "no-store",
		Body: WizardResultsResponse{
			Games: ToGameResponses(games, h.DB, userID),
			Title: title,
		},
	}, nil
}
