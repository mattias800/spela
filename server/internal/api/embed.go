package api

import "embed"

//go:embed static/console-icons
var consoleIcons embed.FS

//go:embed static/console-logos
var consoleLogos embed.FS

//go:embed static/console-logos-png
var consoleLogosPng embed.FS

// Console hardware photos from Wikimedia Commons (mostly public-domain Evan
// Amos; CC-BY-SA files attributed in static/console-photos/CREDITS.json).
// Bundled + served by us so we're self-contained if a file is moved/deleted
// upstream. Regenerate with server/scripts/fetch-console-photos.py. See #1441.
//
//go:embed static/console-photos
var consolePhotos embed.FS

//go:embed static/branding
var brandingAssets embed.FS
