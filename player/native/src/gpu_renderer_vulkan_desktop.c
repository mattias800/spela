/*
 * Vulkan Desktop Surface Creation.
 *
 * Desktop-specific Vulkan surface creation from JAWT window handles.
 * Linux: VK_KHR_xlib_surface from JAWT X11 handle
 * Windows: VK_KHR_win32_surface from JAWT HWND
 *
 * The actual Vulkan rendering is shared with gpu_renderer_vulkan.c.
 * This file only provides the platform-specific surface creation function
 * that is called from gpu_renderer_init_surface() on desktop platforms.
 */

#include "gpu_renderer.h"

/* Only compile on non-Android, non-Apple desktop */
#if !defined(__ANDROID__) && !defined(__APPLE__)

#include <vulkan/vulkan.h>
#include <stdio.h>
#include <stdlib.h>

#ifdef _WIN32
#define VK_USE_PLATFORM_WIN32_KHR
#include <vulkan/vulkan_win32.h>
#include <windows.h>
#elif defined(__linux__)

/* X11 — always available */
#define VK_USE_PLATFORM_XLIB_KHR
#include <vulkan/vulkan_xlib.h>
#include <X11/Xlib.h>

/* Wayland — optional, only if headers were found at build time */
#ifdef HAS_WAYLAND
#define VK_USE_PLATFORM_WAYLAND_KHR
#include <vulkan/vulkan_wayland.h>
#include <wayland-client.h>
#endif

#endif

#define VK_LOGI(...) do { fprintf(stdout, "[VulkanDesktop] "); fprintf(stdout, __VA_ARGS__); fprintf(stdout, "\n"); } while(0)
#define VK_LOGE(...) do { fprintf(stderr, "[VulkanDesktop ERROR] "); fprintf(stderr, __VA_ARGS__); fprintf(stderr, "\n"); } while(0)

/*
 * Creates a Vulkan surface from a desktop native window handle.
 *
 * On Linux (X11/Wayland): native_handle contains the display and window/surface pointers.
 * On Windows: native_handle is an HWND.
 *
 * This function is called by gpu_renderer_vulkan.c when running on desktop.
 * It returns the created VkSurfaceKHR, or VK_NULL_HANDLE on failure.
 */

#ifdef _WIN32

typedef struct {
    HWND hwnd;
    HINSTANCE hinstance;
} desktop_window_info_t;

VkSurfaceKHR vulkan_desktop_create_surface(VkInstance instance, void *native_handle) {
    if (!native_handle) {
        VK_LOGE("NULL native handle");
        return VK_NULL_HANDLE;
    }

    desktop_window_info_t *info = (desktop_window_info_t *)native_handle;

    VkWin32SurfaceCreateInfoKHR create_info = {
        .sType = VK_STRUCTURE_TYPE_WIN32_SURFACE_CREATE_INFO_KHR,
        .hinstance = info->hinstance ? info->hinstance : GetModuleHandle(NULL),
        .hwnd = info->hwnd,
    };

    VkSurfaceKHR surface;
    VkResult result = vkCreateWin32SurfaceKHR(instance, &create_info, NULL, &surface);
    if (result != VK_SUCCESS) {
        VK_LOGE("vkCreateWin32SurfaceKHR failed: %d", result);
        return VK_NULL_HANDLE;
    }

    VK_LOGI("Win32 Vulkan surface created");
    return surface;
}

#elif defined(__linux__)

static bool is_wayland_session(void) {
#ifdef HAS_WAYLAND
    const char *wayland_display = getenv("WAYLAND_DISPLAY");
    return wayland_display != NULL && wayland_display[0] != '\0';
#else
    return false;
#endif
}

const char *vulkan_desktop_get_surface_extension(void) {
#ifdef HAS_WAYLAND
    if (is_wayland_session()) {
        return VK_KHR_WAYLAND_SURFACE_EXTENSION_NAME;
    }
#endif
    return VK_KHR_XLIB_SURFACE_EXTENSION_NAME;
}

/* X11 surface info */
typedef struct {
    Display *display;
    unsigned long window; /* X11 Window (unsigned long) */
} desktop_window_info_x11_t;

#ifdef HAS_WAYLAND
/* Wayland surface info */
typedef struct {
    struct wl_display *display;
    struct wl_surface *surface;
} desktop_window_info_wayland_t;
#endif

VkSurfaceKHR vulkan_desktop_create_surface(VkInstance instance, void *native_handle) {
    if (!native_handle) {
        VK_LOGE("NULL native handle");
        return VK_NULL_HANDLE;
    }

#ifdef HAS_WAYLAND
    if (is_wayland_session()) {
        desktop_window_info_wayland_t *info = (desktop_window_info_wayland_t *)native_handle;

        VkWaylandSurfaceCreateInfoKHR create_info = {
            .sType = VK_STRUCTURE_TYPE_WAYLAND_SURFACE_CREATE_INFO_KHR,
            .display = info->display,
            .surface = info->surface,
        };

        VkSurfaceKHR surface;
        VkResult result = vkCreateWaylandSurfaceKHR(instance, &create_info, NULL, &surface);
        if (result != VK_SUCCESS) {
            VK_LOGE("vkCreateWaylandSurfaceKHR failed: %d", result);
            return VK_NULL_HANDLE;
        }

        VK_LOGI("Wayland Vulkan surface created");
        return surface;
    }
#endif

    /* X11 fallback */
    desktop_window_info_x11_t *info = (desktop_window_info_x11_t *)native_handle;

    VkXlibSurfaceCreateInfoKHR create_info = {
        .sType = VK_STRUCTURE_TYPE_XLIB_SURFACE_CREATE_INFO_KHR,
        .dpy = info->display,
        .window = (Window)info->window,
    };

    VkSurfaceKHR surface;
    VkResult result = vkCreateXlibSurfaceKHR(instance, &create_info, NULL, &surface);
    if (result != VK_SUCCESS) {
        VK_LOGE("vkCreateXlibSurfaceKHR failed: %d", result);
        return VK_NULL_HANDLE;
    }

    VK_LOGI("X11 Vulkan surface created");
    return surface;
}

#endif /* platform */

#endif /* !__ANDROID__ && !__APPLE__ */
