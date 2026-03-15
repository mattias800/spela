package scraper

import (
	"math"
	"strings"
	"testing"
)

func TestNormalizeName(t *testing.T) {
	tests := []struct {
		name     string
		input    string
		expected string
	}{
		// File extensions
		{
			name:     "strips .nes extension",
			input:    "Castlevania.nes",
			expected: "castlevania",
		},
		{
			name:     "strips .png extension",
			input:    "Castlevania (USA).png",
			expected: "castlevania",
		},
		{
			name:     "strips .sfc extension",
			input:    "Super Metroid.sfc",
			expected: "super metroid",
		},

		// Region tags
		{
			name:     "strips single region tag",
			input:    "Castlevania (USA)",
			expected: "castlevania",
		},
		{
			name:     "strips multiple region tags",
			input:    "Castlevania (USA, Europe)",
			expected: "castlevania",
		},

		// Bracket tags
		{
			name:     "strips bracket tags",
			input:    "Castlevania [!]",
			expected: "castlevania",
		},
		{
			name:     "strips region and bracket tags",
			input:    "Castlevania (USA) [!].nes",
			expected: "castlevania",
		},

		// Articles - trailing
		{
			name:     "strips trailing The",
			input:    "Legend of Zelda, The (USA) [!].nes",
			expected: "legend of zelda",
		},
		{
			name:     "strips trailing A",
			input:    "Boy and His Blob, A (USA)",
			expected: "boy and his blob",
		},

		// Articles - leading
		{
			name:     "strips leading The",
			input:    "The Legend of Zelda (USA).png",
			expected: "legend of zelda",
		},
		{
			name:     "strips leading A",
			input:    "A Boy and His Blob (USA)",
			expected: "boy and his blob",
		},

		// Accented characters
		{
			name:     "folds accented e",
			input:    "Pokémon Red Version (USA, Europe)",
			expected: "pokemon red version",
		},
		{
			name:     "folds accented u",
			input:    "Ünderground (USA)",
			expected: "underground",
		},

		// Punctuation and special characters
		{
			name:     "replaces ampersand with and",
			input:    "Chip & Dale (USA)",
			expected: "chip and dale",
		},
		{
			name:     "strips colons and hyphens",
			input:    "Mega Man X: Maverick Hunter - Special",
			expected: "mega man x maverick hunter special",
		},
		{
			name:     "strips apostrophes",
			input:    "Kirby's Adventure (USA)",
			expected: "kirbys adventure",
		},

		// Whitespace
		{
			name:     "collapses multiple spaces",
			input:    "Super   Mario   Bros",
			expected: "super mario bros",
		},
		{
			name:     "trims whitespace",
			input:    "  Super Mario Bros  ",
			expected: "super mario bros",
		},

		// Combined complex cases
		{
			name:     "complex: trailing The with region and extension",
			input:    "Legend of Zelda, The (USA) (Rev A).nes",
			expected: "legend of zelda",
		},
		{
			name:     "complex: leading The with region and extension",
			input:    "The Legend of Zelda (USA) (Rev A).png",
			expected: "legend of zelda",
		},

		// Edge cases
		{
			name:     "empty string",
			input:    "",
			expected: "",
		},
		{
			name:     "only extension",
			input:    ".nes",
			expected: "",
		},
		{
			name:     "only region tags",
			input:    "(USA) (Europe)",
			expected: "",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := normalizeName(tt.input)
			if got != tt.expected {
				t.Errorf("normalizeName(%q) = %q, want %q", tt.input, got, tt.expected)
			}
		})
	}
}

func TestJaroWinkler(t *testing.T) {
	tests := []struct {
		name    string
		s1      string
		s2      string
		wantMin float64
		wantMax float64
	}{
		{
			name:    "identical strings",
			s1:      "castlevania",
			s2:      "castlevania",
			wantMin: 1.0,
			wantMax: 1.0,
		},
		{
			name:    "empty strings",
			s1:      "",
			s2:      "",
			wantMin: 1.0,
			wantMax: 1.0,
		},
		{
			name:    "one empty string",
			s1:      "castlevania",
			s2:      "",
			wantMin: 0.0,
			wantMax: 0.0,
		},
		{
			name:    "similar strings with shared prefix",
			s1:      "castlevania",
			s2:      "castlevania ii",
			wantMin: 0.90,
			wantMax: 1.0,
		},
		{
			name:    "completely different strings",
			s1:      "castlevania",
			s2:      "mario",
			wantMin: 0.0,
			wantMax: 0.60,
		},
		{
			name:    "slightly different strings",
			s1:      "super mario bros",
			s2:      "super mario brothers",
			wantMin: 0.85,
			wantMax: 1.0,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := jaroWinkler(tt.s1, tt.s2)
			if got < tt.wantMin || got > tt.wantMax {
				t.Errorf("jaroWinkler(%q, %q) = %f, want [%f, %f]", tt.s1, tt.s2, got, tt.wantMin, tt.wantMax)
			}
		})
	}
}

func TestComputePriority(t *testing.T) {
	tests := []struct {
		name     string
		input    string
		expected int
	}{
		{"clean No-Intro", "Castlevania (USA)", 0},
		{"clean No-Intro Europe", "Castlevania (Europe)", 0},
		{"with revision", "Castlevania (USA) (Rev A)", 1},
		{"with version", "Castlevania (USA) (v1.1)", 1},
		{"multiple tags", "Castlevania (USA, Europe) (SGB Enhanced)", 2},
		{"GoodTools with date", "Castlevania (1987-09)(Konami)(US)", 3},
		{"bad dump tag", "Castlevania (USA) [b]", 4},
		{"hack tag", "Castlevania (USA) [h1]", 4},
		{"beta", "Castlevania (Beta)", 5},
		{"prototype", "Castlevania (Proto)", 5},
		{"hack in parens", "Castlevania (Hack)", 5},
		{"unlicensed", "Castlevania (Unl)", 5},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := computePriority(tt.input)
			if got != tt.expected {
				t.Errorf("computePriority(%q) = %d, want %d", tt.input, got, tt.expected)
			}
		})
	}
}

func TestFindBestMatch(t *testing.T) {
	// Helper to create entries
	entry := func(raw string) nameEntry {
		return nameEntry{
			Raw:        raw,
			Normalized: normalizeName(raw),
			Priority:   computePriority(raw),
		}
	}

	entries := []nameEntry{
		entry("Castlevania (USA)"),
		entry("Castlevania (Europe)"),
		entry("Castlevania (USA) (Rev A)"),
		entry("Castlevania (USA) [b]"),
		entry("The Legend of Zelda (USA)"),
		entry("The Legend of Zelda (Europe)"),
		entry("Super Mario Bros. (World)"),
		entry("Pokemon Red Version (USA, Europe)"),
		entry("Mega Man 2 (USA)"),
		entry("Mega Man 3 (USA)"),
		entry("Chip 'n Dale - Rescue Rangers (USA)"),
	}

	tests := []struct {
		name          string
		query         string
		wantRaw       string
		wantFound     bool
		wantScoreMin  float64
	}{
		{
			name:         "exact match after normalization",
			query:        "Castlevania.nes",
			wantRaw:      "Castlevania (USA)",
			wantFound:    true,
			wantScoreMin: 1.0,
		},
		{
			name:         "trailing The matches leading The",
			query:        "Legend of Zelda, The.nes",
			wantRaw:      "The Legend of Zelda (USA)",
			wantFound:    true,
			wantScoreMin: 1.0,
		},
		{
			name:         "leading The matches leading The",
			query:        "The Legend of Zelda",
			wantRaw:      "The Legend of Zelda (USA)",
			wantFound:    true,
			wantScoreMin: 1.0,
		},
		{
			name:         "scene tags stripped",
			query:        "Castlevania (USA) [!].nes",
			wantRaw:      "Castlevania (USA)",
			wantFound:    true,
			wantScoreMin: 1.0,
		},
		{
			name:         "prefers clean entry over bad dump",
			query:        "Castlevania",
			wantRaw:      "Castlevania (USA)",
			wantFound:    true,
			wantScoreMin: 1.0,
		},
		{
			name:         "partial title matches via prefix/contains",
			query:        "Pokemon Red",
			wantRaw:      "Pokemon Red Version (USA, Europe)",
			wantFound:    true,
			wantScoreMin: 0.85,
		},
		{
			name:      "no match returns false",
			query:     "Totally Unknown Game XYZ 99",
			wantRaw:   "",
			wantFound: false,
		},
		{
			name:         "super mario bros matches with period in source",
			query:        "Super Mario Bros",
			wantRaw:      "Super Mario Bros. (World)",
			wantFound:    true,
			wantScoreMin: 0.85,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			normalized := normalizeName(tt.query)
			match, score, found := findBestMatch(normalized, entries, 0.88)
			if found != tt.wantFound {
				t.Errorf("findBestMatch(%q): found = %v, want %v", tt.query, found, tt.wantFound)
				return
			}
			if !tt.wantFound {
				return
			}
			if match.Raw != tt.wantRaw {
				t.Errorf("findBestMatch(%q): got %q, want %q (score=%f)", tt.query, match.Raw, tt.wantRaw, score)
			}
			if score < tt.wantScoreMin {
				t.Errorf("findBestMatch(%q): score = %f, want >= %f", tt.query, score, tt.wantScoreMin)
			}
		})
	}
}

func TestFindBestMatch_MAMEShortNames(t *testing.T) {
	// MAME thumbnail directories use full game titles.
	// Short ROM names like "sf2" should match "Street Fighter II" etc.
	entry := func(raw string) nameEntry {
		return nameEntry{
			Raw:        raw,
			Normalized: normalizeName(raw),
			Priority:   computePriority(raw),
		}
	}

	mameEntries := []nameEntry{
		entry("Akka Arrh (Prototype)"),
		entry("Street Fighter II - The World Warrior (World 910522)"),
		entry("Metal Slug - Super Vehicle-001"),
		entry("Alien³ - The Gun (World)"),
	}

	tests := []struct {
		name      string
		query     string
		wantRaw   string
		wantFound bool
	}{
		{
			name:      "akkaarrh matches Akka Arrh",
			query:     "akkaarrh",
			wantRaw:   "Akka Arrh (Prototype)",
			wantFound: true,
		},
		{
			name:      "mslug matches Metal Slug",
			query:     "mslug",
			wantRaw:   "Metal Slug - Super Vehicle-001",
			wantFound: false, // too different after normalization
		},
		{
			name:      "alien3 matches Alien³",
			query:     "alien3",
			wantRaw:   "Alien³ - The Gun (World)",
			wantFound: false, // too different
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			match, _, found := findBestMatch(normalizeName(tt.query), mameEntries, 0.85)
			if found != tt.wantFound {
				t.Errorf("findBestMatch(%q): found = %v, want %v (matched: %q)", tt.query, found, tt.wantFound, match.Raw)
			}
			if found && match.Raw != tt.wantRaw {
				t.Errorf("findBestMatch(%q): raw = %q, want %q", tt.query, match.Raw, tt.wantRaw)
			}
		})
	}
}

func TestFindBestMatch_PrefersUSAOverJapan(t *testing.T) {
	entry := func(raw string) nameEntry {
		return nameEntry{
			Raw:        raw,
			Normalized: normalizeName(raw),
			Priority:   computePriority(raw),
		}
	}

	// Simulate LibRetro entries for Ice Climber with Japan listed first
	entries := []nameEntry{
		entry("Ice Climber (Japan)"),
		entry("Ice Climber (USA, Europe)"),
	}

	tests := []struct {
		name    string
		query   string
		wantRaw string
	}{
		{
			name:    "Japan ROM prefers USA entry",
			query:   "Ice Climber (Japan)",
			wantRaw: "Ice Climber (USA, Europe)",
		},
		{
			name:    "multi-region ROM prefers USA entry",
			query:   "Ice Climber (Japan, USA)",
			wantRaw: "Ice Climber (USA, Europe)",
		},
		{
			name:    "bare name prefers USA entry",
			query:   "Ice Climber",
			wantRaw: "Ice Climber (USA, Europe)",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			normalized := normalizeName(tt.query)
			match, _, found := findBestMatch(normalized, entries, 0.88)
			if !found {
				t.Fatalf("findBestMatch(%q): expected match, got none", tt.query)
			}
			if match.Raw != tt.wantRaw {
				t.Errorf("findBestMatch(%q): got %q, want %q", tt.query, match.Raw, tt.wantRaw)
			}
		})
	}
}

func TestFindBestMatch_EmptyInputs(t *testing.T) {
	_, _, found := findBestMatch("", []nameEntry{{Raw: "test", Normalized: "test"}}, 0.88)
	if found {
		t.Error("expected no match for empty query")
	}

	_, _, found = findBestMatch("test", nil, 0.88)
	if found {
		t.Error("expected no match for empty entries")
	}
}

func TestFindAllMatches(t *testing.T) {
	entry := func(raw string) nameEntry {
		return nameEntry{
			Raw:        raw,
			Normalized: normalizeName(raw),
			Priority:   computePriority(raw),
		}
	}

	entries := []nameEntry{
		entry("Castlevania (USA)"),
		entry("Castlevania (Europe)"),
		entry("Castlevania (Japan)"),
		entry("Castlevania (USA) (Rev A)"),
		entry("Castlevania (USA) [b]"),
		entry("Castlevania (Beta)"),
		entry("Super Mario Bros. (World)"),
		entry("Totally Different Game (USA)"),
	}

	t.Run("finds all regional variants of a game", func(t *testing.T) {
		normalized := normalizeName("Castlevania")
		matches := findAllMatches(normalized, entries, 0.88)
		// Should match all Castlevania entries (USA, Europe, Japan, Rev A, bad dump, beta)
		// but not Super Mario Bros or Totally Different Game
		if len(matches) < 3 {
			t.Errorf("expected at least 3 matches for Castlevania, got %d", len(matches))
		}
		for _, m := range matches {
			if !strings.Contains(m.Normalized, "castlevania") {
				t.Errorf("unexpected match: %q", m.Raw)
			}
		}
	})

	t.Run("empty query returns nil", func(t *testing.T) {
		matches := findAllMatches("", entries, 0.88)
		if matches != nil {
			t.Errorf("expected nil for empty query, got %d matches", len(matches))
		}
	})

	t.Run("no matches returns nil", func(t *testing.T) {
		matches := findAllMatches("nonexistent game xyz", entries, 0.88)
		if len(matches) != 0 {
			t.Errorf("expected 0 matches, got %d", len(matches))
		}
	})
}

// almostEqual checks if two floats are approximately equal.
func almostEqual(a, b, epsilon float64) bool {
	return math.Abs(a-b) < epsilon
}
