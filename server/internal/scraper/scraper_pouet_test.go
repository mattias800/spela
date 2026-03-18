package scraper

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestSanitizePouetDate(t *testing.T) {
	tests := []struct {
		input string
		want  string
	}{
		{"1993-08-01", "1993-08-01"},     // valid date unchanged
		{"1989-00-15", "1989-01-15"},     // zero month → 01
		{"1992-05-00", "1992-05-01"},     // zero day → 01
		{"1990-00-00", "1990-01-01"},     // both zero → 01
		{"1993", "1993"},                 // year only unchanged
		{"", ""},                         // empty unchanged
		{"2020-12-31", "2020-12-31"},     // valid date unchanged
	}
	for _, tt := range tests {
		t.Run(tt.input, func(t *testing.T) {
			assert.Equal(t, tt.want, sanitizePouetDate(tt.input))
		})
	}
}

func TestStripParenContent(t *testing.T) {
	tests := []struct {
		input string
		want  string
	}{
		{"State of the Art (Phenomena)", "State of the Art"},
		{"Demo (Group) (Extra)", "Demo"},
		{"No parens here", "No parens here"},
		{"(All parens)", ""},
	}
	for _, tt := range tests {
		t.Run(tt.input, func(t *testing.T) {
			assert.Equal(t, tt.want, stripParenContent(tt.input))
		})
	}
}
