/*
 * Spela libretro bridge - internal header.
 * Manages loading and running libretro cores from Kotlin via JNI.
 */

#ifndef LIBRETRO_BRIDGE_H
#define LIBRETRO_BRIDGE_H

#include "libretro.h"
#include <stdbool.h>

/* Forward declarations for subsystem modules */
struct gpu_renderer;
typedef struct gpu_renderer gpu_renderer_t;
struct hw_gl_context;
typedef struct hw_gl_context hw_gl_context_t;

/* Video subsystem (libretro_video.c) */
void video_init(void);
void video_deinit(void);
void video_set_pixel_format(unsigned format);
void video_refresh_callback(const void *data, unsigned width, unsigned height, size_t pitch);
unsigned video_get_width(void);
unsigned video_get_height(void);
const void *video_get_frame_buffer(void);
size_t video_get_frame_buffer_size(void);
unsigned video_get_pixel_format(void);
void video_set_gpu_renderer(gpu_renderer_t *renderer);
gpu_renderer_t *video_get_gpu_renderer(void);

/* Audio subsystem (libretro_audio.c) */
void audio_init(double sample_rate);
void audio_deinit(void);
void audio_sample_callback(int16_t left, int16_t right);
size_t audio_sample_batch_callback(const int16_t *data, size_t frames);
const int16_t *audio_get_buffer(void);
size_t audio_get_buffer_frames(void);
void audio_clear_buffer(void);

/* Input subsystem (libretro_input.c) */
void input_init(void);
void input_deinit(void);
void input_poll_callback(void);
int16_t input_state_callback(unsigned port, unsigned device, unsigned index, unsigned id);
void input_set_button(unsigned port, unsigned id, bool pressed);
void input_set_analog(unsigned port, unsigned index, unsigned id, int16_t value);
void input_set_pointer(unsigned port, int16_t x, int16_t y, bool pressed);

/* Core variable subsystem (libretro_bridge.c) */
void core_variables_set(const char *key, const char *value);
void core_variables_clear(void);

/* Core state */
typedef struct {
    void *handle;                           /* dlopen handle */

    /* Core API functions */
    retro_init_t                    retro_init;
    retro_deinit_t                  retro_deinit;
    retro_api_version_t             retro_api_version;
    retro_get_system_info_t         retro_get_system_info;
    retro_get_system_av_info_t      retro_get_system_av_info;
    retro_set_environment_t         retro_set_environment;
    retro_set_video_refresh_t       retro_set_video_refresh;
    retro_set_audio_sample_t        retro_set_audio_sample;
    retro_set_audio_sample_batch_t  retro_set_audio_sample_batch;
    retro_set_input_poll_t          retro_set_input_poll;
    retro_set_input_state_t         retro_set_input_state;
    retro_set_controller_port_device_t retro_set_controller_port_device;
    retro_reset_t                   retro_reset;
    retro_run_t                     retro_run;
    retro_load_game_t               retro_load_game;
    retro_unload_game_t             retro_unload_game;
    retro_serialize_size_t          retro_serialize_size;
    retro_serialize_t               retro_serialize;
    retro_unserialize_t             retro_unserialize;
    retro_get_memory_data_t         retro_get_memory_data;
    retro_get_memory_size_t         retro_get_memory_size;

    /* AV info */
    struct retro_system_av_info     av_info;
    struct retro_system_info        system_info;

    /* State */
    bool                            initialized;
    bool                            game_loaded;
    char                            system_dir[4096];
    char                            save_dir[4096];

    /* HW render */
    struct retro_hw_render_callback hw_render_callback;
    bool                            hw_render_enabled;

    /* Vulkan context negotiation (set by core via env cmd 43) */
    const struct retro_hw_render_context_negotiation_interface_vulkan *hw_vk_negotiation;

    /* OpenGL HW render context (NULL when not using GL HW render) */
    hw_gl_context_t                *hw_gl_ctx;
} libretro_core_t;

/* Global core instance */
extern libretro_core_t g_core;

#endif /* LIBRETRO_BRIDGE_H */
