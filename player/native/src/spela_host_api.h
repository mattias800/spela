/*
 * spela_host_api.h — native (non-JNI) entry points for the out-of-process
 * core host (desktop only).
 *
 * The desktop player normally drives the libretro bridge in-process via JNI
 * (LibretroJni). Some cores (e.g. Azahar/3DS) corrupt their own internal
 * state when hosted inside the JVM process and crash; running them in a
 * separate native process (like RetroArch does) avoids this. See #1243.
 *
 * These functions mirror the JNI surface but take plain C types instead of
 * JNIEnv/jobject, so a native `main()` (spela_core_host.c) can load and run a
 * core without a JVM. They are implemented at the end of libretro_bridge.c
 * and reuse the same internal core/video/audio/input/GPU machinery.
 */
#ifndef SPELA_HOST_API_H
#define SPELA_HOST_API_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/* Set system (BIOS) + save directories. Call before sp_host_load_core. */
void sp_host_set_dirs(const char *system_dir, const char *save_dir);

/* Load the core .dll/.so/.dylib. Returns true on success. */
bool sp_host_load_core(const char *core_path);

/* retro_init + video/input init (mirrors nativeInit). */
void sp_host_init(void);

/* retro_load_game. Returns true on success. */
bool sp_host_load_game(const char *game_path);

/* AV info (valid after sp_host_load_game). */
double sp_host_target_fps(void);
double sp_host_sample_rate(void);
float  sp_host_aspect_ratio(void);

/* HW-render queries. */
bool sp_host_is_hw_render(void);
bool sp_host_is_vulkan_hw_render(void);

/* Offscreen Vulkan renderer for HW-render cores. Call after sp_host_load_game
 * when sp_host_is_vulkan_hw_render() is true. */
bool sp_host_gpu_init_offscreen(int width, int height);

/* Run a single frame (retro_run with HW-render gating). */
void sp_host_run_frame(void);

/* Software-rendered frame accessors (for cores not using Vulkan HW render). */
unsigned    sp_host_video_width(void);
unsigned    sp_host_video_height(void);
unsigned    sp_host_pixel_format(void);
const void *sp_host_video_buffer(void);
size_t      sp_host_video_buffer_size(void);

/* Vulkan HW-render readback into a BGRA8888 buffer.
 * Returns bytes written; sets *w,*h to the frame dimensions. */
size_t sp_host_render_to_bgra(void *dst, size_t dst_capacity, unsigned *w, unsigned *h);

/* Copy currently-buffered audio as interleaved stereo int16 frames into dst.
 * dst_frames = capacity in frames (each frame = 2 int16). Returns frames copied. */
size_t sp_host_get_audio(int16_t *dst, size_t dst_frames);

/* Input (port/device per libretro). */
void sp_host_set_button(unsigned port, unsigned id, bool pressed);
void sp_host_set_analog(unsigned port, unsigned index, unsigned id, int16_t value);
void sp_host_set_pointer(unsigned port, int16_t x, int16_t y, bool pressed);

/* Set a core option (key/value) before sp_host_load_game. */
void sp_host_set_core_variable(const char *key, const char *value);

/* Save/load a libretro save state to/from a filesystem path. These let a
 * downloaded session state be replayed against a core out-of-process — how
 * the poisoned BG&E state behind #1533 was reproduced byte-identically off
 * the device. sp_host_save_state returns the number of bytes written, or -1
 * on failure; sp_host_load_state returns true on success. Both require a
 * game to be loaded, and save additionally requires the core to have run at
 * least one frame (some cores' retro_serialize_size is 0 before that). */
long sp_host_save_state(const char *path);
bool sp_host_load_state(const char *path);

void sp_host_unload(void);   /* unload game */
void sp_host_deinit(void);   /* deinit core */

#endif /* SPELA_HOST_API_H */
