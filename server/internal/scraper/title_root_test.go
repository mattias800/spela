package scraper

import (
	"fmt"
	"testing"

	"github.com/spela/server/internal/igdb"
	"github.com/stretchr/testify/assert"
)

func TestResolveTitleRootIGDBID(t *testing.T) {
	id := func(v int) *int { return &v }

	tests := []struct {
		name  string
		start igdb.Game
		graph map[int]igdb.Game
		want  *uint
	}{
		{
			name:  "root uses own IGDB ID",
			start: igdb.Game{ID: 100},
			want:  uintPtrFromInt(100),
		},
		{
			name:  "one-hop parent game",
			start: igdb.Game{ID: 200, ParentGameID: id(100)},
			graph: map[int]igdb.Game{
				100: {ID: 100},
			},
			want: uintPtrFromInt(100),
		},
		{
			name:  "multi-hop parent chain",
			start: igdb.Game{ID: 300, ParentGameID: id(200)},
			graph: map[int]igdb.Game{
				200: {ID: 200, ParentGameID: id(100)},
				100: {ID: 100},
			},
			want: uintPtrFromInt(100),
		},
		{
			name:  "version parent chain",
			start: igdb.Game{ID: 300, VersionParentID: id(200)},
			graph: map[int]igdb.Game{
				200: {ID: 200, VersionParentID: id(100)},
				100: {ID: 100},
			},
			want: uintPtrFromInt(100),
		},
		{
			name:  "parent game takes priority over version parent",
			start: igdb.Game{ID: 300, ParentGameID: id(200), VersionParentID: id(999)},
			graph: map[int]igdb.Game{
				200: {ID: 200},
				999: {ID: 999},
			},
			want: uintPtrFromInt(200),
		},
		{
			name:  "cycle returns deepest distinct game",
			start: igdb.Game{ID: 100, ParentGameID: id(200)},
			graph: map[int]igdb.Game{
				200: {ID: 200, ParentGameID: id(100)},
			},
			want: uintPtrFromInt(200),
		},
		{
			name:  "depth cap returns deepest reached game",
			start: igdb.Game{ID: 1, ParentGameID: id(2)},
			graph: map[int]igdb.Game{
				2: {ID: 2, ParentGameID: id(3)},
				3: {ID: 3, ParentGameID: id(4)},
				4: {ID: 4, ParentGameID: id(5)},
				5: {ID: 5, ParentGameID: id(6)},
				6: {ID: 6, ParentGameID: id(7)},
				7: {ID: 7},
			},
			want: uintPtrFromInt(6),
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			fetch := func(id int) (*igdb.Game, error) {
				game, ok := tt.graph[id]
				if !ok {
					return nil, fmt.Errorf("missing IGDB game %d", id)
				}
				return &game, nil
			}

			got := resolveTitleRootIGDBID(tt.start, fetch)
			assert.Equal(t, tt.want, got)
		})
	}
}

func TestResolveTitleRootIGDBIDFetchFailureReturnsNil(t *testing.T) {
	parentID := 200

	got := resolveTitleRootIGDBID(
		igdb.Game{ID: 100, ParentGameID: &parentID},
		func(id int) (*igdb.Game, error) {
			return nil, fmt.Errorf("boom")
		},
	)

	assert.Nil(t, got)
}
