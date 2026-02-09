package scraper

import (
	"math"
	"regexp"
	"strings"
)

// regexes used by normalizeName, compiled once.
var (
	reParens   = regexp.MustCompile(`\([^)]*\)`)
	reBrackets = regexp.MustCompile(`\[[^\]]*\]`)
	rePunct    = regexp.MustCompile(`[.,'":;!?\-_/\\+]+`)
	reSpaces   = regexp.MustCompile(`\s+`)
)

// accentMap folds common accented characters to ASCII equivalents.
var accentMap = map[rune]rune{
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

// normalizeName transforms a game name (ROM filename or LibRetro listing entry)
// into a canonical form for comparison.
func normalizeName(name string) string {
	// 1. Strip file extensions
	for _, ext := range []string{".nes", ".sfc", ".smc", ".gba", ".gbc", ".gb", ".n64", ".z64", ".v64",
		".nds", ".sms", ".gg", ".gen", ".md", ".bin", ".iso", ".cue", ".img",
		".psx", ".pbp", ".cso", ".png", ".jpg", ".jpeg"} {
		if strings.HasSuffix(strings.ToLower(name), ext) {
			name = name[:len(name)-len(ext)]
			break
		}
	}

	// 2. Strip (...) and [...] content
	name = reParens.ReplaceAllString(name, "")
	name = reBrackets.ReplaceAllString(name, "")

	// Trim whitespace left over from stripped tags before checking articles
	name = strings.TrimSpace(name)

	// 3. Handle articles: trailing ", The" / ", A" / ", An"
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

	// 4. Fold accented characters
	var buf strings.Builder
	buf.Grow(len(name))
	for _, r := range name {
		if mapped, ok := accentMap[r]; ok {
			buf.WriteRune(mapped)
		} else {
			buf.WriteRune(r)
		}
	}
	name = buf.String()

	// 5. Lowercase
	name = strings.ToLower(name)

	// 6. Replace & with "and", strip apostrophes (no space), strip remaining punctuation
	name = strings.ReplaceAll(name, "&", "and")
	name = strings.ReplaceAll(name, "'", "")
	name = strings.ReplaceAll(name, "\u2019", "") // right single quotation mark
	name = rePunct.ReplaceAllString(name, " ")

	// 7. Collapse whitespace, trim
	name = reSpaces.ReplaceAllString(name, " ")
	name = strings.TrimSpace(name)

	return name
}

// jaroWinkler computes the Jaro-Winkler similarity between two strings (0.0–1.0).
func jaroWinkler(s1, s2 string) float64 {
	if s1 == s2 {
		return 1.0
	}
	r1 := []rune(s1)
	r2 := []rune(s2)
	l1 := len(r1)
	l2 := len(r2)
	if l1 == 0 || l2 == 0 {
		return 0.0
	}

	// Matching window
	matchDist := int(math.Max(float64(l1), float64(l2)))/2 - 1
	if matchDist < 0 {
		matchDist = 0
	}

	s1Matches := make([]bool, l1)
	s2Matches := make([]bool, l2)

	matches := 0
	transpositions := 0

	// Count matches
	for i := 0; i < l1; i++ {
		lo := i - matchDist
		if lo < 0 {
			lo = 0
		}
		hi := i + matchDist + 1
		if hi > l2 {
			hi = l2
		}
		for j := lo; j < hi; j++ {
			if s2Matches[j] || r1[i] != r2[j] {
				continue
			}
			s1Matches[i] = true
			s2Matches[j] = true
			matches++
			break
		}
	}

	if matches == 0 {
		return 0.0
	}

	// Count transpositions
	k := 0
	for i := 0; i < l1; i++ {
		if !s1Matches[i] {
			continue
		}
		for !s2Matches[k] {
			k++
		}
		if r1[i] != r2[k] {
			transpositions++
		}
		k++
	}

	jaro := (float64(matches)/float64(l1) +
		float64(matches)/float64(l2) +
		float64(matches-transpositions/2)/float64(matches)) / 3.0

	// Winkler bonus for common prefix (up to 4 chars)
	prefixLen := 0
	for i := 0; i < min(min(l1, l2), 4); i++ {
		if r1[i] == r2[i] {
			prefixLen++
		} else {
			break
		}
	}

	return jaro + float64(prefixLen)*0.1*(1.0-jaro)
}

// computePriority ranks a LibRetro entry name by "cleanliness" (lower = better).
func computePriority(rawName string) int {
	lower := strings.ToLower(rawName)

	// 5: Beta/proto/hack/sample/unlicensed
	for _, tag := range []string{"(beta", "(proto", "(hack", "(sample", "(unl)", "(unlicensed", "(pirate"} {
		if strings.Contains(lower, tag) {
			return 5
		}
	}

	// 4: Bad dump/hack tags [b], [h], [t+]
	for _, tag := range []string{"[b]", "[b1]", "[b2]", "[h]", "[h1]", "[t+", "[t-", "[o", "[!]"} {
		// [!] is actually "verified good dump" in GoodTools but we deprioritize GoodTools entries
		if strings.Contains(lower, tag) {
			return 4
		}
	}

	// Count parenthesized groups
	parenCount := strings.Count(rawName, "(")

	// 3: GoodTools-style with date pattern like (1987-09)
	if matched, _ := regexp.MatchString(`\(\d{4}`, rawName); matched {
		return 3
	}

	// 2: Multiple parenthesized tags
	if parenCount >= 2 {
		// 1: Single revision tag like (Rev A) alongside region
		if strings.Contains(lower, "(rev ") || strings.Contains(lower, "(v1.") || strings.Contains(lower, "(v2.") {
			return 1
		}
		return 2
	}

	// 0: Clean No-Intro style "Game (USA)" or "Game (Europe)"
	return 0
}

// hasPreferredRegion checks if the raw name contains a preferred region tag.
func hasPreferredRegion(rawName string) bool {
	lower := strings.ToLower(rawName)
	return strings.Contains(lower, "(usa") || strings.Contains(lower, "(world") || strings.Contains(lower, "(us)")
}

// findBestMatch finds the best matching entry from the list for the given normalized query.
// Returns the match, the score, and whether a match was found.
func findBestMatch(normalizedQuery string, entries []nameEntry, threshold float64) (nameEntry, float64, bool) {
	if normalizedQuery == "" || len(entries) == 0 {
		return nameEntry{}, 0, false
	}

	var bestEntry nameEntry
	bestScore := 0.0
	found := false

	for _, e := range entries {
		var score float64

		// Tier 1: Exact normalized match
		if e.Normalized == normalizedQuery {
			score = 1.0
		} else if strings.HasPrefix(e.Normalized, normalizedQuery) || strings.HasPrefix(normalizedQuery, e.Normalized) {
			// Tier 2: Prefix match
			shorter := len(normalizedQuery)
			longer := len(e.Normalized)
			if shorter > longer {
				shorter, longer = longer, shorter
			}
			score = 0.85 + 0.10*float64(shorter)/float64(longer)
		} else if strings.Contains(e.Normalized, normalizedQuery) || strings.Contains(normalizedQuery, e.Normalized) {
			// Tier 2b: Contains match
			shorter := len(normalizedQuery)
			longer := len(e.Normalized)
			if shorter > longer {
				shorter, longer = longer, shorter
			}
			score = 0.85 + 0.10*float64(shorter)/float64(longer)
		} else {
			// Tier 3: Jaro-Winkler fuzzy
			score = jaroWinkler(normalizedQuery, e.Normalized)
		}

		if score < threshold && score < 1.0 {
			continue
		}

		// Tie-breaking: higher score > lower priority > preferred region
		if score > bestScore ||
			(score == bestScore && e.Priority < bestEntry.Priority) ||
			(score == bestScore && e.Priority == bestEntry.Priority && hasPreferredRegion(e.Raw) && !hasPreferredRegion(bestEntry.Raw)) {
			bestEntry = e
			bestScore = score
			found = true
		}
	}

	return bestEntry, bestScore, found
}

