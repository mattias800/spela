/*
 * OpenGL HW Render Module.
 *
 * Provides an offscreen CGL OpenGL context with FBO for libretro cores
 * that request hardware-accelerated rendering via RETRO_ENVIRONMENT_SET_HW_RENDER.
 *
 * After each frame, pixels are read back via glReadPixels and fed through
 * the existing Metal shader pipeline.
 */

#ifndef HW_RENDER_GL_H
#define HW_RENDER_GL_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct hw_gl_context hw_gl_context_t;

hw_gl_context_t *hw_gl_create(void);
void hw_gl_destroy(hw_gl_context_t *ctx);

/* Initialize CGL context + FBO. version_major/minor specify the GL version. */
bool hw_gl_init(hw_gl_context_t *ctx, unsigned version_major, unsigned version_minor,
                bool depth, bool stencil);
void hw_gl_deinit(hw_gl_context_t *ctx);

/* Make/release the GL context as current on this thread */
void hw_gl_make_current(hw_gl_context_t *ctx);
void hw_gl_release_current(hw_gl_context_t *ctx);

/* Resize FBO attachments when game geometry changes */
void hw_gl_resize_fbo(hw_gl_context_t *ctx, unsigned width, unsigned height);

/* Return the FBO name for get_current_framebuffer callback */
uintptr_t hw_gl_get_framebuffer(hw_gl_context_t *ctx);

/* Resolve GL function addresses (macOS links GL statically via dlsym) */
void *hw_gl_get_proc_address(const char *sym);

/* Read the HW framebuffer to a CPU buffer as XRGB8888.
 *
 * Reads the core-reported frame region (req_width x req_height) from the
 * bottom-left of the FBO, clamped to the FBO's actual size. The FBO can be
 * larger than the current frame (e.g. left over from a prior larger frame
 * before hw_gl_resize_fbo shrinks it), so reading the whole FBO would both
 * overflow a buffer sized for the reported frame and capture empty padding
 * (#1268). Pass req_width/req_height = 0 to read the full FBO.
 *
 * The actual region read is written to out_width/out_height. Returns the
 * number of pixels read, or 0 on failure. */
unsigned hw_gl_read_pixels(hw_gl_context_t *ctx, void *out_data, size_t out_capacity,
                           unsigned req_width, unsigned req_height,
                           unsigned *out_width, unsigned *out_height);

/* Debug: reset per-frame counters (call before retro_run) */
void hw_gl_debug_reset_frame(void);

/* Rebind GL symbols in the core's GOT/lazy-symbol-pointers.
 * Must be called AFTER context_reset so lazy pointers are resolved. */
void hw_gl_rebind_gl_symbols(void);

#ifdef __cplusplus
}
#endif

#endif /* HW_RENDER_GL_H */
