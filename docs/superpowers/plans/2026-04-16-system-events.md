# System Events Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename SecurityEvent to SystemEvent, add category support (security + operational), add dismissal, and wire up four operational event producers.

**Architecture:** Unified event table with a FK to a code-seeded category table. Existing SecurityEvent infrastructure (model, recorder, handler, frontend) is renamed in-place. New operational events use the same recorder with convenience wrappers.

**Tech Stack:** Go + GORM (backend), React + TypeScript + TanStack Query (frontend), Playwright (E2E), Vitest (unit)

---

### Task 1: Add SystemEventCategory model and seed categories

**Files:**
- Modify: `server/internal/db/models.go`
- Modify: `server/internal/db/database.go`

- [ ] **Step 1: Add SystemEventCategory struct to models.go**

Add after the `ServerSetting` struct (line 348):

```go
// SystemEventCategory groups system events into logical categories (security,
// operational, etc.). Rows are code-seeded on startup — admins cannot create or
// delete categories because no code path emits events into custom categories.
type SystemEventCategory struct {
	ID   uint   `gorm:"primarykey"`
	Code string `gorm:"size:32;uniqueIndex;not null"`
	Name string `gorm:"size:64;not null"`
}
```

- [ ] **Step 2: Add category code constants**

Add right after the struct:

```go
const (
	CategorySecurity    = "security"
	CategoryOperational = "operational"
)
```

- [ ] **Step 3: Register SystemEventCategory in AutoMigrate**

In `database.go`, add `&SystemEventCategory{}` to the `AutoMigrate` call, before `&SecurityEvent{}` (line 176):

```go
&SystemEventCategory{},
```

- [ ] **Step 4: Add category seeding function in database.go**

Add a function after the `AutoMigrate` block:

```go
// seedSystemEventCategories inserts the code-defined categories if they don't
// already exist. Called on startup after AutoMigrate.
func seedSystemEventCategories(database *gorm.DB) error {
	categories := []SystemEventCategory{
		{Code: CategorySecurity, Name: "Security"},
		{Code: CategoryOperational, Name: "Operational"},
	}
	for _, cat := range categories {
		if err := database.Where("code = ?", cat.Code).FirstOrCreate(&cat).Error; err != nil {
			return fmt.Errorf("seeding category %q: %w", cat.Code, err)
		}
	}
	return nil
}
```

Add `"fmt"` to the import block in `database.go` if not already present.

- [ ] **Step 5: Call seedSystemEventCategories from InitDatabase**

Find the function that calls `AutoMigrate` in `database.go` and add the seeding call right after it:

```go
if err := seedSystemEventCategories(database); err != nil {
	return nil, fmt.Errorf("seeding system event categories: %w", err)
}
```

- [ ] **Step 6: Verify it compiles**

Run: `cd server && go build ./...`
Expected: Success

- [ ] **Step 7: Commit**

```bash
git add server/internal/db/models.go server/internal/db/database.go
git commit -m "feat(db): add SystemEventCategory model and seed security/operational categories"
```

---

### Task 2: Rename SecurityEvent to SystemEvent and add new fields

**Files:**
- Modify: `server/internal/db/models.go`
- Modify: `server/internal/db/database.go`

- [ ] **Step 1: Rename SecurityEvent struct and constants**

In `models.go`, rename the struct `SecurityEvent` → `SystemEvent` and update all field comments. Add the new `CategoryID`, `Category`, and `DismissedAt` fields:

```go
// SystemEvent records an admin-only audit entry for authentication, session,
// or operational events. These rows back the /admin/system-events page so
// admins can investigate suspicious activity and operational issues without
// tailing container logs.
type SystemEvent struct {
	ID            uint                `gorm:"primarykey"`
	CreatedAt     time.Time           `gorm:"index;not null"`
	CategoryID    uint                `gorm:"not null;index"`
	Category      SystemEventCategory `gorm:"foreignKey:CategoryID"`
	EventType     string              `gorm:"size:64;not null;index"`
	Reason        string              `gorm:"size:64;index"`
	Username      string              `gorm:"size:128;index"`
	UsernameLower string              `gorm:"size:128;index"`
	UserID        *uint               `gorm:"index"`
	IP            string              `gorm:"size:64;index"`
	Path          string              `gorm:"size:256"`
	Metadata      string              `gorm:"type:text"`
	DismissedAt   *time.Time          `gorm:"index"`
}
```

- [ ] **Step 2: Rename event type constants**

Rename all `SecurityEvent*` constants to `SystemEvent*`:

```go
const (
	SystemEventLoginSuccess         = "login_success"
	SystemEventLoginFailed          = "login_failed"
	SystemEventLoginLocked          = "login_locked"
	SystemEventLoginBlocked         = "login_blocked"
	SystemEventAccountLocked        = "account_locked"
	SystemEventRevokedTokenUsed     = "revoked_token_used"
	SystemEventDisabledAccountToken = "disabled_account_token"
	SystemEventTokenUserMissing     = "token_user_missing"
	SystemEventStaleTokenVersion    = "stale_token_version"
)
```

- [ ] **Step 3: Add operational event type constants**

Add below the existing constants:

```go
const (
	SystemEventRACircuitBreakerTripped = "ra_circuit_breaker_tripped"
	SystemEventScraperRepeatedErrors   = "scraper_repeated_errors"
	SystemEventROMFileMissing          = "rom_file_missing"
	SystemEventAPICredentialsInvalid   = "api_credentials_invalid"
)
```

- [ ] **Step 4: Rename AllSecurityEventTypes to AllSystemEventTypes and add new types**

```go
var AllSystemEventTypes = []string{
	SystemEventLoginSuccess,
	SystemEventLoginFailed,
	SystemEventLoginLocked,
	SystemEventLoginBlocked,
	SystemEventAccountLocked,
	SystemEventRevokedTokenUsed,
	SystemEventDisabledAccountToken,
	SystemEventTokenUserMissing,
	SystemEventStaleTokenVersion,
	SystemEventRACircuitBreakerTripped,
	SystemEventScraperRepeatedErrors,
	SystemEventROMFileMissing,
	SystemEventAPICredentialsInvalid,
}
```

- [ ] **Step 5: Add SystemEventTypeCategory mapping**

Add a map from event type to category code so the recorder and handler can resolve which category an event belongs to:

```go
// SystemEventTypeCategory maps each event type to its category code. Used by
// the recorder to auto-resolve CategoryID and by the types endpoint to tell
// the frontend which types belong to which category.
var SystemEventTypeCategory = map[string]string{
	SystemEventLoginSuccess:            CategorySecurity,
	SystemEventLoginFailed:             CategorySecurity,
	SystemEventLoginLocked:             CategorySecurity,
	SystemEventLoginBlocked:            CategorySecurity,
	SystemEventAccountLocked:           CategorySecurity,
	SystemEventRevokedTokenUsed:        CategorySecurity,
	SystemEventDisabledAccountToken:    CategorySecurity,
	SystemEventTokenUserMissing:        CategorySecurity,
	SystemEventStaleTokenVersion:       CategorySecurity,
	SystemEventRACircuitBreakerTripped: CategoryOperational,
	SystemEventScraperRepeatedErrors:   CategoryOperational,
	SystemEventROMFileMissing:          CategoryOperational,
	SystemEventAPICredentialsInvalid:   CategoryOperational,
}
```

- [ ] **Step 6: Update AutoMigrate in database.go**

Replace `&SecurityEvent{}` with `&SystemEvent{}` in the `AutoMigrate` call.

- [ ] **Step 7: Add table migration for rename**

Add a migration hook in `database.go` that renames the old table and backfills the category_id. This runs right after `AutoMigrate` and before seeding (if the old table exists):

```go
// migrateSecurityEventsToSystemEvents renames the old security_events table
// and backfills category_id for existing rows. Idempotent — skips if the old
// table no longer exists.
func migrateSecurityEventsToSystemEvents(database *gorm.DB) error {
	if !database.Migrator().HasTable("security_events") {
		return nil
	}

	// Get the security category ID for backfill
	var cat SystemEventCategory
	if err := database.Where("code = ?", CategorySecurity).First(&cat).Error; err != nil {
		return fmt.Errorf("finding security category for migration: %w", err)
	}

	// Copy rows from old table to new, setting category_id
	err := database.Exec(
		"INSERT INTO system_events (id, created_at, category_id, event_type, reason, username, username_lower, user_id, ip, path, metadata) "+
			"SELECT id, created_at, ?, event_type, reason, username, username_lower, user_id, ip, path, metadata FROM security_events",
		cat.ID,
	).Error
	if err != nil {
		return fmt.Errorf("migrating security_events rows: %w", err)
	}

	// Drop old table
	if err := database.Exec("DROP TABLE security_events").Error; err != nil {
		return fmt.Errorf("dropping security_events: %w", err)
	}

	return nil
}
```

Call it after `seedSystemEventCategories` and before the return in `InitDatabase`:

```go
if err := migrateSecurityEventsToSystemEvents(database); err != nil {
	slog.Warn("security_events migration failed (may already be done)", "error", err)
}
```

- [ ] **Step 8: Verify it compiles (will have errors in other files — expected)**

Run: `cd server && go build ./... 2>&1 | head -30`
Expected: Compilation errors in files that still reference `SecurityEvent` — this is fine, we'll fix them in the next tasks.

- [ ] **Step 9: Commit**

```bash
git add server/internal/db/models.go server/internal/db/database.go
git commit -m "feat(db): rename SecurityEvent to SystemEvent with category and dismissal fields"
```

---

### Task 3: Rename recorder (db package)

**Files:**
- Rename: `server/internal/db/security_event_recorder.go` → `server/internal/db/system_event_recorder.go`
- Rename: `server/internal/db/security_event_recorder_test.go` → `server/internal/db/system_event_recorder_test.go`
- Modify: `server/internal/db/testing.go`

- [ ] **Step 1: Rename the recorder file**

```bash
cd server && git mv internal/db/security_event_recorder.go internal/db/system_event_recorder.go
```

- [ ] **Step 2: Update the recorder to use SystemEvent types**

In `system_event_recorder.go`:

Rename `SecurityEventInput` → `SystemEventInput`. Remove the old `Category` field from the input — instead, category is resolved automatically from `SystemEventTypeCategory`.

```go
// SystemEventInput is the parameter bag accepted by the recording functions.
// Only EventType is required — every other field is optional and is only
// persisted when non-empty / non-nil.
type SystemEventInput struct {
	EventType string
	Reason    string
	Username  string
	UserID    *uint
	IP        string
	Path      string
	Metadata  map[string]any
}
```

Rename all internal type references:
- `securityEventDedupKey` → `systemEventDedupKey`
- `securityEventDedup` → `systemEventDedup`
- `globalSecurityEventDedup` → `globalSystemEventDedup`
- `securityEventDedupWindow` stays (unchanged constant value)

Update `eventTypeShouldDedup` to include operational event types that need dedup:

```go
func eventTypeShouldDedup(eventType string) bool {
	switch eventType {
	case SystemEventRevokedTokenUsed,
		SystemEventDisabledAccountToken,
		SystemEventTokenUserMissing,
		SystemEventStaleTokenVersion,
		SystemEventRACircuitBreakerTripped,
		SystemEventScraperRepeatedErrors,
		SystemEventROMFileMissing,
		SystemEventAPICredentialsInvalid:
		return true
	}
	return false
}
```

Add a category ID cache and resolver:

```go
var (
	categoryIDCache   map[string]uint
	categoryIDCacheMu sync.Mutex
)

func resolveCategoryID(database *gorm.DB, eventType string) uint {
	categoryIDCacheMu.Lock()
	defer categoryIDCacheMu.Unlock()

	if categoryIDCache == nil {
		categoryIDCache = make(map[string]uint)
		var cats []SystemEventCategory
		database.Find(&cats)
		for _, c := range cats {
			categoryIDCache[c.Code] = c.ID
		}
	}

	code, ok := SystemEventTypeCategory[eventType]
	if !ok {
		code = CategoryOperational // fallback
	}
	return categoryIDCache[code]
}
```

Rename `RecordSecurityEvent` → internal `recordSystemEvent`, and add public convenience functions:

```go
// recordSystemEvent persists a system event row and emits a structured slog
// entry. Best-effort: a DB failure is logged but never blocks the caller.
func recordSystemEvent(database *gorm.DB, in SystemEventInput) {
	logSystemEvent(in)

	if eventTypeShouldDedup(in.EventType) && !globalSystemEventDedup.shouldRecord(in) {
		return
	}

	var metaJSON string
	if len(in.Metadata) > 0 {
		if b, err := json.Marshal(in.Metadata); err == nil {
			metaJSON = string(b)
		}
	}

	row := SystemEvent{
		CategoryID:    resolveCategoryID(database, in.EventType),
		EventType:     in.EventType,
		Reason:        in.Reason,
		Username:      in.Username,
		UsernameLower: strings.ToLower(in.Username),
		UserID:        in.UserID,
		IP:            in.IP,
		Path:          in.Path,
		Metadata:      metaJSON,
	}
	if err := database.Create(&row).Error; err != nil {
		slog.Warn("failed to persist system event",
			"event", in.EventType,
			"error", err,
		)
	}
}

// RecordSecurityEvent records a security-category system event.
func RecordSecurityEvent(database *gorm.DB, in SystemEventInput) {
	recordSystemEvent(database, in)
}

// RecordOperationalEvent records an operational-category system event.
func RecordOperationalEvent(database *gorm.DB, in SystemEventInput) {
	recordSystemEvent(database, in)
}
```

Rename `logSecurityEvent` → `logSystemEvent` and update the slog prefix:

```go
func logSystemEvent(in SystemEventInput) {
	category := SystemEventTypeCategory[in.EventType]
	logArgs := []any{
		"event", in.EventType,
		"category", category,
		"username", in.Username,
		"ip", in.IP,
	}
	if in.Reason != "" {
		logArgs = append(logArgs, "reason", in.Reason)
	}
	if in.UserID != nil {
		logArgs = append(logArgs, "userId", *in.UserID)
	}
	if in.Path != "" {
		logArgs = append(logArgs, "path", in.Path)
	}
	for k, v := range in.Metadata {
		logArgs = append(logArgs, k, v)
	}
	if in.EventType == SystemEventLoginSuccess {
		slog.Info("system-event: "+in.EventType, logArgs...)
	} else {
		slog.Warn("system-event: "+in.EventType, logArgs...)
	}
}
```

- [ ] **Step 3: Rename the test file and update references**

```bash
cd server && git mv internal/db/security_event_recorder_test.go internal/db/system_event_recorder_test.go
```

In `system_event_recorder_test.go`, update all references:
- `SecurityEvent` → `SystemEvent` in struct references
- `SecurityEventInput` → `SystemEventInput`
- `SecurityEventLoginFailed` → `SystemEventLoginFailed`
- `SecurityEventRevokedTokenUsed` → `SystemEventRevokedTokenUsed`
- `RecordSecurityEvent` → `RecordSecurityEvent` (still the public name)
- `ResetSecurityEventDedupForTest` → `ResetSystemEventDedupForTest`

Update `newRecorderTestDB` to also migrate `SystemEventCategory` and seed categories:

```go
func newRecorderTestDB(t *testing.T) *gorm.DB {
	t.Helper()
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(&db.SystemEventCategory{}, &db.SystemEvent{}))
	// Seed categories so the recorder can resolve category IDs.
	database.Create(&db.SystemEventCategory{Code: db.CategorySecurity, Name: "Security"})
	database.Create(&db.SystemEventCategory{Code: db.CategoryOperational, Name: "Operational"})
	return database
}
```

- [ ] **Step 4: Update testing.go**

Rename `ResetSecurityEventDedupForTest` → `ResetSystemEventDedupForTest` and update internal references:

```go
// ResetSystemEventDedupForTest clears the process-level system event dedup
// cache. Tests that exercise RecordSecurityEvent or RecordOperationalEvent
// must call this in their setup.
func ResetSystemEventDedupForTest() {
	globalSystemEventDedup.mu.Lock()
	defer globalSystemEventDedup.mu.Unlock()
	globalSystemEventDedup.lastSeen = make(map[systemEventDedupKey]time.Time)
}
```

Also add a function to reset the category ID cache for tests:

```go
// ResetCategoryIDCacheForTest clears the cached category ID lookup so tests
// using fresh in-memory databases don't get stale IDs from a prior test.
func ResetCategoryIDCacheForTest() {
	categoryIDCacheMu.Lock()
	defer categoryIDCacheMu.Unlock()
	categoryIDCache = nil
}
```

- [ ] **Step 5: Verify it compiles**

Run: `cd server && go build ./... 2>&1 | head -30`
Expected: Errors in `api` package referencing old names — expected, fixed in next tasks.

- [ ] **Step 6: Commit**

```bash
cd server && git add -A
git commit -m "refactor(db): rename SecurityEvent recorder to SystemEvent with category resolution"
```

---

### Task 4: Update API handler and recorder wrapper

**Files:**
- Rename: `server/internal/api/security_event_handler.go` → `server/internal/api/system_event_handler.go`
- Rename: `server/internal/api/security_event_recorder.go` → `server/internal/api/system_event_recorder.go`
- Modify: `server/internal/api/router.go`

- [ ] **Step 1: Rename handler file**

```bash
cd server && git mv internal/api/security_event_handler.go internal/api/system_event_handler.go
```

- [ ] **Step 2: Rename recorder wrapper file**

```bash
cd server && git mv internal/api/security_event_recorder.go internal/api/system_event_recorder.go
```

- [ ] **Step 3: Update handler types and functions**

In `system_event_handler.go`:

Rename:
- `SecurityEventHandler` → `SystemEventHandler`
- `SecurityEventResponse` → `SystemEventResponse`
- `SecurityEventsListResponse` → `SystemEventsListResponse`
- `SecurityEventTypesResponse` → `SystemEventTypesResponse`
- `ListSecurityEvents` → `ListSystemEvents`
- `GetSecurityEvent` → `GetSystemEvent`
- `GetSecurityEventTypes` → `GetSystemEventTypes`
- `toSecurityEventResponse` → `toSystemEventResponse`
- `defaultSecurityEventsSince` → `defaultSystemEventsSince`

Add `CategoryCode`, `CategoryName`, and `DismissedAt` fields to the response:

```go
type SystemEventResponse struct {
	ID           uint           `json:"id"`
	CreatedAt    time.Time      `json:"createdAt"`
	CategoryCode string         `json:"categoryCode"`
	CategoryName string         `json:"categoryName"`
	EventType    string         `json:"eventType"`
	Reason       string         `json:"reason,omitempty"`
	Username     string         `json:"username,omitempty"`
	UserID       *uint          `json:"userId,omitempty"`
	IP           string         `json:"ip,omitempty"`
	Path         string         `json:"path,omitempty"`
	Metadata     map[string]any `json:"metadata,omitempty"`
	MetadataRaw  string         `json:"metadataRaw,omitempty"`
	DismissedAt  *time.Time     `json:"dismissedAt,omitempty"`
}
```

Update `toSystemEventResponse` to populate the new fields:

```go
func toSystemEventResponse(e db.SystemEvent) SystemEventResponse {
	r := SystemEventResponse{
		ID:           e.ID,
		CreatedAt:    e.CreatedAt,
		CategoryCode: e.Category.Code,
		CategoryName: e.Category.Name,
		EventType:    e.EventType,
		Reason:       e.Reason,
		Username:     e.Username,
		UserID:       e.UserID,
		IP:           e.IP,
		Path:         e.Path,
		DismissedAt:  e.DismissedAt,
	}
	if e.Metadata != "" {
		var m map[string]any
		if err := json.Unmarshal([]byte(e.Metadata), &m); err == nil {
			r.Metadata = m
		} else {
			slog.Warn("failed to parse system event metadata JSON",
				"id", e.ID,
				"eventType", e.EventType,
				"error", err,
				"rawPrefix", truncateForLog(e.Metadata, 200),
			)
			r.MetadataRaw = e.Metadata
		}
	}
	return r
}
```

Update `ListSystemEvents` to add `.Preload("Category")` to the query, and add category and dismissed filters:

```go
// Category filter
if cat := strings.TrimSpace(c.Query("category")); cat != "" {
	var catRow db.SystemEventCategory
	if err := h.DB.Where("code = ?", cat).First(&catRow).Error; err == nil {
		q = q.Where("category_id = ?", catRow.ID)
	}
}

// Dismissed filter — default to excluding dismissed events
showDismissed := strings.TrimSpace(c.Query("dismissed")) == "true"
if !showDismissed {
	q = q.Where("dismissed_at IS NULL")
}
```

Add `.Preload("Category")` to the Find query:

```go
if err := q.Preload("Category").Order("created_at DESC").
	Limit(pageSize).
	Offset((page - 1) * pageSize).
	Find(&rows).Error; err != nil {
```

Update `GetSystemEvent` to also preload Category:

```go
if err := h.DB.Preload("Category").First(&e, id).Error; err != nil {
```

Update `GetSystemEventTypes` to return type+category pairs:

```go
type SystemEventTypeInfo struct {
	Type     string `json:"type"`
	Category string `json:"category"`
}

type SystemEventTypesResponse struct {
	Types []SystemEventTypeInfo `json:"types"`
}

func (h *SystemEventHandler) GetSystemEventTypes(c *gin.Context) {
	types := make([]SystemEventTypeInfo, 0, len(db.AllSystemEventTypes))
	for _, t := range db.AllSystemEventTypes {
		types = append(types, SystemEventTypeInfo{
			Type:     t,
			Category: db.SystemEventTypeCategory[t],
		})
	}
	c.JSON(http.StatusOK, SystemEventTypesResponse{Types: types})
}
```

Add new handler methods:

```go
// GetSystemEventCategories returns the seeded categories.
func (h *SystemEventHandler) GetSystemEventCategories(c *gin.Context) {
	var cats []db.SystemEventCategory
	if err := h.DB.Find(&cats).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to load categories"})
		return
	}
	type catResponse struct {
		Code string `json:"code"`
		Name string `json:"name"`
	}
	out := make([]catResponse, 0, len(cats))
	for _, cat := range cats {
		out = append(out, catResponse{Code: cat.Code, Name: cat.Name})
	}
	c.JSON(http.StatusOK, out)
}

// DismissSystemEvent sets dismissed_at on a single event.
func (h *SystemEventHandler) DismissSystemEvent(c *gin.Context) {
	id, err := strconv.ParseUint(c.Param("id"), 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid id"})
		return
	}
	now := time.Now()
	result := h.DB.Model(&db.SystemEvent{}).Where("id = ?", id).Update("dismissed_at", now)
	if result.Error != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to dismiss event"})
		return
	}
	if result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "event not found"})
		return
	}
	c.JSON(http.StatusOK, gin.H{"dismissed": true})
}
```

- [ ] **Step 4: Update recorder wrapper**

In `system_event_recorder.go`:

```go
// recordSecurityEventCtx is a thin convenience wrapper that extracts the
// client IP and request path from a gin.Context and then delegates to
// db.RecordSecurityEvent.
func recordSecurityEventCtx(database *gorm.DB, c *gin.Context, in db.SystemEventInput) {
	if in.IP == "" {
		in.IP = c.ClientIP()
	}
	if in.Path == "" && c.Request != nil && c.Request.URL != nil {
		in.Path = c.Request.URL.Path
	}
	db.RecordSecurityEvent(database, in)
}

// recordOperationalEventCtx is the operational-event counterpart.
func recordOperationalEventCtx(database *gorm.DB, c *gin.Context, in db.SystemEventInput) {
	if in.IP == "" {
		in.IP = c.ClientIP()
	}
	if in.Path == "" && c.Request != nil && c.Request.URL != nil {
		in.Path = c.Request.URL.Path
	}
	db.RecordOperationalEvent(database, in)
}
```

- [ ] **Step 5: Update router.go**

Replace the security event route registration (around line 580-584) with:

```go
// System event audit log (admin-only)
systemEventHandler := &SystemEventHandler{DB: cfg.DB}
admin.GET("/system-events", systemEventHandler.ListSystemEvents)
admin.GET("/system-events/types", systemEventHandler.GetSystemEventTypes)
admin.GET("/system-events/categories", systemEventHandler.GetSystemEventCategories)
admin.GET("/system-events/:id", systemEventHandler.GetSystemEvent)
admin.PUT("/system-events/:id/dismiss", systemEventHandler.DismissSystemEvent)
```

- [ ] **Step 6: Update all callers of old constant names**

In `auth_handler.go`, update all references from `db.SecurityEvent*` → `db.SystemEvent*`:
- `db.SecurityEventLoginLocked` → `db.SystemEventLoginLocked`
- `db.SecurityEventLoginFailed` → `db.SystemEventLoginFailed`
- `db.SecurityEventAccountLocked` → `db.SystemEventAccountLocked`
- `db.SecurityEventLoginBlocked` → `db.SystemEventLoginBlocked`
- `db.SecurityEventLoginSuccess` → `db.SystemEventLoginSuccess`

And `SecurityEventInput` → `SystemEventInput`.

Rename `pruneExpiredSecurityEvents` → `pruneExpiredSystemEvents` and update it:

```go
const systemEventRetention = 90 * 24 * time.Hour

func pruneExpiredSystemEvents(database *gorm.DB) int64 {
	result := database.Where("created_at < ?", time.Now().Add(-systemEventRetention)).Delete(&db.SystemEvent{})
	return result.RowsAffected
}
```

Update the call in `StartTokenCleanup`:

```go
if count := pruneExpiredSystemEvents(database); count > 0 {
	slog.Info("pruned old system events", "count", count)
}
```

In `middleware.go`, update all references:
- `db.SecurityEvent*` → `db.SystemEvent*`
- `db.SecurityEventInput` → `db.SystemEventInput`

In `test_handler.go`, update the unscoped delete:
- `db.SecurityEvent{}` → `db.SystemEvent{}`

In `api_test.go`, update AutoMigrate:
- `db.SecurityEvent{}` → `db.SystemEvent{}`

Also add `&db.SystemEventCategory{}` to the AutoMigrate call and seed categories in the test setup.

- [ ] **Step 7: Verify it compiles**

Run: `cd server && go build ./...`
Expected: Success

- [ ] **Step 8: Run Go tests**

Run: `cd server && go test ./... -count=1 2>&1 | tail -20`
Expected: Some test failures due to renamed handler test file — fixed in next task.

- [ ] **Step 9: Commit**

```bash
cd server && git add -A
git commit -m "refactor(api): rename SecurityEvent handler/recorder to SystemEvent with category and dismiss endpoints"
```

---

### Task 5: Update Go handler tests

**Files:**
- Rename: `server/internal/api/security_event_handler_test.go` → `server/internal/api/system_event_handler_test.go`

- [ ] **Step 1: Rename test file**

```bash
cd server && git mv internal/api/security_event_handler_test.go internal/api/system_event_handler_test.go
```

- [ ] **Step 2: Update all references in the test file**

Globally rename:
- `SecurityEvent` → `SystemEvent` (struct refs)
- `SecurityEventInput` → `SystemEventInput`
- `SecurityEventResponse` → `SystemEventResponse`
- `SecurityEventsListResponse` → `SystemEventsListResponse`
- `SecurityEventTypesResponse` → `SystemEventTypesResponse`
- `SecurityEventLoginFailed` → `SystemEventLoginFailed`
- `SecurityEventLoginSuccess` → `SystemEventLoginSuccess`
- `SecurityEventAccountLocked` → `SystemEventAccountLocked`
- `SecurityEventRevokedTokenUsed` → `SystemEventRevokedTokenUsed`
- `AllSecurityEventTypes` → `AllSystemEventTypes`
- `pruneExpiredSecurityEvents` → `pruneExpiredSystemEvents`
- `makeSecurityEvent` → `makeSystemEvent`
- `seedSecurityEvents` → `seedSystemEvents`
- `ResetSecurityEventDedupForTest` → `ResetSystemEventDedupForTest`

Update all URL paths:
- `/api/admin/security-events` → `/api/admin/system-events`

Update all test function names:
- `TestListSecurityEvents_*` → `TestListSystemEvents_*`
- `TestGetSecurityEvent_*` → `TestGetSystemEvent_*`
- `TestGetSecurityEventTypes` → `TestGetSystemEventTypes`
- `TestPruneExpiredSecurityEvents` → `TestPruneExpiredSystemEvents`
- `TestLoginFailureRecordsSecurityEvent` → `TestLoginFailureRecordsSystemEvent`

Update `makeSystemEvent` to set CategoryID:

```go
func makeSystemEvent(e db.SystemEvent) db.SystemEvent {
	e.UsernameLower = strings.ToLower(e.Username)
	if e.CategoryID == 0 {
		e.CategoryID = 1 // security category
	}
	return e
}
```

Update `seedSystemEvents` to set CategoryID on all rows (security category = 1).

Update the types test to check the new response shape:

```go
func TestGetSystemEventTypes(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)

	req := httptest.NewRequest("GET", "/api/admin/system-events/types", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var resp SystemEventTypesResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Len(t, resp.Types, len(db.AllSystemEventTypes))
	// Verify each type has a category
	for _, ti := range resp.Types {
		assert.NotEmpty(t, ti.Category)
	}
}
```

- [ ] **Step 3: Add tests for new endpoints**

Add test for category filter:

```go
func TestListSystemEvents_FilterByCategory(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)

	// Seed one security event and one operational event
	var secCat, opCat db.SystemEventCategory
	database.Where("code = ?", db.CategorySecurity).First(&secCat)
	database.Where("code = ?", db.CategoryOperational).First(&opCat)

	secEvent := makeSystemEvent(db.SystemEvent{
		CategoryID: secCat.ID,
		EventType:  db.SystemEventLoginFailed,
		Username:   "alice",
		IP:         "10.0.0.1",
		CreatedAt:  time.Now().Add(-1 * time.Hour),
	})
	opEvent := makeSystemEvent(db.SystemEvent{
		CategoryID: opCat.ID,
		EventType:  db.SystemEventRACircuitBreakerTripped,
		CreatedAt:  time.Now().Add(-1 * time.Hour),
	})
	require.NoError(t, database.Create(&secEvent).Error)
	require.NoError(t, database.Create(&opEvent).Error)

	req := httptest.NewRequest("GET", "/api/admin/system-events?category=operational", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var resp SystemEventsListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, int64(1), resp.Total)
	assert.Equal(t, db.SystemEventRACircuitBreakerTripped, resp.Data[0].EventType)
}
```

Add test for dismiss:

```go
func TestDismissSystemEvent(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)

	var secCat db.SystemEventCategory
	database.Where("code = ?", db.CategorySecurity).First(&secCat)

	row := db.SystemEvent{
		CategoryID: secCat.ID,
		EventType:  db.SystemEventLoginFailed,
		Username:   "alice",
		CreatedAt:  time.Now(),
	}
	require.NoError(t, database.Create(&row).Error)

	// Dismiss it
	req := httptest.NewRequest("PUT", "/api/admin/system-events/"+strconv.FormatUint(uint64(row.ID), 10)+"/dismiss", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Default list should exclude it
	req = httptest.NewRequest("GET", "/api/admin/system-events", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w = httptest.NewRecorder()
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var resp SystemEventsListResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, int64(0), resp.Total)

	// dismissed=true should include it
	req = httptest.NewRequest("GET", "/api/admin/system-events?dismissed=true", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w = httptest.NewRecorder()
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, int64(1), resp.Total)
	assert.NotNil(t, resp.Data[0].DismissedAt)
}
```

Add test for categories endpoint:

```go
func TestGetSystemEventCategories(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)

	req := httptest.NewRequest("GET", "/api/admin/system-events/categories", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var cats []struct {
		Code string `json:"code"`
		Name string `json:"name"`
	}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &cats))
	assert.Len(t, cats, 2)
	codes := []string{cats[0].Code, cats[1].Code}
	assert.Contains(t, codes, "security")
	assert.Contains(t, codes, "operational")
}
```

- [ ] **Step 4: Run Go tests**

Run: `cd server && go test ./... -count=1 2>&1 | tail -30`
Expected: All pass

- [ ] **Step 5: Commit**

```bash
cd server && git add -A
git commit -m "test(api): rename and extend handler tests for SystemEvent with category/dismiss coverage"
```

---

### Task 6: Wire up operational event producers

**Files:**
- Modify: `server/internal/scraper/scraper.go`
- Modify: `server/internal/scraper/worker.go`
- Modify: `server/internal/api/game_handler.go`

- [ ] **Step 1: Wire up RA circuit breaker event**

In `scraper.go`, update `tryFetchRAAchievements` to record an operational event when the circuit opens. The scraper needs access to the DB (it already has `s.DB`):

```go
func (s *Scraper) tryFetchRAAchievements(game *db.Game) {
	if !s.IsRAConfigured() || s.raCircuitOpen {
		return
	}
	if err := s.FetchRAAchievements(game); err != nil {
		s.raConsecutiveFailures++
		if s.raConsecutiveFailures >= raCircuitBreakerThreshold {
			s.raCircuitOpen = true
			slog.Warn("RA achievements disabled for remainder of scrape",
				"consecutiveFailures", s.raConsecutiveFailures, "lastError", err)
			db.RecordOperationalEvent(s.DB, db.SystemEventInput{
				EventType: db.SystemEventRACircuitBreakerTripped,
				Metadata: map[string]any{
					"consecutiveFailures": s.raConsecutiveFailures,
					"lastError":           err.Error(),
				},
			})
		} else {
			slog.Warn("RA achievement fetch failed", "game", game.Title, "error", err)
		}
	} else {
		s.raConsecutiveFailures = 0
	}
}
```

- [ ] **Step 2: Wire up scraper repeated errors**

In `worker.go`, add a consecutive failure counter and emit an event when the threshold is hit. Add fields to the worker struct (find the struct definition):

```go
scraperConsecutiveFailures int
```

In the `processItem` function (or equivalent), after a scrape failure:

```go
w.scraperConsecutiveFailures++
if w.scraperConsecutiveFailures >= 5 {
	db.RecordOperationalEvent(w.db, db.SystemEventInput{
		EventType: db.SystemEventScraperRepeatedErrors,
		Metadata: map[string]any{
			"consecutiveFailures": w.scraperConsecutiveFailures,
			"error":               err.Error(),
			"gameTitle":            game.Title,
		},
	})
}
```

And on success, reset: `w.scraperConsecutiveFailures = 0`.

- [ ] **Step 3: Wire up ROM file missing event**

In `game_handler.go`, in `DownloadGame`, after the file not found check (around line 407):

```go
absPath, err := storage.ResolveGamePath(game.FilePath, h.GameDirs)
if err != nil {
	db.RecordOperationalEvent(h.DB, db.SystemEventInput{
		EventType: db.SystemEventROMFileMissing,
		Metadata: map[string]any{
			"gameId":       game.ID,
			"gameTitle":    game.Title,
			"expectedPath": game.FilePath,
		},
	})
	c.JSON(http.StatusNotFound, gin.H{"error": "game file not found"})
	return
}
```

- [ ] **Step 4: Wire up API credentials invalid**

The RA client (`server/internal/retroachievements/client.go`) returns errors containing the HTTP status code when requests fail, e.g. `"RA gameid returned status 403: ..."`. The SteamGridDB client (`server/internal/scraper/steamgriddb.go:85`) explicitly checks `http.StatusUnauthorized` and returns `"unauthorized: invalid SteamGridDB API key"`.

Add detection in `tryFetchRAAchievements` in `scraper.go` — when the error message contains `status 401` or `status 403`, emit the credentials event before the circuit breaker:

```go
if err := s.FetchRAAchievements(game); err != nil {
	// Detect auth/blocked errors (RA client includes status code in error string)
	errStr := err.Error()
	if strings.Contains(errStr, "status 401") || strings.Contains(errStr, "status 403") {
		db.RecordOperationalEvent(s.DB, db.SystemEventInput{
			EventType: db.SystemEventAPICredentialsInvalid,
			Metadata: map[string]any{
				"service": "retroachievements",
				"error":   errStr,
			},
		})
	}
	s.raConsecutiveFailures++
	// ... rest of circuit breaker logic
```

Add `"strings"` to imports if not already present.

For SteamGridDB, add detection in `steamgriddb.go` — the `doRequest` method (line 85) already checks for `StatusUnauthorized`. Add an event emission there:

```go
if resp.StatusCode == http.StatusUnauthorized {
	db.RecordOperationalEvent(c.db, db.SystemEventInput{
		EventType: db.SystemEventAPICredentialsInvalid,
		Metadata: map[string]any{
			"service": "steamgriddb",
			"error":   "unauthorized: invalid SteamGridDB API key",
		},
	})
	return nil, fmt.Errorf("unauthorized: invalid SteamGridDB API key")
}
```

The SteamGridDB client will need access to the GORM `*gorm.DB` — check whether the struct already has it or whether it needs to be passed through. If not available, pass it through the `Scraper` that owns the client, or add a `DB` field to the SteamGridDB client struct.

- [ ] **Step 5: Verify it compiles**

Run: `cd server && go build ./...`
Expected: Success

- [ ] **Step 6: Run Go tests**

Run: `cd server && go test ./... -count=1 2>&1 | tail -20`
Expected: All pass

- [ ] **Step 7: Commit**

```bash
cd server && git add -A
git commit -m "feat(scraper): wire up operational event producers for RA circuit breaker, scraper errors, ROM missing, API credentials"
```

---

### Task 7: Update frontend types and API routes

**Files:**
- Modify: `web/src/types/api.ts`
- Modify: `web/src/lib/api-routes.ts`

- [ ] **Step 1: Rename and extend types in api.ts**

Replace the `SecurityEventType`, `SecurityEventTypeLike`, `SecurityEvent`, `SecurityEventsListResponse`, and `SecurityEventsListFilters` types:

```typescript
// System event audit log (admin-only). Mirrors the SystemEvent model on the
// server. The metadata blob is per-event-type and may include things like
// failedCount, lockedUntil, consecutiveFailures, etc.
export type SecurityEventType =
  | "login_success"
  | "login_failed"
  | "login_locked"
  | "login_blocked"
  | "account_locked"
  | "revoked_token_used"
  | "disabled_account_token"
  | "token_user_missing"
  | "stale_token_version";

export type OperationalEventType =
  | "ra_circuit_breaker_tripped"
  | "scraper_repeated_errors"
  | "rom_file_missing"
  | "api_credentials_invalid";

export type SystemEventType = SecurityEventType | OperationalEventType;

export type SystemEventTypeLike = SystemEventType | (string & {});

export type SystemEventCategoryCode = "security" | "operational";

export interface SystemEvent {
  id: number;
  createdAt: string;
  categoryCode: SystemEventCategoryCode;
  categoryName: string;
  eventType: SystemEventTypeLike;
  reason?: string;
  username?: string;
  userId?: number;
  ip?: string;
  path?: string;
  metadata?: Record<string, unknown>;
  metadataRaw?: string;
  dismissedAt?: string | null;
}

export interface SystemEventsListResponse {
  data: SystemEvent[];
  total: number;
  page: number;
  pageSize: number;
}

export interface SystemEventsListFilters {
  page?: number;
  pageSize?: number;
  eventType?: SystemEventType[];
  category?: SystemEventCategoryCode;
  username?: string;
  ip?: string;
  since?: string;
  dismissed?: boolean;
}

export interface SystemEventCategory {
  code: SystemEventCategoryCode;
  name: string;
}

export interface SystemEventTypeInfo {
  type: string;
  category: SystemEventCategoryCode;
}
```

Keep the old `SecurityEventType` type exported as an alias to avoid breaking existing filter chip code:
The existing `SecurityEventType` stays as defined (it's the security subset), and `SystemEventType` is the union.

- [ ] **Step 2: Update API routes**

In `api-routes.ts`, replace the security-events paths in `ApiGetPath`:

```typescript
| "/admin/system-events"
| "/admin/system-events/types"
| "/admin/system-events/categories"
| `/admin/system-events/${string}`
```

Add to `ApiPutPath`:

```typescript
| `/admin/system-events/${string}/dismiss`
```

- [ ] **Step 3: Verify TypeScript compiles**

Run: `cd web && npx tsc --noEmit 2>&1 | head -30`
Expected: Errors in hooks/components referencing old types — fixed in next tasks.

- [ ] **Step 4: Commit**

```bash
git add web/src/types/api.ts web/src/lib/api-routes.ts
git commit -m "refactor(web): rename SecurityEvent types to SystemEvent with category and dismiss support"
```

---

### Task 8: Update frontend hooks

**Files:**
- Rename: `web/src/hooks/use-security-events.ts` → `web/src/hooks/use-system-events.ts`

- [ ] **Step 1: Rename the file**

```bash
cd web && git mv src/hooks/use-security-events.ts src/hooks/use-system-events.ts
```

- [ ] **Step 2: Rewrite the hooks**

```typescript
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type {
  SystemEvent,
  SystemEventCategory,
  SystemEventTypeInfo,
  SystemEventsListFilters,
  SystemEventsListResponse,
} from "@/types/api";

function buildSystemEventsQuery(filters: SystemEventsListFilters): string {
  const params = new URLSearchParams();
  if (filters.page) params.set("page", String(filters.page));
  if (filters.pageSize) params.set("pageSize", String(filters.pageSize));
  if (filters.username) params.set("username", filters.username);
  if (filters.ip) params.set("ip", filters.ip);
  if (filters.category) params.set("category", filters.category);
  if (filters.dismissed) params.set("dismissed", "true");
  if (filters.since && filters.since !== "all") {
    params.set("since", filters.since);
  }
  if (filters.eventType?.length) {
    for (const t of filters.eventType) params.append("eventType", t);
  }
  return params.toString();
}

export function useSystemEvents(filters: SystemEventsListFilters) {
  const qs = buildSystemEventsQuery(filters);
  const path = qs
    ? (`/admin/system-events?${qs}` as const)
    : ("/admin/system-events" as const);
  return useQuery({
    queryKey: ["admin", "system-events", filters],
    queryFn: () => api.get<SystemEventsListResponse>(path),
    refetchInterval: 60_000,
    refetchOnWindowFocus: true,
  });
}

export function useSystemEvent(id: number | null) {
  return useQuery({
    queryKey: ["admin", "system-events", id],
    queryFn: () => {
      const path = `/admin/system-events/${id}` as const;
      return api.get<SystemEvent>(path);
    },
    enabled: id !== null,
  });
}

export function useSystemEventTypes() {
  return useQuery({
    queryKey: ["admin", "system-events", "types"],
    queryFn: () =>
      api.get<{ types: SystemEventTypeInfo[] }>(
        "/admin/system-events/types",
      ),
    staleTime: Infinity,
  });
}

export function useSystemEventCategories() {
  return useQuery({
    queryKey: ["admin", "system-events", "categories"],
    queryFn: () =>
      api.get<SystemEventCategory[]>(
        "/admin/system-events/categories",
      ),
    staleTime: Infinity,
  });
}

export function useDismissSystemEvent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) =>
      api.put(`/admin/system-events/${id}/dismiss` as const, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "system-events"] });
    },
  });
}
```

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "refactor(web): rename use-security-events hooks to use-system-events with category/dismiss support"
```

---

### Task 9: Update frontend components — badge and filters

**Files:**
- Rename: `web/src/features/admin/components/security-event-badge.tsx` → `web/src/features/admin/components/system-event-badge.tsx`
- Rename: `web/src/features/admin/components/security-events-filters.tsx` → `web/src/features/admin/components/system-events-filters.tsx`

- [ ] **Step 1: Rename badge file**

```bash
cd web && git mv src/features/admin/components/security-event-badge.tsx src/features/admin/components/system-event-badge.tsx
```

- [ ] **Step 2: Update badge component**

In `system-event-badge.tsx`:

Update imports to use new types. Rename all exports:
- `SECURITY_EVENT_META` → `SYSTEM_EVENT_META`
- `SecurityEventBadge` → `SystemEventBadge`
- `getSecurityEventMeta` → `getSystemEventMeta`
- `getSecurityEventLabel` → `getSystemEventLabel`

Add operational event entries to the metadata record:

```typescript
import {
  CheckCircle,
  XCircle,
  Lock,
  ShieldOff,
  AlertTriangle,
  UserX,
  KeyRound,
  Cpu,
  FileWarning,
  KeySquare,
  type LucideIcon,
} from "lucide-react";
import { Badge } from "@/components/ui";
import type { SystemEventType, SystemEventTypeLike } from "@/types/api";

// ... keep existing EventMeta interface ...

export const SYSTEM_EVENT_META: Record<SystemEventType, EventMeta> = {
  // Security events (unchanged)
  login_success: { label: "Login success", variant: "success", icon: CheckCircle, severity: "info" },
  login_failed: { label: "Login failed", variant: "warning", icon: XCircle, severity: "notice" },
  login_locked: { label: "Login on locked account", variant: "warning", icon: Lock, severity: "notice" },
  login_blocked: { label: "Login blocked", variant: "warning", icon: ShieldOff, severity: "notice" },
  account_locked: { label: "Account locked", variant: "danger", icon: Lock, severity: "alert" },
  revoked_token_used: { label: "Revoked token used", variant: "danger", icon: AlertTriangle, severity: "alert" },
  disabled_account_token: { label: "Disabled account token", variant: "danger", icon: UserX, severity: "alert" },
  token_user_missing: { label: "Token for missing user", variant: "danger", icon: UserX, severity: "alert" },
  stale_token_version: { label: "Stale token version", variant: "warning", icon: KeyRound, severity: "notice" },
  // Operational events
  ra_circuit_breaker_tripped: { label: "RA Circuit Breaker", variant: "danger", icon: AlertTriangle, severity: "alert" },
  scraper_repeated_errors: { label: "Scraper Errors", variant: "warning", icon: Cpu, severity: "notice" },
  rom_file_missing: { label: "ROM Missing", variant: "warning", icon: FileWarning, severity: "notice" },
  api_credentials_invalid: { label: "Invalid Credentials", variant: "danger", icon: KeySquare, severity: "alert" },
};
```

Rename the component and exported functions:

```typescript
export function getSystemEventMeta(type: SystemEventTypeLike): EventMeta | undefined {
  return (SYSTEM_EVENT_META as Record<string, EventMeta>)[type];
}

export function getSystemEventLabel(type: SystemEventTypeLike): string {
  return getSystemEventMeta(type)?.label ?? type;
}

export function SystemEventBadge({ type }: { type: SystemEventTypeLike }) {
  const meta = getSystemEventMeta(type) ?? UNKNOWN_EVENT_META;
  const Icon = meta.icon;
  const label = getSystemEventLabel(type);
  return (
    <Badge variant={meta.variant}>
      <Icon aria-hidden="true" className="h-3 w-3 mr-1" />
      {label}
    </Badge>
  );
}
```

- [ ] **Step 3: Rename filters file**

```bash
cd web && git mv src/features/admin/components/security-events-filters.tsx src/features/admin/components/system-events-filters.tsx
```

- [ ] **Step 4: Update filters component**

In `system-events-filters.tsx`:

Update imports, rename types, add category filter. The key changes:

```typescript
import { Search, X } from "lucide-react";
import { Button, FilterChip, Input } from "@/components/ui";
import { SYSTEM_EVENT_META } from "./system-event-badge";
import type {
  SystemEventType,
  SystemEventCategoryCode,
  SystemEventTypeInfo,
} from "@/types/api";

export type SinceOption = "1h" | "24h" | "7d" | "30d" | "all";
export const DEFAULT_SYSTEM_EVENTS_SINCE: SinceOption = "24h";

// ... SINCE_LABELS and SINCE_OPTIONS unchanged ...

interface SystemEventsFiltersProps {
  eventTypes: SystemEventType[];
  category: SystemEventCategoryCode | null;
  username: string;
  ip: string;
  since: SinceOption;
  showDismissed: boolean;
  typeInfos: SystemEventTypeInfo[] | undefined;
  onEventTypesChange: (types: SystemEventType[]) => void;
  onCategoryChange: (category: SystemEventCategoryCode | null) => void;
  onUsernameChange: (v: string) => void;
  onIpChange: (v: string) => void;
  onSinceChange: (v: SinceOption) => void;
  onShowDismissedChange: (v: boolean) => void;
  onClear: () => void;
}
```

Add category filter chips at the top of the filter UI:

```tsx
{/* Category row */}
<div>
  <p className="mb-2 text-xs font-medium text-surface-500">Category</p>
  <div className="flex flex-wrap gap-2">
    <FilterChip
      label="All"
      isSelected={category === null}
      onClick={() => onCategoryChange(null)}
    />
    <FilterChip
      label="Security"
      isSelected={category === "security"}
      onClick={() => onCategoryChange("security")}
    />
    <FilterChip
      label="Operational"
      isSelected={category === "operational"}
      onClick={() => onCategoryChange("operational")}
    />
  </div>
</div>
```

Filter the event type chips to only show types matching the selected category:

```tsx
{/* Event type chips — filtered by selected category */}
<div>
  <p className="mb-2 text-xs font-medium text-surface-500">Event type</p>
  <div className="flex flex-wrap gap-2">
    {filteredEventTypes.map((t) => (
      <FilterChip
        key={t}
        label={SYSTEM_EVENT_META[t]?.label ?? t}
        isSelected={eventTypes.includes(t)}
        onClick={() => toggleEventType(t)}
      />
    ))}
  </div>
</div>
```

Where `filteredEventTypes` is computed from `typeInfos` filtered by the selected category:

```tsx
const filteredEventTypes = useMemo(() => {
  if (!typeInfos) return Object.keys(SYSTEM_EVENT_META) as SystemEventType[];
  const filtered = category
    ? typeInfos.filter((ti) => ti.category === category)
    : typeInfos;
  return filtered.map((ti) => ti.type as SystemEventType);
}, [typeInfos, category]);
```

Add dismissed toggle after the time range row:

```tsx
<label className="flex items-center gap-2 text-sm text-surface-400 cursor-pointer">
  <input
    type="checkbox"
    checked={showDismissed}
    onChange={(e) => onShowDismissedChange(e.target.checked)}
    className="rounded border-surface-600 bg-surface-800 text-brand-500 focus:ring-brand-500"
  />
  Show dismissed
</label>
```

Update `hasFilters` to include category and dismissed:

```tsx
const hasFilters =
  eventTypes.length > 0 ||
  category !== null ||
  username !== "" ||
  ip !== "" ||
  showDismissed ||
  since !== DEFAULT_SYSTEM_EVENTS_SINCE;
```

- [ ] **Step 5: Commit**

```bash
cd web && git add -A
git commit -m "refactor(web): rename badge and filters components to SystemEvent with category filter and dismiss toggle"
```

---

### Task 10: Update frontend table, modal, and page

**Files:**
- Rename: `web/src/features/admin/components/security-events-table.tsx` → `web/src/features/admin/components/system-events-table.tsx`
- Rename: `web/src/features/admin/components/security-event-detail-modal.tsx` → `web/src/features/admin/components/system-event-detail-modal.tsx`
- Rename: `web/src/pages/admin/security-events-page.tsx` → `web/src/pages/admin/system-events-page.tsx`

- [ ] **Step 1: Rename files**

```bash
cd web && git mv src/features/admin/components/security-events-table.tsx src/features/admin/components/system-events-table.tsx
git mv src/features/admin/components/security-event-detail-modal.tsx src/features/admin/components/system-event-detail-modal.tsx
git mv src/pages/admin/security-events-page.tsx src/pages/admin/system-events-page.tsx
```

- [ ] **Step 2: Update table component**

In `system-events-table.tsx`:

Update all imports and type references:
- `SecurityEvent` → `SystemEvent`
- `SecurityEventBadge` → `SystemEventBadge`
- `getSecurityEventMeta` → `getSystemEventMeta`
- `SecurityEventsTable` → `SystemEventsTable`

Add a dismiss button to each row. Accept `onDismiss` callback in props:

```typescript
interface SystemEventsTableProps {
  events: SystemEvent[] | undefined;
  isLoading: boolean;
  onRowClick: (event: SystemEvent) => void;
  onDismiss: (id: number) => void;
}
```

Add a dismiss button in the last column of each row:

```tsx
<td className="px-5 py-3 text-right">
  {!e.dismissedAt && (
    <button
      data-testid={`dismiss-event-${e.id}`}
      onClick={(ev) => {
        ev.stopPropagation();
        onDismiss(e.id);
      }}
      className="text-surface-500 hover:text-surface-300 transition-colors"
      title="Dismiss"
    >
      <X className="h-4 w-4" />
    </button>
  )}
  {e.dismissedAt && (
    <span className="text-xs text-surface-600">Dismissed</span>
  )}
</td>
```

Add a 6th column header for the dismiss action. Update the `colSpan` in the empty state to 6.

Update `summarizeDetails` to handle operational event metadata:

```typescript
function summarizeDetails(e: SystemEvent): string {
  if (e.reason) return humanizeReason(e.reason);
  if (e.metadata) {
    if (typeof e.metadata.failedCount === "number") {
      return `${e.metadata.failedCount} failed attempt${e.metadata.failedCount === 1 ? "" : "s"}`;
    }
    if (typeof e.metadata.lockedUntil === "string") {
      return `Locked until ${formatDateTime(e.metadata.lockedUntil)}`;
    }
    if (typeof e.metadata.consecutiveFailures === "number") {
      return `${e.metadata.consecutiveFailures} consecutive failures`;
    }
    if (typeof e.metadata.service === "string") {
      return `Service: ${e.metadata.service}`;
    }
    if (typeof e.metadata.gameTitle === "string") {
      return e.metadata.gameTitle as string;
    }
  }
  if (e.path) return e.path;
  return "";
}
```

Update empty state text from "No security events" to "No system events".

- [ ] **Step 3: Update detail modal**

In `system-event-detail-modal.tsx`:

Rename imports and type references:
- `SecurityEvent` → `SystemEvent`
- `SecurityEventBadge` → `SystemEventBadge`
- `getSecurityEventLabel` → `getSystemEventLabel`
- `SecurityEventDetailModal` → `SystemEventDetailModal`

Update fallback title from "Security event" to "System event".

Add category display in the detail grid:

```tsx
<DetailRow label="Category" value={event.categoryName} />
```

- [ ] **Step 4: Update page component**

In `system-events-page.tsx`:

Update all imports to use renamed components and hooks. Rename `AdminSecurityEventsPage` → `AdminSystemEventsPage`.

Add category and dismissed state from URL params:

```typescript
const category = (searchParams.get("category") as SystemEventCategoryCode) ?? null;
const showDismissed = searchParams.get("dismissed") === "true";
```

Fetch types for category filtering:

```typescript
const { data: typesData } = useSystemEventTypes();
```

Add dismiss mutation:

```typescript
const dismissMutation = useDismissSystemEvent();
```

Update page title and subtitle:

```tsx
<PageLayout
  title="System Events"
  subtitle="Audit log of system events. Security events track authentication activity; operational events surface infrastructure issues like scraper failures and missing ROMs."
>
```

Pass new props to filters:

```tsx
<SystemEventsFilters
  eventTypes={eventTypes}
  category={category}
  username={username}
  ip={ip}
  since={since}
  showDismissed={showDismissed}
  typeInfos={typesData?.types}
  onEventTypesChange={(t) => updateParams({ eventType: t })}
  onCategoryChange={(c) => updateParams({ category: c })}
  onUsernameChange={(v) => updateParams({ username: v })}
  onIpChange={(v) => updateParams({ ip: v })}
  onSinceChange={(v) => updateParams({ since: v === DEFAULT_SYSTEM_EVENTS_SINCE ? null : v })}
  onShowDismissedChange={(v) => updateParams({ dismissed: v ? "true" : null })}
  onClear={() =>
    updateParams({
      eventType: [],
      category: null,
      username: null,
      ip: null,
      since: null,
      dismissed: null,
    })
  }
/>
```

Pass `onDismiss` to the table:

```tsx
<SystemEventsTable
  events={data?.data}
  isLoading={isLoading}
  onRowClick={setDetailEvent}
  onDismiss={(id) => dismissMutation.mutate(id)}
/>
```

Add `category` and `dismissed` to the hook call:

```typescript
const { data, isLoading, isError, error, refetch } = useSystemEvents({
  page,
  pageSize: PAGE_SIZE,
  eventType: eventTypes,
  category: category || undefined,
  username: username || undefined,
  ip: ip || undefined,
  since,
  dismissed: showDismissed,
});
```

Update error text from "Failed to load security events" to "Failed to load system events".

- [ ] **Step 5: Update App.tsx route and import**

In `App.tsx`:
- Update import: `AdminSecurityEventsPage` → `AdminSystemEventsPage` from `"@/pages/admin/system-events-page"`
- Update route path: `"admin/security-events"` → `"admin/system-events"`

- [ ] **Step 6: Update app-layout.tsx nav link**

Change the nav link:
- `to: "/admin/security-events"` → `to: "/admin/system-events"`
- `label: "Security Events"` → `label: "System Events"`

- [ ] **Step 7: Verify TypeScript compiles**

Run: `cd web && npx tsc --noEmit 2>&1 | head -30`
Expected: Success (or minor issues to fix)

- [ ] **Step 8: Commit**

```bash
cd web && git add -A
git commit -m "refactor(web): rename page, table, modal to SystemEvent with category filter, dismiss, and operational badges"
```

---

### Task 11: Update frontend tests

**Files:**
- Rename: `web/e2e/admin-security-events.spec.ts` → `web/e2e/admin-system-events.spec.ts`
- Rename: `web/src/pages/admin/__tests__/security-events-page.test.tsx` → `web/src/pages/admin/__tests__/system-events-page.test.tsx`
- Rename: `web/src/features/admin/components/__tests__/security-event-badge.test.tsx` → `web/src/features/admin/components/__tests__/system-event-badge.test.tsx`

- [ ] **Step 1: Rename all test files**

```bash
cd web && git mv e2e/admin-security-events.spec.ts e2e/admin-system-events.spec.ts
git mv src/pages/admin/__tests__/security-events-page.test.tsx src/pages/admin/__tests__/system-events-page.test.tsx
git mv src/features/admin/components/__tests__/security-event-badge.test.tsx src/features/admin/components/__tests__/system-event-badge.test.tsx
```

- [ ] **Step 2: Update E2E test**

In `admin-system-events.spec.ts`, update all:
- Route mocks: `**/api/admin/security-events*` → `**/api/admin/system-events*`
- Navigation: `/admin/security-events` → `/admin/system-events`
- Heading text: `Security Events` → `System Events`
- Empty state text: `No security events` → `No system events`
- Nav link text: `Security Events` → `System Events`
- Test describe name: `Admin Security Events Page` → `Admin System Events Page`
- Add `categoryCode` and `categoryName` fields to mock data:

```typescript
body: JSON.stringify({
  data: [
    {
      id: 1,
      createdAt: "2026-04-10T09:00:00Z",
      categoryCode: "security",
      categoryName: "Security",
      eventType: "login_failed",
      // ...rest
    },
  ],
  // ...
}),
```

Update the modal heading assertion:
- `"Security event"` → `"System event"`

Add E2E test for category filter:

```typescript
test("category filter narrows visible events", async ({ page }) => {
  await page.route("**/api/admin/system-events/categories", (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify([
        { code: "security", name: "Security" },
        { code: "operational", name: "Operational" },
      ]),
    });
  });
  await page.route("**/api/admin/system-events/types", (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        types: [
          { type: "login_failed", category: "security" },
          { type: "ra_circuit_breaker_tripped", category: "operational" },
        ],
      }),
    });
  });
  await page.route("**/api/admin/system-events?*", (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ data: [], total: 0, page: 1, pageSize: 50 }),
    });
  });
  await page.route("**/api/admin/system-events", (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ data: [], total: 0, page: 1, pageSize: 50 }),
    });
  });

  await page.goto("/admin/system-events");
  await page.getByRole("button", { name: "Operational" }).click();
  await expect(page).toHaveURL(/category=operational/);
});
```

Add E2E test for dismiss:

```typescript
test("dismiss button removes event from view", async ({ page }) => {
  const eventData = {
    id: 1,
    createdAt: "2026-04-10T09:00:00Z",
    categoryCode: "security",
    categoryName: "Security",
    eventType: "login_failed",
    reason: "bad_password",
    username: "alice",
    ip: "10.0.0.1",
  };

  let dismissed = false;
  await page.route("**/api/admin/system-events*", (route) => {
    if (route.request().method() === "PUT") {
      dismissed = true;
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ dismissed: true }) });
      return;
    }
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: dismissed ? [] : [eventData],
        total: dismissed ? 0 : 1,
        page: 1,
        pageSize: 50,
      }),
    });
  });

  await page.goto("/admin/system-events");
  await expect(page.getByText("alice")).toBeVisible();
  await page.getByTestId("dismiss-event-1").click();
});
```

- [ ] **Step 3: Update unit test for page**

In `system-events-page.test.tsx`, update all references:
- Import: `AdminSecurityEventsPage` → `AdminSystemEventsPage`
- Mock path: `@/hooks/use-security-events` → `@/hooks/use-system-events`
- Hook names: `useSecurityEvents` → `useSystemEvents`, etc.
- Heading text: `Security Events` → `System Events`
- Empty state: `No security events` → `No system events`
- Error text: `Failed to load security events` → `Failed to load system events`
- Routes: `/admin/security-events` → `/admin/system-events`
- Test describe: `AdminSecurityEventsPage` → `AdminSystemEventsPage`
- data-testid: `security-events-error` → `system-events-error`
- data-testid: `security-event-pivot-actions` → `system-event-pivot-actions`
- Response types: `SecurityEventsListResponse` → `SystemEventsListResponse`
- Add `categoryCode` and `categoryName` to sample data

Add mock for `useSystemEventTypes`:

```typescript
vi.mock("@/hooks/use-system-events", () => ({
  useSystemEvents: vi.fn(),
  useSystemEvent: vi.fn(),
  useSystemEventTypes: vi.fn(() => ({ data: undefined })),
  useSystemEventCategories: vi.fn(() => ({ data: undefined })),
  useDismissSystemEvent: vi.fn(() => ({ mutate: vi.fn() })),
}));
```

- [ ] **Step 4: Update badge unit test**

In `system-event-badge.test.tsx`, rename:
- `SecurityEventBadge` → `SystemEventBadge`
- `getSecurityEventLabel` → `getSystemEventLabel`
- Import path updated to `"../system-event-badge"`

Add test for operational event badge:

```typescript
it("renders operational event types", () => {
  render(<SystemEventBadge type="ra_circuit_breaker_tripped" />);
  expect(screen.getByText("RA Circuit Breaker")).toBeInTheDocument();
});
```

- [ ] **Step 5: Run unit tests**

Run: `cd web && npx vitest run 2>&1 | tail -30`
Expected: All pass

- [ ] **Step 6: Commit**

```bash
cd web && git add -A
git commit -m "test(web): rename and update all frontend tests for SystemEvent rename"
```

---

### Task 12: Run full test suite and fix regressions

**Files:** None (verification only)

- [ ] **Step 1: Run Go tests**

Run: `cd server && go test ./... -count=1 2>&1 | tail -30`
Expected: All pass

- [ ] **Step 2: Run frontend unit tests**

Run: `cd web && npx vitest run 2>&1 | tail -30`
Expected: All pass

- [ ] **Step 3: Run frontend E2E tests**

Start the E2E environment:
```bash
docker compose -f docker-compose.e2e.yml up -d --build --wait
```

Then run:
```bash
cd web && npx playwright test e2e/admin-system-events.spec.ts 2>&1 | tail -30
```

Expected: All pass

- [ ] **Step 4: Fix any regressions**

Search for any remaining references to old names:
```bash
grep -r "SecurityEvent\|security-events\|security_events" server/internal/ web/src/ --include="*.go" --include="*.ts" --include="*.tsx" -l
```

Fix any files that still reference the old names.

- [ ] **Step 5: Run full E2E suite**

```bash
cd web && npx playwright test 2>&1 | tail -30
```

Expected: All pass — no regressions from the rename.

- [ ] **Step 6: Commit any fixes**

```bash
git add -A
git commit -m "fix: resolve remaining SecurityEvent references after rename"
```
