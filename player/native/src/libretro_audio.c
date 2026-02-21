/*
 * Spela libretro bridge - Audio subsystem.
 *
 * Collects audio samples from the core and buffers them
 * for the Kotlin layer to read and output via platform audio APIs.
 */

#include "libretro_bridge.h"

#include <stdlib.h>
#include <string.h>

/* Audio sample buffer. Stereo interleaved int16.
 * Sized for ~5 frames at 48kHz/60fps (~85ms). With audio-sync pacing
 * the buffer is drained every retro_run(), so this is ample headroom. */
#define AUDIO_BUFFER_MAX_FRAMES 4096

static struct {
    int16_t  buffer[AUDIO_BUFFER_MAX_FRAMES * 2]; /* stereo */
    size_t   write_pos;  /* in frames */
    double   sample_rate;
    bool     initialized;
} audio_state = {0};

void audio_init(double sample_rate) {
    memset(&audio_state, 0, sizeof(audio_state));
    audio_state.sample_rate = sample_rate;
    audio_state.initialized = true;
}

void audio_deinit(void) {
    audio_state.initialized = false;
    audio_state.write_pos = 0;
}

/* Called by core for single stereo sample */
void audio_sample_callback(int16_t left, int16_t right) {
    if (!audio_state.initialized) return;
    if (audio_state.write_pos >= AUDIO_BUFFER_MAX_FRAMES) return;

    size_t idx = audio_state.write_pos * 2;
    audio_state.buffer[idx]     = left;
    audio_state.buffer[idx + 1] = right;
    audio_state.write_pos++;
}

/* Called by core for batch of stereo samples */
size_t audio_sample_batch_callback(const int16_t *data, size_t frames) {
    if (!audio_state.initialized || !data) return 0;

    size_t available = AUDIO_BUFFER_MAX_FRAMES - audio_state.write_pos;
    size_t to_write = frames < available ? frames : available;

    if (to_write > 0) {
        memcpy(
            &audio_state.buffer[audio_state.write_pos * 2],
            data,
            to_write * 2 * sizeof(int16_t)
        );
        audio_state.write_pos += to_write;
    }

    return to_write;
}

const int16_t *audio_get_buffer(void) {
    return audio_state.buffer;
}

size_t audio_get_buffer_frames(void) {
    return audio_state.write_pos;
}

void audio_clear_buffer(void) {
    audio_state.write_pos = 0;
}
