package bios

import (
	"crypto/md5"
	"encoding/hex"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"os"
	"path/filepath"
	"time"

	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// DefaultRepoBaseURL is the base URL for the retrobios BIOS repository.
// Previously "Abdess/retroarch_system" on master branch with flat folder names;
// renamed to "Abdess/retrobios" on main branch with manufacturer/system paths.
const DefaultRepoBaseURL = "https://raw.githubusercontent.com/Abdess/retrobios/main/bios"

// DownloadProgress reports the status of a single file download.
type DownloadProgress struct {
	FileName  string `json:"fileName"`
	ConsoleID string `json:"consoleId"`
	Status    string `json:"status"` // "downloaded", "skipped", "failed"
	Error     string `json:"error,omitempty"`
	Current   int    `json:"current"`
	Total     int    `json:"total"`
}

// DownloadResult summarises a completed DownloadMissing run.
type DownloadResult struct {
	Downloaded int
	Skipped    int
	Failed     int
	Errors     []string
}

// DownloadMissing downloads all BIOS files that are missing from biosDir.
// Files already present on disk are skipped. Downloaded files are validated
// against their registry MD5 (when available). The optional onProgress
// callback is invoked after each file is processed.
func DownloadMissing(biosDir, baseURL string, onProgress func(DownloadProgress)) DownloadResult {
	entries := Downloadable()
	result := DownloadResult{}
	total := len(entries)

	client := &http.Client{Timeout: 30 * time.Second}

	for i, entry := range entries {
		progress := DownloadProgress{
			FileName:  entry.FileName,
			ConsoleID: entry.ConsoleID,
			Current:   i + 1,
			Total:     total,
		}

		// Skip if already present and valid.
		// Re-download if the file is suspiciously small (< 1KB, likely a
		// placeholder from a failed earlier download) or if it fails MD5
		// validation when a checksum is available.
		destPath := entry.FilePath(biosDir)
		if info, err := os.Stat(destPath); err == nil {
			needsRedownload := false
			if info.Size() < 1024 {
				slog.Warn("BIOS file too small, re-downloading",
					"file", entry.FileName, "size", info.Size())
				needsRedownload = true
			} else if entry.MD5 != "" {
				if f, err := os.Open(destPath); err == nil {
					h := md5.New()
					io.Copy(h, f)
					f.Close()
					actual := hex.EncodeToString(h.Sum(nil))
					if actual != entry.MD5 {
						slog.Warn("BIOS file MD5 mismatch, re-downloading",
							"file", entry.FileName, "expected", entry.MD5, "actual", actual)
						needsRedownload = true
					}
				}
			}
			if needsRedownload {
				os.Remove(destPath)
			} else {
				progress.Status = "skipped"
				result.Skipped++
				if onProgress != nil {
					onProgress(progress)
				}
				continue
			}
		}

		// Use OverrideURL if set, otherwise build from repo base URL
		var url string
		if entry.OverrideURL != "" {
			url = entry.OverrideURL
		} else {
			folder := RepoFolder(entry.ConsoleID)
			url = fmt.Sprintf("%s/%s/%s", baseURL, folder, entry.FileName)
		}

		resp, err := client.Get(url)
		if err != nil {
			progress.Status = "failed"
			progress.Error = fmt.Sprintf("HTTP request failed: %v", err)
			result.Failed++
			result.Errors = append(result.Errors, fmt.Sprintf("%s: %s", entry.FileName, progress.Error))
			if onProgress != nil {
				onProgress(progress)
			}
			continue
		}

		if resp.StatusCode != http.StatusOK {
			resp.Body.Close()
			progress.Status = "failed"
			progress.Error = fmt.Sprintf("HTTP %d", resp.StatusCode)
			result.Failed++
			result.Errors = append(result.Errors, fmt.Sprintf("%s: %s", entry.FileName, progress.Error))
			if onProgress != nil {
				onProgress(progress)
			}
			continue
		}

		// Ensure subdirectory exists for entries that need it
		if entry.SubDir != "" {
			if err := os.MkdirAll(filepath.Dir(destPath), 0755); err != nil {
				resp.Body.Close()
				progress.Status = "failed"
				progress.Error = fmt.Sprintf("creating subdirectory: %v", err)
				result.Failed++
				result.Errors = append(result.Errors, fmt.Sprintf("%s: %s", entry.FileName, progress.Error))
				if onProgress != nil {
					onProgress(progress)
				}
				continue
			}
		}

		// Write to .tmp file first
		tmpPath := destPath + ".tmp"
		tmpFile, err := os.OpenFile(tmpPath, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0644)
		if err != nil {
			resp.Body.Close()
			progress.Status = "failed"
			progress.Error = fmt.Sprintf("creating temp file: %v", err)
			result.Failed++
			result.Errors = append(result.Errors, fmt.Sprintf("%s: %s", entry.FileName, progress.Error))
			if onProgress != nil {
				onProgress(progress)
			}
			continue
		}

		hasher := md5.New()
		writer := io.MultiWriter(tmpFile, hasher)
		_, copyErr := io.Copy(writer, resp.Body)
		resp.Body.Close()
		tmpFile.Close()

		if copyErr != nil {
			os.Remove(tmpPath)
			progress.Status = "failed"
			progress.Error = fmt.Sprintf("downloading: %v", copyErr)
			result.Failed++
			result.Errors = append(result.Errors, fmt.Sprintf("%s: %s", entry.FileName, progress.Error))
			if onProgress != nil {
				onProgress(progress)
			}
			continue
		}

		// Validate MD5 if the registry has a checksum
		actualMD5 := hex.EncodeToString(hasher.Sum(nil))
		if entry.MD5 != "" && actualMD5 != entry.MD5 {
			os.Remove(tmpPath)
			progress.Status = "failed"
			progress.Error = fmt.Sprintf("MD5 mismatch: expected %s, got %s", entry.MD5, actualMD5)
			result.Failed++
			result.Errors = append(result.Errors, fmt.Sprintf("%s: %s", entry.FileName, progress.Error))
			if onProgress != nil {
				onProgress(progress)
			}
			continue
		}

		// Rename to final path
		if err := os.Rename(tmpPath, destPath); err != nil {
			os.Remove(tmpPath)
			progress.Status = "failed"
			progress.Error = fmt.Sprintf("renaming temp file: %v", err)
			result.Failed++
			result.Errors = append(result.Errors, fmt.Sprintf("%s: %s", entry.FileName, progress.Error))
			if onProgress != nil {
				onProgress(progress)
			}
			continue
		}

		progress.Status = "downloaded"
		result.Downloaded++
		if onProgress != nil {
			onProgress(progress)
		}
	}

	return result
}

// StartAutoDownload checks the bios_auto_download setting and, if enabled,
// spawns a goroutine that downloads missing BIOS files at startup.
func StartAutoDownload(biosDir string, database *gorm.DB) {
	// Read the setting; default to "true" if not set
	var setting db.ServerSetting
	enabled := true
	if err := database.Where("key = ?", "bios_auto_download").First(&setting).Error; err == nil {
		if setting.Value == "false" {
			enabled = false
		}
	}

	if !enabled {
		slog.Info("BIOS auto-download disabled by server setting")
		return
	}

	go func() {
		slog.Info("BIOS auto-download starting")
		result := DownloadMissing(biosDir, DefaultRepoBaseURL, nil)
		slog.Info("BIOS auto-download complete",
			"downloaded", result.Downloaded,
			"skipped", result.Skipped,
			"failed", result.Failed,
		)
		if len(result.Errors) > 0 {
			for _, e := range result.Errors {
				slog.Warn("BIOS download error", "detail", e)
			}
		}
	}()
}
