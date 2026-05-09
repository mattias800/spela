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
func TestBroadcast_RecipientFilter(t *testing.T) {
	hub := NewHub(nil)
	go hub.Run()
	defer hub.Close()

	make := func(uid uint) *Client {
		return &Client{
			Hub:    hub,
			Send:   make(chan []byte, 4),
			UserID: uid,
		}
	}

	a := make(1)
	b := make(2)
	c := make(3)

	hub.register <- a
	hub.register <- b
	hub.register <- c

	// Wait briefly for register to settle and for the implicit
	// online-status broadcast to drain.
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
	for _, cli := range []*Client{a, b, c} {
	drain:
		for {
			select {
			case <-cli.Send:
			default:
				break drain
			}
		}
	}

	// Targeted event — only users 1 and 3 should receive it.
	hub.Broadcast(Event{
		Type:             "private_test",
		Payload:          map[string]string{"hello": "world"},
		RecipientUserIDs: []uint{1, 3},
	})

	collect := func(cli *Client) []string {
		var got []string
		t := time.NewTimer(100 * time.Millisecond)
		defer t.Stop()
	loop:
		for {
			select {
			case msg := <-cli.Send:
				got = append(got, string(msg))
			case <-t.C:
				break loop
			}
		}
		return got
	}

	aMsgs := collect(a)
	bMsgs := collect(b)
	cMsgs := collect(c)

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
