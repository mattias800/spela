/*
 * Spela libretro bridge - Video subsystem.
 *
 * Receives frame buffers from the core via the video_refresh callback,
 * stores them for the Kotlin layer to read and render to a surface.
 *
 * Three rendering paths, selected per-frame:
 *
 * 1. Vulkan HW render (Dolphin, etc.): Core renders to its own VkImage.
 *    video_refresh gets RETRO_HW_FRAME_BUFFER_VALID; we route to
 *    gpu_renderer_hw_render_frame() which composites + reads back.
 *    IMPORTANT: video_state.width/height MUST be set here because the
 *    Kotlin side (renderGpuFrameToBgra) reads them via nativeGetVideoWidth()
 *    to allocate the readback buffer. If they're 0, Kotlin skips the frame.
 *
 * 2. GLES HW render (via pbuffer): Core renders to an OpenGL FBO.
 *    We read pixels back and upload through the GPU pipeline.
 *
 * 3. Software render: Core provides a CPU framebuffer directly.
 *    Uploaded to GPU staging buffer, or copied to CPU buffer for Kotlin.
 */

#include "libretro_bridge.h"
#include "gpu_renderer.h"

#include "hw_render_gl.h"
#ifdef __APPLE__
#include <OpenGL/gl3.h>
#endif

#include <stdlib.h>
#include <string.h>
#include <stdio.h>


static struct {
    void    *frame_buffer;
    size_t   frame_buffer_size;
    unsigned width;
    unsigned height;
    size_t   pitch;
    unsigned pixel_format;  /* RETRO_PIXEL_FORMAT_* */

    /* GPU renderer (NULL = software path) */
    gpu_renderer_t *gpu_renderer;

    /* When true, video_refresh_callback returns early. Set before
     * retro_deinit so a core that flushes a final frame during its
     * own shutdown (Play! libretro is the canonical case — its
     * MailBox flush calls FlipImpl → video_cb on data the GS handler
     * has already partially released) doesn't crash inside our
     * memcpy. (#916) */
    bool shutting_down;

    /* #1044: cross-thread state guard.
     *
     * Writer = emulation thread inside video_refresh_callback. It can
     * realloc(frame_buffer) on dimension change, then memcpy the new
     * frame into it.
     *
     * Reader = render thread via the JNI bridge: nativeGetVideoFrame /
     * nativeFillVideoFrame call video_get_frame_buffer + size, then
     * SetByteArrayRegion to copy out.
     *
     * Without sync, a realloc on the writer side while the reader is
     * mid-memcpy returns a dangling pointer. The reader also sees torn
     * width/height pairs across the dimension-change frame.
     *
     * The mutex covers every video_state mutation and every read. The
     * JNI bridge holds it across the get_buffer + size + memcpy triple
     * via video_lock/video_unlock. */
    sp_mutex_t lock;
    bool       lock_initialized;
} video_state = {0};

void video_init(void) {
    /* Preserve the lock across re-init: a core teardown + relaunch
     * doesn't necessarily call video_deinit between. Lazy create on
     * the first init. */
    bool had_lock = video_state.lock_initialized;
    sp_mutex_t saved_lock;
    if (had_lock) {
        saved_lock = video_state.lock;
    }

    video_state.frame_buffer = NULL;
    video_state.frame_buffer_size = 0;
    video_state.width = 0;
    video_state.height = 0;
    video_state.pitch = 0;
    video_state.pixel_format = RETRO_PIXEL_FORMAT_0RGB1555; /* default */
    video_state.shutting_down = false;
    /* gpu_renderer is not touched here -- managed externally */

    if (had_lock) {
        video_state.lock = saved_lock;
        video_state.lock_initialized = true;
    } else if (sp_mutex_init(&video_state.lock) == 0) {
        video_state.lock_initialized = true;
    }
}

void video_set_shutting_down(void) {
    video_state.shutting_down = true;
}

void video_deinit(void) {
    if (video_state.lock_initialized) {
        sp_mutex_lock(&video_state.lock);
    }
    if (video_state.frame_buffer) {
        free(video_state.frame_buffer);
        video_state.frame_buffer = NULL;
    }
    video_state.frame_buffer_size = 0;
    video_state.width = 0;
    video_state.height = 0;
    /* gpu_renderer is not destroyed here -- managed externally */
    if (video_state.lock_initialized) {
        sp_mutex_unlock(&video_state.lock);
        sp_mutex_destroy(&video_state.lock);
        video_state.lock_initialized = false;
    }
}

/* #1044: lock primitives exposed for the JNI bridge so it can hold the
 * video mutex across the get_frame_buffer + size + memcpy triple in
 * nativeGetVideoFrame / nativeFillVideoFrame. */
void video_lock(void) {
    if (video_state.lock_initialized) {
        sp_mutex_lock(&video_state.lock);
    }
}

void video_unlock(void) {
    if (video_state.lock_initialized) {
        sp_mutex_unlock(&video_state.lock);
    }
}

/* Clear the displayed frame so the previous game's last frame doesn't linger
 * into the next game (#1236). Zeroing the dimensions makes readers
 * (nativeGetVideoWidth/Height, renderGpuFrameToBgra's >0 guard, the SW
 * fill path) report "no frame" until the next game's first video_refresh
 * repopulates them. The frame buffer allocation is kept for reuse. */
void video_clear_frame(void) {
    video_lock();
    video_state.width = 0;
    video_state.height = 0;
    video_state.pitch = 0;
    video_unlock();
}

void video_set_pixel_format(unsigned format) {
    video_lock();
    video_state.pixel_format = format;
    video_unlock();
}

void video_set_gpu_renderer(gpu_renderer_t *renderer) {
    /* Writer crosses the JNI thread / emulation thread boundary
     * (Kotlin GPU surface lifecycle vs core's video_refresh_callback),
     * so the pointer write must be locked even though it's a single
     * machine-word store. */
    video_lock();
    video_state.gpu_renderer = renderer;
    video_unlock();
}

gpu_renderer_t *video_get_gpu_renderer(void) {
    video_lock();
    gpu_renderer_t *r = video_state.gpu_renderer;
    video_unlock();
    return r;
}

/*
 * Called by the core each frame with the rendered framebuffer.
 *
 * When GPU renderer is active: route directly to gpu_renderer_upload_frame(),
 * which writes to a Vulkan/Metal staging buffer. No CPU buffer copy.
 *
 * When GPU renderer is inactive: copy data into our own buffer so Kotlin
 * can read it safely (software fallback path).
 *
 * data = NULL means the frame is a duplicate (software framebuffer unchanged).
 * data = RETRO_HW_FRAME_BUFFER_VALID means the core rendered to HW FBO.
 */
void video_refresh_callback(const void *data, unsigned width, unsigned height, size_t pitch) {
    if (!data) return;
    /* See video_state.shutting_down. */
    if (video_state.shutting_down) return;

    /* #1044: hold the lock across the entire callback. This serialises:
     *  - dimension fields (width / height / pitch / pixel_format) that
     *    JNI readers consume to allocate output buffers,
     *  - frame_buffer realloc + memcpy, which would otherwise return a
     *    dangling pointer to a reader that called video_get_frame_buffer
     *    just before the realloc.
     * GPU paths (Vulkan / Metal) only mutate width / height under the
     * lock; the GPU staging buffer they write into is the renderer's
     * own and is synchronised internally. */
    video_lock();

    if (data == RETRO_HW_FRAME_BUFFER_VALID && g_core.hw_render_enabled) {
        /*
         * Vulkan HW render path (e.g. Dolphin): core rendered to its own VkImage.
         *
         * CRITICAL: We MUST set video_state.width/height before returning.
         * The Kotlin layer calls nativeGetVideoWidth()/nativeGetVideoHeight()
         * to determine the readback buffer size. If these are 0 (never set),
         * renderGpuFrameToBgra() returns early and frames are never displayed.
         * The actual pixel data is in the GPU renderer's readback buffer,
         * accessed via gpu_renderer_render_to_bgra().
         */
        if (video_state.gpu_renderer && gpu_renderer_is_active(video_state.gpu_renderer) &&
            gpu_renderer_is_hw_render_active(video_state.gpu_renderer)) {
            video_state.width = width;
            video_state.height = height;
            gpu_renderer_hw_render_frame(video_state.gpu_renderer, width, height);
            video_unlock();
            return;
        }
        /* GLES HW render path: readback from pbuffer, upload through Vulkan pipeline.
         * Read pixels FIRST from the current pbuffer (the core just rendered to it),
         * then resize the pbuffer for the next frame if dimensions changed. */
        if (g_core.hw_gl_ctx) {
            size_t needed = (size_t)width * height * 4;
            if (needed > video_state.frame_buffer_size) {
                void *new_buf = realloc(video_state.frame_buffer, needed);
                if (!new_buf) {
                    video_unlock();
                    return;
                }
                video_state.frame_buffer = new_buf;
                video_state.frame_buffer_size = needed;
            }

            unsigned rb_w = 0, rb_h = 0;
            // Pass the core-reported frame size so the readback is clamped to
            // it: the FBO can be larger than the current frame (the resize to
            // the new dims happens below, after this read), which previously
            // overflowed the buffer and dropped the frame with a "Buffer too
            // small" error. (#1268)
            unsigned pixels = hw_gl_read_pixels(g_core.hw_gl_ctx, video_state.frame_buffer,
                                                video_state.frame_buffer_size, width, height,
                                                &rb_w, &rb_h);

            if (pixels > 0) {
                video_state.width = rb_w;
                video_state.height = rb_h;
                video_state.pitch = rb_w * 4;

                if (video_state.gpu_renderer && gpu_renderer_is_active(video_state.gpu_renderer)) {
                    gpu_renderer_upload_frame(video_state.gpu_renderer, video_state.frame_buffer,
                        rb_w, rb_h, rb_w * 4, RETRO_PIXEL_FORMAT_XRGB8888);
                }
            }

            /* Resize pbuffer for next frame if core reported different dimensions */
            hw_gl_resize_fbo(g_core.hw_gl_ctx, width, height);
            video_unlock();
            return;
        }
        /* HW frame but no active renderer — drop the frame. Falling through to
         * the software path would dereference RETRO_HW_FRAME_BUFFER_VALID as a
         * pointer, causing a SIGSEGV. This happens when the GPU surface is
         * suspended (e.g. clamshell close) while the core is still running. */
        video_unlock();
        return;
    }

    /*
     * NOTE: There was previously an __APPLE__-only GLES readback path here.
     * It is no longer needed — macOS now uses the Vulkan HW render path above
     * (via MoltenVK) or the cross-platform GLES pbuffer path (g_core.hw_gl_ctx).
     */

    video_state.width = width;
    video_state.height = height;
    video_state.pitch = pitch;

    /* GPU path: upload directly to GPU staging buffer */
    if (video_state.gpu_renderer && gpu_renderer_is_active(video_state.gpu_renderer)) {
        gpu_renderer_upload_frame(video_state.gpu_renderer, data,
            width, height, pitch, video_state.pixel_format);
        video_unlock();
        return;
    }

    /* Software path: copy to CPU buffer for Kotlin to read */
    unsigned bpp;
    switch (video_state.pixel_format) {
        case RETRO_PIXEL_FORMAT_XRGB8888:
            bpp = 4;
            break;
        case RETRO_PIXEL_FORMAT_RGB565:
        case RETRO_PIXEL_FORMAT_0RGB1555:
        default:
            bpp = 2;
            break;
    }

    /* Store as tightly-packed pixel data (no padding per row) */
    size_t needed = (size_t)width * height * bpp;

    if (needed != video_state.frame_buffer_size) {
        void *new_buf = realloc(video_state.frame_buffer, needed);
        if (!new_buf) {
            video_unlock();
            return;
        }
        video_state.frame_buffer = new_buf;
        video_state.frame_buffer_size = needed;
    }

    /* Copy row-by-row in case pitch != width * bpp */
    const uint8_t *src = (const uint8_t *)data;
    uint8_t *dst = (uint8_t *)video_state.frame_buffer;
    size_t row_bytes = (size_t)width * bpp;

    for (unsigned y = 0; y < height; y++) {
        memcpy(dst, src, row_bytes);
        src += pitch;
        dst += row_bytes;
    }

    video_unlock();
}

unsigned video_get_width(void) {
    video_lock();
    unsigned w = video_state.width;
    video_unlock();
    return w;
}

unsigned video_get_height(void) {
    video_lock();
    unsigned h = video_state.height;
    video_unlock();
    return h;
}

/* Pointer accessor: the buffer is a heap allocation that the writer can
 * realloc on dimension change, so reading the buffer's *contents* through
 * this pointer requires holding video_lock. The JNI bridge does so via
 * video_lock() / video_unlock() around the whole read+copy. */
const void *video_get_frame_buffer(void) {
    return video_state.frame_buffer;
}

size_t video_get_frame_buffer_size(void) {
    video_lock();
    size_t s = video_state.frame_buffer_size;
    video_unlock();
    return s;
}

unsigned video_get_pixel_format(void) {
    video_lock();
    unsigned f = video_state.pixel_format;
    video_unlock();
    return f;
}

/* Caller-locked accessors for use by the JNI bridge when it has already
 * acquired video_lock — avoids a second acquisition per JNI call and
 * keeps width/height/pitch consistent with the buffer pointer. */
unsigned video_get_width_unlocked(void) { return video_state.width; }
unsigned video_get_height_unlocked(void) { return video_state.height; }
size_t video_get_frame_buffer_size_unlocked(void) { return video_state.frame_buffer_size; }
unsigned video_get_pixel_format_unlocked(void) { return video_state.pixel_format; }
