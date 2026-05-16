package cores

import (
	"archive/zip"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"math/rand"
	"net/http"
	"os"
	"path/filepath"
	"time"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/safehttp"
	"gorm.io/gorm"
)

// downloadMaxBytes caps the size of any single buildbot fetch so a runaway
// asset (or a server that streams forever) can't OOM the process. The
// largest current libretro core sits at ~50 MB; 200 MB gives us 4x headroom
// without trusting upstream to be sane.
const downloadMaxBytes = 200 * 1024 * 1024

// PollerOptions configures the buildbot poller. CoreDir is the directory
// where the server writes per-platform binaries — same directory the
// admin upload path already uses. PollMatrix is the (platform, arch) set
// to poll for each buildbot-default core; defaults to DefaultPollMatrix
// when unset. Interval is the gap between full poll cycles; jittered so
// every server pinned to the libretro buildbot doesn't hit it at the
// same second.
type PollerOptions struct {
	CoreDir    string
	PollMatrix []PlatformArch
	Interval   time.Duration

	// HTTPClient is used for all outbound buildbot fetches. Defaults to
	// safehttp.NewClient(60s) when nil — production should leave it nil.
	// Tests override it to inject httptest.Server URLs.
	HTTPClient *http.Client

	// BaseURLOverride replaces the buildbot host for tests. When non-empty,
	// `https://buildbot.libretro.com/nightly` is rewritten to this base.
	// Production must leave it empty.
	BaseURLOverride string

	// Clock returns the current time. Defaults to time.Now when nil; tests
	// substitute a fixed clock so FetchedAt assertions are stable.
	Clock func() time.Time
}

// Poller is the background worker that keeps CorePlatformBinary rows in
// sync with libretro buildbot nightlies. Construct via NewPoller and run
// via Run(ctx). Safe to start at most once per process.
type Poller struct {
	db   *gorm.DB
	opts PollerOptions
}

// NewPoller constructs a Poller with sensible defaults filled in.
func NewPoller(database *gorm.DB, opts PollerOptions) *Poller {
	if opts.HTTPClient == nil {
		opts.HTTPClient = safehttp.NewClient(60 * time.Second)
	}
	if len(opts.PollMatrix) == 0 {
		opts.PollMatrix = DefaultPollMatrix
	}
	if opts.Interval <= 0 {
		opts.Interval = 24 * time.Hour
	}
	if opts.Clock == nil {
		opts.Clock = func() time.Time { return time.Now().UTC() }
	}
	return &Poller{db: database, opts: opts}
}

// Run starts the poll loop. Blocks until ctx is cancelled.
//
// Cadence: initial sleep of ~5 minutes (so a freshly booted server isn't
// bandwidth-spiky during startup), then RunOnce + sleep(interval+jitter)
// in a loop. Errors from RunOnce are logged and swallowed — the worker
// MUST stay alive across transient failures or one bad buildbot day
// permanently disables auto-updates.
func (p *Poller) Run(ctx context.Context) {
	slog.Info("cores: buildbot poller started",
		"interval", p.opts.Interval,
		"matrix_size", len(p.opts.PollMatrix),
	)

	// Initial delay; randomized so a fleet of servers booted in lockstep
	// (Kubernetes rolling restart) doesn't pummel buildbot at once.
	initialDelay := 5*time.Minute + jitter(60*time.Second)
	select {
	case <-ctx.Done():
		slog.Info("cores: buildbot poller shutting down before first run")
		return
	case <-time.After(initialDelay):
	}

	for {
		if err := p.RunOnce(ctx); err != nil {
			slog.Warn("cores: poll cycle errored", "error", err)
		}
		wait := p.opts.Interval + jitter(p.opts.Interval/10)
		select {
		case <-ctx.Done():
			slog.Info("cores: buildbot poller shutting down")
			return
		case <-time.After(wait):
		}
	}
}

// RunOnce executes a single poll pass: for every buildbot-default Core
// row, for every (platform, arch) in PollMatrix that the core declares
// it supports, fetch the buildbot URL, compare sha256, write through on
// change. Exported so admin tooling can trigger a poll without waiting
// for the next scheduled cycle.
func (p *Poller) RunOnce(ctx context.Context) error {
	if err := os.MkdirAll(p.opts.CoreDir, 0o755); err != nil {
		return fmt.Errorf("cores: ensuring CoreDir: %w", err)
	}

	var cores []db.Core
	// Only poll cores that don't have a CustomDownloadURL override —
	// pinned cores have their own update flow (admin upload + manual
	// refresh) and the poller would race against it.
	err := p.db.
		Where("download_url = '' OR download_url IS NULL").
		Where("platforms <> ''").
		Find(&cores).Error
	if err != nil {
		return fmt.Errorf("cores: listing buildbot-default cores: %w", err)
	}

	for _, core := range cores {
		if ctx.Err() != nil {
			return ctx.Err()
		}
		for _, pa := range MatrixForPlatforms(core.Platforms) {
			if !inMatrix(pa, p.opts.PollMatrix) {
				continue
			}
			if ctx.Err() != nil {
				return ctx.Err()
			}
			p.pollOne(ctx, core, pa)
		}
	}
	return nil
}

func (p *Poller) pollOne(ctx context.Context, core db.Core, pa PlatformArch) {
	url := p.urlFor(core.Name, pa)
	log := slog.With("core", core.Name, "platform", pa.String(), "url", url)

	body, err := p.fetchZipBody(ctx, url)
	if err != nil {
		p.emitFailure(core, pa, url, err)
		log.Warn("cores: buildbot fetch failed", "error", err)
		return
	}
	defer body.cleanup()

	binary, err := extractCoreFromZip(body.path, core.Name, pa.Platform)
	if err != nil {
		p.emitFailure(core, pa, url, err)
		log.Warn("cores: zip extract failed", "error", err)
		return
	}
	defer os.Remove(binary.path)

	sum, size, err := hashAndSize(binary.path)
	if err != nil {
		p.emitFailure(core, pa, url, err)
		log.Warn("cores: hashing extracted binary failed", "error", err)
		return
	}

	var existing db.CorePlatformBinary
	err = p.db.
		Where("core_id = ? AND platform_arch = ?", core.ID, pa.String()).
		First(&existing).Error
	rowExists := err == nil
	if err != nil && !errors.Is(err, gorm.ErrRecordNotFound) {
		log.Warn("cores: loading per-platform binary row failed", "error", err)
		return
	}

	if rowExists && existing.Sha256 == sum {
		log.Debug("cores: buildbot binary unchanged", "sha256", shortSha(sum))
		return
	}

	dstDir := filepath.Join(p.opts.CoreDir, pa.String())
	if err := os.MkdirAll(dstDir, 0o755); err != nil {
		log.Warn("cores: creating per-platform core dir failed", "dir", dstDir, "error", err)
		return
	}
	dst := filepath.Join(dstDir, CoreBinaryFilename(core.Name, pa.Platform))
	if err := os.Rename(binary.path, dst); err != nil {
		// Cross-device move (temp dir on a different fs from CoreDir):
		// fall back to copy + remove. The defer will clean up the original.
		if err := copyFile(binary.path, dst); err != nil {
			log.Warn("cores: writing core binary to disk failed", "dst", dst, "error", err)
			return
		}
	} else {
		// Rename succeeded — defer's Remove will silently no-op on a
		// missing path, so we don't need to nil out binary.path.
	}

	now := p.opts.Clock()
	old := existing.Sha256
	row := db.CorePlatformBinary{
		CoreID:       core.ID,
		PlatformArch: pa.String(),
		FilePath:     dst,
		Sha256:       sum,
		SizeBytes:    size,
		FetchedAt:    &now,
		SourceURL:    url,
	}
	if rowExists {
		row.ID = existing.ID
		row.CreatedAt = existing.CreatedAt
		if err := p.db.Save(&row).Error; err != nil {
			log.Warn("cores: persisting per-platform binary row failed", "error", err)
			return
		}
	} else {
		if err := p.db.Create(&row).Error; err != nil {
			log.Warn("cores: creating per-platform binary row failed", "error", err)
			return
		}
	}

	db.RecordOperationalEvent(p.db, db.SystemEventInput{
		EventType: db.SystemEventCoreUpdated,
		Reason: fmt.Sprintf("core %q (%s) replaced: %s → %s",
			core.Name, pa.String(), shortSha(old), shortSha(sum)),
		Metadata: db.CoreUpdatedMetadata{
			Core:         core.Name,
			PlatformArch: pa.String(),
			OldSha256:    old,
			NewSha256:    sum,
			SizeBytes:    size,
			SourceURL:    url,
			Trigger:      "buildbot_poll",
		},
	})
	log.Info("cores: buildbot binary updated",
		"old_sha256", shortSha(old),
		"new_sha256", shortSha(sum),
		"size_bytes", size,
	)
}

// urlFor composes the buildbot URL for (coreName, pa). Honors the
// BaseURLOverride hook used by tests.
func (p *Poller) urlFor(coreName string, pa PlatformArch) string {
	u := BuildbotURL(coreName, pa.Platform, pa.Arch)
	if p.opts.BaseURLOverride != "" {
		// BuildbotURL always starts with `https://buildbot.libretro.com/nightly`
		// (constant in this package), so a fixed-prefix swap is safe.
		return p.opts.BaseURLOverride + u[len(buildbotBase):]
	}
	return u
}

func (p *Poller) emitFailure(core db.Core, pa PlatformArch, url string, err error) {
	db.RecordOperationalEvent(p.db, db.SystemEventInput{
		EventType: db.SystemEventCoreUpdateFailed,
		Reason:    fmt.Sprintf("buildbot poll failed for %q (%s): %s", core.Name, pa.String(), err.Error()),
		Metadata: db.CoreUpdateFailedMetadata{
			Core:         core.Name,
			PlatformArch: pa.String(),
			URL:          url,
			Error:        err.Error(),
		},
	})
}

// fetchedBody owns the temp file holding a downloaded zip plus a cleanup
// that removes it. Callers must defer cleanup() after a successful fetch.
type fetchedBody struct {
	path    string
	cleanup func()
}

func (p *Poller) fetchZipBody(ctx context.Context, url string) (fetchedBody, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return fetchedBody{}, fmt.Errorf("building request: %w", err)
	}
	resp, err := p.opts.HTTPClient.Do(req)
	if err != nil {
		return fetchedBody{}, fmt.Errorf("buildbot GET: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return fetchedBody{}, fmt.Errorf("buildbot GET: HTTP %d", resp.StatusCode)
	}

	tmp, err := os.CreateTemp("", "buildbot-zip-*.zip")
	if err != nil {
		return fetchedBody{}, fmt.Errorf("temp file: %w", err)
	}
	path := tmp.Name()
	cleanup := func() { _ = os.Remove(path) }

	written, err := io.Copy(tmp, io.LimitReader(resp.Body, downloadMaxBytes+1))
	if cerr := tmp.Close(); err == nil {
		err = cerr
	}
	if err != nil {
		cleanup()
		return fetchedBody{}, fmt.Errorf("writing zip body: %w", err)
	}
	if written > downloadMaxBytes {
		cleanup()
		return fetchedBody{}, fmt.Errorf("zip body exceeded %d-byte cap (got %d)", downloadMaxBytes, written)
	}
	return fetchedBody{path: path, cleanup: cleanup}, nil
}

type extractedBinary struct {
	path string
}

// extractCoreFromZip pulls the core's .so/.dylib/.dll out of the buildbot
// zip and writes it to a fresh temp file. Returns an error when the zip
// doesn't carry exactly the expected filename — defensive against an
// upstream re-layout that would otherwise install the wrong file.
func extractCoreFromZip(zipPath, coreName, platform string) (extractedBinary, error) {
	z, err := zip.OpenReader(zipPath)
	if err != nil {
		return extractedBinary{}, fmt.Errorf("open zip: %w", err)
	}
	defer z.Close()

	wantName := CoreBinaryFilename(coreName, platform)
	for _, f := range z.File {
		if filepath.Base(f.Name) != wantName {
			continue
		}
		rc, err := f.Open()
		if err != nil {
			return extractedBinary{}, fmt.Errorf("open zip entry %q: %w", f.Name, err)
		}
		defer rc.Close()

		tmp, err := os.CreateTemp("", "buildbot-bin-*")
		if err != nil {
			return extractedBinary{}, fmt.Errorf("temp file: %w", err)
		}
		written, err := io.Copy(tmp, io.LimitReader(rc, downloadMaxBytes+1))
		if cerr := tmp.Close(); err == nil {
			err = cerr
		}
		if err != nil {
			_ = os.Remove(tmp.Name())
			return extractedBinary{}, fmt.Errorf("extracting %q: %w", f.Name, err)
		}
		if written > downloadMaxBytes {
			_ = os.Remove(tmp.Name())
			return extractedBinary{}, fmt.Errorf("extracted entry %q exceeded %d-byte cap", f.Name, downloadMaxBytes)
		}
		return extractedBinary{path: tmp.Name()}, nil
	}
	return extractedBinary{}, fmt.Errorf("zip missing %q (got %d entries)", wantName, len(z.File))
}

func hashAndSize(path string) (string, int64, error) {
	f, err := os.Open(path)
	if err != nil {
		return "", 0, err
	}
	defer f.Close()
	h := sha256.New()
	n, err := io.Copy(h, f)
	if err != nil {
		return "", 0, err
	}
	return hex.EncodeToString(h.Sum(nil)), n, nil
}

func copyFile(src, dst string) error {
	in, err := os.Open(src)
	if err != nil {
		return err
	}
	defer in.Close()
	out, err := os.OpenFile(dst, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, 0o644)
	if err != nil {
		return err
	}
	if _, err := io.Copy(out, in); err != nil {
		_ = out.Close()
		return err
	}
	return out.Close()
}

// jitter returns a random duration in [0, max]. Used to spread poll
// cadence across server fleets and to mask request bursts to buildbot.
//
// max may be zero (the early-startup path passes opts.Interval/10 which
// becomes 0 for tiny intervals in tests); in that case we return 0
// rather than panicking on rand.Int63n(0).
func jitter(max time.Duration) time.Duration {
	if max <= 0 {
		return 0
	}
	return time.Duration(rand.Int63n(int64(max)))
}

func inMatrix(pa PlatformArch, matrix []PlatformArch) bool {
	for _, m := range matrix {
		if m == pa {
			return true
		}
	}
	return false
}

// shortSha mirrors api.shortSha (kept private to that package) so the
// poller's audit-event Reason lines render identically.
func shortSha(s string) string {
	if s == "" {
		return "(none)"
	}
	if len(s) < 12 {
		return s
	}
	return s[:12]
}
