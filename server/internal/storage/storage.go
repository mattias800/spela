package storage

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
)

// Storage manages file operations for ROMs, saves, and cores.
type Storage struct {
	SaveDir string
	CoreDir string
}

// NewStorage creates a new storage instance, creating directories as needed.
func NewStorage(saveDir, coreDir string) (*Storage, error) {
	for _, dir := range []string{saveDir, coreDir} {
		if err := os.MkdirAll(dir, 0755); err != nil {
			return nil, fmt.Errorf("creating directory %s: %w", dir, err)
		}
	}
	return &Storage{
		SaveDir: saveDir,
		CoreDir: coreDir,
	}, nil
}

// sanitizeFilename strips path separators and traversal sequences from a filename,
// returning only the base name to prevent path traversal attacks.
func sanitizeFilename(filename string) string {
	// Use only the base name, stripping any directory components
	clean := filepath.Base(filename)
	// filepath.Base returns "." for empty input
	if clean == "." || clean == ".." {
		return "unnamed"
	}
	return clean
}

// SaveStatePath returns the filesystem path for a user's save state.
func (s *Storage) SaveStatePath(userID, gameID uint, filename string) string {
	safe := sanitizeFilename(filename)
	return filepath.Join(s.SaveDir, fmt.Sprintf("user_%d", userID), fmt.Sprintf("game_%d", gameID), safe)
}

// WriteSave stores a save state file.
func (s *Storage) WriteSave(userID, gameID uint, filename string, data io.Reader) (int64, error) {
	path := s.SaveStatePath(userID, gameID, filename)

	// Verify the resolved path is inside the save directory
	absPath, err := filepath.Abs(path)
	if err != nil {
		return 0, fmt.Errorf("resolving save path: %w", err)
	}
	absSaveDir, err := filepath.Abs(s.SaveDir)
	if err != nil {
		return 0, fmt.Errorf("resolving save dir: %w", err)
	}
	if !strings.HasPrefix(absPath, absSaveDir+string(filepath.Separator)) {
		return 0, fmt.Errorf("invalid save path: outside save directory")
	}

	if err := os.MkdirAll(filepath.Dir(path), 0755); err != nil {
		return 0, fmt.Errorf("creating save directory: %w", err)
	}

	f, err := os.Create(path)
	if err != nil {
		return 0, fmt.Errorf("creating save file: %w", err)
	}
	defer f.Close()

	n, err := io.Copy(f, data)
	if err != nil {
		return 0, fmt.Errorf("writing save file: %w", err)
	}

	return n, nil
}

// ReadSave opens a save state file for reading.
func (s *Storage) ReadSave(path string) (*os.File, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, fmt.Errorf("opening save file: %w", err)
	}
	return f, nil
}

// DeleteSave removes a save state file.
func (s *Storage) DeleteSave(path string) error {
	if err := os.Remove(path); err != nil && !os.IsNotExist(err) {
		return fmt.Errorf("deleting save file: %w", err)
	}
	return nil
}

// ValidateROMPath checks that a ROM path is within allowed game directories.
func ValidateROMPath(filePath string, allowedDirs []string) bool {
	absPath, err := filepath.Abs(filePath)
	if err != nil {
		return false
	}
	// Resolve symlinks for security
	realPath, err := filepath.EvalSymlinks(absPath)
	if err != nil {
		// File may not exist yet; just check the directory
		realPath = absPath
	}

	for _, dir := range allowedDirs {
		absDir, err := filepath.Abs(dir)
		if err != nil {
			continue
		}
		realDir, err := filepath.EvalSymlinks(absDir)
		if err != nil {
			realDir = absDir
		}
		if strings.HasPrefix(realPath, realDir+string(filepath.Separator)) || realPath == realDir {
			return true
		}
	}
	return false
}
