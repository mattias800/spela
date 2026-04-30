# ROM Hacks & Patches -- Design Proposal & User Stories

## Context

This document defines user stories for adding ROM hack and patch support to Spela. It builds on the analysis in `rom-hacks-and-patches-analysis.md` and the decisions documented there.

## Key Decisions (Already Made)

1. **Server-side patching** -- the server applies patches and serves patched ROMs. Client-side patching may come later.
2. **One patch at a time** -- no multi-patch stacking.
3. **All users see all patches** -- no per-user access control on patch visibility.
4. **Hybrid RHDN integration** -- bundle the RHDN metadata index (~6.5 MB SQL dump from archive.org) for discovery; individual patch files downloaded on-demand from archive.org when a user wants to apply one.
5. **Patches target specific ROM variants** -- matched by CRC32.
6. **Six categories**: minor patches (toggleable on game detail), translations (language options with admin-default support), total conversions (standalone game entries), enhancement/DX (same-platform = minor patch, cross-platform = new game under target console), randomizers (deferred), IGDB-listed hacks (just total conversions that happen to be scrapeable).

## Roles

- **server admin** -- manages the Spela instance, uploads games and patches, configures settings
- **player** -- end user who browses the library and plays games
- **system** -- automated/background processes

---

## Phase 1: Foundation -- Patch Data Model & RHDN Integration

**Goal:** Import the RHDN metadata index, match patches to games in the library by CRC32, and let users discover available patches. No patching happens yet -- this phase is purely about awareness and browsing.

### Story 1.1: Import RHDN metadata index

**As a** server admin
**I want to** import the RHDN metadata dump into my Spela server
**So that** my users can discover community patches available for games in the library without me having to curate everything manually.

**Acceptance criteria:**
- [ ] Admin can trigger an RHDN import from the admin panel (or it happens automatically on first startup when the bundled data is present)
- [ ] The import processes the RHDN SQL dump and stores patch metadata (name, author, description, type, version, release date, target ROM CRC32, download URL, RHDN page URL)
- [ ] The import is idempotent -- re-importing updates existing entries rather than creating duplicates
- [ ] Import progress is visible to the admin (count of patches imported, any errors)
- [ ] The import completes in a reasonable time (the ~6.5 MB dump should import in under a minute)

**Notes:**
- The RHDN dump is from archive.org/details/romhacking.net-20240801
- Each RHDN entry has a category (e.g., "Translation", "Improvement", "Complete") that maps to our patch type taxonomy
- Patches in the dump include the CRC32 of the source ROM they target

### Story 1.2: Match RHDN patches to library games

**As a** system
**I want to** automatically match imported RHDN patches to games in the library by CRC32
**So that** users can see which patches are available for games they actually have.

**Acceptance criteria:**
- [ ] After import (and after each library scan), the system matches RHDN patches to games by comparing the patch's target CRC32 against the CRC32 of games in the library
- [ ] A patch can match multiple games (if the same ROM exists as multiple variants/copies in the library)
- [ ] A game can have multiple matched patches
- [ ] Unmatched patches (target CRC32 not found in library) are stored but not surfaced to users
- [ ] When a new game is added to the library (via scan or upload), the system checks for matching RHDN patches

**Notes:**
- The CRC32 matching is the core of the discovery system -- it ensures patches are only shown when they can actually be applied
- Games with no CRC32 value cannot be matched and should be skipped

### Story 1.3: Patch data model with type classification

**As a** system
**I want to** store patches with proper type classification and metadata
**So that** patches can be presented appropriately based on their category (minor patch, translation, total conversion, etc.).

**Acceptance criteria:**
- [ ] Each patch record includes: name, author, description, type (bugfix, qol, translation, cosmetic, widescreen, difficulty, enhancement, total_conversion, other), version, source (rhdn, custom), target ROM CRC32, patch file reference (URL or local path), RHDN page URL (if applicable)
- [ ] Translation patches additionally store: source language and target language
- [ ] Each patch is linked to zero or more games in the library (via CRC32 matching)
- [ ] Patch type determines how the patch is presented to users in later phases (minor patches appear as toggles, translations as language options, total conversions as standalone entries)

**Notes:**
- The type classification will drive UX decisions in later phases
- RHDN categories map approximately to our types, but the mapping is not 1:1 -- some RHDN "Improvement" patches are QoL, some are bugfixes, some are enhancements

### Story 1.4: "Patches available" indicator on game cards

**As a** player
**I want to** see at a glance which games in my library have patches available
**So that** I can discover enhancement opportunities without having to open every game's detail page.

**Acceptance criteria:**
- [ ] Game cards in the library grid show a subtle indicator (badge, icon, or label) when the game has one or more matched patches
- [ ] The indicator shows the count of available patches
- [ ] The indicator is visible in both grid and list views (web frontend)
- [ ] Tapping/clicking the indicator navigates to the game detail page, scrolled to the patches section
- [ ] Games with no matched patches show no indicator (no "0 patches" clutter)

**Notes:**
- The indicator should be unobtrusive -- it is supplementary information, not the primary content of the game card
- Consider a small icon (like a wrench or puzzle piece) with a count badge

### Story 1.5: Browse available patches on game detail page

**As a** player
**I want to** see all available patches for a game on its detail page
**So that** I can learn what modifications exist and decide which ones interest me.

**Acceptance criteria:**
- [ ] The game detail page has a "Patches" section listing all matched patches for that game
- [ ] Each patch entry shows: name, author, type badge (bugfix, QoL, translation, etc.), description (truncated with expand), and version
- [ ] Patches are grouped by type (translations first, then bugfixes, then QoL, then other types)
- [ ] Each patch entry links to its RHDN page (if it has one) so users can read more details and community discussion
- [ ] The patches section is hidden when no patches are available for the game
- [ ] The section header shows the total count of available patches

**Notes:**
- This is discovery-only in Phase 1 -- there is no "Apply" or "Download" button yet
- The grouping by type helps users find what they are looking for (e.g., someone looking for a translation does not want to scroll through 20 cosmetic patches)

### Story 1.6: Patches section for unmatched CRC32

**As a** player
**I want to** understand why patches might not be available for a game I own
**So that** I can take action (e.g., obtain the correct ROM version) if I want to use community patches.

**Acceptance criteria:**
- [ ] If a game has no CRC32 value, the patches section shows a message explaining that ROM verification is needed before patches can be matched
- [ ] If a game has a CRC32 but no matching RHDN patches exist, the section is simply hidden (no "no patches found" noise)
- [ ] If the admin has not yet imported the RHDN index, the patches section is hidden entirely (not shown with an error)

**Notes:**
- This prevents user confusion when patches exist on RHDN for a game title but the user has a different ROM variant (e.g., a different region or revision)
- In the future, the system could suggest which ROM variant a patch targets so the admin knows which version to obtain

### Story 1.7: Admin view of RHDN import status

**As a** server admin
**I want to** see the status of the RHDN metadata import and how many patches matched my library
**So that** I understand the coverage and can decide whether to obtain additional ROM variants.

**Acceptance criteria:**
- [ ] Admin panel shows: total RHDN patches imported, total matched to library games, total unmatched, last import date
- [ ] Admin can view unmatched patches filtered by console, showing which games (by title) have patches available for a different ROM CRC32 than what is in the library
- [ ] Admin can re-trigger the RHDN import to update the index

**Notes:**
- The "unmatched patches" view is a powerful tool for admins who want to maximize patch coverage -- it tells them exactly which ROM variants they need
- This is an admin-only view; regular players do not see unmatched patches

---

## Phase 2: Patch Application & Admin Upload

**Goal:** Enable actual patching. Users can apply a patch to a game, the server produces a patched ROM, caches it, and serves it. Admins can also upload their own custom patch files.

### Story 2.1: Server-side patch application

**As a** player
**I want to** apply a patch to a game and play the patched version
**So that** I can experience bug fixes, quality-of-life improvements, and other enhancements.

**Acceptance criteria:**
- [ ] On the game detail page, each patch in the Patches section has an "Apply" action
- [ ] When a player selects a patch, the server applies the patch file to the base ROM and produces a patched ROM
- [ ] The patched ROM is cached on the server so subsequent launches do not re-apply the patch
- [ ] The player can launch the game with the selected patch active
- [ ] Only one patch can be active at a time per game -- selecting a new patch replaces the previous one
- [ ] The player can deactivate a patch and return to the unpatched original

**Notes:**
- The patch cache should be keyed by base ROM CRC32 + patch file hash, so the same patch applied to the same ROM always produces the same cached result
- The cache can be cleaned up by the admin if disk space is a concern (Story 2.7)

### Story 2.2: On-demand patch file download from archive.org

**As a** system
**I want to** download patch files from archive.org on-demand when a user wants to apply an RHDN patch
**So that** the server does not need to store thousands of patch files it may never use.

**Acceptance criteria:**
- [ ] When a user requests to apply an RHDN patch, and the patch file is not already stored locally, the system downloads it from archive.org
- [ ] The download happens transparently -- the user sees a brief "Preparing patch..." state
- [ ] Downloaded patch files are cached locally for future use
- [ ] If the download fails (network error, file not found on archive.org), the user sees a clear error message explaining the patch file could not be retrieved
- [ ] The system validates the downloaded file (correct size, not corrupted) before attempting to apply it

**Notes:**
- Archive.org URLs for RHDN patches follow a predictable pattern based on the RHDN patch ID
- The patch file cache is separate from the patched ROM cache -- patch files are small (typically under 1 MB) and can be kept indefinitely

### Story 2.3: Patch format support (IPS, BPS, UPS, xdelta)

**As a** system
**I want to** support applying patches in all common ROM patching formats
**So that** the vast majority of community patches can be used.

**Acceptance criteria:**
- [ ] The server can apply IPS format patches
- [ ] The server can apply BPS format patches with CRC32 verification of the source ROM
- [ ] The server can apply UPS format patches with CRC32 verification of the source ROM
- [ ] The server can apply xdelta format patches
- [ ] For BPS and UPS patches, if the source ROM CRC32 does not match the expected value, the patch application fails with a clear error message
- [ ] For IPS patches (which have no checksum), the system shows a warning that compatibility cannot be verified, but still allows application
- [ ] If a patch file format is unrecognized, the system shows an error message naming the expected formats

**Notes:**
- BPS is the most common modern format and should be prioritized
- IPS is the oldest and most common for older/smaller patches but lacks integrity checking
- xdelta is common for large patches (CD-based games)

### Story 2.4: CRC32 verification and mismatch handling

**As a** player
**I want to** be warned when a patch might not be compatible with my ROM version
**So that** I do not end up with a corrupted or non-functional game.

**Acceptance criteria:**
- [ ] For BPS and UPS patches, the system verifies the base ROM's CRC32 against the patch's expected source CRC32 before applying
- [ ] If verification fails, the system shows an error explaining that the patch targets a different ROM version, names the expected CRC32, and does not apply the patch
- [ ] For IPS patches (no embedded checksum), the system shows a warning that the patch has no verification and the result may not work correctly, but allows the user to proceed
- [ ] After patch application, the resulting patched ROM is tested for basic integrity (file size is reasonable, not zero-length)

**Notes:**
- CRC32 mismatches are the most common source of broken patched ROMs -- clear error messages here save users from frustration
- Some users may have a slightly different ROM revision that produces a slightly different CRC32 -- the error message should help them understand this

### Story 2.5: Admin upload of custom patches

**As a** server admin
**I want to** upload my own patch files for games in the library
**So that** I can provide patches that are not in the RHDN database (private fixes, custom translations, in-development patches).

**Acceptance criteria:**
- [ ] On the game detail page (admin view), there is an "Upload Patch" action in the Patches section
- [ ] Admin can upload a patch file (.ips, .bps, .ups, .xdelta) and provide metadata: name, description, type, author, version
- [ ] For translation patches, admin can additionally specify source and target language
- [ ] The uploaded patch is associated with the game and immediately visible to all users
- [ ] Admin can edit metadata and replace the patch file for custom patches
- [ ] Admin can delete custom patches
- [ ] Custom patches are visually distinguished from RHDN patches (e.g., labeled "Custom" vs showing the RHDN source)

**Notes:**
- Custom patches are stored on the server filesystem alongside the RHDN patch file cache
- The admin upload flow should validate the file format before accepting it

### Story 2.6: Patch selection UI before launch

**As a** player
**I want to** choose which patch (if any) to use before launching a game
**So that** I have a clear moment to decide between the original and patched versions.

**Acceptance criteria:**
- [ ] When launching a game that has available patches, the player can see the currently active patch (if any) on the game detail page
- [ ] The active patch is indicated clearly (e.g., a badge on the game detail showing "Playing with: Bugfix v1.2")
- [ ] The player can change or remove the active patch from the game detail page without navigating elsewhere
- [ ] Changing the active patch warns the player that save states from the previous patch configuration may not be compatible
- [ ] If no patch is selected, the game launches with the original unpatched ROM (the default behavior)

**Notes:**
- The patch selection is per-user, per-game -- different users can have different patches active for the same game
- The selection persists across sessions until the user changes it

### Story 2.7: Save state isolation by active patch

**As a** player
**I want** my save states to be kept separate for different patch configurations
**So that** loading a save from the unpatched version does not crash the patched version (and vice versa).

**Acceptance criteria:**
- [ ] Save states (both manual and auto-saves) are namespaced by the active patch configuration
- [ ] When the player switches patches, only save states from the matching patch configuration are shown
- [ ] Save states from the unpatched original are shown when no patch is active
- [ ] The player can see which patch configuration a save state was created with
- [ ] SRAM/battery saves follow the same isolation -- each patch configuration has its own SRAM save

**Notes:**
- This is critical for preventing save corruption
- The namespacing should be based on the patch file hash, not the patch name/version, so re-uploading the same patch file reuses saves
- Game sessions (the existing session system) naturally provide this isolation if each patch config uses a separate session

### Story 2.8: Patch version updates

**As a** server admin
**I want to** update a patch to a newer version
**So that** users get access to improved patches without losing track of their existing saves.

**Acceptance criteria:**
- [ ] Admin can upload a new version of a custom patch, replacing the patch file while keeping the same patch entry
- [ ] When a patch is updated, users who had the old version active see a notification that a new version is available
- [ ] Users are warned that save states from the old patch version may not be compatible with the new version
- [ ] The old patched ROM cache is invalidated when the patch file changes
- [ ] RHDN patches update automatically when the RHDN index is re-imported (if a newer version exists in the dump)

**Notes:**
- Save state compatibility across patch versions is never guaranteed -- the system should always warn, even if the change is minor
- The old saves are not deleted -- they remain accessible if the user wants to revert to the old patch version (though there is no automated mechanism for this in Phase 2)

### Story 2.9: Patched ROM cache management

**As a** server admin
**I want to** manage the disk space used by cached patched ROMs
**So that** patch caching does not consume excessive storage on my server.

**Acceptance criteria:**
- [ ] Admin panel shows the total size of the patched ROM cache
- [ ] Admin can clear the entire patched ROM cache (cached ROMs will be regenerated on next use)
- [ ] Admin can see a breakdown of cache usage by console
- [ ] The system does not cache patched ROMs for games that have not been played recently (or uses an LRU eviction policy)

**Notes:**
- Patched ROMs can be large (CD-based games can be hundreds of MB) so cache management is important for admins with limited storage
- Clearing the cache has no impact on saves or user experience beyond a brief delay on next launch while the patch is re-applied

### Story 2.10: Patch interaction with achievements

**As a** player
**I want to** be clearly informed that applying a patch disables RetroAchievements
**So that** I can make an informed decision between using patches and earning achievements.

**Acceptance criteria:**
- [ ] When a player activates a patch on a game that supports RetroAchievements, a warning is shown: "Applying a patch will disable RetroAchievements for this game"
- [ ] While a patch is active, the RetroAchievements section on the game detail page shows a message explaining that achievements are disabled because a patch is active
- [ ] Removing the patch and returning to the original ROM re-enables achievements
- [ ] The RA hash verification naturally rejects patched ROMs -- this story ensures the UX explains why, rather than showing a cryptic error

**Notes:**
- RetroAchievements requires an unmodified ROM that matches their hash database
- This is not a Spela restriction -- it is how RA works. The UX should explain this clearly.

### Story 2.11: Patch interaction with netplay

**As a** player
**I want** netplay sessions to ensure both players are using the same ROM version
**So that** the game does not desync due to one player having a patch applied.

**Acceptance criteria:**
- [ ] When creating a netplay session, the system records whether a patch is active and which patch it is
- [ ] When a second player joins a netplay session, the system checks that they have the same patch configuration (same patch or both unpatched)
- [ ] If the patch configurations do not match, the joining player is informed of the mismatch and told which patch they need to activate (or deactivate) to join
- [ ] The netplay lobby shows the active patch (if any) so players know what to expect before joining

**Notes:**
- Both players must run the exact same ROM binary for netplay to work
- Since patching is server-side, the server can verify this before allowing the session to start

### Story 2.12: Patch interaction with challenges

**As a** player
**I want** challenges to enforce a specific ROM version (patched or unpatched)
**So that** all participants are competing on a level playing field.

**Acceptance criteria:**
- [ ] When a challenge is created, the system records the active patch configuration (including "no patch")
- [ ] When a player attempts a challenge, the system ensures they are using the same patch configuration as the challenge creator
- [ ] If the player's patch configuration does not match, they are prompted to switch to the correct configuration before starting
- [ ] The challenge detail page shows which patch (if any) is required

**Notes:**
- Challenges are inherently about fair competition -- enforcing ROM consistency is essential
- This also applies to shared sessions, where all participants should be on the same ROM version

### Story 2.13: Patch interaction with shared saves

**As a** player
**I want** shared save states to indicate which patch configuration they were created with
**So that** I do not download a save that is incompatible with my setup.

**Acceptance criteria:**
- [ ] When a player shares a save state, the active patch configuration is recorded as metadata on the shared save
- [ ] When browsing shared saves, the patch configuration is displayed (e.g., "Created with: Bugfix v1.2" or "Original/Unpatched")
- [ ] If a player tries to download a shared save that was created with a different patch configuration, a warning is shown explaining the potential incompatibility

**Notes:**
- Shared saves with mismatched patches will almost certainly crash -- the warning should be prominent
- The system should still allow the download after the warning, since some patches may be compatible (e.g., cosmetic-only patches)

---

## Phase 3: Translation UX

**Goal:** Give translation patches first-class treatment. Translations are the most impactful patch type for many users -- a fan translation of a Japan-only game is often the only way to play it. The UX should reflect this importance.

### Story 3.1: Language metadata for translation patches

**As a** system
**I want** translation patches to carry structured language metadata
**So that** they can be presented as language options rather than generic patches.

**Acceptance criteria:**
- [ ] Translation patches store a source language (the language of the unpatched ROM) and a target language (the language the patch translates to)
- [ ] Language values use a standardized format (e.g., ISO 639-1 codes: "ja", "en", "es", "de", etc.)
- [ ] RHDN translation patches have their language metadata populated automatically from the RHDN dump where available
- [ ] Admin can set or correct language metadata for any translation patch

**Notes:**
- Some RHDN translation patches may not have clean language metadata -- the admin should be able to fix these manually
- A game can have multiple translation patches for the same target language (e.g., two different English translations of Final Fantasy V) -- both should be available as options

### Story 3.2: Prominent language selector on game detail

**As a** player
**I want** translation patches presented as a language selector on the game detail page, separate from the general patches list
**So that** choosing a language feels natural and is not buried in a list of bug fixes and cosmetic tweaks.

**Acceptance criteria:**
- [ ] If a game has translation patches, a "Languages" section appears on the game detail page, above the general "Patches" section
- [ ] The Languages section shows the original language and all available translation languages as selectable options (e.g., flags or language name chips)
- [ ] Selecting a language activates the corresponding translation patch
- [ ] If multiple translations exist for the same target language (e.g., two English translations), they are shown as sub-options with their names and authors
- [ ] The currently active language is clearly indicated
- [ ] The original (unpatched) language is always available as an option to revert to

**Notes:**
- The language selector should feel like choosing a language setting, not like "applying a patch"
- Consider using country/language flags for visual clarity, but always include the language name for accessibility

### Story 3.3: Admin sets a default translation

**As a** server admin
**I want to** set a translation patch as the default for a game
**So that** my users do not have to manually select the English translation every time they open a Japanese-only game.

**Acceptance criteria:**
- [ ] Admin can mark one translation patch per game as the "default"
- [ ] When a player opens a game with a default translation set, the translation is pre-selected (the patched ROM is served by default)
- [ ] The player can opt out by switching back to the original language or choosing a different translation
- [ ] The default translation is indicated in the language selector (e.g., "English (default)" or a star icon)
- [ ] Setting a new default replaces the previous default (only one default per game)

**Notes:**
- This is about reducing friction for the most common case -- an admin with a Japanese ROM and an English translation wants their English-speaking users to just play the game without having to know about patching
- The default applies to all users on the server

### Story 3.4: Translation indicator in library browsing

**As a** player
**I want to** see which games have translations available when browsing the library
**So that** I can discover playable Japan-only games I might have overlooked.

**Acceptance criteria:**
- [ ] Games with translation patches show a language indicator on their game card (e.g., a small flag icon or "EN" badge)
- [ ] If a default translation is set, the language indicator reflects the default language rather than the original
- [ ] The language indicator is distinct from the general "patches available" indicator (Story 1.4)
- [ ] Players can filter the library to show only games with translations available in a specific language

**Notes:**
- This is particularly valuable for libraries with many Japanese games -- the translation indicator helps users find which ones they can actually play
- The filter could be integrated with existing library filters

### Story 3.5: Translation does not change game region

**As a** system
**I want** translation patches to not modify the game's region metadata
**So that** the library remains accurately categorized.

**Acceptance criteria:**
- [ ] Applying a translation patch does not change the game's Region field (a Japanese game remains tagged as Japan region even with an English translation active)
- [ ] The library filter for "Region: Japan" still includes a game even if it has an active English translation
- [ ] The game's metadata (title, description) is not altered by the translation patch -- the original metadata is preserved

**Notes:**
- The translation is a patch overlay, not a region change
- If the admin wants to change the displayed title to the English title, they can do so manually using existing metadata editing -- that is a separate action from applying a translation

---

## Phase 4: Total Conversions & Cross-Platform

**Goal:** Handle ROM hacks that are fundamentally different games. Total conversions get their own library entries with proper metadata and parent game linking. Cross-platform patches (e.g., GB to GBC) create entries under the target console.

### Story 4.1: Promote a patch to a standalone game entry

**As a** server admin
**I want to** promote a total conversion patch into a standalone game entry in the library
**So that** major ROM hacks like "Pokemon Unbound" or "Chrono Trigger: Crimson Echoes" appear as their own games rather than being buried in a patches list.

**Acceptance criteria:**
- [ ] Admin can select a patch and choose "Promote to Game"
- [ ] A new game entry is created in the library with its own title (defaulting to the patch name, editable by admin)
- [ ] The new game entry is linked to the parent game (the base ROM the patch applies to)
- [ ] The new game's ROM is generated by applying the patch to the parent game's ROM
- [ ] The new game entry can have its own cover art, description, screenshots, and other metadata (set manually by admin since most total conversions are not scrapeable)
- [ ] The promoted game appears in the library, search results, and browse pages like any other game
- [ ] The original patch entry is replaced by a link to the new standalone game (no duplicate listing)

**Notes:**
- The promoted game's ROM can either be cached (generated from base + patch on demand) or stored permanently -- the admin may want a choice, but on-demand with cache is the default to save disk space
- If the parent game's ROM changes (unlikely but possible via ROM replacement), the promoted game's ROM should be regenerated

### Story 4.2: Parent game linking ("Based on" display)

**As a** player
**I want to** see which original game a ROM hack is based on
**So that** I understand the relationship and can explore the original if interested.

**Acceptance criteria:**
- [ ] Promoted ROM hack game entries display a "Based on [parent game title]" link on their detail page
- [ ] Clicking/tapping the parent game link navigates to the parent game's detail page
- [ ] The "Based on" link includes the parent game's cover art thumbnail for visual recognition
- [ ] If the parent game is not in the library (e.g., it was removed), the link shows the title without navigation

**Notes:**
- This is useful for discovery in both directions -- from hack to original and from original to hacks (see Story 4.3)

### Story 4.3: "ROM Hacks" section on parent game detail

**As a** player
**I want to** see all promoted ROM hacks for a game on its detail page
**So that** I can discover total conversion hacks I might enjoy, starting from a game I already know.

**Acceptance criteria:**
- [ ] The game detail page has a "ROM Hacks" section showing all promoted standalone games that are based on this game
- [ ] Each ROM hack entry shows its title, cover art (if available), and a brief description
- [ ] Clicking/tapping a ROM hack entry navigates to that game's detail page
- [ ] The section is hidden when no promoted ROM hacks exist for the game
- [ ] The section is positioned after the existing game metadata sections (description, screenshots, etc.) but before the general Patches section

**Notes:**
- This creates a natural discovery path: player browsing "Pokemon FireRed" sees "ROM Hacks" section with "Pokemon Unbound", clicks through to explore it
- This section shows only promoted total conversions, not minor patches

### Story 4.4: Cross-platform patches create game under target console

**As a** server admin
**I want to** apply a cross-platform patch (e.g., a Game Boy to Game Boy Color colorization) and have the result appear under the correct target console
**So that** the enhanced version runs on the right emulator core and appears in the right console section.

**Acceptance criteria:**
- [ ] When uploading or promoting a patch, the admin can specify a target console different from the parent game's console
- [ ] The promoted game entry is created under the target console (e.g., a GB-to-GBC color patch creates a GBC game entry)
- [ ] The promoted game's ROM is served with the target console's file type expectations (so the correct libretro core is loaded)
- [ ] The "Based on" link on the promoted game shows the parent game under its original console
- [ ] The parent game's "ROM Hacks" section lists the cross-platform enhanced version

**Notes:**
- Cross-platform patches are less common but important -- the most notable examples are GB-to-GBC color patches and MSU-1 SNES audio enhancements
- The admin must explicitly specify the target console -- the system does not auto-detect platform changes in patches

### Story 4.5: Manual metadata editing for non-scrapeable hacks

**As a** server admin
**I want to** set custom metadata (title, cover art, description, screenshots) for promoted ROM hacks
**So that** these games look polished in the library even though they have no IGDB or other scraper coverage.

**Acceptance criteria:**
- [ ] Admin can edit title, description, cover art, and screenshots for promoted ROM hack game entries
- [ ] Admin can upload a custom cover art image for the game
- [ ] If the hack happens to have an IGDB entry, the admin can trigger a scrape to pull metadata automatically
- [ ] Custom metadata overrides scraped metadata (so the admin can fix any mismatches)

**Notes:**
- Most total conversion ROM hacks are not in IGDB, so manual metadata entry is the primary path
- Cover art for popular ROM hacks is often available from community sites -- the admin may upload images sourced from there
- This story reuses the existing metadata editing system but ensures it works well for promoted hack entries

### Story 4.6: Total conversion favorites, play history, and collections

**As a** player
**I want** promoted ROM hacks to behave like any other game in the library for favorites, play history, collections, and other features
**So that** my experience is consistent regardless of whether a game is an original or a hack.

**Acceptance criteria:**
- [ ] Promoted ROM hacks can be favorited independently of the parent game
- [ ] Play history and play time are tracked separately for the hack (it is a different game entry with its own ID)
- [ ] Promoted ROM hacks can be added to user collections
- [ ] Promoted ROM hacks appear in "recently played", "most played", and other library views
- [ ] Promoted ROM hacks can be rated and reviewed independently

**Notes:**
- Since promoted hacks are full game entries, most of this should work automatically -- this story ensures nothing is accidentally excluded
- The one thing that does NOT carry over is RetroAchievements (unless RA happens to have hashes for the hack)

### Story 4.7: Search and discoverability for promoted hacks

**As a** player
**I want to** find ROM hacks by searching for their titles
**So that** I can quickly locate a hack I have heard about (e.g., searching for "Pokemon Unbound").

**Acceptance criteria:**
- [ ] Promoted ROM hacks appear in search results when searching by their title
- [ ] Search results for a promoted hack show it as a standalone game entry (not as a patch of the parent)
- [ ] Minor patches (non-promoted) do NOT appear in search results -- they are only discoverable from the parent game's detail page
- [ ] If a player searches for a parent game title (e.g., "Pokemon FireRed"), the parent game appears in results but its ROM hacks do not clutter the results (they are discoverable from the parent's detail page)

**Notes:**
- This distinction is important for keeping search results clean -- minor patches should not pollute search
- Total conversions deserve to be searchable because users look for them by name

---

## Phase 5: Player App Integration

**Goal:** Bring patch discovery, selection, and patched game play to the native player app (Kotlin Multiplatform / Compose Multiplatform). The player app needs to display patches, allow selection, download the correct ROM version, and isolate saves.

### Story 5.1: View available patches in player app

**As a** player using the native app
**I want to** see available patches for a game on the game detail screen
**So that** I can discover and consider patches from within the player app.

**Acceptance criteria:**
- [ ] The game detail screen in the player app shows a "Patches" section listing available patches for the game
- [ ] Each patch entry shows: name, author, type badge, description, and version
- [ ] Translation patches are shown in a separate "Languages" section above the Patches section, consistent with the web UI (Phase 3)
- [ ] The sections are hidden when no patches are available
- [ ] The data is fetched from the server API and follows the same structure as the web frontend

**Notes:**
- The player app uses Compose Multiplatform -- all UI for this feature goes in commonMain (shared between Android and desktop)
- The layout should follow the player app's existing design patterns (Sp* components, SpColor tokens, etc.)

### Story 5.2: Select and apply a patch in player app

**As a** player using the native app
**I want to** select a patch and play the patched version of a game
**So that** I can experience patches without needing to use the web interface.

**Acceptance criteria:**
- [ ] The player can select a patch from the Patches section on the game detail screen
- [ ] The currently active patch is clearly indicated on the game detail screen
- [ ] When the player launches the game, the server serves the patched ROM
- [ ] The player can deactivate a patch and return to the unpatched original
- [ ] Switching patches shows a warning about save state compatibility, consistent with the web UI

**Notes:**
- Patching happens server-side -- the player app just requests the correct ROM version from the server
- The API should return the download URL for the patched ROM when a patch is active, or the original ROM when no patch is active

### Story 5.3: Download patched ROM in player app

**As a** player using the native app
**I want to** download the patched ROM to play offline
**So that** I can play patched games without needing a constant server connection.

**Acceptance criteria:**
- [ ] When a patch is active, the download manager downloads the patched ROM (not the original)
- [ ] If the player changes patches, the old patched ROM is replaced by the new one (not kept alongside)
- [ ] If the player deactivates all patches, the original ROM is downloaded (or kept if already downloaded)
- [ ] The downloaded games list shows which patch (if any) is active for each downloaded game
- [ ] Download size shown before downloading reflects the patched ROM size

**Notes:**
- For Phase 5, the player app downloads the pre-patched ROM from the server -- client-side patching (downloading the base ROM + patch file separately) is a future optimization
- Storage management is important on mobile -- keeping multiple large patched ROMs is wasteful

### Story 5.4: Save state isolation in player app

**As a** player using the native app
**I want** my save states to be isolated by patch configuration in the player app
**So that** saves from the patched version do not interfere with the unpatched version.

**Acceptance criteria:**
- [ ] Save states uploaded to the server from the player app include the active patch configuration in their metadata
- [ ] When loading saves, only saves matching the current patch configuration are shown
- [ ] SRAM saves are isolated by patch configuration in local storage
- [ ] When syncing saves with the server, the patch configuration is preserved

**Notes:**
- This mirrors the server-side save isolation from Story 2.7 but ensures the player app's local save management is consistent
- The game session system should handle most of this naturally if each patch config uses a separate session

### Story 5.5: Patch indicator in player app game library

**As a** player using the native app
**I want to** see which games have patches available when browsing the library
**So that** I can discover patch options from the home screen and console views.

**Acceptance criteria:**
- [ ] Game cards in the player app's library views show a patch indicator when patches are available, consistent with the web UI (Story 1.4)
- [ ] Games with an active patch show the active patch name on the game card or in the game detail header
- [ ] Games with a default translation active show the translation language indicator
- [ ] The indicators follow the player app's existing visual style (SpCard, SpBadge, etc.)

**Notes:**
- The player app's game cards are used in multiple screens (home, consoles, collections, search results) -- the indicator should work consistently across all of them

### Story 5.6: Translation language selector in player app

**As a** player using the native app
**I want to** choose a translation language from the game detail screen
**So that** I can switch languages naturally within the native app.

**Acceptance criteria:**
- [ ] The game detail screen shows a language selector for games with translation patches, consistent with the web UI (Story 3.2)
- [ ] The selector shows available languages with visual indicators (flags or language codes)
- [ ] Selecting a language activates the corresponding translation patch
- [ ] The default translation (if set by admin) is pre-selected on first view
- [ ] The currently active language is clearly indicated

**Notes:**
- The language selector should feel native to the platform (Compose Multiplatform components, not web-style dropdowns)
- The interaction should be consistent with other selection UIs in the player app

### Story 5.7: View promoted ROM hacks in player app

**As a** player using the native app
**I want to** see and browse promoted ROM hacks as standalone games
**So that** total conversions feel like real games in my library, not second-class citizens.

**Acceptance criteria:**
- [ ] Promoted ROM hacks appear in the player app's library alongside regular games
- [ ] They are searchable by their own title
- [ ] Their game detail screen shows the "Based on" link to the parent game (Story 4.2)
- [ ] The parent game's detail screen shows a "ROM Hacks" section linking to promoted hacks (Story 4.3)
- [ ] Promoted hacks can be favorited, rated, and added to collections from the player app

**Notes:**
- Since promoted hacks are regular game entries on the server, most of this should work automatically
- The "Based on" link and "ROM Hacks" section require new UI sections in the game detail screen

---

## Cross-Phase Considerations

These are not standalone stories but important behaviors that span multiple phases. Developers should reference these when implementing the related stories.

### Scanner interaction

- The game scanner ignores the patched ROM cache directory -- cached patched ROMs are not library entries.
- Pre-patched ROMs that the admin has placed in the games directory continue to work through the existing variant/grouping system. This is an existing workflow and is not changed by the patch system.
- After each scan, the system re-runs CRC32 matching to pick up newly discovered games that may match existing RHDN patches (Story 1.2).

### Play history aggregation

- Minor patches: play time is attributed to the parent game. Whether the player uses the vanilla ROM or a bugfix patch, the play time goes to the same game entry.
- Total conversions (promoted hacks): play time is tracked on the promoted game entry, separate from the parent.

### Activity feed

- Applying or removing a patch does not generate an activity event. It is a configuration change, not a social action.
- Playing a promoted ROM hack generates normal "started playing" activity events under the hack's title.

### Favorites

- Favoriting a game is unaffected by the active patch. The favorite is on the game entry, not on a specific patch configuration.
- Promoted ROM hacks have independent favorite status.

### Library filters

- The existing library filter system should gain a "Has Patches" filter option (Phase 1).
- The "Language" filter should include translation patch languages in addition to the game's native language support (Phase 3).

### Offline play (player app)

- When playing offline with a downloaded patched ROM, the patch configuration is fixed to whatever was active at download time.
- The player cannot change patches while offline (patching requires the server).
- The save state isolation still works offline using the locally stored patch configuration.
