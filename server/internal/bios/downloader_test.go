package bios

import (
	"crypto/md5"
	"encoding/hex"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// md5hex returns the hex-encoded MD5 of the given data.
func md5hex(data []byte) string {
	h := md5.Sum(data)
	return hex.EncodeToString(h[:])
}

func TestDownloadMissing_SuccessfulDownload(t *testing.T) {
	biosDir := t.TempDir()
	fileContent := []byte("fake bios content")
	contentMD5 := md5hex(fileContent)

	// Serve files that match registry entries for PSX
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Write(fileContent)
	}))
	defer server.Close()

	// Temporarily override registry to a single entry with known MD5
	origRegistry := make([]Entry, len(registry))
	copy(origRegistry, registry)
	registry = []Entry{
		{ConsoleID: "psx", FileName: "test_bios.bin", Description: "Test BIOS", MD5: contentMD5, Required: true},
	}
	defer func() { registry = origRegistry }()

	var progressCalls []DownloadProgress
	result := DownloadMissing(biosDir, server.URL, func(p DownloadProgress) {
		progressCalls = append(progressCalls, p)
	})

	assert.Equal(t, 1, result.Downloaded)
	assert.Equal(t, 0, result.Skipped)
	assert.Equal(t, 0, result.Failed)
	assert.Empty(t, result.Errors)

	// File should exist on disk
	data, err := os.ReadFile(filepath.Join(biosDir, "test_bios.bin"))
	require.NoError(t, err)
	assert.Equal(t, fileContent, data)

	// Progress callback should have been called
	require.Len(t, progressCalls, 1)
	assert.Equal(t, "downloaded", progressCalls[0].Status)
	assert.Equal(t, "test_bios.bin", progressCalls[0].FileName)
	assert.Equal(t, 1, progressCalls[0].Current)
	assert.Equal(t, 1, progressCalls[0].Total)
}

func TestDownloadMissing_SkipsExistingFiles(t *testing.T) {
	biosDir := t.TempDir()

	// Pre-create the file with enough data to pass the minimum size check (>= 1KB)
	bigContent := make([]byte, 2048)
	for i := range bigContent {
		bigContent[i] = byte(i % 256)
	}
	err := os.WriteFile(filepath.Join(biosDir, "existing.bin"), bigContent, 0644)
	require.NoError(t, err)

	origRegistry := make([]Entry, len(registry))
	copy(origRegistry, registry)
	registry = []Entry{
		// No MD5 set — skips checksum validation for existing files
		{ConsoleID: "psx", FileName: "existing.bin", Description: "Existing BIOS", Required: true},
	}
	defer func() { registry = origRegistry }()

	result := DownloadMissing(biosDir, "http://unused", nil)

	assert.Equal(t, 0, result.Downloaded)
	assert.Equal(t, 1, result.Skipped)
	assert.Equal(t, 0, result.Failed)
}

func TestDownloadMissing_MD5Mismatch(t *testing.T) {
	biosDir := t.TempDir()
	fileContent := []byte("wrong content")

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Write(fileContent)
	}))
	defer server.Close()

	origRegistry := make([]Entry, len(registry))
	copy(origRegistry, registry)
	registry = []Entry{
		{ConsoleID: "psx", FileName: "bad_md5.bin", Description: "Bad MD5", MD5: "0000000000000000000000000000dead", Required: true},
	}
	defer func() { registry = origRegistry }()

	result := DownloadMissing(biosDir, server.URL, nil)

	assert.Equal(t, 0, result.Downloaded)
	assert.Equal(t, 0, result.Skipped)
	assert.Equal(t, 1, result.Failed)
	assert.Len(t, result.Errors, 1)
	assert.Contains(t, result.Errors[0], "MD5 mismatch")

	// Temp file should have been cleaned up, no file on disk
	_, err := os.Stat(filepath.Join(biosDir, "bad_md5.bin"))
	assert.True(t, os.IsNotExist(err))
	_, err = os.Stat(filepath.Join(biosDir, "bad_md5.bin.tmp"))
	assert.True(t, os.IsNotExist(err))
}

func TestDownloadMissing_ServerError(t *testing.T) {
	biosDir := t.TempDir()

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
	}))
	defer server.Close()

	origRegistry := make([]Entry, len(registry))
	copy(origRegistry, registry)
	registry = []Entry{
		{ConsoleID: "gba", FileName: "missing_remote.bin", Description: "Not Found", MD5: "abc", Required: true},
	}
	defer func() { registry = origRegistry }()

	result := DownloadMissing(biosDir, server.URL, nil)

	assert.Equal(t, 0, result.Downloaded)
	assert.Equal(t, 1, result.Failed)
	assert.Contains(t, result.Errors[0], "HTTP 404")
}

func TestDownloadMissing_EmptyMD5Accepted(t *testing.T) {
	biosDir := t.TempDir()
	fileContent := []byte("sega cd bios")

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Write(fileContent)
	}))
	defer server.Close()

	origRegistry := make([]Entry, len(registry))
	copy(origRegistry, registry)
	registry = []Entry{
		{ConsoleID: "scd", FileName: "bios_CD_U.bin", Description: "Sega CD BIOS", MD5: "", Required: true},
	}
	defer func() { registry = origRegistry }()

	result := DownloadMissing(biosDir, server.URL, nil)

	assert.Equal(t, 1, result.Downloaded)
	assert.Equal(t, 0, result.Failed)

	// File should exist
	data, err := os.ReadFile(filepath.Join(biosDir, "bios_CD_U.bin"))
	require.NoError(t, err)
	assert.Equal(t, fileContent, data)
}

func TestDownloadMissing_OverrideURL(t *testing.T) {
	biosDir := t.TempDir()
	fileContent := []byte("neo geo bios content")

	// This server simulates the OverrideURL (archive.org)
	overrideServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Write(fileContent)
	}))
	defer overrideServer.Close()

	// This server simulates the default repo — should NOT be called
	defaultServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Error("default server should not be called for entries with OverrideURL")
		w.WriteHeader(http.StatusNotFound)
	}))
	defer defaultServer.Close()

	origRegistry := make([]Entry, len(registry))
	copy(origRegistry, registry)
	registry = []Entry{
		{ConsoleID: "neogeo", FileName: "neogeo.zip", Description: "Neo Geo BIOS", MD5: "", Required: true, OverrideURL: overrideServer.URL + "/neogeo.zip"},
	}
	defer func() { registry = origRegistry }()

	result := DownloadMissing(biosDir, defaultServer.URL, nil)

	assert.Equal(t, 1, result.Downloaded)
	assert.Equal(t, 0, result.Failed)

	data, err := os.ReadFile(filepath.Join(biosDir, "neogeo.zip"))
	require.NoError(t, err)
	assert.Equal(t, fileContent, data)
}

func TestDownloadMissing_NilProgressCallback(t *testing.T) {
	biosDir := t.TempDir()

	origRegistry := make([]Entry, len(registry))
	copy(origRegistry, registry)
	registry = []Entry{
		{ConsoleID: "psx", FileName: "skip_me.bin", Description: "Skip", MD5: "", Required: false},
	}
	defer func() { registry = origRegistry }()

	// Pre-create with enough data to pass minimum size check
	skipContent := make([]byte, 2048)
	os.WriteFile(filepath.Join(biosDir, "skip_me.bin"), skipContent, 0644)

	// Should not panic with nil callback
	result := DownloadMissing(biosDir, "http://unused", nil)
	assert.Equal(t, 1, result.Skipped)
}
