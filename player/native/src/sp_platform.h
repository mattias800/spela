/*
 * Cross-platform wrappers for dynamic library loading, mutexes, and temp dirs.
 * POSIX (Linux/macOS/Android) vs Win32.
 *
 * All functions are static inline to avoid ODR issues when included
 * from multiple translation units.
 */

#ifndef SP_PLATFORM_H
#define SP_PLATFORM_H

#ifdef _WIN32

/* ── Windows ─────────────────────────────────────────────────────── */

#define WIN32_LEAN_AND_MEAN
#include <windows.h>

/* Dynamic library loading */
typedef HMODULE sp_lib_t;

static inline sp_lib_t sp_dlopen(const char *path, int flags) {
    (void)flags;
    return LoadLibraryA(path);
}

static inline void *sp_dlsym(sp_lib_t lib, const char *sym) {
    return (void *)GetProcAddress(lib, sym);
}

static inline int sp_dlclose(sp_lib_t lib) {
    return FreeLibrary(lib) ? 0 : -1;
}

static inline const char *sp_dlerror(void) {
    static char buf[256];
    DWORD err = GetLastError();
    if (err == 0) return "(no error)";
    FormatMessageA(FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
                   NULL, err, 0, buf, sizeof(buf), NULL);
    return buf;
}

/* RTLD_* constants — unused on Windows but needed for source compat */
#ifndef RTLD_LAZY
#define RTLD_LAZY   0
#endif
#ifndef RTLD_NOW
#define RTLD_NOW    0
#endif
#ifndef RTLD_GLOBAL
#define RTLD_GLOBAL 0
#endif

/* Mutex */
typedef CRITICAL_SECTION sp_mutex_t;

static inline int sp_mutex_init(sp_mutex_t *m) {
    InitializeCriticalSection(m);
    return 0;
}

static inline void sp_mutex_lock(sp_mutex_t *m) {
    EnterCriticalSection(m);
}

static inline void sp_mutex_unlock(sp_mutex_t *m) {
    LeaveCriticalSection(m);
}

static inline void sp_mutex_destroy(sp_mutex_t *m) {
    DeleteCriticalSection(m);
}

/* Sleep */
static inline void sp_sleep_ms(unsigned ms) {
    Sleep(ms);
}

/* Temp directory (includes trailing path separator) */
static inline const char *sp_get_temp_dir(void) {
    static char buf[MAX_PATH];
    if (buf[0] == '\0') {
        DWORD len = GetTempPathA(MAX_PATH, buf);
        /* GetTempPathA always includes trailing backslash, but guard anyway */
        if (len > 0 && buf[len - 1] != '\\') {
            buf[len] = '\\';
            buf[len + 1] = '\0';
        }
    }
    return buf;
}

#else

/* ── POSIX (Linux / macOS / Android) ─────────────────────────────── */

#include <dlfcn.h>
#include <pthread.h>
#include <unistd.h>

/* Dynamic library loading */
typedef void *sp_lib_t;

static inline sp_lib_t sp_dlopen(const char *path, int flags) {
    return dlopen(path, flags);
}

static inline void *sp_dlsym(sp_lib_t lib, const char *sym) {
    return dlsym(lib, sym);
}

static inline int sp_dlclose(sp_lib_t lib) {
    return dlclose(lib);
}

static inline const char *sp_dlerror(void) {
    return dlerror();
}

/* Mutex */
typedef pthread_mutex_t sp_mutex_t;

static inline int sp_mutex_init(sp_mutex_t *m) {
    return pthread_mutex_init(m, NULL);
}

static inline void sp_mutex_lock(sp_mutex_t *m) {
    pthread_mutex_lock(m);
}

static inline void sp_mutex_unlock(sp_mutex_t *m) {
    pthread_mutex_unlock(m);
}

static inline void sp_mutex_destroy(sp_mutex_t *m) {
    pthread_mutex_destroy(m);
}

/* Sleep */
static inline void sp_sleep_ms(unsigned ms) {
    usleep(ms * 1000u);
}

/* Temp directory (includes trailing slash) */
static inline const char *sp_get_temp_dir(void) {
    return "/tmp/";
}

#endif /* _WIN32 */

#endif /* SP_PLATFORM_H */
