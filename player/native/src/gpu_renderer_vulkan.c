/*
 * Vulkan GPU renderer for Android.
 *
 * Implements the gpu_renderer interface using Vulkan 1.0.
 * Software-rendered cores upload frames via staging buffer.
 * Real SPIR-V fragment shaders replace CPU overlays.
 *
 * Threading model:
 *   - gpu_renderer_upload_frame() called from emulation thread
 *   - gpu_renderer_render() called from emulation thread (after upload)
 *   - All Vulkan commands are single-threaded (emulation thread owns the context)
 */

#include "gpu_renderer.h"
#include "gpu_shaders_spirv.h"

#ifdef __ANDROID__
#define VK_USE_PLATFORM_ANDROID_KHR
#endif

#include <vulkan/vulkan.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

#ifdef __ANDROID__
#include <android/log.h>
#include <android/native_window.h>
#define LOG_TAG "SpelaVulkan"
#define VK_LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define VK_LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define VK_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#define VK_LOGI(...) do { fprintf(stdout, "[Vulkan] "); fprintf(stdout, __VA_ARGS__); fprintf(stdout, "\n"); } while(0)
#define VK_LOGW(...) do { fprintf(stderr, "[Vulkan WARN] "); fprintf(stderr, __VA_ARGS__); fprintf(stderr, "\n"); } while(0)
#define VK_LOGE(...) do { fprintf(stderr, "[Vulkan ERROR] "); fprintf(stderr, __VA_ARGS__); fprintf(stderr, "\n"); } while(0)
#endif

#define VK_CHECK(call) do { \
    VkResult _res = (call); \
    if (_res != VK_SUCCESS) { \
        VK_LOGE("%s failed: %d (line %d)", #call, _res, __LINE__); \
        return false; \
    } \
} while(0)

#define VK_CHECK_VOID(call) do { \
    VkResult _res = (call); \
    if (_res != VK_SUCCESS) { \
        VK_LOGE("%s failed: %d (line %d)", #call, _res, __LINE__); \
        return; \
    } \
} while(0)

#define MAX_FRAMES_IN_FLIGHT 3
#define NUM_SHADERS 6

/* Push constant data passed to fragment shaders */
typedef struct {
    float texture_size[2];
} push_constants_t;

struct gpu_renderer {
    int backend;
    bool active;
    bool surface_initialized;

    /* Vulkan core objects */
    VkInstance instance;
    VkPhysicalDevice physical_device;
    VkDevice device;
    VkQueue graphics_queue;
    uint32_t queue_family_index;

    /* Surface and swapchain */
    VkSurfaceKHR surface;
    VkSwapchainKHR swapchain;
    VkFormat swapchain_format;
    VkExtent2D swapchain_extent;
    uint32_t swapchain_image_count;
    VkImage *swapchain_images;
    VkImageView *swapchain_image_views;
    VkFramebuffer *framebuffers;

    /* Render pass */
    VkRenderPass render_pass;

    /* Pipelines -- one per shader */
    VkPipelineLayout pipeline_layout;
    VkPipeline pipelines[NUM_SHADERS];
    int current_shader;

    /* Descriptor set for texture sampling */
    VkDescriptorSetLayout descriptor_set_layout;
    VkDescriptorPool descriptor_pool;
    VkDescriptorSet descriptor_set;

    /* Game texture (uploaded from CPU) */
    VkImage game_texture;
    VkDeviceMemory game_texture_memory;
    VkImageView game_texture_view;
    unsigned game_texture_width;
    unsigned game_texture_height;
    VkFormat game_texture_format;

    /* Staging buffer for frame upload */
    VkBuffer staging_buffer;
    VkDeviceMemory staging_memory;
    void *staging_mapped;
    VkDeviceSize staging_size;

    /* Samplers */
    VkSampler sampler_nearest;
    VkSampler sampler_linear;

    /* Synchronization */
    VkSemaphore image_available_semaphores[MAX_FRAMES_IN_FLIGHT];
    VkSemaphore render_finished_semaphores[MAX_FRAMES_IN_FLIGHT];
    VkFence in_flight_fences[MAX_FRAMES_IN_FLIGHT];
    uint32_t current_frame;

    /* Command pool and buffers */
    VkCommandPool command_pool;
    VkCommandBuffer command_buffers[MAX_FRAMES_IN_FLIGHT];

    /* Frame state */
    bool frame_uploaded;
    unsigned frame_width;
    unsigned frame_height;
    unsigned frame_pixel_format;

    /* Offscreen rendering */
    bool offscreen_mode;
    VkImage offscreen_image;
    VkDeviceMemory offscreen_image_memory;
    VkImageView offscreen_image_view;
    VkFramebuffer offscreen_framebuffer;
    VkRenderPass offscreen_render_pass;
    VkBuffer readback_buffer;
    VkDeviceMemory readback_memory;
    void *readback_mapped;
    VkDeviceSize readback_size;
    VkFence offscreen_fence;
    VkCommandBuffer offscreen_cmd;
    int offscreen_width;
    int offscreen_height;

    /* Source rect for DS dual-screen */
    int source_x, source_y, source_w, source_h;
    bool source_rect_set;

    /* Native window handle */
#ifdef __ANDROID__
    ANativeWindow *native_window;
#else
    void *native_window;
#endif
};

/* Forward declarations */
static bool create_instance(gpu_renderer_t *r);
static bool select_physical_device(gpu_renderer_t *r);
static bool create_device(gpu_renderer_t *r);
static bool create_swapchain(gpu_renderer_t *r);
static bool create_render_pass(gpu_renderer_t *r);
static bool create_descriptor_layout(gpu_renderer_t *r);
static bool create_pipeline_layout(gpu_renderer_t *r);
static bool create_pipelines(gpu_renderer_t *r);
static bool create_framebuffers(gpu_renderer_t *r);
static bool create_command_pool(gpu_renderer_t *r);
static bool create_command_buffers(gpu_renderer_t *r);
static bool create_sync_objects(gpu_renderer_t *r);
static bool create_samplers(gpu_renderer_t *r);
static bool create_descriptor_pool(gpu_renderer_t *r);
static bool create_game_texture(gpu_renderer_t *r, unsigned w, unsigned h, VkFormat fmt);
static bool update_descriptor_set(gpu_renderer_t *r);
static bool create_staging_buffer(gpu_renderer_t *r, VkDeviceSize size);
static void cleanup_swapchain(gpu_renderer_t *r);
static bool recreate_swapchain(gpu_renderer_t *r);
static uint32_t find_memory_type(gpu_renderer_t *r, uint32_t type_filter, VkMemoryPropertyFlags properties);
static VkShaderModule create_shader_module(gpu_renderer_t *r, const uint32_t *code, size_t size);
static bool transition_image_layout(gpu_renderer_t *r, VkImage image,
    VkImageLayout old_layout, VkImageLayout new_layout);
static bool copy_buffer_to_image(gpu_renderer_t *r, VkBuffer buffer,
    VkImage image, unsigned width, unsigned height);
static bool create_offscreen_target(gpu_renderer_t *r, int width, int height);
static bool create_offscreen_render_pass(gpu_renderer_t *r);
static bool create_readback_buffer(gpu_renderer_t *r, VkDeviceSize size);
static void cleanup_offscreen(gpu_renderer_t *r);

/* ===== Public API ===== */

gpu_renderer_t *gpu_renderer_create(int backend) {
    gpu_renderer_t *r = (gpu_renderer_t *)calloc(1, sizeof(gpu_renderer_t));
    if (!r) return NULL;
    r->backend = backend;
    r->current_shader = GPU_SHADER_NONE;
    return r;
}

void gpu_renderer_destroy(gpu_renderer_t *r) {
    if (!r) return;
    if (r->device) {
        vkDeviceWaitIdle(r->device);
    }
    gpu_renderer_deinit_surface(r);

    if (r->device) {
        vkDestroyDevice(r->device, NULL);
    }
    if (r->surface) {
        vkDestroySurfaceKHR(r->instance, r->surface, NULL);
    }
    if (r->instance) {
        vkDestroyInstance(r->instance, NULL);
    }
#ifdef __ANDROID__
    if (r->native_window) {
        ANativeWindow_release(r->native_window);
    }
#endif
    free(r);
}

bool gpu_renderer_init_surface(gpu_renderer_t *r, void *native_surface) {
    if (!r || r->surface_initialized) return false;

#ifdef __ANDROID__
    r->native_window = (ANativeWindow *)native_surface;
    ANativeWindow_acquire(r->native_window);
#else
    r->native_window = native_surface;
#endif

    if (!create_instance(r)) return false;

    /* Create surface */
#ifdef __ANDROID__
    VkAndroidSurfaceCreateInfoKHR surface_info = {
        .sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR,
        .window = r->native_window,
    };
    VK_CHECK(vkCreateAndroidSurfaceKHR(r->instance, &surface_info, NULL, &r->surface));
#else
    /* Desktop surface creation is handled by gpu_renderer_vulkan_desktop.c */
    VK_LOGE("Desktop Vulkan surface creation not implemented in this file");
    return false;
#endif

    if (!select_physical_device(r)) return false;
    if (!create_device(r)) return false;
    if (!create_swapchain(r)) return false;
    if (!create_render_pass(r)) return false;
    if (!create_descriptor_layout(r)) return false;
    if (!create_pipeline_layout(r)) return false;
    if (!create_pipelines(r)) return false;
    if (!create_framebuffers(r)) return false;
    if (!create_command_pool(r)) return false;
    if (!create_command_buffers(r)) return false;
    if (!create_sync_objects(r)) return false;
    if (!create_samplers(r)) return false;
    if (!create_descriptor_pool(r)) return false;

    r->surface_initialized = true;
    r->active = true;
    VK_LOGI("Vulkan GPU renderer initialized (%ux%u)",
            r->swapchain_extent.width, r->swapchain_extent.height);
    return true;
}

void gpu_renderer_resize(gpu_renderer_t *r, int width, int height) {
    if (!r || !r->surface_initialized) return;
    VK_LOGI("Surface resize: %dx%d", width, height);
    recreate_swapchain(r);
}

void gpu_renderer_deinit_surface(gpu_renderer_t *r) {
    if (!r || !r->surface_initialized) return;
    if (r->device) {
        vkDeviceWaitIdle(r->device);
    }

    if (r->offscreen_mode) {
        cleanup_offscreen(r);
    }
    cleanup_swapchain(r);

    /* Destroy game texture */
    if (r->game_texture_view) {
        vkDestroyImageView(r->device, r->game_texture_view, NULL);
        r->game_texture_view = VK_NULL_HANDLE;
    }
    if (r->game_texture) {
        vkDestroyImage(r->device, r->game_texture, NULL);
        r->game_texture = VK_NULL_HANDLE;
    }
    if (r->game_texture_memory) {
        vkFreeMemory(r->device, r->game_texture_memory, NULL);
        r->game_texture_memory = VK_NULL_HANDLE;
    }

    /* Destroy staging buffer */
    if (r->staging_buffer) {
        vkDestroyBuffer(r->device, r->staging_buffer, NULL);
        r->staging_buffer = VK_NULL_HANDLE;
    }
    if (r->staging_memory) {
        vkUnmapMemory(r->device, r->staging_memory);
        vkFreeMemory(r->device, r->staging_memory, NULL);
        r->staging_memory = VK_NULL_HANDLE;
        r->staging_mapped = NULL;
    }

    /* Destroy samplers */
    if (r->sampler_nearest) {
        vkDestroySampler(r->device, r->sampler_nearest, NULL);
        r->sampler_nearest = VK_NULL_HANDLE;
    }
    if (r->sampler_linear) {
        vkDestroySampler(r->device, r->sampler_linear, NULL);
        r->sampler_linear = VK_NULL_HANDLE;
    }

    /* Destroy descriptor pool */
    if (r->descriptor_pool) {
        vkDestroyDescriptorPool(r->device, r->descriptor_pool, NULL);
        r->descriptor_pool = VK_NULL_HANDLE;
    }
    if (r->descriptor_set_layout) {
        vkDestroyDescriptorSetLayout(r->device, r->descriptor_set_layout, NULL);
        r->descriptor_set_layout = VK_NULL_HANDLE;
    }

    /* Destroy pipelines */
    for (int i = 0; i < NUM_SHADERS; i++) {
        if (r->pipelines[i]) {
            vkDestroyPipeline(r->device, r->pipelines[i], NULL);
            r->pipelines[i] = VK_NULL_HANDLE;
        }
    }
    if (r->pipeline_layout) {
        vkDestroyPipelineLayout(r->device, r->pipeline_layout, NULL);
        r->pipeline_layout = VK_NULL_HANDLE;
    }
    if (r->render_pass) {
        vkDestroyRenderPass(r->device, r->render_pass, NULL);
        r->render_pass = VK_NULL_HANDLE;
    }

    /* Destroy sync objects */
    for (uint32_t i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
        if (r->image_available_semaphores[i]) {
            vkDestroySemaphore(r->device, r->image_available_semaphores[i], NULL);
            r->image_available_semaphores[i] = VK_NULL_HANDLE;
        }
        if (r->render_finished_semaphores[i]) {
            vkDestroySemaphore(r->device, r->render_finished_semaphores[i], NULL);
            r->render_finished_semaphores[i] = VK_NULL_HANDLE;
        }
        if (r->in_flight_fences[i]) {
            vkDestroyFence(r->device, r->in_flight_fences[i], NULL);
            r->in_flight_fences[i] = VK_NULL_HANDLE;
        }
    }

    /* Destroy command pool */
    if (r->command_pool) {
        vkDestroyCommandPool(r->device, r->command_pool, NULL);
        r->command_pool = VK_NULL_HANDLE;
    }

    r->surface_initialized = false;
    r->active = false;
}

void gpu_renderer_upload_frame(gpu_renderer_t *r, const void *data,
    unsigned width, unsigned height, size_t pitch, unsigned pixel_format) {
    if (!r || !r->active || !data) return;

    /* Determine Vulkan format from libretro pixel format */
    VkFormat vk_format;
    unsigned bpp;
    switch (pixel_format) {
        case RETRO_PIXEL_FORMAT_XRGB8888:
            vk_format = VK_FORMAT_B8G8R8A8_UNORM;
            bpp = 4;
            break;
        case RETRO_PIXEL_FORMAT_RGB565:
            vk_format = VK_FORMAT_R5G6B5_UNORM_PACK16;
            bpp = 2;
            break;
        case RETRO_PIXEL_FORMAT_0RGB1555:
            vk_format = VK_FORMAT_A1R5G5B5_UNORM_PACK16;
            bpp = 2;
            break;
        default:
            vk_format = VK_FORMAT_B8G8R8A8_UNORM;
            bpp = 4;
            break;
    }

    /* Recreate game texture if dimensions or format changed */
    if (width != r->game_texture_width || height != r->game_texture_height ||
        vk_format != r->game_texture_format) {
        vkDeviceWaitIdle(r->device);

        if (r->game_texture_view) {
            vkDestroyImageView(r->device, r->game_texture_view, NULL);
            r->game_texture_view = VK_NULL_HANDLE;
        }
        if (r->game_texture) {
            vkDestroyImage(r->device, r->game_texture, NULL);
            r->game_texture = VK_NULL_HANDLE;
        }
        if (r->game_texture_memory) {
            vkFreeMemory(r->device, r->game_texture_memory, NULL);
            r->game_texture_memory = VK_NULL_HANDLE;
        }

        if (!create_game_texture(r, width, height, vk_format)) {
            VK_LOGE("Failed to create game texture %ux%u", width, height);
            return;
        }
        update_descriptor_set(r);
    }

    /* Ensure staging buffer is large enough */
    VkDeviceSize needed = (VkDeviceSize)width * height * bpp;
    if (needed > r->staging_size) {
        if (r->staging_buffer) {
            vkDestroyBuffer(r->device, r->staging_buffer, NULL);
            r->staging_buffer = VK_NULL_HANDLE;
        }
        if (r->staging_memory) {
            vkUnmapMemory(r->device, r->staging_memory);
            vkFreeMemory(r->device, r->staging_memory, NULL);
            r->staging_memory = VK_NULL_HANDLE;
            r->staging_mapped = NULL;
        }
        if (!create_staging_buffer(r, needed)) {
            VK_LOGE("Failed to create staging buffer (%llu bytes)", (unsigned long long)needed);
            return;
        }
    }

    /* Copy frame data to staging buffer (handle pitch != width * bpp) */
    const uint8_t *src = (const uint8_t *)data;
    uint8_t *dst = (uint8_t *)r->staging_mapped;
    size_t row_bytes = (size_t)width * bpp;

    if (pitch == row_bytes) {
        memcpy(dst, src, needed);
    } else {
        for (unsigned y = 0; y < height; y++) {
            memcpy(dst, src, row_bytes);
            src += pitch;
            dst += row_bytes;
        }
    }

    /* Transition texture to transfer destination, copy, then to shader read */
    transition_image_layout(r, r->game_texture,
        VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
    copy_buffer_to_image(r, r->staging_buffer, r->game_texture, width, height);
    transition_image_layout(r, r->game_texture,
        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

    r->frame_uploaded = true;
    r->frame_width = width;
    r->frame_height = height;
    r->frame_pixel_format = pixel_format;
}

void gpu_renderer_set_shader(gpu_renderer_t *r, int shader_id) {
    if (!r) return;
    if (shader_id >= 0 && shader_id < NUM_SHADERS) {
        r->current_shader = shader_id;
    }
}

void gpu_renderer_render(gpu_renderer_t *r) {
    if (!r || !r->active || !r->frame_uploaded) return;

    /* Wait for the previous frame using this slot to finish */
    vkWaitForFences(r->device, 1, &r->in_flight_fences[r->current_frame],
                    VK_TRUE, UINT64_MAX);

    /* Acquire next swapchain image */
    uint32_t image_index;
    VkResult result = vkAcquireNextImageKHR(r->device, r->swapchain, UINT64_MAX,
        r->image_available_semaphores[r->current_frame], VK_NULL_HANDLE, &image_index);

    if (result == VK_ERROR_OUT_OF_DATE_KHR) {
        recreate_swapchain(r);
        return;
    } else if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) {
        VK_LOGE("vkAcquireNextImageKHR failed: %d", result);
        return;
    }

    vkResetFences(r->device, 1, &r->in_flight_fences[r->current_frame]);

    /* Record command buffer */
    VkCommandBuffer cmd = r->command_buffers[r->current_frame];
    vkResetCommandBuffer(cmd, 0);

    VkCommandBufferBeginInfo begin_info = {
        .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
        .flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT,
    };
    vkBeginCommandBuffer(cmd, &begin_info);

    /* Begin render pass */
    VkClearValue clear_value = { .color = { .float32 = { 0.0f, 0.0f, 0.0f, 1.0f } } };
    VkRenderPassBeginInfo rp_info = {
        .sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO,
        .renderPass = r->render_pass,
        .framebuffer = r->framebuffers[image_index],
        .renderArea = {
            .offset = { 0, 0 },
            .extent = r->swapchain_extent,
        },
        .clearValueCount = 1,
        .pClearValues = &clear_value,
    };
    vkCmdBeginRenderPass(cmd, &rp_info, VK_SUBPASS_CONTENTS_INLINE);

    /* Select pipeline based on current shader */
    int shader_idx = r->current_shader;
    if (shader_idx < 0 || shader_idx >= NUM_SHADERS || !r->pipelines[shader_idx]) {
        shader_idx = GPU_SHADER_NONE;
    }
    vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, r->pipelines[shader_idx]);

    /* Bind descriptor set (game texture) */
    vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS,
        r->pipeline_layout, 0, 1, &r->descriptor_set, 0, NULL);

    /* Push constants: texture size for shader effects */
    push_constants_t pc = {
        .texture_size = { (float)r->frame_width, (float)r->frame_height },
    };
    vkCmdPushConstants(cmd, r->pipeline_layout, VK_SHADER_STAGE_FRAGMENT_BIT,
                       0, sizeof(push_constants_t), &pc);

    /* Set viewport to maintain aspect ratio */
    float src_w = r->source_rect_set ? (float)r->source_w : (float)r->frame_width;
    float src_h = r->source_rect_set ? (float)r->source_h : (float)r->frame_height;
    float dst_w = (float)r->swapchain_extent.width;
    float dst_h = (float)r->swapchain_extent.height;
    float scale_x = dst_w / src_w;
    float scale_y = dst_h / src_h;
    float scale = scale_x < scale_y ? scale_x : scale_y;
    float vp_w = src_w * scale;
    float vp_h = src_h * scale;
    float vp_x = (dst_w - vp_w) / 2.0f;
    float vp_y = (dst_h - vp_h) / 2.0f;

    VkViewport viewport = {
        .x = vp_x, .y = vp_y,
        .width = vp_w, .height = vp_h,
        .minDepth = 0.0f, .maxDepth = 1.0f,
    };
    vkCmdSetViewport(cmd, 0, 1, &viewport);

    VkRect2D scissor = {
        .offset = { (int32_t)vp_x, (int32_t)vp_y },
        .extent = { (uint32_t)vp_w, (uint32_t)vp_h },
    };
    vkCmdSetScissor(cmd, 0, 1, &scissor);

    /* Draw fullscreen triangle (3 vertices, no vertex buffer) */
    vkCmdDraw(cmd, 3, 1, 0, 0);

    vkCmdEndRenderPass(cmd);
    vkEndCommandBuffer(cmd);

    /* Submit */
    VkSemaphore wait_semaphores[] = { r->image_available_semaphores[r->current_frame] };
    VkPipelineStageFlags wait_stages[] = { VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT };
    VkSemaphore signal_semaphores[] = { r->render_finished_semaphores[r->current_frame] };

    VkSubmitInfo submit_info = {
        .sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
        .waitSemaphoreCount = 1,
        .pWaitSemaphores = wait_semaphores,
        .pWaitDstStageMask = wait_stages,
        .commandBufferCount = 1,
        .pCommandBuffers = &cmd,
        .signalSemaphoreCount = 1,
        .pSignalSemaphores = signal_semaphores,
    };

    result = vkQueueSubmit(r->graphics_queue, 1, &submit_info,
                           r->in_flight_fences[r->current_frame]);
    if (result != VK_SUCCESS) {
        VK_LOGE("vkQueueSubmit failed: %d", result);
    }

    /* Present */
    VkPresentInfoKHR present_info = {
        .sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR,
        .waitSemaphoreCount = 1,
        .pWaitSemaphores = signal_semaphores,
        .swapchainCount = 1,
        .pSwapchains = &r->swapchain,
        .pImageIndices = &image_index,
    };

    result = vkQueuePresentKHR(r->graphics_queue, &present_info);
    if (result == VK_ERROR_OUT_OF_DATE_KHR || result == VK_SUBOPTIMAL_KHR) {
        recreate_swapchain(r);
    } else if (result != VK_SUCCESS) {
        VK_LOGE("vkQueuePresentKHR failed: %d", result);
    }

    r->current_frame = (r->current_frame + 1) % MAX_FRAMES_IN_FLIGHT;
}

void gpu_renderer_set_source_rect(gpu_renderer_t *r, int x, int y, int w, int h) {
    if (!r) return;
    if (w <= 0 || h <= 0) {
        r->source_rect_set = false;
        return;
    }
    r->source_x = x;
    r->source_y = y;
    r->source_w = w;
    r->source_h = h;
    r->source_rect_set = true;
}

struct retro_hw_render_callback *gpu_renderer_get_hw_callback(gpu_renderer_t *r) {
    /* Phase 4: will return HW render interface */
    (void)r;
    return NULL;
}

bool gpu_renderer_is_active(gpu_renderer_t *r) {
    return r && r->active;
}

bool gpu_renderer_init_offscreen(gpu_renderer_t *r, int width, int height) {
    if (!r || r->surface_initialized) return false;

    r->offscreen_mode = true;
    r->offscreen_width = width;
    r->offscreen_height = height;

    if (!create_instance(r)) return false;
    if (!select_physical_device(r)) return false;
    if (!create_device(r)) return false;
    if (!create_command_pool(r)) return false;
    if (!create_offscreen_render_pass(r)) return false;
    if (!create_descriptor_layout(r)) return false;
    if (!create_pipeline_layout(r)) return false;
    if (!create_pipelines(r)) return false;
    if (!create_offscreen_target(r, width, height)) return false;
    if (!create_samplers(r)) return false;
    if (!create_descriptor_pool(r)) return false;

    /* Create fence for offscreen rendering synchronization */
    VkFenceCreateInfo fence_info = {
        .sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO,
        .flags = VK_FENCE_CREATE_SIGNALED_BIT,
    };
    if (vkCreateFence(r->device, &fence_info, NULL, &r->offscreen_fence) != VK_SUCCESS) {
        VK_LOGE("Failed to create offscreen fence");
        return false;
    }

    /* Allocate a command buffer for offscreen rendering */
    VkCommandBufferAllocateInfo alloc_info = {
        .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,
        .commandPool = r->command_pool,
        .level = VK_COMMAND_BUFFER_LEVEL_PRIMARY,
        .commandBufferCount = 1,
    };
    if (vkAllocateCommandBuffers(r->device, &alloc_info, &r->offscreen_cmd) != VK_SUCCESS) {
        VK_LOGE("Failed to allocate offscreen command buffer");
        return false;
    }

    r->surface_initialized = true;
    r->active = true;
    VK_LOGI("Vulkan offscreen GPU renderer initialized (%dx%d)", width, height);
    return true;
}

size_t gpu_renderer_render_to_bgra(gpu_renderer_t *r, void *out_data, size_t out_capacity,
    unsigned *out_width, unsigned *out_height) {
    if (!r || !r->active || !r->offscreen_mode || !r->frame_uploaded) return 0;

    unsigned w = (unsigned)r->offscreen_width;
    unsigned h = (unsigned)r->offscreen_height;
    size_t needed = (size_t)w * h * 4;

    if (out_capacity < needed) {
        VK_LOGE("Readback buffer too small: need %zu, have %zu", needed, out_capacity);
        return 0;
    }

    /* Ensure readback buffer is large enough */
    if (needed > r->readback_size) {
        if (r->readback_buffer) {
            vkDestroyBuffer(r->device, r->readback_buffer, NULL);
            r->readback_buffer = VK_NULL_HANDLE;
        }
        if (r->readback_memory) {
            vkUnmapMemory(r->device, r->readback_memory);
            vkFreeMemory(r->device, r->readback_memory, NULL);
            r->readback_memory = VK_NULL_HANDLE;
            r->readback_mapped = NULL;
        }
        if (!create_readback_buffer(r, needed)) return 0;
    }

    /* Wait for previous offscreen render to complete */
    vkWaitForFences(r->device, 1, &r->offscreen_fence, VK_TRUE, UINT64_MAX);
    vkResetFences(r->device, 1, &r->offscreen_fence);

    /* Record command buffer */
    VkCommandBuffer cmd = r->offscreen_cmd;
    vkResetCommandBuffer(cmd, 0);

    VkCommandBufferBeginInfo begin_info = {
        .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
        .flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT,
    };
    vkBeginCommandBuffer(cmd, &begin_info);

    /* Begin render pass targeting offscreen framebuffer */
    VkClearValue clear_value = { .color = { .float32 = { 0.0f, 0.0f, 0.0f, 1.0f } } };
    VkRenderPassBeginInfo rp_info = {
        .sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO,
        .renderPass = r->offscreen_render_pass,
        .framebuffer = r->offscreen_framebuffer,
        .renderArea = {
            .offset = { 0, 0 },
            .extent = { w, h },
        },
        .clearValueCount = 1,
        .pClearValues = &clear_value,
    };
    vkCmdBeginRenderPass(cmd, &rp_info, VK_SUBPASS_CONTENTS_INLINE);

    /* Select pipeline based on current shader */
    int shader_idx = r->current_shader;
    if (shader_idx < 0 || shader_idx >= NUM_SHADERS || !r->pipelines[shader_idx]) {
        shader_idx = GPU_SHADER_NONE;
    }
    vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, r->pipelines[shader_idx]);

    vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS,
        r->pipeline_layout, 0, 1, &r->descriptor_set, 0, NULL);

    push_constants_t pc = {
        .texture_size = { (float)r->frame_width, (float)r->frame_height },
    };
    vkCmdPushConstants(cmd, r->pipeline_layout, VK_SHADER_STAGE_FRAGMENT_BIT,
                       0, sizeof(push_constants_t), &pc);

    /* Set viewport to maintain aspect ratio */
    float src_w = r->source_rect_set ? (float)r->source_w : (float)r->frame_width;
    float src_h = r->source_rect_set ? (float)r->source_h : (float)r->frame_height;
    float dst_w = (float)w;
    float dst_h = (float)h;
    float scale_x = dst_w / src_w;
    float scale_y = dst_h / src_h;
    float scale = scale_x < scale_y ? scale_x : scale_y;
    float vp_w = src_w * scale;
    float vp_h = src_h * scale;
    float vp_x = (dst_w - vp_w) / 2.0f;
    float vp_y = (dst_h - vp_h) / 2.0f;

    VkViewport viewport = {
        .x = vp_x, .y = vp_y,
        .width = vp_w, .height = vp_h,
        .minDepth = 0.0f, .maxDepth = 1.0f,
    };
    vkCmdSetViewport(cmd, 0, 1, &viewport);

    VkRect2D scissor = {
        .offset = { (int32_t)vp_x, (int32_t)vp_y },
        .extent = { (uint32_t)vp_w, (uint32_t)vp_h },
    };
    vkCmdSetScissor(cmd, 0, 1, &scissor);

    vkCmdDraw(cmd, 3, 1, 0, 0);
    vkCmdEndRenderPass(cmd);

    /* Transition offscreen image for transfer read */
    VkImageMemoryBarrier barrier = {
        .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
        .oldLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
        .newLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
        .srcAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
        .dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT,
        .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
        .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
        .image = r->offscreen_image,
        .subresourceRange = {
            .aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
            .baseMipLevel = 0,
            .levelCount = 1,
            .baseArrayLayer = 0,
            .layerCount = 1,
        },
    };
    vkCmdPipelineBarrier(cmd,
        VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
        VK_PIPELINE_STAGE_TRANSFER_BIT,
        0, 0, NULL, 0, NULL, 1, &barrier);

    /* Copy offscreen image to readback buffer */
    VkBufferImageCopy copy_region = {
        .bufferOffset = 0,
        .bufferRowLength = 0,
        .bufferImageHeight = 0,
        .imageSubresource = {
            .aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
            .mipLevel = 0,
            .baseArrayLayer = 0,
            .layerCount = 1,
        },
        .imageOffset = { 0, 0, 0 },
        .imageExtent = { w, h, 1 },
    };
    vkCmdCopyImageToBuffer(cmd, r->offscreen_image,
        VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, r->readback_buffer, 1, &copy_region);

    vkEndCommandBuffer(cmd);

    /* Submit and wait */
    VkSubmitInfo submit_info = {
        .sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
        .commandBufferCount = 1,
        .pCommandBuffers = &cmd,
    };
    vkQueueSubmit(r->graphics_queue, 1, &submit_info, r->offscreen_fence);
    vkWaitForFences(r->device, 1, &r->offscreen_fence, VK_TRUE, UINT64_MAX);

    /* Copy from mapped readback buffer to output */
    memcpy(out_data, r->readback_mapped, needed);

    if (out_width) *out_width = w;
    if (out_height) *out_height = h;

    return needed;
}

/* ===== Internal implementation ===== */

static bool create_instance(gpu_renderer_t *r) {
    VkApplicationInfo app_info = {
        .sType = VK_STRUCTURE_TYPE_APPLICATION_INFO,
        .pApplicationName = "Spela",
        .applicationVersion = VK_MAKE_VERSION(1, 0, 0),
        .pEngineName = "Spela GPU Renderer",
        .engineVersion = VK_MAKE_VERSION(1, 0, 0),
        .apiVersion = VK_API_VERSION_1_0,
    };

    VkInstanceCreateInfo create_info = {
        .sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,
        .pApplicationInfo = &app_info,
    };

    if (!r->offscreen_mode) {
        /* On-screen: need surface extensions */
        static const char *surface_extensions[] = {
            VK_KHR_SURFACE_EXTENSION_NAME,
#ifdef __ANDROID__
            VK_KHR_ANDROID_SURFACE_EXTENSION_NAME,
#elif defined(_WIN32)
            "VK_KHR_win32_surface",
#elif defined(__linux__)
            "VK_KHR_xlib_surface",
#endif
        };
        create_info.enabledExtensionCount = sizeof(surface_extensions) / sizeof(surface_extensions[0]);
        create_info.ppEnabledExtensionNames = surface_extensions;
    }
    /* Offscreen: no extensions needed */

    VK_CHECK(vkCreateInstance(&create_info, NULL, &r->instance));
    VK_LOGI("Vulkan instance created (offscreen=%d)", r->offscreen_mode);
    return true;
}

static bool select_physical_device(gpu_renderer_t *r) {
    uint32_t device_count = 0;
    vkEnumeratePhysicalDevices(r->instance, &device_count, NULL);
    if (device_count == 0) {
        VK_LOGE("No Vulkan physical devices found");
        return false;
    }

    VkPhysicalDevice *devices = (VkPhysicalDevice *)malloc(device_count * sizeof(VkPhysicalDevice));
    vkEnumeratePhysicalDevices(r->instance, &device_count, devices);

    /* Pick first device with a graphics queue (and presentation support if on-screen) */
    for (uint32_t i = 0; i < device_count; i++) {
        uint32_t queue_family_count = 0;
        vkGetPhysicalDeviceQueueFamilyProperties(devices[i], &queue_family_count, NULL);
        VkQueueFamilyProperties *queue_families = (VkQueueFamilyProperties *)
            malloc(queue_family_count * sizeof(VkQueueFamilyProperties));
        vkGetPhysicalDeviceQueueFamilyProperties(devices[i], &queue_family_count, queue_families);

        for (uint32_t j = 0; j < queue_family_count; j++) {
            if (queue_families[j].queueFlags & VK_QUEUE_GRAPHICS_BIT) {
                if (r->offscreen_mode) {
                    /* Offscreen: just need graphics, no present */
                    r->physical_device = devices[i];
                    r->queue_family_index = j;
                    free(queue_families);
                    free(devices);

                    VkPhysicalDeviceProperties props;
                    vkGetPhysicalDeviceProperties(r->physical_device, &props);
                    VK_LOGI("Selected GPU: %s (offscreen)", props.deviceName);
                    return true;
                }
                VkBool32 present_support = VK_FALSE;
                vkGetPhysicalDeviceSurfaceSupportKHR(devices[i], j, r->surface, &present_support);
                if (present_support) {
                    r->physical_device = devices[i];
                    r->queue_family_index = j;
                    free(queue_families);
                    free(devices);

                    VkPhysicalDeviceProperties props;
                    vkGetPhysicalDeviceProperties(r->physical_device, &props);
                    VK_LOGI("Selected GPU: %s", props.deviceName);
                    return true;
                }
            }
        }
        free(queue_families);
    }

    free(devices);
    VK_LOGE("No suitable GPU found");
    return false;
}

static bool create_device(gpu_renderer_t *r) {
    float queue_priority = 1.0f;
    VkDeviceQueueCreateInfo queue_info = {
        .sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO,
        .queueFamilyIndex = r->queue_family_index,
        .queueCount = 1,
        .pQueuePriorities = &queue_priority,
    };

    const char *device_extensions[] = {
        VK_KHR_SWAPCHAIN_EXTENSION_NAME,
    };

    VkDeviceCreateInfo create_info = {
        .sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO,
        .queueCreateInfoCount = 1,
        .pQueueCreateInfos = &queue_info,
    };

    if (!r->offscreen_mode) {
        create_info.enabledExtensionCount = 1;
        create_info.ppEnabledExtensionNames = device_extensions;
    }
    /* Offscreen: no swapchain extension needed */

    VK_CHECK(vkCreateDevice(r->physical_device, &create_info, NULL, &r->device));
    vkGetDeviceQueue(r->device, r->queue_family_index, 0, &r->graphics_queue);

    VK_LOGI("Vulkan device created");
    return true;
}

static bool create_swapchain(gpu_renderer_t *r) {
    VkSurfaceCapabilitiesKHR capabilities;
    vkGetPhysicalDeviceSurfaceCapabilitiesKHR(r->physical_device, r->surface, &capabilities);

    /* Choose surface format */
    uint32_t format_count;
    vkGetPhysicalDeviceSurfaceFormatsKHR(r->physical_device, r->surface, &format_count, NULL);
    VkSurfaceFormatKHR *formats = (VkSurfaceFormatKHR *)
        malloc(format_count * sizeof(VkSurfaceFormatKHR));
    vkGetPhysicalDeviceSurfaceFormatsKHR(r->physical_device, r->surface, &format_count, formats);

    VkSurfaceFormatKHR chosen_format = formats[0];
    for (uint32_t i = 0; i < format_count; i++) {
        if (formats[i].format == VK_FORMAT_B8G8R8A8_UNORM &&
            formats[i].colorSpace == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
            chosen_format = formats[i];
            break;
        }
    }
    free(formats);

    /* Choose present mode: prefer FIFO (vsync) */
    VkPresentModeKHR present_mode = VK_PRESENT_MODE_FIFO_KHR;

    /* Choose extent */
    VkExtent2D extent = capabilities.currentExtent;
    if (extent.width == UINT32_MAX) {
        extent.width = 800;
        extent.height = 600;
    }

    /* Image count: prefer triple buffering */
    uint32_t image_count = capabilities.minImageCount + 1;
    if (capabilities.maxImageCount > 0 && image_count > capabilities.maxImageCount) {
        image_count = capabilities.maxImageCount;
    }

    VkSwapchainCreateInfoKHR create_info = {
        .sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR,
        .surface = r->surface,
        .minImageCount = image_count,
        .imageFormat = chosen_format.format,
        .imageColorSpace = chosen_format.colorSpace,
        .imageExtent = extent,
        .imageArrayLayers = 1,
        .imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT,
        .imageSharingMode = VK_SHARING_MODE_EXCLUSIVE,
        .preTransform = capabilities.currentTransform,
        .compositeAlpha = VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR,
        .presentMode = present_mode,
        .clipped = VK_TRUE,
        .oldSwapchain = VK_NULL_HANDLE,
    };

    /* Fallback composite alpha if INHERIT is not supported */
    if (!(capabilities.supportedCompositeAlpha & VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR)) {
        if (capabilities.supportedCompositeAlpha & VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR) {
            create_info.compositeAlpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
        }
    }

    VK_CHECK(vkCreateSwapchainKHR(r->device, &create_info, NULL, &r->swapchain));

    r->swapchain_format = chosen_format.format;
    r->swapchain_extent = extent;

    /* Get swapchain images */
    vkGetSwapchainImagesKHR(r->device, r->swapchain, &r->swapchain_image_count, NULL);
    r->swapchain_images = (VkImage *)malloc(r->swapchain_image_count * sizeof(VkImage));
    vkGetSwapchainImagesKHR(r->device, r->swapchain, &r->swapchain_image_count, r->swapchain_images);

    /* Create image views */
    r->swapchain_image_views = (VkImageView *)malloc(r->swapchain_image_count * sizeof(VkImageView));
    for (uint32_t i = 0; i < r->swapchain_image_count; i++) {
        VkImageViewCreateInfo view_info = {
            .sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO,
            .image = r->swapchain_images[i],
            .viewType = VK_IMAGE_VIEW_TYPE_2D,
            .format = r->swapchain_format,
            .components = {
                .r = VK_COMPONENT_SWIZZLE_IDENTITY,
                .g = VK_COMPONENT_SWIZZLE_IDENTITY,
                .b = VK_COMPONENT_SWIZZLE_IDENTITY,
                .a = VK_COMPONENT_SWIZZLE_IDENTITY,
            },
            .subresourceRange = {
                .aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
                .baseMipLevel = 0,
                .levelCount = 1,
                .baseArrayLayer = 0,
                .layerCount = 1,
            },
        };
        VK_CHECK(vkCreateImageView(r->device, &view_info, NULL, &r->swapchain_image_views[i]));
    }

    VK_LOGI("Swapchain created: %ux%u, %u images", extent.width, extent.height, r->swapchain_image_count);
    return true;
}

static bool create_render_pass(gpu_renderer_t *r) {
    VkAttachmentDescription color_attachment = {
        .format = r->swapchain_format,
        .samples = VK_SAMPLE_COUNT_1_BIT,
        .loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR,
        .storeOp = VK_ATTACHMENT_STORE_OP_STORE,
        .stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE,
        .stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE,
        .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED,
        .finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
    };

    VkAttachmentReference color_ref = {
        .attachment = 0,
        .layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
    };

    VkSubpassDescription subpass = {
        .pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS,
        .colorAttachmentCount = 1,
        .pColorAttachments = &color_ref,
    };

    VkSubpassDependency dependency = {
        .srcSubpass = VK_SUBPASS_EXTERNAL,
        .dstSubpass = 0,
        .srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
        .srcAccessMask = 0,
        .dstStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
        .dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
    };

    VkRenderPassCreateInfo create_info = {
        .sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO,
        .attachmentCount = 1,
        .pAttachments = &color_attachment,
        .subpassCount = 1,
        .pSubpasses = &subpass,
        .dependencyCount = 1,
        .pDependencies = &dependency,
    };

    VK_CHECK(vkCreateRenderPass(r->device, &create_info, NULL, &r->render_pass));
    return true;
}

static bool create_descriptor_layout(gpu_renderer_t *r) {
    VkDescriptorSetLayoutBinding binding = {
        .binding = 0,
        .descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        .descriptorCount = 1,
        .stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT,
    };

    VkDescriptorSetLayoutCreateInfo create_info = {
        .sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO,
        .bindingCount = 1,
        .pBindings = &binding,
    };

    VK_CHECK(vkCreateDescriptorSetLayout(r->device, &create_info, NULL, &r->descriptor_set_layout));
    return true;
}

static bool create_pipeline_layout(gpu_renderer_t *r) {
    VkPushConstantRange push_range = {
        .stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT,
        .offset = 0,
        .size = sizeof(push_constants_t),
    };

    VkPipelineLayoutCreateInfo create_info = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO,
        .setLayoutCount = 1,
        .pSetLayouts = &r->descriptor_set_layout,
        .pushConstantRangeCount = 1,
        .pPushConstantRanges = &push_range,
    };

    VK_CHECK(vkCreatePipelineLayout(r->device, &create_info, NULL, &r->pipeline_layout));
    return true;
}

static bool create_single_pipeline(gpu_renderer_t *r, const uint32_t *frag_code,
    size_t frag_size, int shader_index) {

    /* Use placeholder check -- if shader size is 0, skip (will use passthrough) */
    if (!frag_code || frag_size == 0) return true;

    VkShaderModule vert_module = create_shader_module(r, spv_fullscreen_quad_vert, spv_fullscreen_quad_vert_size);
    if (!vert_module && spv_fullscreen_quad_vert_size > 0) return false;
    if (!vert_module) return true; /* Placeholder -- no shaders compiled yet */

    VkShaderModule frag_module = create_shader_module(r, frag_code, frag_size);
    if (!frag_module) {
        vkDestroyShaderModule(r->device, vert_module, NULL);
        return false;
    }

    VkPipelineShaderStageCreateInfo stages[] = {
        {
            .sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
            .stage = VK_SHADER_STAGE_VERTEX_BIT,
            .module = vert_module,
            .pName = "main",
        },
        {
            .sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
            .stage = VK_SHADER_STAGE_FRAGMENT_BIT,
            .module = frag_module,
            .pName = "main",
        },
    };

    /* No vertex input -- fullscreen triangle uses gl_VertexIndex */
    VkPipelineVertexInputStateCreateInfo vertex_input = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO,
    };

    VkPipelineInputAssemblyStateCreateInfo input_assembly = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO,
        .topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST,
    };

    VkPipelineViewportStateCreateInfo viewport_state = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO,
        .viewportCount = 1,
        .scissorCount = 1,
    };

    VkPipelineRasterizationStateCreateInfo rasterizer = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO,
        .polygonMode = VK_POLYGON_MODE_FILL,
        .lineWidth = 1.0f,
        .cullMode = VK_CULL_MODE_NONE,
        .frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE,
    };

    VkPipelineMultisampleStateCreateInfo multisampling = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO,
        .rasterizationSamples = VK_SAMPLE_COUNT_1_BIT,
    };

    VkPipelineColorBlendAttachmentState blend_attachment = {
        .colorWriteMask = VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT |
                          VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT,
        .blendEnable = VK_FALSE,
    };

    VkPipelineColorBlendStateCreateInfo color_blending = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO,
        .attachmentCount = 1,
        .pAttachments = &blend_attachment,
    };

    VkDynamicState dynamic_states[] = {
        VK_DYNAMIC_STATE_VIEWPORT,
        VK_DYNAMIC_STATE_SCISSOR,
    };

    VkPipelineDynamicStateCreateInfo dynamic_state = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO,
        .dynamicStateCount = 2,
        .pDynamicStates = dynamic_states,
    };

    VkGraphicsPipelineCreateInfo pipeline_info = {
        .sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO,
        .stageCount = 2,
        .pStages = stages,
        .pVertexInputState = &vertex_input,
        .pInputAssemblyState = &input_assembly,
        .pViewportState = &viewport_state,
        .pRasterizationState = &rasterizer,
        .pMultisampleState = &multisampling,
        .pColorBlendState = &color_blending,
        .pDynamicState = &dynamic_state,
        .layout = r->pipeline_layout,
        .renderPass = r->render_pass,
        .subpass = 0,
    };

    VkResult result = vkCreateGraphicsPipelines(r->device, VK_NULL_HANDLE, 1,
        &pipeline_info, NULL, &r->pipelines[shader_index]);

    vkDestroyShaderModule(r->device, vert_module, NULL);
    vkDestroyShaderModule(r->device, frag_module, NULL);

    return result == VK_SUCCESS;
}

static bool create_pipelines(gpu_renderer_t *r) {
    /* Create one pipeline per shader. If SPIR-V is not compiled yet (placeholder),
     * pipelines will be NULL and we fall back to passthrough. */

    struct {
        const uint32_t *code;
        size_t size;
    } frag_shaders[NUM_SHADERS] = {
        [GPU_SHADER_NONE]           = { spv_passthrough_frag, spv_passthrough_frag_size },
        [GPU_SHADER_BILINEAR]       = { spv_bilinear_frag, spv_bilinear_frag_size },
        [GPU_SHADER_SHARP_BILINEAR] = { spv_sharp_bilinear_frag, spv_sharp_bilinear_frag_size },
        [GPU_SHADER_CRT_SIMPLE]     = { spv_crt_simple_frag, spv_crt_simple_frag_size },
        [GPU_SHADER_SCANLINES]      = { spv_scanlines_frag, spv_scanlines_frag_size },
        [GPU_SHADER_LCD_GRID]       = { spv_lcd_grid_frag, spv_lcd_grid_frag_size },
    };

    for (int i = 0; i < NUM_SHADERS; i++) {
        if (frag_shaders[i].size > 0) {
            if (!create_single_pipeline(r, frag_shaders[i].code, frag_shaders[i].size, i)) {
                VK_LOGW("Failed to create pipeline for shader %d, will fall back", i);
            }
        }
    }

    return true;
}

static bool create_framebuffers(gpu_renderer_t *r) {
    r->framebuffers = (VkFramebuffer *)malloc(r->swapchain_image_count * sizeof(VkFramebuffer));

    for (uint32_t i = 0; i < r->swapchain_image_count; i++) {
        VkFramebufferCreateInfo fb_info = {
            .sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO,
            .renderPass = r->render_pass,
            .attachmentCount = 1,
            .pAttachments = &r->swapchain_image_views[i],
            .width = r->swapchain_extent.width,
            .height = r->swapchain_extent.height,
            .layers = 1,
        };
        VK_CHECK(vkCreateFramebuffer(r->device, &fb_info, NULL, &r->framebuffers[i]));
    }

    return true;
}

static bool create_command_pool(gpu_renderer_t *r) {
    VkCommandPoolCreateInfo pool_info = {
        .sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO,
        .flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT,
        .queueFamilyIndex = r->queue_family_index,
    };

    VK_CHECK(vkCreateCommandPool(r->device, &pool_info, NULL, &r->command_pool));
    return true;
}

static bool create_command_buffers(gpu_renderer_t *r) {
    VkCommandBufferAllocateInfo alloc_info = {
        .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,
        .commandPool = r->command_pool,
        .level = VK_COMMAND_BUFFER_LEVEL_PRIMARY,
        .commandBufferCount = MAX_FRAMES_IN_FLIGHT,
    };

    VK_CHECK(vkAllocateCommandBuffers(r->device, &alloc_info, r->command_buffers));
    return true;
}

static bool create_sync_objects(gpu_renderer_t *r) {
    VkSemaphoreCreateInfo sem_info = {
        .sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO,
    };

    VkFenceCreateInfo fence_info = {
        .sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO,
        .flags = VK_FENCE_CREATE_SIGNALED_BIT,
    };

    for (uint32_t i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
        VK_CHECK(vkCreateSemaphore(r->device, &sem_info, NULL, &r->image_available_semaphores[i]));
        VK_CHECK(vkCreateSemaphore(r->device, &sem_info, NULL, &r->render_finished_semaphores[i]));
        VK_CHECK(vkCreateFence(r->device, &fence_info, NULL, &r->in_flight_fences[i]));
    }

    return true;
}

static bool create_samplers(gpu_renderer_t *r) {
    VkSamplerCreateInfo nearest_info = {
        .sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO,
        .magFilter = VK_FILTER_NEAREST,
        .minFilter = VK_FILTER_NEAREST,
        .addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
        .addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
        .addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
        .maxLod = 1.0f,
    };
    VK_CHECK(vkCreateSampler(r->device, &nearest_info, NULL, &r->sampler_nearest));

    VkSamplerCreateInfo linear_info = nearest_info;
    linear_info.magFilter = VK_FILTER_LINEAR;
    linear_info.minFilter = VK_FILTER_LINEAR;
    VK_CHECK(vkCreateSampler(r->device, &linear_info, NULL, &r->sampler_linear));

    return true;
}

static bool create_descriptor_pool(gpu_renderer_t *r) {
    VkDescriptorPoolSize pool_size = {
        .type = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        .descriptorCount = 1,
    };

    VkDescriptorPoolCreateInfo pool_info = {
        .sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO,
        .maxSets = 1,
        .poolSizeCount = 1,
        .pPoolSizes = &pool_size,
    };

    VK_CHECK(vkCreateDescriptorPool(r->device, &pool_info, NULL, &r->descriptor_pool));

    VkDescriptorSetAllocateInfo alloc_info = {
        .sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO,
        .descriptorPool = r->descriptor_pool,
        .descriptorSetCount = 1,
        .pSetLayouts = &r->descriptor_set_layout,
    };

    VK_CHECK(vkAllocateDescriptorSets(r->device, &alloc_info, &r->descriptor_set));
    return true;
}

static bool create_game_texture(gpu_renderer_t *r, unsigned w, unsigned h, VkFormat fmt) {
    VkImageCreateInfo image_info = {
        .sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO,
        .imageType = VK_IMAGE_TYPE_2D,
        .format = fmt,
        .extent = { w, h, 1 },
        .mipLevels = 1,
        .arrayLayers = 1,
        .samples = VK_SAMPLE_COUNT_1_BIT,
        .tiling = VK_IMAGE_TILING_OPTIMAL,
        .usage = VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
        .sharingMode = VK_SHARING_MODE_EXCLUSIVE,
        .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED,
    };

    VK_CHECK(vkCreateImage(r->device, &image_info, NULL, &r->game_texture));

    VkMemoryRequirements mem_reqs;
    vkGetImageMemoryRequirements(r->device, r->game_texture, &mem_reqs);

    VkMemoryAllocateInfo alloc_info = {
        .sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,
        .allocationSize = mem_reqs.size,
        .memoryTypeIndex = find_memory_type(r, mem_reqs.memoryTypeBits,
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT),
    };

    VK_CHECK(vkAllocateMemory(r->device, &alloc_info, NULL, &r->game_texture_memory));
    VK_CHECK(vkBindImageMemory(r->device, r->game_texture, r->game_texture_memory, 0));

    /* Create image view */
    VkImageViewCreateInfo view_info = {
        .sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO,
        .image = r->game_texture,
        .viewType = VK_IMAGE_VIEW_TYPE_2D,
        .format = fmt,
        .subresourceRange = {
            .aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
            .baseMipLevel = 0,
            .levelCount = 1,
            .baseArrayLayer = 0,
            .layerCount = 1,
        },
    };

    VK_CHECK(vkCreateImageView(r->device, &view_info, NULL, &r->game_texture_view));

    r->game_texture_width = w;
    r->game_texture_height = h;
    r->game_texture_format = fmt;

    VK_LOGI("Game texture created: %ux%u, format=%d", w, h, fmt);
    return true;
}

static bool update_descriptor_set(gpu_renderer_t *r) {
    if (!r->game_texture_view) return false;

    /* Choose sampler based on current shader */
    VkSampler sampler = r->sampler_nearest;
    if (r->current_shader == GPU_SHADER_BILINEAR ||
        r->current_shader == GPU_SHADER_SHARP_BILINEAR) {
        sampler = r->sampler_linear;
    }

    VkDescriptorImageInfo image_info = {
        .sampler = sampler,
        .imageView = r->game_texture_view,
        .imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
    };

    VkWriteDescriptorSet write = {
        .sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET,
        .dstSet = r->descriptor_set,
        .dstBinding = 0,
        .descriptorCount = 1,
        .descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        .pImageInfo = &image_info,
    };

    vkUpdateDescriptorSets(r->device, 1, &write, 0, NULL);
    return true;
}

static bool create_staging_buffer(gpu_renderer_t *r, VkDeviceSize size) {
    VkBufferCreateInfo buffer_info = {
        .sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO,
        .size = size,
        .usage = VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
        .sharingMode = VK_SHARING_MODE_EXCLUSIVE,
    };

    VK_CHECK(vkCreateBuffer(r->device, &buffer_info, NULL, &r->staging_buffer));

    VkMemoryRequirements mem_reqs;
    vkGetBufferMemoryRequirements(r->device, r->staging_buffer, &mem_reqs);

    VkMemoryAllocateInfo alloc_info = {
        .sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,
        .allocationSize = mem_reqs.size,
        .memoryTypeIndex = find_memory_type(r, mem_reqs.memoryTypeBits,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT),
    };

    VK_CHECK(vkAllocateMemory(r->device, &alloc_info, NULL, &r->staging_memory));
    VK_CHECK(vkBindBufferMemory(r->device, r->staging_buffer, r->staging_memory, 0));
    VK_CHECK(vkMapMemory(r->device, r->staging_memory, 0, size, 0, &r->staging_mapped));

    r->staging_size = size;
    return true;
}

static void cleanup_swapchain(gpu_renderer_t *r) {
    if (!r->device) return;

    if (r->framebuffers) {
        for (uint32_t i = 0; i < r->swapchain_image_count; i++) {
            if (r->framebuffers[i]) {
                vkDestroyFramebuffer(r->device, r->framebuffers[i], NULL);
            }
        }
        free(r->framebuffers);
        r->framebuffers = NULL;
    }

    if (r->swapchain_image_views) {
        for (uint32_t i = 0; i < r->swapchain_image_count; i++) {
            if (r->swapchain_image_views[i]) {
                vkDestroyImageView(r->device, r->swapchain_image_views[i], NULL);
            }
        }
        free(r->swapchain_image_views);
        r->swapchain_image_views = NULL;
    }

    free(r->swapchain_images);
    r->swapchain_images = NULL;

    if (r->swapchain) {
        vkDestroySwapchainKHR(r->device, r->swapchain, NULL);
        r->swapchain = VK_NULL_HANDLE;
    }
}

static bool recreate_swapchain(gpu_renderer_t *r) {
    vkDeviceWaitIdle(r->device);
    cleanup_swapchain(r);

    if (!create_swapchain(r)) return false;
    if (!create_framebuffers(r)) return false;

    VK_LOGI("Swapchain recreated: %ux%u", r->swapchain_extent.width, r->swapchain_extent.height);
    return true;
}

static uint32_t find_memory_type(gpu_renderer_t *r, uint32_t type_filter, VkMemoryPropertyFlags properties) {
    VkPhysicalDeviceMemoryProperties mem_props;
    vkGetPhysicalDeviceMemoryProperties(r->physical_device, &mem_props);

    for (uint32_t i = 0; i < mem_props.memoryTypeCount; i++) {
        if ((type_filter & (1 << i)) &&
            (mem_props.memoryTypes[i].propertyFlags & properties) == properties) {
            return i;
        }
    }

    VK_LOGE("Failed to find suitable memory type");
    return 0;
}

static VkShaderModule create_shader_module(gpu_renderer_t *r, const uint32_t *code, size_t size) {
    if (!code || size == 0) return VK_NULL_HANDLE;

    VkShaderModuleCreateInfo create_info = {
        .sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO,
        .codeSize = size,
        .pCode = code,
    };

    VkShaderModule module;
    VkResult result = vkCreateShaderModule(r->device, &create_info, NULL, &module);
    if (result != VK_SUCCESS) {
        VK_LOGE("vkCreateShaderModule failed: %d", result);
        return VK_NULL_HANDLE;
    }
    return module;
}

static bool transition_image_layout(gpu_renderer_t *r, VkImage image,
    VkImageLayout old_layout, VkImageLayout new_layout) {

    VkCommandBuffer cmd;
    VkCommandBufferAllocateInfo alloc_info = {
        .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,
        .commandPool = r->command_pool,
        .level = VK_COMMAND_BUFFER_LEVEL_PRIMARY,
        .commandBufferCount = 1,
    };
    VK_CHECK(vkAllocateCommandBuffers(r->device, &alloc_info, &cmd));

    VkCommandBufferBeginInfo begin_info = {
        .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
        .flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT,
    };
    vkBeginCommandBuffer(cmd, &begin_info);

    VkImageMemoryBarrier barrier = {
        .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
        .oldLayout = old_layout,
        .newLayout = new_layout,
        .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
        .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
        .image = image,
        .subresourceRange = {
            .aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
            .baseMipLevel = 0,
            .levelCount = 1,
            .baseArrayLayer = 0,
            .layerCount = 1,
        },
    };

    VkPipelineStageFlags src_stage, dst_stage;

    if (old_layout == VK_IMAGE_LAYOUT_UNDEFINED &&
        new_layout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
        barrier.srcAccessMask = 0;
        barrier.dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        src_stage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
        dst_stage = VK_PIPELINE_STAGE_TRANSFER_BIT;
    } else if (old_layout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL &&
               new_layout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
        barrier.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        barrier.dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
        src_stage = VK_PIPELINE_STAGE_TRANSFER_BIT;
        dst_stage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
    } else {
        barrier.srcAccessMask = 0;
        barrier.dstAccessMask = 0;
        src_stage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
        dst_stage = VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT;
    }

    vkCmdPipelineBarrier(cmd, src_stage, dst_stage, 0,
                         0, NULL, 0, NULL, 1, &barrier);

    vkEndCommandBuffer(cmd);

    VkSubmitInfo submit_info = {
        .sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
        .commandBufferCount = 1,
        .pCommandBuffers = &cmd,
    };

    vkQueueSubmit(r->graphics_queue, 1, &submit_info, VK_NULL_HANDLE);
    vkQueueWaitIdle(r->graphics_queue);

    vkFreeCommandBuffers(r->device, r->command_pool, 1, &cmd);
    return true;
}

static bool copy_buffer_to_image(gpu_renderer_t *r, VkBuffer buffer,
    VkImage image, unsigned width, unsigned height) {

    VkCommandBuffer cmd;
    VkCommandBufferAllocateInfo alloc_info = {
        .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,
        .commandPool = r->command_pool,
        .level = VK_COMMAND_BUFFER_LEVEL_PRIMARY,
        .commandBufferCount = 1,
    };
    VK_CHECK(vkAllocateCommandBuffers(r->device, &alloc_info, &cmd));

    VkCommandBufferBeginInfo begin_info = {
        .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
        .flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT,
    };
    vkBeginCommandBuffer(cmd, &begin_info);

    VkBufferImageCopy region = {
        .bufferOffset = 0,
        .bufferRowLength = 0,
        .bufferImageHeight = 0,
        .imageSubresource = {
            .aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
            .mipLevel = 0,
            .baseArrayLayer = 0,
            .layerCount = 1,
        },
        .imageOffset = { 0, 0, 0 },
        .imageExtent = { width, height, 1 },
    };

    vkCmdCopyBufferToImage(cmd, buffer, image,
                           VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &region);

    vkEndCommandBuffer(cmd);

    VkSubmitInfo submit_info = {
        .sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
        .commandBufferCount = 1,
        .pCommandBuffers = &cmd,
    };

    vkQueueSubmit(r->graphics_queue, 1, &submit_info, VK_NULL_HANDLE);
    vkQueueWaitIdle(r->graphics_queue);

    vkFreeCommandBuffers(r->device, r->command_pool, 1, &cmd);
    return true;
}

/* ===== Offscreen rendering support ===== */

static bool create_offscreen_render_pass(gpu_renderer_t *r) {
    VkAttachmentDescription color_attachment = {
        .format = VK_FORMAT_B8G8R8A8_UNORM,
        .samples = VK_SAMPLE_COUNT_1_BIT,
        .loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR,
        .storeOp = VK_ATTACHMENT_STORE_OP_STORE,
        .stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE,
        .stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE,
        .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED,
        .finalLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
    };

    VkAttachmentReference color_ref = {
        .attachment = 0,
        .layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
    };

    VkSubpassDescription subpass = {
        .pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS,
        .colorAttachmentCount = 1,
        .pColorAttachments = &color_ref,
    };

    VkSubpassDependency dependency = {
        .srcSubpass = VK_SUBPASS_EXTERNAL,
        .dstSubpass = 0,
        .srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
        .srcAccessMask = 0,
        .dstStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
        .dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
    };

    VkRenderPassCreateInfo create_info = {
        .sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO,
        .attachmentCount = 1,
        .pAttachments = &color_attachment,
        .subpassCount = 1,
        .pSubpasses = &subpass,
        .dependencyCount = 1,
        .pDependencies = &dependency,
    };

    VK_CHECK(vkCreateRenderPass(r->device, &create_info, NULL, &r->offscreen_render_pass));
    /* Use the offscreen render pass as the primary render pass for pipeline creation */
    r->render_pass = r->offscreen_render_pass;
    return true;
}

static bool create_offscreen_target(gpu_renderer_t *r, int width, int height) {
    /* Create the offscreen image (BGRA8 for direct readback) */
    VkImageCreateInfo image_info = {
        .sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO,
        .imageType = VK_IMAGE_TYPE_2D,
        .format = VK_FORMAT_B8G8R8A8_UNORM,
        .extent = { (uint32_t)width, (uint32_t)height, 1 },
        .mipLevels = 1,
        .arrayLayers = 1,
        .samples = VK_SAMPLE_COUNT_1_BIT,
        .tiling = VK_IMAGE_TILING_OPTIMAL,
        .usage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT,
        .sharingMode = VK_SHARING_MODE_EXCLUSIVE,
        .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED,
    };

    VK_CHECK(vkCreateImage(r->device, &image_info, NULL, &r->offscreen_image));

    VkMemoryRequirements mem_reqs;
    vkGetImageMemoryRequirements(r->device, r->offscreen_image, &mem_reqs);

    VkMemoryAllocateInfo alloc_info = {
        .sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,
        .allocationSize = mem_reqs.size,
        .memoryTypeIndex = find_memory_type(r, mem_reqs.memoryTypeBits,
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT),
    };

    VK_CHECK(vkAllocateMemory(r->device, &alloc_info, NULL, &r->offscreen_image_memory));
    VK_CHECK(vkBindImageMemory(r->device, r->offscreen_image, r->offscreen_image_memory, 0));

    /* Create image view */
    VkImageViewCreateInfo view_info = {
        .sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO,
        .image = r->offscreen_image,
        .viewType = VK_IMAGE_VIEW_TYPE_2D,
        .format = VK_FORMAT_B8G8R8A8_UNORM,
        .subresourceRange = {
            .aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
            .baseMipLevel = 0,
            .levelCount = 1,
            .baseArrayLayer = 0,
            .layerCount = 1,
        },
    };
    VK_CHECK(vkCreateImageView(r->device, &view_info, NULL, &r->offscreen_image_view));

    /* Create framebuffer */
    VkFramebufferCreateInfo fb_info = {
        .sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO,
        .renderPass = r->offscreen_render_pass,
        .attachmentCount = 1,
        .pAttachments = &r->offscreen_image_view,
        .width = (uint32_t)width,
        .height = (uint32_t)height,
        .layers = 1,
    };
    VK_CHECK(vkCreateFramebuffer(r->device, &fb_info, NULL, &r->offscreen_framebuffer));

    VK_LOGI("Offscreen target created: %dx%d", width, height);
    return true;
}

static bool create_readback_buffer(gpu_renderer_t *r, VkDeviceSize size) {
    VkBufferCreateInfo buffer_info = {
        .sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO,
        .size = size,
        .usage = VK_BUFFER_USAGE_TRANSFER_DST_BIT,
        .sharingMode = VK_SHARING_MODE_EXCLUSIVE,
    };

    VK_CHECK(vkCreateBuffer(r->device, &buffer_info, NULL, &r->readback_buffer));

    VkMemoryRequirements mem_reqs;
    vkGetBufferMemoryRequirements(r->device, r->readback_buffer, &mem_reqs);

    VkMemoryAllocateInfo alloc_info = {
        .sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,
        .allocationSize = mem_reqs.size,
        .memoryTypeIndex = find_memory_type(r, mem_reqs.memoryTypeBits,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT),
    };

    VK_CHECK(vkAllocateMemory(r->device, &alloc_info, NULL, &r->readback_memory));
    VK_CHECK(vkBindBufferMemory(r->device, r->readback_buffer, r->readback_memory, 0));
    VK_CHECK(vkMapMemory(r->device, r->readback_memory, 0, size, 0, &r->readback_mapped));

    r->readback_size = size;
    return true;
}

static void cleanup_offscreen(gpu_renderer_t *r) {
    if (!r->device) return;

    if (r->offscreen_framebuffer) {
        vkDestroyFramebuffer(r->device, r->offscreen_framebuffer, NULL);
        r->offscreen_framebuffer = VK_NULL_HANDLE;
    }
    if (r->offscreen_image_view) {
        vkDestroyImageView(r->device, r->offscreen_image_view, NULL);
        r->offscreen_image_view = VK_NULL_HANDLE;
    }
    if (r->offscreen_image) {
        vkDestroyImage(r->device, r->offscreen_image, NULL);
        r->offscreen_image = VK_NULL_HANDLE;
    }
    if (r->offscreen_image_memory) {
        vkFreeMemory(r->device, r->offscreen_image_memory, NULL);
        r->offscreen_image_memory = VK_NULL_HANDLE;
    }
    if (r->offscreen_render_pass && r->offscreen_render_pass != r->render_pass) {
        vkDestroyRenderPass(r->device, r->offscreen_render_pass, NULL);
    }
    r->offscreen_render_pass = VK_NULL_HANDLE;
    if (r->readback_buffer) {
        vkDestroyBuffer(r->device, r->readback_buffer, NULL);
        r->readback_buffer = VK_NULL_HANDLE;
    }
    if (r->readback_memory) {
        vkUnmapMemory(r->device, r->readback_memory);
        vkFreeMemory(r->device, r->readback_memory, NULL);
        r->readback_memory = VK_NULL_HANDLE;
        r->readback_mapped = NULL;
    }
    if (r->offscreen_fence) {
        vkDestroyFence(r->device, r->offscreen_fence, NULL);
        r->offscreen_fence = VK_NULL_HANDLE;
    }
}
