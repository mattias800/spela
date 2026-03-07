package scraper

import (
	"regexp"
	"strings"
)

// regionPattern matches parenthesized region tags in No-Intro filenames,
// e.g. "(USA)", "(Japan)", "(USA, Europe)", "(World)".
var regionPattern = regexp.MustCompile(`\(([^)]*(?:` +
	`USA|Japan|Europe|World|Korea|Brazil|France|Germany|Spain|Italy|` +
	`Australia|Netherlands|Sweden|Canada|China|Taiwan|Asia|Russia|` +
	`UK|En|Ja|Fr|De|Es|It|Pt|Zh|Ko|Nl|Sv|No|Da|Fi|Pl|Ru` +
	`)[^)]*)\)`)

// ExtractRegion parses a region tag from a filename (e.g. "(USA)", "(Japan, Europe)").
// Returns the region string without parentheses, or empty string if none found.
func ExtractRegion(filename string) string {
	match := regionPattern.FindStringSubmatch(filename)
	if len(match) < 2 {
		return ""
	}
	return match[1]
}

// hasNonPreferredRegion returns true if the name contains a region tag with
// at least one non-preferred region (anything other than USA, US, or World).
// Returns false for names with no region tags or only preferred regions.
func hasNonPreferredRegion(name string) bool {
	region := ExtractRegion(name)
	if region == "" {
		return false
	}
	for _, part := range strings.Split(region, ",") {
		part = strings.TrimSpace(strings.ToLower(part))
		if part != "usa" && part != "world" && part != "us" {
			return true
		}
	}
	return false
}
