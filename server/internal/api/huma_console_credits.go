package api

import (
	"context"
	"encoding/json"
	"html"
)

// ConsolePhotoCredit is one bundled-photo attribution entry. The fields mirror
// the per-image record in static/console-photos/CREDITS.json, trimmed to what a
// user-facing credits screen needs. See #1441.
type ConsolePhotoCredit struct {
	Console string `json:"console"`
	Title   string `json:"title"`
	Author  string `json:"author"`
	License string `json:"license"`
	Source  string `json:"source"`
}

// ConsolePhotoCreditsResponse is the public attribution manifest for the bundled
// console hardware photos. Surfacing it is what keeps us CC-BY-SA compliant: the
// images vary by author and license, so a single hardcoded credit line would
// misattribute most of them.
type ConsolePhotoCreditsResponse struct {
	Note   string               `json:"note"`
	Photos []ConsolePhotoCredit `json:"photos"`
}

// consolePhotoCreditsManifest is parsed once from the embedded CREDITS.json.
// HTML entities in the upstream author/title strings (e.g. "&amp;") are decoded
// so the credits render cleanly. Empty if the manifest is missing or malformed —
// console_photo_test.go guards against that.
var consolePhotoCreditsManifest = func() ConsolePhotoCreditsResponse {
	var raw struct {
		Note   string               `json:"note"`
		Photos []ConsolePhotoCredit `json:"photos"`
	}
	data, err := consolePhotos.ReadFile("static/console-photos/CREDITS.json")
	if err != nil {
		return ConsolePhotoCreditsResponse{}
	}
	if err := json.Unmarshal(data, &raw); err != nil {
		return ConsolePhotoCreditsResponse{}
	}
	out := ConsolePhotoCreditsResponse{Note: raw.Note, Photos: make([]ConsolePhotoCredit, 0, len(raw.Photos))}
	for _, p := range raw.Photos {
		out.Photos = append(out.Photos, ConsolePhotoCredit{
			Console: p.Console,
			Title:   html.UnescapeString(p.Title),
			Author:  html.UnescapeString(p.Author),
			License: p.License,
			Source:  p.Source,
		})
	}
	return out
}()

// ConsolePhotoCreditsInput is the (empty) input for GET /api/console-photo-credits.
type ConsolePhotoCreditsInput struct{}

// ConsolePhotoCreditsOutput wraps the attribution manifest.
type ConsolePhotoCreditsOutput struct {
	Body ConsolePhotoCreditsResponse
}

// HumaGetConsolePhotoCredits serves the attribution manifest for the bundled
// console hardware photos so clients can display proper credits. Public, no auth.
func (h *ConsoleHandler) HumaGetConsolePhotoCredits(_ context.Context, _ *ConsolePhotoCreditsInput) (*ConsolePhotoCreditsOutput, error) {
	return &ConsolePhotoCreditsOutput{Body: consolePhotoCreditsManifest}, nil
}
