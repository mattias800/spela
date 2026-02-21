# N64 Vulkan HW Render on Android — Status

## Goal

Enable Vulkan hardware-accelerated rendering for N64 games on Android using paraLLEl-RDP
(Vulkan compute-based N64 RDP renderer) in the `mupen64plus_next_gles3` core. This replaces
the Angrylion software renderer currently forced on all platforms.

## Architecture

The Vulkan HW render pipeline has 4 phases:

1. **Core requests HW render** — `RETRO_ENVIRONMENT_SET_HW_RENDER` with `RETRO_HW_CONTEXT_VULKAN`
2. **Frontend provides Vulkan device** — `RETRO_ENVIRONMENT_GET_HW_RENDER_INTERFACE` returns
   `retro_hw_render_interface_vulkan` with VkDevice, VkQueue, function pointers, sync callbacks
3. **Core renders to Vulkan** — paraLLEl-RDP submits compute shaders, calls `set_image` with result
4. **Frontend composites** — `gpu_renderer_hw_render_frame()` blits core's image to swapchain

The pipeline code is fully implemented in `gpu_renderer_vulkan.c` (Phase 4 commit `e348116`).

## What Works

- **NES games on Ayn Thor physical device** — all E2E tests pass
- **NES games on Android emulator** — all E2E tests pass
- **Desktop build** — compiles and runs correctly (macOS still uses Angrylion)
- **Vulkan pipeline initialization** — instance, device, swapchain, HW render interface all set up correctly
- **Core accepts Vulkan context** — "Accepted Vulkan HW render" in logs
- **Context negotiation accepted** — "Accepted Vulkan context negotiation interface (version=2)"

## Current Blocker: Core Crashes

The N64 core (mupen64plus_next_gles3) crashes with SIGSEGV when trying to use paraLLEl-RDP's
Vulkan renderer on the Ayn Thor (Snapdragon 8 Gen 2, Adreno 740, Vulkan 1.3).

### Crash Details

**Without context negotiation** (returning false for env cmd 43):
- Core logs: "libretro frontend doesn't have context negotiation support"
- Crash: `retro_run+188`, fault addr 0x0 (null pointer in core's global state)
- All registers x19-x28 are zero — core's rendering state never initialized
- context_reset was called and the core received our Vulkan interface

**With context negotiation** (accepting env cmd 43 and calling core's create_device):
- Core's `create_device` callback crashes immediately
- Crash: inside core at offset 0x40c574, fault addr 0x1C (null + 0x1C)
- Backtrace: 3 frames inside core → our create_device call → gpu_renderer_init_surface

### Root Cause Hypothesis

The `mupen64plus_next_gles3` variant from the libretro buildbot may have paraLLEl-RDP's Vulkan
backend compiled in an incompatible configuration, or the core's context negotiation
`create_device` expects some internal state that isn't set up at the time we call it.

The `gles3` suffix refers to the RSP (Reality Signal Processor) backend using GLES3 compute,
while the RDP (paraLLEl-RDP) uses Vulkan compute. The core self-identifies as
"Mupen64Plus-Next v2.8-Vulkan" and does request `RETRO_HW_CONTEXT_VULKAN`, so Vulkan support
appears to be present.

## What Has Been Tried

### 1. Enable all Vulkan features (VkPhysicalDeviceFeatures2 pNext chain)
- Added shaderFloat16Int8, 8-bit storage, 16-bit storage feature structs
- Used dynamic loading (`vkGetInstanceProcAddr`) for Vulkan 1.1 functions
- Result: Still crashes at retro_run+188

### 2. Enable ALL supported device extensions (113 extensions on Adreno 740)
- Enumerate all extensions, enable them all at device creation
- Result: Still crashes at retro_run+188

### 3. Bump instance API version to Vulkan 1.1
- Changed from VK_API_VERSION_1_0 to VK_API_VERSION_1_1
- Used core's `get_application_info` for API version
- Result: Still crashes at retro_run+188

### 4. Implement context negotiation (env cmd 43)
- Added `RETRO_ENVIRONMENT_SET_HW_RENDER_CONTEXT_NEGOTIATION_INTERFACE` handler
- Added `retro_hw_render_context_negotiation_interface_vulkan` struct to libretro.h
- Core accepts negotiation, we call `create_device` callback
- Result: Crash MOVES to inside core's create_device callback (progress but still broken)

### 5. Use core's get_application_info for instance creation
- Result: No change, same crash in create_device

## Files Modified

### Native C code
- `player/native/src/libretro_bridge.c` — Angrylion override gated by `#ifdef __APPLE__`,
  N64 core detection for paraLLEl-RDP, nativeRun guard for HW render readiness,
  context negotiation handler (env cmd 43)
- `player/native/src/libretro_bridge.h` — Added `hw_vk_negotiation` field to core state
- `player/native/src/gpu_renderer_vulkan.c` — Vulkan 1.1 instance, all-extension device creation,
  context negotiation create_device support, dynamic vkGetPhysicalDeviceFeatures2 loading
- `player/native/src/gpu_renderer.h` — Added `gpu_renderer_set_vk_negotiation` API
- `player/native/src/libretro.h` — Added context negotiation structs and env cmd define

### Kotlin/Android
- `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/SpelaApp.kt` —
  "Game running" semantic marker for E2E tests (dual-screen + physical controller support)
- `player/android/src/main/res/xml/network_security_config.xml` — Added 127.0.0.1 for cleartext

### E2E test infrastructure
- `player/run-e2e.sh` — adb reverse port forwarding, screen timeout management
- `player/android/src/androidTest/java/com/spela/player/android/TestHelpers.kt` —
  SERVER_URL changed to 127.0.0.1, game-started indicator changed to "Game running"
- All test files updated to use "Game running" instead of "Touch controls"/"FPS"

## Next Steps to Try

1. **Try a different core binary** — Download `parallel_n64` or a non-gles3 variant of
   mupen64plus_next from the buildbot and test if it works with our Vulkan pipeline

2. **Compare with RetroArch's implementation** — Study how RetroArch calls the core's
   `create_device` callback and what state is set up before the call. The crash inside
   the core suggests we're calling it at the wrong time or with wrong parameters.

3. **Try GLideN64 with GLES3 HW render** — Instead of paraLLEl-RDP (Vulkan), configure
   `rdp-plugin=glide64` and accept `RETRO_HW_CONTEXT_OPENGL` on Android. This would
   use EGL/GLES3 instead of Vulkan.

4. **Fall back to Angrylion** — If Vulkan HW render can't be made to work with the
   buildbot core, keep Angrylion software rendering as a working fallback. N64 games
   do run with Angrylion (tested on emulator in previous sessions).

## Test Device Info

- **Ayn Thor** — Snapdragon 8 Gen 2, Adreno 740, Vulkan 1.3, dual-screen gaming handheld
- **ADB serial**: 54071896
- **Android 13** (API 33)
- Physical gamepad controls (touch controls hidden)
- Secondary display active during gameplay
