package bios

import (
	"archive/zip"
	"crypto/md5"
	"encoding/hex"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/safehttp"
	"gorm.io/gorm"
)

// DefaultRepoBaseURL is the base URL for the retrobios BIOS repository.
// Previously "Abdess/retroarch_system" on master branch with flat folder names;
// renamed to "Abdess/retrobios" on main branch with manufacturer/system paths.
const DefaultRepoBaseURL = "https://raw.githubusercontent.com/Abdess/retrobios/main/bios"

// maxBiosDownloadBytes caps a single BIOS file/bundle download and
// maxBiosBundleBytes caps the total extracted size of a bundle archive, so a
// compromised source or a decompression bomb can't exhaust the disk (#1325).
const (
	maxBiosDownloadBytes = 512 << 20
	maxBiosBundleBytes   = 512 << 20
)

// DownloadProgress reports the status of a single file download.
type DownloadProgress struct {
	FileName  string `json:"fileName"`
	ConsoleID string `json:"consoleId"`
	Status    string `json:"status"` // "downloaded", "skipped", "failed"
	Error     string `json:"error"`
	Current   int    `json:"current"`
	Total     int    `json:"total"`
}

// DownloadError is one entry in a DownloadResult's error list. Carries the
// fields a system-event recorder or admin UI needs without the caller
// having to re-parse a free-form string. See StartAutoDownload + #918.
type DownloadError struct {
	FileName  string
	ConsoleID string
	URL       string
	Error     string
}

// String renders the error in the historical "filename: error" form so
// callers that just want the human-readable summary still work.
func (e DownloadError) String() string {
	return fmt.Sprintf("%s: %s", e.FileName, e.Error)
}

// DownloadResult summarises a completed DownloadMissing run.
type DownloadResult struct {
	Downloaded int
	Skipped    int
	Failed     int
	Errors     []DownloadError
}

// DownloadMissing downloads all BIOS files that are missing from biosDir.
// Files already present on disk are skipped. Downloaded files are validated
// against their registry MD5 (when available). The optional onProgress
// callback is invoked after each file is processed.
func DownloadMissing(biosDir, baseURL string, onProgress func(DownloadProgress)) DownloadResult {
	entries := Downloadable()
	result := DownloadResult{}
	total := len(entries)

	// SSRF-hardened client: BIOS sources (archive.org / github / buildbot)
	// redirect heavily, so re-validate every redirect hop against the
	// private-IP block list instead of following blindly (#1322). Content is
	// additionally MD5-gated below.
	client := safehttp.NewClient(30 * time.Second)

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
		//
		// Bundle entries: the sentinel file on disk is just one member
		// of the extracted archive, not the archive itself. The MD5
		// stored in the registry is the archive's hash, so we can't
		// validate the sentinel directly. Fall back to size-only check
		// for bundles; the archive MD5 still gates the actual download
		// path further down.
		destPath := entry.FilePath(biosDir)
		if info, err := os.Stat(destPath); err == nil {
			needsRedownload := false
			if info.Size() < 1024 {
				slog.Warn("BIOS file too small, re-downloading",
					"file", entry.FileName, "size", info.Size())
				needsRedownload = true
			} else if entry.MD5 != "" && !entry.Bundle {
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

		// Issue #1124(B): refuse to fetch entries lacking a registry
		// checksum. The downloader pulls from a third-party GitHub
		// account (Abdess/retrobios); without an MD5 we have no way
		// to detect a malicious commit or cache poisoning, and the
		// bytes are passed verbatim to the emulated CPU by libretro
		// cores. Files already on disk are still served (operator
		// uploaded them manually) — this only blocks the network
		// fetch path.
		if entry.MD5 == "" {
			progress.Status = "failed"
			progress.Error = "registry entry has no MD5 checksum; refusing untrusted auto-download (issue #1124)"
			result.Failed++
			result.Errors = append(result.Errors, DownloadError{
				FileName:  entry.FileName,
				ConsoleID: entry.ConsoleID,
				URL:       "",
				Error:     progress.Error,
			})
			if onProgress != nil {
				onProgress(progress)
			}
			continue
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
			result.Errors = append(result.Errors, DownloadError{
				FileName:  entry.FileName,
				ConsoleID: entry.ConsoleID,
				URL:       url,
				Error:     progress.Error,
			})
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
			result.Errors = append(result.Errors, DownloadError{
				FileName:  entry.FileName,
				ConsoleID: entry.ConsoleID,
				URL:       url,
				Error:     progress.Error,
			})
			if onProgress != nil {
				onProgress(progress)
			}
			continue
		}

		// Ensure the destination's parent directory exists. SubDir-style
		// entries need this for `<biosDir>/<SubDir>/`; Bundle entries
		// where FileName itself contains a relative path (e.g. the
		// PPSSPP buildbot archive's `PPSSPP/ppge_atlas.zim` sentinel)
		// also need it. Unconditional MkdirAll is harmless when the
		// parent is already biosDir.
		if err := os.MkdirAll(filepath.Dir(destPath), 0755); err != nil {
			resp.Body.Close()
			progress.Status = "failed"
			progress.Error = fmt.Sprintf("creating destination directory: %v", err)
			result.Failed++
			result.Errors = append(result.Errors, DownloadError{
				FileName:  entry.FileName,
				ConsoleID: entry.ConsoleID,
				URL:       url,
				Error:     progress.Error,
			})
			if onProgress != nil {
				onProgress(progress)
			}
			continue
		}

		// Write to .tmp file first
		tmpPath := destPath + ".tmp"
		tmpFile, err := os.OpenFile(tmpPath, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0644)
		if err != nil {
			resp.Body.Close()
			progress.Status = "failed"
			progress.Error = fmt.Sprintf("creating temp file: %v", err)
			result.Failed++
			result.Errors = append(result.Errors, DownloadError{
				FileName:  entry.FileName,
				ConsoleID: entry.ConsoleID,
				URL:       url,
				Error:     progress.Error,
			})
			if onProgress != nil {
				onProgress(progress)
			}
			continue
		}

		hasher := md5.New()
		writer := io.MultiWriter(tmpFile, hasher)
		// Cap the download so a compromised source can't fill the disk before
		// the MD5 check runs (#1325). +1 lets us detect an over-cap body.
		nCopied, copyErr := io.Copy(writer, io.LimitReader(resp.Body, maxBiosDownloadBytes+1))
		resp.Body.Close()
		tmpFile.Close()

		if copyErr == nil && nCopied > maxBiosDownloadBytes {
			os.Remove(tmpPath)
			progress.Status = "failed"
			progress.Error = fmt.Sprintf("download exceeded %d-byte cap", maxBiosDownloadBytes)
			result.Failed++
			result.Errors = append(result.Errors, DownloadError{
				FileName:  entry.FileName,
				ConsoleID: entry.ConsoleID,
				URL:       url,
				Error:     progress.Error,
			})
			if onProgress != nil {
				onProgress(progress)
			}
			continue
		}

		if copyErr != nil {
			os.Remove(tmpPath)
			progress.Status = "failed"
			progress.Error = fmt.Sprintf("downloading: %v", copyErr)
			result.Failed++
			result.Errors = append(result.Errors, DownloadError{
				FileName:  entry.FileName,
				ConsoleID: entry.ConsoleID,
				URL:       url,
				Error:     progress.Error,
			})
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
			result.Errors = append(result.Errors, DownloadError{
				FileName:  entry.FileName,
				ConsoleID: entry.ConsoleID,
				URL:       url,
				Error:     progress.Error,
			})
			if onProgress != nil {
				onProgress(progress)
			}
			continue
		}

		// Bundle entries (#911): the bytes we just downloaded are a
		// .zip archive — extract every file into <biosDir>/<SubDir>/
		// then delete the archive. The sentinel file at destPath is
		// expected to be one of the extracted files; the next pass of
		// the "is installed" check uses os.Stat(destPath) to detect
		// the bundle as installed. Failure rolls back the partial
		// extraction so a half-extracted bundle doesn't get treated
		// as installed on next launch.
		if entry.Bundle {
			extractRoot := biosDir
			if entry.SubDir != "" {
				extractRoot = filepath.Join(biosDir, entry.SubDir)
			}
			if err := os.MkdirAll(extractRoot, 0755); err != nil {
				os.Remove(tmpPath)
				progress.Status = "failed"
				progress.Error = fmt.Sprintf("creating bundle target: %v", err)
				result.Failed++
				result.Errors = append(result.Errors, DownloadError{
					FileName:  entry.FileName,
					ConsoleID: entry.ConsoleID,
					URL:       url,
					Error:     progress.Error,
				})
				if onProgress != nil {
					onProgress(progress)
				}
				continue
			}
			if err := extractZipBundle(tmpPath, extractRoot, entry.StripPrefix); err != nil {
				// Best-effort cleanup of any partial files written
				// before the failure so the bundle isn't half-
				// installed. We don't try to undo every extracted
				// path — just remove the sentinel if it landed,
				// which forces a re-download next attempt.
				os.Remove(destPath)
				os.Remove(tmpPath)
				progress.Status = "failed"
				progress.Error = fmt.Sprintf("extracting bundle: %v", err)
				result.Failed++
				result.Errors = append(result.Errors, DownloadError{
					FileName:  entry.FileName,
					ConsoleID: entry.ConsoleID,
					URL:       url,
					Error:     progress.Error,
				})
				if onProgress != nil {
					onProgress(progress)
				}
				continue
			}
			// Archive is no longer needed.
			os.Remove(tmpPath)
		} else {
			// Rename to final path (single-file entry)
			if err := os.Rename(tmpPath, destPath); err != nil {
				os.Remove(tmpPath)
				progress.Status = "failed"
				progress.Error = fmt.Sprintf("renaming temp file: %v", err)
				result.Failed++
				result.Errors = append(result.Errors, DownloadError{
					FileName:  entry.FileName,
					ConsoleID: entry.ConsoleID,
					URL:       url,
					Error:     progress.Error,
				})
				if onProgress != nil {
					onProgress(progress)
				}
				continue
			}
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
//
// Issue #1124: defaults to OFF. Auto-download trusts a third-party GitHub
// account (Abdess/retrobios) and BIOS bytes are passed verbatim to the
// emulated CPU by libretro cores; admins should opt in explicitly after
// reviewing the trust model in SECURITY.md.
func StartAutoDownload(biosDir string, database *gorm.DB) {
	// Read the setting; default to OFF unless an admin has explicitly
	// enabled auto-download. Pre-existing deployments that relied on
	// the old default-on behaviour will not silently start fetching
	// after this change — they need to flip the toggle in admin
	// settings.
	var setting db.ServerSetting
	enabled := false
	if err := database.Where("key = ?", "bios_auto_download").First(&setting).Error; err == nil {
		if setting.Value == "true" {
			enabled = true
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
		for _, e := range result.Errors {
			slog.Warn("BIOS download error", "detail", e.String())
			db.RecordOperationalEvent(database, db.SystemEventInput{
				EventType: db.SystemEventBIOSDownloadFailed,
				Metadata: db.BIOSDownloadFailedMetadata{
					FileName:  e.FileName,
					ConsoleID: e.ConsoleID,
					URL:       e.URL,
					Error:     e.Error,
				},
			})
		}
	}()
}

// extractZipBundle extracts every file in [archivePath] into [destDir],
// preserving the archive's relative directory structure. Used by the
// Bundle path in [DownloadMissing] (#911).
//
// When stripPrefix is non-empty, archive entries that start with that
// prefix have it removed before being joined onto destDir; entries
// that don't start with the prefix are skipped. Used to drop a
// wrapper directory baked into archives like the libretro PPSSPP
// buildbot zip (rooted at `PPSSPP/`) so its contents land at
// `<destDir>/...` rather than `<destDir>/PPSSPP/...`.
//
// Refuses to write outside destDir (defends against zip-slip — an
// archive entry whose name resolves to "../etc/passwd" can't escape
// the BIOS dir). Symlinks inside the archive are skipped, not
// followed, for the same reason.
func extractZipBundle(archivePath, destDir, stripPrefix string) error {
	r, err := zip.OpenReader(archivePath)
	if err != nil {
		return fmt.Errorf("opening archive: %w", err)
	}
	defer r.Close()

	absDest, err := filepath.Abs(destDir)
	if err != nil {
		return fmt.Errorf("resolving dest dir: %w", err)
	}

	var extracted int64 // running total of bytes written; bounds zip bombs (#1325)

	for _, zf := range r.File {
		// Reject absolute paths and traversal.
		if filepath.IsAbs(zf.Name) || strings.Contains(zf.Name, "..") {
			return fmt.Errorf("archive entry has unsafe path: %q", zf.Name)
		}

		name := zf.Name
		if stripPrefix != "" {
			if !strings.HasPrefix(name, stripPrefix) {
				// Archive contains files outside the wrapper we
				// expect — skip rather than spilling them next to
				// SubDir.
				continue
			}
			name = strings.TrimPrefix(name, stripPrefix)
			if name == "" {
				// The prefix directory entry itself.
				continue
			}
		}

		target := filepath.Join(destDir, filepath.FromSlash(name))
		absTarget, err := filepath.Abs(target)
		if err != nil {
			return fmt.Errorf("resolving target: %w", err)
		}
		if !strings.HasPrefix(absTarget, absDest+string(filepath.Separator)) && absTarget != absDest {
			return fmt.Errorf("archive entry escapes target dir: %q", zf.Name)
		}

		// Skip symlinks and other non-regular file types.
		if zf.Mode()&os.ModeSymlink != 0 {
			continue
		}

		if zf.FileInfo().IsDir() {
			if err := os.MkdirAll(target, 0755); err != nil {
				return fmt.Errorf("mkdir %s: %w", target, err)
			}
			continue
		}

		if err := os.MkdirAll(filepath.Dir(target), 0755); err != nil {
			return fmt.Errorf("mkdir parent of %s: %w", target, err)
		}

		out, err := os.OpenFile(target, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0644)
		if err != nil {
			return fmt.Errorf("creating %s: %w", target, err)
		}
		in, err := zf.Open()
		if err != nil {
			out.Close()
			return fmt.Errorf("opening archive entry %s: %w", zf.Name, err)
		}
		remaining := maxBiosBundleBytes - extracted
		n, err := io.Copy(out, io.LimitReader(in, remaining+1))
		out.Close()
		in.Close()
		if err != nil {
			return fmt.Errorf("writing %s: %w", target, err)
		}
		extracted += n
		if extracted > maxBiosBundleBytes {
			return fmt.Errorf("bundle extraction exceeded %d-byte cap (possible decompression bomb)", maxBiosBundleBytes)
		}
	}
	return nil
}
