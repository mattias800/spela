# Social Features for Spela

## Low Effort

- **Activity feed** — See what games other users on the server are playing or have recently played. "Alice played Super Mario Bros. 3 for 45 min."
- **Public profiles** — View another user's favorite games, total play time, and most-played titles.
- **Online status** — Show who's currently online/in-game on the server.

## Medium Effort

- **Ratings & reviews** — Let users rate games (thumbs up/star rating) and leave short reviews. Helps others discover good games in a large library.
- **Recommendations** — "Users who played X also played Y." Simple collaborative filtering based on play history.
- **Achievements/milestones** — Server-defined or automatic badges like "Played 10 different NES games" or "100 hours total play time."
- **Shared save states** — Let users publish a save state (e.g., a tricky boss fight) for others to load and try.
- **Challenge links** — Share a save state + game combo with the framing "can you beat this level?" — basically shared save states with social context.

## Higher Effort

- **Netplay / online multiplayer** — libretro supports netplay. Two users on the same server could play co-op or versus games together.
- **Live spectating** — Watch another user's game session in real-time (stream frames over WebSocket).
- **Leaderboards** — For games that expose score via libretro memory, track and rank high scores.
- **Chat** — Simple real-time messaging between users on the server, either global or DMs.

## Phase 2 Ideas

### Low Effort

- **Profile bio** — Short 280-char bio on public profiles. Just a text field on the User model.
- **Play session timer** — Show "Playing for 45m" live-updating in the online users widget. Track session start time in the WebSocket hub (in-memory).
- **Top consoles on profiles** — Bar chart or list showing games played and hours per console, derived from PlayHistory grouped by console.

### Medium Effort

- **Computed badges/milestones** — Calculated on-the-fly from existing data, no extra DB tables needed:
  - `Newcomer` — first week
  - `Collector` — 10+ games played
  - `Dedicated` — 100+ total hours
  - `Marathon Runner` — any single game with 24+ hours
  - `Critic` — 10+ ratings given
  - `Generous` — 5+ shared saves
  - `Curator` — 3+ public collections
  - `Streak Master` — 30+ day streak
  - `Retro Scholar` — played games on 5+ different consoles
  - `Achievement Hunter` — 50+ RA achievements unlocked
  - `Completionist` — 100% achievements on any game
- **Profile privacy settings** — `UserProfileSettings` model with `ProfileVisibility` (public/users_only/private) and toggles for showing play history, favorites, play time, ratings, collections, achievements. Public-by-default makes sense for self-hosted.
- **Game grouping in online widget** — "2 playing Super Metroid" with stacked avatars when multiple users play the same game.
- **Mini game card on hover** — When hovering over a playing user in the online widget, show a popover with larger cover art, title, console, and play duration.
- **Featured game on profile** — The user's most played game displayed prominently with cover art, play time, and their rating.
- **Achievement showcase on profiles** — If RA is linked: total unlocks, total points, and 3-5 most recent achievement badges.

### Higher Effort

- **Recommendations** — "Users who played X also played Y." Simple collaborative filtering based on play history.
- **Challenge links** — Share a save state + game combo with the framing "can you beat this level?" — shared save states with social context.

## Implementation History

### Phase 1 (Done)

1. **Activity feed + online status** — Low effort, immediately makes the service feel alive and social.
2. **Ratings** — Simple to build, adds real value for game discovery.
3. **Shared save states** — Unique to emulation, very on-brand, builds on existing save state infrastructure.
4. **Public profiles** — View another user's stats, favorites, most-played, and recent games. Clickable usernames everywhere.
5. **Online status with game details** — Online users widget shows what game each user is playing with cover art and console name. WebSocket presence broadcasting.
