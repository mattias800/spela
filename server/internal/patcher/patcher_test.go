package patcher

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestApply_IPS(t *testing.T) {
	rom := []byte("Hello, World!")
	patch := buildIPSPatch([]ipsRecord{
		{offset: 7, data: []byte("Go!!!")},
	})

	result, err := Apply(rom, patch, "patch.ips")
	require.NoError(t, err)
	assert.Equal(t, "Hello, Go!!!!", string(result))
}

func TestApply_BPS(t *testing.T) {
	source := []byte("Hello, World!")
	target := []byte("Hello, Go!!!")

	patch := buildBPSPatch(source, target)

	result, err := Apply(source, patch, "patch.bps")
	require.NoError(t, err)
	assert.Equal(t, target, result)
}

func TestApply_UPS(t *testing.T) {
	source := []byte("Hello, World!")
	target := []byte("Hello, Go!!!")

	patch := buildUPSPatch(source, target)

	result, err := Apply(source, patch, "patch.ups")
	require.NoError(t, err)
	assert.Equal(t, string(target), string(result))
}

func TestApply_UnsupportedFormat(t *testing.T) {
	rom := []byte("test")
	patch := []byte("data")

	_, err := Apply(rom, patch, "patch.xyz")
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "unsupported patch format")
}

func TestApply_CaseInsensitiveExtension(t *testing.T) {
	rom := []byte("Hello, World!")
	patch := buildIPSPatch([]ipsRecord{
		{offset: 7, data: []byte("Go!!!")},
	})

	result, err := Apply(rom, patch, "patch.IPS")
	require.NoError(t, err)
	assert.Equal(t, "Hello, Go!!!!", string(result))
}
