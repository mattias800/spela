# Deploying Spela

This guide covers deploying Spela on a server for yourself and your friends. Spela is designed to be self-hosted -- everything runs on your own hardware with no external dependencies.

## What You'll Need

- A server (VPS, home server, NAS, Raspberry Pi, etc.) running Linux with Docker
- Your ROM files organized by console (see [ROM Organization](#rom-organization))
- A domain name (optional but recommended for HTTPS)

## Architecture Overview

A Spela deployment has two services:

| Service | Purpose | Port |
|---------|---------|------|
| **spela** | Go backend + React frontend in a single container | 8080 |
| **coturn** | TURN server for netplay NAT traversal (optional, for multiplayer) | 3478, 5349, 49152-49252 |

The **spela** container is the single entry point. It serves both the API and the frontend. You point your reverse proxy (nginx proxy manager, Traefik, Caddy, etc.) at port 8080.

## ROM Organization

Organize your ROMs in folders named by console abbreviation:

```
/data/spela/games/
  nes/
    Super Mario Bros.nes
    Zelda.nes
  snes/
    Chrono Trigger.sfc
    Final Fantasy VI.smc
  gb/
    Pokemon Red.gb
  gbc/
    Pokemon Crystal.gbc
  gba/
    Pokemon Emerald.gba
  n64/
    Mario Kart 64.z64
  nds/
    Pokemon HeartGold.nds
  gen/
    Sonic the Hedgehog.md
  sms/
    Alex Kidd.sms
  psx/
    Final Fantasy VII.bin
    Final Fantasy VII.cue
  psp/
    God of War.iso
  neogeo/
    Metal Slug.zip
  mame/
    pacman.zip
  pce/
    Bonk's Adventure.pce
  a26/
    Pitfall.a26
```

Spela auto-detects console and game title from the folder structure and file extensions. You can also use full console names (`genesis/`, `gameboy/`, etc.) -- the scanner is flexible.

## Quick Deploy with Docker Compose

### 1. Clone the repository

```bash
git clone https://github.com/mattias800/spela.git
cd spela
```

### 2. Run the setup wizard

```bash
./setup.sh > .env
```

The wizard prompts for your hostname, games path, BIOS path (optional), and IGDB API credentials (optional, for metadata scraping). JWT and TURN secrets are generated automatically. The resulting `.env` file is printed to stdout -- the redirect writes it to `.env`.

If you prefer to review the output first, run `./setup.sh` without the redirect, then copy-paste into `.env`. For a non-interactive flow with random secrets and placeholder paths you'll edit by hand, use `./generate-secrets.sh > .env` instead.

### 3. Deploy

```bash
docker compose -f docker-compose.qa.yml up -d --build
```

Spela is now running on port 8080.

### 4. First-time setup

1. Open `http://your-server:8080` in a browser
2. Register the first user account — this account becomes the **server owner** (highest role; can promote / demote / delete other admins)
3. Subsequent registrations are pending admin approval — register your friends and approve them from the admin user list
4. Go to the admin panel and trigger a library scan
5. Spela will find your ROMs and start scraping metadata (cover art, descriptions)

### Updating to the latest version

```bash
./update.sh
```

This pulls the latest code from GitHub, rebuilds the containers, and restarts. Your database, saves, and settings are preserved.

## Deploy with Portainer

Portainer's GitOps stacks make it easy to deploy and update Spela.

### 1. Create a new stack

In Portainer: **Stacks** > **Add stack** > **Repository**

| Setting | Value |
|---------|-------|
| Repository URL | `https://github.com/mattias800/spela` |
| Branch | `master` |
| Compose path | `docker-compose.qa.yml` |

### 2. Set environment variables

In the stack's **Environment variables** section, add:

| Variable | Required | Description |
|----------|----------|-------------|
| `SPELA_JWT_SECRET` | Yes | JWT signing key. Generate with: `openssl rand -base64 48` |
| `TURN_SECRET` | Yes | TURN server shared secret. Generate with: `openssl rand -base64 48` |
| `TURN_HOST` | Yes | Your public domain (e.g. `spela.example.com`) |
| `SPELA_GAMES_PATH` | Yes | Host path to your ROM directory (e.g. `/data/spela/games`) |
| `SPELA_IGDB_CLIENT_ID` | No | IGDB / Twitch API client ID (for metadata scraping) |
| `SPELA_IGDB_CLIENT_SECRET` | No | IGDB / Twitch API client secret |
| `TURN_EXTERNAL_IP` | No | Public IP if behind NAT (coturn usually auto-detects) |

### 3. Deploy

Click **Deploy the stack**. Portainer clones the repo, builds both containers from source, and starts everything.

### 4. Update to latest

Click **Pull and redeploy** in Portainer. It pulls the latest commit, rebuilds, and restarts. Your database, saves, and settings are preserved (they live on Docker volumes).

## CI/CD

GitHub Actions workflows build and test the player app automatically. Builds trigger on pushes to the `release` branch and on manual `workflow_dispatch` (from the GitHub Actions tab).

| Workflow | Platforms | Artifacts |
|----------|-----------|-----------|
| **Desktop Build** | macOS, Linux, Windows | DMG, Deb, MSI |
| **Android Build** | Android | Debug APK |

Both workflows run unit tests before packaging. Day-to-day development on `master` does not trigger any builds -- merge `master` into `release` when you're ready to build.

Built artifacts are available under the **Actions** tab on GitHub.

## Reverse Proxy Setup

Spela exposes port 8080 (HTTP). For HTTPS, put a reverse proxy in front of it.

### nginx proxy manager

1. Add a new proxy host
2. Set the forward hostname to your Spela server's IP or Docker hostname
3. Set the forward port to `8080`
4. Enable SSL with Let's Encrypt
5. Under **Advanced**, add WebSocket support:

```nginx
location / {
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
}
```

### Caddy

```
spela.example.com {
    reverse_proxy localhost:8080
}
```

Caddy handles HTTPS automatically.

### Traefik

Add labels to the `spela` service in your compose override:

```yaml
labels:
  - "traefik.enable=true"
  - "traefik.http.routers.spela.rule=Host(`spela.example.com`)"
  - "traefik.http.routers.spela.tls.certresolver=letsencrypt"
  - "traefik.http.services.spela.loadbalancer.server.port=80"
```

## Required Headers (EmulatorJS)

Spela's web-based emulation uses multi-threaded WebAssembly cores for full-speed performance. This requires two HTTP response headers to enable `SharedArrayBuffer` in the browser:

| Header | Value | Purpose |
|--------|-------|---------|
| `Cross-Origin-Opener-Policy` | `same-origin` | Isolates the browsing context |
| `Cross-Origin-Embedder-Policy` | `credentialless` | Allows cross-origin resource loading |

**These headers are already set by Spela's containers** -- both the Go server and the nginx frontend include them automatically. You only need to worry about this if your reverse proxy strips or overrides response headers.

### What happens without them

Without these headers, the browser cannot create `SharedArrayBuffer`, so EmulatorJS falls back to single-threaded cores. Games still work, but emulation is noticeably slower (roughly 2-4x).

### Verify headers are working

Open your browser's developer tools, go to the Network tab, and check the response headers on any page load:

```
Cross-Origin-Opener-Policy: same-origin
Cross-Origin-Embedder-Policy: credentialless
```

Alternatively, check from the command line:

```bash
curl -sI https://spela.example.com | grep -i cross-origin
```

### Reverse proxy notes

Most reverse proxies (Caddy, Traefik, nginx) pass response headers through by default. If you're having issues:

- **nginx**: Ensure you are not using `proxy_hide_header` for these headers
- **Caddy**: Headers are passed through automatically, no action needed
- **Traefik**: Headers are passed through automatically, no action needed
- **Cloudflare**: If using Cloudflare as a proxy, these headers are preserved by default

## TURN Server (Netplay)

The TURN server (coturn) enables netplay for players behind restrictive firewalls or NATs. It runs on the host network and requires these UDP ports to be open:

| Port | Protocol | Purpose |
|------|----------|---------|
| 3478 | UDP/TCP | STUN/TURN signaling |
| 5349 | TCP | TURN over TLS (if enabled) |
| 49152-49252 | UDP | Media relay ports |

If you don't need netplay, you can remove the `coturn` service from the compose file.

### Firewall rules (ufw example)

```bash
sudo ufw allow 3478/udp
sudo ufw allow 3478/tcp
sudo ufw allow 49152:49252/udp
```

### Behind NAT

If your server is behind a NAT (e.g. home network), set `TURN_EXTERNAL_IP` to your public IP and forward the ports above on your router.

## Data Persistence

All persistent data lives on named Docker volumes:

| Volume | Contents | Importance |
|--------|----------|------------|
| `spela-data` | SQLite database (users, games, metadata, settings) | Critical -- back this up |
| `spela-saves` | Game save states | Important -- your players' progress |
| `spela-cores` | Downloaded libretro cores | Can be re-downloaded |
| `spela-images` | Scraped cover art and screenshots | Can be re-scraped |

Your ROMs are bind-mounted read-only from the host, so they're not on a volume.

### Backing up

```bash
# Find where Docker stores the volume
docker volume inspect spela-data --format '{{ .Mountpoint }}'

# Copy the database file
sudo cp /var/lib/docker/volumes/spela-data/_data/spela.db /backups/spela.db

# Or use docker cp
docker cp spela-1:/app/data/spela.db /backups/spela.db
```

### Restoring

```bash
docker compose -f docker-compose.qa.yml down
sudo cp /backups/spela.db /var/lib/docker/volumes/spela-data/_data/spela.db
docker compose -f docker-compose.qa.yml up -d
```

## Metadata Scraping

Spela uses three independent metadata sources, each optional and each owning a different slice of the game presentation:

| Source | Provides | Credentials |
|--------|----------|-------------|
| [libretro-thumbnails](https://github.com/libretro-thumbnails/libretro-thumbnails) | Box art (the primary cover image) | None — works out of the box |
| [IGDB](https://www.igdb.com/) | Titles, descriptions, ratings, release dates, developer/publisher info, in-game screenshots | Twitch developer credentials (free) |
| [SteamGridDB](https://www.steamgriddb.com/) | Hero banner artwork shown at the top of the game / console pages | Free SteamGridDB API key |

The scraper falls back gracefully when sources are unconfigured: with no IGDB credentials you still get cover art (libretro-thumbnails) but no descriptions or screenshots; with no SteamGridDB key you still get the rest but no hero banners. Game files are detected and playable without any of the three.

When both libretro-thumbnails and IGDB return a cover, **libretro-thumbnails wins** (closer-cropped, more consistent box art). Admins can override the choice per-game from the cover-art selector in the admin game-detail page; manual overrides survive re-scrapes.

### Setting up IGDB

1. Create a free Twitch account at [twitch.tv](https://www.twitch.tv/) — IGDB is owned by Twitch and reuses the Twitch developer console for API credentials
2. Register an application at [dev.twitch.tv/console](https://dev.twitch.tv/console/apps) and copy the **Client ID** and generate a **Client Secret**
3. Set `SPELA_IGDB_CLIENT_ID` and `SPELA_IGDB_CLIENT_SECRET` in `.env` or Portainer environment variables (or use the in-app Setup wizard / admin Settings panel)
4. Trigger a scrape from the admin panel in the web UI

Scraping respects IGDB's rate limit (4 req/sec); large libraries may take a while on the first scrape. Spela's scraper backs off automatically on HTTP 429 responses.

### Setting up SteamGridDB

1. Sign in at [steamgriddb.com](https://www.steamgriddb.com/) and request an API key from your profile
2. Configure the key in the admin **Settings** panel (or via `SPELA_STEAMGRIDDB_API_KEY` env var) — the in-app Setup wizard prompts for it as well
3. Re-scrape or trigger an artwork refresh from a console / game admin page

## Environment Variable Reference

### Server

| Variable | Default | Description |
|----------|---------|-------------|
| `SPELA_JWT_SECRET` | (required) | JWT signing key |
| `SPELA_PORT` | `8080` | Server listen port |
| `SPELA_DB_PATH` | `/app/data/spela.db` | SQLite database path |
| `SPELA_GAME_DIRS` | `/app/games` | Comma-separated game directories |
| `SPELA_SAVE_DIR` | `/app/saves` | Save state storage directory |
| `SPELA_CORE_DIR` | `/app/cores` | libretro core storage directory |
| `SPELA_IMAGE_DIR` | `/app/images` | Scraped image storage directory |
| `SPELA_CORS_ORIGINS` | `*` | Comma-separated allowed CORS origins |
| `SPELA_IGDB_CLIENT_ID` | | IGDB / Twitch API client ID (metadata scraping) |
| `SPELA_IGDB_CLIENT_SECRET` | | IGDB / Twitch API client secret |
| `GIN_MODE` | `release` | Gin framework mode (`debug` or `release`) |

### TURN Server

| Variable | Default | Description |
|----------|---------|-------------|
| `TURN_ENABLED` | `true` | Enable TURN credential endpoint |
| `TURN_HOST` | | Public domain for the TURN realm |
| `TURN_SECRET` | | Shared secret for HMAC-SHA1 credentials |
| `TURN_PORT` | `3478` | TURN listening port |
| `TURN_EXTERNAL_IP` | | Public IP (if behind NAT) |

## Troubleshooting

### Games not appearing after scan

- Check that your ROM directory is mounted correctly: `docker exec spela-1 ls /app/games`
- ROMs must be in console-named subdirectories (`nes/`, `snes/`, etc.)
- Verify file extensions are supported (see [Supported Consoles](../README.md#supported-consoles))

### Metadata not loading

- Verify `SPELA_IGDB_CLIENT_ID` and `SPELA_IGDB_CLIENT_SECRET` are set
- Check server logs: `docker logs spela-1`
- IGDB has a 4 req/sec rate limit — large libraries take a while on the first scrape; the scraper backs off automatically on HTTP 429
- Box art comes from the libretro-thumbnails collection independently of IGDB; see "Cover art is missing for some games" if specific games are missing thumbnails but have descriptions

### WebSocket connection issues

- Make sure your reverse proxy passes WebSocket upgrade headers
- Check that the `Upgrade` and `Connection` headers are forwarded
- For nginx proxy manager, enable WebSocket support in the proxy host settings

### Netplay not connecting

- Verify TURN server ports (3478, 49152-49252) are open in your firewall
- Check coturn logs: `docker logs spela-coturn-1`
- Set `TURN_EXTERNAL_IP` if behind NAT
- Both players must have the same game ROM downloaded

### Database issues

- The database is auto-migrated on startup -- no manual migrations needed
- If the database is corrupted, restore from backup (see [Backing up](#backing-up))
- To reset completely: `docker volume rm spela-data` (destroys all data)
