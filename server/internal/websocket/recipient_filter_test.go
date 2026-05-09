package websocket

import (
	"encoding/json"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
)

// TestBroadcast_RecipientFilter verifies issue #1119: an Event with a
// non-empty RecipientUserIDs is delivered only to clients whose UserID
// matches, leaving the existing fan-out unchanged for events without
// recipients.
//
// Implementation note: register fires an online_status broadcast for
// every client. Those events race with the test's drain loop on CI —
// previous "wait then drain" approach was flaky because the broadcast
// goroutine hadn't always delivered the online_status messages by the
// time the loop ran. The collect step now filters by event type, so
// online_status events are tolerated regardless of their delivery
// timing and only the targeted "private_test" events are counted.
func TestBroadcast_RecipientFilter(t *testing.T) {
	hub := NewHub(nil)
	go hub.Run()
	defer hub.Close()

	make := func(uid uint) *Client {
		return &Client{
			Hub:    hub,
			Send:   make(chan []byte, 16),
			UserID: uid,
		}
	}

	a := make(1)
	b := make(2)
	c := make(3)

	hub.register <- a
	hub.register <- b
	hub.register <- c

	// Wait until all three clients are registered.
	deadline := time.Now().Add(500 * time.Millisecond)
	for time.Now().Before(deadline) {
		hub.mu.Lock()
		registered := len(hub.clients)
		hub.mu.Unlock()
		if registered == 3 {
			break
		}
		time.Sleep(5 * time.Millisecond)
	}

	// Targeted event — only users 1 and 3 should receive it.
	hub.Broadcast(Event{
		Type:             "private_test",
		Payload:          map[string]string{"hello": "world"},
		RecipientUserIDs: []uint{1, 3},
	})

	// Collect drains the client's Send channel for a short window and
	// keeps only the events whose `type` matches `wantType`. This
	// sidesteps the online_status race entirely.
	collect := func(cli *Client, wantType string) []string {
		var got []string
		t := time.NewTimer(200 * time.Millisecond)
		defer t.Stop()
	loop:
		for {
			select {
			case msg := <-cli.Send:
				var env struct {
					Type string `json:"type"`
				}
				if err := json.Unmarshal(msg, &env); err == nil && env.Type == wantType {
					got = append(got, string(msg))
				}
			case <-t.C:
				break loop
			}
		}
		return got
	}

	aMsgs := collect(a, "private_test")
	bMsgs := collect(b, "private_test")
	cMsgs := collect(c, "private_test")

	assert.Len(t, aMsgs, 1, "user 1 should receive the targeted event")
	assert.Empty(t, bMsgs, "user 2 should NOT receive the targeted event")
	assert.Len(t, cMsgs, 1, "user 3 should receive the targeted event")

	// Sanity: the recipient list is not serialised to clients.
	for _, msgs := range [][]string{aMsgs, cMsgs} {
		if len(msgs) == 0 {
			continue
		}
		var m map[string]any
		if err := json.Unmarshal([]byte(msgs[0]), &m); err == nil {
			_, hasRecipients := m["recipientUserIds"]
			_, hasGoFieldName := m["RecipientUserIDs"]
			assert.False(t, hasRecipients, "wire payload must not include recipientUserIds")
			assert.False(t, hasGoFieldName, "wire payload must not include RecipientUserIDs")
		}
	}
}
