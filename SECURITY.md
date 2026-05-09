# Security

This document is the operational security baseline for **administrators
self-hosting Spela**. It explains the threat model the backend was
designed against, the protections that are in place today, the known
gaps that have been audited and fixed, and the deployment settings that
turn a default install into a hardened one.

> **Last full audit:** 2026-05-08 — every Go file under `server/` was
> reviewed across authentication, authorization, file handling, SQL/input
> validation, crypto/transport, and patcher safety.
>
> **Status:** All 20 audit findings (1 critical, 3 high, 11 medium,
> 5 low) have been addressed; see _Resolved findings_ for issue and
> commit references. The audit will be repeated and this document
> refreshed at every release with significant security-relevant
> changes.

## Threat model

Spela is designed to be **self-hosted** by a single operator (or a small
trusted team), serving game-emulation features to a private group of
users — typically family, friends, or a small community. The audit
assumed:

- The server is reachable from the public Internet, behind a reverse
  proxy with TLS (e.g. Caddy, nginx, Traefik).
- New-account registration may be open or closed at the operator's
  choice.
- Authenticated users are not assumed trustworthy. They may try to read
  other users' data, escalate privileges, or attack the host.
- Administrators are assumed mostly trustworthy but can be phished or
  have their accounts compromised; defense-in-depth against rogue
  admins is a goal where reasonable.
- The deployment host is not actively compromised at the OS level.
  Spela cannot defend the host from itself.

**Out of scope** for the audit: DoS protection beyond basic rate
limiting; defending against a malicious operator with shell access; and
attacks requiring physical access to the database file.

## Reporting a vulnerability

Please **do not file a public issue** for an unpatched vulnerability.
Email the maintainer at the address in the project README, or use
GitHub's [private security advisory](https://github.com/mattias800/spela/security/advisories/new)
flow. Coordinated disclosure is preferred. Public issues are appropriate
once a fix is merged or for low-severity hardening recommendations.

## What the backend does well

These are the protections in place today; admins reading this should
know they exist before scrutinizing the changelog.

### Authentication & session

- **JWT secret enforcement**: at startup the server refuses to run with
  the default placeholder or a secret shorter than 32 characters
  (`cmd/server/main.go`).
- **bcrypt cost 12** with a startup-generated dummy hash so that the
  timing for a non-existent username matches a real password check
  (`internal/auth/auth.go`, `internal/api/auth_handler.go`).
- **Login lockout** escalates 15 → 30 → 60 → 120 minutes per failure
  tier and is per-account (hashed username) so the lockout table never
  stores raw usernames. A successful login fully clears the lockout
  row, so a slow attacker can't keep the escalation tier armed across
  days.
- **Common-password blocklist** rejects ~50 of the most-leaked / most-
  sprayed passwords on registration and password change. Static and
  offline — no DNS leak.
- **Refresh tokens** are 32 random bytes from `crypto/rand`, stored
  only as SHA-256 hashes, and use **token families with replay
  detection** — presenting a consumed token revokes the entire family.
- **Token version** on the user row is checked on every request, so
  role change, password change, email change, or `disabled=true`
  immediately invalidates all outstanding access tokens.
- **JWT alg confusion is blocked**: only HMAC signing methods are
  accepted; `alg: none` and asymmetric-key confusion are rejected.
- **Logout** blacklists the access token (SHA-256 hash, indexed) and
  deletes every refresh token belonging to the user. The blacklist
  also catches tokens delivered via the legacy `?token=` query param
  on download routes.
- **`?token=` query fallback** is restricted to a small allowlist of
  download / WebSocket / asset routes where browsers genuinely cannot
  set Authorization headers. Every JSON-API endpoint requires a Bearer
  header, keeping access tokens out of reverse-proxy logs.

### Authorization

- **Owner > admin > user** role hierarchy. Only owner can mutate or
  delete other admins, change another admin's password, or change
  another admin's email — a single compromised admin account cannot
  lock the rest out.
- **Per-route admin gate** on every `/api/admin/*` endpoint plus
  defense-in-depth admin checks inside handlers that traverse
  privileged data.
- **Per-user file ACLs** on save screenshots and shared session saves;
  download endpoints re-validate ownership before streaming bytes.
- **Profile visibility** (`public` default, `private` opt-in) gates
  exposure of play time, recent games, top-played games, currentGame,
  and online status on the public profile endpoint.
- **Block model** filters search, profile, and invite endpoints
  symmetrically — neither the blocker nor the blocked party shows up
  in the other's results.
- **Shared-session turn enforcement** on every save-upload handler
  (slot, auto-save, dir-bundle). The session owner cannot overwrite
  saves while a co-op partner holds the turn.

### Crypto & transport

- **AES-GCM** for at-rest encryption of admin secrets (scraper API
  keys, RA tokens). Random per-message nonce; key sizes validated to
  16/24/32 bytes.
- **Separate encryption key required in release mode**
  (`SPELA_ENCRYPTION_KEY`); falls back to JWT-secret-derived key only
  in development.
- **Outbound HTTP**: every scraper client pins its base URL to a known
  constant. The hardened `safehttp` client adds private-IP rejection
  on every redirect hop, scheme allowlisting (http/https only),
  response-body size capping, and Content-Type gating for image
  fetches. No `InsecureSkipVerify` anywhere in the tree.
- **Trusted proxies** restricted to RFC 1918 + loopback so admins
  behind public CDNs don't get spoofable `X-Forwarded-For`.
- **pprof bound to `127.0.0.1` only** — heap dumps and stack traces
  cannot be reached from the network.
- **Security headers**: COOP same-origin, COEP credentialless,
  X-Content-Type-Options nosniff, X-Frame-Options SAMEORIGIN, HSTS,
  Referrer-Policy strict-origin-when-cross-origin, Permissions-Policy.
- **CSP** scoped for EmulatorJS needs but tight everywhere else.
- **CORS** defaults to same-origin (no headers sent); explicit origins
  honored; `*` automatically disables `AllowCredentials` on HTTP.
- **WebSocket origin checks** refuse cross-origin upgrades that
  authenticate via `?token=` even under a `*` policy — the wildcard
  is treated as "no credentials" only.
- **WebSocket recipient filter** delivers private events (invites,
  shared-session turn changes) only to the connected clients those
  events are addressed to, instead of broadcasting them to every
  authenticated WS subscriber.

### Input validation

- **Body size limit** of 1 MB on all JSON endpoints (multipart excluded
  and uses its own limits).
- **GORM placeholders** used universally for user input. Every numeric
  path parameter carries a `pattern:"^[0-9]+$"` schema constraint
  enforced at the API edge by huma — non-numeric IDs are rejected with
  422 before any DB call. A unit test in the `api` package fails the
  build if a future endpoint forgets the pattern.
- **`escapeLikePattern`** is used consistently with `ESCAPE '\'` in
  LIKE queries.
- **Admin settings PUT** enforces a strict allowlist of keys; secret
  values cannot be overwritten with the masked placeholder.
- **`os/exec`** is used in only one place (`xdelta3`), with argv form
  and server-generated temp filenames — no shell, no command
  injection. The child process runs under a 30-second context timeout
  to bound damage from any future xdelta3 parser CVE.
- **SSRF guard** (`internal/safehttp`) covers RFC 1918, 169.254/16
  cloud metadata, IPv6 ULA/link-local, IPv4-mapped IPv6, and applies
  to user avatars, admin "set hero art", and every scraper image
  fetch. CheckRedirect re-validates each hop's IP so a public host
  cannot bounce us to a private one.

### File handling

- **Containment checks** on every write/read into save/image/BIOS
  dirs via `filepath.Abs` + prefix verification.
- **Symlink-aware path resolution** in `ImageHandler.ServeImage` via
  `filepath.EvalSymlinks`.
- **`ResolveGamePath`** rejects empty / absolute / `..`-prefixed
  paths and verifies the resolved path stays inside the game dir
  before stat. Any caller that ever forgets to follow up with
  `ValidateROMPath` is still safe.
- **Cue/GDI companion paths** are bounded to the disc's directory:
  the parser refuses absolute paths, refuses `..` segments, and
  re-resolves the joined path against the disc dir. Defense in
  depth: the tar/zip stream writer refuses non-regular files, so a
  planted symlink under the disc dir cannot escape via re-resolution.
- **SPA static fallback** containment-checks every resolved path
  against the absolute frontend dir before serving — the previous
  Stat-vs-FileServer two-path inconsistency is gone.
- **Filename sanitization** strips path separators with
  `filepath.Base`.
- **Save/BIOS dirs** use `0700` permissions; image/core dirs `0755`.
- **ZIP extraction**: BIOS bundle extractor explicitly checks
  `filepath.IsAbs`, `..` substrings, and absolute prefix; skips
  symlinks. File count cap (10 000), declared total uncompressed
  size cap (50 GB), and per-extraction `io.LimitReader` budget
  that decrements as extraction progresses.
- **Patcher**:
  - 30-second `xdelta3` timeout (no sandbox yet — see _Limitations_).
  - Output bounded to 256 MB for IPS / IPS32 / UPS / BPS.
  - Input bounded to 256 MB at the rom-hack endpoint so the in-memory
    peak matches the patcher's contract.
  - VLQ decoder returns `(value, n, ok)`; truncated input surfaces a
    clean parser error instead of a panic recovered into 500.
- **BIOS uploads**: MD5 mismatch deletes the upload and returns 400
  with the expected hash — invalid bytes never sit on disk waiting
  for a permissive core to load them.
- **BIOS auto-download**:
  - **Default OFF**. Operators opt in explicitly via the
    `bios_auto_download` server setting.
  - Refuses to fetch any registry entry that lacks an MD5 checksum
    (the upstream is a third-party GitHub account, not Spela's own
    source tree, so an empty checksum is untrustworthy).
- **Save screenshot ACL**: `/api/images/save-screenshots/` is
  auth-gated with ownership check and full token-blacklist /
  disabled / token-version re-validation.

### Setup safety

- The first-run `/api/auth/setup` endpoint persists a
  `setup_completed` row to `server_settings` after the owner is
  created. Subsequent setup calls are refused regardless of users-
  table state, so an out-of-band table truncation cannot re-open the
  endpoint to whoever races to it first.

## Resolved findings

The 2026-05-08 audit surfaced 20 issues; every one is fixed in the
2026-05 security PR. Each entry below records the original severity,
title, GitHub issue, and the fixing commit.

### Critical

| # | Finding | Issue | Fix |
|---|---|---|---|
| 1 | GORM string-WHERE SQL injection on user-reachable endpoints | [#1115](https://github.com/mattias800/spela/issues/1115) | `5de02860` |

### High

| # | Finding | Issue | Fix |
|---|---|---|---|
| 2 | Cue/GDI companion-file path traversal | [#1116](https://github.com/mattias800/spela/issues/1116) | `3c0e07e9` |
| 3 | `?token=` query fallback applied to every protected route | [#1117](https://github.com/mattias800/spela/issues/1117) | `58e8ed26` |
| 4 | SPA static fallback file server lacked containment check | [#1118](https://github.com/mattias800/spela/issues/1118) | `250abe4e` |

### Medium

| # | Finding | Issue | Fix |
|---|---|---|---|
| 5 | WebSocket hub broadcast every event to every client | [#1119](https://github.com/mattias800/spela/issues/1119) | `4a78b661` |
| 6 | SSRF via admin "set hero art" + unvalidated scraper image fetch | [#1120](https://github.com/mattias800/spela/issues/1120) | `4e133c36` |
| 7 | Public profile / search exposed activity to all authenticated users | [#1121](https://github.com/mattias800/spela/issues/1121) | `ffad90a6` |
| 8 | Non-owner admins could demote/disable/delete other admins | [#1122](https://github.com/mattias800/spela/issues/1122) | `1df39959` |
| 9 | Admin email change had no uniqueness or owner protection | [#1123](https://github.com/mattias800/spela/issues/1123) | `1df39959` |
| 10 | BIOS upload mismatch + auto-downloader trust on third-party GitHub | [#1124](https://github.com/mattias800/spela/issues/1124) | `15efb126` |
| 11 | `ResolveGamePath` did not bound resolved path; rom-hack skipped validation | [#1125](https://github.com/mattias800/spela/issues/1125) | `57b256d2` |
| 12 | WebSocket Origin `*` wildcard allowed credentialed cross-origin | [#1126](https://github.com/mattias800/spela/issues/1126) | `7ae29615` |
| 13 | Numeric path-param IDs accepted as strings without `pattern` validation | [#1127](https://github.com/mattias800/spela/issues/1127) | `03e0baad` |
| 14 | Slot-save upload skipped shared-session turn check | [#1128](https://github.com/mattias800/spela/issues/1128) | `483d802f` |
| 15 | `xdelta3` invoked without sandbox or timeout | [#1129](https://github.com/mattias800/spela/issues/1129) | `228ee5e4` |

### Low

| # | Finding | Issue | Fix |
|---|---|---|---|
| 16 | Re-setup possible if `users` table was truncated out-of-band | [#1130](https://github.com/mattias800/spela/issues/1130) | `bf5dd16f` |
| 17 | No common-password blocklist; lockout escalation never decayed on success | [#1131](https://github.com/mattias800/spela/issues/1131) | `4bc1783b` |
| 18 | Username/email enumeration on registration via 409 disambiguation | [#1132](https://github.com/mattias800/spela/issues/1132) | `2e89089a` |
| 19 | Email change in `PUT /api/user/profile` did not bump `TokenVersion` | [#1133](https://github.com/mattias800/spela/issues/1133) | `84bd1c70` |
| 20 | Patcher in-memory ROM cap; BPS VLQ decoder lacked bounds check | [#1134](https://github.com/mattias800/spela/issues/1134) | `6ca7b551` |

## Limitations & known residual risk

The audit found no further defects above the "low" severity bar that
had not been fixed at the time of the PR. Some accepted residual risks
worth noting up front:

- **`xdelta3` runs under a timeout but not a sandbox.** A
  memory-corruption CVE in the host's installed `xdelta3` binary
  would still execute as the spela server user. The long-term fix is
  an in-process Go VCDIFF implementation; the short-term mitigation
  is the 30-second timeout (#1129) and running spela under a
  least-privilege OS account.
- **Open registration leaks email existence via timing.** With
  registration enabled, an attacker can in principle distinguish a
  valid email from an invalid one because the password-hash branch
  is taken on conflict. Self-hosted instances that care about this
  should disable open registration and provision users via the admin
  panel.
- **Email-based password reset is not implemented.** If/when it
  ships, the email-change → TokenVersion bump (#1133) becomes the
  hardening that stops a transient password thief from pivoting
  recovery to their own address.
- **Activity feed events are broadcast to every WS subscriber today.**
  Per-user filtering will land alongside the wider profile-visibility
  surface; for now, hide your activity by setting
  `profile_visibility = "private"` (which gates the public profile
  endpoint).

## Hardening checklist for self-hosting admins

The settings below give you a hardened baseline beyond Spela's
defaults. Items marked **Required** are enforced at startup; items
marked **Strongly recommended** are not, but every non-trivial
deployment should set them.

### Required (the server refuses to start without them)

- `SPELA_JWT_SECRET` — at least 32 random characters. Refuses to run
  on `change-me-in-production` or any value shorter than 32 chars.
  Generate with `openssl rand -base64 48`.
- In release mode (`GIN_MODE=release`): `SPELA_ENCRYPTION_KEY` —
  exactly 16, 24, or 32 bytes. Use a different value from the JWT
  secret so you can rotate one without re-encrypting stored data.
  Generate with `openssl rand 32 | base64`.

### Strongly recommended

- **Run behind a reverse proxy with TLS**. Spela emits HSTS but the
  header is only honored on HTTPS responses.
- **Set `SPELA_CORS_ORIGINS`** to your frontend's exact origin list.
  Avoid `*` — it disables `AllowCredentials` for HTTP and (separately)
  weakens WebSocket origin checking. Even after #1126's tightening,
  enumerating origins is the safer choice.
- **Set `SPELA_WS_ORIGINS`** explicitly if you need it different from
  CORS. By default it inherits CORS origins, which is the safer
  default.
- **Don't expose `/api/auth/setup` to the public** until the first
  owner has been created. Once setup completes, the server persists a
  `setup_completed` marker that prevents re-bootstrap (#1130) — but
  the cleanest posture is to firewall the endpoint until you've
  created your owner account.
- **Disable open registration** if your user list is closed. Admin →
  Settings → "Allow new registrations" off (or set
  `registration_enabled=false` in `server_settings`).
- **Strip `?token=` from your reverse-proxy access logs** even though
  the new fallback is restricted (#1117), to reduce token-leakage
  exposure on the routes that still legitimately use it. nginx
  example:
  ```nginx
  log_format spela_safe '$remote_addr - $remote_user [$time_local] '
                        '"$request_method $uri $server_protocol" $status $body_bytes_sent';
  access_log /var/log/nginx/spela.access.log spela_safe;
  ```
  This avoids capturing the query string entirely.
- **Back up `spela.db`** with care: it contains bcrypt password
  hashes, refresh-token hashes, and AES-GCM ciphertext of admin
  secrets. The encryption key is **not** in the DB — losing it means
  the encrypted admin secrets become unrecoverable.
- **BIOS auto-download is OFF by default** (#1124). If you enable it,
  understand that the upstream is a third-party GitHub account
  (`Abdess/retrobios`); a compromised commit there could ship
  malicious BIOS bytes that libretro cores will execute. Prefer
  uploading BIOS files manually and verify their MD5 against a
  source you trust.
- **Run with the smallest privileges that work**. Non-root, dedicated
  user; only the configured directories writable. The `0700` save
  and BIOS dirs assume the spela user is the only one reading them.

### Optional

- `SPELA_MAX_SAVE_UPLOAD_MB` (default 256): per-upload save-state
  cap.
- `SPELA_MAX_SAVE_STORAGE_MB` (default 1024): per-user storage
  quota.
- `SPELA_CHALLENGE_RATE_LIMIT_SEC` (default 30): minimum seconds
  between challenge attempt submissions.

### Network exposure summary

| Port / Path | Exposure | Notes |
|---|---|---|
| `:8080` (default) | Public via reverse proxy | The main API and SPA. |
| `127.0.0.1:6060` | Localhost only | pprof. Never expose. |
| `/api/test/reset` | Only when `SPELA_TEST_MODE=true` | Don't enable in production. |
| `/api/openapi`, `/api/docs` | Public, unauthenticated | OpenAPI spec + Swagger UI. Read-only metadata about the route surface. |
| `/api/auth/setup-status` | Public, unauthenticated | Returns `needsSetup` and `gameCount`. `needsSetup` is true only when there are no users AND the `setup_completed` marker is absent (#1130). |

## Audit methodology

The audit was performed by reading all Go files under `server/` and
running targeted `grep` patterns for risky constructs (`Raw`, `Exec`,
dynamic `Order`, `os/exec`, `math/rand`, `InsecureSkipVerify`, query
construction in handlers, multipart filename handling, and so on).
Findings are reproducible from the codebase as it stood at commit
`64a4e6ab` (master tip on 2026-05-08).

The fix PR includes:

- 24 commits, one per finding plus targeted test fixups.
- New regression-style tests in the `api`, `scanner`, `storage`,
  `safehttp`, `bios`, `patcher`, and `websocket` packages covering
  each fix.
- A structural lint (`server/internal/api/path_param_pattern_test.go`)
  that runs in CI and fails any future PR that adds a `path:"..."`
  tag without a `pattern:` constraint, preventing regression of the
  SQLi-class issues.

When future audits surface findings, list them at the top of this
file and link the fix commit on resolution.
