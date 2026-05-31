/*
 * spela_core_host — out-of-process libretro core host (desktop).
 *
 * A minimal native frontend that loads a libretro core via the bridge's
 * sp_host_* API and runs it in its OWN process, sharing the framebuffer +
 * audio + input with the Spela JVM through a memory-mapped file. This gives
 * the core a clean native address space (like RetroArch) and avoids the
 * in-process-JVM corruption that crashes cores such as Azahar/3DS. See #1243.
 *
 * Args:
 *   --core <path> --game <path> --system <dir> --save <dir> --shm <file>
 *   [--width N --height N] [--var key=value ...]
 *
 * Phase 1: focuses on Vulkan HW-render cores (Azahar). Software-rendered
 * output is passed through raw with its pixel format in the header.
 */
#include "sp_platform.h"   /* windows.h on Win32; sp_sleep_ms */
#include "spela_host_api.h"
#include "spela_host_ipc.h"
#include "libretro.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>

#ifdef _WIN32
#  include <windows.h>
#else
#  include <fcntl.h>
#  include <sys/mman.h>
#  include <unistd.h>
#endif

#define HOSTLOG(...) do { fprintf(stderr, "[CoreHost] " __VA_ARGS__); fprintf(stderr, "\n"); fflush(stderr); } while (0)

/* ---- shared-memory mapping ------------------------------------------- */

static uint8_t *g_shm = NULL;
#ifdef _WIN32
static HANDLE g_hFile = INVALID_HANDLE_VALUE;
static HANDLE g_hMap = NULL;
#else
static int g_fd = -1;
#endif

static uint8_t *map_shared(const char *path) {
#ifdef _WIN32
    g_hFile = CreateFileA(path, GENERIC_READ | GENERIC_WRITE,
                          FILE_SHARE_READ | FILE_SHARE_WRITE, NULL,
                          OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
    if (g_hFile == INVALID_HANDLE_VALUE) { HOSTLOG("CreateFile failed: %lu", GetLastError()); return NULL; }
    g_hMap = CreateFileMappingA(g_hFile, NULL, PAGE_READWRITE, 0, SP_TOTAL_SIZE, NULL);
    if (!g_hMap) { HOSTLOG("CreateFileMapping failed: %lu", GetLastError()); return NULL; }
    void *base = MapViewOfFile(g_hMap, FILE_MAP_ALL_ACCESS, 0, 0, SP_TOTAL_SIZE);
    if (!base) { HOSTLOG("MapViewOfFile failed: %lu", GetLastError()); return NULL; }
    return (uint8_t *)base;
#else
    g_fd = open(path, O_RDWR);
    if (g_fd < 0) { HOSTLOG("open shm failed"); return NULL; }
    void *base = mmap(NULL, SP_TOTAL_SIZE, PROT_READ | PROT_WRITE, MAP_SHARED, g_fd, 0);
    if (base == MAP_FAILED) { HOSTLOG("mmap failed"); return NULL; }
    return (uint8_t *)base;
#endif
}

static void mem_barrier(void) {
#ifdef _WIN32
    MemoryBarrier();
#else
    __sync_synchronize();
#endif
}

/* ---- input ----------------------------------------------------------- */

static void apply_input(SpHostHeader *h) {
    uint32_t buttons = h->input_buttons;
    for (unsigned id = 0; id <= RETRO_DEVICE_ID_JOYPAD_R3; id++) {
        sp_host_set_button(0, id, (buttons >> id) & 1u);
    }
    sp_host_set_analog(0, RETRO_DEVICE_INDEX_ANALOG_LEFT,  RETRO_DEVICE_ID_ANALOG_X, h->analog_lx);
    sp_host_set_analog(0, RETRO_DEVICE_INDEX_ANALOG_LEFT,  RETRO_DEVICE_ID_ANALOG_Y, h->analog_ly);
    sp_host_set_analog(0, RETRO_DEVICE_INDEX_ANALOG_RIGHT, RETRO_DEVICE_ID_ANALOG_X, h->analog_rx);
    sp_host_set_analog(0, RETRO_DEVICE_INDEX_ANALOG_RIGHT, RETRO_DEVICE_ID_ANALOG_Y, h->analog_ry);
    sp_host_set_pointer(0, (int16_t)h->pointer_x, (int16_t)h->pointer_y, h->pointer_pressed != 0);
}

/* ---- main ------------------------------------------------------------ */

int main(int argc, char **argv) {
    const char *core = NULL, *game = NULL, *system_dir = NULL, *save_dir = NULL, *shm = NULL;
    int off_w = 256, off_h = 224;
    const char *vars[64]; int nvars = 0;

    for (int i = 1; i < argc; i++) {
        if (!strcmp(argv[i], "--core")   && i + 1 < argc) core = argv[++i];
        else if (!strcmp(argv[i], "--game")   && i + 1 < argc) game = argv[++i];
        else if (!strcmp(argv[i], "--system") && i + 1 < argc) system_dir = argv[++i];
        else if (!strcmp(argv[i], "--save")   && i + 1 < argc) save_dir = argv[++i];
        else if (!strcmp(argv[i], "--shm")    && i + 1 < argc) shm = argv[++i];
        else if (!strcmp(argv[i], "--width")  && i + 1 < argc) off_w = atoi(argv[++i]);
        else if (!strcmp(argv[i], "--height") && i + 1 < argc) off_h = atoi(argv[++i]);
        else if (!strcmp(argv[i], "--var")    && i + 1 < argc && nvars < 64) vars[nvars++] = argv[++i];
    }
    if (!core || !game || !shm) {
        HOSTLOG("usage: --core P --game P --shm FILE [--system D --save D --width N --height N --var k=v]");
        return 2;
    }

    g_shm = map_shared(shm);
    if (!g_shm) return 3;
    SpHostHeader *h = (SpHostHeader *)g_shm;
    memset(h, 0, SP_HDR_SIZE);
    h->status = SP_STATUS_STARTING;
    h->abi = SP_IPC_ABI;
    uint8_t *video_buf = g_shm + SP_VIDEO_OFFSET;
    int16_t *audio_buf = (int16_t *)(g_shm + SP_AUDIO_OFFSET);
    const size_t audio_cap_frames = SP_AUDIO_CAP_BYTES / (2 * sizeof(int16_t));

    sp_host_set_dirs(system_dir, save_dir);
    if (!sp_host_load_core(core)) { HOSTLOG("load_core failed: %s", core); h->status = SP_STATUS_ERROR; mem_barrier(); h->magic = SP_IPC_MAGIC; return 4; }
    sp_host_init();
    for (int i = 0; i < nvars; i++) {
        char tmp[256]; strncpy(tmp, vars[i], sizeof(tmp) - 1); tmp[sizeof(tmp) - 1] = 0;
        char *eq = strchr(tmp, '=');
        if (eq) { *eq = 0; sp_host_set_core_variable(tmp, eq + 1); }
    }
    /* Create the offscreen GPU renderer BEFORE load_game (matches desktop in-process
     * order); HW Vulkan init happens inside load_game once the core declares it. */
    sp_host_gpu_init_offscreen(off_w, off_h);
    if (!sp_host_load_game(game)) { HOSTLOG("load_game failed: %s", game); h->status = SP_STATUS_ERROR; mem_barrier(); h->magic = SP_IPC_MAGIC; return 5; }

    double fps = sp_host_target_fps(); if (fps <= 0) fps = 60.0;
    h->target_fps = fps;
    h->sample_rate = sp_host_sample_rate();
    h->aspect_ratio = sp_host_aspect_ratio();
    int vulkan = sp_host_is_vulkan_hw_render();
    HOSTLOG("running: fps=%.2f vulkan_hw=%d offscreen=%dx%d", fps, vulkan, off_w, off_h);

    h->status = SP_STATUS_RUNNING;
    mem_barrier();
    h->magic = SP_IPC_MAGIC;   /* signal JVM we're up */

#ifdef _WIN32
    LARGE_INTEGER freq; QueryPerformanceFrequency(&freq);
    LARGE_INTEGER next; QueryPerformanceCounter(&next);
    const double period = (double)freq.QuadPart / fps;
#endif

    while (!h->should_stop) {
        apply_input(h);
        if (!h->paused) sp_host_run_frame();

        /* video */
        unsigned w = 0, hh = 0;
        if (vulkan) {
            size_t bytes = sp_host_render_to_bgra(video_buf, SP_VIDEO_CAP, &w, &hh);
            if (bytes > 0) {
                h->video_width = w; h->video_height = hh;
                h->video_format = 0; /* BGRA8888 */
                h->video_bytes = (uint32_t)bytes;
            }
        } else {
            /* software path (not the Phase-1 focus): pass through raw */
            const void *fb = sp_host_video_buffer();
            size_t sz = sp_host_video_buffer_size();
            if (fb && sz > 0 && sz <= SP_VIDEO_CAP) {
                memcpy(video_buf, fb, sz);
                h->video_width = sp_host_video_width();
                h->video_height = sp_host_video_height();
                h->video_format = sp_host_pixel_format();
                h->video_bytes = (uint32_t)sz;
            }
        }

        /* audio */
        size_t frames = sp_host_get_audio(audio_buf, audio_cap_frames);
        h->audio_frames = (uint32_t)frames;

        mem_barrier();
        h->frame_counter++;   /* publish: JVM reads when this changes */

        /* pacing */
#ifdef _WIN32
        next.QuadPart += (LONGLONG)period;
        LARGE_INTEGER now; QueryPerformanceCounter(&now);
        double remain_ms = (double)(next.QuadPart - now.QuadPart) * 1000.0 / (double)freq.QuadPart;
        if (remain_ms > 1.0) sp_sleep_ms((int)remain_ms);
        else if (remain_ms < -100.0) next = now; /* fell far behind; resync */
#else
        sp_sleep_ms((int)(1000.0 / fps));
#endif
    }

    HOSTLOG("stopping");
    sp_host_unload();
    sp_host_deinit();
    h->status = SP_STATUS_EXITED;
    mem_barrier();
    return 0;
}
