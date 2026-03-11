package igdb

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestCountryName(t *testing.T) {
	tests := []struct {
		name     string
		code     int
		expected string
	}{
		{"Japan", 392, "Japan"},
		{"United States", 840, "United States"},
		{"United Kingdom", 826, "United Kingdom"},
		{"Sweden", 752, "Sweden"},
		{"Canada", 124, "Canada"},
		{"France", 250, "France"},
		{"Germany", 276, "Germany"},
		{"zero code", 0, ""},
		{"unknown code", 999, ""},
		{"negative code", -1, ""},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, CountryName(tt.code))
		})
	}
}

func TestCountryCodeToName_Coverage(t *testing.T) {
	// Verify all entries in the map return non-empty names
	for code, name := range CountryCodeToName {
		assert.NotEmpty(t, name, "code %d should have a non-empty name", code)
		assert.Equal(t, name, CountryName(code))
	}
}
