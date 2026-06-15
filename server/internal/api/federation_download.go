package api

import (
	"fmt"
	"io"
	"net/http"
	"net/url"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/federation"
	"github.com/spela/server/internal/storage"
	"gorm.io/gorm"
)

// relayEnabledSettingKey gates the multi-hop ROM relay (#1348 Phase 3b-2). The
// relay forwards a friend-of-friend's game file through us — copyright-sensitive,
// so it is OFF unless an admin explicitly enables it.
const relayEnabledSettingKey = "federation_relay_enabled"

// relayEnabled reports whether this server will forward (relay) ROMs it doesn't
// have locally. Default false.
func (h *FederationHandler) relayEnabled() bool {
	var s db.ServerSetting
	if err := h.DB.Where("key = ?", relayEnabledSettingKey).First(&s).Error; err != nil {
		return false
	}
	return s.Value == "true"
}

// safeDownloadName turns a cross-server game key into a filesystem-safe download
// filename (the peer's filename is untrusted and never used).
func safeDownloadName(key string) string {
	b := make([]byte, 0, len(key))
	for i := 0; i < len(key); i++ {
		c := key[i]
		switch {
		case c >= 'a' && c <= 'z', c >= 'A' && c <= 'Z', c >= '0' && c <= '9', c == '.', c == '-', c == '_':
			b = append(b, c)
		default:
			b = append(b, '_')
		}
	}
	if len(b) == 0 {
		return "download.bin"
	}
	return string(b) + ".bin"
}

// streamSafeDownload proxies a fetched ROM response to c with a FORCED safe
// disposition — never trusting the upstream's Content-Type/Disposition (a
// malicious peer could send text/html → XSS on our origin). Returns bytes sent.
func streamSafeDownload(c *gin.Context, resp *http.Response, key string) int64 {
	c.Header("Content-Type", "application/octet-stream")
	c.Header("Content-Disposition", `attachment; filename="`+safeDownloadName(key)+`"`)
	c.Header("X-Content-Type-Options", "nosniff")
	if cl := resp.Header.Get("Content-Length"); cl != "" {
		if _, err := strconv.ParseInt(cl, 10, 64); err == nil {
			c.Header("Content-Length", cl)
		}
	}
	c.Status(http.StatusOK)
	n, _ := io.Copy(c.Writer, resp.Body)
	return n
}

// downloadClient fetches a ROM from a friend over a signed request, returning the
// raw response so the caller can stream the body. hops is the remaining forward
// budget the friend may use. Faked in tests.
type downloadClient interface {
	FetchDownload(baseURL, requestID string, id federation.Identity, peerFingerprint, key string, hops int) (*http.Response, error)
}

type httpDownloadClient struct{}

// fedDownloadHTTPClient bounds the dial/TLS/response-header phase (so a dead or
// stalling friend can't hold a goroutine + connection forever) but sets no
// overall timeout — the body stream is large and legitimately slow.
var fedDownloadHTTPClient = func() *http.Client {
	tr := http.DefaultTransport.(*http.Transport).Clone()
	tr.ResponseHeaderTimeout = 30 * time.Second
	return &http.Client{Transport: tr}
}()

func (httpDownloadClient) FetchDownload(baseURL, requestID string, id federation.Identity, peerFingerprint, key string, hops int) (*http.Response, error) {
	const path = "/api/federation/download"
	u := fmt.Sprintf("%s%s?key=%s&hops=%d", baseURL, path, url.QueryEscape(key), hops)
	req, err := http.NewRequest(http.MethodGet, u, nil)
	if err != nil {
		return nil, err
	}
	// Sign the path only (query is excluded from the signature, matching the verifier).
	for k, v := range signedFederationHeaders(id, http.MethodGet, path, requestID, nil, peerFingerprint) {
		req.Header.Set(k, v)
	}
	return fedDownloadHTTPClient.Do(req)
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

// ginServeDownload serves a ROM to a requesting friend (signed +
// SharePolicy(download)). If we have it locally, stream it. Otherwise, IF the
// relay is enabled and there's hop budget, forward it from a friend that offers
// it (multi-hop relay, #1348 Phase 3b-2). Default-off: with relay disabled this
// is the direct-only behaviour from 3b-1.
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

	// 1. Serve a local game directly.
	if game, absPath, found := h.resolveLocalGameByKey(key); found {
		federation.RecordExchange(h.DB, federation.ExchangeRecord{
			RequestID: reqID, PeerFingerprint: peer.Fingerprint, PeerName: peer.Name,
			Direction: db.ExchangeInbound, Operation: "download_serve",
			DataClass: string(federation.DataClassDownload), Status: db.ExchangeOK, ItemCount: 1,
		})
		c.FileAttachment(absPath, filepath.Base(game.FilePath))
		return
	}

	// 2. Relay (forward) from a friend, if enabled and within the hop budget.
	hops := federation.MaxFederationHops
	if q := c.Query("hops"); q != "" {
		if n, err := strconv.Atoi(q); err == nil && n >= 0 && n <= hops {
			hops = n
		}
	}
	if h.relayEnabled() && hops > 0 && h.relayForward(c, reqID, peer, key, hops) {
		federation.RecordExchange(h.DB, federation.ExchangeRecord{
			RequestID: reqID, PeerFingerprint: peer.Fingerprint, PeerName: peer.Name,
			Direction: db.ExchangeInbound, Operation: "download_serve",
			DataClass: string(federation.DataClassDownload), Status: db.ExchangeOK,
			MaxHops: hops, ItemCount: 1,
		})
		return
	}

	federation.RecordExchange(h.DB, federation.ExchangeRecord{
		RequestID: reqID, PeerFingerprint: peer.Fingerprint, PeerName: peer.Name,
		Direction: db.ExchangeInbound, Operation: "download_serve",
		DataClass: string(federation.DataClassDownload), Status: db.ExchangeError,
		Error: "not available locally and no relay path (relay off, no budget, or no source)",
	})
	c.JSON(http.StatusNotFound, gin.H{"error": "game not available here"})
}

// relayForward fetches the key from a friend that offers it (excluding the
// requester, to avoid a loop) and streams it through. Returns true if it served
// the response. hops is the budget; the onward request gets hops-1.
func (h *FederationHandler) relayForward(c *gin.Context, reqID string, requester *db.FederationPeer, key string, hops int) bool {
	sources, err := h.CatalogSnapshots.SourcePeersForKey(key)
	if err != nil {
		return false
	}
	client := h.DownloadClient
	if client == nil {
		client = httpDownloadClient{}
	}
	for _, fp := range sources {
		if fp == requester.Fingerprint {
			continue // loop guard: never forward back to the requester
		}
		src, err := h.Peers.GetByFingerprint(fp)
		if err != nil || src.Status != db.PeerStatusActive || !federation.CanConsume(*src, federation.DataClassDownload) {
			continue
		}
		started := time.Now()
		resp, ferr := client.FetchDownload(src.BaseURL, reqID, h.Identity, src.Fingerprint, key, hops-1)
		if ferr != nil || resp.StatusCode != http.StatusOK {
			if resp != nil {
				resp.Body.Close()
			}
			errMsg := "relay fetch failed"
			if ferr != nil {
				errMsg = ferr.Error()
			}
			federation.RecordExchange(h.DB, federation.ExchangeRecord{
				RequestID: reqID, PeerFingerprint: src.Fingerprint, PeerName: src.Name,
				Direction: db.ExchangeOutbound, Operation: "download_relay",
				DataClass: string(federation.DataClassDownload), Status: db.ExchangeError,
				StartedAt: started, MaxHops: hops - 1, Error: errMsg,
			})
			continue
		}
		n := streamSafeDownload(c, resp, key)
		resp.Body.Close()
		federation.RecordExchange(h.DB, federation.ExchangeRecord{
			RequestID: reqID, PeerFingerprint: src.Fingerprint, PeerName: src.Name,
			Direction: db.ExchangeOutbound, Operation: "download_relay",
			DataClass: string(federation.DataClassDownload), Status: db.ExchangeOK,
			StartedAt: started, MaxHops: hops - 1, Bytes: n,
		})
		return true
	}
	return false
}

// ginUserDownload lets a local user download a game a friend offers — directly
// from a friend that has it, or via that friend's relay for a friend-of-friend's
// game. Picks a friend whose catalog offers the key and whose ConsumePolicy
// allows downloads, then proxies the (safely re-typed) stream. Behind user auth.
func (h *FederationHandler) ginUserDownload(c *gin.Context) {
	key := c.Query("key")
	if key == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "missing key"})
		return
	}
	sources, err := h.CatalogSnapshots.SourcePeersForKey(key)
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
		resp, ferr := client.FetchDownload(peer.BaseURL, reqID, h.Identity, peer.Fingerprint, key, federation.MaxFederationHops-1)
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
		n := streamSafeDownload(c, resp, key)
		resp.Body.Close()
		federation.RecordExchange(h.DB, federation.ExchangeRecord{
			RequestID: reqID, PeerFingerprint: peer.Fingerprint, PeerName: peer.Name,
			Direction: db.ExchangeOutbound, Operation: "download_fetch",
			DataClass: string(federation.DataClassDownload), Status: db.ExchangeOK,
			StartedAt: started, Bytes: n,
		})
		return
	}
	c.JSON(http.StatusNotFound, gin.H{"error": "not available from any friend"})
}
