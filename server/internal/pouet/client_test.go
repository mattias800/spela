package pouet

import (
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func testClient(handler http.HandlerFunc) *Client {
	server := httptest.NewServer(handler)
	ticker := time.NewTicker(1 * time.Millisecond)
	return &Client{
		HTTPClient: server.Client(),
		BaseURL:    server.URL,
		rateTick:   ticker.C,
	}
}

func TestSearchProd(t *testing.T) {
	c := testClient(func(w http.ResponseWriter, r *http.Request) {
		assert.Contains(t, r.URL.Path, "/search/prod/")
		assert.Equal(t, "second reality", r.URL.Query().Get("q"))
		w.Write([]byte(`{"results":[
			{"id":"3064","name":"Second Reality","types":["demo"],
			 "groups":[{"id":"44","name":"Future Crew","acronym":"FC"}],
			 "platforms":{"67":{"name":"MS-Dos","slug":"msdos"}},
			 "voteup":1200,"votepig":100,"votedown":20,
			 "releaseDate":"1993-08-01"}
		]}`))
	})

	prods, err := c.SearchProd("second reality")
	require.NoError(t, err)
	require.Len(t, prods, 1)
	assert.Equal(t, "3064", prods[0].ID)
	assert.Equal(t, "Second Reality", prods[0].Name)
	assert.Equal(t, "Future Crew", prods[0].Groups[0].Name)
	assert.Contains(t, prods[0].Platforms, "67")
}

func TestGetProd(t *testing.T) {
	c := testClient(func(w http.ResponseWriter, r *http.Request) {
		assert.Contains(t, r.URL.Path, "/prod/")
		assert.Equal(t, "3064", r.URL.Query().Get("id"))
		w.Write([]byte(`{"prod":{
			"id":"3064","name":"Second Reality","types":["demo"],
			"groups":[{"id":"44","name":"Future Crew"}],
			"platforms":{"67":{"name":"MS-Dos"}},
			"screenshot":"https://content.pouet.net/files/screenshots/00003/00003064.jpg",
			"voteup":1200,"votepig":100,"votedown":"20",
			"releaseDate":"1993-08-01",
			"party":{"id":"3","name":"Assembly"},
			"party_year":"1993",
			"party_compo_name":"pc demo",
			"party_place":"1",
			"placings":[{"party":{"id":"3","name":"Assembly"},"year":"1993","ranking":"1","compo_name":"pc demo"}]
		}}`))
	})

	prod, err := c.GetProd("3064")
	require.NoError(t, err)
	assert.Equal(t, "Second Reality", prod.Name)
	assert.Equal(t, "https://content.pouet.net/files/screenshots/00003/00003064.jpg", prod.Screenshot)
	assert.Len(t, prod.Placings, 1)
}

func TestFilterByPlatform(t *testing.T) {
	prods := []Prod{
		{ID: "1", Name: "DOS Demo", Platforms: map[string]Platform{"67": {Name: "MS-Dos"}}},
		{ID: "2", Name: "Windows Demo", Platforms: map[string]Platform{"68": {Name: "Windows"}}},
		{ID: "3", Name: "Amiga Demo", Platforms: map[string]Platform{"73": {Name: "Amiga OCS/ECS"}}},
		{ID: "4", Name: "Multi", Platforms: map[string]Platform{"67": {Name: "MS-Dos"}, "68": {Name: "Windows"}}},
	}

	dos := FilterByPlatform(prods, []int{67, 69})
	assert.Len(t, dos, 2)
	assert.Equal(t, "DOS Demo", dos[0].Name)
	assert.Equal(t, "Multi", dos[1].Name)

	amiga := FilterByPlatform(prods, []int{73, 71})
	assert.Len(t, amiga, 1)
	assert.Equal(t, "Amiga Demo", amiga[0].Name)
}

func TestCleanDemoName(t *testing.T) {
	tests := []struct {
		input string
		want  string
	}{
		{"State of the Art (Phenomena) [1992].adf", "State of the Art (Phenomena)"},
		{"Second_Reality.zip", "Second Reality"},
		{"Batman Rises (Batman Group) A.adf", "Batman Rises (Batman Group)"},
		{"BatmanVuelve_1.adf", "BatmanVuelve"},
		{"Enigma (Phenomena).adf", "Enigma (Phenomena)"},
		{"2nd Demo (Hypnotic) [1991].adf", "2nd Demo (Hypnotic)"},
		{"+6 dB (Teklords) [1998].zip", "+6 dB (Teklords)"},
	}
	for _, tt := range tests {
		t.Run(tt.input, func(t *testing.T) {
			assert.Equal(t, tt.want, CleanDemoName(tt.input))
		})
	}
}

func TestGroupNames(t *testing.T) {
	prod := &Prod{Groups: []Group{{Name: "Future Crew"}, {Name: "Remedy"}}}
	assert.Equal(t, "Future Crew & Remedy", GroupNames(prod))

	empty := &Prod{}
	assert.Equal(t, "", GroupNames(empty))
}

func TestPartyInfo(t *testing.T) {
	prod := &Prod{Placings: []Placement{
		{Party: Party{Name: "Assembly"}, Year: "1993", Ranking: "1", CompoName: "pc demo"},
	}}
	assert.Equal(t, "Assembly 1993, 1st place (pc demo)", PartyInfo(prod))

	noPlace := &Prod{Placings: []Placement{
		{Party: Party{Name: "Revision"}, Year: "2020", Ranking: "0", CompoName: "amiga demo"},
	}}
	assert.Equal(t, "Revision 2020 (amiga demo)", PartyInfo(noPlace))
}

func TestRating(t *testing.T) {
	prod := &Prod{VoteUp: "100", VotePig: "0", VoteDown: "0"}
	assert.InDelta(t, 100.0, Rating(prod), 0.1)

	mixed := &Prod{VoteUp: "50", VotePig: "30", VoteDown: "20"}
	assert.InDelta(t, 65.0, Rating(mixed), 0.1)

	none := &Prod{}
	assert.Equal(t, 0.0, Rating(none))
}
