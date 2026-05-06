/*
 * Spela libretro bridge - Audio subsystem.
 *
 * Collects audio samples from the core and buffers them
 * for the Kotlin layer to read and output via platform audio APIs.
 *
 * Includes a native SINC resampler for high-quality sample rate conversion
 * (e.g. 32029 Hz → 48000 Hz) with NEON SIMD on ARM64.
 *
 * Threading (#1044):
 *   Writer = emulation thread (libretro core → audio_sample[_batch]_callback).
 *   Reader = Kotlin audio coroutine (JNI → audio_lock → audio_get_buffer +
 *            copy + audio_clear_buffer → audio_unlock).
 *   Without sync the reader saw torn `write_pos` reads (visibility) and
 *   missed-increment races (writer added samples between get_buffer and
 *   clear_buffer, those samples got dropped). The mutex protects every
 *   read/write of `audio_state` from both sides; the JNI bridge holds it
 *   across the get-frames + memcpy + clear triple so a writer interleaving
 *   in the middle is impossible.
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

    /* #1044: cross-thread state guard. See file-level comment. */
    sp_mutex_t lock;
    bool       lock_initialized;
} audio_state = {0};

void audio_init(double sample_rate) {
    /* Destroy previous resampler if any */
    if (audio_state.resampler) {
        sinc_resampler_destroy(audio_state.resampler);
    }

    /* Preserve the lock across re-init: callers may re-init between games
     * without a deinit, and zeroing the mutex would corrupt it. The lock
     * is created lazily on the first audio_init() call. */
    bool had_lock = audio_state.lock_initialized;
    sp_mutex_t saved_lock;
    if (had_lock) {
        saved_lock = audio_state.lock;
    }

    memset(&audio_state, 0, sizeof(audio_state));

    if (had_lock) {
        audio_state.lock = saved_lock;
        audio_state.lock_initialized = true;
    } else {
        if (sp_mutex_init(&audio_state.lock) == 0) {
            audio_state.lock_initialized = true;
        }
    }

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
    if (audio_state.lock_initialized) {
        sp_mutex_lock(&audio_state.lock);
    }
    if (audio_state.resampler) {
        sinc_resampler_destroy(audio_state.resampler);
        audio_state.resampler = NULL;
    }
    audio_state.initialized = false;
    audio_state.write_pos = 0;
    audio_state.resampled_frames = 0;
    if (audio_state.lock_initialized) {
        sp_mutex_unlock(&audio_state.lock);
        sp_mutex_destroy(&audio_state.lock);
        audio_state.lock_initialized = false;
    }
}

/* #1044: lock primitives exposed for the JNI bridge so it can hold the
 * audio mutex across the read-frames + copy + clear-buffer triple, which
 * would otherwise race with the emulation thread's `audio_sample_callback`.
 *
 * Public callers MUST pair these (lock → … → unlock) and MUST NOT call any
 * audio_*() function that takes the lock itself while holding it (no
 * recursion: pthread_mutex defaults to non-recursive on Linux/macOS). The
 * `_unlocked` variants are provided for use inside a held lock. */
void audio_lock(void) {
    if (audio_state.lock_initialized) {
        sp_mutex_lock(&audio_state.lock);
    }
}

void audio_unlock(void) {
    if (audio_state.lock_initialized) {
        sp_mutex_unlock(&audio_state.lock);
    }
}

/* Called by core for single stereo sample */
void audio_sample_callback(int16_t left, int16_t right) {
    audio_lock();
    if (audio_state.initialized && audio_state.write_pos < AUDIO_BUFFER_MAX_FRAMES) {
        size_t idx = audio_state.write_pos * 2;
        audio_state.buffer[idx]     = left;
        audio_state.buffer[idx + 1] = right;
        audio_state.write_pos++;
    }
    audio_unlock();
}

/* Called by core for batch of stereo samples */
size_t audio_sample_batch_callback(const int16_t *data, size_t frames) {
    if (!data) return 0;
    audio_lock();
    size_t to_write = 0;
    if (audio_state.initialized) {
        size_t available = AUDIO_BUFFER_MAX_FRAMES - audio_state.write_pos;
        to_write = frames < available ? frames : available;

        if (to_write > 0) {
            memcpy(
                &audio_state.buffer[audio_state.write_pos * 2],
                data,
                to_write * 2 * sizeof(int16_t)
            );
            audio_state.write_pos += to_write;
        }
    }
    audio_unlock();
    return to_write;
}

/* Buffer accessors. The lock-free variants assume the caller already
 * holds audio_lock — see the file-level comment on the read+copy+clear
 * pairing the JNI bridge needs. Existing public symbols are kept as
 * thin self-locking wrappers for callers that don't need the triple. */
const int16_t *audio_get_buffer(void) {
    /* Pointer is stable for the lifetime of audio_state — buffer is
     * an embedded array, never realloc'd. Safe to return without
     * locking; the caller is expected to hold audio_lock before reading
     * from the buffer through the returned pointer. */
    return audio_state.buffer;
}

size_t audio_get_buffer_frames(void) {
    audio_lock();
    size_t frames = audio_state.write_pos;
    audio_unlock();
    return frames;
}

void audio_clear_buffer(void) {
    audio_lock();
    audio_state.write_pos = 0;
    audio_unlock();
}

/* Caller-locked variants for use when the lock is already held (e.g. the
 * JNI bridge taking the lock once around the get-frames + memcpy + clear
 * triple). Naming mirrors the gpu_renderer pattern of `_unlocked` helpers. */
size_t audio_get_buffer_frames_unlocked(void) {
    return audio_state.write_pos;
}

void audio_clear_buffer_unlocked(void) {
    audio_state.write_pos = 0;
}

/* --- SINC resampler integration --- */

size_t audio_resample(double ratio) {
    audio_lock();
    size_t out_frames = 0;
    if (audio_state.resampler && audio_state.write_pos > 0) {
        out_frames = sinc_resampler_process(
            audio_state.resampler,
            audio_state.buffer,
            audio_state.write_pos,
            audio_state.resampled_buffer,
            RESAMPLED_BUFFER_MAX_FRAMES,
            ratio
        );
        audio_state.resampled_frames = out_frames;
        audio_state.write_pos = 0; /* consumed */
    } else {
        audio_state.resampled_frames = 0;
    }
    audio_unlock();
    return out_frames;
}

const int16_t *audio_get_resampled_buffer(void) {
    /* Like audio_get_buffer: the buffer is an embedded array, never
     * relocated. Caller must hold audio_lock to read its contents. */
    return audio_state.resampled_buffer;
}

size_t audio_get_resampled_frames(void) {
    audio_lock();
    size_t frames = audio_state.resampled_frames;
    audio_unlock();
    return frames;
}

void audio_resampler_reset(void) {
    audio_lock();
    if (audio_state.resampler) {
        sinc_resampler_reset(audio_state.resampler);
    }
    audio_state.resampled_frames = 0;
    audio_unlock();
}
