package scraper

import (
	"testing"
)

func TestParseDirectoryListing(t *testing.T) {
	html := `<html>
<head><title>Index of /Nintendo - Nintendo Entertainment System/Named_Boxarts/</title></head>
<body>
<h1>Index of /Nintendo - Nintendo Entertainment System/Named_Boxarts/</h1>
<hr><pre><a href="../">../</a>
<a href="Castlevania%20(USA).png">Castlevania (USA).png</a>
<a href="Castlevania%20(Europe).png">Castlevania (Europe).png</a>
<a href="The%20Legend%20of%20Zelda%20(USA).png">The Legend of Zelda (USA).png</a>
<a href="Super%20Mario%20Bros.%20(World).png">Super Mario Bros. (World).png</a>
</pre><hr>
</body></html>`

	names := parseDirectoryListing(html)

	expected := []string{
		"Castlevania (USA)",
		"Castlevania (Europe)",
		"The Legend of Zelda (USA)",
		"Super Mario Bros. (World)",
	}

	if len(names) != len(expected) {
		t.Fatalf("parseDirectoryListing: got %d names, want %d", len(names), len(expected))
	}

	for i, name := range names {
		if name != expected[i] {
			t.Errorf("parseDirectoryListing[%d] = %q, want %q", i, name, expected[i])
		}
	}
}

func TestParseDirectoryListing_EmptyPage(t *testing.T) {
	html := `<html><body></body></html>`
	names := parseDirectoryListing(html)
	if len(names) != 0 {
		t.Errorf("parseDirectoryListing(empty): got %d names, want 0", len(names))
	}
}

func TestParseDirectoryListing_SkipsNonPng(t *testing.T) {
	html := `<a href="file.txt">file.txt</a><a href="image.png">image.png</a>`
	names := parseDirectoryListing(html)
	if len(names) != 1 {
		t.Fatalf("got %d names, want 1", len(names))
	}
	if names[0] != "image" {
		t.Errorf("got %q, want %q", names[0], "image")
	}
}

func TestComputePriority_TableDriven(t *testing.T) {
	tests := []struct {
		name     string
		input    string
		expected int
	}{
		{"clean USA", "Game (USA)", 0},
		{"clean Europe", "Game (Europe)", 0},
		{"clean World", "Game (World)", 0},
		{"revision", "Game (USA) (Rev A)", 1},
		{"version number", "Game (USA) (v1.0)", 1},
		{"multi-tag SGB", "Game (USA, Europe) (SGB Enhanced)", 2},
		{"multi-tag two regions", "Game (USA) (En,Fr,De)", 2},
		{"GoodTools date", "Game (1987-09)(Konami)(US)", 3},
		{"GoodTools date 2", "Game (2001)(Publisher)(EU)", 3},
		{"bad dump [b]", "Game (USA) [b]", 4},
		{"bad dump [b1]", "Game (USA) [b1]", 4},
		{"hack [h]", "Game (USA) [h]", 4},
		{"trainer [t+]", "Game (USA) [t+Eng1.0]", 4},
		{"beta", "Game (Beta)", 5},
		{"proto", "Game (Proto)", 5},
		{"hack in parens", "Game (Hack)", 5},
		{"sample", "Game (Sample)", 5},
		{"unlicensed", "Game (Unl)", 5},
		{"pirate", "Game (Pirate)", 5},
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
