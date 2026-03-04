/*
 * Spela libretro bridge - Audio subsystem.
 *
 * Collects audio samples from the core and buffers them
 * for the Kotlin layer to read and output via platform audio APIs.
 *
 * Includes a native SINC resampler for high-quality sample rate conversion
 * (e.g. 32029 Hz → 48000 Hz) with NEON SIMD on ARM64.
 */

#include "libretro_bridge.h"
#include "sinc_resampler.h"

#include <stdlib.h>
#include <string.h>
#include <stdio.h>

/* Audio sample buffer. Stereo interleaved int16.
 * Sized for ~5 frames at 48kHz/60fps (~85ms). With audio-sync pacing
 * the buffer is drained every retro_run(), so this is ample headroom. */
#define AUDIO_BUFFER_MAX_FRAMES 4096

/* Resampled output buffer. At ratio ~1.5 (48000/32029), 4096 input frames
 * produce ~6144 output frames. 8192 gives comfortable headroom. */
#define RESAMPLED_BUFFER_MAX_FRAMES 8192

static struct {
    int16_t  buffer[AUDIO_BUFFER_MAX_FRAMES * 2]; /* stereo */
    size_t   write_pos;  /* in frames */
    double   sample_rate;
    bool     initialized;

    /* SINC resampler */
    sinc_resampler_t *resampler;
    int16_t  resampled_buffer[RESAMPLED_BUFFER_MAX_FRAMES * 2]; /* stereo */
    size_t   resampled_frames;
} audio_state = {0};

void audio_init(double sample_rate) {
    /* Destroy previous resampler if any */
    if (audio_state.resampler) {
        sinc_resampler_destroy(audio_state.resampler);
    }

    memset(&audio_state, 0, sizeof(audio_state));
    audio_state.sample_rate = sample_rate;
    audio_state.initialized = true;

    /* Create SINC resampler: stereo, 8 taps, 256 subphases */
    audio_state.resampler = sinc_resampler_create(2, 8, 256);
#if defined(__aarch64__)
    printf("[SpelaAudio] SINC resampler created (8 taps, 256 subphases, NEON)\n");
#else
    printf("[SpelaAudio] SINC resampler created (8 taps, 256 subphases, scalar)\n");
#endif
}

void audio_deinit(void) {
    if (audio_state.resampler) {
        sinc_resampler_destroy(audio_state.resampler);
        audio_state.resampler = NULL;
    }
    audio_state.initialized = false;
    audio_state.write_pos = 0;
    audio_state.resampled_frames = 0;
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

/* --- SINC resampler integration --- */

size_t audio_resample(double ratio) {
    if (!audio_state.resampler || audio_state.write_pos == 0) {
        audio_state.resampled_frames = 0;
        return 0;
    }

    size_t out_frames = sinc_resampler_process(
        audio_state.resampler,
        audio_state.buffer,
        audio_state.write_pos,
        audio_state.resampled_buffer,
        RESAMPLED_BUFFER_MAX_FRAMES,
        ratio
    );

    audio_state.resampled_frames = out_frames;
    audio_state.write_pos = 0; /* consumed */
    return out_frames;
}

const int16_t *audio_get_resampled_buffer(void) {
    return audio_state.resampled_buffer;
}

size_t audio_get_resampled_frames(void) {
    return audio_state.resampled_frames;
}

void audio_resampler_reset(void) {
    if (audio_state.resampler) {
        sinc_resampler_reset(audio_state.resampler);
    }
    audio_state.resampled_frames = 0;
}
