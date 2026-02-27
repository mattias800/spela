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
#define RETRO_ENVIRONMENT_SET_HW_RENDER_CONTEXT_NEGOTIATION_INTERFACE 43
#define RETRO_ENVIRONMENT_SET_HW_SHARED_CONTEXT 44
#define RETRO_ENVIRONMENT_GET_PREFERRED_HW_RENDER 56

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

/* Mouse device IDs */
#define RETRO_DEVICE_ID_MOUSE_X      0
#define RETRO_DEVICE_ID_MOUSE_Y      1
#define RETRO_DEVICE_ID_MOUSE_LEFT   2
#define RETRO_DEVICE_ID_MOUSE_RIGHT  3

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

/* HW render interface types */
#define RETRO_HW_RENDER_INTERFACE_VULKAN 0

/* Vulkan HW render interface structs.
 * Only available when <vulkan/vulkan.h> is included before this header. */
#ifdef VK_VERSION_1_0

struct retro_vulkan_image {
    VkImageView image_view;
    VkImageLayout image_layout;
    VkImageViewCreateInfo create_info;
};

typedef void (*retro_vulkan_set_image_t)(void *handle,
    const struct retro_vulkan_image *image,
    uint32_t num_semaphores, const VkSemaphore *semaphores,
    uint32_t src_queue_family);
typedef uint32_t (*retro_vulkan_get_sync_index_t)(void *handle);
typedef uint32_t (*retro_vulkan_get_sync_index_mask_t)(void *handle);
typedef void (*retro_vulkan_set_command_buffers_t)(void *handle,
    uint32_t num_cmd, const VkCommandBuffer *cmd);
typedef void (*retro_vulkan_wait_sync_index_t)(void *handle);
typedef void (*retro_vulkan_lock_queue_t)(void *handle);
typedef void (*retro_vulkan_unlock_queue_t)(void *handle);
typedef void (*retro_vulkan_set_signal_semaphore_t)(void *handle, VkSemaphore semaphore);

struct retro_hw_render_interface_vulkan {
    unsigned interface_type;
    unsigned interface_version;
    void *handle;
    VkInstance instance;
    VkPhysicalDevice gpu;
    VkDevice device;
    PFN_vkGetDeviceProcAddr get_device_proc_addr;
    PFN_vkGetInstanceProcAddr get_instance_proc_addr;
    VkQueue queue;
    unsigned queue_index;
    retro_vulkan_set_image_t set_image;
    retro_vulkan_get_sync_index_t get_sync_index;
    retro_vulkan_get_sync_index_mask_t get_sync_index_mask;
    retro_vulkan_set_command_buffers_t set_command_buffers;
    retro_vulkan_wait_sync_index_t wait_sync_index;
    retro_vulkan_lock_queue_t lock_queue;
    retro_vulkan_unlock_queue_t unlock_queue;
    retro_vulkan_set_signal_semaphore_t set_signal_semaphore;
};

/* Context negotiation interface type */
#define RETRO_HW_RENDER_CONTEXT_NEGOTIATION_INTERFACE_VULKAN 0

/* Vulkan context — filled by core's create_device callback */
struct retro_vulkan_context {
    VkPhysicalDevice gpu;
    VkDevice device;
    VkQueue queue;
    uint32_t queue_family_index;
    VkQueue presentation_queue;
    uint32_t presentation_queue_family_index;
};

/* Context negotiation: lets cores participate in VkDevice creation.
 * The core provides this via SET_HW_RENDER_CONTEXT_NEGOTIATION_INTERFACE.
 * Version 1: get_application_info + create_device (v1 signature).
 * Version 2+: adds create_instance + create_device2 (wrapper-based signature). */
typedef const VkApplicationInfo *(*retro_vulkan_get_application_info_t)(void);

/* v1 create_device — core creates device directly */
typedef bool (*retro_vulkan_create_device_t)(
    struct retro_vulkan_context *context,
    VkInstance instance,
    VkPhysicalDevice gpu,
    VkSurfaceKHR surface,
    PFN_vkGetInstanceProcAddr get_instance_proc_addr,
    const char **required_device_extensions,
    unsigned num_required_device_extensions,
    const char **required_device_layers,
    unsigned num_required_device_layers,
    const VkPhysicalDeviceFeatures *required_features);

typedef void (*retro_vulkan_destroy_device_t)(void);

/* v2 device wrapper — frontend callback that core calls to create the actual device.
 * Returns VkDevice (or VK_NULL_HANDLE on failure). */
typedef VkDevice (*retro_vulkan_create_device_wrapper_t)(
    VkPhysicalDevice gpu,
    void *opaque,
    const VkDeviceCreateInfo *create_info);

/* v2 instance wrapper — frontend callback that core calls to create VkInstance.
 * Returns VkInstance (or VK_NULL_HANDLE on failure). */
typedef VkInstance (*retro_vulkan_create_instance_wrapper_t)(
    void *opaque,
    const VkInstanceCreateInfo *create_info);

/* v2 create_device2 — core prepares create_info, calls wrapper to create device */
typedef bool (*retro_vulkan_create_device2_t)(
    struct retro_vulkan_context *context,
    VkInstance instance,
    VkPhysicalDevice gpu,
    VkSurfaceKHR surface,
    PFN_vkGetInstanceProcAddr get_instance_proc_addr,
    retro_vulkan_create_device_wrapper_t create_device_wrapper,
    void *opaque);

/* v2 create_instance — core creates VkInstance (optional) */
typedef VkInstance (*retro_vulkan_create_instance_t)(
    PFN_vkGetInstanceProcAddr get_instance_proc_addr,
    const VkApplicationInfo *app,
    retro_vulkan_create_instance_wrapper_t create_instance_wrapper,
    void *opaque);

struct retro_hw_render_context_negotiation_interface_vulkan {
    unsigned interface_type;
    unsigned interface_version;
    retro_vulkan_get_application_info_t get_application_info;
    retro_vulkan_create_device_t create_device;   /* v1 */
    retro_vulkan_destroy_device_t destroy_device;
    /* v2 additions (only valid when interface_version >= 2) */
    retro_vulkan_create_instance_t create_instance;
    retro_vulkan_create_device2_t create_device2;
};

#endif /* VK_VERSION_1_0 */

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
