package api

import "embed"

//go:embed static/console-icons
var consoleIcons embed.FS

//go:embed static/console-logos
var consoleLogos embed.FS

//go:embed static/console-logos-png
var consoleLogosPng embed.FS

//go:embed static/branding
var brandingAssets embed.FS
