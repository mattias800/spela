// Command dump-openapi writes the generated OpenAPI 3.1 spec to stdout (or
// to the file passed as the first argument). Used by the client codegen
// pipeline to feed the TypeScript and Kotlin generators a fresh spec
// without needing to boot the full server.
package main

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/api"
	"github.com/spela/server/internal/scanner"
	"github.com/spela/server/internal/scraper"
	"github.com/spela/server/internal/storage"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

func main() {
	gin.SetMode(gin.ReleaseMode)

	// In-memory SQLite for schema-only router setup.
	db, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	if err != nil {
		fmt.Fprintln(os.Stderr, "failed to open db:", err)
		os.Exit(1)
	}

	tmp, err := os.MkdirTemp("", "openapi-dump-*")
	if err != nil {
		fmt.Fprintln(os.Stderr, "failed to create tmp dir:", err)
		os.Exit(1)
	}
	defer os.RemoveAll(tmp)

	store, err := storage.NewStorage(
		filepath.Join(tmp, "saves"),
		filepath.Join(tmp, "cores"),
		filepath.Join(tmp, "images"),
		filepath.Join(tmp, "bios"),
	)
	if err != nil {
		fmt.Fprintln(os.Stderr, "failed to init storage:", err)
		os.Exit(1)
	}

	hub := ws.NewHub(nil)
	go hub.Run()
	defer hub.Close()

	cfg := api.Config{
		DB:        db,
		JWTSecret: "dump-openapi-not-a-real-secret",
		GameDirs:  []string{tmp},
		Storage:   store,
		Scanner:   scanner.NewScanner(db, []string{tmp}),
		Scraper:   scraper.NewScraper(db, store, tmp, []string{tmp}),
		Hub:       hub,
		CoreDir:   filepath.Join(tmp, "cores"),
		Version:   "dump-openapi",
	}

	router, cleanup := api.NewRouter(cfg)
	defer cleanup()

	// Fire an internal request against /api/openapi.json via the router to
	// materialise the spec through the same code path huma serves at runtime.
	specBytes, err := fetchSpec(router)
	if err != nil {
		fmt.Fprintln(os.Stderr, "failed to fetch openapi spec:", err)
		os.Exit(1)
	}

	// Pretty-print so the committed artifact diffs sensibly.
	var parsed any
	if err := json.Unmarshal(specBytes, &parsed); err != nil {
		fmt.Fprintln(os.Stderr, "failed to parse spec:", err)
		os.Exit(1)
	}
	pretty, err := json.MarshalIndent(parsed, "", "  ")
	if err != nil {
		fmt.Fprintln(os.Stderr, "failed to re-encode spec:", err)
		os.Exit(1)
	}
	pretty = append(pretty, '\n')

	if len(os.Args) > 1 {
		if err := os.WriteFile(os.Args[1], pretty, 0o644); err != nil {
			fmt.Fprintln(os.Stderr, "failed to write spec:", err)
			os.Exit(1)
		}
		fmt.Fprintf(os.Stderr, "wrote %d bytes to %s\n", len(pretty), os.Args[1])
		return
	}
	if _, err := os.Stdout.Write(pretty); err != nil {
		os.Exit(1)
	}
}
