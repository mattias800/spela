/*
 * Minimal libretro API header.
 * Based on the libretro specification (https://docs.libretro.com/).
 * This defines the types and function pointer signatures needed
 * to load and interact with a libretro core dynamically.
 */

#ifndef LIBRETRO_H
#define LIBRETRO_H

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Pixel formats */
#define RETRO_PIXEL_FORMAT_0RGB1555  0
#define RETRO_PIXEL_FORMAT_XRGB8888  1
#define RETRO_PIXEL_FORMAT_RGB565    2

/* Environment commands */
#define RETRO_ENVIRONMENT_GET_CAN_DUPE        3
#define RETRO_ENVIRONMENT_SET_MESSAGE          6
#define RETRO_ENVIRONMENT_SET_PERFORMANCE_LEVEL 8
#define RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY 9
#define RETRO_ENVIRONMENT_SET_PIXEL_FORMAT     10
#define RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS 11
#define RETRO_ENVIRONMENT_SHUTDOWN             13
#define RETRO_ENVIRONMENT_SET_HW_RENDER       14
#define RETRO_ENVIRONMENT_GET_VARIABLE         15
#define RETRO_ENVIRONMENT_SET_VARIABLES        16
#define RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE  17
#define RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME  18
#define RETRO_ENVIRONMENT_GET_RUMBLE_INTERFACE 23
#define RETRO_ENVIRONMENT_SET_CONTROLLER_PORT_DEVICE_ENV 25
#define RETRO_ENVIRONMENT_GET_LOG_INTERFACE    27
#define RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY   31
#define RETRO_ENVIRONMENT_SET_GEOMETRY         37
#define RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION 52
#define RETRO_ENVIRONMENT_GET_HW_RENDER_INTERFACE 41

/* Input device types */
#define RETRO_DEVICE_NONE     0
#define RETRO_DEVICE_JOYPAD   1
#define RETRO_DEVICE_MOUSE    2
#define RETRO_DEVICE_KEYBOARD 3
#define RETRO_DEVICE_LIGHTGUN 4
#define RETRO_DEVICE_ANALOG   5
#define RETRO_DEVICE_POINTER  6

/* Joypad buttons */
#define RETRO_DEVICE_ID_JOYPAD_B      0
#define RETRO_DEVICE_ID_JOYPAD_Y      1
#define RETRO_DEVICE_ID_JOYPAD_SELECT 2
#define RETRO_DEVICE_ID_JOYPAD_START  3
#define RETRO_DEVICE_ID_JOYPAD_UP     4
#define RETRO_DEVICE_ID_JOYPAD_DOWN   5
#define RETRO_DEVICE_ID_JOYPAD_LEFT   6
#define RETRO_DEVICE_ID_JOYPAD_RIGHT  7
#define RETRO_DEVICE_ID_JOYPAD_A      8
#define RETRO_DEVICE_ID_JOYPAD_X      9
#define RETRO_DEVICE_ID_JOYPAD_L      10
#define RETRO_DEVICE_ID_JOYPAD_R      11
#define RETRO_DEVICE_ID_JOYPAD_L2     12
#define RETRO_DEVICE_ID_JOYPAD_R2     13
#define RETRO_DEVICE_ID_JOYPAD_L3     14
#define RETRO_DEVICE_ID_JOYPAD_R3     15

/* Analog indices */
#define RETRO_DEVICE_INDEX_ANALOG_LEFT  0
#define RETRO_DEVICE_INDEX_ANALOG_RIGHT 1
#define RETRO_DEVICE_ID_ANALOG_X 0
#define RETRO_DEVICE_ID_ANALOG_Y 1

/* Pointer device IDs */
#define RETRO_DEVICE_ID_POINTER_X       0
#define RETRO_DEVICE_ID_POINTER_Y       1
#define RETRO_DEVICE_ID_POINTER_PRESSED 2

/* HW render context types */
#define RETRO_HW_CONTEXT_NONE       0
#define RETRO_HW_CONTEXT_OPENGL     1
#define RETRO_HW_CONTEXT_OPENGLES2  2
#define RETRO_HW_CONTEXT_OPENGL_CORE 3
#define RETRO_HW_CONTEXT_OPENGLES3  4
#define RETRO_HW_CONTEXT_OPENGLES_VERSION 5
#define RETRO_HW_CONTEXT_VULKAN     6
#define RETRO_HW_CONTEXT_METAL      7

/* HW render callback typedefs */
typedef void (*retro_hw_context_reset_t)(void);
typedef uintptr_t (*retro_hw_get_current_framebuffer_t)(void);
typedef void *(*retro_hw_get_proc_address_t)(const char *sym);

/* HW render callback struct - passed via RETRO_ENVIRONMENT_SET_HW_RENDER */
struct retro_hw_render_callback {
    unsigned context_type;
    retro_hw_context_reset_t context_reset;
    retro_hw_get_current_framebuffer_t get_current_framebuffer;
    retro_hw_get_proc_address_t get_proc_address;
    bool depth;
    bool stencil;
    bool bottom_left_origin;
    unsigned version_major;
    unsigned version_minor;
    bool cache_context;
    retro_hw_context_reset_t context_destroy;
    bool debug_context;
};

/* Sentinel value passed as data to video_refresh when the core has rendered
 * to the hardware framebuffer (FBO) and the frontend should read it back. */
#define RETRO_HW_FRAME_BUFFER_VALID ((const void *)(intptr_t)-1)

/* Memory regions */
#define RETRO_MEMORY_SAVE_RAM  0
#define RETRO_MEMORY_RTC       1
#define RETRO_MEMORY_SYSTEM_RAM 2
#define RETRO_MEMORY_VIDEO_RAM  3

/* Log levels */
enum retro_log_level {
    RETRO_LOG_DEBUG = 0,
    RETRO_LOG_INFO,
    RETRO_LOG_WARN,
    RETRO_LOG_ERROR
};

struct retro_log_callback {
    void (*log)(enum retro_log_level level, const char *fmt, ...);
};

struct retro_game_info {
    const char *path;
    const void *data;
    size_t      size;
    const char *meta;
};

struct retro_system_info {
    const char *library_name;
    const char *library_version;
    const char *valid_extensions;
    bool        need_fullpath;
    bool        block_extract;
};

struct retro_system_av_info {
    struct {
        unsigned base_width;
        unsigned base_height;
        unsigned max_width;
        unsigned max_height;
        float    aspect_ratio;
    } geometry;
    struct {
        double fps;
        double sample_rate;
    } timing;
};

struct retro_variable {
    const char *key;
    const char *value;
};

struct retro_message {
    const char *msg;
    unsigned    frames;
};

struct retro_game_geometry {
    unsigned base_width;
    unsigned base_height;
    unsigned max_width;
    unsigned max_height;
    float    aspect_ratio;
};

/* Callback typedefs */
typedef bool (*retro_environment_t)(unsigned cmd, void *data);
typedef void (*retro_video_refresh_t)(const void *data, unsigned width, unsigned height, size_t pitch);
typedef void (*retro_audio_sample_t)(int16_t left, int16_t right);
typedef size_t (*retro_audio_sample_batch_t)(const int16_t *data, size_t frames);
typedef void (*retro_input_poll_t)(void);
typedef int16_t (*retro_input_state_t)(unsigned port, unsigned device, unsigned index, unsigned id);

/* Core API function pointer typedefs */
typedef void (*retro_set_environment_t)(retro_environment_t);
typedef void (*retro_set_video_refresh_t)(retro_video_refresh_t);
typedef void (*retro_set_audio_sample_t)(retro_audio_sample_t);
typedef void (*retro_set_audio_sample_batch_t)(retro_audio_sample_batch_t);
typedef void (*retro_set_input_poll_t)(retro_input_poll_t);
typedef void (*retro_set_input_state_t)(retro_input_state_t);
typedef void (*retro_init_t)(void);
typedef void (*retro_deinit_t)(void);
typedef unsigned (*retro_api_version_t)(void);
typedef void (*retro_get_system_info_t)(struct retro_system_info *info);
typedef void (*retro_get_system_av_info_t)(struct retro_system_av_info *info);
typedef void (*retro_set_controller_port_device_t)(unsigned port, unsigned device);
typedef void (*retro_reset_t)(void);
typedef void (*retro_run_t)(void);
typedef size_t (*retro_serialize_size_t)(void);
typedef bool (*retro_serialize_t)(void *data, size_t size);
typedef bool (*retro_unserialize_t)(const void *data, size_t size);
typedef bool (*retro_load_game_t)(const struct retro_game_info *game);
typedef void (*retro_unload_game_t)(void);
typedef void *(*retro_get_memory_data_t)(unsigned id);
typedef size_t (*retro_get_memory_size_t)(unsigned id);

#ifdef __cplusplus
}
#endif

#endif /* LIBRETRO_H */
