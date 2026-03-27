# ScummVM Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add ScummVM as a playable platform — console/core seed, scanner for `.scummvm` files, directory-based game serving, and trackpad default on the player.

**Architecture:** Server-side: add seed data + extend scanner to detect `.scummvm` files recursively. Extend download handler to serve ScummVM game directories as tar. Player-side: default to trackpad tab and RETRO_DEVICE_MOUSE for ScummVM games.

**Tech Stack:** Go (server), Kotlin Multiplatform (player), SQLite, libretro

---

### Task 1: Seed ScummVM console and core

**Files:**
- Modify: `server/internal/db/database.go`

- [ ] **Step 1: Add ScummVM to console seed list**

Add after the Arcade entry (line 838, before the closing `}`):

```go
		// ScummVM (generation = 100, alongside home computers)
		{Name: "ScummVM", Abbreviation: "SCUMMVM", Extensions: ".scummvm", DefaultCore: "scummvm", EmulatorJSCore: "", FolderName: "scummvm", ColorTheme: "#6b8e23", CoverAspect: "5:7", Generation: 100, SaveStateSupport: false, Playable: true},
```

- [ ] **Step 2: Add ScummVM to core seed list**

Add after the azahar entry (line 950, before the closing `}`):

```go
		{Name: "scummvm", DisplayName: "ScummVM", Description: "Point-and-click adventure game engine", Platforms: "windows,linux,macos,android"},
```

- [ ] **Step 3: Add scummvm to directory console map in scanner**

In `server/internal/scanner/scanner.go`, add to `directoryConsoleMap`:

```go
	"scummvm":      "SCUMMVM",
```

- [ ] **Step 4: Add .scummvm to extension map in scanner**

In `server/internal/scanner/scanner.go`, add to `ConsoleExtMap`:

```go
	".scummvm": "SCUMMVM",
```

- [ ] **Step 5: Run existing tests to verify no regressions**

Run: `cd server && go test ./internal/db/... ./internal/scanner/... -v`
Expected: All existing tests pass.

- [ ] **Step 6: Commit**

```bash
git add server/internal/db/database.go server/internal/scanner/scanner.go
git commit -m "feat: seed ScummVM console and core, add scanner mappings"
```

---

### Task 2: Scanner — detect ScummVM game directories

**Files:**
- Modify: `server/internal/scanner/scanner.go`
- Test: `server/internal/scanner/scanner_test.go`

- [ ] **Step 1: Write failing test for ScummVM directory detection**

Add to `server/internal/scanner/scanner_test.go`:

```go
func TestScan_DetectsScummVMGames(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	// Flat layout: scummvm/monkey1/monkey1.scummvm
	scummDir := filepath.Join(dir, "scummvm", "monkey1")
	require.NoError(t, os.MkdirAll(scummDir, 0755))
	require.NoError(t, os.WriteFile(filepath.Join(scummDir, "monkey1.scummvm"), []byte("monkey\n"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(scummDir, "MONKEY.000"), []byte("data"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(scummDir, "MONKEY.001"), []byte("data"), 0644))

	s := NewScanner(database, []string{dir})
	result, err := s.Scan(nil)
	require.NoError(t, err)
	assert.Equal(t, 1, result.NewGames)

	var game db.Game
	err = database.Preload("Console").First(&game).Error
	require.NoError(t, err)
	assert.Equal(t, "SCUMMVM", game.Console.Abbreviation)
	assert.Equal(t, "monkey1.scummvm", game.FileName)
	assert.Contains(t, game.FilePath, "scummvm")
	assert.Contains(t, game.FilePath, "monkey1")
}

func TestScan_DetectsNestedScummVMGames(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	// Nested layout: scummvm/Beneath a Steel Sky/sky/sky.scummvm
	scummDir := filepath.Join(dir, "scummvm", "Beneath a Steel Sky", "sky")
	require.NoError(t, os.MkdirAll(scummDir, 0755))
	require.NoError(t, os.WriteFile(filepath.Join(scummDir, "sky.scummvm"), []byte("sky\n"), 0644))
	require.NoError(t, os.WriteFile(filepath.Join(scummDir, "SKY.DNR"), []byte("data"), 0644))

	s := NewScanner(database, []string{dir})
	result, err := s.Scan(nil)
	require.NoError(t, err)
	assert.Equal(t, 1, result.NewGames)

	var game db.Game
	err = database.Preload("Console").First(&game).Error
	require.NoError(t, err)
	assert.Equal(t, "SCUMMVM", game.Console.Abbreviation)
	assert.Equal(t, "sky.scummvm", game.FileName)
}

func TestScan_MultipleScummVMGames(t *testing.T) {
	database := setupTestDB(t)
	dir := t.TempDir()

	// Two games
	game1Dir := filepath.Join(dir, "scummvm", "monkey1")
	require.NoError(t, os.MkdirAll(game1Dir, 0755))
	require.NoError(t, os.WriteFile(filepath.Join(game1Dir, "monkey1.scummvm"), []byte("monkey\n"), 0644))

	game2Dir := filepath.Join(dir, "scummvm", "tentacle")
	require.NoError(t, os.MkdirAll(game2Dir, 0755))
	require.NoError(t, os.WriteFile(filepath.Join(game2Dir, "tentacle.scummvm"), []byte("tentacle\n"), 0644))

	s := NewScanner(database, []string{dir})
	result, err := s.Scan(nil)
	require.NoError(t, err)
	assert.Equal(t, 2, result.NewGames)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd server && go test ./internal/scanner/... -run TestScan_DetectsScummVM -v`
Expected: FAIL — scanner doesn't detect `.scummvm` files in subdirectories.

- [ ] **Step 3: Implement ScummVM directory scanning**

The scanner's main scan loop processes individual files. ScummVM needs a pre-pass that walks the `scummvm/` folder recursively looking for `.scummvm` files. Add this to the `Scan` method in `server/internal/scanner/scanner.go`, after the existing multi-disc discovery pass but before the individual file pass.

Add a new method to the Scanner:

```go
// scanScummVMGames walks the scummvm/ directory in each game dir, looking for
// .scummvm files. Each .scummvm file marks its parent directory as a game.
func (s *Scanner) scanScummVMGames(result *ScanResult, onProgress ProgressFunc) error {
	var scummConsole db.Console
	if err := s.db.Where("abbreviation = ?", "SCUMMVM").First(&scummConsole).Error; err != nil {
		// ScummVM console not seeded — skip silently
		return nil
	}

	for _, gameDir := range s.gameDirs {
		scummRoot := filepath.Join(gameDir, "scummvm")
		if _, err := os.Stat(scummRoot); os.IsNotExist(err) {
			continue
		}

		err := filepath.WalkDir(scummRoot, func(path string, d fs.DirEntry, err error) error {
			if err != nil {
				return nil // skip unreadable entries
			}
			if d.IsDir() {
				return nil
			}
			if strings.ToLower(filepath.Ext(path)) != ".scummvm" {
				return nil
			}

			// The game directory is the parent of the .scummvm file
			gameDataDir := filepath.Dir(path)
			relPath := storage.RelativeGamePath(gameDataDir, s.gameDirs)
			fileName := filepath.Base(path)

			// Check if game already exists
			var existing db.Game
			if err := s.db.Where("file_path = ?", relPath).First(&existing).Error; err == nil {
				return nil // already scanned
			}

			// Derive title from directory name
			title := humanizeTitle(filepath.Base(gameDataDir))

			game := db.Game{
				ConsoleID: scummConsole.ID,
				Title:     title,
				FileName:  fileName,
				FilePath:  relPath,
			}

			// Calculate total directory size
			var totalSize int64
			filepath.WalkDir(gameDataDir, func(p string, d fs.DirEntry, err error) error {
				if err != nil || d.IsDir() {
					return nil
				}
				info, err := d.Info()
				if err == nil {
					totalSize += info.Size()
				}
				return nil
			})
			game.FileSize = totalSize

			if err := s.db.Create(&game).Error; err != nil {
				slog.Warn("failed to create ScummVM game", "path", relPath, "error", err)
				return nil
			}

			result.NewGames++
			if onProgress != nil {
				onProgress(fmt.Sprintf("Found ScummVM game: %s", title))
			}
			return nil
		})
		if err != nil {
			slog.Warn("error walking scummvm directory", "dir", scummRoot, "error", err)
		}
	}
	return nil
}
```

Then call it from the `Scan` method, right before the existing individual-file scan pass:

```go
	// ScummVM directory scan (before individual file processing)
	if err := s.scanScummVMGames(result, onProgress); err != nil {
		slog.Warn("ScummVM scan error", "error", err)
	}
```

Note: `humanizeTitle` should already exist in the scanner (converts filenames to readable titles). If not, add:

```go
func humanizeTitle(name string) string {
	// Replace underscores and hyphens with spaces, capitalize first letter of each word
	name = strings.ReplaceAll(name, "_", " ")
	name = strings.ReplaceAll(name, "-", " ")
	return strings.TrimSpace(name)
}
```

Also add `"io/fs"` to the imports if not already present.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd server && go test ./internal/scanner/... -run TestScan_DetectsScummVM -v`
Expected: All 3 tests PASS.

- [ ] **Step 5: Run full scanner test suite**

Run: `cd server && go test ./internal/scanner/... -v`
Expected: All tests pass (no regressions).

- [ ] **Step 6: Commit**

```bash
git add server/internal/scanner/scanner.go server/internal/scanner/scanner_test.go
git commit -m "feat: scanner detects ScummVM games by .scummvm file recursively"
```

---

### Task 3: Game download — serve ScummVM directories as tar

**Files:**
- Modify: `server/internal/api/game_handler.go`
- Test: `server/internal/api/game_handler_test.go` (if exists, or verify manually)

- [ ] **Step 1: Add ScummVM directory tar serving**

In `server/internal/api/game_handler.go`, in the `DownloadGame` function, add a check for `.scummvm` files before the existing `.cue`/`.gdi` check (before line 420):

```go
	// For .scummvm files, serve the entire game directory as a tar archive.
	// The .scummvm file lives inside a directory of game data files.
	if strings.HasSuffix(lower, ".scummvm") {
		gameDataDir := filepath.Dir(absPath)
		var files []string
		filepath.WalkDir(gameDataDir, func(p string, d fs.DirEntry, err error) error {
			if err != nil || d.IsDir() {
				return nil
			}
			files = append(files, p)
			return nil
		})

		if len(files) > 0 {
			c.Header("Content-Type", "application/x-tar")
			c.Header("Content-Disposition", fmt.Sprintf("attachment; filename=%q", game.FileName+".tar"))
			c.Status(http.StatusOK)
			if err := serveTar(c.Writer, files); err != nil {
				slog.Warn("error streaming tar for ScummVM game", "game", game.Title, "error", err)
			}
			return
		}
	}
```

Add `"io/fs"` to the imports if not already present.

- [ ] **Step 2: Verify existing tests pass**

Run: `cd server && go test ./internal/api/... -v`
Expected: All existing tests pass.

- [ ] **Step 3: Commit**

```bash
git add server/internal/api/game_handler.go
git commit -m "feat: serve ScummVM game directories as tar archives"
```

---

### Task 4: Player — default trackpad for ScummVM

**Files:**
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/data/repository/PreferencesRepositoryImpl.kt`

- [ ] **Step 1: Update getControlTab to default to trackpad for ScummVM**

Change the fallback in `getControlTab` (line 207):

```kotlin
override fun getControlTab(consoleId: String): String {
    return database.spelaDatabaseQueries.getDeviceSetting("control_tab:$consoleId")
        .executeAsOneOrNull()
        ?: if (consoleId.lowercase() == "scummvm") "trackpad" else "gamepad"
}
```

- [ ] **Step 2: Update FakePreferencesRepository in TestFakes.kt**

Update the fake to match the new default:

```kotlin
override fun getControlTab(consoleId: String): String =
    controlTabs[consoleId]
        ?: if (consoleId.lowercase() == "scummvm") "trackpad" else "gamepad"
```

Also update all other FakePreferencesRepository implementations (in `KeyMappingViewModelTest.kt`, `SyncEngineTest.kt`, `NavigationViewModelTest.kt`, `EmulationTestStubs.kt`) with the same pattern.

- [ ] **Step 3: Compile check**

Run: `cd player && ./gradlew :shared:compileKotlinDesktop`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add player/shared/src/commonMain/kotlin/com/spela/player/data/repository/PreferencesRepositoryImpl.kt \
       player/desktop/src/desktopTest/kotlin/com/spela/player/desktop/e2e/TestFakes.kt \
       player/shared/src/desktopTest/kotlin/com/spela/player/data/remote/SyncEngineTest.kt \
       player/shared/src/desktopTest/kotlin/com/spela/player/presentation/navigation/NavigationViewModelTest.kt \
       player/shared/src/desktopTest/kotlin/com/spela/player/presentation/viewmodel/KeyMappingViewModelTest.kt \
       player/shared/src/desktopTest/kotlin/com/spela/player/presentation/viewmodel/emulation/EmulationTestStubs.kt
git commit -m "feat: default to trackpad tab for ScummVM games"
```

---

### Task 5: Player — set RETRO_DEVICE_MOUSE for ScummVM

**Files:**
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/viewmodel/EmulationViewModel.kt`

- [ ] **Step 1: Set mouse device for ScummVM after core load**

In `EmulationViewModel.kt`, after the dual-screen detection block (around line 395), add:

```kotlin
            // ScummVM: set port 0 to RETRO_DEVICE_MOUSE for point-and-click input
            if (lc == "scummvm") {
                libretroController.setControllerPortDevice(0, 2) // RETRO_DEVICE_MOUSE = 2
            }
```

The variable `lc` is already defined as `consoleId.lowercase()` on line 372.

- [ ] **Step 2: Compile check**

Run: `cd player && ./gradlew :shared:compileKotlinDesktop`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run full test suite**

Run: `cd player && ./run-desktop-tests.sh`
Expected: All shared unit tests pass (420/420), no new failures in E2E.

- [ ] **Step 4: Commit**

```bash
git add player/shared/src/commonMain/kotlin/com/spela/player/presentation/viewmodel/EmulationViewModel.kt
git commit -m "feat: set RETRO_DEVICE_MOUSE for ScummVM games on launch"
```

---

### Task 6: Server — full test suite verification

**Files:** None (verification only)

- [ ] **Step 1: Run full Go test suite**

Run: `cd server && go test ./... -v`
Expected: All tests pass.

- [ ] **Step 2: Run full player test suite**

Run: `cd player && ./run-desktop-tests.sh`
Expected: All shared unit tests pass. No new E2E failures.

- [ ] **Step 3: Final commit (if any fixes needed)**

Only if adjustments were needed during verification.
