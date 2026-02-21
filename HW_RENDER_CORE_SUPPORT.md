# HW Render Core Support (Phase 4)

## Background

Phases 0-3 implemented GPU hardware rendering for **software-rendered cores** (NES, SNES, GBA, etc.). The pipeline is:

```
Core CPU framebuffer -> gpu_renderer_upload_frame() -> staging buffer -> GPU texture -> shader -> swapchain
```

This eliminates the old 3-copy software path (core -> native buffer -> JNI ByteArray -> Skia bitmap) and runs real SPIR-V/MSL fragment shaders instead of CPU Canvas overlays.

**What's missing:** Cores that can render directly on the GPU (PSX, N64, PSP) still fall back to software rendering because `RETRO_ENVIRONMENT_SET_HW_RENDER` returns `false` (see `libretro_bridge.c:202`).

## Goal

Accept Vulkan and Metal HW render context requests from libretro cores so they render directly to our GPU texture, skipping the staging buffer upload entirely.

## Target Cores

| Core | System | HW Context | Notes |
|------|--------|------------|-------|
| `beetle_psx_hw` | PSX | Vulkan | Most important target |
| `parallel_n64` | N64 | Vulkan | ParaLLEl RDP, native Vulkan |
| `ppsspp` | PSP | Vulkan | Already has Vulkan backend |
| `mupen64plus_next` | N64 | Vulkan/GL | Vulkan preferred |

Cores requesting OpenGL contexts -> return `false`, they fall back to software. Adding a GL->Vulkan translation layer is not worth the complexity.

## Current State

Already in place from Phases 0-3:

- `libretro.h`: `struct retro_hw_render_callback`, `RETRO_HW_CONTEXT_VULKAN` (6), `RETRO_HW_CONTEXT_METAL` (7)
- `libretro_bridge.h`: `hw_render_callback` and `hw_render_enabled` fields on `libretro_core_t`
- `libretro_bridge.c`: `SET_HW_RENDER` case stores the callback but returns `false`
- `gpu_renderer.h`: `gpu_renderer_get_hw_callback()` declared (not yet implemented)

## Implementation

### 1. Accept HW render requests (`libretro_bridge.c`)

In the `RETRO_ENVIRONMENT_SET_HW_RENDER` case:

- Accept `RETRO_HW_CONTEXT_VULKAN` when running on Vulkan platforms (Android, Linux, Windows)
- Accept `RETRO_HW_CONTEXT_METAL` when running on macOS
- Reject all OpenGL context types (return `false`)
- When accepted: set `g_core.hw_render_enabled = true`, fill the callback struct with our implementations of `context_reset`, `get_current_framebuffer`, `context_destroy`
- `get_proc_address` is not needed for Vulkan/Metal (cores use the HW render interface instead)

### 2. Vulkan HW render interface (`gpu_renderer_vulkan.c`)

Implement `retro_hw_render_interface_vulkan` (defined in libretro's `libretro_vulkan.h`):

```c
struct retro_hw_render_interface_vulkan {
    VkInstance instance;
    VkPhysicalDevice gpu;
    VkDevice device;
    VkQueue queue;
    unsigned queue_index;
    // ... lock/unlock functions for shared queue access
};
```

Key work:
- Expose our existing VkInstance, VkPhysicalDevice, VkDevice, VkQueue to the core
- `get_current_framebuffer()` returns a VkImage/framebuffer the core renders into
- Our shader pass reads from that texture instead of the staging buffer
- Synchronization: the core signals when it's done rendering (via semaphore), then we run our shader pass and present
- Handle `RETRO_ENVIRONMENT_GET_HW_RENDER_INTERFACE` (cmd 41, already defined in `libretro.h`) to return the interface struct

### 3. Metal HW render interface (`gpu_renderer_metal.m`)

Implement the Metal equivalent:

- Provide `MTLDevice` and `MTLCommandQueue` to the core
- Core renders into a provided `MTLTexture`
- We composite with our shader pipeline after the core's command buffer completes
- Fence-based synchronization between core rendering and our shader pass

### 4. Render path branching (`libretro_video.c`)

When `g_core.hw_render_enabled`:
- `video_refresh_callback` with `data == RETRO_HW_FRAME_BUFFER_VALID` means the core rendered to our GPU texture directly
- Skip `gpu_renderer_upload_frame()` -- texture is already populated
- Still call `gpu_renderer_render()` to run the shader pass and present

When `!g_core.hw_render_enabled`:
- Existing path: `gpu_renderer_upload_frame()` from CPU buffer (unchanged)

### 5. Lifecycle

- `context_reset()`: called after GPU context is created, core initializes its GPU resources
- `context_destroy()`: called before GPU teardown, core cleans up
- On surface loss (Android backgrounding): call `context_destroy()`, reinit surface, call `context_reset()`

## Files to Modify

| File | Change |
|------|--------|
| `native/src/libretro.h` | Add `RETRO_HW_FRAME_BUFFER_VALID`, Vulkan/Metal HW render interface structs |
| `native/src/libretro_bridge.c` | Accept Vulkan/Metal in `SET_HW_RENDER`, handle `GET_HW_RENDER_INTERFACE` |
| `native/src/gpu_renderer.h` | Implement `gpu_renderer_get_hw_callback()` |
| `native/src/gpu_renderer_vulkan.c` | Vulkan HW render interface, shared queue, core framebuffer management |
| `native/src/gpu_renderer_metal.m` | Metal HW render interface, shared device/queue, core texture management |
| `native/src/libretro_video.c` | Branch on `hw_render_enabled` in `video_refresh_callback` |

No Kotlin changes needed -- the emulation surfaces and controllers are already GPU-aware.

## Risks

| Risk | Mitigation |
|------|------------|
| Core crashes with our Vulkan context | Per-core fallback: catch failure, set `hw_render_enabled = false`, re-init with software path |
| Queue contention (core + our shader pass) | Use `VkSemaphore` for ordered submission; lock/unlock in the HW render interface |
| Android surface recreation during HW render | `context_destroy()` before surface loss, `context_reset()` after new surface |
| Different Vulkan extensions required by cores | Query core requirements from `retro_hw_render_interface_vulkan`, enable at device creation |

## Verification

1. Load `beetle_psx_hw` with a PSX game -- should request Vulkan HW context and render via GPU
2. Load `snes9x` -- should still use software upload path (no HW render request)
3. Load any core requesting OpenGL -- should fall back to software gracefully
4. Test Android surface recreation (background/foreground app) during HW core gameplay
5. Test all shader presets work with HW-rendered frames (shader pass reads from core's texture)
6. Performance: PSX at locked 60 FPS, N64 playable
