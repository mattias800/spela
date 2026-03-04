/*
 * Windowed SINC resampler with NEON SIMD optimization.
 *
 * Kaiser-windowed sinc filter (beta=5.0, 8 taps, 256 subphases) matching
 * RetroArch's audio quality. NEON intrinsics on ARM64, scalar fallback
 * on x86/armeabi-v7a.
 *
 * Usage:
 *   sinc_resampler_t *r = sinc_resampler_create(2, 8, 256);
 *   size_t out = sinc_resampler_process(r, in, in_frames, out, max_out, ratio);
 *   sinc_resampler_destroy(r);
 */

#ifndef SINC_RESAMPLER_H
#define SINC_RESAMPLER_H

#include <stddef.h>

typedef struct sinc_resampler sinc_resampler_t;

/* Allocate resampler and precompute polyphase coefficient table.
 * channels: 1 (mono) or 2 (stereo)
 * taps:     filter length (must be even, typically 8)
 * subphases: phase table resolution (typically 256) */
sinc_resampler_t *sinc_resampler_create(int channels, int taps, int subphases);

/* Free resampler and all internal buffers. */
void sinc_resampler_destroy(sinc_resampler_t *r);

/* Reset internal state (history buffer, fractional position).
 * Call on seek or discontinuity. */
void sinc_resampler_reset(sinc_resampler_t *r);

/* Resample input_frames of interleaved int16 audio to output buffer.
 * ratio = output_rate / input_rate (e.g. 48000/32029 ≈ 1.499).
 * Returns number of output frames written.
 * Carries fractional position across calls for seamless boundaries. */
size_t sinc_resampler_process(sinc_resampler_t *r,
                              const short *input, size_t input_frames,
                              short *output, size_t max_output_frames,
                              double ratio);

#endif /* SINC_RESAMPLER_H */
