package api

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// challengeTestEnv holds test fixtures.
type challengeTestEnv struct {
	router    http.Handler
	token     string
	userID    uint
	gameID    uint
	consoleID uint

	// Secondary user for multi-user tests
	token2 string
	userID2 uint

	// Admin user
	adminToken string
	adminID    uint
}

func setupChallengeTest(t *testing.T) *challengeTestEnv {
	return setupChallengeTestWithRateLimit(t, 0) // No rate limiting in most tests
}

func setupChallengeTestWithRateLimit(t *testing.T, rateLimitSec int) *challengeTestEnv {
	t.Helper()
	database, cfg := setupTestEnv(t)
	cfg.ChallengeAttemptRateLimitSec = rateLimitSec
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	// Create user 1
	w := httptest.NewRecorder()
	body, _ := json.Marshal(map[string]string{
		"username": "challenger1",
		"email":    "challenger1@test.com",
		"password": "password123",
	})
	req := httptest.NewRequest("POST", "/api/auth/register", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code, "register user1: %s", w.Body.String())

	var reg1 map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &reg1)
	token1 := reg1["accessToken"].(string)

	claims1, err := auth.ValidateAccessToken(token1, testJWTSecret)
	require.NoError(t, err)

	// Create user 2
	token2 := createNonOwnerUser(t, router, token1, "challenger2", "challenger2@test.com", "password123")

	claims2, err := auth.ValidateAccessToken(token2, testJWTSecret)
	require.NoError(t, err)

	// Create admin user
	adminUser := db.User{
		Username:     "admin_user",
		Email:        "admin@test.com",
		PasswordHash: "$2a$10$dummy",
		Role:         "admin",
	}
	database.Create(&adminUser)
	adminToken, _ := auth.GenerateAccessToken(adminUser.ID, adminUser.Username, string(adminUser.Role), testJWTSecret)

	// Create a game with a console
	var console db.Console
	database.First(&console) // Get seeded console (NES)

	game := db.Game{
		ConsoleID: console.ID,
		Title:     "Test Game",
		FileName:  "test.nes",
		FilePath:  "/tmp/test.nes",
	}
	database.Create(&game)

	return &challengeTestEnv{
		router:     router,
		token:      token1,
		userID:     claims1.UserID,
		gameID:     game.ID,
		consoleID:  console.ID,
		token2:     token2,
		userID2:    claims2.UserID,
		adminToken: adminToken,
		adminID:    adminUser.ID,
	}
}

func (e *challengeTestEnv) createChallengeRequest(t *testing.T, name, gameID string, token string) *httptest.ResponseRecorder {
	t.Helper()

	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	writer.WriteField("name", name)
	writer.WriteField("gameId", gameID)
	writer.WriteField("type", "speedrun")
	writer.WriteField("difficulty", "medium")
	writer.WriteField("description", "Beat the level as fast as you can")
	writer.WriteField("coreName", "nestopia")

	part, _ := writer.CreateFormFile("save", "save.state")
	part.Write([]byte("fake-save-state-data-for-testing"))
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/challenges", &buf)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	req.Header.Set("Authorization", "Bearer "+token)
	e.router.ServeHTTP(w, req)
	return w
}

func TestCreateChallenge(t *testing.T) {
	env := setupChallengeTest(t)

	t.Run("success", func(t *testing.T) {
		w := env.createChallengeRequest(t, "Speed Run Level 1", fmt.Sprintf("%d", env.gameID), env.token)
		assert.Equal(t, http.StatusCreated, w.Code, w.Body.String())

		var resp ChallengeResponse
		err := json.Unmarshal(w.Body.Bytes(), &resp)
		require.NoError(t, err)
		assert.Equal(t, "Speed Run Level 1", resp.Name)
		assert.Equal(t, "speedrun", resp.Type)
		assert.Equal(t, "medium", resp.Difficulty)
		assert.Equal(t, "active", resp.Status)
		assert.Equal(t, "nestopia", resp.CoreName)
		assert.Equal(t, "challenger1", resp.CreatorUsername)
		assert.Equal(t, fmt.Sprintf("%d", env.gameID), resp.GameID)
		assert.Greater(t, resp.SaveFileSize, int64(0))
	})

	t.Run("missing name", func(t *testing.T) {
		var buf bytes.Buffer
		writer := multipart.NewWriter(&buf)
		writer.WriteField("gameId", fmt.Sprintf("%d", env.gameID))
		part, _ := writer.CreateFormFile("save", "save.state")
		part.Write([]byte("data"))
		writer.Close()

		w := httptest.NewRecorder()
		req := httptest.NewRequest("POST", "/api/challenges", &buf)
		req.Header.Set("Content-Type", writer.FormDataContentType())
		req.Header.Set("Authorization", "Bearer "+env.token)
		env.router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusBadRequest, w.Code)
	})

	t.Run("missing save file", func(t *testing.T) {
		var buf bytes.Buffer
		writer := multipart.NewWriter(&buf)
		writer.WriteField("name", "Test")
		writer.WriteField("gameId", fmt.Sprintf("%d", env.gameID))
		writer.Close()

		w := httptest.NewRecorder()
		req := httptest.NewRequest("POST", "/api/challenges", &buf)
		req.Header.Set("Content-Type", writer.FormDataContentType())
		req.Header.Set("Authorization", "Bearer "+env.token)
		env.router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusBadRequest, w.Code)
	})

	t.Run("invalid game ID", func(t *testing.T) {
		w := env.createChallengeRequest(t, "Test", "99999", env.token)
		assert.Equal(t, http.StatusNotFound, w.Code)
	})

	t.Run("invalid challenge type", func(t *testing.T) {
		var buf bytes.Buffer
		writer := multipart.NewWriter(&buf)
		writer.WriteField("name", "Test")
		writer.WriteField("gameId", fmt.Sprintf("%d", env.gameID))
		writer.WriteField("type", "invalid")
		part, _ := writer.CreateFormFile("save", "save.state")
		part.Write([]byte("data"))
		writer.Close()

		w := httptest.NewRecorder()
		req := httptest.NewRequest("POST", "/api/challenges", &buf)
		req.Header.Set("Content-Type", writer.FormDataContentType())
		req.Header.Set("Authorization", "Bearer "+env.token)
		env.router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusBadRequest, w.Code)
	})

	t.Run("invalid difficulty", func(t *testing.T) {
		var buf bytes.Buffer
		writer := multipart.NewWriter(&buf)
		writer.WriteField("name", "Test")
		writer.WriteField("gameId", fmt.Sprintf("%d", env.gameID))
		writer.WriteField("difficulty", "extreme")
		part, _ := writer.CreateFormFile("save", "save.state")
		part.Write([]byte("data"))
		writer.Close()

		w := httptest.NewRecorder()
		req := httptest.NewRequest("POST", "/api/challenges", &buf)
		req.Header.Set("Content-Type", writer.FormDataContentType())
		req.Header.Set("Authorization", "Bearer "+env.token)
		env.router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusBadRequest, w.Code)
	})

	t.Run("with expiration", func(t *testing.T) {
		future := time.Now().Add(24 * time.Hour).Format(time.RFC3339)
		var buf bytes.Buffer
		writer := multipart.NewWriter(&buf)
		writer.WriteField("name", "Timed Challenge")
		writer.WriteField("gameId", fmt.Sprintf("%d", env.gameID))
		writer.WriteField("expiresAt", future)
		part, _ := writer.CreateFormFile("save", "save.state")
		part.Write([]byte("data"))
		writer.Close()

		w := httptest.NewRecorder()
		req := httptest.NewRequest("POST", "/api/challenges", &buf)
		req.Header.Set("Content-Type", writer.FormDataContentType())
		req.Header.Set("Authorization", "Bearer "+env.token)
		env.router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusCreated, w.Code)

		var resp ChallengeResponse
		json.Unmarshal(w.Body.Bytes(), &resp)
		assert.NotNil(t, resp.ExpiresAt)
	})

	t.Run("expired expiresAt rejected", func(t *testing.T) {
		past := time.Now().Add(-1 * time.Hour).Format(time.RFC3339)
		var buf bytes.Buffer
		writer := multipart.NewWriter(&buf)
		writer.WriteField("name", "Bad Challenge")
		writer.WriteField("gameId", fmt.Sprintf("%d", env.gameID))
		writer.WriteField("expiresAt", past)
		part, _ := writer.CreateFormFile("save", "save.state")
		part.Write([]byte("data"))
		writer.Close()

		w := httptest.NewRecorder()
		req := httptest.NewRequest("POST", "/api/challenges", &buf)
		req.Header.Set("Content-Type", writer.FormDataContentType())
		req.Header.Set("Authorization", "Bearer "+env.token)
		env.router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusBadRequest, w.Code)
	})

	t.Run("unauthenticated", func(t *testing.T) {
		var buf bytes.Buffer
		writer := multipart.NewWriter(&buf)
		writer.WriteField("name", "Test")
		writer.WriteField("gameId", fmt.Sprintf("%d", env.gameID))
		part, _ := writer.CreateFormFile("save", "save.state")
		part.Write([]byte("data"))
		writer.Close()

		w := httptest.NewRecorder()
		req := httptest.NewRequest("POST", "/api/challenges", &buf)
		req.Header.Set("Content-Type", writer.FormDataContentType())
		env.router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusUnauthorized, w.Code)
	})
}

func TestListChallenges(t *testing.T) {
	env := setupChallengeTest(t)

	// Create a few challenges
	gameIDStr := fmt.Sprintf("%d", env.gameID)
	env.createChallengeRequest(t, "Challenge A", gameIDStr, env.token)
	env.createChallengeRequest(t, "Challenge B", gameIDStr, env.token)
	env.createChallengeRequest(t, "Challenge C", gameIDStr, env.token2)

	t.Run("list all active", func(t *testing.T) {
		w := httptest.NewRecorder()
		req := httptest.NewRequest("GET", "/api/challenges", nil)
		req.Header.Set("Authorization", "Bearer "+env.token)
		env.router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusOK, w.Code)

		var resp PaginatedResponse[json.RawMessage]
		json.Unmarshal(w.Body.Bytes(), &resp)
		assert.Equal(t, int64(3), resp.Total)
	})

	t.Run("filter by game", func(t *testing.T) {
		w := httptest.NewRecorder()
		req := httptest.NewRequest("GET", fmt.Sprintf("/api/challenges?gameId=%d", env.gameID), nil)
		req.Header.Set("Authorization", "Bearer "+env.token)
		env.router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusOK, w.Code)

		var resp PaginatedResponse[json.RawMessage]
		json.Unmarshal(w.Body.Bytes(), &resp)
		assert.Equal(t, int64(3), resp.Total)
	})

	t.Run("filter by creator", func(t *testing.T) {
		w := httptest.NewRecorder()
		req := httptest.NewRequest("GET", fmt.Sprintf("/api/challenges?creatorId=%d", env.userID), nil)
		req.Header.Set("Authorization", "Bearer "+env.token)
		env.router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusOK, w.Code)

		var resp PaginatedResponse[json.RawMessage]
		json.Unmarshal(w.Body.Bytes(), &resp)
		assert.Equal(t, int64(2), resp.Total)
	})

	t.Run("sort by popular", func(t *testing.T) {
		w := httptest.NewRecorder()
		req := httptest.NewRequest("GET", "/api/challenges?sort=popular", nil)
		req.Header.Set("Authorization", "Bearer "+env.token)
		env.router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusOK, w.Code)
	})
}

func TestGetChallenge(t *testing.T) {
	env := setupChallengeTest(t)

	w := env.createChallengeRequest(t, "Test Challenge", fmt.Sprintf("%d", env.gameID), env.token)
	require.Equal(t, http.StatusCreated, w.Code)

	var created ChallengeResponse
	json.Unmarshal(w.Body.Bytes(), &created)

	t.Run("success", func(t *testing.T) {
		w := httptest.NewRecorder()
		req := httptest.NewRequest("GET", "/api/challenges/"+created.ID, nil)
		req.Header.Set("Authorization", "Bearer "+env.token)
		env.router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusOK, w.Code)

		var resp ChallengeResponse
		json.Unmarshal(w.Body.Bytes(), &resp)
		assert.Equal(t, created.ID, resp.ID)
		assert.Equal(t, "Test Challenge", resp.Name)
	})

	t.Run("not found", func(t *testing.T) {
		w := httptest.NewRecorder()
		req := httptest.NewRequest("GET", "/api/challenges/99999", nil)
		req.Header.Set("Authorization", "Bearer "+env.token)
		env.router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusNotFound, w.Code)
	})
}

func TestUpdateChallenge(t *testing.T) {
	env := setupChallengeTest(t)

	w := env.createChallengeRequest(t, "Original Name", fmt.Sprintf("%d", env.gameID), env.token)
	require.Equal(t, http.StatusCreated, w.Code)

	var created ChallengeResponse
	json.Unmarshal(w.Body.Bytes(), &created)

	t.Run("creator can update", func(t *testing.T) {
		body, _ := json.Marshal(map[string]string{
			"name":        "Updated Name",
			"description": "New description",
		})
		w := httptest.NewRecorder()
		req := httptest.NewRequest("PUT", "/api/challenges/"+created.ID, bytes.NewReader(body))
		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("Authorization", "Bearer "+env.token)
		env.router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusOK, w.Code)

		var resp ChallengeResponse
		json.Unmarshal(w.Body.Bytes(), &resp)
		assert.Equal(t, "Updated Name", resp.Name)
		assert.Equal(t, "New description", resp.Description)
	})

	t.Run("non-creator cannot update", func(t *testing.T) {
		body, _ := json.Marshal(map[string]string{"name": "Hacked"})
		w := httptest.NewRecorder()
		req := httptest.NewRequest("PUT", "/api/challenges/"+created.ID, bytes.NewReader(body))
		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("Authorization", "Bearer "+env.token2)
		env.router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusForbidden, w.Code)
	})

	t.Run("admin can update", func(t *testing.T) {
		body, _ := json.Marshal(map[string]string{"status": "closed"})
		w := httptest.NewRecorder()
		req := httptest.NewRequest("PUT", "/api/challenges/"+created.ID, bytes.NewReader(body))
		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("Authorization", "Bearer "+env.adminToken)
		env.router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusOK, w.Code)

		var resp ChallengeResponse
		json.Unmarshal(w.Body.Bytes(), &resp)
		assert.Equal(t, "closed", resp.Status)
	})
}

func TestDeleteChallenge(t *testing.T) {
	env := setupChallengeTest(t)

	t.Run("creator can delete", func(t *testing.T) {
		w := env.createChallengeRequest(t, "To Delete", fmt.Sprintf("%d", env.gameID), env.token)
		require.Equal(t, http.StatusCreated, w.Code)
		var created ChallengeResponse
		json.Unmarshal(w.Body.Bytes(), &created)

		w = httptest.NewRecorder()
		req := httptest.NewRequest("DELETE", "/api/challenges/"+created.ID, nil)
		req.Header.Set("Authorization", "Bearer "+env.token)
		env.router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusOK, w.Code)

		// Verify it's gone
		w = httptest.NewRecorder()
		req = httptest.NewRequest("GET", "/api/challenges/"+created.ID, nil)
		req.Header.Set("Authorization", "Bearer "+env.token)
		env.router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusNotFound, w.Code)
	})

	t.Run("non-creator cannot delete", func(t *testing.T) {
		w := env.createChallengeRequest(t, "Protected", fmt.Sprintf("%d", env.gameID), env.token)
		require.Equal(t, http.StatusCreated, w.Code)
		var created ChallengeResponse
		json.Unmarshal(w.Body.Bytes(), &created)

		w = httptest.NewRecorder()
		req := httptest.NewRequest("DELETE", "/api/challenges/"+created.ID, nil)
		req.Header.Set("Authorization", "Bearer "+env.token2)
		env.router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusForbidden, w.Code)
	})

	t.Run("admin can delete", func(t *testing.T) {
		w := env.createChallengeRequest(t, "Admin Delete", fmt.Sprintf("%d", env.gameID), env.token)
		require.Equal(t, http.StatusCreated, w.Code)
		var created ChallengeResponse
		json.Unmarshal(w.Body.Bytes(), &created)

		w = httptest.NewRecorder()
		req := httptest.NewRequest("DELETE", "/api/challenges/"+created.ID, nil)
		req.Header.Set("Authorization", "Bearer "+env.adminToken)
		env.router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusOK, w.Code)
	})
}

func TestDownloadChallengeSave(t *testing.T) {
	env := setupChallengeTest(t)

	w := env.createChallengeRequest(t, "Download Test", fmt.Sprintf("%d", env.gameID), env.token)
	require.Equal(t, http.StatusCreated, w.Code)
	var created ChallengeResponse
	json.Unmarshal(w.Body.Bytes(), &created)

	t.Run("success", func(t *testing.T) {
		w := httptest.NewRecorder()
		req := httptest.NewRequest("GET", "/api/challenges/"+created.ID+"/save/download", nil)
		req.Header.Set("Authorization", "Bearer "+env.token)
		env.router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusOK, w.Code)
		assert.Contains(t, w.Header().Get("Content-Disposition"), "attachment")
		assert.Equal(t, "fake-save-state-data-for-testing", w.Body.String())
	})

	t.Run("not found", func(t *testing.T) {
		w := httptest.NewRecorder()
		req := httptest.NewRequest("GET", "/api/challenges/99999/save/download", nil)
		req.Header.Set("Authorization", "Bearer "+env.token)
		env.router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusNotFound, w.Code)
	})
}

func TestAttemptLifecycle(t *testing.T) {
	env := setupChallengeTest(t)

	// Create challenge
	w := env.createChallengeRequest(t, "Lifecycle Test", fmt.Sprintf("%d", env.gameID), env.token)
	require.Equal(t, http.StatusCreated, w.Code)
	var challenge ChallengeResponse
	json.Unmarshal(w.Body.Bytes(), &challenge)

	t.Run("start attempt", func(t *testing.T) {
		w := httptest.NewRecorder()
		req := httptest.NewRequest("POST", "/api/challenges/"+challenge.ID+"/attempts/start", nil)
		req.Header.Set("Authorization", "Bearer "+env.token2)
		env.router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusCreated, w.Code)

		var resp ChallengeAttemptResponse
		json.Unmarshal(w.Body.Bytes(), &resp)
		assert.Equal(t, "in_progress", resp.Status)
		assert.Equal(t, challenge.ID, resp.ChallengeID)
		assert.False(t, resp.StartedAt.IsZero())
	})

	t.Run("complete attempt", func(t *testing.T) {
		// Start a new attempt (previous one gets auto-abandoned)
		time.Sleep(35 * time.Millisecond) // Small delay for timing

		w := httptest.NewRecorder()
		req := httptest.NewRequest("POST", "/api/challenges/"+challenge.ID+"/attempts/start", nil)
		req.Header.Set("Authorization", "Bearer "+env.token2)
		env.router.ServeHTTP(w, req)
		require.Equal(t, http.StatusCreated, w.Code)

		var attempt ChallengeAttemptResponse
		json.Unmarshal(w.Body.Bytes(), &attempt)

		// Complete it
		time.Sleep(10 * time.Millisecond) // Simulate some play time
		w = httptest.NewRecorder()
		req = httptest.NewRequest("POST", fmt.Sprintf("/api/challenges/%s/attempts/%s/complete", challenge.ID, attempt.ID), nil)
		req.Header.Set("Authorization", "Bearer "+env.token2)
		env.router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusOK, w.Code)

		var completed ChallengeAttemptResponse
		json.Unmarshal(w.Body.Bytes(), &completed)
		assert.Equal(t, "completed", completed.Status)
		assert.NotNil(t, completed.CompletedAt)
		assert.Greater(t, completed.DurationMs, int64(0))
		assert.True(t, completed.IsBest) // First completion = best
	})

	t.Run("abandon attempt", func(t *testing.T) {
		// Start an attempt
		w := httptest.NewRecorder()
		req := httptest.NewRequest("POST", "/api/challenges/"+challenge.ID+"/attempts/start", nil)
		req.Header.Set("Authorization", "Bearer "+env.token2)
		env.router.ServeHTTP(w, req)
		require.Equal(t, http.StatusCreated, w.Code)

		var attempt ChallengeAttemptResponse
		json.Unmarshal(w.Body.Bytes(), &attempt)

		// Abandon it
		w = httptest.NewRecorder()
		req = httptest.NewRequest("POST", fmt.Sprintf("/api/challenges/%s/attempts/%s/abandon", challenge.ID, attempt.ID), nil)
		req.Header.Set("Authorization", "Bearer "+env.token2)
		env.router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusOK, w.Code)

		var abandoned ChallengeAttemptResponse
		json.Unmarshal(w.Body.Bytes(), &abandoned)
		assert.Equal(t, "abandoned", abandoned.Status)
	})

	t.Run("cannot complete others attempt", func(t *testing.T) {
		// User2 starts an attempt
		w := httptest.NewRecorder()
		req := httptest.NewRequest("POST", "/api/challenges/"+challenge.ID+"/attempts/start", nil)
		req.Header.Set("Authorization", "Bearer "+env.token2)
		env.router.ServeHTTP(w, req)
		require.Equal(t, http.StatusCreated, w.Code)

		var attempt ChallengeAttemptResponse
		json.Unmarshal(w.Body.Bytes(), &attempt)

		// User1 tries to complete it
		w = httptest.NewRecorder()
		req = httptest.NewRequest("POST", fmt.Sprintf("/api/challenges/%s/attempts/%s/complete", challenge.ID, attempt.ID), nil)
		req.Header.Set("Authorization", "Bearer "+env.token)
		env.router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusForbidden, w.Code)
	})

	t.Run("cannot complete already completed attempt", func(t *testing.T) {
		// Start
		w := httptest.NewRecorder()
		req := httptest.NewRequest("POST", "/api/challenges/"+challenge.ID+"/attempts/start", nil)
		req.Header.Set("Authorization", "Bearer "+env.token2)
		env.router.ServeHTTP(w, req)
		require.Equal(t, http.StatusCreated, w.Code)

		var attempt ChallengeAttemptResponse
		json.Unmarshal(w.Body.Bytes(), &attempt)

		// Complete
		w = httptest.NewRecorder()
		req = httptest.NewRequest("POST", fmt.Sprintf("/api/challenges/%s/attempts/%s/complete", challenge.ID, attempt.ID), nil)
		req.Header.Set("Authorization", "Bearer "+env.token2)
		env.router.ServeHTTP(w, req)
		require.Equal(t, http.StatusOK, w.Code)

		// Try to complete again
		w = httptest.NewRecorder()
		req = httptest.NewRequest("POST", fmt.Sprintf("/api/challenges/%s/attempts/%s/complete", challenge.ID, attempt.ID), nil)
		req.Header.Set("Authorization", "Bearer "+env.token2)
		env.router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusBadRequest, w.Code)
	})

	t.Run("cannot start attempt on inactive challenge", func(t *testing.T) {
		// Close the challenge
		body, _ := json.Marshal(map[string]string{"status": "closed"})
		w := httptest.NewRecorder()
		req := httptest.NewRequest("PUT", "/api/challenges/"+challenge.ID, bytes.NewReader(body))
		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("Authorization", "Bearer "+env.token)
		env.router.ServeHTTP(w, req)
		require.Equal(t, http.StatusOK, w.Code)

		// Try to start attempt
		w = httptest.NewRecorder()
		req = httptest.NewRequest("POST", "/api/challenges/"+challenge.ID+"/attempts/start", nil)
		req.Header.Set("Authorization", "Bearer "+env.token2)
		env.router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusBadRequest, w.Code)
	})
}

func TestGetMyAttempts(t *testing.T) {
	env := setupChallengeTest(t)

	w := env.createChallengeRequest(t, "My Attempts Test", fmt.Sprintf("%d", env.gameID), env.token)
	require.Equal(t, http.StatusCreated, w.Code)
	var challenge ChallengeResponse
	json.Unmarshal(w.Body.Bytes(), &challenge)

	// Create two attempts for user2
	for i := 0; i < 2; i++ {
		w = httptest.NewRecorder()
		req := httptest.NewRequest("POST", "/api/challenges/"+challenge.ID+"/attempts/start", nil)
		req.Header.Set("Authorization", "Bearer "+env.token2)
		env.router.ServeHTTP(w, req)
		require.Equal(t, http.StatusCreated, w.Code)
	}

	w = httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/challenges/"+challenge.ID+"/attempts/mine", nil)
	req.Header.Set("Authorization", "Bearer "+env.token2)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var attempts []ChallengeAttemptResponse
	json.Unmarshal(w.Body.Bytes(), &attempts)
	assert.GreaterOrEqual(t, len(attempts), 2)
}

func TestLeaderboard(t *testing.T) {
	env := setupChallengeTest(t)

	w := env.createChallengeRequest(t, "Leaderboard Test", fmt.Sprintf("%d", env.gameID), env.token)
	require.Equal(t, http.StatusCreated, w.Code)
	var challenge ChallengeResponse
	json.Unmarshal(w.Body.Bytes(), &challenge)

	// User1 completes with longer time
	w = httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/challenges/"+challenge.ID+"/attempts/start", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)
	var attempt1 ChallengeAttemptResponse
	json.Unmarshal(w.Body.Bytes(), &attempt1)

	time.Sleep(50 * time.Millisecond)
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", fmt.Sprintf("/api/challenges/%s/attempts/%s/complete", challenge.ID, attempt1.ID), nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// User2 completes with shorter time
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/challenges/"+challenge.ID+"/attempts/start", nil)
	req.Header.Set("Authorization", "Bearer "+env.token2)
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)
	var attempt2 ChallengeAttemptResponse
	json.Unmarshal(w.Body.Bytes(), &attempt2)

	time.Sleep(10 * time.Millisecond)
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", fmt.Sprintf("/api/challenges/%s/attempts/%s/complete", challenge.ID, attempt2.ID), nil)
	req.Header.Set("Authorization", "Bearer "+env.token2)
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Get leaderboard
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/challenges/"+challenge.ID+"/leaderboard", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp PaginatedResponse[json.RawMessage]
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, int64(2), resp.Total)

	// Verify ranking (faster user should be rank 1)
	dataBytes, _ := json.Marshal(resp.Data)
	var entries []ChallengeLeaderboardEntry
	json.Unmarshal(dataBytes, &entries)
	require.Len(t, entries, 2)
	assert.Equal(t, 1, entries[0].Rank)
	assert.Equal(t, 2, entries[1].Rank)
	assert.LessOrEqual(t, entries[0].DurationMs, entries[1].DurationMs)
}

func TestLeaderboardBestAttemptOnly(t *testing.T) {
	env := setupChallengeTest(t)

	w := env.createChallengeRequest(t, "Best Only Test", fmt.Sprintf("%d", env.gameID), env.token)
	require.Equal(t, http.StatusCreated, w.Code)
	var challenge ChallengeResponse
	json.Unmarshal(w.Body.Bytes(), &challenge)

	// User2 completes twice - second should be faster (shorter sleep)
	// Attempt 1 (slower)
	w = httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/challenges/"+challenge.ID+"/attempts/start", nil)
	req.Header.Set("Authorization", "Bearer "+env.token2)
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)
	var a1 ChallengeAttemptResponse
	json.Unmarshal(w.Body.Bytes(), &a1)

	time.Sleep(100 * time.Millisecond)
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", fmt.Sprintf("/api/challenges/%s/attempts/%s/complete", challenge.ID, a1.ID), nil)
	req.Header.Set("Authorization", "Bearer "+env.token2)
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Attempt 2 (faster)
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/challenges/"+challenge.ID+"/attempts/start", nil)
	req.Header.Set("Authorization", "Bearer "+env.token2)
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)
	var a2 ChallengeAttemptResponse
	json.Unmarshal(w.Body.Bytes(), &a2)

	time.Sleep(10 * time.Millisecond)
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", fmt.Sprintf("/api/challenges/%s/attempts/%s/complete", challenge.ID, a2.ID), nil)
	req.Header.Set("Authorization", "Bearer "+env.token2)
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var completed ChallengeAttemptResponse
	json.Unmarshal(w.Body.Bytes(), &completed)
	assert.True(t, completed.IsBest, "faster attempt should be marked as best")

	// Leaderboard should only show 1 entry for this user
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/challenges/"+challenge.ID+"/leaderboard", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp PaginatedResponse[json.RawMessage]
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, int64(1), resp.Total, "leaderboard should show only best attempt per user")
}

func TestLazyExpiration(t *testing.T) {
	env := setupChallengeTest(t)

	// Create a challenge normally first, then manually set it to expired via the DB
	w := env.createChallengeRequest(t, "Will Expire", fmt.Sprintf("%d", env.gameID), env.token)
	require.Equal(t, http.StatusCreated, w.Code)
	var created ChallengeResponse
	json.Unmarshal(w.Body.Bytes(), &created)

	// Get the DB from the env's router config — we need to update ExpiresAt directly
	// We can access the DB through the challenge handler's DB field
	// Instead, use the fact that setupTestEnv returns the DB
	// Re-approach: create a test that uses the returned DB properly
	t.Run("expired challenge shown as expired", func(t *testing.T) {
		database, cfg := setupTestEnv(t)
		router, cleanup := NewRouter(*cfg)
	defer cleanup()

		// Register a user
		body, _ := json.Marshal(map[string]string{
			"username": "expuser",
			"email":    "expuser@test.com",
			"password": "password123",
		})
		rw := httptest.NewRecorder()
		req := httptest.NewRequest("POST", "/api/auth/register", bytes.NewReader(body))
		req.Header.Set("Content-Type", "application/json")
		router.ServeHTTP(rw, req)
		require.Equal(t, http.StatusCreated, rw.Code)

		var reg map[string]interface{}
		json.Unmarshal(rw.Body.Bytes(), &reg)
		token := reg["accessToken"].(string)

		claims, _ := auth.ValidateAccessToken(token, testJWTSecret)

		// Create a game
		var console db.Console
		database.First(&console)
		game := db.Game{ConsoleID: console.ID, Title: "Exp Game", FileName: "exp.nes", FilePath: "/tmp/exp.nes"}
		database.Create(&game)

		// Create challenge directly with expired date
		past := time.Now().Add(-1 * time.Hour)
		ch := db.Challenge{
			CreatorID:    claims.UserID,
			GameID:       game.ID,
			Name:         "Expired Challenge",
			Type:         "completion",
			Difficulty:   "easy",
			Status:       "active",
			SaveFilePath: "/tmp/fake",
			ExpiresAt:    &past,
		}
		database.Create(&ch)

		// Fetch via API - lazy expiration should trigger
		rw = httptest.NewRecorder()
		req = httptest.NewRequest("GET", fmt.Sprintf("/api/challenges/%d", ch.ID), nil)
		req.Header.Set("Authorization", "Bearer "+token)
		router.ServeHTTP(rw, req)
		assert.Equal(t, http.StatusOK, rw.Code)

		var resp ChallengeResponse
		json.Unmarshal(rw.Body.Bytes(), &resp)
		assert.Equal(t, "expired", resp.Status)

		// Verify DB was updated
		database.First(&ch, ch.ID)
		assert.Equal(t, "expired", ch.Status)
	})
}

func TestRateLimitAttemptStart(t *testing.T) {
	env := setupChallengeTestWithRateLimit(t, 30)

	w := env.createChallengeRequest(t, "Rate Limit Test", fmt.Sprintf("%d", env.gameID), env.token)
	require.Equal(t, http.StatusCreated, w.Code)
	var challenge ChallengeResponse
	json.Unmarshal(w.Body.Bytes(), &challenge)

	// First attempt should succeed
	w = httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/challenges/"+challenge.ID+"/attempts/start", nil)
	req.Header.Set("Authorization", "Bearer "+env.token2)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusCreated, w.Code)

	// Second attempt within 30s should fail
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/challenges/"+challenge.ID+"/attempts/start", nil)
	req.Header.Set("Authorization", "Bearer "+env.token2)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusTooManyRequests, w.Code)
}

func TestListGameChallenges(t *testing.T) {
	env := setupChallengeTest(t)

	gameIDStr := fmt.Sprintf("%d", env.gameID)
	env.createChallengeRequest(t, "Game Challenge 1", gameIDStr, env.token)
	env.createChallengeRequest(t, "Game Challenge 2", gameIDStr, env.token2)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/challenges", env.gameID), nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp PaginatedResponse[json.RawMessage]
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, int64(2), resp.Total)
}

func TestListMyChallenges(t *testing.T) {
	env := setupChallengeTest(t)

	gameIDStr := fmt.Sprintf("%d", env.gameID)
	env.createChallengeRequest(t, "My Challenge 1", gameIDStr, env.token)
	env.createChallengeRequest(t, "My Challenge 2", gameIDStr, env.token)
	env.createChallengeRequest(t, "Other's Challenge", gameIDStr, env.token2)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/challenges", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp PaginatedResponse[json.RawMessage]
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, int64(2), resp.Total)
}

func TestFileSizeLimit(t *testing.T) {
	env := setupChallengeTest(t)

	// Create a save file that exceeds 10MB
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	writer.WriteField("name", "Too Large")
	writer.WriteField("gameId", fmt.Sprintf("%d", env.gameID))

	part, _ := writer.CreateFormFile("save", "huge.state")
	// Write 10MB + 1 byte
	data := make([]byte, maxChallengeSaveSize+1)
	io.Copy(part, bytes.NewReader(data))
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/challenges", &buf)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
	assert.Contains(t, w.Body.String(), "too large")
}

func TestServerSideTiming(t *testing.T) {
	env := setupChallengeTest(t)

	w := env.createChallengeRequest(t, "Timing Test", fmt.Sprintf("%d", env.gameID), env.token)
	require.Equal(t, http.StatusCreated, w.Code)
	var challenge ChallengeResponse
	json.Unmarshal(w.Body.Bytes(), &challenge)

	// Start attempt
	beforeStart := time.Now()
	w = httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/challenges/"+challenge.ID+"/attempts/start", nil)
	req.Header.Set("Authorization", "Bearer "+env.token2)
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)
	afterStart := time.Now()

	var attempt ChallengeAttemptResponse
	json.Unmarshal(w.Body.Bytes(), &attempt)

	// Verify StartedAt is server-set (between our before/after markers)
	assert.True(t, attempt.StartedAt.After(beforeStart.Add(-time.Second)) || attempt.StartedAt.Equal(beforeStart))
	assert.True(t, attempt.StartedAt.Before(afterStart.Add(time.Second)))

	// Wait measurable time
	sleepDuration := 100 * time.Millisecond
	time.Sleep(sleepDuration)

	// Complete
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", fmt.Sprintf("/api/challenges/%s/attempts/%s/complete", challenge.ID, attempt.ID), nil)
	req.Header.Set("Authorization", "Bearer "+env.token2)
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var completed ChallengeAttemptResponse
	json.Unmarshal(w.Body.Bytes(), &completed)

	// DurationMs should reflect server-side computation
	assert.Greater(t, completed.DurationMs, int64(50)) // At least 50ms (generous margin for CI)
	assert.Equal(t, "completed", completed.Status)
	assert.NotNil(t, completed.CompletedAt)

	// Duration should roughly match elapsed time
	assert.InDelta(t, float64(sleepDuration.Milliseconds()), float64(completed.DurationMs), 200, // 200ms tolerance
		"server-computed duration should roughly match actual elapsed time")
}

// TestDeleteChallengeDeletesAttempts verifies cascade delete behavior.
func TestDeleteChallengeDeletesAttempts(t *testing.T) {
	env := setupChallengeTest(t)

	w := env.createChallengeRequest(t, "Cascade Delete", fmt.Sprintf("%d", env.gameID), env.token)
	require.Equal(t, http.StatusCreated, w.Code)
	var challenge ChallengeResponse
	json.Unmarshal(w.Body.Bytes(), &challenge)

	// Start an attempt
	w = httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/challenges/"+challenge.ID+"/attempts/start", nil)
	req.Header.Set("Authorization", "Bearer "+env.token2)
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	// Delete challenge
	w = httptest.NewRecorder()
	req = httptest.NewRequest("DELETE", "/api/challenges/"+challenge.ID, nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Verify attempts are gone too (get my attempts should return empty or 404)
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/challenges/"+challenge.ID+"/attempts/mine", nil)
	req.Header.Set("Authorization", "Bearer "+env.token2)
	env.router.ServeHTTP(w, req)
	// Challenge is deleted, so attempts query will return empty
	if w.Code == http.StatusOK {
		var attempts []ChallengeAttemptResponse
		json.Unmarshal(w.Body.Bytes(), &attempts)
		assert.Empty(t, attempts)
	}
}

func (e *challengeTestEnv) createSurvivalChallengeRequest(t *testing.T, name, gameID string, token string) *httptest.ResponseRecorder {
	t.Helper()

	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	writer.WriteField("name", name)
	writer.WriteField("gameId", gameID)
	writer.WriteField("type", "survival")
	writer.WriteField("difficulty", "medium")
	writer.WriteField("description", "Survive as long as you can")
	writer.WriteField("coreName", "nestopia")

	part, _ := writer.CreateFormFile("save", "save.state")
	part.Write([]byte("fake-save-state-data-for-testing"))
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/challenges", &buf)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	req.Header.Set("Authorization", "Bearer "+token)
	e.router.ServeHTTP(w, req)
	return w
}

func TestSurvivalLeaderboard(t *testing.T) {
	env := setupChallengeTest(t)

	w := env.createSurvivalChallengeRequest(t, "Survival Test", fmt.Sprintf("%d", env.gameID), env.token)
	require.Equal(t, http.StatusCreated, w.Code, w.Body.String())
	var challenge ChallengeResponse
	json.Unmarshal(w.Body.Bytes(), &challenge)
	assert.Equal(t, "survival", challenge.Type)

	// User1 completes with shorter time (worse for survival)
	w = httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/challenges/"+challenge.ID+"/attempts/start", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)
	var attempt1 ChallengeAttemptResponse
	json.Unmarshal(w.Body.Bytes(), &attempt1)

	time.Sleep(10 * time.Millisecond)
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", fmt.Sprintf("/api/challenges/%s/attempts/%s/complete", challenge.ID, attempt1.ID), nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)
	var completed1 ChallengeAttemptResponse
	json.Unmarshal(w.Body.Bytes(), &completed1)

	// User2 completes with longer time (better for survival)
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/challenges/"+challenge.ID+"/attempts/start", nil)
	req.Header.Set("Authorization", "Bearer "+env.token2)
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)
	var attempt2 ChallengeAttemptResponse
	json.Unmarshal(w.Body.Bytes(), &attempt2)

	time.Sleep(50 * time.Millisecond)
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", fmt.Sprintf("/api/challenges/%s/attempts/%s/complete", challenge.ID, attempt2.ID), nil)
	req.Header.Set("Authorization", "Bearer "+env.token2)
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)
	var completed2 ChallengeAttemptResponse
	json.Unmarshal(w.Body.Bytes(), &completed2)

	// Verify user2 (longer time) has higher duration
	assert.Greater(t, completed2.DurationMs, completed1.DurationMs,
		"user2 should have a longer duration than user1")

	// Get leaderboard — for survival, longest time should be rank 1
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/challenges/"+challenge.ID+"/leaderboard", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp PaginatedResponse[json.RawMessage]
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, int64(2), resp.Total)

	// Verify ranking (longest time should be rank 1 for survival)
	dataBytes, _ := json.Marshal(resp.Data)
	var entries []ChallengeLeaderboardEntry
	json.Unmarshal(dataBytes, &entries)
	require.Len(t, entries, 2)
	assert.Equal(t, 1, entries[0].Rank)
	assert.Equal(t, 2, entries[1].Rank)
	assert.GreaterOrEqual(t, entries[0].DurationMs, entries[1].DurationMs,
		"survival leaderboard should rank longest time first")
}

func TestSurvivalBestAttempt(t *testing.T) {
	env := setupChallengeTest(t)

	w := env.createSurvivalChallengeRequest(t, "Survival Best Test", fmt.Sprintf("%d", env.gameID), env.token)
	require.Equal(t, http.StatusCreated, w.Code)
	var challenge ChallengeResponse
	json.Unmarshal(w.Body.Bytes(), &challenge)

	// User2 completes with shorter time first
	w = httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/challenges/"+challenge.ID+"/attempts/start", nil)
	req.Header.Set("Authorization", "Bearer "+env.token2)
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)
	var a1 ChallengeAttemptResponse
	json.Unmarshal(w.Body.Bytes(), &a1)

	time.Sleep(10 * time.Millisecond)
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", fmt.Sprintf("/api/challenges/%s/attempts/%s/complete", challenge.ID, a1.ID), nil)
	req.Header.Set("Authorization", "Bearer "+env.token2)
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)
	var c1 ChallengeAttemptResponse
	json.Unmarshal(w.Body.Bytes(), &c1)
	assert.True(t, c1.IsBest, "first attempt should be marked as best")

	// User2 completes with longer time (better for survival — should replace best)
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/challenges/"+challenge.ID+"/attempts/start", nil)
	req.Header.Set("Authorization", "Bearer "+env.token2)
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)
	var a2 ChallengeAttemptResponse
	json.Unmarshal(w.Body.Bytes(), &a2)

	time.Sleep(100 * time.Millisecond)
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", fmt.Sprintf("/api/challenges/%s/attempts/%s/complete", challenge.ID, a2.ID), nil)
	req.Header.Set("Authorization", "Bearer "+env.token2)
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)
	var c2 ChallengeAttemptResponse
	json.Unmarshal(w.Body.Bytes(), &c2)
	assert.True(t, c2.IsBest, "longer survival attempt should be new best")
	assert.Greater(t, c2.DurationMs, c1.DurationMs, "second attempt should be longer")

	// Leaderboard should show 1 entry (best only)
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/challenges/"+challenge.ID+"/leaderboard", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp PaginatedResponse[json.RawMessage]
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, int64(1), resp.Total, "leaderboard should show only best attempt per user")
}

func TestNameLengthValidation(t *testing.T) {
	env := setupChallengeTest(t)

	longName := strings.Repeat("a", 256)
	w := env.createChallengeRequestWithName(t, longName, fmt.Sprintf("%d", env.gameID), env.token)
	assert.Equal(t, http.StatusBadRequest, w.Code)
	assert.Contains(t, w.Body.String(), "255 characters")
}

func (e *challengeTestEnv) createChallengeRequestWithName(t *testing.T, name, gameID string, token string) *httptest.ResponseRecorder {
	t.Helper()

	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	writer.WriteField("name", name)
	writer.WriteField("gameId", gameID)
	part, _ := writer.CreateFormFile("save", "save.state")
	part.Write([]byte("fake-save-state-data-for-testing"))
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/challenges", &buf)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	req.Header.Set("Authorization", "Bearer "+token)
	e.router.ServeHTTP(w, req)
	return w
}

// Ensure unused imports are satisfied.
var _ = strings.NewReader
var _ = io.Discard
