# Historical Notes

Working notes, feature specs, and UX plans that pre-date or live alongside
the implementation. Kept here for archaeology — the code is the source of
truth for current behaviour.

| File | What it is |
|------|------------|
| [`challenges-spec.md`](challenges-spec.md) | Original spec for the Game Challenges feature (now shipped). |
| [`igdb-admin-ux-plan.md`](igdb-admin-ux-plan.md) | UX plan for the IGDB admin configuration screens. |
| [`player-app-ux.md`](player-app-ux.md) | Walkthrough of the Android player app's UX flow. |
| [`save-ideas.md`](save-ideas.md) | Brainstorm of save-state feature ideas (some shipped). |
| [`social-features-history.md`](social-features-history.md) | Original social features brainstorm; some sections include an "Implementation History" once they shipped. |
| [`hw-render-core-support.md`](hw-render-core-support.md) | Phase 4 status sheet for libretro hardware-render integration (per-core OpenGL/Vulkan support). |
| [`n64-android.md`](n64-android.md) | N64 Vulkan HW render on Android — debugging status. |
| [`n64-macos.md`](n64-macos.md) | N64 OpenGL HW render on macOS — investigation notes. |

If you find one of these contradicts current code, the code wins. Update or
delete the doc rather than backporting a bug.
