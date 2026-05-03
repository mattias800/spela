# N64 OpenGL HW Render on macOS — Investigation Notes

## Problem

N64 games (mupen64plus_next core via GLideN64) require OpenGL hardware rendering (`RETRO_ENVIRONMENT_SET_HW_RENDER`). Our frontend creates an offscreen CGL OpenGL context, the core renders into it, and we read back pixels via `glReadPixels` for the Metal display pipeline.

Three issues were identified:
1. **Upside-down image** — FIXED (GL has bottom-left origin; added vertical flip in readback)
2. **Low FPS (~30 instead of 60)** — FIXED (moved to precision frame pacing + emulation-thread rendering)
3. **Z-order / depth culling incorrect** — ONGOING (Mario appears behind the background in Super Mario 64)

## Architecture

```
Core (mupen64plus/GLideN64)
    ↓ renders to internal FBOs (2,3,4...)
    ↓ blits/downscales to presentation FBOs (7→8, 9→10, 11→13)
    ↓ final composite draw (program 21) → "FBO 0" (default framebuffer)

Our frontend
    ↓ intercepts glBindFramebuffer to redirect FBO 0 → custom FBO
    ↓ glReadPixels from blit destination FBO
    ↓ uploads XRGB8888 pixels to Metal GPU renderer
    ↓ Metal applies shader + displays via Compose
```

## Key Files

| File | Role |
|------|------|
| `player/native/src/hw_render_gl_macos.m` | CGL context, FBO management, GL interception, readback |
| `player/native/src/hw_render_gl.h` | Public API header |
| `player/native/src/libretro_bridge.c` | Core loading, SET_HW_RENDER handling, run loop |
| `player/native/src/libretro_video.c` | video_refresh callback, RETRO_HW_FRAME_BUFFER_VALID handling |

## Investigation Timeline

### Phase 1: Custom FBO approach (original)

Created a custom FBO 1 with RGBA8 texture attachment. Set `g_hw_fbo_redirect = 1` so our `glBindFramebuffer` wrapper redirects FBO 0 → FBO 1. The core's GLSM layer sees FBO 1 as `default_framebuffer`.

**Result:** Image renders but z-ordering is wrong. Mario appears behind the background.

**Finding:** The core's final composite draw (program 21, a textured quad compositing all N64 framebuffer layers) always fails with `GL_INVALID_OPERATION (0x502)` when targeting our custom FBO 1. The FBO is complete, the program validates, but the draw silently fails. We read from FBO 13 (the last blit destination) instead of the composited result.

### Phase 2: N64 Depth Compare modes

Investigated mupen64plus-specific depth options:

| Mode | Requirement | Result |
|------|-------------|--------|
| `False` (default) | Standard GL depth | Fast (60fps), z-order broken |
| `True` | `GL_ARB_shader_image_load_store` | **Not available on macOS 4.1** — silently falls back to False |
| `Compatible` | Shader-based without image_load_store | **Works but 20-40 FPS** — too slow for 60fps target |

**Finding:** macOS OpenGL 4.1 lacks `GL_ARB_shader_image_load_store` (confirmed via `glGetStringi(GL_EXTENSIONS)`). The Compatible mode works correctly but has unacceptable performance.

### Phase 3: Frame dump analysis

Added PPM frame dumping for all internal FBOs (7, 8, 9, 10, 11, 13) at frames 500 and 1500.

**Finding:** All FBOs contained valid rendered content. The blit chain was:
- FBOs 7, 9, 11: 640x480 (render targets)
- FBOs 8, 10, 13: 320x240 (blit/downscale destinations)
- Three blit chains per frame: 7→8, 9→10, 11→13

The readback was from FBO 13 (last blit destination at 320x240). This is just one layer of the N64's multi-layer framebuffer system, not the final composite.

### Phase 4: FBO 0 approach (current)

Hypothesis: The GL_INVALID_OPERATION on FBO 1 is caused by macOS Metal-backed GL rejecting the core's post-processing shader on a custom FBO (possibly format/capability mismatch). If we use the real window backbuffer (FBO 0), the draw should succeed.

Created a hidden `NSWindow` + `NSOpenGLContext` to give FBO 0 a real backing surface. Set `ctx->fbo = 0` and `g_hw_fbo_redirect = 0` so the core renders its final composite to the real FBO 0.

**Results:**
- No more `GL_INVALID_OPERATION` errors
- Core runs at 60 FPS
- But **FBO 0 is not readable** — `glReadPixels(GL_BACK)` returns black pixels (macOS double-buffered Metal-backed window doesn't retain the back buffer for readback)
- Falling back to internal FBO scan finds FBO 12 (same z-ordering issue)

### Phase 5: FBO 0 → Readback FBO blit (in progress)

Current approach: after the core renders to FBO 0, blit FBO 0 → a readback FBO (separate texture-backed FBO), then read from the readback FBO.

```
Core renders → FBO 0 (composite succeeds, no GL error)
                ↓ glBlitFramebuffer
            Readback FBO (texture-backed)
                ↓ glReadPixels
            CPU buffer → Metal pipeline
```

**Status:** Code written, testing in progress.

## GL Interception Layers

Our code intercepts several GL functions to redirect FBO operations:

1. **`glBindFramebuffer` wrapper** — redirects FBO 0 → custom FBO (when using custom FBO mode). Also tracks `g_prev_draw_fbo` and `g_blit_dst_fbo`.
2. **`glDrawBuffer` / `glDrawBuffers` wrapper** — converts `GL_BACK` → `GL_COLOR_ATTACHMENT0` when bound to custom FBO.
3. **`glReadBuffer` wrapper** — similar conversion for reads.
4. **`glBlitFramebuffer` wrapper** — tracks blit destinations, fixes draw buffer state.
5. **`glClear` wrapper** — tracks clears to our FBO.
6. **`glDrawArrays` / `glDrawElements` wrapper** — tracks draw calls for per-frame stats.
7. **GOT rebinding** (`rebind_gl_in_core`) — patches the core .dylib's lazy symbol pointers to route through our wrappers (for functions linked directly against OpenGL.framework).

## macOS-Specific Constraints

- **OpenGL deprecated** but functional up to 4.1 on Apple Silicon via Metal translation
- **No `GL_ARB_shader_image_load_store`** — blocks N64-accurate depth compare
- **Metal-backed GL** — FBO 0 behavior differs from traditional GL (double-buffered, not directly readable)
- **No Vulkan** — MoltenVK could provide Vulkan for ParaLLEl-RDP but is out of scope

## Open Questions

1. Why does the core's composite draw (program 21) fail with GL_INVALID_OPERATION on a custom FBO but succeed on FBO 0? The custom FBO is COMPLETE with RGBA8 texture, depth renderbuffer, program validates — the error is unexplained.
2. Can we read FBO 0 via blit → readback FBO? Testing this now.
3. If the blit approach works, does it capture the correctly composited frame with proper z-ordering?
4. Alternative: could we create a custom FBO with a renderbuffer attachment (instead of texture) to avoid the format mismatch?
