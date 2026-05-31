/*
 * GPU Renderer Interface.
 *
 * Common abstraction over Vulkan (Android/Linux/Windows) and Metal (macOS).
 * Software-rendered cores upload frames via gpu_renderer_upload_frame().
 * HW-accelerated cores render directly to the GPU texture.
 */

#ifndef GPU_RENDERER_H
#define GPU_RENDERER_H

#include "libretro.h"
#include <stdbool.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Backend identifiers */
#define GPU_BACKEND_VULKAN 1
#define GPU_BACKEND_METAL  2

/* Shader identifiers matching ShaderPreset enum on Kotlin side */
#define GPU_SHADER_NONE            0
#define GPU_SHADER_BILINEAR        1
#define GPU_SHADER_SHARP_BILINEAR  2
#define GPU_SHADER_CRT_SIMPLE      3
#define GPU_SHADER_SCANLINES       4
#define GPU_SHADER_LCD_GRID        5

typedef struct gpu_renderer gpu_renderer_t;

/* Lifecycle */
gpu_renderer_t *gpu_renderer_create(int backend);
void gpu_renderer_destroy(gpu_renderer_t *r);
bool gpu_renderer_init_surface(gpu_renderer_t *r, void *native_surface);
bool gpu_renderer_init_offscreen(gpu_renderer_t *r, int width, int height);
void gpu_renderer_resize(gpu_renderer_t *r, int width, int height);
void gpu_renderer_deinit_surface(gpu_renderer_t *r);

/* Surface suspend/resume — used when the surface is temporarily destroyed
 * (e.g. Android SurfaceView hidden for overlay). Destroys only the swapchain
 * and VkSurface, keeping the Vulkan device and HW render context alive.
 * This avoids calling context_destroy/context_reset on the core. */
void gpu_renderer_suspend_surface(gpu_renderer_t *r);
bool gpu_renderer_resume_surface(gpu_renderer_t *r, void *native_surface);

/* Rendering */
void gpu_renderer_upload_frame(gpu_renderer_t *r, const void *data,
    unsigned width, unsigned height, size_t pitch, unsigned pixel_format);
void gpu_renderer_set_shader(gpu_renderer_t *r, int shader_id);
void gpu_renderer_render(gpu_renderer_t *r);
/* Offscreen rendering: render with shader to an internal texture and copy to out_data (BGRA8).
 * Returns the number of bytes written, or 0 on failure.
 * out_width/out_height receive the rendered dimensions. */
size_t gpu_renderer_render_to_bgra(gpu_renderer_t *r, void *out_data, size_t out_capacity,
    unsigned *out_width, unsigned *out_height);

/* Source rect for DS dual-screen: render a sub-region of the framebuffer */
void gpu_renderer_set_source_rect(gpu_renderer_t *r, int x, int y, int w, int h);

/* Vulkan context negotiation — call before init_surface to let core create VkDevice */
struct retro_hw_render_context_negotiation_interface_vulkan;
void gpu_renderer_set_vk_negotiation(gpu_renderer_t *r,
    const struct retro_hw_render_context_negotiation_interface_vulkan *iface);

/* Hide problematic Vulkan device extensions from the core during enumeration.
 * Default is on — VK_EXT_subgroup_size_control crashes Granite (paraLLEl-RDP
 * for N64) on Adreno. Cores that need the extension (PPSSPP — investigating
 * for #916) flip this off. */
void gpu_renderer_set_extension_filter_enabled(gpu_renderer_t *r, bool enabled);

/* Reinitialize Vulkan context (instance, device, pipelines) in-place.
 * Used when the core provides a v2 negotiation interface after the renderer
 * was already initialized — tears down and recreates with negotiation. */
bool gpu_renderer_reinit_vulkan(gpu_renderer_t *r);

/* Vulkan HW render support (Phase 4) */
bool gpu_renderer_hw_vulkan_init(gpu_renderer_t *r);
void *gpu_renderer_hw_vulkan_get_interface(gpu_renderer_t *r);
void gpu_renderer_hw_render_frame(gpu_renderer_t *r, unsigned width, unsigned height);
void gpu_renderer_hw_vulkan_deinit(gpu_renderer_t *r);
bool gpu_renderer_is_hw_render_active(gpu_renderer_t *r);
/* Context-reset gating: the renderer goes hw_render_active before the core's
 * context_reset() runs; mark/query whether context_reset has completed so
 * retro_run can wait for the core's GPU backend to exist. See #925/#1270. */
void gpu_renderer_mark_hw_context_reset_done(gpu_renderer_t *r);
bool gpu_renderer_is_hw_context_reset_done(gpu_renderer_t *r);
void gpu_renderer_set_hw_bottom_left_origin(gpu_renderer_t *r, bool bottom_left);
/* Wait for GPU to finish all pending work (call before context_destroy) */
void gpu_renderer_wait_idle(gpu_renderer_t *r);

/* Query state */
bool gpu_renderer_is_active(gpu_renderer_t *r);

/* Desktop Vulkan surface extension query (Linux: X11 vs Wayland) */
#if defined(__linux__) && !defined(__ANDROID__)
const char *vulkan_desktop_get_surface_extension(void);
#endif

#ifdef __cplusplus
}
#endif

#endif /* GPU_RENDERER_H */
