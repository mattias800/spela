/*
 * Vulkan macOS Surface Creation (via MoltenVK).
 *
 * Creates a VkSurfaceKHR from a JAWT CAMetalLayer using VK_EXT_metal_surface.
 * This is the macOS counterpart of gpu_renderer_vulkan_desktop.c (Linux/Windows).
 */

#include "gpu_renderer.h"

#ifdef __APPLE__

#define VK_USE_PLATFORM_METAL_EXT
#include <vulkan/vulkan.h>
#include <vulkan/vulkan_metal.h>

#import <Metal/Metal.h>
#import <QuartzCore/QuartzCore.h>
#import <AppKit/AppKit.h>

#include <jawt.h>
#include <jawt_md.h>
#include <stdio.h>

#define VK_MAC_LOGI(...) do { fprintf(stdout, "[VulkanMacOS] "); fprintf(stdout, __VA_ARGS__); fprintf(stdout, "\n"); fflush(stdout); } while(0)
#define VK_MAC_LOGE(...) do { fprintf(stderr, "[VulkanMacOS ERROR] "); fprintf(stderr, __VA_ARGS__); fprintf(stderr, "\n"); fflush(stderr); } while(0)

const char *vulkan_desktop_get_surface_extension(void) {
    return VK_EXT_METAL_SURFACE_EXTENSION_NAME;
}

VkSurfaceKHR vulkan_desktop_create_surface(VkInstance instance, void *native_handle) {
    if (!native_handle) {
        VK_MAC_LOGE("NULL native handle");
        return VK_NULL_HANDLE;
    }

    id<JAWT_SurfaceLayers> surfaceLayers = (__bridge id<JAWT_SurfaceLayers>)native_handle;

    CAMetalLayer *metalLayer = [CAMetalLayer layer];
    metalLayer.device = MTLCreateSystemDefaultDevice();
    metalLayer.pixelFormat = MTLPixelFormatBGRA8Unorm;
    metalLayer.framebufferOnly = YES;

    CGFloat scale = [[NSScreen mainScreen] backingScaleFactor];
    if (scale < 1.0) scale = 1.0;
    metalLayer.contentsScale = scale;
    metalLayer.displaySyncEnabled = YES;
    metalLayer.zPosition = 1000;

    surfaceLayers.layer = metalLayer;

    CALayer *windowLayer = surfaceLayers.windowLayer;
    if (windowLayer) {
        [windowLayer addSublayer:metalLayer];
        VK_MAC_LOGI("Metal layer added as sublayer of windowLayer (scale=%.1f)", scale);
    }

    VkMetalSurfaceCreateInfoEXT create_info = {0};
    create_info.sType = VK_STRUCTURE_TYPE_METAL_SURFACE_CREATE_INFO_EXT;
    create_info.pLayer = metalLayer;

    VkSurfaceKHR surface;
    VkResult result = vkCreateMetalSurfaceEXT(instance, &create_info, NULL, &surface);
    if (result != VK_SUCCESS) {
        VK_MAC_LOGE("vkCreateMetalSurfaceEXT failed: %d", result);
        return VK_NULL_HANDLE;
    }

    VK_MAC_LOGI("Vulkan surface created via MoltenVK (device: %s)",
                [[metalLayer.device name] UTF8String]);
    return surface;
}

#endif /* __APPLE__ */
