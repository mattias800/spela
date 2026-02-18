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

/* Vulkan HW render support (Phase 4) */
bool gpu_renderer_hw_vulkan_init(gpu_renderer_t *r);
void *gpu_renderer_hw_vulkan_get_interface(gpu_renderer_t *r);
void gpu_renderer_hw_render_frame(gpu_renderer_t *r, unsigned width, unsigned height);
void gpu_renderer_hw_vulkan_deinit(gpu_renderer_t *r);
bool gpu_renderer_is_hw_render_active(gpu_renderer_t *r);
/* Wait for GPU to finish all pending work (call before context_destroy) */
void gpu_renderer_wait_idle(gpu_renderer_t *r);

/* Query state */
bool gpu_renderer_is_active(gpu_renderer_t *r);

#ifdef __cplusplus
}
#endif

#endif /* GPU_RENDERER_H */
