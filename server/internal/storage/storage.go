package storage

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
)

// Storage manages file operations for ROMs, saves, cores, and images.
type Storage struct {
	SaveDir  string
	CoreDir  string
	ImageDir string
	BiosDir  string
}

// NewStorage creates a new storage instance, creating directories as needed.
func NewStorage(saveDir, coreDir, imageDir, biosDir string) (*Storage, error) {
	for _, dir := range []string{saveDir, coreDir, imageDir, biosDir} {
		if err := os.MkdirAll(dir, 0755); err != nil {
			return nil, fmt.Errorf("creating directory %s: %w", dir, err)
		}
	}
	return &Storage{
		SaveDir:  saveDir,
		CoreDir:  coreDir,
		ImageDir: imageDir,
		BiosDir:  biosDir,
	}, nil
}

// WriteImage writes image data to {ImageDir}/{subpath}, creating subdirectories as needed.
// Returns the relative subpath for storage in the database.
func (s *Storage) WriteImage(subpath string, data io.Reader) (string, error) {
	fullPath := filepath.Join(s.ImageDir, subpath)

	// Verify the resolved path is inside the image directory
	absPath, err := filepath.Abs(fullPath)
	if err != nil {
		return "", fmt.Errorf("resolving image path: %w", err)
	}
	absImageDir, err := filepath.Abs(s.ImageDir)
	if err != nil {
		return "", fmt.Errorf("resolving image dir: %w", err)
	}
	if !strings.HasPrefix(absPath, absImageDir+string(filepath.Separator)) {
		return "", fmt.Errorf("invalid image path: outside image directory")
	}

	if err := os.MkdirAll(filepath.Dir(fullPath), 0755); err != nil {
		return "", fmt.Errorf("creating image directory: %w", err)
	}

	f, err := os.Create(fullPath)
	if err != nil {
		return "", fmt.Errorf("creating image file: %w", err)
	}
	defer f.Close()

	if _, err := io.Copy(f, data); err != nil {
		return "", fmt.Errorf("writing image file: %w", err)
	}

	return subpath, nil
}

// ImagePath returns the full filesystem path for a stored image subpath.
func (s *Storage) ImagePath(subpath string) string {
	return filepath.Join(s.ImageDir, subpath)
}

// BiosFilePath returns the filesystem path for a BIOS file, using sanitizeFilename
// to prevent path traversal attacks.
func (s *Storage) BiosFilePath(filename string) string {
	safe := sanitizeFilename(filename)
	return filepath.Join(s.BiosDir, safe)
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

// SharedSavePath returns the filesystem path for a shared save state.
func (s *Storage) SharedSavePath(gameID, saveID uint, filename string) string {
	safe := sanitizeFilename(filename)
	return filepath.Join(s.SaveDir, "shared-saves", fmt.Sprintf("game_%d", gameID), fmt.Sprintf("%d_%s", saveID, safe))
}

// WriteSharedSave stores a shared save state file.
func (s *Storage) WriteSharedSave(gameID, saveID uint, filename string, data io.Reader) (string, int64, error) {
	path := s.SharedSavePath(gameID, saveID, filename)

	absPath, err := filepath.Abs(path)
	if err != nil {
		return "", 0, fmt.Errorf("resolving shared save path: %w", err)
	}
	absSaveDir, err := filepath.Abs(s.SaveDir)
	if err != nil {
		return "", 0, fmt.Errorf("resolving save dir: %w", err)
	}
	if !strings.HasPrefix(absPath, absSaveDir+string(filepath.Separator)) {
		return "", 0, fmt.Errorf("invalid shared save path: outside save directory")
	}

	if err := os.MkdirAll(filepath.Dir(path), 0755); err != nil {
		return "", 0, fmt.Errorf("creating shared save directory: %w", err)
	}

	f, err := os.Create(path)
	if err != nil {
		return "", 0, fmt.Errorf("creating shared save file: %w", err)
	}
	defer f.Close()

	n, err := io.Copy(f, data)
	if err != nil {
		return "", 0, fmt.Errorf("writing shared save file: %w", err)
	}

	return path, n, nil
}

// RelaySavePath returns the filesystem path for a relay save state.
func (s *Storage) RelaySavePath(relayID uint, filename string) string {
	safe := sanitizeFilename(filename)
	return filepath.Join(s.SaveDir, "relays", fmt.Sprintf("relay_%d", relayID), safe)
}

// WriteRelaySave stores a relay save state file.
func (s *Storage) WriteRelaySave(relayID uint, filename string, data io.Reader) (string, int64, error) {
	path := s.RelaySavePath(relayID, filename)

	absPath, err := filepath.Abs(path)
	if err != nil {
		return "", 0, fmt.Errorf("resolving relay save path: %w", err)
	}
	absSaveDir, err := filepath.Abs(s.SaveDir)
	if err != nil {
		return "", 0, fmt.Errorf("resolving save dir: %w", err)
	}
	if !strings.HasPrefix(absPath, absSaveDir+string(filepath.Separator)) {
		return "", 0, fmt.Errorf("invalid relay save path: outside save directory")
	}

	if err := os.MkdirAll(filepath.Dir(path), 0755); err != nil {
		return "", 0, fmt.Errorf("creating relay save directory: %w", err)
	}

	f, err := os.Create(path)
	if err != nil {
		return "", 0, fmt.Errorf("creating relay save file: %w", err)
	}
	defer f.Close()

	n, err := io.Copy(f, data)
	if err != nil {
		return "", 0, fmt.Errorf("writing relay save file: %w", err)
	}

	return path, n, nil
}

// DeleteRelaySave removes a relay save state file.
func (s *Storage) DeleteRelaySave(filePath string) error {
	if err := os.Remove(filePath); err != nil && !os.IsNotExist(err) {
		return fmt.Errorf("deleting relay save file: %w", err)
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
