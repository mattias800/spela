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
    rcheevos/src/rc_client_stub.c
)

# Platform-specific GPU renderer sources
if(ANDROID)
    list(APPEND COMMON_SOURCES src/gpu_renderer_vulkan.c)
elseif(APPLE)
    # macOS: Metal renderer (Objective-C)
    enable_language(OBJC)
    list(APPEND COMMON_SOURCES src/gpu_renderer_metal.m)
else()
    # Linux/Windows: Vulkan renderer + desktop surface creation
    list(APPEND COMMON_SOURCES
        src/gpu_renderer_vulkan.c
        src/gpu_renderer_vulkan_desktop.c
    )
endif()

add_library(spela-libretro SHARED ${COMMON_SOURCES})

target_include_directories(spela-libretro PRIVATE
    src
    rcheevos/include
)

# Link platform libraries
if(ANDROID)
    find_library(log-lib log)
    find_library(android-lib android)
    find_library(GLESv2-lib GLESv2)
    find_library(OpenSLES-lib OpenSLES)
    find_library(vulkan-lib vulkan)
    target_link_libraries(spela-libretro
        ${log-lib}
        ${android-lib}
        ${GLESv2-lib}
        ${OpenSLES-lib}
        ${vulkan-lib}
        dl
    )
else()
    # Desktop (JNI)
    find_package(JNI REQUIRED)
    target_include_directories(spela-libretro PRIVATE ${JNI_INCLUDE_DIRS})
    if(APPLE)
        # macOS: Metal + QuartzCore frameworks, no separate libdl needed
        find_library(METAL_FRAMEWORK Metal)
        find_library(METALKIT_FRAMEWORK MetalKit)
        find_library(QUARTZCORE_FRAMEWORK QuartzCore)
        find_library(COCOA_FRAMEWORK Cocoa)
        target_link_libraries(spela-libretro
            ${JNI_LIBRARIES}
            ${METAL_FRAMEWORK}
            ${METALKIT_FRAMEWORK}
            ${QUARTZCORE_FRAMEWORK}
            ${COCOA_FRAMEWORK}
        )
        # Set Objective-C ARC for Metal renderer
        set_source_files_properties(src/gpu_renderer_metal.m PROPERTIES
            COMPILE_FLAGS "-fobjc-arc"
        )
    else()
        # Linux/Windows: Vulkan
        find_package(Vulkan REQUIRED)
        target_include_directories(spela-libretro PRIVATE ${Vulkan_INCLUDE_DIRS})
        target_link_libraries(spela-libretro
            ${JNI_LIBRARIES}
            ${Vulkan_LIBRARIES}
            dl
        )
    endif()
endif()

# Shader compilation custom commands
if(ANDROID OR (NOT APPLE))
    # SPIR-V shader compilation for Vulkan platforms
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

if(APPLE)
    # Metal shader compilation for macOS
    set(SHADER_DIR "${CMAKE_CURRENT_SOURCE_DIR}/shaders")
    set(METALLIB_OUTPUT "${CMAKE_CURRENT_SOURCE_DIR}/src/shaders.metallib")

    find_program(XCRUN xcrun)
    if(XCRUN)
        add_custom_command(
            OUTPUT ${METALLIB_OUTPUT}
            COMMAND bash ${SHADER_DIR}/compile_metal.sh
            DEPENDS
                ${SHADER_DIR}/passthrough.metal
                ${SHADER_DIR}/bilinear.metal
                ${SHADER_DIR}/sharp_bilinear.metal
                ${SHADER_DIR}/crt_simple.metal
                ${SHADER_DIR}/lcd_grid.metal
                ${SHADER_DIR}/scanlines.metal
            COMMENT "Compiling Metal shaders to metallib"
        )
        add_custom_target(compile_metal_shaders DEPENDS ${METALLIB_OUTPUT})
        add_dependencies(spela-libretro compile_metal_shaders)
    else()
        message(STATUS "xcrun not found -- Metal shaders will be compiled at runtime")
    endif()
endif()
