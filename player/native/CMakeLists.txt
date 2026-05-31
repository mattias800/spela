cmake_minimum_required(VERSION 3.22.1)
project(spela-libretro C)

set(CMAKE_C_STANDARD 11)

# Common source files
set(COMMON_SOURCES
    src/libretro_bridge.c
    src/libretro_audio.c
    src/libretro_video.c
    src/libretro_input.c
    src/libretro_achievements.c
    src/sinc_resampler.c
    rcheevos/src/rc_client_stub.c
)

# Platform-specific GPU renderer sources
if(ANDROID)
    list(APPEND COMMON_SOURCES
        src/gpu_renderer_vulkan.c
        src/hw_render_gl_android.c
    )
elseif(APPLE)
    # macOS: Vulkan via MoltenVK + Obj-C surface creation + OpenGL HW render
    enable_language(OBJC)
    list(APPEND COMMON_SOURCES
        src/gpu_renderer_vulkan.c
        src/gpu_renderer_vulkan_macos.m
        src/hw_render_gl_macos.m
    )
else()
    # Linux/Windows: Vulkan renderer + desktop surface creation + OpenGL HW render
    list(APPEND COMMON_SOURCES
        src/gpu_renderer_vulkan.c
        src/gpu_renderer_vulkan_desktop.c
        src/hw_render_gl_desktop.c
    )
endif()

# SDL2 gamepad support (desktop only)
if(NOT ANDROID)
    list(APPEND COMMON_SOURCES src/gamepad_sdl2.c)
    find_package(SDL2 QUIET)
    if(SDL2_FOUND)
        add_compile_definitions(HAS_SDL2)
    endif()
endif()

add_library(spela-libretro SHARED ${COMMON_SOURCES})

# Optional Wayland header detection (Linux only, for Vulkan surface creation)
if(UNIX AND NOT APPLE AND NOT ANDROID)
    find_package(PkgConfig QUIET)
    if(PkgConfig_FOUND)
        pkg_check_modules(WAYLAND_CLIENT QUIET wayland-client)
        if(WAYLAND_CLIENT_FOUND)
            message(STATUS "Wayland support enabled")
            target_compile_definitions(spela-libretro PRIVATE HAS_WAYLAND)
            target_include_directories(spela-libretro PRIVATE ${WAYLAND_CLIENT_INCLUDE_DIRS})
        else()
            message(STATUS "Wayland support disabled (wayland-client headers not found)")
        endif()
    endif()
endif()

target_include_directories(spela-libretro PRIVATE
    src
    rcheevos/include
)

# Link platform libraries
if(ANDROID)
    find_library(log-lib log)
    find_library(android-lib android)
    find_library(GLESv2-lib GLESv2)
    find_library(GLESv3-lib GLESv3)
    find_library(EGL-lib EGL)
    find_library(OpenSLES-lib OpenSLES)
    find_library(vulkan-lib vulkan)
    target_link_libraries(spela-libretro
        ${log-lib}
        ${android-lib}
        ${GLESv2-lib}
        ${GLESv3-lib}
        ${EGL-lib}
        ${OpenSLES-lib}
        ${vulkan-lib}
        dl
    )
else()
    # Desktop (JNI)
    find_package(JNI REQUIRED)
    target_include_directories(spela-libretro PRIVATE ${JNI_INCLUDE_DIRS})
    # SDL2 include/link (optional, for gamepad support)
    if(SDL2_FOUND)
        target_include_directories(spela-libretro PRIVATE ${SDL2_INCLUDE_DIRS})
        target_link_libraries(spela-libretro ${SDL2_LIBRARIES})
    endif()

    if(APPLE)
        # macOS: Vulkan via MoltenVK (linked directly, bypasses Vulkan loader ICD).
        # MoltenVK exports all Vulkan entry points, so no VK_ICD_FILENAMES needed.
        find_package(Vulkan REQUIRED)
        find_library(MOLTENVK_LIB MoltenVK HINTS /opt/homebrew/lib /usr/local/lib)
        find_library(METAL_FRAMEWORK Metal)
        find_library(QUARTZCORE_FRAMEWORK QuartzCore)
        find_library(COCOA_FRAMEWORK Cocoa)
        find_library(OPENGL_FRAMEWORK OpenGL)
        find_library(IOKIT_FRAMEWORK IOKit)
        find_library(IOSURFACE_FRAMEWORK IOSurface)
        target_include_directories(spela-libretro PRIVATE ${Vulkan_INCLUDE_DIRS})
        if(MOLTENVK_LIB)
            message(STATUS "Using MoltenVK directly: ${MOLTENVK_LIB}")
            target_link_libraries(spela-libretro
                ${JNI_LIBRARIES}
                ${MOLTENVK_LIB}
                ${METAL_FRAMEWORK}
                ${QUARTZCORE_FRAMEWORK}
                ${COCOA_FRAMEWORK}
                ${OPENGL_FRAMEWORK}
                ${IOKIT_FRAMEWORK}
                ${IOSURFACE_FRAMEWORK}
            )
        else()
            message(STATUS "MoltenVK not found, falling back to Vulkan loader")
            target_link_libraries(spela-libretro
                ${JNI_LIBRARIES}
                ${Vulkan_LIBRARIES}
                ${METAL_FRAMEWORK}
                ${QUARTZCORE_FRAMEWORK}
                ${COCOA_FRAMEWORK}
                ${OPENGL_FRAMEWORK}
            )
        endif()
        # Set Objective-C ARC for .m files
        set_source_files_properties(src/gpu_renderer_vulkan_macos.m PROPERTIES
            COMPILE_FLAGS "-fobjc-arc"
        )
        set_source_files_properties(src/hw_render_gl_macos.m PROPERTIES
            COMPILE_FLAGS "-fobjc-arc -DGL_SILENCE_DEPRECATION"
        )
    else()
        # Linux/Windows: Vulkan + OpenGL HW render (GL/EGL loaded dynamically)
        find_package(Vulkan REQUIRED)
        target_include_directories(spela-libretro PRIVATE ${Vulkan_INCLUDE_DIRS})
        if(UNIX)
            # Linux: Vulkan + dl (GL and EGL loaded at runtime via dlopen)
            target_link_libraries(spela-libretro
                ${JNI_LIBRARIES}
                ${Vulkan_LIBRARIES}
                dl
                m
            )
        else()
            # Windows: Vulkan + opengl32 (for wglGetProcAddress)
            target_link_libraries(spela-libretro
                ${JNI_LIBRARIES}
                ${Vulkan_LIBRARIES}
                opengl32
            )
        endif()
    endif()
endif()

# Shader compilation custom commands — all platforms now use Vulkan (macOS via MoltenVK)
if(TRUE)
    set(SHADER_DIR "${CMAKE_CURRENT_SOURCE_DIR}/shaders")
    set(SPIRV_HEADER "${CMAKE_CURRENT_SOURCE_DIR}/src/gpu_shaders_spirv.h")

    # Only run if compile_shaders.sh exists and glslangValidator is available
    find_program(GLSLANG_VALIDATOR glslangValidator)
    if(GLSLANG_VALIDATOR)
        add_custom_command(
            OUTPUT ${SPIRV_HEADER}
            COMMAND bash ${SHADER_DIR}/compile_shaders.sh
            DEPENDS
                ${SHADER_DIR}/fullscreen_quad.vert
                ${SHADER_DIR}/passthrough.frag
                ${SHADER_DIR}/bilinear.frag
                ${SHADER_DIR}/sharp_bilinear.frag
                ${SHADER_DIR}/crt_simple.frag
                ${SHADER_DIR}/lcd_grid.frag
                ${SHADER_DIR}/scanlines.frag
            COMMENT "Compiling GLSL shaders to SPIR-V"
        )
        add_custom_target(compile_shaders DEPENDS ${SPIRV_HEADER})
        add_dependencies(spela-libretro compile_shaders)
    else()
        message(STATUS "glslangValidator not found -- using placeholder SPIR-V header")
    endif()
endif()

# Note: Metal shaders are compiled from inline MSL strings at runtime
# (see gpu_renderer_metal.m). The .metal files in shaders/ are reference
# copies but are not pre-compiled into a .metallib.

# =====================================================================
# Out-of-process core host (desktop only) — debug/isolation harness.
#
# A standalone native executable that loads + runs a libretro core in its
# own process (via the sp_host_* API in libretro_bridge.c), sharing video/
# audio/input with a parent over a memory-mapped file. It gives a core a
# clean native address space (like RetroArch) and full symbols (PDB), which
# makes it a fast repro for diagnosing core/integration crashes — it's how
# the #1243 GET_SENSOR_INTERFACE bug was pinned. See player/native/CORE_HOST.md.
#
# Reuses COMMON_SOURCES with SPELA_CORE_HOST defined, which compiles OUT the
# JNI bindings — so the host links NO jvm/jawt libraries and stays JVM-free.
# jni.h/jawt.h are needed only as headers (for the JavaVM* type, unused at
# runtime), so we add JNI_INCLUDE_DIRS but do NOT link JNI_LIBRARIES.
# =====================================================================
if(NOT ANDROID)
    add_executable(spela-core-host ${COMMON_SOURCES} src/spela_core_host.c)
    target_compile_definitions(spela-core-host PRIVATE SPELA_CORE_HOST)
    target_include_directories(spela-core-host PRIVATE src rcheevos/include)

    find_package(JNI REQUIRED)
    target_include_directories(spela-core-host PRIVATE ${JNI_INCLUDE_DIRS})

    if(SDL2_FOUND)
        target_include_directories(spela-core-host PRIVATE ${SDL2_INCLUDE_DIRS})
        target_link_libraries(spela-core-host ${SDL2_LIBRARIES})
    endif()

    find_package(Vulkan REQUIRED)
    target_include_directories(spela-core-host PRIVATE ${Vulkan_INCLUDE_DIRS})

    if(APPLE)
        find_library(MOLTENVK_LIB MoltenVK HINTS /opt/homebrew/lib /usr/local/lib)
        find_library(METAL_FRAMEWORK Metal)
        find_library(QUARTZCORE_FRAMEWORK QuartzCore)
        find_library(COCOA_FRAMEWORK Cocoa)
        find_library(OPENGL_FRAMEWORK OpenGL)
        find_library(IOKIT_FRAMEWORK IOKit)
        find_library(IOSURFACE_FRAMEWORK IOSurface)
        if(MOLTENVK_LIB)
            target_link_libraries(spela-core-host ${MOLTENVK_LIB}
                ${METAL_FRAMEWORK} ${QUARTZCORE_FRAMEWORK} ${COCOA_FRAMEWORK}
                ${OPENGL_FRAMEWORK} ${IOKIT_FRAMEWORK} ${IOSURFACE_FRAMEWORK})
        else()
            target_link_libraries(spela-core-host ${Vulkan_LIBRARIES}
                ${METAL_FRAMEWORK} ${QUARTZCORE_FRAMEWORK} ${COCOA_FRAMEWORK}
                ${OPENGL_FRAMEWORK})
        endif()
    elseif(UNIX)
        target_link_libraries(spela-core-host ${Vulkan_LIBRARIES} dl m)
    else()
        target_link_libraries(spela-core-host ${Vulkan_LIBRARIES} opengl32)
    endif()

    if(TARGET compile_shaders)
        add_dependencies(spela-core-host compile_shaders)
    endif()
endif()
