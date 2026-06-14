package api

import (
	"bytes"
	"crypto/ed25519"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"strconv"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/federation"
	"gorm.io/gorm"
)

// Federation request headers for signed server-to-server calls (#1343, #1350).
const (
	headerFedFingerprint = "X-Spela-Fingerprint"
	headerFedTimestamp   = "X-Spela-Timestamp"
	headerFedSignature   = "X-Spela-Signature"
	headerFedRequestID   = "X-Spela-Request-Id"
)

// federationTimestampSkew is the maximum age of a signed federation request
// (replay protection).
const federationTimestampSkew = 5 * time.Minute

// fedPeerContextKey is where the verified peer is stashed for handlers.
const fedPeerContextKey = "federationPeer"

// signedRequestMessage is the canonical byte string a peer signs for a
// server-to-server request: method, path, timestamp, body hash, AND the
// recipient server's fingerprint. Binding the recipient prevents a captured
// signed request from being replayed against a different server the same peer is
// paired with (within the skew window). Both the signer (outbound client) and
// verifier (this middleware) must build it identically.
func signedRequestMessage(method, path, timestamp, bodyHashHex, recipientFingerprint string) []byte {
	return []byte(fmt.Sprintf("%s\n%s\n%s\n%s\n%s", method, path, timestamp, bodyHashHex, recipientFingerprint))
}

// VerifyFederationRequest authenticates server-to-server requests: it requires a
// known, active peer whose Ed25519 signature covers method, path, timestamp,
// body hash, and this server's own fingerprint (selfFingerprint), within the
// allowed clock skew. On success the verified peer is set on the gin context
// under fedPeerContextKey. Rejections are logged (not written to the ledger) to
// avoid a hostile peer flooding storage.
func VerifyFederationRequest(database *gorm.DB, selfFingerprint string) gin.HandlerFunc {
	store := federation.PeerStore{DB: database}
	return func(c *gin.Context) {
		fp := c.GetHeader(headerFedFingerprint)
		tsStr := c.GetHeader(headerFedTimestamp)
		sigB64 := c.GetHeader(headerFedSignature)
		reqID := c.GetHeader(headerFedRequestID)

		reject := func(reason string) {
			slog.Warn("federation: rejected inbound request",
				"component", "federation", "request_id", reqID,
				"peer", federation.ShortFingerprint(fp), "path", c.Request.URL.Path,
				"reason", reason)
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": reason})
		}

		if fp == "" || tsStr == "" || sigB64 == "" {
			reject("missing federation auth headers")
			return
		}

		ts, err := strconv.ParseInt(tsStr, 10, 64)
		if err != nil || absDuration(time.Since(time.Unix(ts, 0))) > federationTimestampSkew {
			reject("stale or invalid timestamp")
			return
		}

		peer, err := store.GetByFingerprint(fp)
		if err != nil || peer.Status != db.PeerStatusActive {
			reject("unknown or inactive peer")
			return
		}

		pub, err := base64.StdEncoding.DecodeString(peer.PublicKey)
		if err != nil || len(pub) != ed25519.PublicKeySize {
			reject("corrupt peer key")
			return
		}

		// Hash the body without consuming it for the handler.
		bodyBytes, _ := io.ReadAll(c.Request.Body)
		c.Request.Body = io.NopCloser(bytes.NewReader(bodyBytes))
		bodyHash := sha256.Sum256(bodyBytes)

		sig, err := base64.StdEncoding.DecodeString(sigB64)
		if err != nil {
			reject("invalid signature encoding")
			return
		}
		msg := signedRequestMessage(c.Request.Method, c.Request.URL.Path, tsStr, hex.EncodeToString(bodyHash[:]), selfFingerprint)
		if !federation.Verify(pub, msg, sig) {
			reject("signature verification failed")
			return
		}

		c.Set(fedPeerContextKey, peer)
		c.Next()
	}
}

// federationPeerFromGin returns the verified peer set by VerifyFederationRequest.
func federationPeerFromGin(c *gin.Context) (*db.FederationPeer, bool) {
	v, ok := c.Get(fedPeerContextKey)
	if !ok {
		return nil, false
	}
	peer, ok := v.(*db.FederationPeer)
	return peer, ok
}

func absDuration(d time.Duration) time.Duration {
	if d < 0 {
		return -d
	}
	return d
}
