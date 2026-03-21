# Improvements

Append-only log of potential improvements spotted during development.
Each entry includes the date, area, and a brief description. Used as
input for planning and prioritization.

Format: `- YYYY-MM-DD [area] Description`

Areas: `server`, `web`, `player`, `infra`, `ux`, `testing`, `docs`, `architecture`

---

- 2026-03-20 [player] ConsoleScreen.kt has many unused imports (AnimatedVisibility, expandVertically, fadeIn, fadeOut, shrinkVertically, CircleShape, Column, Row, items, Check, Close, Search, SwapVert, DropdownMenu, DropdownMenuItem, mutableStateOf, rememberSaveable, FocusRequester, focusRequester, Role, role, semantics, SpSearchField, ConsoleGenreBreakdown, GameGridItem, SpTypography). These should be cleaned up.
- 2026-03-20 [player] SecondaryArtPage.kt and SecondaryScreenContent.kt still use `consoleId.uppercase()` for console labels — should be reviewed to determine if those contexts are genuinely space-constrained or should also use `consoleName`.
- 2026-03-20 [player] ConsoleShowcaseSections.kt — all four section composables (ConsoleEssentials, ConsoleHiddenGems, ConsoleTopDevelopers, ConsoleRecentlyPlayed) add `.padding(horizontal = SpSpacing.ScreenHorizontal)` on their root modifier. This violates the "components never control their own outer spacing" principle. The parent (ConsoleScreen) should control horizontal padding instead.
