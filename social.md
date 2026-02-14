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

## Implementation Priority (Phase 1)

1. **Activity feed + online status** — Low effort, immediately makes the service feel alive and social.
2. **Ratings** — Simple to build, adds real value for game discovery.
3. **Shared save states** — Unique to emulation, very on-brand, builds on existing save state infrastructure.
