package api

import (
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/federation"
	"github.com/spela/server/internal/scraper"
	"github.com/spela/server/internal/storage"
	"gorm.io/gorm"
)

// ImportService downloads a game from a connected federation server and ingests
// it into the local library, tracked as a db.ImportJob with byte-level progress.
// A single background worker processes pending jobs oldest-first, so the UI can
// show a real queue. Mirrors the scrape worker's lifecycle; progress is read by
// polling the imports endpoint.
type ImportService struct {
	DB       *gorm.DB
	Peers    federation.PeerStore
	Catalog  federation.CatalogSnapshotStore
	Identity federation.Identity
	GameDirs []string
	Queue    *scraper.ScrapeQueue // to enqueue metadata scrape after ingest
	Client   downloadClient       // defaults to httpDownloadClient when nil (overridden in tests)

	PollInterval time.Duration
	stop         chan struct{}
	once         sync.Once
	stopOnce     sync.Once
}

func (s *ImportService) client() downloadClient {
	if s.Client != nil {
		return s.Client
	}
	return httpDownloadClient{}
}

// Enqueue records a pending import job for the worker to pick up.
func (s *ImportService) Enqueue(key, title, console string, userID uint) (*db.ImportJob, error) {
	job := &db.ImportJob{
		Status: "pending", Key: key, Title: title, Console: console,
		RequestedByUserID: userID,
	}
	if err := s.DB.Create(job).Error; err != nil {
		return nil, err
	}
	return job, nil
}

// Start launches the background worker (idempotent).
func (s *ImportService) Start() {
	s.once.Do(func() {
		if s.PollInterval == 0 {
			s.PollInterval = 2 * time.Second
		}
		// Recover jobs left mid-flight by a restart: any job not in a terminal
		// state can never be resumed (the .part file is gone / progress is
		// lost), so mark it failed rather than leaving it stuck forever.
		s.sweepOrphans()
		s.stop = make(chan struct{})
		go s.loop()
	})
}

// sweepOrphans fails any non-terminal job left over from a previous process.
func (s *ImportService) sweepOrphans() {
	res := s.DB.Model(&db.ImportJob{}).
		Where("status IN ?", []string{"downloading", "ingesting", "scraping"}).
		Updates(map[string]any{
			"status":        "failed",
			"error_message": "interrupted by server restart",
			"completed_at":  time.Now(),
		})
	if res.Error != nil {
		slog.Warn("import: failed to sweep orphaned jobs", "component", "federation", "error", res.Error)
	} else if res.RowsAffected > 0 {
		slog.Info("import: failed orphaned jobs on startup", "component", "federation", "count", res.RowsAffected)
	}
}

// Stop halts the worker (safe to call more than once).
func (s *ImportService) Stop() {
	s.stopOnce.Do(func() {
		if s.stop != nil {
			close(s.stop)
		}
	})
}

func (s *ImportService) loop() {
	t := time.NewTicker(s.PollInterval)
	defer t.Stop()
	for {
		select {
		case <-s.stop:
			return
		case <-t.C:
			s.ProcessNext()
		}
	}
}

// ProcessNext picks up and runs the oldest pending job, if any. Exported so
// tests can drive a single pass deterministically.
func (s *ImportService) ProcessNext() {
	var job db.ImportJob
	if err := s.DB.Where("status = ?", "pending").Order("created_at ASC").First(&job).Error; err != nil {
		return // nothing pending
	}
	s.run(&job)
}

func (s *ImportService) run(job *db.ImportJob) {
	// Resolve the console first — it determines where the download lands, so we
	// can stream onto the same filesystem and rename atomically into place.
	var console db.Console
	if err := s.DB.Where("abbreviation = ?", job.Console).First(&console).Error; err != nil {
		s.fail(job, fmt.Sprintf("unknown console %q", job.Console))
		return
	}
	if len(s.GameDirs) == 0 {
		s.fail(job, "no game directory configured")
		return
	}

	// Atomically claim the job by flipping it off "pending", guarded on that
	// status. If a concurrent caller already claimed it the update affects no
	// rows and we bail, so the same job is never processed (downloaded) twice.
	claim := s.DB.Model(&db.ImportJob{}).
		Where("id = ? AND status = ?", job.ID, "pending").
		Updates(map[string]any{"status": "downloading", "started_at": time.Now()})
	if claim.Error != nil || claim.RowsAffected == 0 {
		return
	}
	job.Status = "downloading"

	part, size, err := s.download(job)
	if err != nil {
		s.fail(job, err.Error())
		return
	}

	s.update(job, map[string]any{"status": "ingesting"})
	gameID, err := s.ingest(job, &console, part, size)
	if err != nil {
		_ = os.Remove(part)
		s.fail(job, err.Error())
		return
	}

	s.update(job, map[string]any{"status": "scraping", "game_id": gameID})
	if err := s.Queue.EnqueueGame(gameID, nil, 100); err != nil {
		// Non-fatal: the game is imported and playable; metadata can be scraped
		// later. Don't fail the import over a scrape-enqueue hiccup.
		slog.Warn("import: failed to enqueue scrape", "component", "federation", "gameID", gameID, "error", err)
	}
	s.update(job, map[string]any{"status": "completed", "completed_at": time.Now()})
	slog.Info("import: completed", "component", "federation", "key", job.Key, "gameID", gameID)
}

// download streams the ROM from the first connected server that offers the key
// and consents to downloads, into a .part file in the game dir. Returns the
// temp path and byte count.
func (s *ImportService) download(job *db.ImportJob) (string, int64, error) {
	sources, err := s.Catalog.SourcePeersForKey(job.Key)
	if err != nil {
		return "", 0, errors.New("failed to look up sources")
	}
	for _, fp := range sources {
		peer, err := s.Peers.GetByFingerprint(fp)
		if err != nil || peer.Status != db.PeerStatusActive || !federation.CanConsume(*peer, federation.DataClassDownload) {
			continue
		}
		reqID := federation.NewRequestID()
		resp, ferr := s.client().FetchDownload(peer.BaseURL, reqID, s.Identity, peer.Fingerprint, job.Key, federation.MaxFederationHops-1)
		if ferr != nil || resp == nil || resp.StatusCode != http.StatusOK {
			if resp != nil {
				resp.Body.Close()
			}
			continue
		}
		if resp.ContentLength > 0 {
			s.DB.Model(job).Update("total_bytes", resp.ContentLength)
		}
		part, n, derr := s.streamToPart(job, resp.Body)
		resp.Body.Close()
		if derr != nil {
			return "", 0, derr
		}
		return part, n, nil
	}
	return "", 0, errors.New("no connected server offers this game for download")
}

func (s *ImportService) streamToPart(job *db.ImportJob, r io.Reader) (string, int64, error) {
	f, err := os.CreateTemp(s.GameDirs[0], "spela-import-*.part")
	if err != nil {
		return "", 0, err
	}
	defer f.Close()
	buf := make([]byte, 1<<20) // 1 MiB
	var total int64
	last := time.Now()
	for {
		nr, er := r.Read(buf)
		if nr > 0 {
			if _, ew := f.Write(buf[:nr]); ew != nil {
				os.Remove(f.Name())
				return "", 0, ew
			}
			total += int64(nr)
			if time.Since(last) >= time.Second {
				s.DB.Model(job).Update("bytes_downloaded", total)
				last = time.Now()
			}
		}
		if er == io.EOF {
			break
		}
		if er != nil {
			os.Remove(f.Name())
			return "", 0, er
		}
	}
	s.DB.Model(job).Update("bytes_downloaded", total)
	return f.Name(), total, nil
}

// ingest renames the downloaded file into the console's game dir under a name
// derived from the title, then creates the local Game row.
func (s *ImportService) ingest(job *db.ImportJob, console *db.Console, part string, size int64) (uint, error) {
	fileName := sanitizeImportFilename(job.Title) + primaryExtension(console.Extensions)
	destDir := filepath.Join(s.GameDirs[0], console.Abbreviation)
	if err := os.MkdirAll(destDir, 0o755); err != nil {
		return 0, err
	}
	destPath := filepath.Join(destDir, fileName)
	if _, err := os.Stat(destPath); err == nil {
		return 0, errors.New("a game file with this name already exists")
	}
	if err := os.Rename(part, destPath); err != nil {
		return 0, err
	}
	relPath := storage.RelativeGamePath(destPath, s.GameDirs)
	game := db.Game{
		ConsoleID: console.ID, Title: job.Title, FileName: fileName,
		FilePath: relPath, FileSize: size,
	}
	if err := s.DB.Create(&game).Error; err != nil {
		os.Remove(destPath) // roll back the file if the row can't be created (e.g. dup path)
		return 0, fmt.Errorf("create game: %w", err)
	}
	return game.ID, nil
}

func (s *ImportService) update(job *db.ImportJob, fields map[string]any) {
	if err := s.DB.Model(job).Updates(fields).Error; err != nil {
		slog.Warn("import: failed to update job", "component", "federation", "id", job.ID, "error", err)
	}
}

func (s *ImportService) fail(job *db.ImportJob, msg string) {
	if len(msg) > 512 {
		msg = msg[:512]
	}
	s.update(job, map[string]any{"status": "failed", "error_message": msg, "completed_at": time.Now()})
	slog.Warn("import: failed", "component", "federation", "key", job.Key, "error", msg)
}

// primaryExtension returns the first extension from a console's comma-separated
// list, with a leading dot. Falls back to ".rom".
func primaryExtension(extensions string) string {
	for _, e := range strings.Split(extensions, ",") {
		e = strings.TrimSpace(e)
		if e == "" {
			continue
		}
		if !strings.HasPrefix(e, ".") {
			e = "." + e
		}
		return e
	}
	return ".rom"
}

// sanitizeImportFilename makes a title safe to use as a filename: strips path
// separators and control characters, collapses whitespace, and caps length.
func sanitizeImportFilename(title string) string {
	cleaned := strings.Map(func(r rune) rune {
		switch r {
		case '/', '\\', ':', '*', '?', '"', '<', '>', '|', 0:
			return '_'
		}
		if r < 0x20 {
			return '_'
		}
		return r
	}, title)
	cleaned = strings.TrimSpace(cleaned)
	// "." and ".." are not usable as filenames; treat them as empty.
	if cleaned == "" || cleaned == "." || cleaned == ".." {
		cleaned = "imported-game"
	}
	if len(cleaned) > 200 {
		cleaned = cleaned[:200]
	}
	return cleaned
}
