/*
 * Spela libretro bridge - Video subsystem.
 *
 * Receives frame buffers from the core via the video_refresh callback,
 * stores them for the Kotlin layer to read and render to a surface.
 */

#include "libretro_bridge.h"

#include <stdlib.h>
#include <string.h>

static struct {
    void    *frame_buffer;
    size_t   frame_buffer_size;
    unsigned width;
    unsigned height;
    size_t   pitch;
    unsigned pixel_format;  /* RETRO_PIXEL_FORMAT_* */
} video_state = {0};

void video_init(void) {
    video_state.frame_buffer = NULL;
    video_state.frame_buffer_size = 0;
    video_state.width = 0;
    video_state.height = 0;
    video_state.pitch = 0;
    video_state.pixel_format = RETRO_PIXEL_FORMAT_0RGB1555; /* default */
}

void video_deinit(void) {
    if (video_state.frame_buffer) {
        free(video_state.frame_buffer);
        video_state.frame_buffer = NULL;
    }
    video_state.frame_buffer_size = 0;
    video_state.width = 0;
    video_state.height = 0;
}

void video_set_pixel_format(unsigned format) {
    video_state.pixel_format = format;
}

/*
 * Called by the core each frame with the rendered framebuffer.
 * We copy the data into our own buffer so Kotlin can read it safely.
 *
 * data = NULL means the frame is a duplicate (software framebuffer unchanged).
 */
void video_refresh_callback(const void *data, unsigned width, unsigned height, size_t pitch) {
    if (!data) return;

    video_state.width = width;
    video_state.height = height;
    video_state.pitch = pitch;

    /* Calculate bytes per pixel based on format */
    unsigned bpp;
    switch (video_state.pixel_format) {
        case RETRO_PIXEL_FORMAT_XRGB8888:
            bpp = 4;
            break;
        case RETRO_PIXEL_FORMAT_RGB565:
        case RETRO_PIXEL_FORMAT_0RGB1555:
        default:
            bpp = 2;
            break;
    }

    /* Store as tightly-packed pixel data (no padding per row) */
    size_t needed = (size_t)width * height * bpp;

    if (needed != video_state.frame_buffer_size) {
        void *new_buf = realloc(video_state.frame_buffer, needed);
        if (!new_buf) return;
        video_state.frame_buffer = new_buf;
        video_state.frame_buffer_size = needed;
    }

    /* Copy row-by-row in case pitch != width * bpp */
    const uint8_t *src = (const uint8_t *)data;
    uint8_t *dst = (uint8_t *)video_state.frame_buffer;
    size_t row_bytes = (size_t)width * bpp;

    for (unsigned y = 0; y < height; y++) {
        memcpy(dst, src, row_bytes);
        src += pitch;
        dst += row_bytes;
    }
}

unsigned video_get_width(void) {
    return video_state.width;
}

unsigned video_get_height(void) {
    return video_state.height;
}

const void *video_get_frame_buffer(void) {
    return video_state.frame_buffer;
}

size_t video_get_frame_buffer_size(void) {
    return video_state.frame_buffer_size;
}

unsigned video_get_pixel_format(void) {
    return video_state.pixel_format;
}
