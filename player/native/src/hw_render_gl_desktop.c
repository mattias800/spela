/*
 * OpenGL HW Render Module - Desktop (Linux/Windows).
 *
 * Provides an offscreen OpenGL context with FBO for libretro cores
 * that request hardware-accelerated rendering via RETRO_ENVIRONMENT_SET_HW_RENDER.
 *
 * Linux: EGL pbuffer surface (works on X11 and Wayland)
 * Windows: WGL with hidden window
 *
 * After each frame, pixels are read back via glReadPixels and fed through
 * the Vulkan shader pipeline.
 */

/* Only compile on non-Android, non-Apple desktop */
#if !defined(__ANDROID__) && !defined(__APPLE__)

#include "hw_render_gl.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define GL_LOGI(...) do { fprintf(stdout, "[HW_GL] "); fprintf(stdout, __VA_ARGS__); fprintf(stdout, "\n"); } while(0)
#define GL_LOGE(...) do { fprintf(stderr, "[HW_GL ERROR] "); fprintf(stderr, __VA_ARGS__); fprintf(stderr, "\n"); } while(0)

/* ===== Platform headers ===== */

#ifdef _WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <wingdi.h>

/* WGL function types */
typedef HGLRC (WINAPI *PFN_wglCreateContext)(HDC);
typedef BOOL  (WINAPI *PFN_wglDeleteContext)(HGLRC);
typedef BOOL  (WINAPI *PFN_wglMakeCurrent)(HDC, HGLRC);
typedef PROC  (WINAPI *PFN_wglGetProcAddress)(LPCSTR);

/* WGL_ARB_create_context */
typedef HGLRC (WINAPI *PFN_wglCreateContextAttribsARB)(HDC, HGLRC, const int *);
#define WGL_CONTEXT_MAJOR_VERSION_ARB 0x2091
#define WGL_CONTEXT_MINOR_VERSION_ARB 0x2092
#define WGL_CONTEXT_PROFILE_MASK_ARB  0x9126
#define WGL_CONTEXT_CORE_PROFILE_BIT_ARB 0x00000001

#else /* Linux */
#include <dlfcn.h>

/* EGL types and constants */
typedef void *EGLDisplay;
typedef void *EGLConfig;
typedef void *EGLContext;
typedef void *EGLSurface;
typedef unsigned int EGLBoolean;
typedef int32_t EGLint;

#define EGL_DEFAULT_DISPLAY ((void *)0)
#define EGL_NO_DISPLAY      ((EGLDisplay)0)
#define EGL_NO_CONTEXT      ((EGLContext)0)
#define EGL_NO_SURFACE      ((EGLSurface)0)
#define EGL_FALSE           0
#define EGL_TRUE            1

/* EGL attribute tokens (from EGL 1.5 spec) */
#define EGL_SURFACE_TYPE      0x3033
#define EGL_PBUFFER_BIT       0x0001
#define EGL_RENDERABLE_TYPE   0x3040
#define EGL_OPENGL_BIT        0x0008
#define EGL_RED_SIZE          0x3024
#define EGL_GREEN_SIZE        0x3023
#define EGL_BLUE_SIZE         0x3022
#define EGL_ALPHA_SIZE        0x3021
#define EGL_DEPTH_SIZE        0x3025
#define EGL_STENCIL_SIZE      0x3026
#define EGL_NONE              0x3038
#define EGL_CONTEXT_MAJOR_VERSION 0x3098
#define EGL_CONTEXT_MINOR_VERSION 0x30FB
#define EGL_CONTEXT_OPENGL_PROFILE_MASK 0x30FD
#define EGL_CONTEXT_OPENGL_CORE_PROFILE_BIT 0x00000001
#define EGL_WIDTH             0x3057
#define EGL_HEIGHT            0x3058
#define EGL_OPENGL_API        0x30A2

/* EGL function types */
typedef EGLDisplay (*PFN_eglGetDisplay)(void *);
typedef EGLBoolean (*PFN_eglInitialize)(EGLDisplay, EGLint *, EGLint *);
typedef EGLBoolean (*PFN_eglTerminate)(EGLDisplay);
typedef EGLBoolean (*PFN_eglChooseConfig)(EGLDisplay, const EGLint *, EGLConfig *, EGLint, EGLint *);
typedef EGLContext  (*PFN_eglCreateContext)(EGLDisplay, EGLConfig, EGLContext, const EGLint *);
typedef EGLBoolean (*PFN_eglDestroyContext)(EGLDisplay, EGLContext);
typedef EGLSurface (*PFN_eglCreatePbufferSurface)(EGLDisplay, EGLConfig, const EGLint *);
typedef EGLBoolean (*PFN_eglDestroySurface)(EGLDisplay, EGLSurface);
typedef EGLBoolean (*PFN_eglMakeCurrent)(EGLDisplay, EGLSurface, EGLSurface, EGLContext);
typedef EGLBoolean (*PFN_eglBindAPI)(unsigned int);
typedef void *     (*PFN_eglGetProcAddress)(const char *);
#endif

/* ===== OpenGL types and constants ===== */

typedef unsigned int GLenum;
typedef unsigned int GLuint;
typedef int GLint;
typedef int GLsizei;
typedef unsigned char GLboolean;
typedef unsigned int GLbitfield;
typedef float GLfloat;
typedef void GLvoid;

#define GL_TRUE                  1
#define GL_FALSE                 0
#define GL_NO_ERROR              0
#define GL_COLOR_BUFFER_BIT      0x00004000
#define GL_DEPTH_BUFFER_BIT      0x00000100
#define GL_STENCIL_BUFFER_BIT    0x00000400
#define GL_FRAMEBUFFER           0x8D40
#define GL_RENDERBUFFER          0x8D41
#define GL_COLOR_ATTACHMENT0     0x8CE0
#define GL_DEPTH_ATTACHMENT      0x8D00
#define GL_STENCIL_ATTACHMENT    0x8D20
#define GL_DEPTH_STENCIL_ATTACHMENT 0x821A
#define GL_FRAMEBUFFER_COMPLETE  0x8CD5
#define GL_DEPTH_COMPONENT24     0x81A6
#define GL_DEPTH24_STENCIL8      0x88F0
#define GL_STENCIL_INDEX8        0x8D48
#define GL_TEXTURE_2D            0x0DE1
#define GL_RGBA                  0x1908
#define GL_BGRA                  0x80E1
#define GL_RGBA8                 0x8058
#define GL_UNSIGNED_BYTE         0x1401
#define GL_UNSIGNED_INT_8_8_8_8_REV 0x8367
#define GL_TEXTURE_MIN_FILTER    0x2801
#define GL_TEXTURE_MAG_FILTER    0x2800
#define GL_NEAREST               0x2600
#define GL_READ_FRAMEBUFFER      0x8CA8

/* GL function pointer types */
typedef GLenum  (*PFN_glGetError)(void);
typedef void    (*PFN_glGenFramebuffers)(GLsizei, GLuint *);
typedef void    (*PFN_glBindFramebuffer)(GLenum, GLuint);
typedef void    (*PFN_glDeleteFramebuffers)(GLsizei, const GLuint *);
typedef void    (*PFN_glGenRenderbuffers)(GLsizei, GLuint *);
typedef void    (*PFN_glBindRenderbuffer)(GLenum, GLuint);
typedef void    (*PFN_glRenderbufferStorage)(GLenum, GLenum, GLsizei, GLsizei);
typedef void    (*PFN_glDeleteRenderbuffers)(GLsizei, const GLuint *);
typedef void    (*PFN_glFramebufferRenderbuffer)(GLenum, GLenum, GLenum, GLuint);
typedef void    (*PFN_glGenTextures)(GLsizei, GLuint *);
typedef void    (*PFN_glBindTexture)(GLenum, GLuint);
typedef void    (*PFN_glDeleteTextures)(GLsizei, const GLuint *);
typedef void    (*PFN_glTexImage2D)(GLenum, GLint, GLint, GLsizei, GLsizei, GLint, GLenum, GLenum, const void *);
typedef void    (*PFN_glTexParameteri)(GLenum, GLenum, GLint);
typedef void    (*PFN_glFramebufferTexture2D)(GLenum, GLenum, GLenum, GLuint, GLint);
typedef GLenum  (*PFN_glCheckFramebufferStatus)(GLenum);
typedef void    (*PFN_glViewport)(GLint, GLint, GLsizei, GLsizei);
typedef void    (*PFN_glReadPixels)(GLint, GLint, GLsizei, GLsizei, GLenum, GLenum, void *);
typedef void    (*PFN_glClear)(GLbitfield);
typedef void    (*PFN_glClearColor)(GLfloat, GLfloat, GLfloat, GLfloat);
typedef void    (*PFN_glFlush)(void);
typedef void    (*PFN_glFinish)(void);

/* ===== Context struct ===== */

struct hw_gl_context {
#ifdef _WIN32
    HMODULE gl_lib;
    HWND hwnd;
    HDC hdc;
    HGLRC hglrc;
    PFN_wglCreateContext pfn_wglCreateContext;
    PFN_wglDeleteContext pfn_wglDeleteContext;
    PFN_wglMakeCurrent pfn_wglMakeCurrent;
    PFN_wglGetProcAddress pfn_wglGetProcAddress;
    PFN_wglCreateContextAttribsARB pfn_wglCreateContextAttribsARB;
#else /* Linux */
    void *gl_lib;
    void *egl_lib;
    EGLDisplay egl_display;
    EGLContext egl_context;
    EGLSurface egl_surface;
    PFN_eglGetDisplay pfn_eglGetDisplay;
    PFN_eglInitialize pfn_eglInitialize;
    PFN_eglTerminate pfn_eglTerminate;
    PFN_eglChooseConfig pfn_eglChooseConfig;
    PFN_eglCreateContext pfn_eglCreateContext;
    PFN_eglDestroyContext pfn_eglDestroyContext;
    PFN_eglCreatePbufferSurface pfn_eglCreatePbufferSurface;
    PFN_eglDestroySurface pfn_eglDestroySurface;
    PFN_eglMakeCurrent pfn_eglMakeCurrent;
    PFN_eglBindAPI pfn_eglBindAPI;
    PFN_eglGetProcAddress pfn_eglGetProcAddress;
#endif

    /* GL function pointers */
    PFN_glGetError pfn_glGetError;
    PFN_glGenFramebuffers pfn_glGenFramebuffers;
    PFN_glBindFramebuffer pfn_glBindFramebuffer;
    PFN_glDeleteFramebuffers pfn_glDeleteFramebuffers;
    PFN_glGenRenderbuffers pfn_glGenRenderbuffers;
    PFN_glBindRenderbuffer pfn_glBindRenderbuffer;
    PFN_glRenderbufferStorage pfn_glRenderbufferStorage;
    PFN_glDeleteRenderbuffers pfn_glDeleteRenderbuffers;
    PFN_glFramebufferRenderbuffer pfn_glFramebufferRenderbuffer;
    PFN_glGenTextures pfn_glGenTextures;
    PFN_glBindTexture pfn_glBindTexture;
    PFN_glDeleteTextures pfn_glDeleteTextures;
    PFN_glTexImage2D pfn_glTexImage2D;
    PFN_glTexParameteri pfn_glTexParameteri;
    PFN_glFramebufferTexture2D pfn_glFramebufferTexture2D;
    PFN_glCheckFramebufferStatus pfn_glCheckFramebufferStatus;
    PFN_glViewport pfn_glViewport;
    PFN_glReadPixels pfn_glReadPixels;
    PFN_glClear pfn_glClear;
    PFN_glClearColor pfn_glClearColor;
    PFN_glFlush pfn_glFlush;
    PFN_glFinish pfn_glFinish;

    /* FBO state */
    GLuint fbo;
    GLuint color_tex;
    GLuint depth_rb;
    GLuint stencil_rb;
    unsigned fbo_width;
    unsigned fbo_height;
    bool has_depth;
    bool has_stencil;
    bool initialized;
};

/* ===== GL function loading ===== */

static void *load_gl_func(hw_gl_context_t *ctx, const char *name) {
    void *proc = NULL;
#ifdef _WIN32
    /* Try wglGetProcAddress first (for extension functions), then GetProcAddress */
    if (ctx->pfn_wglGetProcAddress) {
        proc = (void *)ctx->pfn_wglGetProcAddress(name);
    }
    if (!proc && ctx->gl_lib) {
        proc = (void *)GetProcAddress(ctx->gl_lib, name);
    }
#else
    /* Try eglGetProcAddress first, then dlsym from libGL */
    if (ctx->pfn_eglGetProcAddress) {
        proc = ctx->pfn_eglGetProcAddress(name);
    }
    if (!proc && ctx->gl_lib) {
        proc = dlsym(ctx->gl_lib, name);
    }
#endif
    return proc;
}

static bool load_gl_functions(hw_gl_context_t *ctx) {
#define LOAD_GL(fn) do { \
    ctx->pfn_##fn = (PFN_##fn)load_gl_func(ctx, #fn); \
    if (!ctx->pfn_##fn) { GL_LOGE("Failed to load " #fn); return false; } \
} while(0)

    LOAD_GL(glGetError);
    LOAD_GL(glGenFramebuffers);
    LOAD_GL(glBindFramebuffer);
    LOAD_GL(glDeleteFramebuffers);
    LOAD_GL(glGenRenderbuffers);
    LOAD_GL(glBindRenderbuffer);
    LOAD_GL(glRenderbufferStorage);
    LOAD_GL(glDeleteRenderbuffers);
    LOAD_GL(glFramebufferRenderbuffer);
    LOAD_GL(glGenTextures);
    LOAD_GL(glBindTexture);
    LOAD_GL(glDeleteTextures);
    LOAD_GL(glTexImage2D);
    LOAD_GL(glTexParameteri);
    LOAD_GL(glFramebufferTexture2D);
    LOAD_GL(glCheckFramebufferStatus);
    LOAD_GL(glViewport);
    LOAD_GL(glReadPixels);
    LOAD_GL(glClear);
    LOAD_GL(glClearColor);
    LOAD_GL(glFlush);
    LOAD_GL(glFinish);

#undef LOAD_GL
    return true;
}

/* ===== Platform-specific context creation ===== */

#ifdef _WIN32

static LRESULT CALLBACK dummy_wndproc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    return DefWindowProcA(hwnd, msg, wp, lp);
}

static bool create_gl_context_win32(hw_gl_context_t *ctx, unsigned vmaj, unsigned vmin) {
    ctx->gl_lib = LoadLibraryA("opengl32.dll");
    if (!ctx->gl_lib) {
        GL_LOGE("Failed to load opengl32.dll");
        return false;
    }

    ctx->pfn_wglCreateContext = (PFN_wglCreateContext)GetProcAddress(ctx->gl_lib, "wglCreateContext");
    ctx->pfn_wglDeleteContext = (PFN_wglDeleteContext)GetProcAddress(ctx->gl_lib, "wglDeleteContext");
    ctx->pfn_wglMakeCurrent = (PFN_wglMakeCurrent)GetProcAddress(ctx->gl_lib, "wglMakeCurrent");
    ctx->pfn_wglGetProcAddress = (PFN_wglGetProcAddress)GetProcAddress(ctx->gl_lib, "wglGetProcAddress");

    if (!ctx->pfn_wglCreateContext || !ctx->pfn_wglDeleteContext ||
        !ctx->pfn_wglMakeCurrent || !ctx->pfn_wglGetProcAddress) {
        GL_LOGE("Failed to load WGL functions");
        return false;
    }

    /* Create a hidden window for the GL context */
    WNDCLASSA wc = {
        .lpfnWndProc = dummy_wndproc,
        .hInstance = GetModuleHandle(NULL),
        .lpszClassName = "SpelaHWGL",
    };
    RegisterClassA(&wc);

    ctx->hwnd = CreateWindowExA(0, "SpelaHWGL", "SpelaHWGL", 0,
        0, 0, 1, 1, NULL, NULL, GetModuleHandle(NULL), NULL);
    if (!ctx->hwnd) {
        GL_LOGE("Failed to create hidden window");
        return false;
    }

    ctx->hdc = GetDC(ctx->hwnd);

    PIXELFORMATDESCRIPTOR pfd = {
        .nSize = sizeof(pfd),
        .nVersion = 1,
        .dwFlags = PFD_DRAW_TO_WINDOW | PFD_SUPPORT_OPENGL | PFD_DOUBLEBUFFER,
        .iPixelType = PFD_TYPE_RGBA,
        .cColorBits = 32,
        .cDepthBits = 24,
        .cStencilBits = 8,
        .iLayerType = PFD_MAIN_PLANE,
    };

    int format = ChoosePixelFormat(ctx->hdc, &pfd);
    if (!format || !SetPixelFormat(ctx->hdc, format, &pfd)) {
        GL_LOGE("Failed to set pixel format");
        return false;
    }

    /* Create a temporary context to get wglCreateContextAttribsARB */
    HGLRC tmp_ctx = ctx->pfn_wglCreateContext(ctx->hdc);
    if (!tmp_ctx) {
        GL_LOGE("Failed to create temporary GL context");
        return false;
    }
    ctx->pfn_wglMakeCurrent(ctx->hdc, tmp_ctx);

    ctx->pfn_wglCreateContextAttribsARB =
        (PFN_wglCreateContextAttribsARB)ctx->pfn_wglGetProcAddress("wglCreateContextAttribsARB");

    if (ctx->pfn_wglCreateContextAttribsARB && vmaj >= 3) {
        int attribs[] = {
            WGL_CONTEXT_MAJOR_VERSION_ARB, (int)vmaj,
            WGL_CONTEXT_MINOR_VERSION_ARB, (int)vmin,
            WGL_CONTEXT_PROFILE_MASK_ARB, WGL_CONTEXT_CORE_PROFILE_BIT_ARB,
            0,
        };
        ctx->hglrc = ctx->pfn_wglCreateContextAttribsARB(ctx->hdc, NULL, attribs);
    }

    if (!ctx->hglrc) {
        /* Fall back to legacy context */
        ctx->hglrc = ctx->pfn_wglCreateContext(ctx->hdc);
    }

    ctx->pfn_wglDeleteContext(tmp_ctx);

    if (!ctx->hglrc) {
        GL_LOGE("Failed to create GL context");
        return false;
    }

    ctx->pfn_wglMakeCurrent(ctx->hdc, ctx->hglrc);
    GL_LOGI("WGL context created (GL %u.%u)", vmaj, vmin);
    return true;
}

static void destroy_gl_context_win32(hw_gl_context_t *ctx) {
    if (ctx->hglrc) {
        ctx->pfn_wglMakeCurrent(NULL, NULL);
        ctx->pfn_wglDeleteContext(ctx->hglrc);
        ctx->hglrc = NULL;
    }
    if (ctx->hdc && ctx->hwnd) {
        ReleaseDC(ctx->hwnd, ctx->hdc);
        ctx->hdc = NULL;
    }
    if (ctx->hwnd) {
        DestroyWindow(ctx->hwnd);
        ctx->hwnd = NULL;
    }
    if (ctx->gl_lib) {
        FreeLibrary(ctx->gl_lib);
        ctx->gl_lib = NULL;
    }
}

#else /* Linux: EGL */

static bool load_egl_functions(hw_gl_context_t *ctx) {
    ctx->egl_lib = dlopen("libEGL.so.1", RTLD_LAZY);
    if (!ctx->egl_lib) {
        ctx->egl_lib = dlopen("libEGL.so", RTLD_LAZY);
    }
    if (!ctx->egl_lib) {
        GL_LOGE("Failed to load libEGL: %s", dlerror());
        return false;
    }

#define LOAD_EGL(fn) do { \
    ctx->pfn_##fn = (PFN_##fn)dlsym(ctx->egl_lib, #fn); \
    if (!ctx->pfn_##fn) { GL_LOGE("Failed to load " #fn); return false; } \
} while(0)

    LOAD_EGL(eglGetDisplay);
    LOAD_EGL(eglInitialize);
    LOAD_EGL(eglTerminate);
    LOAD_EGL(eglChooseConfig);
    LOAD_EGL(eglCreateContext);
    LOAD_EGL(eglDestroyContext);
    LOAD_EGL(eglCreatePbufferSurface);
    LOAD_EGL(eglDestroySurface);
    LOAD_EGL(eglMakeCurrent);
    LOAD_EGL(eglBindAPI);
    LOAD_EGL(eglGetProcAddress);

#undef LOAD_EGL
    return true;
}

static bool create_gl_context_egl(hw_gl_context_t *ctx, unsigned vmaj, unsigned vmin) {
    if (!load_egl_functions(ctx)) return false;

    ctx->gl_lib = dlopen("libGL.so.1", RTLD_LAZY);
    if (!ctx->gl_lib) {
        ctx->gl_lib = dlopen("libGL.so", RTLD_LAZY);
    }
    if (!ctx->gl_lib) {
        GL_LOGE("Failed to load libGL: %s", dlerror());
        return false;
    }

    ctx->egl_display = ctx->pfn_eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (ctx->egl_display == EGL_NO_DISPLAY) {
        GL_LOGE("eglGetDisplay failed");
        return false;
    }

    EGLint major, minor;
    if (!ctx->pfn_eglInitialize(ctx->egl_display, &major, &minor)) {
        GL_LOGE("eglInitialize failed");
        return false;
    }
    GL_LOGI("EGL %d.%d initialized", major, minor);

    ctx->pfn_eglBindAPI(EGL_OPENGL_API);

    EGLint config_attribs[] = {
        EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, 24,
        EGL_STENCIL_SIZE, 8,
        EGL_NONE,
    };

    EGLConfig config;
    EGLint num_configs;
    if (!ctx->pfn_eglChooseConfig(ctx->egl_display, config_attribs, &config, 1, &num_configs) ||
        num_configs == 0) {
        GL_LOGE("eglChooseConfig failed");
        return false;
    }

    EGLint context_attribs[] = {
        EGL_CONTEXT_MAJOR_VERSION, (EGLint)vmaj,
        EGL_CONTEXT_MINOR_VERSION, (EGLint)vmin,
        EGL_CONTEXT_OPENGL_PROFILE_MASK, EGL_CONTEXT_OPENGL_CORE_PROFILE_BIT,
        EGL_NONE,
    };

    ctx->egl_context = ctx->pfn_eglCreateContext(
        ctx->egl_display, config, EGL_NO_CONTEXT, context_attribs);
    if (ctx->egl_context == EGL_NO_CONTEXT) {
        GL_LOGE("eglCreateContext failed");
        return false;
    }

    EGLint pbuf_attribs[] = {
        EGL_WIDTH, 1,
        EGL_HEIGHT, 1,
        EGL_NONE,
    };
    ctx->egl_surface = ctx->pfn_eglCreatePbufferSurface(ctx->egl_display, config, pbuf_attribs);
    if (ctx->egl_surface == EGL_NO_SURFACE) {
        GL_LOGE("eglCreatePbufferSurface failed");
        return false;
    }

    ctx->pfn_eglMakeCurrent(ctx->egl_display, ctx->egl_surface, ctx->egl_surface, ctx->egl_context);
    GL_LOGI("EGL context created (GL %u.%u)", vmaj, vmin);
    return true;
}

static void destroy_gl_context_egl(hw_gl_context_t *ctx) {
    if (ctx->egl_display != EGL_NO_DISPLAY) {
        ctx->pfn_eglMakeCurrent(ctx->egl_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (ctx->egl_surface != EGL_NO_SURFACE) {
            ctx->pfn_eglDestroySurface(ctx->egl_display, ctx->egl_surface);
            ctx->egl_surface = EGL_NO_SURFACE;
        }
        if (ctx->egl_context != EGL_NO_CONTEXT) {
            ctx->pfn_eglDestroyContext(ctx->egl_display, ctx->egl_context);
            ctx->egl_context = EGL_NO_CONTEXT;
        }
        ctx->pfn_eglTerminate(ctx->egl_display);
        ctx->egl_display = EGL_NO_DISPLAY;
    }
    if (ctx->gl_lib) {
        dlclose(ctx->gl_lib);
        ctx->gl_lib = NULL;
    }
    if (ctx->egl_lib) {
        dlclose(ctx->egl_lib);
        ctx->egl_lib = NULL;
    }
}

#endif /* platform */

/* ===== FBO management ===== */

static bool create_fbo(hw_gl_context_t *ctx, unsigned width, unsigned height) {
    /* Create color texture */
    ctx->pfn_glGenTextures(1, &ctx->color_tex);
    ctx->pfn_glBindTexture(GL_TEXTURE_2D, ctx->color_tex);
    ctx->pfn_glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    ctx->pfn_glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    ctx->pfn_glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0,
                          GL_RGBA, GL_UNSIGNED_BYTE, NULL);
    ctx->pfn_glBindTexture(GL_TEXTURE_2D, 0);

    /* Create FBO */
    ctx->pfn_glGenFramebuffers(1, &ctx->fbo);
    ctx->pfn_glBindFramebuffer(GL_FRAMEBUFFER, ctx->fbo);

    /* Attach color texture */
    ctx->pfn_glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                                     GL_TEXTURE_2D, ctx->color_tex, 0);

    /* Depth + stencil as combined renderbuffer if both requested */
    if (ctx->has_depth && ctx->has_stencil) {
        ctx->pfn_glGenRenderbuffers(1, &ctx->depth_rb);
        ctx->pfn_glBindRenderbuffer(GL_RENDERBUFFER, ctx->depth_rb);
        ctx->pfn_glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, width, height);
        ctx->pfn_glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT,
                                           GL_RENDERBUFFER, ctx->depth_rb);
    } else if (ctx->has_depth) {
        ctx->pfn_glGenRenderbuffers(1, &ctx->depth_rb);
        ctx->pfn_glBindRenderbuffer(GL_RENDERBUFFER, ctx->depth_rb);
        ctx->pfn_glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, width, height);
        ctx->pfn_glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT,
                                           GL_RENDERBUFFER, ctx->depth_rb);
    }

    if (ctx->has_stencil && !ctx->has_depth) {
        ctx->pfn_glGenRenderbuffers(1, &ctx->stencil_rb);
        ctx->pfn_glBindRenderbuffer(GL_RENDERBUFFER, ctx->stencil_rb);
        ctx->pfn_glRenderbufferStorage(GL_RENDERBUFFER, GL_STENCIL_INDEX8, width, height);
        ctx->pfn_glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_STENCIL_ATTACHMENT,
                                           GL_RENDERBUFFER, ctx->stencil_rb);
    }

    GLenum status = ctx->pfn_glCheckFramebufferStatus(GL_FRAMEBUFFER);
    if (status != GL_FRAMEBUFFER_COMPLETE) {
        GL_LOGE("FBO incomplete: 0x%x", status);
        ctx->pfn_glBindFramebuffer(GL_FRAMEBUFFER, 0);
        return false;
    }

    ctx->fbo_width = width;
    ctx->fbo_height = height;

    /* Bind FBO as default target */
    ctx->pfn_glViewport(0, 0, width, height);

    GL_LOGI("FBO created: %ux%u (depth=%d, stencil=%d)", width, height,
            ctx->has_depth, ctx->has_stencil);
    return true;
}

static void destroy_fbo(hw_gl_context_t *ctx) {
    if (ctx->stencil_rb) {
        ctx->pfn_glDeleteRenderbuffers(1, &ctx->stencil_rb);
        ctx->stencil_rb = 0;
    }
    if (ctx->depth_rb) {
        ctx->pfn_glDeleteRenderbuffers(1, &ctx->depth_rb);
        ctx->depth_rb = 0;
    }
    if (ctx->color_tex) {
        ctx->pfn_glDeleteTextures(1, &ctx->color_tex);
        ctx->color_tex = 0;
    }
    if (ctx->fbo) {
        ctx->pfn_glBindFramebuffer(GL_FRAMEBUFFER, 0);
        ctx->pfn_glDeleteFramebuffers(1, &ctx->fbo);
        ctx->fbo = 0;
    }
    ctx->fbo_width = 0;
    ctx->fbo_height = 0;
}

/* ===== Public API ===== */

hw_gl_context_t *hw_gl_create(void) {
    hw_gl_context_t *ctx = (hw_gl_context_t *)calloc(1, sizeof(hw_gl_context_t));
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
        version_minor = 2;
    }

#ifdef _WIN32
    if (!create_gl_context_win32(ctx, version_major, version_minor)) return false;
#else
    if (!create_gl_context_egl(ctx, version_major, version_minor)) return false;
#endif

    if (!load_gl_functions(ctx)) return false;

    /* Create initial FBO at a default size (will be resized by hw_gl_resize_fbo) */
    if (!create_fbo(ctx, 256, 224)) return false;

    ctx->initialized = true;
    GL_LOGI("HW GL context initialized (GL %u.%u)", version_major, version_minor);
    return true;
}

void hw_gl_deinit(hw_gl_context_t *ctx) {
    if (!ctx || !ctx->initialized) return;

    hw_gl_make_current(ctx);
    destroy_fbo(ctx);

#ifdef _WIN32
    destroy_gl_context_win32(ctx);
#else
    destroy_gl_context_egl(ctx);
#endif

    ctx->initialized = false;
    GL_LOGI("HW GL context deinitialized");
}

void hw_gl_make_current(hw_gl_context_t *ctx) {
    if (!ctx) return;
#ifdef _WIN32
    if (ctx->hdc && ctx->hglrc) {
        ctx->pfn_wglMakeCurrent(ctx->hdc, ctx->hglrc);
    }
#else
    if (ctx->egl_display != EGL_NO_DISPLAY && ctx->egl_context != EGL_NO_CONTEXT) {
        ctx->pfn_eglMakeCurrent(ctx->egl_display, ctx->egl_surface,
                                 ctx->egl_surface, ctx->egl_context);
    }
#endif
}

void hw_gl_release_current(hw_gl_context_t *ctx) {
    if (!ctx) return;
#ifdef _WIN32
    if (ctx->hdc) {
        ctx->pfn_wglMakeCurrent(ctx->hdc, NULL);
    }
#else
    if (ctx->egl_display != EGL_NO_DISPLAY) {
        ctx->pfn_eglMakeCurrent(ctx->egl_display, EGL_NO_SURFACE,
                                 EGL_NO_SURFACE, EGL_NO_CONTEXT);
    }
#endif
}

void hw_gl_resize_fbo(hw_gl_context_t *ctx, unsigned width, unsigned height) {
    if (!ctx || !ctx->initialized) return;
    if (width == ctx->fbo_width && height == ctx->fbo_height) return;

    hw_gl_make_current(ctx);
    destroy_fbo(ctx);
    create_fbo(ctx, width, height);
}

uintptr_t hw_gl_get_framebuffer(hw_gl_context_t *ctx) {
    if (!ctx) return 0;
    return (uintptr_t)ctx->fbo;
}

void *hw_gl_get_proc_address(const char *sym) {
    /* This is a static function used by the bridge's get_proc_address callback.
     * It resolves GL function addresses for the core. On desktop, we use
     * the platform's standard GL function resolution. */
#ifdef _WIN32
    /* On Windows: wglGetProcAddress for extensions, GetProcAddress for core */
    HMODULE gl = GetModuleHandleA("opengl32.dll");
    void *proc = NULL;
    /* wglGetProcAddress requires a current context */
    proc = (void *)wglGetProcAddress(sym);
    if (!proc && gl) {
        proc = (void *)GetProcAddress(gl, sym);
    }
    return proc;
#else
    /* On Linux: dlsym from libGL */
    void *proc = NULL;
    void *gl = dlopen("libGL.so.1", RTLD_LAZY | RTLD_NOLOAD);
    if (!gl) gl = dlopen("libGL.so", RTLD_LAZY | RTLD_NOLOAD);
    if (gl) {
        proc = dlsym(gl, sym);
        dlclose(gl);
    }
    if (!proc) {
        /* Try EGL */
        void *egl = dlopen("libEGL.so.1", RTLD_LAZY | RTLD_NOLOAD);
        if (!egl) egl = dlopen("libEGL.so", RTLD_LAZY | RTLD_NOLOAD);
        if (egl) {
            PFN_eglGetProcAddress eglGPA = (PFN_eglGetProcAddress)dlsym(egl, "eglGetProcAddress");
            if (eglGPA) proc = eglGPA(sym);
            dlclose(egl);
        }
    }
    return proc;
#endif
}

unsigned hw_gl_read_pixels(hw_gl_context_t *ctx, void *out_data, size_t out_capacity,
                           unsigned *out_width, unsigned *out_height) {
    if (!ctx || !ctx->initialized || !out_data) return 0;

    unsigned w = ctx->fbo_width;
    unsigned h = ctx->fbo_height;
    size_t needed = (size_t)w * h * 4;

    if (out_capacity < needed) {
        GL_LOGE("Buffer too small: need %zu, have %zu", needed, out_capacity);
        return 0;
    }

    hw_gl_make_current(ctx);
    ctx->pfn_glBindFramebuffer(GL_READ_FRAMEBUFFER, ctx->fbo);
    ctx->pfn_glFinish();

    /* Read BGRA pixels (XRGB8888 compatible) */
    ctx->pfn_glReadPixels(0, 0, w, h, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, out_data);

    /* Do NOT flip vertically here. The frame is presented through
     * gpu_renderer_render_to_bgra, whose vertex shader applies
     * flip_y = hw_bottom_left_origin to convert the GL bottom-left origin
     * to the screen top-left origin. Flipping here as well double-flips,
     * producing an upside-down image (#1254). This matches the Android
     * readback path (hw_render_gl_android.c). */

    if (out_width) *out_width = w;
    if (out_height) *out_height = h;

    return w * h;
}

void hw_gl_debug_reset_frame(void) {
    /* No-op on desktop - debug counters not needed */
}

void hw_gl_rebind_gl_symbols(void) {
    /* No-op on desktop.
     * On macOS, this rebinds dyld lazy symbol pointers for OpenGL.framework.
     * On Linux/Windows, cores resolve GL functions through get_proc_address
     * which returns our addresses directly, so no rebinding is needed. */
}

#endif /* !__ANDROID__ && !__APPLE__ */
