# Security

This document is for self-hosting administrators. It explains Spela's threat
model, what the backend does well, what known issues exist (with links to
tracking issues), and how to harden a deployment.

> **Last full audit:** 2026-05-08 — every Go file under `server/` was
> reviewed across authentication, authorization, file handling, SQL/input
> validation, and crypto/transport. The findings below reflect that audit;
> open issues are tracked on GitHub and SECURITY.md is updated as they are
> resolved.

## Threat model

Spela is designed to be **self-hosted** by a single operator (or a small
trusted team), serving game-emulation features to a private group of users —
typically family, friends, or a small community. The audit assumed:

- The server is reachable from the public Internet, behind a reverse
  proxy with TLS (e.g. Caddy, nginx, Traefik).
- New-account registration may be open or closed at the operator's choice.
- Authenticated users are not assumed trustworthy. They may try to read
  other users' data, escalate privileges, or attack the host.
- Administrators are assumed mostly trustworthy but can be phished or have
  their accounts compromised; defense-in-depth against rogue admins is a
  goal where reasonable.
- The deployment host is not actively compromised at the OS level. Spela
  cannot defend the host from itself.

Out of scope: DoS protection beyond basic rate limiting; defending against a
malicious operator with shell access; and attacks requiring physical access
to the database file.

## Reporting a vulnerability

Please **do not file a public issue** for an unpatched vulnerability. Email
the maintainer at the address in the project README, or use GitHub's
[private security advisory](https://github.com/mattias800/spela/security/advisories/new)
flow. Coordinated disclosure is preferred. Public issues are appropriate
once a fix is merged or for low-severity hardening recommendations.

## What the backend does well

These are the protections in place today; admins reading this should know
they exist before scrutinizing the findings list.

### Authentication & session

- **JWT secret is enforced**: at startup the server refuses to run with
  the default placeholder or a secret shorter than 32 characters
  (`cmd/server/main.go:86-95`).
- **bcrypt cost 12** with a startup-generated dummy hash so that timing
  for a non-existent username matches a real password check
  (`internal/auth/auth.go:38`, `internal/api/auth_handler.go:43-55`).
- **Login lockout** escalates 15 → 30 → 60 → 120 minutes per failure tier
  and is per-account (hashed username) to prevent the lockout table from
  storing raw usernames (`internal/api/auth_handler.go:66-127`).
- **Refresh tokens** are 32 random bytes from `crypto/rand`, stored only
  as SHA-256 hashes, and use **token families with replay detection** —
  presenting a consumed token revokes the entire family
  (`internal/api/huma_auth_handlers.go:498-583`).
- **Token version** on the user row is checked on every request, so role
  change, password change, or `disabled=true` immediately invalidates all
  outstanding access tokens (`internal/api/middleware.go:111-145`).
- **JWT alg confusion is blocked**: only HMAC signing methods are
  accepted; `alg: none` and asymmetric-key confusion are rejected
  (`internal/auth/auth.go:87-89`).
- **Logout** blacklists the access token (SHA-256 hash, indexed) and
  deletes every refresh token belonging to the user
  (`internal/api/huma_auth_handlers.go:692-714`).

### Crypto & transport

- **AES-GCM** for at-rest encryption of admin secrets (scraper API keys,
  RA tokens). Random per-message nonce; key sizes validated to
  16/24/32 bytes (`internal/auth/encrypt.go`).
- **Separate encryption key required in release mode**
  (`SPELA_ENCRYPTION_KEY`); falls back to JWT-secret-derived key only in
  development (`cmd/server/main.go:97-113`).
- **Outbound HTTP**: every scraper client pins its base URL to a known
  constant (igdb.com, libretro.com, steamgriddb.com, pouet.net). No
  `InsecureSkipVerify` anywhere in the tree.
- **Trusted proxies** restricted to RFC 1918 + loopback so admins behind
  public CDNs don't get spoofable `X-Forwarded-For`
  (`internal/api/router.go:55`).
- **pprof bound to `127.0.0.1` only** — heap dumps and stack traces
  cannot be reached from the network (`cmd/server/main.go:37-43`).
- **Security headers**: COOP same-origin, COEP credentialless,
  X-Content-Type-Options nosniff, X-Frame-Options SAMEORIGIN, HSTS,
  Referrer-Policy strict-origin-when-cross-origin, Permissions-Policy
  (`internal/api/router.go:58-77`).
- **CSP** scoped for EmulatorJS needs but tight everywhere else.
- **CORS** defaults to same-origin (no headers sent); explicit origins
  honored; `*` automatically disables `AllowCredentials`.

### File handling

- **Containment checks** on every write/read into save/image/BIOS dirs
  via `filepath.Abs` + prefix verification
  (`internal/storage/storage.go`).
- **Symlink-aware path resolution** in `ImageHandler.ServeImage` via
  `filepath.EvalSymlinks` (`internal/api/router.go:503-519`).
- **Filename sanitization** strips path separators with `filepath.Base`.
- **Save/BIOS dirs** use `0700` permissions; image/core dirs `0755`.
- **ROM paths** validated against allowed game directories with
  symlink resolution (`internal/storage/storage.go:754-779`).
- **ZIP extraction**: BIOS bundle extractor explicitly checks
  `filepath.IsAbs`, `..` substrings, and absolute prefix; skips
  symlinks (`internal/bios/downloader.go:380-456`).
- **Zip-bomb protection**: file count cap (10 000), declared total
  uncompressed size cap (50 GB), and per-extraction `io.LimitReader`
  budget that decrements as extraction progresses.
- **Patcher output bounded** to 256 MB for IPS / IPS32 / UPS / BPS.
- **Save screenshot ACL**: `/api/images/save-screenshots/` is auth-gated
  with ownership check and full token-blacklist/disabled/token-version
  re-validation (`internal/api/router.go:521-600`).

### Input validation

- **Body size limit** of 1 MB on all JSON endpoints (multipart excluded
  and uses its own limits) (`internal/api/middleware.go:170-181`).
- **GORM placeholders** used universally for user input — no
  string-concatenated WHERE clauses against user-controlled values were
  found (with the structural exception flagged in the findings).
- **`escapeLikePattern`** is used consistently with `ESCAPE '\'` in
  LIKE queries.
- **Admin settings PUT** enforces a strict allowlist of keys; secret
  values cannot be overwritten with the masked placeholder
  (`internal/api/admin_handler_settings.go:14-23`).
- **`os/exec`** is used in only one place (`xdelta3`), with argv form
  and server-generated temp filenames — no shell, no command injection.
- **SSRF guard** for user-set avatar URLs via a robust `isPrivateURL`
  covering RFC 1918, 169.254/16 cloud metadata, IPv6 ULA/link-local, and
  IPv4-mapped IPv6 (`internal/api/user_handler.go:170-221`).

## Open findings

Each finding links to a GitHub issue. Mark as **Resolved** when the
referenced PR is merged.

### Critical

| # | Finding | Issue |
|---|---|---|
| 1 | GORM string-WHERE SQL injection on user-reachable endpoints (any authenticated user can perform blind boolean SQLi to exfiltrate password hashes / secrets) | [#1115](https://github.com/mattias800/spela/issues/1115) |

### High

| # | Finding | Issue |
|---|---|---|
| 2 | Cue/GDI companion-file path traversal — malicious `.cue` lets a download include arbitrary host files in the response tar | [#1116](https://github.com/mattias800/spela/issues/1116) |
| 3 | `?token=` query fallback applies to every protected route — JWTs leak into proxy logs and aren't blacklisted on logout-via-query | [#1117](https://github.com/mattias800/spela/issues/1117) |
| 4 | SPA static fallback file server lacks containment check (defense-in-depth — relies on Gin's URL normalization today) | [#1118](https://github.com/mattias800/spela/issues/1118) |

### Medium

| # | Finding | Issue |
|---|---|---|
| 5 | WebSocket hub broadcasts every event to every connected client — cross-tenant leak of invites, turn changes, activity feed | [#1119](https://github.com/mattias800/spela/issues/1119) |
| 6 | SSRF via admin "set hero art" URL fetch + unvalidated scraper image downloads | [#1120](https://github.com/mattias800/spela/issues/1120) |
| 7 | Public profile endpoint exposes activity/play data to all authenticated users; no privacy or block model | [#1121](https://github.com/mattias800/spela/issues/1121) |
| 8 | Non-owner admins can demote, disable, and hard-delete other admins | [#1122](https://github.com/mattias800/spela/issues/1122) |
| 9 | Admin email change has no uniqueness check or owner protection | [#1123](https://github.com/mattias800/spela/issues/1123) |
| 10 | BIOS upload kept on disk after MD5 mismatch + auto-downloader trusts third-party GitHub repo with weak hashing | [#1124](https://github.com/mattias800/spela/issues/1124) |
| 11 | `ResolveGamePath` does not bound resolved path to game dirs; rom-hack endpoint skips validation | [#1125](https://github.com/mattias800/spela/issues/1125) |
| 12 | WebSocket Origin `*` wildcard + `?token=` permits authenticated cross-origin WS | [#1126](https://github.com/mattias800/spela/issues/1126) |
| 13 | Project-wide: numeric path-param IDs accepted as strings without `pattern` validation (structural cause of #1115) | [#1127](https://github.com/mattias800/spela/issues/1127) |
| 14 | Slot-save upload skips shared-session turn check | [#1128](https://github.com/mattias800/spela/issues/1128) |
| 15 | `xdelta3` invoked with attacker-controlled patch input, no sandboxing or timeout | [#1129](https://github.com/mattias800/spela/issues/1129) |

### Low

| # | Finding | Issue |
|---|---|---|
| 16 | Re-setup possible if `users` table is truncated out-of-band | [#1130](https://github.com/mattias800/spela/issues/1130) |
| 17 | Auth hardening: no breached-password / strength check; lockout escalation tier never decays on success | [#1131](https://github.com/mattias800/spela/issues/1131) |
| 18 | Username/email enumeration on registration via 409 disambiguation | [#1132](https://github.com/mattias800/spela/issues/1132) |
| 19 | Email change in `PUT /api/user/profile` does not bump `TokenVersion` | [#1133](https://github.com/mattias800/spela/issues/1133) |
| 20 | Patcher reads full base ROM into memory; BPS VLQ decoder lacks bounds check | [#1134](https://github.com/mattias800/spela/issues/1134) |

## Resolved findings

_None yet — this section will be populated as the issues above are
fixed. Each entry should record the issue number, the finding title, the
fixing PR / commit, and the date of the fix._

## Deployment guidance for self-hosting admins

The settings below give you a hardened baseline beyond Spela's defaults.

### Required (will refuse to start without them)

- `SPELA_JWT_SECRET` — at least 32 random characters. Refuses to run on
  `change-me-in-production` or any value shorter than 32 chars. Generate
  with `openssl rand -base64 48`.
- In release mode (`GIN_MODE=release`): `SPELA_ENCRYPTION_KEY` — exactly
  16, 24, or 32 bytes. Use a different value from the JWT secret so you
  can rotate one without re-encrypting stored data. Generate with
  `openssl rand 32 | base64`.

### Strongly recommended

- **Run behind a reverse proxy with TLS**. Spela emits HSTS but the
  header is only honored on HTTPS responses.
- **Set `SPELA_CORS_ORIGINS`** to your frontend's exact origin list.
  Avoid `*` — it disables `AllowCredentials` for HTTP and (separately)
  weakens WebSocket origin checking; see #1126.
- **Set `SPELA_WS_ORIGINS`** explicitly if you need it different from
  CORS. By default it inherits CORS origins, which is the safer default.
- **Don't expose `/api/auth/setup` to the public** until the first owner
  has been created. Once created, the endpoint refuses further setup —
  but see #1130 for the corner case that requires DB access to trigger.
- **Disable open registration** if your user list is closed. Admin →
  Settings → "Allow new registrations" off (or set `registration_enabled=false`
  in `server_settings`).
- **Strip `?token=` from your reverse-proxy access logs** to reduce
  token-leakage exposure. nginx example:
  ```nginx
  log_format spela_safe '$remote_addr - $remote_user [$time_local] '
                        '"$request_method $uri $server_protocol" $status $body_bytes_sent';
  access_log /var/log/nginx/spela.access.log spela_safe;
  ```
  This avoids capturing the query string entirely. See #1117.
- **Back up `spela.db`** with care: it contains bcrypt password hashes,
  refresh-token hashes, and AES-GCM ciphertext of admin secrets. The
  encryption key is **not** in the DB — losing it means the encrypted
  admin secrets become unrecoverable.
- **Disable BIOS auto-download** if you can supply BIOS files manually:
  Admin → BIOS → "Auto-download" off. The default-on setting fetches
  from a third-party GitHub account; see #1124.
- **Run with the smallest privileges that work**. Non-root, dedicated
  user; only the configured directories writable. The `0700` save and
  BIOS dirs assume the spela user is the only one reading them.

### Optional

- `SPELA_MAX_SAVE_UPLOAD_MB` (default 256): per-upload save-state cap.
- `SPELA_MAX_SAVE_STORAGE_MB` (default 1024): per-user storage quota.
- `SPELA_CHALLENGE_RATE_LIMIT_SEC` (default 30): minimum seconds
  between challenge attempt submissions.

### Network exposure summary

| Port / Path | Exposure | Notes |
|---|---|---|
| `:8080` (default) | Public via reverse proxy | The main API and SPA. |
| `127.0.0.1:6060` | Localhost only | pprof. Never expose. |
| `/api/test/reset` | Only when `SPELA_TEST_MODE=true` | Don't enable in production. |
| `/api/openapi`, `/api/docs` | Public, unauthenticated | OpenAPI spec + Swagger UI. Read-only metadata about the route surface. |
| `/api/auth/setup-status` | Public, unauthenticated | Leaks `needsSetup` and `gameCount`. |

## Audit methodology

The audit was performed by reading all Go files under `server/` and
running targeted `grep` patterns for risky constructs (`Raw`, `Exec`,
dynamic `Order`, `os/exec`, `math/rand`, `InsecureSkipVerify`, query
construction in handlers, multipart filename handling, and so on).
Findings are reproducible from the codebase as it stood at commit
`64a4e6ab` (master tip on 2026-05-08). When an issue is fixed, update
this file's "Open findings" table, move the entry to "Resolved
findings" with the fixing PR/commit and date, and close the linked
issue.
