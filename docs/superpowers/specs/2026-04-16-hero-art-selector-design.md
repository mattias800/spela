# Allow Admin to Choose Hero Art from SteamGridDB Options

**Issue:** #412
**Date:** 2026-04-16

## Summary

Add a "Change Hero Art" option on the game detail page that lets admins
pick from available SteamGridDB hero images, mirroring the existing
"Change Cover" feature.

## Backend

### New endpoint: GET /admin/games/:id/heroes

- Looks up the game's SteamGridDB ID from `GameArtwork.SteamGridDBID`
- Calls `SteamGridDBClient.GetHeroes(sgdbID)` to fetch available heroes
- Returns list of options with CDN thumbnail URLs + the currently active
  hero URL for highlighting
- If no SteamGridDB ID exists, returns empty list

### New endpoint: PUT /admin/games/:id/heroes

- Accepts `{ url: "<steamgriddb CDN URL>" }`
- Downloads the image locally (same pattern as cover art download)
- Updates `GameArtwork.HeroURL` with the local path
- Sets `GameArtwork.HeroManuallySet = true`

### New field: HeroManuallySet on GameArtwork

Add `HeroManuallySet bool` to the `GameArtwork` model. When true, the
scraper skips overwriting the hero during rescrapes. Same pattern as
`Game.CoverManuallySet`.

### Scraper change

In the SteamGridDB artwork scraping code, check `HeroManuallySet` before
overwriting `HeroURL`. If true, preserve the admin's choice.

## Frontend

### New component: HeroArtSelector

Mirrors `CoverArtSelector` — a modal with a grid of hero images fetched
from the GET endpoint. Currently active hero shows a checkmark badge.
Clicking an image calls the PUT endpoint and shows a toast.

### UI trigger

Add "Change Hero Art" to the actions menu in `game-hero.tsx`, alongside
the existing "Change Cover" option.

## Testing

- Backend: GET returns hero options from SteamGridDB
- Backend: PUT saves selection and sets HeroManuallySet flag
- Backend: scraper skips hero when HeroManuallySet is true
- Frontend: no new tests needed (mirrors existing pattern)

## Out of Scope

- Hero art from sources other than SteamGridDB
- Bulk hero art selection across multiple games
- Custom upload for hero art
