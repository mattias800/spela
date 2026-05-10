# Security

This document is the operational security baseline for **administrators
self-hosting Spela**. It explains the threat model the backend was
designed against, the protections that are in place today, and the
deployment settings that turn a default install into a hardened one.

## Status at a glance

- **Current state:** verified end-to-end in May 2026; the entire Go
  backend was read and probed across authentication, authorization,
  file handling, SQL/input validation, crypto/transport, and patcher
  safety.
- **Cadence:** the review is repeated on every release with significant
  security-relevant changes; structural lints in CI prevent regressions
  in the highest-risk classes (numeric path-param validation, raw SQL
  construction, etc.).
- **Reporting a vulnerability:** see _Reporting_ below — please **do
  not** file a public issue for an unpatched vulnerability.

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

**Out of scope:** application-layer DoS protection (rate limiting
beyond login lockout — see _What's rate-limited_ below); defending
against a malicious operator with shell access; and attacks requiring
physical access to the database file.

## Reporting a vulnerability

Please **do not file a public issue** for an unpatched vulnerability.
Use GitHub's [private security advisory](https://github.com/mattias800/spela/security/advisories/new)
flow, or email the maintainer at the address in the project README.
Coordinated disclosure is preferred. Public issues are appropriate
once a fix is merged or for low-severity hardening recommendations.

## Current security posture

What's in place today. The bullet lists are dense by design — admins
who need depth can scan the headlines first, then read the rows that
matter for their deployment.

### Authentication & session

JWT with short-lived access tokens, hashed refresh tokens with replay
detection, and per-account login lockout that escalates on failure
without leaking usernames.

- **JWT secret enforcement** — at startup the server refuses to run
  with the default placeholder or a secret shorter than 32 characters.
- **bcrypt cost 12** with a startup-generated dummy hash so timing
  for a non-existent username matches a real password check.
- **Login lockout** escalates 15 → 30 → 60 → 120 minutes per failure
  tier and is per-account (hashed username) so the lockout table never
  stores raw usernames. A successful login fully clears the row, so a
  slow attacker can't keep the escalation tier armed across days.
- **Common-password blocklist** rejects ~50 of the most-leaked / most-
  sprayed passwords on registration and password change. Static and
  offline — no DNS leak.
- **Refresh tokens** are 32 random bytes from `crypto/rand`, stored
  only as SHA-256 hashes, and use **token families with replay
  detection** — presenting a consumed token revokes the entire family.
- **Token version** on the user row is checked on every request, so
  role change, password change, email change, or `disabled=true`
  immediately invalidates all outstanding access tokens.
- **JWT alg confusion is blocked** — only HMAC signing methods are
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

Owner > admin > user role hierarchy with admin self-defense, plus
per-user file ACLs and turn enforcement on shared sessions.

- **Owner > admin > user** role hierarchy. Only owner can mutate or
  delete other admins, change another admin's password, or change
  another admin's email — a single compromised admin account cannot
  lock the rest out.
- **Per-route admin gate** on every `/api/admin/*` endpoint plus
  defense-in-depth admin checks inside handlers that traverse
  privileged data.
- **Per-user file ACLs** on save screenshots and shared session saves;
  download endpoints re-validate ownership before streaming bytes.
- **Profile visibility** — each user can choose `public` (default) or
  `private` for their own profile. The setting gates the public
  profile endpoint's exposure of play time, recent games, top-played
  games, currentGame, and online status.
- **Block model** filters search, profile, and invite endpoints
  symmetrically — neither the blocker nor the blocked party shows up
  in the other's results.
- **Shared-session turn enforcement** on every save-upload handler
  (slot, auto-save, dir-bundle). The session owner cannot overwrite
  saves while a co-op partner holds the turn.

### Crypto & transport

AES-GCM at rest for admin secrets, TLS expected at the proxy, hardened
outbound HTTP, and security headers tightened across the surface.

- **AES-GCM** for at-rest encryption of admin secrets (scraper API
  keys, RA tokens). Random per-message nonce; key sizes validated to
  16/24/32 bytes.
- **Separate encryption key required in release mode**
  (`SPELA_ENCRYPTION_KEY`); falls back to JWT-secret-derived key only
  in development.
- **Outbound HTTP** — every scraper client pins its base URL to a
  known constant. The hardened `safehttp` client adds private-IP
  rejection on every redirect hop, scheme allowlisting (http/https
  only), response-body size capping, and Content-Type gating for
  image fetches. No `InsecureSkipVerify` anywhere in the tree.
- **SSRF guard** covers RFC 1918, 169.254/16 cloud metadata, IPv6
  ULA/link-local, IPv4-mapped IPv6, and applies to user avatars,
  admin "set hero art", and every scraper image fetch. CheckRedirect
  re-validates each hop's IP so a public host cannot bounce us to a
  private one.
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

GORM placeholders everywhere, schema-enforced numeric IDs, and a
1 MB JSON body cap; the few `os/exec` paths are argv-only with
timeouts.

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

### File handling

Containment checks on every disk path, symlink-aware resolution, ZIP
bomb caps, and patcher input/output bounds.

- **Containment checks** on every write/read into save/image/BIOS
  dirs via `filepath.Abs` + prefix verification.
- **Symlink-aware path resolution** in `ImageHandler.ServeImage` via
  `filepath.EvalSymlinks`.
- **`ResolveGamePath`** rejects empty / absolute / `..`-prefixed paths
  and verifies the resolved path stays inside the game dir before
  stat. Any caller that ever forgets to follow up with
  `ValidateROMPath` is still safe.
- **Cue/GDI companion paths** are bounded to the disc's directory:
  the parser refuses absolute paths, refuses `..` segments, and
  re-resolves the joined path against the disc dir. Defense in
  depth: the tar/zip stream writer refuses non-regular files, so a
  planted symlink under the disc dir cannot escape via re-resolution.
- **SPA static fallback** containment-checks every resolved path
  against the absolute frontend dir before serving.
- **Filename sanitization** strips path separators with
  `filepath.Base`.
- **Save/BIOS dirs** use `0700` permissions; image/core dirs `0755`.
- **ZIP extraction**: BIOS bundle extractor explicitly checks
  `filepath.IsAbs`, `..` substrings, and absolute prefix; skips
  symlinks. File count cap (10 000), declared total uncompressed
  size cap (50 GB), and per-extraction `io.LimitReader` budget that
  decrements as extraction progresses.
- **Patcher**: 30-second `xdelta3` timeout (no sandbox yet — see
  _Limitations_); output bounded to 256 MB for IPS / IPS32 / UPS /
  BPS; input bounded to 256 MB at the rom-hack endpoint so the
  in-memory peak matches the patcher's contract; VLQ decoder returns
  `(value, n, ok)` so truncated input surfaces a clean parser error
  instead of a panic recovered into 500.
- **BIOS uploads**: MD5 mismatch deletes the upload and returns 400
  with the expected hash — invalid bytes never sit on disk waiting
  for a permissive core to load them.
- **BIOS auto-download**: **default OFF**. Operators opt in
  explicitly via the `bios_auto_download` server setting. The
  downloader refuses to fetch any registry entry that lacks an MD5
  checksum (the upstream is a third-party GitHub account, not Spela's
  own source tree, so an empty checksum is untrustworthy).
- **Save screenshot ACL**: `/api/images/save-screenshots/` is
  auth-gated with ownership check and full token-blacklist /
  disabled / token-version re-validation.

### Setup safety

The first-run `/api/auth/setup` endpoint persists a `setup_completed`
row to `server_settings` after the owner is created. Subsequent setup
calls are refused regardless of the users-table state, so an
out-of-band table truncation cannot re-open the endpoint to whoever
races to it first.

### What's rate-limited

Spela does **not** ship general per-route or per-IP rate limiting —
that's expected to live at your reverse proxy. What the server
enforces itself:

- **Login lockout** — escalating per-account lockout after a small
  number of failures (see _Authentication & session_).
- **Challenge submissions** — `SPELA_CHALLENGE_RATE_LIMIT_SEC`
  (default 30 s) minimum between attempts per user.
- **Save-state uploads** — per-upload size cap and per-user storage
  quota (see _Optional environment variables_).

If your deployment is internet-facing, configure your reverse proxy's
rate-limit module on `/api/*` for general DoS protection and on
`/api/auth/login`, `/api/auth/register`, and `/api/auth/refresh` for
slow credential-attack mitigation.

## Backups & key management

Two artefacts are load-bearing:

1. **`spela.db`** — contains user rows, bcrypt password hashes, refresh-
   token hashes (SHA-256 of the random secret, not the secret itself),
   and AES-GCM ciphertext of admin scraper / RA credentials. Back this
   up like any other production database. The backup needs the same
   filesystem-level access controls as the live DB.

2. **`SPELA_ENCRYPTION_KEY`** — **not** stored in the DB. Losing it
   means the encrypted admin secrets in `spela.db` are unrecoverable.
   Store it in your secrets manager / `.env` / Caddy env-block, and
   keep an offline copy alongside (but separate from) the DB backup.

User-uploaded content (save states, save screenshots, BIOS files,
downloaded ROM cache) lives on the filesystem at the paths configured
on the server. Back these up as a regular file-tree backup; nothing
is encrypted at rest.

## Hardening checklist for self-hosting admins

### Minimum viable deployment

If you do nothing else from this section, do these four:

1. Set `SPELA_JWT_SECRET` to a fresh random ≥32-character value
   (`openssl rand -base64 48`).
2. Set `SPELA_ENCRYPTION_KEY` to a fresh random 16/24/32-byte value
   (`openssl rand 32 | base64`). Different from the JWT secret.
3. Run behind a reverse proxy with TLS — Spela emits HSTS but it's
   only honored on HTTPS.
4. If your user list is closed, disable open registration in
   Admin → Settings → "Allow new registrations".

Everything below is layered on top.

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
  weakens WebSocket origin checking. Even with the wildcard hardening
  in place, enumerating origins is the safer choice.
- **Set `SPELA_WS_ORIGINS`** explicitly if you need it different from
  CORS. By default it inherits CORS origins, which is the safer
  default.
- **Don't expose `/api/auth/setup` to the public** until the first
  owner has been created. Once setup completes, the server persists a
  `setup_completed` marker that prevents re-bootstrap — but the
  cleanest posture is to firewall the endpoint until you've created
  your owner account.
- **Disable open registration** if your user list is closed. Admin →
  Settings → "Allow new registrations" off (or set
  `registration_enabled=false` in `server_settings`).
- **Strip `?token=` from your reverse-proxy access logs** even though
  the fallback is restricted to download / asset / WebSocket routes,
  to reduce token-leakage exposure on the routes that still
  legitimately use it. nginx example:
  ```nginx
  log_format spela_safe '$remote_addr - $remote_user [$time_local] '
                        '"$request_method $uri $server_protocol" $status $body_bytes_sent';
  access_log /var/log/nginx/spela.access.log spela_safe;
  ```
  This avoids capturing the query string entirely.
- **BIOS auto-download is OFF by default**. If you enable it,
  understand that the upstream is a third-party GitHub account
  (`Abdess/retrobios`); a compromised commit there could ship
  malicious BIOS bytes that libretro cores will execute. Prefer
  uploading BIOS files manually and verify their MD5 against a
  source you trust.
- **Run with the smallest privileges that work**. Non-root, dedicated
  user; only the configured directories writable. The `0700` save
  and BIOS dirs assume the spela user is the only one reading them.
- **Configure proxy-level rate limiting** on `/api/*` (general DoS),
  and tighter limits on `/api/auth/login` / `/api/auth/register` /
  `/api/auth/refresh` (slow credential attacks). Spela's own
  application-layer limits are scoped to login lockout, challenge
  submissions, and save uploads — see _What's rate-limited_.

### Optional environment variables

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
| `/api/test/reset` | Only when `SPELA_TEST_MODE=true` | **Never enable in production** — wipes user-generated data. |
| `/api/openapi`, `/api/docs` | Public, unauthenticated | OpenAPI spec + Swagger UI. Read-only metadata about the route surface. |
| `/api/auth/setup-status` | Public, unauthenticated | Returns `needsSetup` and `gameCount`. `needsSetup` is true only when there are no users AND the `setup_completed` marker is absent. |

## Limitations & known residual risk

Things admins should know they're accepting:

- **`xdelta3` runs under a timeout but not a sandbox.** A
  memory-corruption CVE in the host's installed `xdelta3` binary
  would still execute as the spela server user. The long-term fix
  is an in-process Go VCDIFF implementation; the short-term
  mitigation is the 30-second timeout and running spela under a
  least-privilege OS account.
- **Open registration leaks email existence via timing.** With
  registration enabled, an attacker can in principle distinguish a
  valid email from an invalid one because the password-hash branch
  is taken on conflict. Self-hosted instances that care about this
  should disable open registration and provision users via the
  admin panel.
- **Email-based password reset is not implemented.** If/when it
  ships, the email-change → TokenVersion bump becomes the hardening
  that stops a transient password thief from pivoting recovery to
  their own address.
- **Activity feed events are broadcast to every WS subscriber
  today.** Per-user filtering will land alongside the wider
  profile-visibility surface; for now, hide your activity by setting
  your profile to `private` (which gates the public profile
  endpoint).

## How the codebase is reviewed

The Go backend under `server/` is reviewed end-to-end on every
release with significant security-relevant changes. Reviews target
the standard high-risk classes — authentication, authorization, file
handling, SQL/input validation, crypto/transport, patcher safety —
and follow up with `grep` patterns for risky constructs (`Raw`,
`Exec`, dynamic `Order`, `os/exec`, `math/rand`,
`InsecureSkipVerify`, query construction in handlers, multipart
filename handling).

CI carries structural lints that prevent regression of the highest-
risk classes:

- `server/internal/api/path_param_pattern_test.go` fails the build
  if any new endpoint adds a `path:"..."` tag without a `pattern:`
  schema constraint, preventing the SQLi-class issues the audit
  surfaced.
- The `safehttp` package owns SSRF defenses centrally, so handlers
  cannot accidentally bypass private-IP rejection by opening a
  fresh `http.Client`.

When future reviews surface findings, this document is updated to
reflect the post-fix posture rather than to enumerate the bug
history.
