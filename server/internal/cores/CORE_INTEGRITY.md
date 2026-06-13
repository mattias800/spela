# Core download integrity & trust model (#1315)

libretro cores are **native executable libraries** (`.so`/`.dylib`/`.dll`)
that the *player app* downloads from this server and loads into its own
process. A malicious core is arbitrary code execution on the player device,
so the integrity of the core-distribution path is security-critical.

## What the server does

`poller.go` keeps `CorePlatformBinary` rows in sync with libretro buildbot
**nightly** builds:

1. Fetches `https://buildbot.libretro.com/nightly/...` over a
   `safehttp.NewStrictHTTPSClient` — TLS-authenticated, private-IP-blocked,
   and **no redirect may downgrade to http** (a downgrade would drop server
   authentication on a binary we are about to distribute).
2. Refuses any non-`https://` fetch URL in production (the `http` test hook
   is `BaseURLOverride`, used only by `poller_test.go`).
3. Extracts exactly the expected core filename from the zip (size-capped,
   no zip-slip), records its SHA-256, and audits every change via a
   `SystemEventCoreUpdated` event (old → new hash).

## The residual trust boundary — read before "fixing" this

The server **cannot** detect a *compromised-but-TLS-valid* upstream. The
hash it records is computed over the bytes it just downloaded; it is used
for change-detection and audit, **not** verified against an independent
trusted reference. There is no such reference to verify against:

- libretro buildbot nightlies **change continuously** and ship **no
  detached signature and no stable published checksum**. A static in-repo
  expected-hash manifest (the approach the BIOS registry uses in #1124, for
  *immutable* BIOS files) would be wrong within a day, breaking auto-update.

So the design is deliberately **trust-on-fetch over authenticated TLS**, the
same trust model RetroArch itself uses for buildbot cores. Transport is
hardened; upstream compromise is an accepted, documented risk.

## Options for operators who need stronger guarantees

- **Disable auto-update:** set `SPELA_DISABLE_CORE_POLLER=1`. Cores are then
  only changed by the explicit admin upload path.
- **Pin a build:** set a core's `CustomDownloadURL` (the poller skips rows
  that have one) to a specific, vetted artifact you host and trust.

## If upstream gains signing

If libretro ever publishes detached signatures or a stable signed checksum
index for nightlies, add verification in `pollOne` between `hashAndSize` and
the write-through, and reject on mismatch. That is the only change that would
turn this from trust-on-fetch into verified integrity.
