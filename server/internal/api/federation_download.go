package api

import (
	"io"
	"net/http"
	"net/url"
	"path/filepath"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/federation"
	"github.com/spela/server/internal/storage"
	"gorm.io/gorm"
)

// downloadClient fetches a ROM from a friend over a signed request, returning
// the raw response so the caller can stream the body. Faked in tests.
type downloadClient interface {
	FetchDownload(baseURL, requestID string, id federation.Identity, peerFingerprint, key string) (*http.Response, error)
}

type httpDownloadClient struct{}

func (httpDownloadClient) FetchDownload(baseURL, requestID string, id federation.Identity, peerFingerprint, key string) (*http.Response, error) {
	const path = "/api/federation/download"
	u := baseURL + path + "?key=" + url.QueryEscape(key)
	req, err := http.NewRequest(http.MethodGet, u, nil)
	if err != nil {
		return nil, err
	}
	// Sign the path (query is excluded from the signature, matching the verifier).
	for k, v := range signedFederationHeaders(id, http.MethodGet, path, requestID, nil, peerFingerprint) {
		req.Header.Set(k, v)
	}
	// No client timeout: ROM transfers can be large and slow.
	return (&http.Client{}).Do(req)
}

// resolveLocalGameByKey finds a local game by its cross-server key (IGDB scraper
// id, or "crc:"+CRC32) and returns its on-disk path. ok=false if not found or
// the path is unsafe/missing.
func (h *FederationHandler) resolveLocalGameByKey(key string) (db.Game, string, bool) {
	var game db.Game
	var q *gorm.DB
	if strings.HasPrefix(key, "crc:") {
		q = h.DB.Where("crc32 = ? AND crc32 != ''", strings.TrimPrefix(key, "crc:"))
	} else {
		q = h.DB.Where("scraper_id = ? AND scraper_id != ''", key)
	}
	if err := q.First(&game).Error; err != nil {
		return db.Game{}, "", false
	}
	abs, err := storage.ResolveGamePath(game.FilePath, h.GameDirs)
	if err != nil || !storage.ValidateROMPath(abs, h.GameDirs) {
		return db.Game{}, "", false
	}
	return game, abs, true
}

// ginServeDownload streams a LOCAL ROM to a friend that we share downloads with.
// Behind VerifyFederationRequest + SharePolicy(download). Phase 3b-1 does NOT
// forward (relay) games we don't have — that's the opt-in multi-hop relay.
func (h *FederationHandler) ginServeDownload(c *gin.Context) {
	reqID := c.GetHeader(headerFedRequestID)
	if reqID == "" {
		reqID = federation.NewRequestID()
	}
	peer, ok := federationPeerFromGin(c)
	if !ok {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "no verified peer"})
		return
	}
	if !federation.CanShare(*peer, federation.DataClassDownload) {
		c.JSON(http.StatusForbidden, gin.H{"error": "downloads not shared with this peer"})
		return
	}
	key := c.Query("key")
	if key == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "missing key"})
		return
	}

	game, absPath, found := h.resolveLocalGameByKey(key)
	if !found {
		federation.RecordExchange(h.DB, federation.ExchangeRecord{
			RequestID: reqID, PeerFingerprint: peer.Fingerprint, PeerName: peer.Name,
			Direction: db.ExchangeInbound, Operation: "download_serve",
			DataClass: string(federation.DataClassDownload), Status: db.ExchangeError,
			Error: "not available locally (no forwarding in 3b-1)",
		})
		c.JSON(http.StatusNotFound, gin.H{"error": "game not available here"})
		return
	}

	federation.RecordExchange(h.DB, federation.ExchangeRecord{
		RequestID: reqID, PeerFingerprint: peer.Fingerprint, PeerName: peer.Name,
		Direction: db.ExchangeInbound, Operation: "download_serve",
		DataClass: string(federation.DataClassDownload), Status: db.ExchangeOK, ItemCount: 1,
	})
	c.FileAttachment(absPath, filepath.Base(game.FilePath))
}

// ginUserDownload lets a local user download a game that a DIRECT friend offers.
// Finds a hop-1 friend with the key whose ConsumePolicy(download) is set, fetches
// from them, and streams the bytes through. Behind user auth.
func (h *FederationHandler) ginUserDownload(c *gin.Context) {
	key := c.Query("key")
	if key == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "missing key"})
		return
	}
	sources, err := h.CatalogSnapshots.DirectSourcesForKey(key)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to look up sources"})
		return
	}
	client := h.DownloadClient
	if client == nil {
		client = httpDownloadClient{}
	}

	for _, fp := range sources {
		peer, err := h.Peers.GetByFingerprint(fp)
		if err != nil || peer.Status != db.PeerStatusActive || !federation.CanConsume(*peer, federation.DataClassDownload) {
			continue
		}
		reqID := federation.NewRequestID()
		started := time.Now()
		resp, ferr := client.FetchDownload(peer.BaseURL, reqID, h.Identity, peer.Fingerprint, key)
		if ferr != nil || resp.StatusCode != http.StatusOK {
			if resp != nil {
				resp.Body.Close()
			}
			errMsg := "download fetch failed"
			if ferr != nil {
				errMsg = ferr.Error()
			}
			federation.RecordExchange(h.DB, federation.ExchangeRecord{
				RequestID: reqID, PeerFingerprint: peer.Fingerprint, PeerName: peer.Name,
				Direction: db.ExchangeOutbound, Operation: "download_fetch",
				DataClass: string(federation.DataClassDownload), Status: db.ExchangeError,
				StartedAt: started, Error: errMsg,
			})
			continue
		}
		// Proxy the stream through to the user.
		for _, hdr := range []string{"Content-Type", "Content-Disposition", "Content-Length"} {
			if v := resp.Header.Get(hdr); v != "" {
				c.Header(hdr, v)
			}
		}
		c.Status(http.StatusOK)
		n, _ := io.Copy(c.Writer, resp.Body)
		resp.Body.Close()
		federation.RecordExchange(h.DB, federation.ExchangeRecord{
			RequestID: reqID, PeerFingerprint: peer.Fingerprint, PeerName: peer.Name,
			Direction: db.ExchangeOutbound, Operation: "download_fetch",
			DataClass: string(federation.DataClassDownload), Status: db.ExchangeOK,
			StartedAt: started, Bytes: n,
		})
		return
	}
	c.JSON(http.StatusNotFound, gin.H{"error": "not available from any direct friend"})
}
