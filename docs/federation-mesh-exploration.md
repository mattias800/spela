# Spela Federation Mesh — Design Exploration

> **Status: exploration / vision.** Nothing here is implemented. This document
> explores what a federated "mesh of friend servers" could give Spela, how it
> could work on top of the current codebase, and which problems are hard. It is
> a thinking document, not a build spec. A specific, buildable slice can be cut
> from it later (see *Phasing*).
>
> Written 2026-06-14. Grounded in the codebase as of branch `controller-detection`.

---

## 1. TL;DR

Today every Spela server is an island: clients talk to one server, all data
(games, playtime, leaderboards, presence) is local to that server. The vision is
to let server owners **add friend servers**, forming a mesh in which servers
**aggregate data from each other** — friend libraries become browsable and
downloadable, top-lists and "top players" span the network, presence and social
activity reach across servers.

Through the design dialogue we committed to a specific, opinionated shape:

1. **Relay-only topology.** A server *only ever communicates with its direct,
   mutually-authenticated friends.* Transitive reach is achieved by **re-serving**
   (relaying / re-aggregating), never by routing. If A is friends with B and B is
   friends with C, **A can never connect to C** until A and C explicitly friend
   each other. B is the boundary.
2. **Reach is a property of the data class, not a global setting.** Heavy,
   legally-sensitive, executable data (game ROMs) gets *short* reach; cheap,
   additive, low-stakes data (aggregate stats) gets *long* reach. One policy
   vector per data class.
3. **Everything is source-stamped and merged idempotently.** Every datum carries
   an anonymous origin fingerprint and a hop count. Merges dedupe on the
   fingerprint so the same source is never double-counted.
4. **Reach is requester-controlled and hop-bounded.** A asks B *"max 1 hop"* and
   B filters before sending.
5. **Consent is granular and bidirectional per friend, per data class.** A
   controls both *what it shares with* B and *what it accepts from* B.

The elegant payoff: the same relay-only rule gives **unbounded, near-free
mesh-wide reach for aggregates** (they merge hop-by-hop) and **naturally bounded,
pay-as-you-go reach for downloads** (each relay hop costs real bandwidth).

---

## 2. The vision

A Spela server owner adds a handful of friends' servers. From that moment:

- Their library page can show, alongside their own games, **what their friends
  have** — and let them download a friend's game through a relay, without ever
  connecting to the friend directly.
- Leaderboards stop being "the five people on my server" and become **"everyone
  reachable in the mesh"** — top games, most-played, most-active players, trending
  — with a slider for how far out to look ("just my friends" / "2 hops" / "as far
  as reaches").
- They can see **who's playing what right now** across their friend network, and
  a cross-mesh activity feed.
- Achievements, challenges, speedrun times, reviews — all become things you
  compete and compare on across the network, not just locally.

Crucially, this is a **friends mesh, not an open public federation.** You only
ever expose yourself to servers whose operators you chose and authenticated. The
network grows through trust relationships, the way a real social graph does.

---

## 3. Core architectural principles

These are the load-bearing decisions. Everything else follows from them.

### P1 — Relay-only topology (no transitive connections)

Every server talks **only** to its direct, mutually-authenticated friends. There
is no routing, no "connect me to your friend," no ambient ability to reach a
stranger. Transitive reach is achieved by the intermediate server **re-serving**:

- **For downloads → active byte relay.** B fetches the file from C over the
  authenticated B↔C channel and streams it to A over the A↔B channel. A and C
  never share a connection or credentials.
- **For aggregate data → stored aggregate.** B has already merged C's rollup into
  its own. When A pulls from B, it gets B's merged result. The "aggregate of
  aggregates" is literal: each server holds and re-serves its friends' rollups.

```
        A ──auth──> B ──auth──> C ──auth──> D
        │           │           │
   only talks    relays /     relays /
   to B          aggregates   aggregates
                 for A        for B

  A's reachable mesh = {B} ∪ {what B re-serves} ∪ ... (bounded by reach policy)
  A's set of *connections* = {B}.  Always. That is the whole security story.
```

**Why this is strong:**

- **Minimal attack surface.** You are only ever exposed to friends you explicitly
  authenticated. No unknown server ever connects to you.
- **Clean blast radius.** Unfriend B and B's entire relayed/aggregated
  contribution disappears from your view instantly. No lingering trust in C.
- **No ambient authority.** A cannot be tricked into talking to a hostile C,
  because A has no channel to C and no way to obtain one without an explicit,
  mutual, authenticated friending step.

### P2 — Reach is a per-data-class policy

There is no single "trust level." Each data class carries its own policy vector:

```
{ reach, consent layer, transport, freshness, verifiability }
```

This is because the data classes split into two fundamentally different
federation *patterns* with opposite economics:

| | **Gossip-aggregate** | **Locate-and-fetch (relay)** |
|---|---|---|
| Examples | top games, playtime, top players, ratings | game ROMs, save files |
| Payload | tiny, additive numbers | large, executable/personal blobs |
| Cost of reach | ~free (merge hop-by-hop) | real bandwidth per hop |
| Wants reach | long → **mesh-wide** | short → **friends-of-friends** |
| Trust concern | inflation / fake stats | executing an untrusted file; legality |

Trying to force both through one reach setting is the mistake. The matrix in §6
makes the per-class policy concrete.

### P3 — Source-stamping and idempotent merge

Every federated datum carries:

- **An anonymous origin fingerprint** — derived from the origin server's identity
  key (e.g. a hash of its public key). It is **stable** (so the same source can be
  recognized across paths) but **anonymous** (it reveals *that* a distinct origin
  exists and what it claims, never its hostname or address). A learns "origin
  `K_C` reported 1,000 hours"; it does **not** learn who or where C is, and still
  cannot contact C. This does not violate P1 — it is an opaque tag in relayed
  data, not a connection.
- **A hop count** = the graph distance from the holder to the origin (see §5.2).

Merges **dedupe on the origin fingerprint**, so the same source is counted
exactly once regardless of how many paths it arrives by. This makes the merge
idempotent — `merge(merge(x)) = merge(x)` — which is exactly the project's
existing idempotency rule (CLAUDE.md rule 7), applied to federation.

**Why stamping is non-negotiable:** social graphs have triangles. If A friends
both B and D, and both B and D friend C, then C's numbers reach A *twice*. Without
a per-source identity to dedupe on, A literally cannot correct the double-count,
and a double-counted leaderboard is worse than no leaderboard. (Worked example in
§5.3.)

### P4 — Requester-controlled, hop-bounded reach

The consumer decides how far to look, at query time:

> A → B: *"give me the most-played aggregate, max 1 hop."*
> B returns its own data (hop 0) **plus** its direct friends' data (hop 1),
> filtered **before it leaves B**. Friend-of-friend data (hop 2+) is never sent.

Effective reach for any pull is the **minimum** of several caps:

```
reach = min( requester's requested hops,
             each relay's willingness to serve that far,
             the data class's policy cap,
             user-consent reach (for personal data) )
```

### P5 — Granular, bidirectional consent (per friend, per data class)

For each friend, the operator independently configures:

- **Outbound (share):** which data classes this server exposes to that friend.
  *("Share my catalog with B, but not my leaderboards.")*
- **Inbound (consume / trust):** which data classes this server accepts from that
  friend. *("B keeps feeding fake leaderboard data — stop consuming `top-players`
  from B, but keep using B as a game relay.")*

The **inbound toggle is the mesh's core moderation primitive** — local,
unilateral, immediate. (Deliberately *not* a reputation-gossip system; see §11.)

### P6 — Per-server cryptographic identity

A server today has **no identity at all** (confirmed: no `ServerID`, no keypair,
no instance URL — see §9). Federation requires introducing one: an **Ed25519
keypair** per server. The public-key fingerprint *is* the anonymous origin ID of
P3; the private key signs every federated claim and authenticates the friend
handshake. Signed provenance is what lets a receiver trust that "origin `K_C`
really said this" survives relaying — without it, a relay could fabricate sources
undetectably.

---

## 4. Friending, identity & transport security

### Server identity

On first startup (or migration), a server generates and persists an Ed25519
keypair (private key encrypted at rest, alongside the existing IGDB/RA secrets in
`ServerSetting`). Derived values:

- **Origin fingerprint** = `base32(sha256(pubkey))[:N]` — the anonymous, stable ID.
- The public key is shared *only* during a friend handshake, never broadcast.

### The friend handshake (mutual, authenticated, out-of-band)

Friendship is **bilateral and explicit** — nobody is in your mesh without both
operators agreeing:

1. Operator of A generates a short-lived **invite/friend-code** (carries A's
   pubkey + a reachable endpoint + a nonce, signed by A).
2. Operator of B enters it (paste, QR, link). B verifies the signature, presents
   A's fingerprint for **human confirmation** ("Add friend `K_A`?").
3. B replies over the endpoint with B's own signed pubkey bundle; A confirms.
4. Both sides store the peer in a **friend registry** and establish an
   authenticated, encrypted channel (mutual-key handshake; all subsequent
   federation requests are signed and verified).

Out-of-band confirmation (you got the code from a person you trust) defeats
man-in-the-middle on the pairing. Revocation is a local delete of the registry
row + key — instant, unilateral, and removes all of that friend's contribution.

### Transport

All friend-to-friend traffic rides an authenticated, encrypted channel
(server-to-server, distinct from the user JWT auth). The codebase already enforces
strict same-origin WebSocket checks and rejects credentialed cross-origin upgrades
(`server/internal/websocket/hub.go`) — federation extends that posture: only
known friend keys are accepted, everything is signed.

### Anonymity guarantee

The mesh preserves a clean privacy boundary: **A knows the identities (and
addresses) only of its direct friends.** Everything deeper is pseudonymous origin
fingerprints with no contactable address. This is the cryptographic expression of
"connections between friends-of-friends are not allowed."

### User-consent layer (personal data)

Server-level federation policy is necessary but **not sufficient** for personal
data. A *player* appearing on a 4-hop-away leaderboard requires *that user's*
opt-in. Spela already has the hook: `User.ProfileVisibility` (`public` / `private`,
default public). Effective reach for any user-attributed datum is:

```
min( server federation policy, the user's own visibility setting )
```

A `private` user is filtered **at the origin, before stamping** — their data never
enters the mesh at all, so it cannot leak via a downstream relay.

---

## 5. How it works

### 5.1 Gossip-aggregate (stats, leaderboards) — unbounded but cheap

Each server periodically computes local rollups (it already does this live — see
§9) and shares them with friends who are allowed to consume them. On ingest, a
server **merges** a friend's rollup into its own and **re-serves the merged
result**. Reach is unbounded *in depth* but bounded *in cost*: every server only
ever holds and merges its direct friends' rollups. A 5-hop-away server's playtime
is already baked into your friend's aggregate by the time you pull it. **Mesh-wide
reach emerges from purely local communication.**

### 5.2 Hop counting

Convention: **hop count = graph distance from the holder to the origin.**

- A server stamps data it originates with `hop = 0` and its own origin fingerprint.
- On ingesting a friend's rollup, it adds `+1` to every entry's hop count and
  stores it, preserving the origin stamp.

So at any server: own data is hop 0, a direct friend's is hop 1, a
friend-of-friend's is hop 2, and so on.

**Requester translation (the off-by-one worth knowing):** because A's friend B is
one edge away, data B reports as "hop *n*" is "hop *n+1*" from A's perspective. So
to see *everything within distance N of me*, A asks each friend for *max N−1
hops*. A displays by filtering its merged store to `hop ≤ N`.

### 5.3 Idempotent merge & the double-count (worked example)

```
D originates:  (origin = K_D, hop 0, 500h on Game G)
C (friends D), ingests → stores (K_D, hop 1, 500h);  C's own = (K_C, hop 0, ...)
B (friends C), ingests → stores (K_C, hop 1) and (K_D, hop 2);  B's own = (K_B, hop 0)

A is friends with B AND D.
  From B (max 2 hops): (K_B,1), (K_C,2), (K_D,3)     [each +1 on A's ingest]
  From D directly:     (K_D,1), ...                  [D's own = hop 0 → 1 at A]

A now holds K_D twice — via B (hop 3) and via D (hop 1).
A dedupes on origin fingerprint K_D → counts it ONCE (keeping the min hop, 1).
```

Without the `K_*` stamp, A could not detect that the two `K_D` contributions are
the same source, and would report 1,000h for a 500h reality. **The stamp is what
makes mesh-wide aggregation arithmetically correct in a graph with cycles** — and
social graphs are nothing but cycles.

### 5.4 Locate-and-fetch / active relay (downloads) — bounded but valued

1. **Discovery.** Through catalog gossip (a metadata class — small, stamped,
   reach-limited), A's view includes "this game is available somewhere in your
   reach." A browses it as part of B's offered catalog.
2. **Fetch.** A requests the game from B. B does **not** hand A a connection to C.
   B fetches the bytes from C over B↔C and **streams them to A** over A↔B, acting
   as an active relay. Range requests / resume pass straight through (already
   supported — see §9), so multi-GB transfers and interrupted downloads work.
3. **Caching (admin-configurable, §6 / §11).** B may keep the bytes (CDN-like,
   faster for the next requester, but B now stores & redistributes the file) or
   pass through without storing (lighter legal footprint). **We do not pick the
   operator's legal posture for them.**

Each relay hop costs B real bandwidth on both legs, which is exactly why
downloads default to a short reach (friends-of-friends).

---

## 6. The per-data-class policy matrix (the spine)

| Data class | Pattern | Default reach | Consent gate | Transport | Notes |
|---|---|---|---|---|---|
| **Game catalog (availability metadata)** | gossip | Friends-of-friends (≤2) | Server (+ per-game opt-out) | Stamped metadata | Drives discovery; "who has what" |
| **Game ROM download** | relay | Friends-of-friends (≤1 relay hop) | Server | Active byte relay; caching configurable | Heavy, legal, executed |
| **Aggregate stats** (top games, total playtime, trending) | gossip | Mesh-wide (hop-bounded by viewer) | None (non-personal) | Stamped + merged | Network-effect payoff |
| **Top players / leaderboards** | gossip | Mesh-wide (hop-bounded) | **Server + user** (`ProfileVisibility`) | Stamped + merged | Personal → user opt-in required |
| **Presence ("playing now")** | gossip/push | Friends-of-friends | Server + user | Near-real-time, low TTL | High-frequency, privacy-sensitive |
| **Reviews / ratings** | gossip | Configurable (default ≤2) | Server + user | Stamped | Per-user content |
| **Achievements / challenges / speedruns** | gossip | Mesh-wide (hop-bounded) | Server + user | Stamped + merged | Great for cross-mesh competition |
| **Save states (portable / shared saves)** | relay | Direct or friends-of-friends | User | Relay fetch | Personal blobs |
| **Cross-server netplay** | relay/broker | Friends-of-friends | Server + user | Relay-brokered session | Matchmaking + transport |

Defaults are starting points; P4 (requester hop limit) and P5 (per-friend
toggles) let every operator tighten or open each cell.

---

## 7. Feature catalog & user value

What the mesh actually buys users, organized by data class. This is the breadth
the vision is really about.

### Game discovery & sharing
- **Browse friends' libraries** inline with your own ("Available from friends").
- **Download a friend's game** through the relay — no direct connection needed.
- **"Complete the set" / find-the-missing-game:** you're missing one title in a
  series; the mesh shows a friend has it.
- **Game requests:** "I'd love to play X" surfaces to friends who have it (with
  their consent to fulfil).
- **Resilience:** a game (or a core/BIOS) you can't source locally may be
  reachable via a friend.

### Mesh-wide top-lists & competition
- **Top games / most-played across the mesh** — finally meaningful numbers.
- **Most-active players**, network-wide, attributed but privacy-gated.
- **Trending across the mesh** (the existing 24h-activity "trending" goes wide).
- **Reach slider in the UI:** "my server" / "my friends" / "2 hops" / "all
  reachable" — directly powered by the hop count (P4/§5.2).
- **Per-source breakdown** for transparency: "of these 12k hours, X came from your
  direct friends, Y from 2 hops out" — also the anti-inflation tell (§11).
- **Cross-mesh achievements, challenges, and speedrun boards** — Spela already has
  `Challenge`, RA integration, and ratings locally; federating them turns solo
  competition into a network sport.

### Social & presence
- **"Friends playing now"** across servers (heartbeat presence, §9, gossiped to
  friends-of-friends).
- **Cross-mesh activity feed:** started playing, favorited, rated, shared a save.
- **Cross-server multiplayer:** netplay matchmaking with friends-of-friends,
  brokered/relayed through the intermediate server (Spela already has
  `NetplaySession` + a netplay WebSocket).

### Saves & continuity
- **Portable saves / shared saves across friends** (the `SharedSaveState` feature
  exists locally; relay makes it cross-server).

### Mesh-awareness
- **"Your mesh" view:** how many servers/games/players are reachable, at what
  depth — making the network effect visible and motivating more friending.

---

## 8. Reach economics — the elegant asymmetry, restated

It is worth stating plainly because it is the design's nicest property and it is
not an accident:

- **Aggregates go mesh-wide essentially for free.** Hop-by-hop merge means each
  server only ever processes its friends' rollups, yet depth is unbounded. Bigger
  network → richer leaderboards → more reason to add friends → bigger network.
- **Downloads stay naturally local.** Real bandwidth per relay hop makes deep
  chains self-limiting; "friends of friends" is both the security default and the
  economically sane one.

Same relay-only rule, opposite reach — and both are the *right* reach for their
data class.

---

## 9. Mapping onto the current codebase

Grounded in the exploration of `server/` and `player/`.

**What exists and helps:**
- **Live local aggregation already works.** `/api/stats/most-played`,
  `/api/stats/most-active-players`, and `/api/explore/*` compute rollups on demand
  from `PlayHistory` + `ActivityEvent` (`server/internal/api/huma_stats.go`,
  `huma_explore_community.go`). These local rollups become the federation-shareable
  aggregates.
- **`PlayHistory` is uniquely keyed `(UserID, GameID)`** and all aggregations
  assume it — clean source for per-server rollups.
- **`User.ProfileVisibility` (public/private)** is the ready-made user-consent gate
  for personal data (§4).
- **Downloads already support HTTP range/resume and multi-GB files**
  (`HumaDownloadGame` + `streamFileFromDisk` in `huma_downloads.go`) — the relay
  proxies ranges with no new transfer machinery.
- **The player app is already multi-server** (`ServerConnectionEntity`,
  `ServerRepository`) — though only one server is *active* at a time. Much of the
  mesh is server-side aggregation surfaced through enriched responses, so the
  player largely **consumes new fields** rather than needing a new connection model.
- **WebSocket presence** is heartbeat-driven (90s timeout,
  `server/internal/websocket/hub.go`) — a natural source for gossiped presence.
- **Existing social surface to federate:** `Favorite`, `ActivityEvent`,
  `GameRating`/reviews, `Challenge`, `NetplaySession`, `SharedSaveState`, `blocks`.

**What must be added:**
- **Server identity** — `ServerSetting(server_id)` + an Ed25519 keypair
  (encrypted at rest). *None exists today.*
- **Friend registry** — peers (fingerprint, pubkey, endpoint, per-class
  inbound/outbound policy, status), plus the pairing handshake.
- **Source-stamping & sync state** — federated aggregates tagged with
  `(origin fingerprint, hop count, metric, period)`; idempotent merge keyed on
  that; periodic pull/refresh bookkeeping.
- **A `/api/federation/*` namespace** (Huma, like the rest): handshake, hop-bounded
  aggregate pull, catalog query, download-relay endpoint. **No server-to-server
  routes exist today** — this is greenfield.
- **Cross-server game identity.** Local `Game.FilePath`/`ID` are server-specific.
  The cross-server key is **IGDB `ScraperID`**, with `RAGameID` and `CRC32` as
  fallbacks — the natural "same game on different servers" identifiers.

---

## 10. Phasing — how to build it incrementally

Each phase is independently useful and de-risks the next.

- **Phase 0 — Identity & friendship.** Server keypair, friend registry,
  mutual authenticated pairing handshake. No data crosses yet. *(Proves the trust
  fabric.)*
- **Phase 1 — Direct-friend (1-hop) aggregate stats.** Federate the *existing*
  most-played / most-active-players rollups to direct friends only. No relay, no
  downloads, no transitivity. Exercises source-stamping, signing, merge, and the
  user-consent gate at the lowest risk and highest immediate value.
- **Phase 2 — Hop-bounded transitive aggregates.** Add hop counting, re-aggregate
  on ingest, requester-controlled `max hops`, dedupe, and k-anonymity. This is the
  "aggregate of aggregates" and the reach slider.
- **Phase 3 — Catalog federation + download relay.** The bandwidth- and
  legally-heavy part. Catalog gossip for discovery; active byte relay for fetch;
  configurable caching posture per operator.
- **Phase 4 — Presence, cross-server netplay, achievements, shared saves.** The
  richer social/real-time layer, once the trust and transport fabric is proven.

---

## 11. Hard problems & open questions

An honest accounting. Some have proposed mitigations; some are genuinely open.

**Provenance vs. privacy.** Anonymous fingerprints still leak the *existence* of
servers you aren't friends with and their pseudonymous stats. Stability (needed
for dedupe) is in tension with unlinkability. *Mitigation:* k-anonymity — only
include a source's per-game numbers once aggregated over enough users; coarsen
small counts. Rotating fingerprints would help privacy but break dedupe — likely
not worth it.

**Sybil / data inflation.** Mesh-wide + low-trust aggregates are gameable: a
malicious server can fabricate millions of hours or fake players. *Mitigations:*
(a) per-source caps; (b) trust-weighting by path/distance; (c) **always show the
per-source / per-hop breakdown** so inflation is visible; (d) the per-friend
**inbound consume toggle** (P5) — the operator's unilateral kill switch for a
source feeding garbage. Note (d) is reactive and manual by design.

**Trust transitivity.** You trust B to have honestly aggregated *its* deeper
friends; B could fabricate the sources it relays. Signing proves B didn't forge a
*known* key's claim, but B can still invent unknown origins. This is inherent to a
delegated-trust friends model — the mitigations above bound the damage; they don't
eliminate it. (This is arguably the right trade for a *friends* network: you chose
to trust B.)

**Same user across servers.** `PlayHistory` is keyed by *local* user. If one
person plays on both A and B, they appear as two distinct mesh users; their
playtime is not unified and their leaderboard presence is split. Unifying
cross-server identity (a self-sovereign user key? a both-servers-signed identity
claim?) is genuinely hard. **Out of scope for early phases — flagged, not solved.**

**Legality of relay & caching.** Relaying and especially *caching* ROMs means a
server transmits/stores material it may not have rights to. This is why caching is
an explicit per-operator configuration (§5.4/§6), not a default — spanning
pass-through (no storage) to cache to library-ingest. The project already reasons
about operator trust for native binaries (`server/internal/cores/CORE_INTEGRITY.md`);
the same "operator's responsibility, give them the off-switches" posture applies.

**Bandwidth & open-relay abuse.** A relay pays for both legs of every transfer and
could be abused as free bandwidth. *Mitigations:* per-friend rate/volume limits,
caching to amortize, and the outbound share toggle.

**Consistency & freshness.** Aggregates are eventually-consistent and periodically
synced — leaderboards can be minutes/hours stale. Presence wants near-real-time
and is the hardest to keep fresh through gossip. TTLs and refresh cadences per
class.

**Loops, TTL & diamonds.** Hop caps + per-message seen-sets prevent infinite
propagation. Diamonds (multiple paths to one source) are handled by dedupe (§5.3)
but still waste some transfer.

**Key rotation / churn.** A server re-keying changes its fingerprint — breaking
dedupe history and existing friendships. Needs a signed key-rotation message
co-signed by the old key, propagated to friends.

**Protocol versioning.** Heterogeneous server versions across a mesh need
capability negotiation and forward-compatible payloads.

**Offline friends / partial mesh.** A relay being down means part of the mesh is
unreachable; the UI must degrade gracefully ("some friends unavailable") rather
than fail.

**Privacy regression risk.** A `private` user must be filtered *at the origin
before stamping*, never downstream — otherwise a single misbehaving relay leaks
them. This must be an invariant, with tests.

**Deliberate non-goal: reputation gossip.** We do **not** propagate "server B is
bad" across the mesh. Distrust is local and unilateral (the inbound toggle).
Network-wide reputation is a large, abusable system of its own and is explicitly
out of scope.

---

## 12. Non-goals (YAGNI)

- **Not** open / public federation or a global discovery directory. Friends only,
  mutually authenticated.
- **Not** anonymized onion routing — we hide friend-of-friend *addresses*, not the
  fact that data was relayed.
- **Not** a global content DHT — discovery is bounded by your reach, not the world.
- **Not** unified cross-server user identity in early phases (flagged in §11).
- **Not** network-wide reputation/moderation — local consume toggles only.
- **Not** strong consistency — aggregates are eventually-consistent by design.

---

## 13. Glossary

- **Friend** — a server you have mutually, cryptographically paired with. The only
  kind of server you ever connect to.
- **Reach** — how far across the mesh a given data class travels, in hops.
- **Hop count** — graph distance from the data's current holder to its origin.
- **Origin fingerprint** — a stable, anonymous identifier for the server that
  originated a datum (derived from its identity key). Recognizable, not
  contactable.
- **Relay** — an intermediate friend that re-serves data on your behalf: an active
  byte proxy for downloads, a stored merged aggregate for stats.
- **Gossip-aggregate** — the federation pattern for small additive data (merge
  hop-by-hop, mesh-wide).
- **Locate-and-fetch** — the federation pattern for large blobs (discover, then
  relay-fetch, short reach).
- **Inbound / outbound policy** — per-friend, per-data-class consent: what you
  accept from, and what you share with, a given friend.
