/*
 * Metal GPU renderer for macOS.
 *
 * Implements the gpu_renderer interface using Apple Metal.
 * Software-rendered cores upload frames via shared/managed MTLBuffer.
 * MSL shaders are compiled at runtime from embedded source strings.
 *
 * Threading model:
 *   - gpu_renderer_upload_frame() called from emulation thread
 *   - gpu_renderer_render() called from emulation thread (after upload)
 *   - All Metal commands are single-threaded (emulation thread owns the context)
 */

#include "gpu_renderer.h"

#import <Metal/Metal.h>
#import <QuartzCore/QuartzCore.h>
#import <AppKit/AppKit.h>
#import <dispatch/dispatch.h>
#include <jawt.h>
#include <jawt_md.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

#define MTL_LOGI(...) do { fprintf(stdout, "[Metal] "); fprintf(stdout, __VA_ARGS__); fprintf(stdout, "\n"); fflush(stdout); } while(0)
#define MTL_LOGW(...) do { fprintf(stderr, "[Metal WARN] "); fprintf(stderr, __VA_ARGS__); fprintf(stderr, "\n"); fflush(stderr); } while(0)
#define MTL_LOGE(...) do { fprintf(stderr, "[Metal ERROR] "); fprintf(stderr, __VA_ARGS__); fprintf(stderr, "\n"); fflush(stderr); } while(0)

#define MAX_FRAMES_IN_FLIGHT 3
#define NUM_SHADERS 6

/* Push constant data matching the Vulkan renderer */
typedef struct {
    float texture_size[2];
} push_constants_t;

/* ===== Embedded MSL shader sources ===== */

static const char *msl_vertex_source =
    "#include <metal_stdlib>\n"
    "using namespace metal;\n"
    "\n"
    "struct VertexOut {\n"
    "    float4 position [[position]];\n"
    "    float2 uv;\n"
    "};\n"
    "\n"
    "vertex VertexOut vertex_main(uint vertexID [[vertex_id]]) {\n"
    "    VertexOut out;\n"
    "    float2 uv = float2((vertexID << 1) & 2, vertexID & 2);\n"
    "    out.position = float4(uv * 2.0 - 1.0, 0.0, 1.0);\n"
    "    out.uv = float2(uv.x, 1.0 - uv.y);\n"
    "    return out;\n"
    "}\n";

static const char *msl_passthrough_source =
    "#include <metal_stdlib>\n"
    "using namespace metal;\n"
    "\n"
    "struct VertexOut {\n"
    "    float4 position [[position]];\n"
    "    float2 uv;\n"
    "};\n"
    "\n"
    "fragment float4 fragment_passthrough(VertexOut in [[stage_in]],\n"
    "                                      texture2d<float> tex [[texture(0)]],\n"
    "                                      sampler smp [[sampler(0)]]) {\n"
    "    return tex.sample(smp, in.uv);\n"
    "}\n";

static const char *msl_bilinear_source =
    "#include <metal_stdlib>\n"
    "using namespace metal;\n"
    "\n"
    "struct VertexOut {\n"
    "    float4 position [[position]];\n"
    "    float2 uv;\n"
    "};\n"
    "\n"
    "fragment float4 fragment_bilinear(VertexOut in [[stage_in]],\n"
    "                                   texture2d<float> tex [[texture(0)]],\n"
    "                                   sampler smp [[sampler(0)]]) {\n"
    "    return tex.sample(smp, in.uv);\n"
    "}\n";

static const char *msl_sharp_bilinear_source =
    "#include <metal_stdlib>\n"
    "using namespace metal;\n"
    "\n"
    "struct VertexOut {\n"
    "    float4 position [[position]];\n"
    "    float2 uv;\n"
    "};\n"
    "\n"
    "struct PushConstants {\n"
    "    float2 texture_size;\n"
    "};\n"
    "\n"
    "fragment float4 fragment_sharp_bilinear(VertexOut in [[stage_in]],\n"
    "                                         texture2d<float> tex [[texture(0)]],\n"
    "                                         sampler smp [[sampler(0)]],\n"
    "                                         constant PushConstants &pc [[buffer(0)]]) {\n"
    "    float2 texel = in.uv * pc.texture_size;\n"
    "    float2 texel_floor = floor(texel);\n"
    "    float2 s = fract(texel);\n"
    "\n"
    "    float2 tex_size = float2(tex.get_width(), tex.get_height());\n"
    "    float2 region_range = float2(0.5) - float2(0.5) / (pc.texture_size / tex_size);\n"
    "    float2 center_dist = s - 0.5;\n"
    "    float2 f = (center_dist - clamp(center_dist, -region_range, region_range)) * 2.0 + 0.5;\n"
    "\n"
    "    float2 mod_texel = texel_floor + f;\n"
    "    return tex.sample(smp, mod_texel / pc.texture_size);\n"
    "}\n";

static const char *msl_crt_simple_source =
    "#include <metal_stdlib>\n"
    "using namespace metal;\n"
    "\n"
    "struct VertexOut {\n"
    "    float4 position [[position]];\n"
    "    float2 uv;\n"
    "};\n"
    "\n"
    "struct PushConstants {\n"
    "    float2 texture_size;\n"
    "};\n"
    "\n"
    "fragment float4 fragment_crt_simple(VertexOut in [[stage_in]],\n"
    "                                     texture2d<float> tex [[texture(0)]],\n"
    "                                     sampler smp [[sampler(0)]],\n"
    "                                     constant PushConstants &pc [[buffer(0)]]) {\n"
    "    float4 color = tex.sample(smp, in.uv);\n"
    "\n"
    "    float scanline = floor(in.uv.y * pc.texture_size.y);\n"
    "    float scanline_mask = 1.0 - 0.35 * fmod(scanline, 2.0);\n"
    "    color.rgb *= scanline_mask;\n"
    "\n"
    "    float2 center = in.uv - 0.5;\n"
    "    float dist = length(center) * 1.414;\n"
    "    float vignette = 1.0 - dist * dist * 0.5;\n"
    "    color.rgb *= clamp(vignette, 0.0f, 1.0f);\n"
    "\n"
    "    return color;\n"
    "}\n";

static const char *msl_scanlines_source =
    "#include <metal_stdlib>\n"
    "using namespace metal;\n"
    "\n"
    "struct VertexOut {\n"
    "    float4 position [[position]];\n"
    "    float2 uv;\n"
    "};\n"
    "\n"
    "struct PushConstants {\n"
    "    float2 texture_size;\n"
    "};\n"
    "\n"
    "fragment float4 fragment_scanlines(VertexOut in [[stage_in]],\n"
    "                                    texture2d<float> tex [[texture(0)]],\n"
    "                                    sampler smp [[sampler(0)]],\n"
    "                                    constant PushConstants &pc [[buffer(0)]]) {\n"
    "    float4 color = tex.sample(smp, in.uv);\n"
    "\n"
    "    float scanline = floor(in.uv.y * pc.texture_size.y);\n"
    "    float scanline_mask = 1.0 - 0.25 * fmod(scanline, 2.0);\n"
    "    color.rgb *= scanline_mask;\n"
    "\n"
    "    return color;\n"
    "}\n";

static const char *msl_lcd_grid_source =
    "#include <metal_stdlib>\n"
    "using namespace metal;\n"
    "\n"
    "struct VertexOut {\n"
    "    float4 position [[position]];\n"
    "    float2 uv;\n"
    "};\n"
    "\n"
    "struct PushConstants {\n"
    "    float2 texture_size;\n"
    "};\n"
    "\n"
    "fragment float4 fragment_lcd_grid(VertexOut in [[stage_in]],\n"
    "                                   texture2d<float> tex [[texture(0)]],\n"
    "                                   sampler smp [[sampler(0)]],\n"
    "                                   constant PushConstants &pc [[buffer(0)]]) {\n"
    "    float4 color = tex.sample(smp, in.uv);\n"
    "\n"
    "    float2 grid_pos = fract(in.uv * pc.texture_size);\n"
    "\n"
    "    float h_line = smoothstep(0.0, 0.05, grid_pos.y) * smoothstep(1.0, 0.95, grid_pos.y);\n"
    "    float v_line = smoothstep(0.0, 0.05, grid_pos.x) * smoothstep(1.0, 0.95, grid_pos.x);\n"
    "\n"
    "    float grid_mask = mix(0.75f, 1.0f, h_line * v_line);\n"
    "    color.rgb *= grid_mask;\n"
    "\n"
    "    return color;\n"
    "}\n";

/* Fragment function names corresponding to each shader index */
static const char *fragment_function_names[NUM_SHADERS] = {
    "fragment_passthrough",
    "fragment_bilinear",
    "fragment_sharp_bilinear",
    "fragment_crt_simple",
    "fragment_scanlines",
    "fragment_lcd_grid",
};

/* ===== Renderer struct ===== */

struct gpu_renderer {
    int backend;
    bool active;
    bool surface_initialized;

    /* Metal core objects */
    id<MTLDevice> device;
    id<MTLCommandQueue> command_queue;
    CAMetalLayer *metal_layer;

    /* Render pipeline states -- one per shader */
    id<MTLRenderPipelineState> pipelines[NUM_SHADERS];
    int current_shader;

    /* Compiled shader libraries */
    id<MTLLibrary> vertex_library;
    id<MTLLibrary> fragment_libraries[NUM_SHADERS];

    /* Game texture (uploaded from CPU) */
    id<MTLTexture> game_texture;
    unsigned game_texture_width;
    unsigned game_texture_height;
    MTLPixelFormat game_texture_format;

    /* Staging buffer for frame upload */
    id<MTLBuffer> staging_buffer;
    NSUInteger staging_size;

    /* Samplers */
    id<MTLSamplerState> sampler_nearest;
    id<MTLSamplerState> sampler_linear;

    /* Push constants buffer */
    id<MTLBuffer> push_constants_buffer;

    /* Synchronization */
    dispatch_semaphore_t frame_semaphore;
    uint32_t current_frame;

    /* Frame state */
    bool frame_uploaded;
    unsigned frame_width;
    unsigned frame_height;
    unsigned frame_pixel_format;

    /* Source rect for DS dual-screen */
    int source_x, source_y, source_w, source_h;
    bool source_rect_set;

    /* Surface dimensions */
    unsigned surface_width;
    unsigned surface_height;

    /* Offscreen rendering (render-to-BGRA readback path) */
    bool offscreen_mode;
    id<MTLTexture> offscreen_texture;
    unsigned offscreen_width;
    unsigned offscreen_height;
};

/* Forward declarations */
static bool create_metal_device(gpu_renderer_t *r);
static bool setup_metal_layer_jawt(gpu_renderer_t *r, id<JAWT_SurfaceLayers> surface_layers);
static bool compile_shader_libraries(gpu_renderer_t *r);
static bool create_pipeline_states(gpu_renderer_t *r);
static bool create_metal_samplers(gpu_renderer_t *r);
static bool create_push_constants_buffer(gpu_renderer_t *r);
static bool create_game_texture_metal(gpu_renderer_t *r, unsigned w, unsigned h, MTLPixelFormat fmt);
static bool ensure_staging_buffer(gpu_renderer_t *r, NSUInteger size);
static bool ensure_offscreen_texture(gpu_renderer_t *r, unsigned w, unsigned h);
static MTLPixelFormat pixel_format_for_retro(unsigned retro_format, unsigned *bpp);
static void convert_0rgb1555_to_bgra(const uint16_t *src, uint8_t *dst,
    unsigned width, unsigned height, size_t src_pitch);

/* ===== Public API ===== */

gpu_renderer_t *gpu_renderer_create(int backend) {
    gpu_renderer_t *r = (gpu_renderer_t *)calloc(1, sizeof(gpu_renderer_t));
    if (!r) return NULL;
    r->backend = backend;
    r->current_shader = GPU_SHADER_NONE;
    return r;
}

void gpu_renderer_destroy(gpu_renderer_t *r) {
    if (!r) return;
    gpu_renderer_deinit_surface(r);
    free(r);
}

bool gpu_renderer_init_surface(gpu_renderer_t *r, void *native_surface) {
    if (!r || r->surface_initialized) return false;

    @autoreleasepool {
        /* native_surface is JAWT platformInfo: an NSObject<JAWT_SurfaceLayers>* on macOS */
        id<JAWT_SurfaceLayers> surface_layers = (__bridge id<JAWT_SurfaceLayers>)native_surface;

        if (!create_metal_device(r)) return false;
        if (!setup_metal_layer_jawt(r, surface_layers)) return false;
        if (!compile_shader_libraries(r)) return false;
        if (!create_pipeline_states(r)) return false;
        if (!create_metal_samplers(r)) return false;
        if (!create_push_constants_buffer(r)) return false;

        /* Create frame semaphore for triple buffering */
        r->frame_semaphore = dispatch_semaphore_create(MAX_FRAMES_IN_FLIGHT);

        r->surface_initialized = true;
        r->active = true;

        /* Initial size will be set via gpu_renderer_resize from JAWT bounds */
        MTL_LOGI("Metal GPU renderer initialized");
    }

    return true;
}

bool gpu_renderer_init_offscreen(gpu_renderer_t *r, int width, int height) {
    if (!r || r->surface_initialized) return false;
    (void)width;
    (void)height;

    @autoreleasepool {
        if (!create_metal_device(r)) return false;
        if (!compile_shader_libraries(r)) return false;
        if (!create_pipeline_states(r)) return false;
        if (!create_metal_samplers(r)) return false;
        if (!create_push_constants_buffer(r)) return false;

        r->frame_semaphore = dispatch_semaphore_create(1);
        r->offscreen_mode = true;
        r->surface_initialized = true;
        r->active = true;

        MTL_LOGI("Metal GPU renderer initialized (offscreen)");
    }

    return true;
}

void gpu_renderer_resize(gpu_renderer_t *r, int width, int height) {
    if (!r || !r->surface_initialized) return;
    if (width <= 0 || height <= 0) return;

    @autoreleasepool {
        /* Width/height are in Java logical points. Scale to backing pixels. */
        CGFloat scale = r->metal_layer.contentsScale;
        if (scale < 1.0) scale = 1.0;
        unsigned pw = (unsigned)(width * scale);
        unsigned ph = (unsigned)(height * scale);

        r->surface_width = pw;
        r->surface_height = ph;

        /* Update the layer frame (in points) and drawable size (in pixels) */
        [CATransaction begin];
        [CATransaction setDisableActions:YES];
        r->metal_layer.frame = CGRectMake(0, 0, width, height);
        r->metal_layer.drawableSize = CGSizeMake(pw, ph);
        [CATransaction commit];

        MTL_LOGI("Surface resize: %dx%d (drawable %ux%u, scale=%.1f)",
                 width, height, pw, ph, scale);
    }
}

void gpu_renderer_deinit_surface(gpu_renderer_t *r) {
    if (!r || !r->surface_initialized) return;

    @autoreleasepool {
        /* Wait for all in-flight frames to complete */
        if (r->frame_semaphore) {
            for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
                dispatch_semaphore_wait(r->frame_semaphore, DISPATCH_TIME_FOREVER);
            }
            for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
                dispatch_semaphore_signal(r->frame_semaphore);
            }
        }

        /* Release Metal objects (ARC handles dealloc) */
        r->offscreen_texture = nil;
        r->offscreen_width = 0;
        r->offscreen_height = 0;
        r->offscreen_mode = false;
        r->game_texture = nil;
        r->staging_buffer = nil;
        r->push_constants_buffer = nil;
        r->sampler_nearest = nil;
        r->sampler_linear = nil;

        for (int i = 0; i < NUM_SHADERS; i++) {
            r->pipelines[i] = nil;
            r->fragment_libraries[i] = nil;
        }
        r->vertex_library = nil;

        r->command_queue = nil;
        r->metal_layer = nil;
        r->device = nil;
        r->frame_semaphore = nil;

        r->game_texture_width = 0;
        r->game_texture_height = 0;
        r->staging_size = 0;
        r->frame_uploaded = false;

        r->surface_initialized = false;
        r->active = false;
    }
}

void gpu_renderer_upload_frame(gpu_renderer_t *r, const void *data,
    unsigned width, unsigned height, size_t pitch, unsigned pixel_format) {
    if (!r || !r->active || !data) return;

    @autoreleasepool {
        unsigned bpp;
        MTLPixelFormat mtl_format = pixel_format_for_retro(pixel_format, &bpp);

        /* 0RGB1555 requires conversion to BGRA8 since Metal has no native 1555 format */
        bool needs_conversion = (pixel_format == RETRO_PIXEL_FORMAT_0RGB1555);
        unsigned upload_bpp = needs_conversion ? 4 : bpp;
        MTLPixelFormat upload_format = needs_conversion ? MTLPixelFormatBGRA8Unorm : mtl_format;

        /* Recreate game texture if dimensions or format changed */
        if (width != r->game_texture_width || height != r->game_texture_height ||
            upload_format != r->game_texture_format) {
            r->game_texture = nil;
            if (!create_game_texture_metal(r, width, height, upload_format)) {
                MTL_LOGE("Failed to create game texture %ux%u", width, height);
                return;
            }
        }

        /* Ensure staging buffer is large enough */
        NSUInteger needed = (NSUInteger)width * height * upload_bpp;
        if (!ensure_staging_buffer(r, needed)) {
            MTL_LOGE("Failed to ensure staging buffer (%lu bytes)", (unsigned long)needed);
            return;
        }

        /* Copy frame data to staging buffer */
        uint8_t *dst = (uint8_t *)[r->staging_buffer contents];

        if (needs_conversion) {
            convert_0rgb1555_to_bgra((const uint16_t *)data, dst, width, height, pitch);
        } else {
            const uint8_t *src = (const uint8_t *)data;
            size_t row_bytes = (size_t)width * bpp;

            if (pitch == row_bytes) {
                memcpy(dst, src, (size_t)width * height * bpp);
            } else {
                for (unsigned y = 0; y < height; y++) {
                    memcpy(dst + y * row_bytes, src + y * pitch, row_bytes);
                }
            }
        }

        /* Blit staging buffer to texture */
        id<MTLCommandBuffer> cmd = [r->command_queue commandBuffer];
        id<MTLBlitCommandEncoder> blit = [cmd blitCommandEncoder];

        NSUInteger bytes_per_row = (NSUInteger)width * upload_bpp;

        [blit copyFromBuffer:r->staging_buffer
                sourceOffset:0
           sourceBytesPerRow:bytes_per_row
         sourceBytesPerImage:bytes_per_row * height
                  sourceSize:MTLSizeMake(width, height, 1)
                   toTexture:r->game_texture
            destinationSlice:0
            destinationLevel:0
           destinationOrigin:MTLOriginMake(0, 0, 0)];

        [blit endEncoding];
        [cmd commit];

        /* In offscreen mode, skip the synchronous wait here.
         * The render_to_bgra() call will wait for all prior commands
         * (same queue guarantees in-order execution).
         * For on-screen mode, wait to ensure the staging buffer is safe to reuse. */
        if (!r->offscreen_mode) {
            [cmd waitUntilCompleted];
        }

        r->frame_uploaded = true;
        r->frame_width = width;
        r->frame_height = height;
        r->frame_pixel_format = pixel_format;
    }
}

void gpu_renderer_set_shader(gpu_renderer_t *r, int shader_id) {
    if (!r) return;
    if (shader_id >= 0 && shader_id < NUM_SHADERS) {
        r->current_shader = shader_id;
    }
}

void gpu_renderer_render(gpu_renderer_t *r) {
    if (!r || !r->active || !r->frame_uploaded) return;
    if (r->offscreen_mode) return; /* Use gpu_renderer_render_to_bgra() instead */

    @autoreleasepool {
        /* Wait for a frame slot */
        dispatch_semaphore_wait(r->frame_semaphore, DISPATCH_TIME_FOREVER);

        id<CAMetalDrawable> drawable = [r->metal_layer nextDrawable];
        if (!drawable) {
            MTL_LOGW("No drawable available");
            dispatch_semaphore_signal(r->frame_semaphore);
            return;
        }

        /* Update push constants */
        push_constants_t pc = {
            .texture_size = { (float)r->frame_width, (float)r->frame_height },
        };
        memcpy([r->push_constants_buffer contents], &pc, sizeof(pc));

        /* Create render pass descriptor */
        MTLRenderPassDescriptor *pass_desc = [MTLRenderPassDescriptor renderPassDescriptor];
        pass_desc.colorAttachments[0].texture = drawable.texture;
        pass_desc.colorAttachments[0].loadAction = MTLLoadActionClear;
        pass_desc.colorAttachments[0].storeAction = MTLStoreActionStore;
        pass_desc.colorAttachments[0].clearColor = MTLClearColorMake(0.0, 0.0, 0.0, 1.0);

        id<MTLCommandBuffer> cmd = [r->command_queue commandBuffer];
        id<MTLRenderCommandEncoder> encoder = [cmd renderCommandEncoderWithDescriptor:pass_desc];

        /* Select pipeline based on current shader */
        int shader_idx = r->current_shader;
        if (shader_idx < 0 || shader_idx >= NUM_SHADERS || !r->pipelines[shader_idx]) {
            shader_idx = GPU_SHADER_NONE;
        }
        [encoder setRenderPipelineState:r->pipelines[shader_idx]];

        /* Set texture */
        [encoder setFragmentTexture:r->game_texture atIndex:0];

        /* Choose sampler: linear for bilinear/sharp_bilinear, nearest for others */
        id<MTLSamplerState> sampler = r->sampler_nearest;
        if (shader_idx == GPU_SHADER_BILINEAR || shader_idx == GPU_SHADER_SHARP_BILINEAR) {
            sampler = r->sampler_linear;
        }
        [encoder setFragmentSamplerState:sampler atIndex:0];

        /* Set push constants buffer */
        [encoder setFragmentBuffer:r->push_constants_buffer offset:0 atIndex:0];

        /* Calculate viewport to maintain aspect ratio */
        float src_w = r->source_rect_set ? (float)r->source_w : (float)r->frame_width;
        float src_h = r->source_rect_set ? (float)r->source_h : (float)r->frame_height;

        /* Use drawable size for viewport calculations */
        float dst_w = (float)r->metal_layer.drawableSize.width;
        float dst_h = (float)r->metal_layer.drawableSize.height;

        float scale_x = dst_w / src_w;
        float scale_y = dst_h / src_h;
        float scale = scale_x < scale_y ? scale_x : scale_y;
        float vp_w = src_w * scale;
        float vp_h = src_h * scale;
        float vp_x = (dst_w - vp_w) / 2.0f;
        float vp_y = (dst_h - vp_h) / 2.0f;

        MTLViewport viewport = {
            .originX = vp_x,
            .originY = vp_y,
            .width = vp_w,
            .height = vp_h,
            .znear = 0.0,
            .zfar = 1.0,
        };
        [encoder setViewport:viewport];

        MTLScissorRect scissor = {
            .x = (NSUInteger)vp_x,
            .y = (NSUInteger)vp_y,
            .width = (NSUInteger)vp_w,
            .height = (NSUInteger)vp_h,
        };
        [encoder setScissorRect:scissor];

        /* Draw fullscreen triangle (3 vertices, no vertex buffer) */
        [encoder drawPrimitives:MTLPrimitiveTypeTriangle vertexStart:0 vertexCount:3];

        [encoder endEncoding];

        [cmd presentDrawable:drawable];

        /* Signal semaphore when frame completes */
        dispatch_semaphore_t sema = r->frame_semaphore;
        [cmd addCompletedHandler:^(id<MTLCommandBuffer> _Nonnull buffer) {
            (void)buffer;
            dispatch_semaphore_signal(sema);
        }];

        [cmd commit];

        r->current_frame = (r->current_frame + 1) % MAX_FRAMES_IN_FLIGHT;
    }
}

size_t gpu_renderer_render_to_bgra(gpu_renderer_t *r, void *out_data, size_t out_capacity,
    unsigned *out_width, unsigned *out_height) {
    if (!r || !r->active || !r->frame_uploaded || !r->offscreen_mode) return 0;

    @autoreleasepool {
        unsigned w = r->frame_width;
        unsigned h = r->frame_height;
        size_t needed = (size_t)w * h * 4;
        if (needed == 0 || needed > out_capacity) return 0;

        if (!ensure_offscreen_texture(r, w, h)) return 0;

        /* Update push constants */
        push_constants_t pc = {
            .texture_size = { (float)w, (float)h },
        };
        memcpy([r->push_constants_buffer contents], &pc, sizeof(pc));

        /* Create render pass targeting offscreen texture */
        MTLRenderPassDescriptor *pass_desc = [MTLRenderPassDescriptor renderPassDescriptor];
        pass_desc.colorAttachments[0].texture = r->offscreen_texture;
        pass_desc.colorAttachments[0].loadAction = MTLLoadActionClear;
        pass_desc.colorAttachments[0].storeAction = MTLStoreActionStore;
        pass_desc.colorAttachments[0].clearColor = MTLClearColorMake(0.0, 0.0, 0.0, 1.0);

        id<MTLCommandBuffer> cmd = [r->command_queue commandBuffer];
        id<MTLRenderCommandEncoder> encoder = [cmd renderCommandEncoderWithDescriptor:pass_desc];

        /* Select shader pipeline */
        int shader_idx = r->current_shader;
        if (shader_idx < 0 || shader_idx >= NUM_SHADERS || !r->pipelines[shader_idx]) {
            shader_idx = GPU_SHADER_NONE;
        }
        [encoder setRenderPipelineState:r->pipelines[shader_idx]];
        [encoder setFragmentTexture:r->game_texture atIndex:0];

        id<MTLSamplerState> sampler = r->sampler_nearest;
        if (shader_idx == GPU_SHADER_BILINEAR || shader_idx == GPU_SHADER_SHARP_BILINEAR) {
            sampler = r->sampler_linear;
        }
        [encoder setFragmentSamplerState:sampler atIndex:0];
        [encoder setFragmentBuffer:r->push_constants_buffer offset:0 atIndex:0];

        /* Full viewport -- Compose handles aspect ratio fitting */
        MTLViewport viewport = {
            .originX = 0, .originY = 0,
            .width = (double)w, .height = (double)h,
            .znear = 0.0, .zfar = 1.0,
        };
        [encoder setViewport:viewport];

        [encoder drawPrimitives:MTLPrimitiveTypeTriangle vertexStart:0 vertexCount:3];
        [encoder endEncoding];
        [cmd commit];
        [cmd waitUntilCompleted];

        /* Read back pixels from offscreen texture */
        [r->offscreen_texture getBytes:out_data
                           bytesPerRow:(NSUInteger)w * 4
                            fromRegion:MTLRegionMake2D(0, 0, w, h)
                           mipmapLevel:0];

        if (out_width) *out_width = w;
        if (out_height) *out_height = h;
        return needed;
    }
}

void gpu_renderer_set_source_rect(gpu_renderer_t *r, int x, int y, int w, int h) {
    if (!r) return;
    if (w <= 0 || h <= 0) {
        r->source_rect_set = false;
        return;
    }
    r->source_x = x;
    r->source_y = y;
    r->source_w = w;
    r->source_h = h;
    r->source_rect_set = true;
}

struct retro_hw_render_callback *gpu_renderer_get_hw_callback(gpu_renderer_t *r) {
    /* Phase 4: will return HW render interface */
    (void)r;
    return NULL;
}

bool gpu_renderer_is_active(gpu_renderer_t *r) {
    return r && r->active;
}

/* ===== Internal implementation ===== */

static bool create_metal_device(gpu_renderer_t *r) {
    r->device = MTLCreateSystemDefaultDevice();
    if (!r->device) {
        MTL_LOGE("MTLCreateSystemDefaultDevice() failed");
        return false;
    }

    r->command_queue = [r->device newCommandQueue];
    if (!r->command_queue) {
        MTL_LOGE("Failed to create command queue");
        return false;
    }

    MTL_LOGI("Metal device: %s", [[r->device name] UTF8String]);
    return true;
}

static bool setup_metal_layer_jawt(gpu_renderer_t *r, id<JAWT_SurfaceLayers> surface_layers) {
    r->metal_layer = [CAMetalLayer layer];
    r->metal_layer.device = r->device;
    r->metal_layer.pixelFormat = MTLPixelFormatBGRA8Unorm;
    r->metal_layer.framebufferOnly = YES;

    /* Use screen backing scale if available */
    CGFloat scale = [[NSScreen mainScreen] backingScaleFactor];
    if (scale < 1.0) scale = 1.0;
    r->metal_layer.contentsScale = scale;

    /* Enable display sync (vsync) */
    r->metal_layer.displaySyncEnabled = YES;

    /* Ensure the Metal layer draws on top of existing content */
    r->metal_layer.zPosition = 1000;

    /* Attach our Metal layer to the JAWT surface via the protocol.
     * Also add as a sublayer of the window layer to ensure it composites
     * above Compose/Skia rendering. */
    surface_layers.layer = r->metal_layer;

    CALayer *windowLayer = surface_layers.windowLayer;
    if (windowLayer) {
        [windowLayer addSublayer:r->metal_layer];
        MTL_LOGI("Metal layer added as sublayer of windowLayer (scale=%.1f)", scale);
    } else {
        MTL_LOGI("Metal layer set via JAWT (no windowLayer, scale=%.1f)", scale);
    }

    return true;
}

static id<MTLLibrary> compile_msl_source(id<MTLDevice> device, const char *source, const char *label) {
    NSError *error = nil;
    NSString *src = [NSString stringWithUTF8String:source];

    MTLCompileOptions *options = [[MTLCompileOptions alloc] init];
    options.languageVersion = MTLLanguageVersion2_0;

    id<MTLLibrary> library = [device newLibraryWithSource:src options:options error:&error];
    if (!library) {
        MTL_LOGE("Failed to compile %s: %s", label,
                 error ? [[error localizedDescription] UTF8String] : "unknown error");
        return nil;
    }

    return library;
}

static bool compile_shader_libraries(gpu_renderer_t *r) {
    /* Compile vertex shader library */
    r->vertex_library = compile_msl_source(r->device, msl_vertex_source, "vertex shader");
    if (!r->vertex_library) return false;

    /* Compile fragment shader libraries */
    static const struct {
        const char **source;
        const char *label;
    } shaders[NUM_SHADERS] = {
        { &msl_passthrough_source,    "passthrough" },
        { &msl_bilinear_source,       "bilinear" },
        { &msl_sharp_bilinear_source, "sharp_bilinear" },
        { &msl_crt_simple_source,     "crt_simple" },
        { &msl_scanlines_source,      "scanlines" },
        { &msl_lcd_grid_source,       "lcd_grid" },
    };

    for (int i = 0; i < NUM_SHADERS; i++) {
        r->fragment_libraries[i] = compile_msl_source(r->device, *shaders[i].source, shaders[i].label);
        if (!r->fragment_libraries[i]) {
            MTL_LOGW("Failed to compile fragment shader: %s", shaders[i].label);
            /* Non-fatal: will fall back to passthrough */
        }
    }

    MTL_LOGI("Shader libraries compiled");
    return true;
}

static bool create_single_pipeline(gpu_renderer_t *r, int shader_index) {
    if (!r->fragment_libraries[shader_index]) return false;

    id<MTLFunction> vertex_func = [r->vertex_library newFunctionWithName:@"vertex_main"];
    if (!vertex_func) {
        MTL_LOGE("Vertex function 'vertex_main' not found");
        return false;
    }

    NSString *frag_name = [NSString stringWithUTF8String:fragment_function_names[shader_index]];
    id<MTLFunction> fragment_func = [r->fragment_libraries[shader_index] newFunctionWithName:frag_name];
    if (!fragment_func) {
        MTL_LOGE("Fragment function '%s' not found", fragment_function_names[shader_index]);
        return false;
    }

    MTLRenderPipelineDescriptor *desc = [[MTLRenderPipelineDescriptor alloc] init];
    desc.vertexFunction = vertex_func;
    desc.fragmentFunction = fragment_func;
    desc.colorAttachments[0].pixelFormat = MTLPixelFormatBGRA8Unorm;

    /* No blending */
    desc.colorAttachments[0].blendingEnabled = NO;

    NSError *error = nil;
    r->pipelines[shader_index] = [r->device newRenderPipelineStateWithDescriptor:desc error:&error];
    if (!r->pipelines[shader_index]) {
        MTL_LOGE("Failed to create pipeline for shader %d: %s",
                 shader_index,
                 error ? [[error localizedDescription] UTF8String] : "unknown error");
        return false;
    }

    return true;
}

static bool create_pipeline_states(gpu_renderer_t *r) {
    for (int i = 0; i < NUM_SHADERS; i++) {
        if (!create_single_pipeline(r, i)) {
            MTL_LOGW("Failed to create pipeline for shader %d, will fall back", i);
        }
    }

    /* Passthrough pipeline must exist */
    if (!r->pipelines[GPU_SHADER_NONE]) {
        MTL_LOGE("Passthrough pipeline creation failed -- cannot continue");
        return false;
    }

    MTL_LOGI("Render pipelines created (%d shaders)", NUM_SHADERS);
    return true;
}

static bool create_metal_samplers(gpu_renderer_t *r) {
    /* Nearest-neighbor sampler */
    MTLSamplerDescriptor *nearest_desc = [[MTLSamplerDescriptor alloc] init];
    nearest_desc.minFilter = MTLSamplerMinMagFilterNearest;
    nearest_desc.magFilter = MTLSamplerMinMagFilterNearest;
    nearest_desc.sAddressMode = MTLSamplerAddressModeClampToEdge;
    nearest_desc.tAddressMode = MTLSamplerAddressModeClampToEdge;

    r->sampler_nearest = [r->device newSamplerStateWithDescriptor:nearest_desc];
    if (!r->sampler_nearest) {
        MTL_LOGE("Failed to create nearest sampler");
        return false;
    }

    /* Linear sampler */
    MTLSamplerDescriptor *linear_desc = [[MTLSamplerDescriptor alloc] init];
    linear_desc.minFilter = MTLSamplerMinMagFilterLinear;
    linear_desc.magFilter = MTLSamplerMinMagFilterLinear;
    linear_desc.sAddressMode = MTLSamplerAddressModeClampToEdge;
    linear_desc.tAddressMode = MTLSamplerAddressModeClampToEdge;

    r->sampler_linear = [r->device newSamplerStateWithDescriptor:linear_desc];
    if (!r->sampler_linear) {
        MTL_LOGE("Failed to create linear sampler");
        return false;
    }

    return true;
}

static bool create_push_constants_buffer(gpu_renderer_t *r) {
    r->push_constants_buffer = [r->device newBufferWithLength:sizeof(push_constants_t)
                                                      options:MTLResourceStorageModeShared];
    if (!r->push_constants_buffer) {
        MTL_LOGE("Failed to create push constants buffer");
        return false;
    }
    return true;
}

static bool create_game_texture_metal(gpu_renderer_t *r, unsigned w, unsigned h, MTLPixelFormat fmt) {
    MTLTextureDescriptor *desc = [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:fmt
                                                                                    width:w
                                                                                   height:h
                                                                                mipmapped:NO];
    desc.usage = MTLTextureUsageShaderRead;
    desc.storageMode = MTLStorageModeShared;

    r->game_texture = [r->device newTextureWithDescriptor:desc];
    if (!r->game_texture) {
        MTL_LOGE("Failed to create game texture %ux%u", w, h);
        return false;
    }

    r->game_texture_width = w;
    r->game_texture_height = h;
    r->game_texture_format = fmt;

    MTL_LOGI("Game texture created: %ux%u, format=%lu", w, h, (unsigned long)fmt);
    return true;
}

static bool ensure_offscreen_texture(gpu_renderer_t *r, unsigned w, unsigned h) {
    if (r->offscreen_texture && r->offscreen_width == w && r->offscreen_height == h) {
        return true;
    }

    MTLTextureDescriptor *desc = [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:MTLPixelFormatBGRA8Unorm
                                                                                    width:w
                                                                                   height:h
                                                                                mipmapped:NO];
    desc.usage = MTLTextureUsageRenderTarget | MTLTextureUsageShaderRead;
    desc.storageMode = MTLStorageModeShared;

    r->offscreen_texture = [r->device newTextureWithDescriptor:desc];
    if (!r->offscreen_texture) {
        MTL_LOGE("Failed to create offscreen texture %ux%u", w, h);
        return false;
    }

    r->offscreen_width = w;
    r->offscreen_height = h;
    MTL_LOGI("Offscreen texture created: %ux%u", w, h);
    return true;
}

static bool ensure_staging_buffer(gpu_renderer_t *r, NSUInteger size) {
    if (r->staging_buffer && r->staging_size >= size) {
        return true;
    }

    /* Use shared storage mode (zero-copy on Apple Silicon, managed on Intel) */
    r->staging_buffer = [r->device newBufferWithLength:size
                                               options:MTLResourceStorageModeShared];
    if (!r->staging_buffer) {
        MTL_LOGE("Failed to create staging buffer (%lu bytes)", (unsigned long)size);
        return false;
    }

    r->staging_size = size;
    return true;
}

static MTLPixelFormat pixel_format_for_retro(unsigned retro_format, unsigned *bpp) {
    switch (retro_format) {
        case RETRO_PIXEL_FORMAT_XRGB8888:
            *bpp = 4;
            return MTLPixelFormatBGRA8Unorm;
        case RETRO_PIXEL_FORMAT_RGB565:
            *bpp = 2;
            return MTLPixelFormatB5G6R5Unorm;
        case RETRO_PIXEL_FORMAT_0RGB1555:
            /* Metal has no native 1555 format; will be converted to BGRA8 */
            *bpp = 2;
            return MTLPixelFormatBGRA8Unorm;
        default:
            *bpp = 4;
            return MTLPixelFormatBGRA8Unorm;
    }
}

static void convert_0rgb1555_to_bgra(const uint16_t *src, uint8_t *dst,
    unsigned width, unsigned height, size_t src_pitch) {
    size_t src_stride = src_pitch / sizeof(uint16_t);

    for (unsigned y = 0; y < height; y++) {
        const uint16_t *row = src + y * src_stride;
        uint8_t *out = dst + y * width * 4;

        for (unsigned x = 0; x < width; x++) {
            uint16_t pixel = row[x];
            /* 0RGB1555: 0rrrrrgggggbbbbb */
            uint8_t r = (uint8_t)(((pixel >> 10) & 0x1F) * 255 / 31);
            uint8_t g = (uint8_t)(((pixel >> 5) & 0x1F) * 255 / 31);
            uint8_t b = (uint8_t)((pixel & 0x1F) * 255 / 31);

            /* BGRA order */
            out[x * 4 + 0] = b;
            out[x * 4 + 1] = g;
            out[x * 4 + 2] = r;
            out[x * 4 + 3] = 255;
        }
    }
}
