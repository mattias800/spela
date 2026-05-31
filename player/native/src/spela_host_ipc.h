/*
 * spela_host_ipc.h — shared-memory layout between the Spela JVM and the
 * out-of-process core host (spela_core_host). Backed by a memory-mapped file
 * so the JVM (FileChannel.map) and the native host (mmap / MapViewOfFile)
 * share the same region. See #1243.
 *
 * The Kotlin side (DesktopCoreHostController) mirrors these byte offsets — keep
 * the two in sync. Layout: a fixed 256-byte header, then the BGRA video buffer,
 * then the interleaved-stereo-int16 audio buffer.
 */
#ifndef SPELA_HOST_IPC_H
#define SPELA_HOST_IPC_H

#include <stdint.h>

#define SP_IPC_MAGIC       0x53504831u   /* 'SPH1' */
#define SP_IPC_ABI         1u

#define SP_HDR_SIZE        256u
#define SP_VIDEO_CAP       (16u * 1024u * 1024u)  /* up to ~2048x2048 BGRA */
#define SP_AUDIO_CAP_BYTES (256u * 1024u)         /* >> one frame of stereo s16 */

#define SP_VIDEO_OFFSET    SP_HDR_SIZE
#define SP_AUDIO_OFFSET    (SP_VIDEO_OFFSET + SP_VIDEO_CAP)
#define SP_TOTAL_SIZE      (SP_AUDIO_OFFSET + SP_AUDIO_CAP_BYTES)

/* status values */
#define SP_STATUS_STARTING 0u
#define SP_STATUS_RUNNING  1u
#define SP_STATUS_ERROR    2u
#define SP_STATUS_EXITED   3u

/* Header — must stay within SP_HDR_SIZE bytes. All fields little-endian.
 * Producer/consumer noted per field. Synchronization is via frame_counter
 * (host increments after writing a frame; JVM reads when it changes). */
typedef struct {
    uint32_t magic;          /* host writes SP_IPC_MAGIC once ready */
    uint32_t abi;            /* SP_IPC_ABI */
    uint32_t status;         /* SP_STATUS_* (host) */
    uint32_t should_stop;    /* JVM -> host: set 1 to request shutdown */
    uint32_t paused;         /* JVM -> host: set 1 to pause retro_run */

    uint64_t frame_counter;  /* host: ++ after each produced frame */
    uint32_t video_width;    /* host */
    uint32_t video_height;   /* host */
    uint32_t video_format;   /* host: 0 = BGRA8888 */
    uint32_t video_bytes;    /* host: bytes valid in the video buffer */
    uint32_t audio_frames;   /* host: stereo frames in the audio buffer this tick */

    double   target_fps;     /* host */
    double   sample_rate;    /* host */
    float    aspect_ratio;   /* host */

    /* input: JVM -> host (port 0) */
    uint32_t input_buttons;  /* bitmask of (1 << RETRO_DEVICE_ID_JOYPAD_*) */
    int16_t  analog_lx, analog_ly, analog_rx, analog_ry;
    int32_t  pointer_x, pointer_y;
    uint32_t pointer_pressed;
} SpHostHeader;

#endif /* SPELA_HOST_IPC_H */
