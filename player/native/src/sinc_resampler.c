/*
 * Windowed SINC resampler — Kaiser-windowed sinc, polyphase implementation.
 *
 * Design matches RetroArch's audio resampler:
 *   - Kaiser window (beta=5.0) for excellent stopband attenuation
 *   - 8 taps, 256 subphases → 16 KB polyphase table (coefficients + deltas)
 *   - Linear interpolation between adjacent phases for smooth transitions
 *   - NEON SIMD on ARM64, scalar fallback elsewhere
 *
 * The resampler operates on stereo interleaved int16 samples. Internally
 * it works in float32 for filter precision, converting at I/O boundaries.
 */

#include "sinc_resampler.h"

#include <stdlib.h>
#include <string.h>
#include <math.h>

/* Portable 16-byte aligned allocation.
 * aligned_alloc requires C11 and isn't available on older Android NDK targets.
 * posix_memalign is POSIX-only; Windows uses _aligned_malloc instead. */
static void *alloc_aligned(size_t alignment, size_t size) {
#ifdef _WIN32
    return _aligned_malloc(size, alignment);
#else
    void *ptr = NULL;
    if (posix_memalign(&ptr, alignment, size) != 0) return NULL;
    return ptr;
#endif
}

static void free_aligned(void *ptr) {
#ifdef _WIN32
    _aligned_free(ptr);
#else
    free(ptr);
#endif
}

/* NEON detection — restrict to AArch64 where vaddvq_f32 is available.
 * ARMv7 NEON lacks horizontal sum intrinsics, so we use the scalar path. */
#if defined(__aarch64__)
#define SINC_USE_NEON 1
#include <arm_neon.h>
#else
#define SINC_USE_NEON 0
#endif

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

struct sinc_resampler {
    int channels;
    int taps;
    int subphases;

    /* Polyphase coefficient table: coeffs[subphase * taps + tap]
     * and delta table for linear interpolation between phases. */
    float *coeffs;      /* [subphases * taps] */
    float *deltas;      /* [subphases * taps] */

    /* Ring buffer of recent input samples (float, de-interleaved per channel).
     * history_l[0..taps-1] and history_r[0..taps-1]. */
    float *history_l;
    float *history_r;
    int hist_pos;       /* write cursor (next position to write) */

    /* Fractional input position carried across calls. */
    double frac_pos;
};

/* Modified Bessel function of the first kind, order 0 (I0).
 * Used for the Kaiser window. Series expansion is sufficient
 * for beta values up to ~20. */
static double bessel_i0(double x) {
    double sum = 1.0;
    double term = 1.0;
    double xh = x * 0.5;
    for (int k = 1; k < 20; k++) {
        term *= (xh / k) * (xh / k);
        sum += term;
        if (term < sum * 1e-12) break;
    }
    return sum;
}

/* Precompute polyphase coefficient table with Kaiser-windowed sinc. */
static void compute_coefficients(sinc_resampler_t *r) {
    const double beta = 5.0;
    const double inv_i0_beta = 1.0 / bessel_i0(beta);
    const int taps = r->taps;
    const int subphases = r->subphases;
    const double half_taps = taps * 0.5;

    for (int phase = 0; phase < subphases; phase++) {
        double frac = (double)phase / subphases;
        double sum = 0.0;

        /* Compute raw windowed sinc coefficients */
        for (int t = 0; t < taps; t++) {
            double n = t - half_taps + 1.0 + frac;
            double sinc_val;
            if (fabs(n) < 1e-9) {
                sinc_val = 1.0;
            } else {
                sinc_val = sin(M_PI * n) / (M_PI * n);
            }

            /* Kaiser window */
            double win_arg = (2.0 * (t + frac) / taps) - 1.0;
            double win_val = bessel_i0(beta * sqrt(1.0 - win_arg * win_arg)) * inv_i0_beta;

            double coeff = sinc_val * win_val;
            r->coeffs[phase * taps + t] = (float)coeff;
            sum += coeff;
        }

        /* Normalize so coefficients sum to 1.0 (unity gain) */
        if (sum != 0.0) {
            float inv_sum = (float)(1.0 / sum);
            for (int t = 0; t < taps; t++) {
                r->coeffs[phase * taps + t] *= inv_sum;
            }
        }
    }

    /* Compute delta table: delta[phase] = coeffs[phase+1] - coeffs[phase]
     * for linear interpolation between adjacent phases.
     * Last phase wraps to phase 0 (identical for normalized filters). */
    for (int phase = 0; phase < subphases; phase++) {
        int next_phase = (phase + 1) % subphases;
        for (int t = 0; t < taps; t++) {
            r->deltas[phase * taps + t] =
                r->coeffs[next_phase * taps + t] - r->coeffs[phase * taps + t];
        }
    }
}

sinc_resampler_t *sinc_resampler_create(int channels, int taps, int subphases) {
    sinc_resampler_t *r = (sinc_resampler_t *)calloc(1, sizeof(sinc_resampler_t));
    if (!r) return NULL;

    r->channels = channels;
    r->taps = taps;
    r->subphases = subphases;

    /* Allocate coefficient tables (16-byte aligned for NEON) */
    size_t table_size = (size_t)(subphases * taps) * sizeof(float);
    r->coeffs = (float *)alloc_aligned(16, (table_size + 15) & ~(size_t)15);
    r->deltas = (float *)alloc_aligned(16, (table_size + 15) & ~(size_t)15);

    if (!r->coeffs || !r->deltas) {
        sinc_resampler_destroy(r);
        return NULL;
    }

    compute_coefficients(r);

    /* Allocate history ring buffers (16-byte aligned) */
    size_t hist_bytes = (size_t)taps * sizeof(float);
    hist_bytes = (hist_bytes + 15) & ~(size_t)15;
    r->history_l = (float *)alloc_aligned(16, hist_bytes);
    r->history_r = (float *)alloc_aligned(16, hist_bytes);

    if (!r->history_l || !r->history_r) {
        sinc_resampler_destroy(r);
        return NULL;
    }

    memset(r->history_l, 0, hist_bytes);
    memset(r->history_r, 0, hist_bytes);
    r->hist_pos = 0;
    r->frac_pos = 0.0;

    return r;
}

void sinc_resampler_destroy(sinc_resampler_t *r) {
    if (!r) return;
    free_aligned(r->coeffs);
    free_aligned(r->deltas);
    free_aligned(r->history_l);
    free_aligned(r->history_r);
    free(r);  /* r itself is calloc'd, not alloc_aligned */
}

void sinc_resampler_reset(sinc_resampler_t *r) {
    if (!r) return;
    size_t hist_bytes = (size_t)r->taps * sizeof(float);
    memset(r->history_l, 0, hist_bytes);
    memset(r->history_r, 0, hist_bytes);
    r->hist_pos = 0;
    r->frac_pos = 0.0;
}

/* Convolve history buffer with filter coefficients (with delta interpolation).
 * Returns filtered sample for one channel. */
static inline float convolve_scalar(const float *history, int hist_pos,
                                    int taps, const float *coeffs,
                                    const float *deltas, float delta_frac) {
    float sum = 0.0f;
    for (int t = 0; t < taps; t++) {
        int idx = (hist_pos + t) % taps;
        float c = coeffs[t] + deltas[t] * delta_frac;
        sum += history[idx] * c;
    }
    return sum;
}

#if SINC_USE_NEON
/* NEON-optimized convolution for 8 taps (2 iterations of 4). */
static inline float convolve_neon(const float *history, int hist_pos,
                                  int taps, const float *coeffs,
                                  const float *deltas, float delta_frac) {
    /* For 8-tap filter with aligned history, we can process 4 at a time.
     * We need to handle the ring buffer wrap by extracting contiguous data. */
    float aligned_hist[8] __attribute__((aligned(16)));
    for (int t = 0; t < taps; t++) {
        aligned_hist[t] = history[(hist_pos + t) % taps];
    }

    float32x4_t vfrac = vdupq_n_f32(delta_frac);
    float32x4_t vsum = vdupq_n_f32(0.0f);

    /* Process taps in groups of 4 */
    for (int t = 0; t < taps; t += 4) {
        float32x4_t vh = vld1q_f32(&aligned_hist[t]);
        float32x4_t vc = vld1q_f32(&coeffs[t]);
        float32x4_t vd = vld1q_f32(&deltas[t]);
        /* c + delta * frac */
        float32x4_t vcoeff = vmlaq_f32(vc, vd, vfrac);
        vsum = vmlaq_f32(vsum, vh, vcoeff);
    }

    /* Horizontal sum */
    return vaddvq_f32(vsum);
}
#endif

/* Clamp float to int16 range */
static inline short clamp_s16(float x) {
    if (x > 32767.0f) return 32767;
    if (x < -32768.0f) return -32768;
    return (short)(x + (x < 0 ? -0.5f : 0.5f));
}

size_t sinc_resampler_process(sinc_resampler_t *r,
                              const short *input, size_t input_frames,
                              short *output, size_t max_output_frames,
                              double ratio) {
    if (!r || !input || !output || input_frames == 0 || ratio <= 0.0)
        return 0;

    const int taps = r->taps;
    const int subphases = r->subphases;
    const double input_step = 1.0 / ratio;
    size_t out_count = 0;

    /* Feed input samples into history and generate output */
    size_t in_consumed = 0;
    double pos = r->frac_pos;

    while (out_count < max_output_frames) {
        /* Feed input samples into history until we have enough for the current output position */
        while (in_consumed <= (size_t)pos && in_consumed < input_frames) {
            size_t idx = in_consumed * 2; /* stereo interleaved */
            r->history_l[r->hist_pos] = (float)input[idx];
            r->history_r[r->hist_pos] = (float)input[idx + 1];
            r->hist_pos = (r->hist_pos + 1) % taps;
            in_consumed++;
        }

        /* Check if we've consumed all input */
        if (pos >= (double)input_frames) break;

        /* Compute phase and delta fraction for sub-phase interpolation */
        double int_pos;
        double frac = modf(pos, &int_pos);
        double phase_exact = frac * subphases;
        int phase = (int)phase_exact;
        float delta_frac = (float)(phase_exact - phase);
        if (phase >= subphases) { phase = subphases - 1; delta_frac = 0.0f; }

        const float *c = &r->coeffs[phase * taps];
        const float *d = &r->deltas[phase * taps];

        /* The history read position: we want to read the taps samples ending
         * at the current input position. hist_pos points to where the NEXT
         * sample will be written, so the oldest sample in the buffer is at
         * hist_pos (it's a ring buffer of size taps). */
        int read_pos = r->hist_pos; /* oldest sample */

        float out_l, out_r;
#if SINC_USE_NEON
        if (taps == 8) {
            out_l = convolve_neon(r->history_l, read_pos, taps, c, d, delta_frac);
            out_r = convolve_neon(r->history_r, read_pos, taps, c, d, delta_frac);
        } else {
            out_l = convolve_scalar(r->history_l, read_pos, taps, c, d, delta_frac);
            out_r = convolve_scalar(r->history_r, read_pos, taps, c, d, delta_frac);
        }
#else
        out_l = convolve_scalar(r->history_l, read_pos, taps, c, d, delta_frac);
        out_r = convolve_scalar(r->history_r, read_pos, taps, c, d, delta_frac);
#endif

        output[out_count * 2]     = clamp_s16(out_l);
        output[out_count * 2 + 1] = clamp_s16(out_r);
        out_count++;

        pos += input_step;
    }

    /* Consume any remaining input into history */
    while (in_consumed < input_frames) {
        size_t idx = in_consumed * 2;
        r->history_l[r->hist_pos] = (float)input[idx];
        r->history_r[r->hist_pos] = (float)input[idx + 1];
        r->hist_pos = (r->hist_pos + 1) % taps;
        in_consumed++;
    }

    /* Carry fractional position to next call */
    r->frac_pos = pos - (double)input_frames;

    return out_count;
}
