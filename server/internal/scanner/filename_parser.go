package scanner

import (
	"path/filepath"
	"regexp"
	"sort"
	"strings"
)

// FilenameMetadata holds structured metadata extracted from a ROM filename.
type FilenameMetadata struct {
	Region       string // e.g. "USA", "Japan, Europe"
	Revision     string // e.g. "Rev 1", "Rev A", "v1.1"
	Tags         string // comma-separated lowercase, e.g. "beta,unl"
	IsPreRelease bool   // true when tags contain beta, proto, sample, or demo
	GroupKey     string // normalized title for variant grouping
}

// revisionPattern matches revision tags like (Rev 1), (Rev A), (Rev B).
var revisionPattern = regexp.MustCompile(`(?i)\(Rev\s+([^)]+)\)`)

// versionPattern matches version tags like (v1.0), (v1.1), (v2.0).
var versionPattern = regexp.MustCompile(`(?i)\(v(\d+\.\d+[^)]*)\)`)

// parenContentPattern extracts the content of each parenthesized group.
var parenContentPattern = regexp.MustCompile(`\(([^)]*)\)`)

// bracketContentPattern extracts the content of each bracketed group.
var bracketContentPattern = regexp.MustCompile(`\[([^\]]*)\]`)

// tagKeywords maps lowercase tag keywords to their canonical form.
// Tags that indicate pre-release status are marked separately.
var preReleaseTags = map[string]bool{
	"beta":  true,
	"proto": true,
	"sample": true,
	"demo":  true,
}

// allKnownTags are tags we extract from filenames.
var allKnownTags = map[string]string{
	"beta":            "beta",
	"proto":           "proto",
	"prototype":       "proto",
	"sample":          "sample",
	"demo":            "demo",
	"unl":             "unl",
	"unlicensed":      "unl",
	"hack":            "hack",
	"pirate":          "pirate",
	"virtual console": "virtual console",
	"switch online":   "switch online",
}

// Regexes from scraper/namematch.go for normalization, reused here.
var (
	fnReParens   = regexp.MustCompile(`\([^)]*\)`)
	fnReBrackets = regexp.MustCompile(`\[[^\]]*\]`)
	fnRePunct    = regexp.MustCompile(`[.,'":;!?\-_/\\+]+`)
	fnReSpaces   = regexp.MustCompile(`\s+`)
)

// accentMap folds common accented characters to ASCII equivalents.
var fnAccentMap = map[rune]rune{
	'à': 'a', 'á': 'a', 'â': 'a', 'ã': 'a', 'ä': 'a', 'å': 'a',
	'è': 'e', 'é': 'e', 'ê': 'e', 'ë': 'e',
	'ì': 'i', 'í': 'i', 'î': 'i', 'ï': 'i',
	'ò': 'o', 'ó': 'o', 'ô': 'o', 'õ': 'o', 'ö': 'o',
	'ù': 'u', 'ú': 'u', 'û': 'u', 'ü': 'u',
	'ý': 'y', 'ÿ': 'y',
	'ñ': 'n', 'ç': 'c',
	'À': 'a', 'Á': 'a', 'Â': 'a', 'Ã': 'a', 'Ä': 'a', 'Å': 'a',
	'È': 'e', 'É': 'e', 'Ê': 'e', 'Ë': 'e',
	'Ì': 'i', 'Í': 'i', 'Î': 'i', 'Ï': 'i',
	'Ò': 'o', 'Ó': 'o', 'Ô': 'o', 'Õ': 'o', 'Ö': 'o',
	'Ù': 'u', 'Ú': 'u', 'Û': 'u', 'Ü': 'u',
	'Ý': 'y',
	'Ñ': 'n', 'Ç': 'c',
}

// fnRegionPattern matches parenthesized region tags in No-Intro filenames.
// Duplicated from scraper/region.go to avoid import cycle (scraper imports scanner).
var fnRegionPattern = regexp.MustCompile(`\(([^)]*(?:` +
	`USA|Japan|Europe|World|Korea|Brazil|France|Germany|Spain|Italy|` +
	`Australia|Netherlands|Sweden|Canada|China|Taiwan|Asia|Russia|` +
	`UK|En|Ja|Fr|De|Es|It|Pt|Zh|Ko|Nl|Sv|No|Da|Fi|Pl|Ru` +
	`)[^)]*)\)`)

// extractRegion parses a region tag from a filename.
// Returns the region string without parentheses, or empty string if none found.
func extractRegion(filename string) string {
	match := fnRegionPattern.FindStringSubmatch(filename)
	if len(match) < 2 {
		return ""
	}
	return match[1]
}

// ParseFilenameMetadata extracts structured metadata from a ROM filename.
// Uses no-intro naming conventions: "Game Name (Region) (Rev X) [Tags].ext"
func ParseFilenameMetadata(filename string) FilenameMetadata {
	var meta FilenameMetadata

	// Extract region
	meta.Region = extractRegion(filename)

	// Extract revision
	if match := revisionPattern.FindStringSubmatch(filename); len(match) >= 2 {
		meta.Revision = "Rev " + strings.TrimSpace(match[1])
	} else if match := versionPattern.FindStringSubmatch(filename); len(match) >= 2 {
		meta.Revision = "v" + strings.TrimSpace(match[1])
	}

	// Extract tags from parenthesized and bracketed groups
	var tags []string
	seen := make(map[string]bool)

	// Check parenthesized groups
	for _, match := range parenContentPattern.FindAllStringSubmatch(filename, -1) {
		content := strings.ToLower(strings.TrimSpace(match[1]))
		for keyword, canonical := range allKnownTags {
			if strings.Contains(content, keyword) && !seen[canonical] {
				tags = append(tags, canonical)
				seen[canonical] = true
			}
		}
	}

	// Check bracketed groups
	for _, match := range bracketContentPattern.FindAllStringSubmatch(filename, -1) {
		content := strings.ToLower(strings.TrimSpace(match[1]))
		for keyword, canonical := range allKnownTags {
			if strings.Contains(content, keyword) && !seen[canonical] {
				tags = append(tags, canonical)
				seen[canonical] = true
			}
		}
	}

	sort.Strings(tags)
	meta.Tags = strings.Join(tags, ",")

	// Determine pre-release status
	for _, tag := range tags {
		if preReleaseTags[tag] {
			meta.IsPreRelease = true
			break
		}
	}

	// Compute group key: normalized title for variant grouping
	meta.GroupKey = normalizeGroupKey(filename)

	return meta
}

// NormalizeGroupKey produces a normalized title for grouping variants.
// Exported for use by other packages (e.g. ROM hack creation).
func NormalizeGroupKey(title string) string {
	return normalizeGroupKey(title)
}

// normalizeGroupKey produces a normalized title from a filename for grouping variants.
// Mirrors the normalization approach from scraper/namematch.go's normalizeName().
func normalizeGroupKey(filename string) string {
	// Remove extension
	name := strings.TrimSuffix(filename, filepath.Ext(filename))

	// Strip (...) and [...] content
	name = fnReParens.ReplaceAllString(name, "")
	name = fnReBrackets.ReplaceAllString(name, "")

	// Trim whitespace left over from stripped tags before checking articles
	name = strings.TrimSpace(name)

	// Handle articles: trailing ", The" / ", A" / ", An"
	for _, article := range []string{", The", ", A", ", An"} {
		if strings.HasSuffix(name, article) {
			name = name[:len(name)-len(article)]
			break
		}
	}
	// Leading "The " / "A " / "An "
	for _, article := range []string{"The ", "A ", "An "} {
		if strings.HasPrefix(name, article) {
			name = name[len(article):]
			break
		}
	}

	// Fold accented characters
	var buf strings.Builder
	buf.Grow(len(name))
	for _, r := range name {
		if mapped, ok := fnAccentMap[r]; ok {
			buf.WriteRune(mapped)
		} else {
			buf.WriteRune(r)
		}
	}
	name = buf.String()

	// Lowercase
	name = strings.ToLower(name)

	// Replace & with "and", strip apostrophes, strip punctuation
	name = strings.ReplaceAll(name, "&", "and")
	name = strings.ReplaceAll(name, "'", "")
	name = strings.ReplaceAll(name, "\u2019", "")
	name = fnRePunct.ReplaceAllString(name, " ")

	// Collapse whitespace, trim
	name = fnReSpaces.ReplaceAllString(name, " ")
	name = strings.TrimSpace(name)

	return name
}
