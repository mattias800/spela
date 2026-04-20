package api

// --- Phase 14: Wild Features — Wizard, Badges, Completionist Map ---
//
// The gin handlers GetWizardSteps, GetWizardResults, GetExplorerBadges, and
// GetCompletionistMap have been migrated to huma — see huma_explore_wizard.go
// (wizard) and huma_profiles.go (badges + completionist map). Only the shared
// wire-format types remain here because they are still referenced by the huma
// handlers and by the existing test suite.

// WizardStep represents a single step in the decision wizard.
type WizardStep struct {
	Step    int            `json:"step"`
	Title   string         `json:"title"`
	Type    string         `json:"type"` // "mood", "era", "vibe"
	Options []WizardOption `json:"options"`
}

// WizardOption is a selectable option in a wizard step.
type WizardOption struct {
	ID          string `json:"id"`
	Label       string `json:"label"`
	Description string `json:"description"`
	ImageURL    string `json:"imageUrl"`
}

// WizardResponse is the API response for the wizard endpoint.
type WizardResponse struct {
	Steps []WizardStep `json:"steps"`
}

// WizardResultsResponse is the response for wizard recommendations.
type WizardResultsResponse struct {
	Games []GameResponse `json:"games"`
	Title string         `json:"title"`
}

// ExplorerBadge represents a discovery/exploration badge.
type ExplorerBadge struct {
	ID          string `json:"id"`
	Name        string `json:"name"`
	Description string `json:"description"`
	Icon        string `json:"icon"`
	Earned      bool   `json:"earned"`
	Progress    int    `json:"progress"`
	Target      int    `json:"target"`
}

// ExplorerBadgesResponse is the API response for explorer badges.
type ExplorerBadgesResponse struct {
	Badges []ExplorerBadge `json:"badges"`
}

// CompletionistConsole represents per-console completion stats.
type CompletionistConsole struct {
	ID          string `json:"id"`
	Name        string `json:"name"`
	TotalGames  int    `json:"totalGames"`
	PlayedGames int    `json:"playedGames"`
	Percentage  int    `json:"percentage"`
}

// CompletionistMapResponse is the API response for the completionist map.
type CompletionistMapResponse struct {
	Consoles    []CompletionistConsole `json:"consoles"`
	TotalGames  int                    `json:"totalGames"`
	TotalPlayed int                    `json:"totalPlayed"`
	OverallPct  int                    `json:"overallPct"`
}
