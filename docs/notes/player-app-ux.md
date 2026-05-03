# Spela Player App - UX Flow Description

> This document describes the user experience of the **Spela** Android player app
> in detail, suitable for use as a prompt for AI design tools like Google Stitch.
> It covers the full flow from app launch (already signed in) through game
> browsing, game detail, and gameplay, including the secondary companion screen.

---

## App Overview

Spela is a self-hosted retro game emulation app. Think of it like Plex, but for
retro games. Users connect to their own Spela server, browse their game library
organized by console (NES, SNES, Game Boy, PlayStation, etc.), and play games
natively on their device using libretro emulation cores.

The app is designed primarily for Android gaming handhelds (landscape orientation,
physical controls + touchscreen) but also works on phones and tablets.

---

## Visual Design Language

### Color Palette (Dark Theme - Default)

The app uses a rich dark theme with vibrant accent colors:

- **Primary**: Deep electric indigo (#6C5CE7), lighter variant (#9B8FEF)
- **Secondary**: Vivid coral/rose (#FF6B81)
- **Accent**: Electric cyan (#00D2FF)
- **Background**: True dark with subtle blue tint (#0A0A10)
- **Surface**: Slightly elevated dark (#12121C)
- **Surface Elevated**: Cards and panels (#222236)
- **Card Background**: Deep navy (#16162A) with 1dp border (#2A2A3A at 50% opacity)
- **Text Primary**: Near-white (#F0F0F8)
- **Text Secondary**: Muted lavender (#B0B0C8)
- **Text Tertiary**: Dim purple-gray (#707090)
- **Favorite/Heart**: Red (#FF4757)
- **Rating/Star**: Gold (#FACC15)
- **Success**: Green (#00C853)

Each retro console has its own brand color used for badges, gradients, and
accents (NES = red, SNES = purple, Game Boy = green, N64 = green, PlayStation = blue, etc.).

### Typography

Clean sans-serif hierarchy:
- Display: 28-48sp bold (hero text)
- Headline: 18-24sp semibold (section headers, top bar)
- Title: 12-16sp medium (card titles)
- Body: 12-16sp normal (descriptions)
- Label: 10-14sp semibold (buttons, chips, nav labels)

### Spacing & Corners

- Screen horizontal padding: 20dp
- Card corner radius: 16dp
- Button corner radius: 12dp
- Chip corner radius: 20dp (pill shape)
- Section gaps: 20-24dp vertical

### Component Styling

- **Cards**: 16dp rounded corners, dark navy background, subtle 1dp border, 4dp elevation. On hover/focus: scale to 1.02x, shadow increases to 12dp. On press: scale to 0.97x.
- **Buttons**: 48dp min height, 12dp corner radius. Primary = indigo fill + white text. Outlined = transparent with border. Ghost = text only.
- **Chips**: Pill-shaped (20dp radius), 1dp border, selected state has colored fill at 15% opacity.
- **Cover Art**: Portrait aspect ratio (~5:7), 12dp rounded corners, shimmer animation while loading, fallback shows first 2 letters of game title.

---

## Screen Flow: App Launch to Gameplay

### 1. App Shell & Navigation

When the signed-in user opens the app, they see the main app shell:

**Layout**: Full-screen content area with a bottom navigation bar.

**Bottom Navigation Bar** (64dp height, dark surface background):
Six equally-spaced tabs, each with an icon (24dp) above a label (10sp):
1. **Home** (house icon) - Dashboard with personalized content
2. **Explore** (compass icon) - Discovery and browsing
3. **Consoles** (game controller icon) - Browse by console/platform
4. **Collections** (bookmark icon) - User-created game collections
5. **Activity** (bell icon) - Social activity feed
6. **Settings** (gear icon) - App preferences

The active tab's icon and label are highlighted in the primary light color (#9B8FEF). Inactive tabs use the secondary text color (#B0B0C8).

When a physical gamepad is connected, the bottom nav bar is hidden and replaced by a floating section indicator at the top of the screen showing L1/R1 shoulder button hints with the 6 tab icons. The user cycles between tabs with L1/R1.

---

### 2. Home Screen (Default Landing)

The Home screen is the first thing the user sees. It's a vertically scrolling
feed on a dark gradient background (primary-to-accent diagonal gradient, darkened).
Pull-to-refresh is supported.

**Header Area** (top):
- Spela logo icon (36dp, circular) + "Spela" title (headline text) on the left
- Search button (magnifying glass icon) on the right
- Downloads button (with notification badge dot) if downloads are active

**Content Sections** (each only appears if data exists):

**Continue Playing** (play arrow icon, "Continue Playing" header):
- Horizontal scrolling row of up to 6 wide cards
- Each card is a horizontal layout (280dp wide): game cover art on the left (84dp tall), game title (up to 2 lines), console name subtitle, and formatted play time on the right
- Tapping a card navigates to the game detail screen

**Play Later** (clock icon, "Play Later" header, "See all" link):
- Horizontal scrolling row of up to 6 vertical game cards
- Each card shows: cover art (portrait, ~140dp tall), title (2 lines max), console name, star rating (if rated), favorite heart icon (if favorited)

**Favorites** (heart icon, "Favorites" header, "See all" link):
- Same horizontal card row format as Play Later

**Recently Added** (sparkle icon, "Recently Added" header):
- Same horizontal card row format

**Recent Achievements** (trophy icon, "Recent Achievements" header, "See all" link):
- Horizontal scrolling row of achievement cards (160dp wide each)
- Each card: badge image (48dp rounded), achievement title, game title (small), points badge (gold background)

**Trending Challenges** (flame icon, "Trending Challenges" header, "See all" link):
- Horizontal scrolling row of challenge cards (220dp wide)
- Each card: screenshot hero image (16:10 ratio), difficulty chip overlay, challenge name, game title, participant count

**Top Rated** (star icon, "Top Rated" header):
- Horizontal scrolling row of game cover cards with star ratings

**Online Now** (people icon, "Online Now" header):
- Horizontal row of online user avatars (48dp circular each)
- Green online indicator dot, username below, currently-playing game name

**Recent Activity** (history icon, "Recent Activity" header, "See all" link):
- Vertical list of up to 2 activity events
- Each event: user avatar (36dp) + event icon + "[username] [action verb] [game]" text + relative timestamp

**Your Stats** (bar chart icon, "Your Stats" header, "See all" link):
- 2x2 grid of stat cards:
  - Play Time (timer icon, cyan accent) - e.g., "47h 23m"
  - Games Played (controller icon, indigo) - e.g., "28"
  - Current Streak (fire icon, amber) - e.g., "5 days"
  - Best Streak (trophy icon, gold) - e.g., "12 days"

---

### 3. Explore Screen

The Explore tab is a rich discovery experience, also a vertically scrolling feed:

**Key Sections (in order)**:
1. **Hero Carousel** - Auto-rotating featured games with large artwork
2. **Search Bar** - Tappable search entry point
3. **Browse by Console** - Quick-jump console filter buttons
4. **Mood Selector** - Interactive mood chips ("What Are You in the Mood For?")
5. **Surprise Me / Wizard** - Random game picker and guided discovery wizard
6. **For You** - Personalized recommendation rows based on play history
7. **Browse by Theme** - Grid of theme categories (Action, RPG, Puzzle, etc.)
8. **Popular Keywords** - Chip cloud of popular tags
9. **Browse by Series** - Horizontal shelf of game franchises
10. **Developer Spotlight** - Featured developer with their games
11. **Visual Discovery Gallery** - Beautiful game artwork/screenshots
12. **Trending on Your Server** - Currently popular games
13. **Community Favorites** - Top-rated games by all users
14. **On This Day** - Games released on this date in history
15. **Active Challenges** - Ongoing limited-time challenges

Each section supports skeleton loading states and links to detail/filter screens.

---

### 4. Consoles Screen

The Consoles tab shows all available gaming platforms grouped by generation.

**Layout**: Adaptive grid (1-2 columns based on screen width) of console cards.
Dark gradient background.

**Console Cards** (180dp height, rounded corners):
- Large console logo centered (or console name as text fallback)
- Bottom-left: Game count + manufacturer and release year
- Bottom-right: "Browse games" button (for consoles with 15+ games)
- Top-right: Feature badges (save state support, browser play, BIOS warning)
- Watermark: Faded console icon in bottom-right corner (7% opacity)
- Background: Console-specific branded gradient (e.g., NES card has red gradient, SNES has purple)

Tapping a console card opens the **Console Detail Screen**.

---

### 5. Console Detail Screen

Shows all games for a specific console.

**Hero Banner** (200dp, rounded corners):
- Console name/logo, generation info, media type
- Game count and feature badges
- "Browse games" button for large libraries
- Console-branded gradient background (darkened)

**Content Sections**:
- **Continue Playing** - Games you've recently played on this console
- **BIOS Warning** - Alert if required BIOS files are missing
- **Console Essentials** - Curated must-play games
- **Hidden Gems** - Underrated games for this console
- **Top Rated** - Highest-rated games
- **Top Developers** - Notable developers for this console
- **All Games Grid** - Full game grid (for small libraries) or Browse button (for large ones)

---

### 6. Game Detail Screen

When the user taps any game card, they arrive at the Game Detail screen. This is
a rich, vertically scrollable detail view.

**Hero Section** (top):
- Large background hero image with gradient overlay
- Game cover art (portrait, up to 256dp wide in landscape layout)
- **Game Title** (display text, white, up to 2 lines)
- **Badge Row** (wrapping): Console chip (colored), region chip, verification status, IGDB rating (star + score), community rating (stars + count), achievements progress ("X/Y")

**Action Buttons**:
- **Split Button** (primary action): The main button is a two-part button:
  - Left side: "Resume" (if saves exist) or "Play" (if no saves) - tapping this launches the game immediately
  - Right side: Dropdown arrow that opens a menu with:
    - "Continue from Title Screen" - keeps your in-game save but restarts from the beginning
    - "New Game" - starts a completely fresh playthrough from scratch
    - "Netplay" - start a multiplayer session (if console supports it)
    - "Delete Download" - remove the cached game file
  - If the game is not downloaded yet, the button shows "Download" with a loading state
- **Three-dot menu**: Toggle favorite, toggle play later, add to collection
- **Playtime chips**: Total play time (clock icon) + last played (history icon)

**BIOS Warning** (if applicable):
- Expandable alert showing missing BIOS file names
- Play button is disabled until BIOS files are available

**Game Description**: Body text with full game description

**Metadata Grid** (2-column layout):
- Developer (clickable to see more games)
- Publisher (clickable)
- Release year
- Genre
- Player count
- Achievements count
- File size
- Disc count (multi-disc games)

**Sessions Section**:
- List of play sessions (each is a card)
- Each session: name, "Current" badge (green), playtime, last played timestamp
- Actions per session: Duplicate, Rename, Delete, Play (with dropdown)
- "New Session" button to start a fresh playthrough
- Empty state: "No sessions yet. Press Play to start your first playthrough."

**Additional Sections** (scrolling continues):
- Time to Beat estimates (hastily, normally, completely)
- Community play activity heatmap
- Your Rating (interactive star rating)
- Community Reviews
- Screenshots (horizontal scrollable gallery, tappable for fullscreen)
- Similar Games (horizontal shelf)
- More from Developer (horizontal shelf)
- Community Shared Saves
- Game Controls configuration
- Challenges section
- Series/Franchise links

---

### 7. Emulation Screen (Playing a Game)

When the user presses Play/Resume, the entire UI transitions to the emulation
screen. The bottom nav bar disappears and the game fills the screen.

**Main View**: The game renders at full screen. On Android, this is either
GPU-accelerated (Vulkan) or CPU-rendered (Canvas), depending on the emulation
core. Video shaders (CRT, scanlines, LCD grid, etc.) can be applied.

**Always-Visible HUD** (minimal, non-intrusive):
- **Top-right corner**: Small FPS counter pill (color-coded: green for 60+, yellow for 30-59, red for <30). This also serves as a menu button - tapping it opens the in-game overlay.
- **Top-left corner** (context-dependent):
  - During netplay: Opponent username + ping/latency indicator
  - During challenges: Elapsed timer

**Touch Controls** (Android only, auto-hide when gamepad connected):
- Virtual D-pad on the left side of the screen
- A/B action buttons on the right
- Shoulder buttons (L/R) at the top
- Start/Select buttons
- These disappear automatically when a physical controller is being used

**For DS/3DS Games**: The screen shows the top display, with the bottom
touchscreen area receiving touch input and mapping it to the emulated touch
digitizer.

---

### 8. In-Game Overlay Menu

Tapping the FPS button or pressing the menu/back button opens a centered overlay
panel on top of the paused game.

**Appearance**: Full-screen dark scrim (semi-transparent black) with a centered
rounded panel.

**Panel Content**:

**Header**: Game title

**Performance Stats**: FPS badge + frame time badge

**Active Save Slot Indicator**: Shows which slot is selected

**Action Buttons** (icon + label, arranged in a flowing grid):
- **Save** - Save state to current slot
- **Load** - Load state from current slot
- **Rewind** - Rewind gameplay
- **Screenshot** - Capture the screen
- **Fast Forward** - Toggle speed-up (icon changes between play/fast-forward)
- **Challenge** - Start a timed challenge
- **Cheats** - Open cheat browser/toggles
- **Controls** - Remap buttons or configure gamepad ports

**Volume Slider**: Horizontal slider with percentage label

**Bottom Buttons**:
- "Exit Game" (outlined/secondary button, left)
- "Continue" (primary indigo button, right, focused by default)

**In Netplay Mode** (restricted):
- Shows ping (latency) and session duration instead of save/load buttons
- Only "Controls" action available
- "Leave Session" replaces "Exit Game"
- "Resume" replaces "Continue"

**In Challenge Mode** (restricted):
- Large challenge timer display
- Challenge objective text
- Buttons: Mark Complete, Restart, Give Up, Controls
- "Resume" button

---

### 9. Secondary Companion Screen

For devices with a second display (like the AYN Thor gaming handheld with a
3.92" clamshell secondary screen), the app provides a rich companion experience.

**Persistent Header** (always visible on second screen):
- Thin gradient accent line at top (colored to match the current console)
- Game title text
- Session elapsed time (HH:MM or MM:SS format)
- Console badge (e.g., "SNES", "NES")

**Swipeable Pager** (4 pages, indicated by dots at the bottom):

**Page 1 - Art Display**:
- Large hero artwork for the current game (16:9 aspect ratio)
- If no artwork: gradient with console colors as fallback
- Game metadata card below:
  - Developer, Publisher, Release date, Genre
  - Rating, Player count
  - Game description text
- Scrollable for smaller screens

**Page 2 - Controls**:
- P1/P2 port selector toggle at top
- Full touch gamepad overlay (D-pad, action buttons, shoulders, start/select)
- Allows the secondary screen to act as a controller input surface

**Page 3 - Dashboard**:
- Performance stat cards:
  - FPS (color-coded health indicator)
  - Frame time
  - Active save slot
  - Cheats count (if any enabled)
  - Achievement progress (if available)
- Quick action buttons:
  - Save / Load / Screenshot / Fast Forward / Rewind
  - Cheat toggle (expandable cheat list sub-panel)
  - Challenge-specific actions when a challenge is active
- Netplay info (opponent name, latency, pause state)

**Page 4 - Save Slots**:
- Visual grid of save state slots
- Each slot: screenshot thumbnail preview, timestamp, fill indicator
- Actions: Save to slot, Load from slot, Select active slot

**Additional Companion Screen Features**:
- **OLED burn-in protection**: Screen dims to black after 15 seconds of no touches
- **Pause overlay**: Shows large "PAUSED" text with session info when game is paused
- **Achievement celebrations**: Animated overlay when an achievement is unlocked
- **Toast notifications**: Color-coded pills (green = saved, blue = loaded) appear above the page dots
- **DS/3DS mode**: When playing a dual-screen game, the companion screen shows ONLY the bottom screen with direct touch-to-emulation mapping (no UI chrome)

---

## Summary of the Complete User Journey

1. **App opens** -> Home screen with personalized dashboard (continue playing, favorites, stats, activity)
2. **User browses** -> Scrolls Home feed, or switches to Explore (discovery), Consoles (by platform), or Collections tabs
3. **User finds a game** -> Taps a game card anywhere in the app
4. **Game Detail loads** -> Hero with cover art, metadata, badges, split play button, sessions, description, ratings, screenshots, similar games
5. **User presses Play** -> Split button: "Resume" continues latest session, dropdown offers "Continue from Title Screen" or "New Game"
6. **Emulation starts** -> Full-screen game with minimal FPS HUD, optional touch controls, shader effects applied
7. **During gameplay** -> User can tap FPS button to open overlay menu (save/load/screenshot/volume/exit), or use the companion second screen for artwork, controls, dashboard, and save management
8. **User exits** -> Overlay menu -> "Exit Game" -> returns to Game Detail screen with updated play time and session data
