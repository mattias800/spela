# ROM Hacks & Patches: Product Analysis

This document explores all the use cases around ROM hacks, patches, and mods in Spela, and proposes how each should be handled from a user's perspective. This is a discussion document -- not a final spec.

---

## Current State of the Codebase

Before diving into proposals, here is what exists today that is relevant:

- **Game model**: Each game has a `FilePath` (relative to game dirs), `FileName`, `FileSize`, `ConsoleID`, `GroupKey`, `IsPrimary`, `Tags`, `Region`, `Revision`, `CRC32`, `VerificationStatus`, `ScraperID` (IGDB link), and rich metadata (description, cover, screenshots, etc.).

- **Variant grouping**: Games are grouped by `(console_id, group_key)` where `group_key` is a normalized title. Within each group, one game is elected "primary" (shown in the library by default). Variants are things like different regions, revisions, or tagged versions (beta, proto, hack, etc.).

- **Tag system**: The filename parser already recognizes `(Hack)` as a tag. Games tagged `hack` are stored like any other game. They participate in variant grouping -- meaning a hack of "Super Mario World" currently groups with the original game because they share the same normalized title/group key.

- **ROM storage**: ROMs live on the filesystem in console-named directories. The scanner discovers them by walking game directories and matching file extensions to consoles. ROMs are never modified -- they are served as-is to the player.

- **Save states**: Stored per-user, per-game-ID. A different game ID means a separate save state pool. Save states from one ROM are generally not compatible with a patched version of that ROM.

- **Netplay**: Both shared sessions and real-time netplay are tied to a `GameID`. Both players in netplay must be running the exact same ROM.

- **ROM replacement**: Admins can replace a game's ROM file via the API, which updates the file on disk and re-verifies CRC32 against DAT files.

- **Scraping**: Games are scraped against IGDB for metadata. The scraper uses normalized title matching.

---

## Complete Use Case Taxonomy

### Category 1: Minor Gameplay Patches

**What**: Small binary patches that tweak the original game without fundamentally changing it.

Sub-types:
- **Bugfix patches** -- Fix crashes, softlocks, or glitches in the original game (e.g., fixing the Everdrive save issue in Pokemon Crystal).
- **Quality-of-life (QoL) patches** -- Reduce grinding, add fast-forward, remove unskippable cutscenes, rebalance difficulty.
- **Uncensoring patches** -- Restore content that was removed for a specific region release (e.g., restoring blood in the US version of a game).
- **Difficulty hacks** -- "Hard mode" or "easy mode" patches.
- **Cosmetic patches** -- Sprite changes, palette swaps, font improvements.
- **Widescreen patches** -- Modify rendering for widescreen aspect ratios.

**Key characteristic**: The game is still recognizably the same game. Same title, same story, same structure. The user thinks of it as "Super Mario World, but with the softlock fixed."

### Category 2: Translation Patches

**What**: Patches that translate a game from one language to another, typically Japanese-to-English for games that were never officially localized.

Sub-types:
- **Full translations** -- Complete text, menu, and sometimes voice/graphic translation.
- **Partial translations** -- Menus and UI translated but dialogue left untranslated.
- **Retranslations** -- A new, more accurate translation replacing an existing official one (e.g., retranslating Final Fantasy IV to be closer to the Japanese original).

**Key characteristic**: The game itself is unchanged -- only the language is different. For many users, this is THE way they will play this game. A fan translation of a Japan-only RPG is functionally a new regional release.

### Category 3: Total Conversion Hacks / New Games

**What**: ROM hacks so extensive that they are effectively new games built on another game's engine.

Examples:
- Chrono Trigger: Crimson Echoes (new story using CT's engine)
- Pokemon Prism, Pokemon Unbound (entirely new Pokemon games)
- Super Mario Star Road (new levels, new story, new everything)
- Hyper Metroid (complete reimagining of Super Metroid)
- The Legend of Zelda: Parallel Worlds

**Key characteristic**: These have their own identity -- their own title, often their own cover art and community. They just happen to require a base ROM to build. A user looking for "Pokemon Unbound" does not think of it as a variant of "Pokemon FireRed."

### Category 4: Enhancement / DX Patches

**What**: Patches that upgrade a game's technical capabilities, often crossing console boundaries.

Sub-types:
- **Color patches** -- Adding color to a Game Boy game (e.g., GB game modded to run as a GBC game with color palettes).
- **Audio enhancement** -- Adding enhanced audio, e.g., MSU-1 patches for SNES games that replace chiptune music with CD-quality audio.
- **Resolution/framerate patches** -- Unlocking higher resolution or framerate on later-generation consoles.
- **Restoration patches** -- Restoring cut content from an earlier version of the game.

**Key characteristic**: The game identity is the same, but the technical delivery is enhanced. Sometimes the platform changes (GB -> GBC), which is a significant distinction because it affects which core/emulator is used.

### Category 5: Randomizers

**What**: Programs/patches that randomize elements of a game for replayability.

Examples:
- A Link to the Past Randomizer
- Final Fantasy Randomizer
- Super Metroid Randomizer
- Pokemon Randomizers

**Key characteristic**: Each randomized ROM is a unique, one-time-use output. The user generates a seed, applies it to a base ROM, and gets a unique ROM. This is fundamentally different from a static patch -- randomizers produce a new ROM every time.

### Category 6: ROM Hacks with IGDB Entries

**What**: ROM hacks that have become notable enough to have their own IGDB database entries (and sometimes their own cover art, screenshots, and ratings).

Examples: Some major Pokemon ROM hacks, notable total conversions.

**Key characteristic**: These can be scraped like regular games and get full metadata automatically. They blur the line between "hack" and "independent game."

---

## Cross-Cutting Concerns

### Save State Compatibility

A patched ROM is a different binary. Save states created on the original ROM will almost certainly crash or corrupt when loaded on the patched ROM (and vice versa). SRAM/battery saves are more resilient but still not guaranteed.

**Implications**:
- If a patch creates a new game entry, saves are naturally isolated (different game ID = different save directory).
- If a patch replaces the existing ROM in-place, existing saves become potentially dangerous. The user needs to be warned.
- If patches are applied on-demand (at download/launch time), the player app needs to know which variant is active and route saves accordingly.

### Netplay Compatibility

Both players in a netplay session must run the exact same ROM binary. If player A applies a patch and player B does not, netplay will desync.

**Implications**:
- Netplay sessions are tied to a game ID. If the patched version is a separate game entry, this works naturally.
- If patches are applied on-demand, the system must ensure both players apply the same patch (or neither).

### Metadata & Discovery

- Minor patches generally inherit the parent game's metadata (title, cover, description).
- Translations might want a modified title ("Final Fantasy V (English Translation by RPGe)") but the same cover.
- Total conversions need entirely separate metadata -- their own title, cover art, description, etc.
- Randomizers have no stable metadata since each output is unique.

### Legal & Distribution Considerations

- Spela is self-hosted. The admin controls what is in their library.
- Patch files (.ips, .ups, .bps, .xdelta) are legal to distribute. Pre-patched ROMs are in a gray area.
- Some admins will have pre-patched ROMs already in their library. Others may want to apply patches to base ROMs they already own.
- The system should support both workflows without making assumptions about how the admin obtained their files.

---

## Proposed UX for Each Category

### Category 1: Minor Gameplay Patches -- "Patch variants"

**Proposal**: Treat these as patch files that can be attached to an existing game and applied on-demand.

**User journey (admin)**:
1. Admin navigates to the game detail page for "Super Mario World."
2. In a "Patches" section, admin clicks "Add Patch."
3. Admin uploads a .ips/.bps/.ups/.xdelta file with a name and description (e.g., "Softlock fix v1.2").
4. The patch is stored on the server, associated with the parent game.

**User journey (player)**:
1. User opens the game detail page for "Super Mario World."
2. They see a "Patches" section listing available patches with descriptions.
3. They can toggle patches on/off before launching.
4. When launching, the server (or player app) applies the selected patches to the base ROM on-the-fly, producing a patched ROM in memory/temp storage.
5. Save states are namespaced by the active patch configuration.

**Why not separate game entries?** Because these are minor tweaks. Having 5 variants of the same game cluttering the library creates noise. The user mental model is "I am playing Super Mario World with some fixes enabled" -- not "I am playing a different game."

**Alternative considered**: Pre-patched ROMs in the filesystem. This works too (the scanner would pick them up as variants via the existing grouping system), but it is less elegant. The admin would need to patch the ROM externally and drop it in. We should support this via the existing variant system AND also build the patch-attachment system.

### Category 2: Translation Patches -- "Language variants"

**Proposal**: Treat these as a special case of patches, but with higher visibility and different presentation.

**User journey**:
1. Admin uploads a translation patch for "Final Fantasy V (Japan)" and marks it as a "Translation" patch with target language "English."
2. In the library, the game can be filtered/sorted by available languages.
3. On the game detail page, the translation appears prominently -- not buried in a "patches" section but shown as an available language option.
4. When the user launches with the translation patch, the patched ROM is produced on-the-fly.

**Important consideration**: For many users, the translated version IS the definitive version. It should be possible for the admin to set a translation as the "default" launch option, so users do not have to consciously select it every time.

**Region handling**: A translation patch should NOT change the game's region metadata. The base ROM is a Japan-region game. The fact that it has an English translation patch available is additional metadata, not a region change.

**What about pre-translated ROMs?** Some admins will have pre-patched translated ROMs (e.g., "Final Fantasy V (Japan) (En).sfc"). These would be picked up by the scanner as a separate game entry with a "(Hack)" or no specific tag. The existing variant system handles this already -- it would group with the Japanese original. The admin may want to manually edit the title/metadata to clarify what it is.

### Category 3: Total Conversion Hacks -- "Standalone hack games"

**Proposal**: These should be full, independent game entries in the library, potentially with their own metadata.

**User journey (pre-patched ROM on filesystem)**:
1. Admin drops "Pokemon Unbound (Hack).gba" into their GBA games folder.
2. Scanner picks it up as a new game. The `(Hack)` tag is parsed.
3. The normalized title "pokemon unbound" does NOT match any existing game's group key, so it becomes its own standalone entry.
4. If it has an IGDB entry, the scraper finds metadata for it.
5. If it has no IGDB entry, the admin can manually set title, cover art, and description.
6. It appears in the library as its own game.

**User journey (patch applied to base ROM)**:
1. Admin navigates to "Pokemon FireRed" and uploads a total conversion patch.
2. The system recognizes this as a "total conversion" patch (either by admin tagging it as such, or by heuristic -- e.g., the patch has its own title).
3. A NEW game entry is created in the library with its own title, linked to the parent game for provenance.
4. The new game's ROM is either:
   a. Generated on-demand by applying the patch to the base ROM (saves disk space, but requires the base ROM), or
   b. Generated once and stored as a pre-patched ROM (simpler, but uses more disk space).

**Why separate entries?** Because total conversions have their own identity. Grouping "Pokemon Unbound" as a variant of "Pokemon FireRed" would be confusing. Users search for these hacks by name.

**Open question**: Should there be a visible link between the hack and its parent game? Probably yes -- a "Based on Pokemon FireRed" note on the game detail page, and perhaps a "ROM Hacks" section on the parent game's detail page. This helps discovery.

### Category 4: Enhancement / DX Patches -- "Enhancement patches"

**Proposal**: These are tricky because they sometimes change the platform.

**Sub-case A: Same platform enhancement** (e.g., MSU-1 audio for SNES):
- Treat like a minor patch (Category 1). The enhanced version is a patch variant of the same game.
- The user toggles it on/off in the patches section.

**Sub-case B: Cross-platform enhancement** (e.g., GB -> GBC color patch):
- The output ROM runs on a different emulator core. A Game Boy ROM with a GBC color patch needs to be run on a GBC core, not a GB core.
- **Proposal**: This should create a new game entry under the target console (GBC), linked to the parent game (GB). This is because the console determines which core is used, which cores are available, which save format is used, etc.
- The new entry could show "Enhanced version of [Game Boy title]" in its metadata.

**Open question**: Should the system automatically detect when a patch changes the platform? This would require understanding patch formats deeply. It may be simpler to have the admin specify the target console when uploading a cross-platform patch.

### Category 5: Randomizers -- "Randomized sessions"

**Proposal**: Randomizers are fundamentally different from static patches. They need a dedicated workflow.

**User journey**:
1. Admin enables randomizer support for specific games (e.g., "A Link to the Past supports randomizer mode").
2. This likely involves configuring a randomizer tool/service -- either a local binary or a web API.
3. The user goes to the game detail page and clicks "Randomize."
4. The system prompts for randomizer settings (seed, difficulty, item pool, etc.).
5. A randomized ROM is generated and stored temporarily (with its seed as identifier).
6. The user plays the randomized ROM. Saves are tied to this specific seed.
7. Randomized ROMs could be auto-cleaned after some time period, or kept if the user bookmarks them.

**Complexity assessment**: This is BY FAR the most complex use case. It requires:
- Integration with randomizer tools (each game has its own randomizer with its own settings).
- Temporary ROM generation and lifecycle management.
- Seed-based save state management.
- Potentially multiplayer support (shared randomizer seeds for races).

**Recommendation**: Defer randomizer support to a later phase. The other categories cover the vast majority of use cases. Randomizers could be handled in the near term by the admin pre-generating randomized ROMs and dropping them in the games folder as standalone entries.

### Category 6: IGDB-Listed Hacks -- "Recognized hacks"

**Proposal**: These are just Category 3 (total conversions) that happen to have IGDB metadata. No special handling needed beyond what the scraper already does.

The existing scraper should be able to find IGDB entries for notable hacks. If the hack is in the library as a standalone entry with the right title, it will get scraped and enriched automatically.

**Enhancement**: The scraper could be taught to recognize that a game tagged `hack` might match an IGDB "mod" entry. This is a scraper improvement, not a data model change.

---

## Patch File Management

### Patch Formats

The system should support the common patch formats:
- **IPS** -- Simple, oldest format. No checksum verification. Can silently produce garbage if applied to the wrong base ROM.
- **UPS** -- Improved IPS. Includes CRC32 checksums for both source and target ROMs.
- **BPS** -- Modern format. Includes checksums. Generally preferred.
- **xdelta** -- Very efficient for large patches. Common for CD-based game mods.

**Recommendation**: Support all four. BPS and UPS are preferred because they include checksums, which lets the system verify that the patch is being applied to the correct base ROM. IPS patches should show a warning that compatibility cannot be verified.

### Patch Storage

Patches should be stored separately from ROMs:
- `patches/` directory alongside `saves/`, `images/`, etc.
- Each patch file stored with its metadata (format, target game, checksums, description).
- Patch database model linking patches to games.

### Patch Application

Two approaches:

**On-demand patching (recommended)**:
- When a user launches a game with patches selected, the server applies the patch(es) to the base ROM and serves the result.
- The patched ROM is cached (keyed by base ROM CRC + patch hash) to avoid re-patching on every launch.
- Pro: Saves disk space. Only the small patch file is stored, not a full copy of the ROM.
- Pro: Base ROM stays clean and verified.
- Con: Requires patching logic on the server (or in the player app).
- Con: Slight first-launch delay for patching.

**Pre-patched ROM storage**:
- Admin applies the patch externally and drops the result in the games folder.
- Scanner picks it up as a variant.
- Pro: Simple, no patching logic needed.
- Con: Wastes disk space (full ROM copy for every patch).
- Con: No way to track provenance (which base ROM + which patch produced this).

**Recommendation**: Support both. Build the on-demand patching system for the admin-uploaded-patch-file workflow, and continue supporting pre-patched ROMs through the existing scanner/variant system.

---

## Data Model Considerations (High-Level)

The following are product-level entity descriptions, not database schema proposals:

### Patch Entity
- Links to a parent game
- Has a type: "bugfix", "qol", "translation", "cosmetic", "enhancement", "total_conversion", "other"
- Has a patch file (stored on the server)
- Has metadata: name, description, author, version, URL (to patch homepage)
- Has target language (for translation patches)
- Has target console (for cross-platform enhancement patches, if different from parent)
- Has source ROM checksum (for BPS/UPS verification)
- Can be marked as "default" (auto-applied unless the user opts out)

### Patched Game Entry (for total conversions and cross-platform patches)
- A regular Game entry in the library
- Has a `parentGameID` linking back to the base game
- Has a `patchID` linking to the patch that produced it
- Appears independently in the library, with its own metadata
- ROM is either stored pre-patched or generated on-demand from parent + patch

---

## Interaction with Existing Features

### Favorites
- If a user favorites a game, and later applies a patch, the favorite should stick (it is the same game, just patched).
- Total conversions are separate games, so they have separate favorite status.

### Play History & Play Time
- Minor patches: Play time should aggregate to the parent game. Whether you play vanilla or patched, it is the same game.
- Total conversions: Separate play history (different game entry).

### Collections
- Users can add any game to a collection. A total conversion is a separate game and can be added independently.
- Minor patches do not create new game entries, so they do not affect collections.

### Challenges
- Challenges are tied to a game ID and a save state. If the challenge was created on a vanilla ROM, participants must play the vanilla ROM (no patches).
- Patches should be locked for challenge participation.

### Achievements (RetroAchievements)
- RA requires unmodified ROMs. Any patched ROM will fail RA hash checks.
- The system should warn users that enabling patches will disable achievements.
- For total conversions that are separate games: they simply will not have RA support unless RA has hashes for them.

### Search
- Minor patches should not appear in search results as separate entries. Users search for the game, then discover patches on the detail page.
- Total conversions should be fully searchable by their own title.

### Shared Saves
- Shared saves are tied to a game ID. Users sharing saves on a patched game need to be on the same patch version.
- The shared save metadata could include which patches were active when the save was created.

---

## Phased Implementation Suggestion

### Phase 1: Pre-Patched ROM Support (already mostly works)
- Improve handling of `(Hack)` tagged games in the scanner.
- Allow admins to manually set metadata for hacks that do not have IGDB entries.
- Improve the variant system to distinguish hacks from regional variants in the UI (e.g., "Hacks" sub-section on the game detail page, separate from "Regions").
- Add the ability for admins to "promote" a hack from a variant to a standalone game entry.

### Phase 2: Patch File Upload & On-Demand Patching
- Patch entity model and admin upload UI.
- Server-side patching engine (IPS, BPS, UPS, xdelta).
- Patch selection UI on the game detail/launch screen.
- Save state namespacing by active patch configuration.
- CRC verification for BPS/UPS patches.

### Phase 3: Translation Patch UX
- Language metadata for translation patches.
- Language filter/display in the library.
- Default patch configuration (so translated version launches by default).

### Phase 4: Total Conversion Workflow
- Patch-to-game promotion (apply a patch and create a standalone game entry).
- Parent game linking and "Based on" display.
- Cross-platform patch support (target console override).

### Phase 5: Randomizer Support (future)
- Randomizer tool integration.
- Seed-based ROM generation and management.
- Multiplayer race support with shared seeds.

---

## Decisions Made

1. **Patching location**: **Server-side** (open to revisiting later, but server is simpler — one implementation).

2. **Patches are per-variant**: A patch targets a specific ROM variant (identified by CRC32), not the abstract game group.

3. **Patch stacking**: **One patch at a time**. No multi-patch stacking — too easy for users to break things.

4. **Patch visibility**: **All users see all patches**. No per-user access control.

5. **Scanner interaction**: Scanner ignores patched output (temp/cache files). Pre-patched ROMs in the games folder continue to work through the existing variant system.

6. **Patch versioning**: Track patch versions. Warn users when upgrading a patch that saves from the previous version may not be compatible.

7. **Community patch sources**: **Yes — integrate with RHDN (romhacking.net)**. If runtime API is not available, bundle patch metadata at build time. See "RHDN Integration" section below for research findings.

---

## Open Questions (Remaining)

1. **How does this interact with the game scanner?** If patches are applied on-demand, the scanner should ignore the patched output (it is a temp file, not a library game). If pre-patched ROMs are in the games folder, the scanner should detect and handle them gracefully.

2. **What about patch versioning?** If a patch author releases v1.1 of their patch, how do we handle the upgrade? Users with saves on v1.0 may not be compatible with v1.1. We probably need patch version tracking and migration warnings.

3. **RHDN integration approach**: Runtime API vs build-time bundling — depends on RHDN API availability (research pending).
