package federation

import (
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestPolicyRoundTrip(t *testing.T) {
	in := map[DataClass]bool{DataClassStats: true, DataClassCatalog: false}
	encoded, err := MarshalPolicy(in)
	require.NoError(t, err)

	out, err := ParsePolicy(encoded)
	require.NoError(t, err)
	assert.True(t, out[DataClassStats])
	assert.False(t, out[DataClassCatalog])
}

func TestParsePolicy_EmptyDeniesAll(t *testing.T) {
	out, err := ParsePolicy("")
	require.NoError(t, err)
	assert.False(t, out[DataClassStats])
}

func TestCanConsumeAndShare(t *testing.T) {
	share, _ := MarshalPolicy(map[DataClass]bool{DataClassStats: true})
	consume, _ := MarshalPolicy(map[DataClass]bool{DataClassCatalog: true})
	peer := db.FederationPeer{SharePolicy: share, ConsumePolicy: consume}

	assert.True(t, CanShare(peer, DataClassStats))
	assert.False(t, CanShare(peer, DataClassCatalog))
	assert.True(t, CanConsume(peer, DataClassCatalog))
	assert.False(t, CanConsume(peer, DataClassStats))
}
