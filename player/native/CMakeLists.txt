cmake_minimum_required(VERSION 3.22.1)
project(spela-libretro C)

set(CMAKE_C_STANDARD 11)

add_library(spela-libretro SHARED
    src/libretro_bridge.c
    src/libretro_audio.c
    src/libretro_video.c
    src/libretro_input.c
)

target_include_directories(spela-libretro PRIVATE
    src
)

# Link platform libraries
if(ANDROID)
    find_library(log-lib log)
    find_library(android-lib android)
    find_library(GLESv2-lib GLESv2)
    find_library(OpenSLES-lib OpenSLES)
    target_link_libraries(spela-libretro
        ${log-lib}
        ${android-lib}
        ${GLESv2-lib}
        ${OpenSLES-lib}
        dl
    )
else()
    # Desktop (JNI)
    find_package(JNI REQUIRED)
    target_include_directories(spela-libretro PRIVATE ${JNI_INCLUDE_DIRS})
    if(APPLE)
        # macOS: dlopen/dlsym are in libSystem, no separate libdl needed
        target_link_libraries(spela-libretro ${JNI_LIBRARIES})
    else()
        target_link_libraries(spela-libretro ${JNI_LIBRARIES} dl)
    endif()
endif()
