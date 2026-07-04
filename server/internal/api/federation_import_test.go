package api

import (
	"context"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/federation"
	"github.com/spela/server/internal/scraper"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

// importTestRig wires an ImportService against a connected server "B" that
// offers a game via a catalog snapshot and consents to downloads.
func importTestRig(t *testing.T, romBody string) (*ImportService, *gorm.DB, db.Console, string) {
	t.Helper()
	database := openAPIFedTestDB(t)
	selfID, _ := federation.GenerateIdentity()

	console := db.Console{Name: "Super Nintendo", Abbreviation: "SNES", Extensions: ".sfc,.smc"}
	require.NoError(t, database.Create(&console).Error)

	// Connected server B: active, consents to download, and its catalog offers the key.
	idB, _ := federation.GenerateIdentity()
	cp, _ := federation.MarshalPolicy(map[federation.DataClass]bool{federation.DataClassDownload: true})
	require.NoError(t, federation.PeerStore{DB: database}.Upsert(&db.FederationPeer{
		Fingerprint: idB.Fingerprint(), PublicKey: b64(idB.PublicKey), Name: "B",
		BaseURL: "https://b", Status: db.PeerStatusActive, ConsumePolicy: cp,
	}))
	require.NoError(t, federation.CatalogSnapshotStore{DB: database}.ReplacePeerSnapshot(
		idB.Fingerprint(),
		[]federation.CatalogEntry{{OriginFingerprint: idB.Fingerprint(), Hops: 1, Key: "igdb:42", Title: "Chrono Trigger", Console: "SNES"}},
		time.Unix(1, 0),
	))

	svc := &ImportService{
		DB: database, Peers: federation.PeerStore{DB: database},
		Catalog: federation.CatalogSnapshotStore{DB: database}, Identity: selfID,
		GameDirs: []string{t.TempDir()}, Queue: scraper.NewScrapeQueue(database),
		Client: &fakeDownloadClient{body: romBody},
	}
	return svc, database, console, svc.GameDirs[0]
}

func TestImportService_DownloadsAndIngests(t *testing.T) {
	const rom = "ROM-BYTES-1234"
	svc, database, console, gameDir := importTestRig(t, rom)

	job, err := svc.Enqueue("igdb:42", "Chrono Trigger", "SNES", 7)
	require.NoError(t, err)

	svc.ProcessNext()

	require.NoError(t, database.First(job, job.ID).Error)
	assert.Equal(t, "completed", job.Status)
	require.NotNil(t, job.GameID)
	assert.Equal(t, int64(len(rom)), job.BytesDownloaded)
	assert.Empty(t, job.ErrorMessage)

	// A local Game was created on the right console, with the ROM on disk.
	var game db.Game
	require.NoError(t, database.First(&game, *job.GameID).Error)
	assert.Equal(t, "Chrono Trigger", game.Title)
	assert.Equal(t, console.ID, game.ConsoleID)
	assert.Equal(t, "Chrono Trigger.sfc", game.FileName)
	romPath := filepath.Join(gameDir, "SNES", "Chrono Trigger.sfc")
	contents, rerr := os.ReadFile(romPath)
	require.NoError(t, rerr)
	assert.Equal(t, rom, string(contents))

	// The imported game was queued for metadata scraping.
	var queued int64
	database.Model(&db.ScrapeQueueItem{}).Where("game_id = ?", *job.GameID).Count(&queued)
	assert.Equal(t, int64(1), queued)
}

func TestImportService_ClaimsJobOnce(t *testing.T) {
	const rom = "ROM-BYTES-1234"
	svc, database, _, _ := importTestRig(t, rom)

	job, err := svc.Enqueue("igdb:42", "Chrono Trigger", "SNES", 7)
	require.NoError(t, err)

	// Simulate two callers that both SELECTed the same pending job before
	// either claimed it (the SELECT-then-update race). The atomic claim must
	// let exactly one run; the second bails without re-importing.
	first := *job
	second := *job
	svc.run(&first)
	svc.run(&second)

	// Exactly one Game was created, and the loser didn't clobber the winner's
	// completed status with a "file already exists" failure.
	var games int64
	database.Model(&db.Game{}).Count(&games)
	assert.Equal(t, int64(1), games)

	require.NoError(t, database.First(job, job.ID).Error)
	assert.Equal(t, "completed", job.Status)
}

func TestImportService_FailsWhenNoConnectedServerOffersIt(t *testing.T) {
	svc, database, _, _ := importTestRig(t, "x")
	job, err := svc.Enqueue("igdb:does-not-exist", "Ghost", "SNES", 7)
	require.NoError(t, err)

	svc.ProcessNext()

	require.NoError(t, database.First(job, job.ID).Error)
	assert.Equal(t, "failed", job.Status)
	assert.Contains(t, job.ErrorMessage, "no connected server")
	assert.Nil(t, job.GameID)
}

func TestStartImport_PermissionGate(t *testing.T) {
	svc, database, _, _ := importTestRig(t, "rom")
	h := &FederationHandler{DB: database, Imports: svc}

	user := db.User{Username: "u", PasswordHash: "x", Role: db.RoleUser}
	require.NoError(t, database.Create(&user).Error)

	in := &StartImportInput{}
	in.Body.Key = "igdb:42"
	in.Body.Title = "Chrono Trigger"
	in.Body.Console = "SNES"

	// A plain user without the capability is refused.
	ctx := context.WithValue(context.Background(), ctxKeyUserRole, db.RoleUser)
	ctx = context.WithValue(ctx, ctxKeyUserID, user.ID)
	_, err := h.HumaStartImport(ctx, in)
	assert.Error(t, err, "ungranted user must be refused")

	// Granting the capability lets the same user import.
	require.NoError(t, database.Model(&user).Update("can_import_games", true).Error)
	out, err := h.HumaStartImport(ctx, in)
	require.NoError(t, err)
	assert.Equal(t, "pending", out.Body.Job.Status)

	// An admin can import regardless of the flag.
	admin := db.User{Username: "a", PasswordHash: "x", Role: db.RoleAdmin}
	require.NoError(t, database.Create(&admin).Error)
	actx := context.WithValue(context.Background(), ctxKeyUserRole, db.RoleAdmin)
	actx = context.WithValue(actx, ctxKeyUserID, admin.ID)
	_, err = h.HumaStartImport(actx, in)
	require.NoError(t, err)
}

func TestListImports_PermissionGate(t *testing.T) {
	svc, database, _, _ := importTestRig(t, "rom")
	h := &FederationHandler{DB: database, Imports: svc}

	user := db.User{Username: "u", PasswordHash: "x", Role: db.RoleUser}
	require.NoError(t, database.Create(&user).Error)
	ctx := context.WithValue(context.Background(), ctxKeyUserRole, db.RoleUser)
	ctx = context.WithValue(ctx, ctxKeyUserID, user.ID)

	// A plain user without the capability cannot view import history.
	_, err := h.HumaListImports(ctx, &ListImportsInput{})
	assert.Error(t, err, "ungranted user must not see import history")

	// Granting the capability opens the list.
	require.NoError(t, database.Model(&user).Update("can_import_games", true).Error)
	out, err := h.HumaListImports(ctx, &ListImportsInput{})
	require.NoError(t, err)
	assert.NotNil(t, out)
}

func TestImportService_SweepsOrphanedJobsOnStart(t *testing.T) {
	svc, database, _, _ := importTestRig(t, "rom")

	// Jobs left mid-flight by a previous process, plus terminal ones that must
	// be left untouched.
	orphans := []db.ImportJob{
		{Status: "downloading", Key: "igdb:1", Console: "SNES"},
		{Status: "ingesting", Key: "igdb:2", Console: "SNES"},
		{Status: "scraping", Key: "igdb:3", Console: "SNES"},
	}
	for i := range orphans {
		require.NoError(t, database.Create(&orphans[i]).Error)
	}
	pending := db.ImportJob{Status: "pending", Key: "igdb:4", Console: "SNES"}
	completed := db.ImportJob{Status: "completed", Key: "igdb:5", Console: "SNES"}
	require.NoError(t, database.Create(&pending).Error)
	require.NoError(t, database.Create(&completed).Error)

	svc.sweepOrphans()

	for _, o := range orphans {
		var got db.ImportJob
		require.NoError(t, database.First(&got, o.ID).Error)
		assert.Equal(t, "failed", got.Status, "orphan %q should be failed", o.Key)
		assert.Contains(t, got.ErrorMessage, "restart")
	}
	// Pending and terminal jobs are untouched.
	var p, c db.ImportJob
	require.NoError(t, database.First(&p, pending.ID).Error)
	require.NoError(t, database.First(&c, completed.ID).Error)
	assert.Equal(t, "pending", p.Status)
	assert.Equal(t, "completed", c.Status)
}
