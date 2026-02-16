/*
 * OpenGL HW Render - macOS implementation.
 *
 * Creates an OpenGL Core Profile context for libretro cores that render via
 * OpenGL. Uses a hidden NSWindow to back FBO 0 with a real surface, so the
 * core can render to FBO 0 with GL_BACK (its natural target). Falls back to
 * a bare CGL context with custom FBO + FBO scanning if the windowed approach
 * fails.
 *
 * After each frame, glReadPixels reads back XRGB8888 pixels which are then
 * fed to the Metal shader pipeline.
 *
 * macOS supports OpenGL up to 4.1 (deprecated but functional).
 * Most HW render cores need OpenGL 3.2+ Core Profile.
 */

#import <Cocoa/Cocoa.h>
#import <OpenGL/OpenGL.h>
#import <OpenGL/gl3.h>
#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <mach-o/dyld.h>
#include <mach-o/loader.h>
#include <mach-o/nlist.h>
#include <sys/mman.h>

#include "hw_render_gl.h"

#define LOG_PREFIX "[hw_render_gl] "
static FILE *g_hw_log_file = NULL;
static FILE *hw_log_get(void) {
    if (!g_hw_log_file) {
        g_hw_log_file = fopen("/tmp/spela_native_hw.log", "w");
        if (g_hw_log_file) setbuf(g_hw_log_file, NULL); /* unbuffered */
    }
    return g_hw_log_file ? g_hw_log_file : stderr;
}
#define LOGI(...) do { FILE *f = hw_log_get(); fprintf(f, LOG_PREFIX __VA_ARGS__); fprintf(f, "\n"); } while(0)
#define LOGW(...) do { FILE *f = hw_log_get(); fprintf(f, LOG_PREFIX "WARN: " __VA_ARGS__); fprintf(f, "\n"); } while(0)
#define LOGE(...) do { FILE *f = hw_log_get(); fprintf(f, LOG_PREFIX "ERROR: " __VA_ARGS__); fprintf(f, "\n"); } while(0)

/* === FBO 0 redirect mechanism ===
 *
 * We redirect glBindFramebuffer(target, 0) to our custom FBO via two layers:
 * 1. get_proc_address returns our wrapper (for cores that resolve dynamically)
 * 2. Runtime symbol rebinding patches the core .dylib's lazy pointers
 *    (for cores that statically link against OpenGL.framework)
 */

static GLuint g_hw_fbo_redirect = 0;

/* Pointer to the REAL GL functions (resolved once) */
typedef void (*gl_bind_framebuffer_fn)(GLenum target, GLuint framebuffer);
typedef void (*gl_draw_buffer_fn)(GLenum mode);
typedef void (*gl_draw_buffers_fn)(GLsizei n, const GLenum *bufs);
typedef void (*gl_read_buffer_fn)(GLenum mode);

static gl_bind_framebuffer_fn g_real_glBindFramebuffer = NULL;
static gl_draw_buffer_fn g_real_glDrawBuffer = NULL;
static gl_draw_buffers_fn g_real_glDrawBuffers = NULL;
static gl_read_buffer_fn g_real_glReadBuffer = NULL;

/* Track whether the currently bound FBO is our redirected FBO */
static bool g_fbo_redirected_draw = false;
static bool g_fbo_redirected_read = false;

/* Debug: track GL calls per frame */
static int g_bind_fb_call_count = 0;
static int g_bind_fb_zero_count = 0;
static int g_bind_fb_frame_count = 0;
static int g_clear_count = 0;
static int g_viewport_count = 0;
static int g_draw_count = 0;

/* Track binds to our FBO (by direct ID, not via FBO 0 redirect) */
static int g_bind_fb_our_fbo_count = 0;
static int g_bind_fb_our_fbo_draw_count = 0;  /* DRAW binds to our FBO */
static int g_bind_fb_our_fbo_read_count = 0;  /* READ binds to our FBO */
static int g_draws_on_our_fbo = 0;            /* draws while our FBO is DRAW target */

/* Track the FBO bound just BEFORE the core switches to our FBO.
 * In GLideN64's pipeline, the core renders to internal FBOs and then
 * binds our FBO for the final presentation. The FBO bound right before
 * that switch is the source of the final composite (the correct FBO
 * to read from). */
static GLuint g_prev_draw_fbo = 0;       /* FBO before last bind to our FBO */
static GLuint g_tracking_prev_fbo = 0;   /* current tracking state */
static int g_blit_count = 0;             /* blits per frame */
static int g_blit_to_our_fbo_count = 0;  /* blits targeting our FBO */

/* Track the DRAW framebuffer that has the most draws.
 * This is likely the core's main rendering target. */
static GLuint g_last_draw_fbo = 0;       /* current draw FBO */
static GLuint g_most_drawn_fbo = 0;      /* FBO with most draws in current frame */
static int g_most_drawn_count = 0;       /* draw count for that FBO */
static GLuint g_draw_fbo_counts[32];     /* per-FBO draw count (for FBO IDs 0-31) */

/* The blit destination FBO (non-our-FBO) from the last blit */
static GLuint g_blit_dst_fbo = 0;
static bool g_blit_dst_flipped = false;  /* true if last blit to non-our FBO had inverted Y */

void hw_gl_debug_reset_frame(void) {
    if (g_bind_fb_frame_count > 0 && (g_bind_fb_frame_count <= 5 || g_bind_fb_frame_count % 60 == 0)) {
        unsigned our_draws = (g_hw_fbo_redirect < 32) ? g_draw_fbo_counts[g_hw_fbo_redirect] : 0;
        fprintf(stderr, "[hw_gl] frame %d: binds=%d(zero=%d ours=%d) draw=%d(ours=%u most=%u:%d) "
                "blit=%d(ours=%d dst=%u flip=%d) prev=%u\n",
                g_bind_fb_frame_count, g_bind_fb_call_count, g_bind_fb_zero_count,
                g_bind_fb_our_fbo_count, g_draw_count, our_draws,
                g_most_drawn_fbo, g_most_drawn_count,
                g_blit_count, g_blit_to_our_fbo_count, g_blit_dst_fbo,
                g_blit_dst_flipped, g_prev_draw_fbo);
    }
    g_bind_fb_frame_count++;
    g_bind_fb_call_count = 0;
    g_bind_fb_zero_count = 0;
    g_bind_fb_our_fbo_count = 0;
    g_clear_count = 0;
    g_viewport_count = 0;
    g_draw_count = 0;
    g_blit_count = 0;
    g_blit_to_our_fbo_count = 0;
    /* Find the FBO with most draws this frame (excluding our FBO) */
    g_most_drawn_fbo = 0;
    g_most_drawn_count = 0;
    for (int i = 0; i < 32; i++) {
        if ((int)g_draw_fbo_counts[i] > g_most_drawn_count && i != (int)g_hw_fbo_redirect) {
            g_most_drawn_count = g_draw_fbo_counts[i];
            g_most_drawn_fbo = i;
        }
        g_draw_fbo_counts[i] = 0;
    }
}

/* Our wrapper that redirects FBO 0 -> our custom FBO */
static void hw_gl_bind_framebuffer_redirect(GLenum target, GLuint framebuffer) {
    g_bind_fb_call_count++;
    bool was_zero = (framebuffer == 0 && g_hw_fbo_redirect != 0);
    /* KEY FIX: Also detect when the core binds our FBO by its actual ID
     * (from get_current_framebuffer()). The core stores m_defaultFBO =
     * get_current_framebuffer() during context_reset, then later does
     * glBindFramebuffer(target, m_defaultFBO). Without this check,
     * g_fbo_redirected_draw stays false, so glDrawBuffer(GL_BACK) on our
     * custom FBO causes GL_INVALID_OPERATION and the final composite
     * draw silently fails, producing incomplete output (z-order issues). */
    bool is_our_fbo = (g_hw_fbo_redirect != 0 && framebuffer == g_hw_fbo_redirect);
    bool needs_redirect = was_zero || is_our_fbo;

    if (was_zero) {
        g_bind_fb_zero_count++;
        framebuffer = g_hw_fbo_redirect;
    }
    if (is_our_fbo) {
        g_bind_fb_our_fbo_count++;
    }
    /* Log first few calls per frame for debugging */
    if (g_bind_fb_frame_count < 3 && g_bind_fb_call_count <= 20) {
        LOGI("  glBindFramebuffer(0x%X, %u)%s%s", target, framebuffer,
             was_zero ? " [REDIRECTED from 0]" : "",
             is_our_fbo ? " [OUR FBO direct]" : "");
    }
    if (target == GL_FRAMEBUFFER || target == GL_DRAW_FRAMEBUFFER) {
        g_fbo_redirected_draw = needs_redirect;
        /* Track the FBO that was bound right before we switch to our FBO.
         * This is the core's final composite FBO (what we should read from). */
        if (needs_redirect) {
            g_prev_draw_fbo = g_tracking_prev_fbo;
            g_last_draw_fbo = framebuffer;  /* Track draws to our FBO too */
        } else {
            g_tracking_prev_fbo = framebuffer;
            g_last_draw_fbo = framebuffer;
        }
    }
    if (target == GL_FRAMEBUFFER || target == GL_READ_FRAMEBUFFER) {
        g_fbo_redirected_read = needs_redirect;
    }
    if (g_real_glBindFramebuffer) {
        g_real_glBindFramebuffer(target, framebuffer);
    }
    /* When binding our FBO (either via FBO 0 redirect or direct by ID),
     * ensure draw/read buffers are COLOR_ATTACHMENT0. Custom FBOs don't
     * have GL_BACK, so any core code that assumes GL_BACK would silently
     * fail with GL_INVALID_OPERATION. */
    if (needs_redirect && (target == GL_FRAMEBUFFER || target == GL_DRAW_FRAMEBUFFER)) {
        glDrawBuffer(GL_COLOR_ATTACHMENT0);
    }
    if (needs_redirect && (target == GL_FRAMEBUFFER || target == GL_READ_FRAMEBUFFER)) {
        glReadBuffer(GL_COLOR_ATTACHMENT0);
    }
}

/* Debug: intercept glClear to see if core clears our FBO */
static void hw_gl_clear_redirect(GLbitfield mask) {
    g_clear_count++;
    if (g_bind_fb_frame_count < 3 && g_clear_count <= 3) {
        GLint current_fbo = -1;
        glGetIntegerv(GL_FRAMEBUFFER_BINDING, &current_fbo);
        GLfloat cc[4];
        glGetFloatv(GL_COLOR_CLEAR_VALUE, cc);
        LOGI("  glClear(0x%X) fbo=%d clearColor=[%.2f,%.2f,%.2f,%.2f]",
             mask, current_fbo, cc[0], cc[1], cc[2], cc[3]);
    }
    glClear(mask);
}

/* Debug: intercept glViewport */
static void hw_gl_viewport_redirect(GLint x, GLint y, GLsizei w, GLsizei h) {
    g_viewport_count++;
    if (g_bind_fb_frame_count < 3 && g_viewport_count <= 3) {
        LOGI("  glViewport(%d, %d, %d, %d)", x, y, w, h);
    }
    glViewport(x, y, w, h);
}

/* Intercept glBlitFramebuffer: the core blits its final output to "FBO 0" (the screen).
 * When FBO 0 is redirected to our custom FBO, we need to ensure the draw buffer is
 * properly set. Also, if the core bound FBO 0 without going through our interceptor,
 * we fix it here. */
typedef void (*gl_blit_framebuffer_fn)(GLint srcX0, GLint srcY0, GLint srcX1, GLint srcY1,
                                        GLint dstX0, GLint dstY0, GLint dstX1, GLint dstY1,
                                        GLbitfield mask, GLenum filter);
static gl_blit_framebuffer_fn g_real_glBlitFramebuffer = NULL;

static void hw_gl_blit_framebuffer_redirect(GLint srcX0, GLint srcY0, GLint srcX1, GLint srcY1,
                                             GLint dstX0, GLint dstY0, GLint dstX1, GLint dstY1,
                                             GLbitfield mask, GLenum filter) {
    g_blit_count++;
    /* Check if draw framebuffer is FBO 0 or our FBO - ensure correct setup */
    if (g_hw_fbo_redirect != 0) {
        GLint draw_fbo = -1;
        glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &draw_fbo);
        bool is_ours = (draw_fbo == 0 || (GLuint)draw_fbo == g_hw_fbo_redirect);
        if (is_ours) {
            g_blit_to_our_fbo_count++;
            if (draw_fbo == 0) {
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, g_hw_fbo_redirect);
            }
            glDrawBuffer(GL_COLOR_ATTACHMENT0);
        }
        if (!is_ours) {
            g_blit_dst_fbo = (GLuint)draw_fbo;
            /* Track Y-flip: if dst Y is inverted, the blit flips vertically */
            g_blit_dst_flipped = (dstY0 > dstY1);
        }
        {
            static int blit_log_count = 0;
            if (blit_log_count < 5 || g_bind_fb_frame_count % 120 == 0) {
                GLint read_fbo = -1;
                glGetIntegerv(GL_READ_FRAMEBUFFER_BINDING, &read_fbo);
                LOGI("  glBlitFramebuffer src=%d->dst=%d(%s) [%d,%d,%d,%d]->[%d,%d,%d,%d] flip=%d",
                     read_fbo, draw_fbo, is_ours ? "OURS" : "other",
                     srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1,
                     dstY0 > dstY1);
                blit_log_count++;
            }
        }
    }
    if (g_real_glBlitFramebuffer) {
        g_real_glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }
}

/* Intercept glDrawBuffer: redirect GL_BACK -> appropriate target.
 * - When our custom FBO is active (g_fbo_redirected_draw): redirect GL_BACK -> GL_COLOR_ATTACHMENT0
 * - When FBO 0 is bound in a single-buffered context: redirect GL_BACK -> GL_FRONT
 *   (some cores call glDrawBuffer(GL_BACK) expecting a double-buffered context) */
static void hw_gl_draw_buffer_redirect(GLenum mode) {
    if (g_fbo_redirected_draw && (mode == GL_BACK || mode == GL_BACK_LEFT)) {
        mode = GL_COLOR_ATTACHMENT0;
    } else if (!g_fbo_redirected_draw && (mode == GL_BACK || mode == GL_BACK_LEFT)) {
        /* FBO 0 (default framebuffer) is bound. In single-buffered contexts,
         * GL_BACK doesn't exist. Redirect to GL_FRONT to prevent silent failure. */
        GLint dbl = 0;
        glGetIntegerv(GL_DOUBLEBUFFER, &dbl);
        if (!dbl) {
            mode = GL_FRONT;
        }
    }
    if (g_real_glDrawBuffer) {
        g_real_glDrawBuffer(mode);
    } else {
        glDrawBuffer(mode);
    }
}

/* Intercept glDrawBuffers: redirect GL_BACK -> appropriate target */
static void hw_gl_draw_buffers_redirect(GLsizei n, const GLenum *bufs) {
    GLenum fixed[16];
    if (n > 0 && n <= 16) {
        bool need_fix = false;
        for (GLsizei i = 0; i < n; i++) {
            if (bufs[i] == GL_BACK || bufs[i] == GL_BACK_LEFT) { need_fix = true; break; }
        }
        if (need_fix) {
            memcpy(fixed, bufs, n * sizeof(GLenum));
            GLenum replacement = GL_COLOR_ATTACHMENT0;
            if (!g_fbo_redirected_draw) {
                GLint dbl = 0;
                glGetIntegerv(GL_DOUBLEBUFFER, &dbl);
                replacement = dbl ? GL_BACK : GL_FRONT;
            }
            for (GLsizei i = 0; i < n; i++) {
                if (fixed[i] == GL_BACK || fixed[i] == GL_BACK_LEFT) {
                    fixed[i] = replacement;
                }
            }
            bufs = fixed;
        }
    }
    if (g_real_glDrawBuffers) {
        g_real_glDrawBuffers(n, bufs);
    } else {
        glDrawBuffers(n, bufs);
    }
}

/* Intercept glReadBuffer: redirect GL_BACK -> appropriate target */
static void hw_gl_read_buffer_redirect(GLenum mode) {
    if (mode == GL_BACK || mode == GL_BACK_LEFT) {
        if (g_fbo_redirected_read) {
            mode = GL_COLOR_ATTACHMENT0;
        } else {
            GLint dbl = 0;
            glGetIntegerv(GL_DOUBLEBUFFER, &dbl);
            if (!dbl) mode = GL_FRONT;
        }
    }
    if (g_real_glReadBuffer) {
        g_real_glReadBuffer(mode);
    } else {
        glReadBuffer(mode);
    }
}

/* === Minimal fishhook-style symbol rebinding ===
 *
 * Patches lazy/non-lazy symbol pointers in a Mach-O image to redirect
 * a specific function. This works on dlopen'd libraries (core .dylibs). */

/* Patch symbol pointers in a specific segment's sections.
 * __DATA_CONST pages are marked read-only after dyld resolves them on ARM64,
 * so we temporarily make them writable with mprotect before patching. */
static int g_rebind_diag_done = 0;  /* only log section diagnostics once */

static void patch_segment_pointers(const struct segment_command_64 *seg,
                                    intptr_t slide,
                                    const struct nlist_64 *symtab,
                                    const char *strtab,
                                    const uint32_t *indirect_symtab,
                                    uint32_t nsyms,
                                    const char *symbol_name,
                                    void *replacement, void **original) {
    const struct section_64 *sections = (const struct section_64 *)(seg + 1);
    for (uint32_t j = 0; j < seg->nsects; j++) {
        uint32_t sect_type = sections[j].flags & SECTION_TYPE;
        /* Section diagnostic logging removed - GOT rebinding confirmed working */
        if (sect_type != S_LAZY_SYMBOL_POINTERS && sect_type != S_NON_LAZY_SYMBOL_POINTERS) {
            continue;
        }

        uint32_t indirect_offset = sections[j].reserved1;
        void **pointers = (void **)(slide + sections[j].addr);
        uint32_t count = (uint32_t)(sections[j].size / sizeof(void *));

        for (uint32_t k = 0; k < count; k++) {
            uint32_t symtab_index = indirect_symtab[indirect_offset + k];
            if (symtab_index == INDIRECT_SYMBOL_ABS || symtab_index == INDIRECT_SYMBOL_LOCAL) {
                continue;
            }
            if (symtab_index >= nsyms) continue;

            uint32_t str_offset = symtab[symtab_index].n_un.n_strx;
            const char *sym = strtab + str_offset;
            /* Symbol names have a leading underscore on macOS */
            if (strcmp(sym + 1, symbol_name) == 0 || strcmp(sym, symbol_name) == 0) {
                if (original && *original == NULL) {
                    *original = pointers[k];
                }
                /* Make the page writable (needed for __DATA_CONST on ARM64) */
                uintptr_t page = (uintptr_t)&pointers[k] & ~(uintptr_t)0xFFF;
                int mp_ret = mprotect((void *)page, 0x1000, PROT_READ | PROT_WRITE);
                LOGI("  GOT patch: %s old=%p new=%p mprotect=%d sect=%s",
                     symbol_name, pointers[k], replacement, mp_ret,
                     sect_type == S_LAZY_SYMBOL_POINTERS ? "lazy" : "non_lazy");
                pointers[k] = replacement;
            }
        }
    }
}

static void rebind_symbols_in_image(const struct mach_header_64 *header,
                                     const char *symbol_name,
                                     void *replacement, void **original) {
    intptr_t slide = 0;

    /* Find the ASLR slide for this image */
    uint32_t image_count = _dyld_image_count();
    for (uint32_t i = 0; i < image_count; i++) {
        if ((const struct mach_header_64 *)_dyld_get_image_header(i) == header) {
            slide = _dyld_get_image_vmaddr_slide(i);
            break;
        }
    }

    /* Walk load commands to find __LINKEDIT, symtab, dysymtab,
     * and all data segments (__DATA and __DATA_CONST) */
    const struct segment_command_64 *linkedit_seg = NULL;
    const struct symtab_command *symtab_cmd = NULL;
    const struct dysymtab_command *dysymtab_cmd = NULL;
    const struct segment_command_64 *data_segs[4];
    int data_seg_count = 0;

    const uint8_t *cmd_ptr = (const uint8_t *)(header + 1);
    for (uint32_t i = 0; i < header->ncmds; i++) {
        const struct load_command *lc = (const struct load_command *)cmd_ptr;
        if (lc->cmd == LC_SEGMENT_64) {
            const struct segment_command_64 *seg = (const struct segment_command_64 *)lc;
            if (strcmp(seg->segname, SEG_LINKEDIT) == 0) linkedit_seg = seg;
            /* Check both __DATA and __DATA_CONST (ARM64 uses __DATA_CONST for __got) */
            if (strcmp(seg->segname, SEG_DATA) == 0 ||
                strcmp(seg->segname, "__DATA_CONST") == 0) {
                if (data_seg_count < 4) data_segs[data_seg_count++] = seg;
            }
        } else if (lc->cmd == LC_SYMTAB) {
            symtab_cmd = (const struct symtab_command *)lc;
        } else if (lc->cmd == LC_DYSYMTAB) {
            dysymtab_cmd = (const struct dysymtab_command *)lc;
        }
        cmd_ptr += lc->cmdsize;
    }

    if (!linkedit_seg || !symtab_cmd || !dysymtab_cmd || data_seg_count == 0) return;

    uintptr_t linkedit_base = (uintptr_t)(slide + linkedit_seg->vmaddr - linkedit_seg->fileoff);
    const struct nlist_64 *symtab = (const struct nlist_64 *)(linkedit_base + symtab_cmd->symoff);
    const char *strtab = (const char *)(linkedit_base + symtab_cmd->stroff);
    const uint32_t *indirect_symtab = (const uint32_t *)(linkedit_base + dysymtab_cmd->indirectsymoff);

    /* Walk sections in all data segments looking for lazy/non-lazy symbol pointers */
    for (int s = 0; s < data_seg_count; s++) {
        patch_segment_pointers(data_segs[s], slide, symtab, strtab,
                               indirect_symtab, symtab_cmd->nsyms,
                               symbol_name, replacement, original);
    }
}

/* Real function pointers for clear/viewport/draw (for Mach-O rebinding) */
typedef void (*gl_clear_fn)(GLbitfield mask);
typedef void (*gl_viewport_fn)(GLint x, GLint y, GLsizei w, GLsizei h);
typedef void (*gl_draw_arrays_fn)(GLenum mode, GLint first, GLsizei count);
typedef void (*gl_draw_elements_fn)(GLenum mode, GLsizei count, GLenum type, const void *indices);

static gl_clear_fn g_real_glClear = NULL;
static gl_viewport_fn g_real_glViewport = NULL;
static gl_draw_arrays_fn g_real_glDrawArrays = NULL;
static gl_draw_elements_fn g_real_glDrawElements = NULL;

/* Wrappers that call the real function but also track calls for diagnostics */
static void hw_gl_clear_real(GLbitfield mask) {
    g_clear_count++;
    if (g_real_glClear) g_real_glClear(mask);
    else glClear(mask);
}

static void hw_gl_viewport_real(GLint x, GLint y, GLsizei w, GLsizei h) {
    g_viewport_count++;
    if (g_real_glViewport) g_real_glViewport(x, y, w, h);
    else glViewport(x, y, w, h);
}

static int g_our_fbo_draw_diag_count = 0;

static void log_our_fbo_draw_state(const char *call) {
    if (g_our_fbo_draw_diag_count >= 5) return;
    g_our_fbo_draw_diag_count++;

    GLint program = 0, draw_buf = 0, fbo_binding = 0;
    GLint vp[4] = {0};
    glGetIntegerv(GL_CURRENT_PROGRAM, &program);
    glGetIntegerv(GL_DRAW_BUFFER, &draw_buf);
    glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &fbo_binding);
    glGetIntegerv(GL_VIEWPORT, vp);

    GLint vao = 0;
    glGetIntegerv(GL_VERTEX_ARRAY_BINDING, &vao);

    GLenum fb_status = glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER);

    GLint validate_ok = 0;
    glValidateProgram(program);
    glGetProgramiv(program, GL_VALIDATE_STATUS, &validate_ok);
    char info_log[512] = {0};
    GLsizei log_len = 0;
    glGetProgramInfoLog(program, sizeof(info_log), &log_len, info_log);

    /* Check depth/stencil/blend state */
    GLboolean depth_test = glIsEnabled(GL_DEPTH_TEST);
    GLboolean stencil_test = glIsEnabled(GL_STENCIL_TEST);
    GLboolean blend = glIsEnabled(GL_BLEND);
    GLboolean scissor = glIsEnabled(GL_SCISSOR_TEST);
    GLboolean cull = glIsEnabled(GL_CULL_FACE);

    /* Check color mask */
    GLboolean cmask[4];
    glGetBooleanv(GL_COLOR_WRITEMASK, cmask);

    /* Check if our FBO color_tex could be in a feedback loop */
    GLint fbo_color_tex = 0;
    glGetFramebufferAttachmentParameteriv(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
        GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME, &fbo_color_tex);

    /* Check draw buffer count (MRT) */
    GLint max_draw_bufs = 0;
    glGetIntegerv(GL_MAX_DRAW_BUFFERS, &max_draw_bufs);
    GLint draw_buf1 = GL_NONE;
    if (max_draw_bufs > 1) {
        glGetIntegerv(GL_DRAW_BUFFER1, &draw_buf1);
    }

    /* Drain accumulated errors for clean reporting */
    while (glGetError() != GL_NO_ERROR) {}

    LOGI("DRAW to our FBO (%s): fbo=%d program=%d drawbuf=0x%X(buf1=0x%X) vp=[%d,%d,%d,%d] "
         "vao=%d fb_status=0x%X validate=%d",
         call, fbo_binding, program, draw_buf, draw_buf1,
         vp[0], vp[1], vp[2], vp[3],
         vao, fb_status, validate_ok);
    LOGI("  depth=%d stencil=%d blend=%d scissor=%d cull=%d cmask=[%d%d%d%d]",
         depth_test, stencil_test, blend, scissor, cull,
         cmask[0], cmask[1], cmask[2], cmask[3]);
    LOGI("  fbo_color_tex=%d", fbo_color_tex);
    if (log_len > 0) {
        LOGI("  program info: %s", info_log);
    }

    /* Check textures bound on units 0-3 for feedback loop detection */
    for (int unit = 0; unit < 4; unit++) {
        glActiveTexture(GL_TEXTURE0 + unit);
        GLint tex2d = 0;
        glGetIntegerv(GL_TEXTURE_BINDING_2D, &tex2d);
        if (tex2d > 0) {
            LOGI("  tex_unit[%d]=%d%s", unit, tex2d,
                 (tex2d == fbo_color_tex) ? " ** FEEDBACK LOOP **" : "");
        }
    }
}

static void hw_gl_draw_arrays_wrapper(GLenum mode, GLint first, GLsizei count) {
    g_draw_count++;
    if (g_last_draw_fbo < 32) g_draw_fbo_counts[g_last_draw_fbo]++;
    if (g_hw_fbo_redirect != 0 && g_last_draw_fbo == (int)g_hw_fbo_redirect) {
        log_our_fbo_draw_state("glDrawArrays");
        /* Drain errors before draw */
        while (glGetError() != GL_NO_ERROR) {}
        if (g_real_glDrawArrays) g_real_glDrawArrays(mode, first, count);
        /* Check for error after draw and sample pixels */
        if (g_our_fbo_draw_diag_count <= 6) {
            GLenum post_err = glGetError();
            glReadBuffer(GL_COLOR_ATTACHMENT0);
            uint32_t px[4] = {0};
            glReadPixels(320, 240, 2, 2, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, px);
            LOGI("  POST draw: err=0x%X px=[0x%08X,0x%08X,0x%08X,0x%08X]",
                 post_err, px[0], px[1], px[2], px[3]);
        }
        return;
    }
    if (g_real_glDrawArrays) g_real_glDrawArrays(mode, first, count);
}

static void hw_gl_draw_elements_wrapper(GLenum mode, GLsizei count, GLenum type, const void *indices) {
    g_draw_count++;
    if (g_last_draw_fbo < 32) g_draw_fbo_counts[g_last_draw_fbo]++;
    if (g_hw_fbo_redirect != 0 && g_last_draw_fbo == (int)g_hw_fbo_redirect) {
        log_our_fbo_draw_state("glDrawElements");
    }
    if (g_real_glDrawElements) g_real_glDrawElements(mode, count, type, indices);
}

/* Rebind GL functions in a specific dlopen'd library */
static void rebind_gl_in_core(void *dl_handle) {
    /* Resolve the real GL functions first */
    if (!g_real_glBindFramebuffer) {
        g_real_glBindFramebuffer = (gl_bind_framebuffer_fn)dlsym(RTLD_DEFAULT, "glBindFramebuffer");
    }
    if (!g_real_glDrawBuffer) {
        g_real_glDrawBuffer = (gl_draw_buffer_fn)dlsym(RTLD_DEFAULT, "glDrawBuffer");
    }
    if (!g_real_glDrawBuffers) {
        g_real_glDrawBuffers = (gl_draw_buffers_fn)dlsym(RTLD_DEFAULT, "glDrawBuffers");
    }
    if (!g_real_glReadBuffer) {
        g_real_glReadBuffer = (gl_read_buffer_fn)dlsym(RTLD_DEFAULT, "glReadBuffer");
    }
    if (!g_real_glBlitFramebuffer) {
        g_real_glBlitFramebuffer = (gl_blit_framebuffer_fn)dlsym(RTLD_DEFAULT, "glBlitFramebuffer");
    }
    if (!g_real_glClear) {
        g_real_glClear = (gl_clear_fn)dlsym(RTLD_DEFAULT, "glClear");
    }
    if (!g_real_glViewport) {
        g_real_glViewport = (gl_viewport_fn)dlsym(RTLD_DEFAULT, "glViewport");
    }
    if (!g_real_glDrawArrays) {
        g_real_glDrawArrays = (gl_draw_arrays_fn)dlsym(RTLD_DEFAULT, "glDrawArrays");
    }
    if (!g_real_glDrawElements) {
        g_real_glDrawElements = (gl_draw_elements_fn)dlsym(RTLD_DEFAULT, "glDrawElements");
    }

    uint32_t image_count = _dyld_image_count();
    for (uint32_t i = 0; i < image_count; i++) {
        const struct mach_header_64 *header =
            (const struct mach_header_64 *)_dyld_get_image_header(i);
        if (!header) continue;

        const char *image_name = _dyld_get_image_name(i);

        /* Only rebind in libraries that are part of our app or cores */
        if (image_name && (strstr(image_name, "libretro") || strstr(image_name, "libspela"))) {
            LOGI("Rebinding GL symbols in: %s", image_name);
            rebind_symbols_in_image(header, "glBindFramebuffer",
                                    (void *)hw_gl_bind_framebuffer_redirect,
                                    (void **)&g_real_glBindFramebuffer);
            rebind_symbols_in_image(header, "glBindFramebufferEXT",
                                    (void *)hw_gl_bind_framebuffer_redirect,
                                    NULL);
            rebind_symbols_in_image(header, "glDrawBuffer",
                                    (void *)hw_gl_draw_buffer_redirect,
                                    (void **)&g_real_glDrawBuffer);
            rebind_symbols_in_image(header, "glDrawBuffers",
                                    (void *)hw_gl_draw_buffers_redirect,
                                    (void **)&g_real_glDrawBuffers);
            rebind_symbols_in_image(header, "glReadBuffer",
                                    (void *)hw_gl_read_buffer_redirect,
                                    (void **)&g_real_glReadBuffer);
            rebind_symbols_in_image(header, "glBlitFramebuffer",
                                    (void *)hw_gl_blit_framebuffer_redirect,
                                    (void **)&g_real_glBlitFramebuffer);
            /* Rebind core GL functions too - the core may call these directly
             * (not through rglgen/get_proc_address) */
            rebind_symbols_in_image(header, "glClear",
                                    (void *)hw_gl_clear_real,
                                    (void **)&g_real_glClear);
            rebind_symbols_in_image(header, "glViewport",
                                    (void *)hw_gl_viewport_real,
                                    (void **)&g_real_glViewport);
            rebind_symbols_in_image(header, "glDrawArrays",
                                    (void *)hw_gl_draw_arrays_wrapper,
                                    (void **)&g_real_glDrawArrays);
            rebind_symbols_in_image(header, "glDrawElements",
                                    (void *)hw_gl_draw_elements_wrapper,
                                    (void **)&g_real_glDrawElements);
        }
    }

    g_rebind_diag_done = 1;  /* Only log section diagnostics once */

    if (g_real_glBindFramebuffer) {
        LOGI("glBindFramebuffer interposition active (real=%p)", (void *)g_real_glBindFramebuffer);
    } else {
        LOGE("Failed to resolve real glBindFramebuffer!");
    }
}

/* Public entry point for GOT rebinding (called from libretro_bridge.c after context_reset) */
void hw_gl_rebind_gl_symbols(void) {
    rebind_gl_in_core(NULL);
}

/* === GL context and FBO management === */

struct hw_gl_context {
    CGLContextObj cgl_ctx;
    CGLPixelFormatObj pixel_format;

    /* Hidden window for FBO 0 backing (preferred path) */
    NSWindow *ns_window;
    NSOpenGLContext *ns_gl_context;

    GLuint fbo;
    GLuint color_tex;        /* texture for color attachment (cores need to sample it) */
    GLuint depth_rb;         /* renderbuffer for depth (optional) */
    GLuint depth_stencil_rb; /* renderbuffer for depth+stencil (optional) */
    GLuint default_vao;      /* required by Core Profile - cores need a bound VAO */

    unsigned fbo_width;
    unsigned fbo_height;

    bool has_depth;
    bool has_stencil;
    bool use_fbo0;           /* true if FBO 0 has real backing (window or PBuffer) */

    /* Readback FBO: used to blit FBO 0 content for glReadPixels */
    GLuint readback_fbo;
    GLuint readback_tex;
    unsigned readback_w;
    unsigned readback_h;

    /* Persistent readback buffer to avoid per-frame malloc for row flip */
    uint8_t *flip_row;
    size_t flip_row_size;
};

hw_gl_context_t *hw_gl_create(void) {
    hw_gl_context_t *ctx = calloc(1, sizeof(hw_gl_context_t));
    return ctx;
}

void hw_gl_destroy(hw_gl_context_t *ctx) {
    if (!ctx) return;
    hw_gl_deinit(ctx);
    if (ctx->flip_row) {
        free(ctx->flip_row);
        ctx->flip_row = NULL;
    }
    free(ctx);
}

static void destroy_fbo(hw_gl_context_t *ctx) {
    if (ctx->color_tex) {
        glDeleteTextures(1, &ctx->color_tex);
        ctx->color_tex = 0;
    }
    if (ctx->depth_rb) {
        glDeleteRenderbuffers(1, &ctx->depth_rb);
        ctx->depth_rb = 0;
    }
    if (ctx->depth_stencil_rb) {
        glDeleteRenderbuffers(1, &ctx->depth_stencil_rb);
        ctx->depth_stencil_rb = 0;
    }
    if (ctx->fbo) {
        glDeleteFramebuffers(1, &ctx->fbo);
        ctx->fbo = 0;
        g_hw_fbo_redirect = 0;
    }
    ctx->fbo_width = 0;
    ctx->fbo_height = 0;
}

static bool create_fbo(hw_gl_context_t *ctx, unsigned width, unsigned height) {
    if (width == 0 || height == 0) return false;

    destroy_fbo(ctx);

    glGenFramebuffers(1, &ctx->fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, ctx->fbo);

    /* Color attachment: use a TEXTURE so cores can sample from it. */
    glGenTextures(1, &ctx->color_tex);
    glBindTexture(GL_TEXTURE_2D, ctx->color_tex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0,
                 GL_RGBA, GL_UNSIGNED_BYTE, NULL);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                           GL_TEXTURE_2D, ctx->color_tex, 0);
    glBindTexture(GL_TEXTURE_2D, 0);

    /* Always create depth+stencil regardless of what the core requests.
     * macOS Metal-backed GL may need stencil for certain draw operations,
     * and having depth+stencil ensures maximum compatibility with core
     * rendering state (e.g., if stencil test is enabled during final draw). */
    if (ctx->has_depth || ctx->has_stencil) {
        glGenRenderbuffers(1, &ctx->depth_stencil_rb);
        glBindRenderbuffer(GL_RENDERBUFFER, ctx->depth_stencil_rb);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, width, height);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT,
                                  GL_RENDERBUFFER, ctx->depth_stencil_rb);
    }

    /* Set draw/read buffer to COLOR_ATTACHMENT0 (custom FBOs don't have GL_BACK) */
    glDrawBuffer(GL_COLOR_ATTACHMENT0);
    glReadBuffer(GL_COLOR_ATTACHMENT0);

    GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    if (status != GL_FRAMEBUFFER_COMPLETE) {
        LOGE("FBO incomplete: 0x%04X", status);
        destroy_fbo(ctx);
        return false;
    }

    ctx->fbo_width = width;
    ctx->fbo_height = height;

    /* Activate FBO 0 redirect */
    g_hw_fbo_redirect = ctx->fbo;

    LOGI("FBO created: %ux%u (depth=%d, stencil=%d, color=texture, fbo=%u)",
         width, height, ctx->has_depth, ctx->has_stencil, ctx->fbo);
    return true;
}

/* Create a hidden NSWindow with NSOpenGLContext.
 * This gives FBO 0 a real backing surface (the window's drawbuffer).
 * The core can render to FBO 0 with glDrawBuffer(GL_BACK) as normal. */
static bool create_windowed_gl_context(hw_gl_context_t *ctx,
                                        unsigned width, unsigned height,
                                        bool depth, bool stencil) {
    __block bool success = false;

    LOGI("create_windowed_gl_context: entry (is_main_thread=%d, width=%u, height=%u)",
         [NSThread isMainThread], width, height);

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
    void (^create_block)(void) = ^{
        LOGI("create_windowed_gl_context: block start (is_main_thread=%d)", [NSThread isMainThread]);
        @autoreleasepool {
            NSOpenGLPixelFormatAttribute attrs[] = {
                NSOpenGLPFAOpenGLProfile, NSOpenGLProfileVersion3_2Core,
                NSOpenGLPFAColorSize, 24,
                NSOpenGLPFAAlphaSize, 8,
                NSOpenGLPFADepthSize, (NSOpenGLPixelFormatAttribute)(depth ? 24 : 0),
                NSOpenGLPFAStencilSize, (NSOpenGLPixelFormatAttribute)(stencil ? 8 : 0),
                NSOpenGLPFAAccelerated,
                /* Single-buffered: no NSOpenGLPFADoubleBuffer.
                 * This makes FBO 0 readable via glReadPixels(GL_FRONT)
                 * since there's only one buffer. */
                0
            };
            NSOpenGLPixelFormat *pixelFormat =
                [[NSOpenGLPixelFormat alloc] initWithAttributes:attrs];
            if (!pixelFormat) {
                LOGE("NSOpenGLPixelFormat creation failed");
                return;
            }
            LOGI("create_windowed_gl_context: pixel format OK");

            /* Create a window for the GL backbuffer.
             * macOS requires the window to be ordered (visible) for the
             * backing store to be allocated. We make it fully transparent
             * so the user never sees it. */
            NSRect frame = NSMakeRect(0, 0, width, height);
            NSWindow *window = [[NSWindow alloc]
                initWithContentRect:frame
                          styleMask:NSWindowStyleMaskBorderless
                            backing:NSBackingStoreBuffered
                              defer:NO];
            [window setReleasedWhenClosed:NO];
            /* Hide the window - it's just a backing surface for the GL context.
             * Alpha must be >0 for macOS to commit the backing store. */
            [window setAlphaValue:0.01];
            [window setIgnoresMouseEvents:YES];
            [window setLevel:NSNormalWindowLevel - 1];
            /* Position off-screen so the barely-visible window isn't noticed */
            [window setFrameOrigin:NSMakePoint(-20000, -20000)];
            [window orderBack:nil];

            /* Disable retina scaling so pixel dimensions match point dimensions.
             * Without this, a 640x480pt window gets a 1280x960px backing on retina,
             * and glReadPixels(640,480) only reads a quarter of the buffer. */
            [[window contentView] setWantsBestResolutionOpenGLSurface:NO];

            LOGI("create_windowed_gl_context: window created, hasContentView=%d",
                 [window contentView] != nil);

            /* Create OpenGL context and attach to window's content view */
            NSOpenGLContext *glContext =
                [[NSOpenGLContext alloc] initWithFormat:pixelFormat shareContext:nil];
            if (!glContext) {
                LOGE("NSOpenGLContext creation failed");
                return;
            }
            LOGI("create_windowed_gl_context: GL context created");

            [glContext setView:[window contentView]];
            [glContext makeCurrentContext];

            ctx->ns_window = window;
            ctx->ns_gl_context = glContext;
            ctx->cgl_ctx = [glContext CGLContextObj];
            ctx->pixel_format = [pixelFormat CGLPixelFormatObj];
            ctx->use_fbo0 = true;
            ctx->fbo = 0;
            ctx->fbo_width = width;
            ctx->fbo_height = height;
            g_hw_fbo_redirect = 0;  /* No redirect needed - FBO 0 is real */

            /* Verify double-buffering is active */
            GLint dbl = 0;
            glGetIntegerv(GL_DOUBLEBUFFER, &dbl);
            LOGI("Window GL context: %ux%u, GL: %s, renderer: %s, double=%d",
                 width, height, glGetString(GL_VERSION), glGetString(GL_RENDERER), dbl);

            [NSOpenGLContext clearCurrentContext];
            success = true;
        }
    };
#pragma clang diagnostic pop

    if ([NSThread isMainThread]) {
        LOGI("create_windowed_gl_context: calling block directly (main thread)");
        create_block();
    } else {
        LOGI("create_windowed_gl_context: dispatching to main queue...");
        dispatch_sync(dispatch_get_main_queue(), create_block);
        LOGI("create_windowed_gl_context: dispatch_sync returned");
    }

    LOGI("create_windowed_gl_context: returning success=%d", success);
    return success;
}

bool hw_gl_init(hw_gl_context_t *ctx, unsigned version_major, unsigned version_minor,
                bool depth, bool stencil) {
    if (!ctx) return false;

    ctx->has_depth = depth;
    ctx->has_stencil = stencil;

    unsigned init_w = 640, init_h = 480;

    /* Try windowed context first — gives a real GL context backed by Metal
     * with FBO 0 as a real backbuffer (the hidden window's surface).
     * The core renders to FBO 0, we read via glReadPixels(GL_FRONT).
     *
     * GL 2.0+ functions go through rglgen → get_proc_address → our wrappers.
     * GL 1.x functions go through direct system calls, patched via GOT
     * rebinding (rebind_gl_in_core, called after context_reset). */
    if (create_windowed_gl_context(ctx, init_w, init_h, depth, stencil)) {
        LOGI("Using windowed GL context (FBO 0 = real backbuffer)");
        hw_gl_make_current(ctx);
        /* Core Profile requires a bound VAO for all vertex operations. */
        glGenVertexArrays(1, &ctx->default_vao);
        glBindVertexArray(ctx->default_vao);
        hw_gl_release_current(ctx);
        return true;
    }

    LOGW("Windowed GL context failed, falling back to bare CGL + custom FBO");

    /* Fallback: bare CGL context with custom FBO.
     * FBO 0 has no backing surface, so we create a custom FBO and redirect
     * core binds to FBO 0 → our custom FBO via glBindFramebuffer interceptor. */
    CGLPixelFormatAttribute cgl_attrs[] = {
        kCGLPFAOpenGLProfile, (CGLPixelFormatAttribute)kCGLOGLPVersion_3_2_Core,
        kCGLPFAColorSize, (CGLPixelFormatAttribute)24,
        kCGLPFAAlphaSize, (CGLPixelFormatAttribute)8,
        kCGLPFADepthSize, (CGLPixelFormatAttribute)(depth ? 24 : 0),
        kCGLPFAStencilSize, (CGLPixelFormatAttribute)(stencil ? 8 : 0),
        kCGLPFAAccelerated,
        kCGLPFAAllowOfflineRenderers,
        (CGLPixelFormatAttribute)0
    };

    GLint num_formats = 0;
    CGLError err = CGLChoosePixelFormat(cgl_attrs, &ctx->pixel_format, &num_formats);
    if (err != kCGLNoError || num_formats == 0) {
        LOGE("CGLChoosePixelFormat failed: %s", CGLErrorString(err));
        return false;
    }

    err = CGLCreateContext(ctx->pixel_format, NULL, &ctx->cgl_ctx);
    if (err != kCGLNoError) {
        LOGE("CGLCreateContext failed: %s", CGLErrorString(err));
        CGLDestroyPixelFormat(ctx->pixel_format);
        ctx->pixel_format = NULL;
        return false;
    }

    CGLSetCurrentContext(ctx->cgl_ctx);

    LOGI("CGL context created, GL version: %s", glGetString(GL_VERSION));
    LOGI("GL renderer: %s", glGetString(GL_RENDERER));

    /* Core Profile requires a bound VAO for all vertex operations. */
    glGenVertexArrays(1, &ctx->default_vao);
    glBindVertexArray(ctx->default_vao);

    if (!create_fbo(ctx, init_w, init_h)) {
        LOGE("Failed to create initial FBO");
        CGLSetCurrentContext(NULL);
        CGLDestroyContext(ctx->cgl_ctx);
        ctx->cgl_ctx = NULL;
        CGLDestroyPixelFormat(ctx->pixel_format);
        ctx->pixel_format = NULL;
        return false;
    }

    CGLSetCurrentContext(NULL);
    return true;
}

void hw_gl_deinit(hw_gl_context_t *ctx) {
    if (!ctx) return;

    if (ctx->cgl_ctx) {
        CGLSetCurrentContext(ctx->cgl_ctx);
        if (!ctx->use_fbo0) {
            destroy_fbo(ctx);
        }
        if (ctx->readback_fbo) {
            glDeleteFramebuffers(1, &ctx->readback_fbo);
            ctx->readback_fbo = 0;
        }
        if (ctx->readback_tex) {
            glDeleteTextures(1, &ctx->readback_tex);
            ctx->readback_tex = 0;
        }
        if (ctx->default_vao) {
            glDeleteVertexArrays(1, &ctx->default_vao);
            ctx->default_vao = 0;
        }
        CGLSetCurrentContext(NULL);
    }

    /* Clean up hidden window GL context (if used) */
    if (ctx->ns_gl_context) {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
        void (^cleanup_block)(void) = ^{
            [ctx->ns_gl_context clearDrawable];
            ctx->ns_gl_context = nil;
            if (ctx->ns_window) {
                [ctx->ns_window close];
                ctx->ns_window = nil;
            }
        };
#pragma clang diagnostic pop

        if ([NSThread isMainThread]) {
            cleanup_block();
        } else {
            dispatch_sync(dispatch_get_main_queue(), cleanup_block);
        }
        /* CGL context is owned by NSOpenGLContext — don't destroy it separately */
        ctx->cgl_ctx = NULL;
    } else if (ctx->cgl_ctx) {
        /* Bare CGL context path */
        CGLDestroyContext(ctx->cgl_ctx);
        ctx->cgl_ctx = NULL;
    }

    if (ctx->pixel_format && !ctx->ns_gl_context) {
        /* Only destroy pixel format if we own it (not from NSOpenGLContext) */
        CGLDestroyPixelFormat(ctx->pixel_format);
    }
    ctx->pixel_format = NULL;
    ctx->use_fbo0 = false;
}

void hw_gl_make_current(hw_gl_context_t *ctx) {
    if (ctx && ctx->cgl_ctx) {
        CGLSetCurrentContext(ctx->cgl_ctx);
    }
}

void hw_gl_release_current(hw_gl_context_t *ctx) {
    (void)ctx;
    CGLSetCurrentContext(NULL);
}

void hw_gl_resize_fbo(hw_gl_context_t *ctx, unsigned width, unsigned height) {
    if (!ctx || !ctx->cgl_ctx) return;
    if (width == ctx->fbo_width && height == ctx->fbo_height) return;

    if (ctx->use_fbo0 && ctx->ns_window) {
        /* Window-backed path: resize the hidden window */
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
        NSWindow *window = ctx->ns_window;
        NSOpenGLContext *glCtx = ctx->ns_gl_context;
        void (^resize_block)(void) = ^{
            NSRect frame = NSMakeRect(-20000, -20000, width, height);
            [window setFrame:[window frameRectForContentRect:frame] display:NO];
            [glCtx update];
        };
#pragma clang diagnostic pop

        if ([NSThread isMainThread]) {
            resize_block();
        } else {
            dispatch_sync(dispatch_get_main_queue(), resize_block);
        }
        ctx->fbo_width = width;
        ctx->fbo_height = height;
        LOGI("Window resized: %ux%u", width, height);
        return;
    }

    CGLSetCurrentContext(ctx->cgl_ctx);
    create_fbo(ctx, width, height);
}

uintptr_t hw_gl_get_framebuffer(hw_gl_context_t *ctx) {
    if (!ctx) return 0;
    return (uintptr_t)ctx->fbo;
}

void *hw_gl_get_proc_address(const char *sym) {
    /* Intercept via get_proc_address for cores that resolve dynamically */
    if (strcmp(sym, "glBindFramebuffer") == 0 ||
        strcmp(sym, "glBindFramebufferEXT") == 0) {
        return (void *)hw_gl_bind_framebuffer_redirect;
    }
    if (strcmp(sym, "glDrawBuffer") == 0) {
        return (void *)hw_gl_draw_buffer_redirect;
    }
    if (strcmp(sym, "glDrawBuffers") == 0) {
        return (void *)hw_gl_draw_buffers_redirect;
    }
    if (strcmp(sym, "glReadBuffer") == 0) {
        return (void *)hw_gl_read_buffer_redirect;
    }
    if (strcmp(sym, "glBlitFramebuffer") == 0) {
        return (void *)hw_gl_blit_framebuffer_redirect;
    }
    if (strcmp(sym, "glClear") == 0) {
        return (void *)hw_gl_clear_redirect;
    }
    if (strcmp(sym, "glViewport") == 0) {
        return (void *)hw_gl_viewport_redirect;
    }
    if (strcmp(sym, "glDrawArrays") == 0) {
        return (void *)hw_gl_draw_arrays_wrapper;
    }
    if (strcmp(sym, "glDrawElements") == 0) {
        return (void *)hw_gl_draw_elements_wrapper;
    }
    return dlsym(RTLD_DEFAULT, sym);
}

static int g_readback_log_count = 0;

/* Get actual texture dimensions for an FBO's color attachment.
 * Returns false if dimensions couldn't be determined. */
static bool get_fbo_dimensions(GLuint fbo_id, unsigned *out_w, unsigned *out_h) {
    GLint attach_type = 0;
    glGetFramebufferAttachmentParameteriv(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
        GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE, &attach_type);
    if (attach_type == GL_TEXTURE) {
        GLint tex_name = 0;
        glGetFramebufferAttachmentParameteriv(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
            GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME, &tex_name);
        if (tex_name > 0) {
            GLint saved_tex = 0;
            glGetIntegerv(GL_TEXTURE_BINDING_2D, &saved_tex);
            glBindTexture(GL_TEXTURE_2D, tex_name);
            GLint tw = 0, th = 0;
            glGetTexLevelParameteriv(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH, &tw);
            glGetTexLevelParameteriv(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT, &th);
            glBindTexture(GL_TEXTURE_2D, saved_tex);
            if (tw > 0 && th > 0) {
                *out_w = (unsigned)tw;
                *out_h = (unsigned)th;
                return true;
            }
        }
    }
    return false;
}

/* Quick check: does an FBO have any non-black content at center?
 * Uses a single 2x2 read (minimal overhead). Returns true if content found. */
static bool fbo_has_content(GLuint fbo_id, unsigned fw, unsigned fh) {
    while (glGetError() != GL_NO_ERROR) { /* drain */ }
    uint32_t px[4] = {0};
    glReadPixels(fw / 2, fh / 2, 2, 2, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, px);
    if (glGetError() != GL_NO_ERROR) return false;
    for (int i = 0; i < 4; i++) {
        if ((px[i] | 0xFF000000) != 0xFF000000) return true;
    }
    return false;
}

unsigned hw_gl_read_pixels(hw_gl_context_t *ctx, void *out_data, size_t out_capacity,
                           unsigned *out_width, unsigned *out_height) {
    if (!ctx || !ctx->cgl_ctx) return 0;

    unsigned w = ctx->fbo_width;
    unsigned h = ctx->fbo_height;

    if (!out_data || w == 0 || h == 0) return 0;

    CGLSetCurrentContext(ctx->cgl_ctx);

    bool log_this_frame = (g_readback_log_count < 10 || g_readback_log_count % 300 == 0);
    g_readback_log_count++;

    /* Drain accumulated GL errors from the core's rendering */
    while (glGetError() != GL_NO_ERROR) { /* drain */ }

    GLuint read_fbo = ctx->fbo;
    unsigned read_w = w, read_h = h;

    /* Windowed path: ctx->fbo=0, read from FBO 0 via GL_FRONT (single-buffered).
     * Bare CGL path: ctx->fbo=custom FBO, read via GL_COLOR_ATTACHMENT0. */

    glFlush();

    /* Bind our FBO for reading (bypass interceptor) */
    if (g_real_glBindFramebuffer) {
        g_real_glBindFramebuffer(GL_READ_FRAMEBUFFER, ctx->fbo);
    } else {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, ctx->fbo);
    }
    glReadBuffer((ctx->fbo == 0) ? GL_FRONT : GL_COLOR_ATTACHMENT0);

    if (log_this_frame) {
        /* Quick pixel check for diagnostic */
        uint32_t test_px[4] = {0};
        glReadPixels(w / 2, h / 2, 2, 2, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, test_px);
        LOGI("readback frame %d: fbo=%u %ux%u center=[0x%08X,0x%08X,0x%08X,0x%08X]",
             g_readback_log_count, read_fbo, read_w, read_h,
             test_px[0], test_px[1], test_px[2], test_px[3]);
    }

    /* Ensure read dimensions fit in output buffer */
    size_t row_bytes = (size_t)read_w * 4;
    size_t needed = row_bytes * read_h;
    if (out_capacity < needed) {
        read_w = w;
        read_h = h;
        row_bytes = (size_t)w * 4;
        needed = row_bytes * h;
        if (out_capacity < needed) return 0;
    }

    w = read_w;
    h = read_h;

    glReadPixels(0, 0, w, h, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, out_data);

    /* Force alpha to fully opaque (XRGB8888).
     * NO vertical flip — the Metal display pipeline (vertex shader) already flips
     * Y coordinates (1.0 - uv.y) to convert GL's bottom-left origin to Metal's
     * top-left origin. Adding a CPU flip here would double-flip = upside down. */
    {
        uint32_t *px = (uint32_t *)out_data;
        for (unsigned i = 0; i < w * h; i++) px[i] |= 0xFF000000;
    }

    if (out_width) *out_width = w;
    if (out_height) *out_height = h;

    return w * h;
}
