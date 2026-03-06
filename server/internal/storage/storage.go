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
// Save and BIOS directories use 0700 (owner-only) for security; image and core
// directories use 0755 since they may need web server read access.
func NewStorage(saveDir, coreDir, imageDir, biosDir string) (*Storage, error) {
	for _, dir := range []string{saveDir, biosDir} {
		if err := os.MkdirAll(dir, 0700); err != nil {
			return nil, fmt.Errorf("creating directory %s: %w", dir, err)
		}
	}
	for _, dir := range []string{coreDir, imageDir} {
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

	if err := os.MkdirAll(filepath.Dir(path), 0700); err != nil {
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
	if err := s.validatePathInSaveDir(path); err != nil {
		return nil, err
	}
	f, err := os.Open(path)
	if err != nil {
		return nil, fmt.Errorf("opening save file: %w", err)
	}
	return f, nil
}

// DeleteSave removes a save state file.
func (s *Storage) DeleteSave(path string) error {
	if err := s.validatePathInSaveDir(path); err != nil {
		return err
	}
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

	if err := os.MkdirAll(filepath.Dir(path), 0700); err != nil {
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

// SharedSessionSavePath returns the filesystem path for a shared session save state.
// Note: filesystem paths still use "relays" directory to avoid data loss on upgrade.
func (s *Storage) SharedSessionSavePath(sharedSessionID uint, filename string) string {
	safe := sanitizeFilename(filename)
	return filepath.Join(s.SaveDir, "relays", fmt.Sprintf("relay_%d", sharedSessionID), safe)
}

// WriteSharedSessionSave stores a shared session save state file.
func (s *Storage) WriteSharedSessionSave(sharedSessionID uint, filename string, data io.Reader) (string, int64, error) {
	path := s.SharedSessionSavePath(sharedSessionID, filename)

	absPath, err := filepath.Abs(path)
	if err != nil {
		return "", 0, fmt.Errorf("resolving shared session save path: %w", err)
	}
	absSaveDir, err := filepath.Abs(s.SaveDir)
	if err != nil {
		return "", 0, fmt.Errorf("resolving save dir: %w", err)
	}
	if !strings.HasPrefix(absPath, absSaveDir+string(filepath.Separator)) {
		return "", 0, fmt.Errorf("invalid shared session save path: outside save directory")
	}

	if err := os.MkdirAll(filepath.Dir(path), 0700); err != nil {
		return "", 0, fmt.Errorf("creating shared session save directory: %w", err)
	}

	f, err := os.Create(path)
	if err != nil {
		return "", 0, fmt.Errorf("creating shared session save file: %w", err)
	}
	defer f.Close()

	n, err := io.Copy(f, data)
	if err != nil {
		return "", 0, fmt.Errorf("writing shared session save file: %w", err)
	}

	return path, n, nil
}

// DeleteSharedSessionSave removes a shared session save state file.
func (s *Storage) DeleteSharedSessionSave(filePath string) error {
	if err := s.validatePathInSaveDir(filePath); err != nil {
		return err
	}
	if err := os.Remove(filePath); err != nil && !os.IsNotExist(err) {
		return fmt.Errorf("deleting shared session save file: %w", err)
	}
	return nil
}

// ChallengeSavePath returns the filesystem path for a challenge's starting save state.
func (s *Storage) ChallengeSavePath(challengeID uint) string {
	return filepath.Join(s.SaveDir, "challenges", fmt.Sprintf("challenge_%d", challengeID), "save")
}

// ChallengeScreenshotPath returns the filesystem path for a challenge's screenshot.
func (s *Storage) ChallengeScreenshotPath(challengeID uint) string {
	return filepath.Join(s.SaveDir, "challenges", fmt.Sprintf("challenge_%d", challengeID), "screenshot.png")
}

// WriteChallengeSave stores a challenge's starting save state file.
func (s *Storage) WriteChallengeSave(challengeID uint, data io.Reader) (string, int64, error) {
	path := s.ChallengeSavePath(challengeID)

	absPath, err := filepath.Abs(path)
	if err != nil {
		return "", 0, fmt.Errorf("resolving challenge save path: %w", err)
	}
	absSaveDir, err := filepath.Abs(s.SaveDir)
	if err != nil {
		return "", 0, fmt.Errorf("resolving save dir: %w", err)
	}
	if !strings.HasPrefix(absPath, absSaveDir+string(filepath.Separator)) {
		return "", 0, fmt.Errorf("invalid challenge save path: outside save directory")
	}

	if err := os.MkdirAll(filepath.Dir(path), 0700); err != nil {
		return "", 0, fmt.Errorf("creating challenge save directory: %w", err)
	}

	f, err := os.Create(path)
	if err != nil {
		return "", 0, fmt.Errorf("creating challenge save file: %w", err)
	}
	defer f.Close()

	n, err := io.Copy(f, data)
	if err != nil {
		return "", 0, fmt.Errorf("writing challenge save file: %w", err)
	}

	return path, n, nil
}

// WriteChallengeScreenshot stores a challenge's screenshot.
func (s *Storage) WriteChallengeScreenshot(challengeID uint, data io.Reader) (string, error) {
	path := s.ChallengeScreenshotPath(challengeID)

	absPath, err := filepath.Abs(path)
	if err != nil {
		return "", fmt.Errorf("resolving challenge screenshot path: %w", err)
	}
	absSaveDir, err := filepath.Abs(s.SaveDir)
	if err != nil {
		return "", fmt.Errorf("resolving save dir: %w", err)
	}
	if !strings.HasPrefix(absPath, absSaveDir+string(filepath.Separator)) {
		return "", fmt.Errorf("invalid challenge screenshot path: outside save directory")
	}

	if err := os.MkdirAll(filepath.Dir(path), 0700); err != nil {
		return "", fmt.Errorf("creating challenge screenshot directory: %w", err)
	}

	f, err := os.Create(path)
	if err != nil {
		return "", fmt.Errorf("creating challenge screenshot file: %w", err)
	}
	defer f.Close()

	if _, err := io.Copy(f, data); err != nil {
		return "", fmt.Errorf("writing challenge screenshot file: %w", err)
	}

	return path, nil
}

// DeleteChallengeSave removes a challenge's save directory and all its contents.
func (s *Storage) DeleteChallengeSave(challengeID uint) error {
	dir := filepath.Join(s.SaveDir, "challenges", fmt.Sprintf("challenge_%d", challengeID))
	if err := s.validatePathInSaveDir(dir); err != nil {
		return err
	}
	if err := os.RemoveAll(dir); err != nil && !os.IsNotExist(err) {
		return fmt.Errorf("deleting challenge save directory: %w", err)
	}
	return nil
}

// SaveDataPath returns the filesystem path for a user's SRAM/save data file.
func (s *Storage) SaveDataPath(userID, gameID uint, filename string) string {
	safe := sanitizeFilename(filename)
	return filepath.Join(s.SaveDir, fmt.Sprintf("user_%d", userID), fmt.Sprintf("game_%d", gameID), "sram", safe)
}

// WriteSaveData stores a save data file.
func (s *Storage) WriteSaveData(userID, gameID uint, filename string, data io.Reader) (int64, error) {
	path := s.SaveDataPath(userID, gameID, filename)

	absPath, err := filepath.Abs(path)
	if err != nil {
		return 0, fmt.Errorf("resolving save data path: %w", err)
	}
	absSaveDir, err := filepath.Abs(s.SaveDir)
	if err != nil {
		return 0, fmt.Errorf("resolving save dir: %w", err)
	}
	if !strings.HasPrefix(absPath, absSaveDir+string(filepath.Separator)) {
		return 0, fmt.Errorf("invalid save data path: outside save directory")
	}

	if err := os.MkdirAll(filepath.Dir(path), 0700); err != nil {
		return 0, fmt.Errorf("creating save data directory: %w", err)
	}

	f, err := os.Create(path)
	if err != nil {
		return 0, fmt.Errorf("creating save data file: %w", err)
	}
	defer f.Close()

	n, err := io.Copy(f, data)
	if err != nil {
		return 0, fmt.Errorf("writing save data file: %w", err)
	}

	return n, nil
}

// ReadSaveData opens a save data file for reading.
func (s *Storage) ReadSaveData(path string) (*os.File, error) {
	if err := s.validatePathInSaveDir(path); err != nil {
		return nil, err
	}
	f, err := os.Open(path)
	if err != nil {
		return nil, fmt.Errorf("opening save data file: %w", err)
	}
	return f, nil
}

// DeleteSaveData removes a save data file.
func (s *Storage) DeleteSaveData(path string) error {
	if err := s.validatePathInSaveDir(path); err != nil {
		return err
	}
	if err := os.Remove(path); err != nil && !os.IsNotExist(err) {
		return fmt.Errorf("deleting save data file: %w", err)
	}
	return nil
}

// SessionSaveStatePath returns the filesystem path for a session save state file.
func (s *Storage) SessionSaveStatePath(sessionID uint, filename string) string {
	safe := sanitizeFilename(filename)
	return filepath.Join(s.SaveDir, "sessions", fmt.Sprintf("session_%d", sessionID), "states", safe)
}

// SessionSRAMPath returns the filesystem path for a session's SRAM/save data file.
func (s *Storage) SessionSRAMPath(sessionID uint, filename string) string {
	safe := sanitizeFilename(filename)
	return filepath.Join(s.SaveDir, "sessions", fmt.Sprintf("session_%d", sessionID), "sram", safe)
}

// SessionScreenshotPath returns the filesystem path for a session screenshot.
func (s *Storage) SessionScreenshotPath(sessionID uint, filename string) string {
	safe := sanitizeFilename(filename)
	return filepath.Join(s.SaveDir, "sessions", fmt.Sprintf("session_%d", sessionID), "screenshots", safe)
}

// WriteSessionSave stores a session save state file.
func (s *Storage) WriteSessionSave(sessionID uint, filename string, data io.Reader) (string, int64, error) {
	path := s.SessionSaveStatePath(sessionID, filename)

	absPath, err := filepath.Abs(path)
	if err != nil {
		return "", 0, fmt.Errorf("resolving session save path: %w", err)
	}
	absSaveDir, err := filepath.Abs(s.SaveDir)
	if err != nil {
		return "", 0, fmt.Errorf("resolving save dir: %w", err)
	}
	if !strings.HasPrefix(absPath, absSaveDir+string(filepath.Separator)) {
		return "", 0, fmt.Errorf("invalid session save path: outside save directory")
	}

	if err := os.MkdirAll(filepath.Dir(path), 0700); err != nil {
		return "", 0, fmt.Errorf("creating session save directory: %w", err)
	}

	f, err := os.Create(path)
	if err != nil {
		return "", 0, fmt.Errorf("creating session save file: %w", err)
	}
	defer f.Close()

	n, err := io.Copy(f, data)
	if err != nil {
		return "", 0, fmt.Errorf("writing session save file: %w", err)
	}

	return path, n, nil
}

// WriteSessionSRAM stores a session SRAM/save data file.
func (s *Storage) WriteSessionSRAM(sessionID uint, filename string, data io.Reader) (string, int64, error) {
	path := s.SessionSRAMPath(sessionID, filename)

	absPath, err := filepath.Abs(path)
	if err != nil {
		return "", 0, fmt.Errorf("resolving session SRAM path: %w", err)
	}
	absSaveDir, err := filepath.Abs(s.SaveDir)
	if err != nil {
		return "", 0, fmt.Errorf("resolving save dir: %w", err)
	}
	if !strings.HasPrefix(absPath, absSaveDir+string(filepath.Separator)) {
		return "", 0, fmt.Errorf("invalid session SRAM path: outside save directory")
	}

	if err := os.MkdirAll(filepath.Dir(path), 0700); err != nil {
		return "", 0, fmt.Errorf("creating session SRAM directory: %w", err)
	}

	f, err := os.Create(path)
	if err != nil {
		return "", 0, fmt.Errorf("creating session SRAM file: %w", err)
	}
	defer f.Close()

	n, err := io.Copy(f, data)
	if err != nil {
		return "", 0, fmt.Errorf("writing session SRAM file: %w", err)
	}

	return path, n, nil
}

// WriteSessionScreenshot stores a session screenshot image.
func (s *Storage) WriteSessionScreenshot(sessionID uint, filename string, data io.Reader) (string, error) {
	subpath := fmt.Sprintf("save-screenshots/sessions/session_%d/%s", sessionID, sanitizeFilename(filename))
	return s.WriteImage(subpath, data)
}

// CopyFile copies a file from src to dst, creating parent directories as needed.
// Returns the number of bytes copied.
func (s *Storage) CopyFile(src, dst string) (int64, error) {
	if err := s.validatePathInSaveDir(src); err != nil {
		return 0, fmt.Errorf("source path invalid: %w", err)
	}
	return s.copyFileRaw(src, dst)
}

// CopyImageFile copies an image file from srcSubpath to dstSubpath (both relative to ImageDir).
// Returns the number of bytes copied.
func (s *Storage) CopyImageFile(srcSubpath, dstSubpath string) (int64, error) {
	src := filepath.Join(s.ImageDir, srcSubpath)
	dst := filepath.Join(s.ImageDir, dstSubpath)

	absImageDir, err := filepath.Abs(s.ImageDir)
	if err != nil {
		return 0, fmt.Errorf("resolving image dir: %w", err)
	}
	for _, p := range []string{src, dst} {
		abs, err := filepath.Abs(p)
		if err != nil {
			return 0, fmt.Errorf("resolving path: %w", err)
		}
		if !strings.HasPrefix(abs, absImageDir+string(filepath.Separator)) {
			return 0, fmt.Errorf("path outside image directory")
		}
	}

	return s.copyFileRaw(src, dst)
}

// SessionScreenshotSubpath returns the subpath for a session screenshot without writing anything.
func (s *Storage) SessionScreenshotSubpath(sessionID uint, filename string) string {
	return fmt.Sprintf("save-screenshots/sessions/session_%d/%s", sessionID, sanitizeFilename(filename))
}

func (s *Storage) copyFileRaw(src, dst string) (int64, error) {
	if err := os.MkdirAll(filepath.Dir(dst), 0700); err != nil {
		return 0, fmt.Errorf("creating destination directory: %w", err)
	}
	in, err := os.Open(src)
	if err != nil {
		return 0, fmt.Errorf("opening source file: %w", err)
	}
	defer in.Close()
	out, err := os.Create(dst)
	if err != nil {
		return 0, fmt.Errorf("creating destination file: %w", err)
	}
	defer out.Close()
	n, err := io.Copy(out, in)
	if err != nil {
		return 0, fmt.Errorf("copying file: %w", err)
	}
	return n, nil
}

// DeleteSessionDir removes an entire session's storage directory.
func (s *Storage) DeleteSessionDir(sessionID uint) error {
	dir := filepath.Join(s.SaveDir, "sessions", fmt.Sprintf("session_%d", sessionID))
	if err := s.validatePathInSaveDir(dir); err != nil {
		return err
	}
	if err := os.RemoveAll(dir); err != nil && !os.IsNotExist(err) {
		return fmt.Errorf("deleting session directory: %w", err)
	}
	return nil
}

// ResolveGamePath resolves a relative game path (e.g. "nes/Mario.nes") to an
// absolute filesystem path by joining it with each gameDir and returning the
// first that exists on disk. Returns an error if no match is found.
func ResolveGamePath(relPath string, gameDirs []string) (string, error) {
	for _, dir := range gameDirs {
		full := filepath.Join(dir, relPath)
		if _, err := os.Stat(full); err == nil {
			return full, nil
		}
	}
	return "", fmt.Errorf("game file not found in any game directory: %s", relPath)
}

// RelativeGamePath strips the gameDirs prefix from an absolute path to produce
// a relative path suitable for database storage. If the path doesn't start with
// any gameDir prefix, it is returned unchanged.
func RelativeGamePath(absPath string, gameDirs []string) string {
	for _, dir := range gameDirs {
		absDir, err := filepath.Abs(dir)
		if err != nil {
			continue
		}
		absFile, err := filepath.Abs(absPath)
		if err != nil {
			continue
		}
		prefix := absDir + string(filepath.Separator)
		if strings.HasPrefix(absFile, prefix) {
			return absFile[len(prefix):]
		}
	}
	return absPath
}

// validatePathInSaveDir verifies that the given path resolves to within the
// save directory. This is a defense-in-depth check to prevent arbitrary file
// access/deletion if a database-stored path is ever corrupted.
func (s *Storage) validatePathInSaveDir(path string) error {
	absPath, err := filepath.Abs(path)
	if err != nil {
		return fmt.Errorf("resolving path: %w", err)
	}
	absSaveDir, err := filepath.Abs(s.SaveDir)
	if err != nil {
		return fmt.Errorf("resolving save dir: %w", err)
	}
	if !strings.HasPrefix(absPath, absSaveDir+string(filepath.Separator)) && absPath != absSaveDir {
		return fmt.Errorf("path outside save directory: %s", path)
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
