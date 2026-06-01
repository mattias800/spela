/*
 * OpenGL ES HW Render Module - Android.
 *
 * Provides an offscreen EGL/GLES3 context for libretro cores that request
 * hardware-accelerated rendering via RETRO_ENVIRONMENT_SET_HW_RENDER
 * with GLES context types (e.g. GLideN64 for N64 emulation).
 *
 * GLideN64 (and most GLES cores) render their final output to FBO 0
 * (the default framebuffer). We use an EGL pbuffer surface sized to the
 * game resolution as that default framebuffer. After each frame, pixels
 * are read back via glReadPixels from FBO 0 and fed through the existing
 * Vulkan shader pipeline.
 *
 * Do NOT call eglTerminate — the default display is shared with SurfaceFlinger.
 */

#ifdef __ANDROID__

#include "hw_render_gl.h"

#include <EGL/egl.h>
#include <GLES3/gl3.h>

#include <stdlib.h>
#include <string.h>

#include <android/log.h>
#define GL_TAG "SpelaHWGL"
#define GL_LOGI(...) __android_log_print(ANDROID_LOG_INFO, GL_TAG, __VA_ARGS__)
#define GL_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, GL_TAG, __VA_ARGS__)

/* EGL_OPENGL_ES3_BIT_KHR — not always in older NDK headers */
#ifndef EGL_OPENGL_ES3_BIT_KHR
#define EGL_OPENGL_ES3_BIT_KHR 0x0040
#endif

/* ===== Context struct ===== */

struct hw_gl_context {
    EGLDisplay egl_display;
    EGLContext egl_context;
    EGLSurface egl_surface;
    EGLConfig  egl_config;

    /* Surface dimensions (= game resolution) */
    unsigned surface_width;
    unsigned surface_height;
    bool has_depth;
    bool has_stencil;
    bool initialized;
};

/* ===== Public API ===== */

hw_gl_context_t *hw_gl_create(void) {
    hw_gl_context_t *ctx = (hw_gl_context_t *)calloc(1, sizeof(hw_gl_context_t));
    if (ctx) {
        ctx->egl_display = EGL_NO_DISPLAY;
        ctx->egl_context = EGL_NO_CONTEXT;
        ctx->egl_surface = EGL_NO_SURFACE;
    }
    return ctx;
}

void hw_gl_destroy(hw_gl_context_t *ctx) {
    if (!ctx) return;
    hw_gl_deinit(ctx);
    free(ctx);
}

bool hw_gl_init(hw_gl_context_t *ctx, unsigned version_major, unsigned version_minor,
                bool depth, bool stencil) {
    if (!ctx || ctx->initialized) return false;

    ctx->has_depth = depth;
    ctx->has_stencil = stencil;

    if (version_major == 0) {
        version_major = 3;
        version_minor = 0;
    }

    /* Get the default EGL display (shared with SurfaceFlinger) */
    ctx->egl_display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (ctx->egl_display == EGL_NO_DISPLAY) {
        GL_LOGE("eglGetDisplay failed");
        return false;
    }

    EGLint egl_major, egl_minor;
    if (!eglInitialize(ctx->egl_display, &egl_major, &egl_minor)) {
        GL_LOGE("eglInitialize failed");
        return false;
    }
    GL_LOGI("EGL %d.%d initialized", egl_major, egl_minor);

    /* Config: GLES3, pbuffer surface, RGBA8888, with depth/stencil */
    EGLint config_attribs[] = {
        EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, depth ? 24 : 0,
        EGL_STENCIL_SIZE, stencil ? 8 : 0,
        EGL_NONE,
    };

    EGLint num_configs;
    if (!eglChooseConfig(ctx->egl_display, config_attribs, &ctx->egl_config, 1, &num_configs) ||
        num_configs == 0) {
        GL_LOGE("eglChooseConfig failed (GLES3 pbuffer)");
        return false;
    }

    /* Create GLES3 context */
    EGLint context_attribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, (EGLint)version_major,
        EGL_NONE,
    };

    ctx->egl_context = eglCreateContext(
        ctx->egl_display, ctx->egl_config, EGL_NO_CONTEXT, context_attribs);
    if (ctx->egl_context == EGL_NO_CONTEXT) {
        GL_LOGE("eglCreateContext failed (GLES %u.%u)", version_major, version_minor);
        return false;
    }

    /* Create initial pbuffer at default resolution.
     * Will be resized by hw_gl_resize_fbo() when the core reports geometry. */
    unsigned init_w = 640, init_h = 480;
    EGLint pbuf_attribs[] = {
        EGL_WIDTH, (EGLint)init_w,
        EGL_HEIGHT, (EGLint)init_h,
        EGL_NONE,
    };
    ctx->egl_surface = eglCreatePbufferSurface(ctx->egl_display, ctx->egl_config, pbuf_attribs);
    if (ctx->egl_surface == EGL_NO_SURFACE) {
        GL_LOGE("eglCreatePbufferSurface failed");
        return false;
    }
    ctx->surface_width = init_w;
    ctx->surface_height = init_h;

    eglMakeCurrent(ctx->egl_display, ctx->egl_surface, ctx->egl_surface, ctx->egl_context);

    ctx->initialized = true;
    GL_LOGI("GLES3 HW render context initialised (GLES %u.%u, pbuffer %ux%u, depth=%d stencil=%d)",
            version_major, version_minor, init_w, init_h, depth, stencil);
    return true;
}

void hw_gl_deinit(hw_gl_context_t *ctx) {
    if (!ctx || !ctx->initialized) return;

    if (ctx->egl_display != EGL_NO_DISPLAY) {
        /* #907 — DO NOT call eglMakeCurrent(EGL_NO_CONTEXT). After a
         * core's context_destroy callback runs (PPSSPP and other GLES
         * HW-render cores spin up worker threads that bind/release
         * EGL on their own), Adreno's driver-side "currently-bound
         * context" cache is inconsistent. Calling release crashes
         * inside libGLESv2_adreno via a null vtable dispatch — the
         * exact tombstone pattern we hit twice while iterating on
         * this fix. eglDestroyContext on a still-current context is
         * well-defined per EGL spec (mark for deletion, free on
         * release or thread exit); the emulation thread exits within
         * seconds, so we're not leaking persistently.
         *
         * Surface destroy is independent and stays. Context destroy
         * relies on the spec's mark-for-deletion semantics. */
        if (ctx->egl_surface != EGL_NO_SURFACE) {
            eglDestroySurface(ctx->egl_display, ctx->egl_surface);
            ctx->egl_surface = EGL_NO_SURFACE;
        }
        if (ctx->egl_context != EGL_NO_CONTEXT) {
            eglDestroyContext(ctx->egl_display, ctx->egl_context);
            ctx->egl_context = EGL_NO_CONTEXT;
        }
        /* Do NOT call eglTerminate — the default display is shared with SurfaceFlinger */
        ctx->egl_display = EGL_NO_DISPLAY;
    }

    ctx->surface_width = 0;
    ctx->surface_height = 0;
    ctx->initialized = false;
    GL_LOGI("GLES HW render context deinitialized");
}

void hw_gl_make_current(hw_gl_context_t *ctx) {
    if (!ctx) return;
    if (ctx->egl_display != EGL_NO_DISPLAY && ctx->egl_context != EGL_NO_CONTEXT) {
        eglMakeCurrent(ctx->egl_display, ctx->egl_surface,
                       ctx->egl_surface, ctx->egl_context);
    }
}

void hw_gl_release_current(hw_gl_context_t *ctx) {
    if (!ctx) return;
    if (ctx->egl_display != EGL_NO_DISPLAY) {
        eglMakeCurrent(ctx->egl_display, EGL_NO_SURFACE,
                       EGL_NO_SURFACE, EGL_NO_CONTEXT);
    }
}

void hw_gl_resize_fbo(hw_gl_context_t *ctx, unsigned width, unsigned height) {
    if (!ctx || !ctx->initialized) return;
    if (width == ctx->surface_width && height == ctx->surface_height) return;

    GL_LOGI("Resizing pbuffer: %ux%u -> %ux%u", ctx->surface_width, ctx->surface_height,
            width, height);

    /* Unbind the current surface before destroying it */
    eglMakeCurrent(ctx->egl_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);

    if (ctx->egl_surface != EGL_NO_SURFACE) {
        eglDestroySurface(ctx->egl_display, ctx->egl_surface);
        ctx->egl_surface = EGL_NO_SURFACE;
    }

    /* Create new pbuffer at the requested size */
    EGLint pbuf_attribs[] = {
        EGL_WIDTH, (EGLint)width,
        EGL_HEIGHT, (EGLint)height,
        EGL_NONE,
    };
    ctx->egl_surface = eglCreatePbufferSurface(ctx->egl_display, ctx->egl_config, pbuf_attribs);
    if (ctx->egl_surface == EGL_NO_SURFACE) {
        GL_LOGE("eglCreatePbufferSurface failed during resize to %ux%u", width, height);
        ctx->surface_width = 0;
        ctx->surface_height = 0;
        return;
    }

    ctx->surface_width = width;
    ctx->surface_height = height;

    /* Re-bind with the new surface */
    eglMakeCurrent(ctx->egl_display, ctx->egl_surface, ctx->egl_surface, ctx->egl_context);

    glViewport(0, 0, width, height);
    GL_LOGI("Pbuffer resized to %ux%u", width, height);
}

uintptr_t hw_gl_get_framebuffer(hw_gl_context_t *ctx) {
    /* GLideN64 renders its final output to FBO 0 (the default framebuffer).
     * Our EGL pbuffer IS FBO 0, so we return 0. */
    (void)ctx;
    return 0;
}

void *hw_gl_get_proc_address(const char *sym) {
    /* On Android, eglGetProcAddress is the single correct mechanism
     * for resolving both core GLES and extension functions. */
    return (void *)eglGetProcAddress(sym);
}

unsigned hw_gl_read_pixels(hw_gl_context_t *ctx, void *out_data, size_t out_capacity,
                           unsigned req_width, unsigned req_height,
                           unsigned *out_width, unsigned *out_height) {
    if (!ctx || !ctx->initialized || !out_data) return 0;

    unsigned w = ctx->surface_width;
    unsigned h = ctx->surface_height;
    if (w == 0 || h == 0) return 0;
    /* Clamp to the core-reported frame size; the surface can be larger than
     * the current frame, which the core renders into the bottom-left
     * region. No-op when they match (the steady-state case). (#1268) */
    if (req_width != 0 && req_width < w) w = req_width;
    if (req_height != 0 && req_height < h) h = req_height;

    size_t needed = (size_t)w * h * 4; /* XRGB8888 output */

    if (out_capacity < needed) {
        GL_LOGE("Buffer too small: need %zu, have %zu", needed, out_capacity);
        return 0;
    }

    hw_gl_make_current(ctx);

    /* Read from FBO 0 (the default framebuffer = pbuffer surface) */
    glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
    glFinish();

    /* GLES only supports GL_RGBA + GL_UNSIGNED_BYTE for glReadPixels.
     * Read RGBA, then swizzle to XRGB8888 in-place. */
    glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, out_data);

    /* Swizzle RGBA -> XRGB8888 in-place.
     * RGBA in memory: R, G, B, A at byte offsets 0,1,2,3
     * XRGB8888 as uint32: 0xFFRRGGBB (on little-endian: BB, GG, RR, FF) */
    uint8_t *pixels = (uint8_t *)out_data;
    size_t total = (size_t)w * h;
    for (size_t i = 0; i < total; i++) {
        uint8_t r = pixels[i * 4 + 0];
        uint8_t g = pixels[i * 4 + 1];
        uint8_t b = pixels[i * 4 + 2];
        /* Write as XRGB8888 (little-endian: B, G, R, 0xFF) */
        pixels[i * 4 + 0] = b;
        pixels[i * 4 + 1] = g;
        pixels[i * 4 + 2] = r;
        pixels[i * 4 + 3] = 0xFF;
    }

    /* Do NOT flip vertically here — the Vulkan fullscreen quad vertex shader
     * already flips UV.y (uv.y = 1.0 - uv.y), which handles the GL
     * bottom-left origin → screen top-left origin conversion.
     * Flipping here would double-flip, producing an upside-down image. */

    if (out_width) *out_width = w;
    if (out_height) *out_height = h;

    return w * h;
}

void hw_gl_debug_reset_frame(void) {
    /* No-op on Android */
}

void hw_gl_rebind_gl_symbols(void) {
    /* No-op on Android — ELF doesn't need Mach-O style symbol rebinding */
}

#endif /* __ANDROID__ */
