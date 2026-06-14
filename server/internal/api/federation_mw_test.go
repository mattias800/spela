package api

import (
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"net/http"
	"net/http/httptest"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/federation"
	"github.com/stretchr/testify/assert"
	"gorm.io/gorm"
)

// signedFedRequest builds a signed server-to-server request for tests.
func signedFedRequest(id federation.Identity, method, path, body string, ts time.Time) *http.Request {
	bodyHash := sha256.Sum256([]byte(body))
	tsStr := strconv.FormatInt(ts.Unix(), 10)
	msg := signedRequestMessage(method, path, tsStr, hex.EncodeToString(bodyHash[:]))
	sig := id.Sign(msg)

	req := httptest.NewRequest(method, path, strings.NewReader(body))
	req.Header.Set(headerFedFingerprint, id.Fingerprint())
	req.Header.Set(headerFedTimestamp, tsStr)
	req.Header.Set(headerFedSignature, base64.StdEncoding.EncodeToString(sig))
	return req
}

func fedMWRouter(database *gorm.DB) *gin.Engine {
	r := gin.New()
	r.Use(VerifyFederationRequest(database))
	r.GET("/api/federation/test", func(c *gin.Context) {
		peer, ok := federationPeerFromGin(c)
		if !ok {
			c.String(http.StatusInternalServerError, "no peer in context")
			return
		}
		c.String(http.StatusOK, peer.Fingerprint)
	})
	return r
}

func TestFederationMW_AllowsValidSignedRequest(t *testing.T) {
	gin.SetMode(gin.TestMode)
	database := openAPIFedTestDB(t)
	peerID, _ := federation.GenerateIdentity()
	activePeer(t, database, peerID, "Peer", "https://peer")

	w := httptest.NewRecorder()
	fedMWRouter(database).ServeHTTP(w, signedFedRequest(peerID, http.MethodGet, "/api/federation/test", "", time.Now()))
	assert.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, peerID.Fingerprint(), w.Body.String())
}

func TestFederationMW_RejectsUnknownPeer(t *testing.T) {
	gin.SetMode(gin.TestMode)
	database := openAPIFedTestDB(t)
	stranger, _ := federation.GenerateIdentity()

	w := httptest.NewRecorder()
	fedMWRouter(database).ServeHTTP(w, signedFedRequest(stranger, http.MethodGet, "/api/federation/test", "", time.Now()))
	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

func TestFederationMW_RejectsStaleTimestamp(t *testing.T) {
	gin.SetMode(gin.TestMode)
	database := openAPIFedTestDB(t)
	peerID, _ := federation.GenerateIdentity()
	activePeer(t, database, peerID, "Peer", "https://peer")

	w := httptest.NewRecorder()
	old := time.Now().Add(-10 * time.Minute)
	fedMWRouter(database).ServeHTTP(w, signedFedRequest(peerID, http.MethodGet, "/api/federation/test", "", old))
	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

func TestFederationMW_RejectsTamperedPath(t *testing.T) {
	gin.SetMode(gin.TestMode)
	database := openAPIFedTestDB(t)
	peerID, _ := federation.GenerateIdentity()
	activePeer(t, database, peerID, "Peer", "https://peer")

	// Sign for one path, send to another → signature must fail.
	req := signedFedRequest(peerID, http.MethodGet, "/api/federation/other", "", time.Now())
	req2 := httptest.NewRequest(http.MethodGet, "/api/federation/test", nil)
	for k, v := range req.Header {
		req2.Header[k] = v
	}
	w := httptest.NewRecorder()
	fedMWRouter(database).ServeHTTP(w, req2)
	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

func TestFederationMW_RejectsInactivePeer(t *testing.T) {
	gin.SetMode(gin.TestMode)
	database := openAPIFedTestDB(t)
	peerID, _ := federation.GenerateIdentity()
	store := activePeer(t, database, peerID, "Peer", "https://peer")
	_ = store.SetStatus(peerID.Fingerprint(), "pending")

	w := httptest.NewRecorder()
	fedMWRouter(database).ServeHTTP(w, signedFedRequest(peerID, http.MethodGet, "/api/federation/test", "", time.Now()))
	assert.Equal(t, http.StatusUnauthorized, w.Code)
}
