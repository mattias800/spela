# Emulation Rendering Pipeline

This document explains how libretro cores render video in Spela across platforms. Understanding the rendering paths is critical when debugging garbled video, flipped images, or performance issues.

## Core Rendering Modes

Libretro cores use one of three rendering approaches:

| Mode | How it works | Examples |
|------|-------------|----------|
| **Software** | Core renders pixels to a CPU buffer. Frontend receives raw pixel data (RGB565, 0RGB1555, or XRGB8888) via `retro_video_refresh_t` callback. | Nestopia (NES), Snes9x (SNES), Gambatte (GB/GBC), Genesis Plus GX, mGBA (GBA) |
| **OpenGL HW** | Core requests an OpenGL context and renders to a GL framebuffer. Frontend reads pixels back from the GL framebuffer. **Y-axis is flipped** (bottom-left origin). | Flycast (Dreamcast), Beetle PSX HW (PSX with HW), GLideN64 (N64 via mupen64plus) |
| **Vulkan HW** | Core requests a Vulkan context and renders to its own VkImage. Frontend composites the image through the shader pipeline. | Dolphin (GameCube/Wii), ParaLLEl-RDP (N64 via mupen64plus-next) |

## Default Cores and Their Rendering Modes

| Console | Core | Rendering Mode | Pixel Format |
|---------|------|---------------|--------------|
| NES | Nestopia | Software | XRGB8888 |
| SNES | Snes9x | Software | RGB565 |
| Game Boy / GBC | Gambatte | Software | XRGB8888 |
| GBA | mGBA | Software | XRGB8888 |
| Genesis | Genesis Plus GX | Software | RGB565 |
| N64 | Mupen64Plus-Next | Vulkan HW (ParaLLEl) or OpenGL HW (GLideN64) | XRGB8888 |
| PlayStation | Beetle PSX (SW) | Software | XRGB8888 |
| PlayStation | Beetle PSX HW | OpenGL HW | XRGB8888 |
| Dreamcast | Flycast | OpenGL HW | XRGB8888 |
| GameCube/Wii | Dolphin | Vulkan HW | N/A (VkImage) |
| Saturn | Beetle Saturn | Software | XRGB8888 |
| PS2 | Play! | OpenGL HW | XRGB8888 |
| 3DS | Azahar | OpenGL HW | XRGB8888 |
| PSP | PPSSPP | OpenGL HW or Vulkan HW | XRGB8888 |
| DS | DeSmuME | Software | XRGB8888 |
| Arcade | FBNeo | Software | XRGB8888 |

## Platform-Specific Rendering Paths

### Desktop (macOS, Linux, Windows)

```
Core renders frame
    │
    ├─ Software core ──► video_refresh(data, width, height, pitch)
    │                       │
    │                       ├─ GPU renderer active? ──► gpu_renderer_upload_frame()
    │                       │   (tightly packs pitch, uploads to Vulkan staging buffer)
    │                       │
    │                       └─ No GPU ──► Copy to CPU frame_buffer (software fallback)
    │
    ├─ OpenGL HW core ──► video_refresh(RETRO_HW_FRAME_BUFFER_VALID, ...)
    │                       │
    │                       └─ hw_gl_read_pixels() ──► gpu_renderer_upload_frame()
    │                          (reads from GL pbuffer, pixels are Y-FLIPPED)
    │
    └─ Vulkan HW core ──► video_refresh(RETRO_HW_FRAME_BUFFER_VALID, ...)
                            │
                            └─ gpu_renderer_hw_render_frame()
                               (core's VkImage composited through shader pipeline)

GPU Renderer (Vulkan via MoltenVK on macOS):
    gpu_renderer_upload_frame()  ──► Staging buffer ──► Game texture (VkImage)
    gpu_renderer_render_to_bgra() ──► Shader pass ──► Offscreen target ──► Readback buffer

Kotlin reads via nativeGpuRenderToBgra() ──► MetalOffscreenSurface (Compose Canvas)

Software fallback (no GPU):
    nativeGetVideoFrame() ──► convertFrameInPlace() ──► DesktopEmulationSurface (Compose Canvas)
```

### Android

```
Core renders frame
    │
    ├─ Software core ──► video_refresh(data, width, height, pitch)
    │                       └─ GPU renderer uploads to Vulkan/GLES surface
    │
    ├─ GLES HW core ──► video_refresh(RETRO_HW_FRAME_BUFFER_VALID, ...)
    │                       └─ hw_gl_read_pixels() ──► GPU renderer
    │
    └─ Vulkan HW core ──► Vulkan HW render interface ──► GPU renderer

GPU Renderer presents to Android SurfaceView
```

### Web (EmulatorJS)

```
Core renders to asm.js/WASM canvas ──► Browser displays directly
(No native rendering pipeline — EmulatorJS handles everything)
Only software-rendered cores work in the browser.
```

## Key Implementation Files

| File | Purpose |
|------|---------|
| `player/native/src/libretro_video.c` | `video_refresh` callback — receives frames from cores, routes to GPU or CPU path |
| `player/native/src/libretro_bridge.c` | Environment callback — handles `SET_HW_RENDER`, core lifecycle |
| `player/native/src/gpu_renderer_vulkan.c` | Vulkan GPU renderer — texture upload, shader pipeline, offscreen readback |
| `player/native/src/gpu_renderer.h` | GPU renderer public API |
| `player/native/src/hw_render_gl.c` | OpenGL pbuffer management for GL HW render cores |
| `player/shared/src/desktopMain/.../MetalOffscreenSurface.kt` | Compose surface that displays GPU-rendered frames |
| `player/shared/src/desktopMain/.../DesktopEmulationSurface.kt` | Compose surface for software fallback (no GPU) |
| `player/shared/src/desktopMain/.../DesktopLibretroController.kt` | Emulation loop, frame readback coordination |

## Common Issues and Solutions

### Garbled/scrambled video
- **Cause**: Offscreen target dimensions don't match frame dimensions. The renderer reads back wrong-sized data.
- **Fix**: `gpu_renderer_render_to_bgra()` now dynamically resizes the offscreen target when frame dimensions change. See `create_offscreen_target()` — it must set `offscreen_width/height`.

### Upside-down video (Dreamcast, other GL cores)
- **Cause**: OpenGL framebuffer has bottom-left origin (Y-flipped vs Vulkan's top-left). The `hw_bottom_left_origin` flag wasn't being set for OpenGL cores.
- **Fix**: Both Vulkan and OpenGL HW render paths in `libretro_bridge.c` must call `gpu_renderer_set_hw_bottom_left_origin()`. The shader uses `flip_y` push constant to flip when needed.

### Pitch vs width mismatch
- **Cause**: Cores may output frames with row padding (pitch > width * bpp). If pitch isn't handled, every row is offset, creating a diagonal shear.
- **Fix**: `video_refresh` in `libretro_video.c` copies row-by-row using pitch. `gpu_renderer_upload_frame` also handles pitch correctly.

### Stale frame from previous game
- **Cause**: `currentBitmap` or `latestRenderedFrame` persists across game sessions.
- **Fix**: Clear frame state when controller changes (`LaunchedEffect(controller) { currentBitmap = null }`).

### Black screen (software core with GPU renderer)
- **Cause**: Software cores don't set `RETRO_HW_FRAME_BUFFER_VALID`, so they go through `gpu_renderer_upload_frame`. If the GPU renderer isn't active, they fall back to CPU buffer. If `MetalOffscreenSurface` is used but `nativeGpuIsActive()` returns false, no frames are displayed.
- **Fix**: `PlatformEmulationSurface` checks `gpuInitialized` and falls back to `DesktopEmulationSurface`.
