package api

import (
	"archive/tar"
	"archive/zip"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"github.com/spela/server/internal/scanner"
	"github.com/spela/server/internal/scraper"
	"github.com/spela/server/internal/storage"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// GameHandler handles game-related endpoints.
type GameHandler struct {
	DB       *gorm.DB
	Scanner  *scanner.Scanner
	Storage  *storage.Storage
	Hub      *ws.Hub
	GameDirs []string
	Scraper  *scraper.Scraper
}

// serveTar streams files as an uncompressed tar archive.
func serveTar(w io.Writer, filePaths []string) error {
	tw := tar.NewWriter(w)
	defer tw.Close()

	for _, path := range filePaths {
		info, err := os.Stat(path)
		if err != nil {
			return fmt.Errorf("stat file %s: %w", path, err)
		}

		header := &tar.Header{
			Name: filepath.Base(path),
			Size: info.Size(),
			Mode: 0644,
		}
		if err := tw.WriteHeader(header); err != nil {
			return fmt.Errorf("writing tar header for %s: %w", path, err)
		}

		f, err := os.Open(path)
		if err != nil {
			return fmt.Errorf("opening file %s: %w", path, err)
		}
		if _, err := io.Copy(tw, f); err != nil {
			f.Close()
			return fmt.Errorf("writing file %s to tar: %w", path, err)
		}
		f.Close()
	}

	return nil
}

// serveZip streams files as a zip archive. Used by EmulatorJS which supports
// zip extraction but not tar.
func serveZip(w io.Writer, filePaths []string) error {
	zw := zip.NewWriter(w)
	defer zw.Close()

	for _, path := range filePaths {
		info, err := os.Stat(path)
		if err != nil {
			return fmt.Errorf("stat file %s: %w", path, err)
		}

		header, err := zip.FileInfoHeader(info)
		if err != nil {
			return fmt.Errorf("creating zip header for %s: %w", path, err)
		}
		header.Name = filepath.Base(path)
		header.Method = zip.Store // no compression — ROM data doesn't compress well

		writer, err := zw.CreateHeader(header)
		if err != nil {
			return fmt.Errorf("writing zip header for %s: %w", path, err)
		}

		f, err := os.Open(path)
		if err != nil {
			return fmt.Errorf("opening file %s: %w", path, err)
		}
		if _, err := io.Copy(writer, f); err != nil {
			f.Close()
			return fmt.Errorf("writing file %s to zip: %w", path, err)
		}
		f.Close()
	}

	return nil
}

// escapeLikePattern escapes SQL LIKE wildcard characters in user input.
func escapeLikePattern(s string) string {
	s = strings.ReplaceAll(s, "\\", "\\\\")
	s = strings.ReplaceAll(s, "%", "\\%")
	s = strings.ReplaceAll(s, "_", "\\_")
	return s
}
