import { useRef, useEffect, useState, useCallback } from "react";
import { cn } from "@/lib/cn";
import { createWebGLRenderer, type WebGLRenderer } from "../lib/webgl-renderer";
import {
  applyShaderOverlay,
  configureSmoothing,
} from "../lib/canvas-shader-renderer";

interface ShaderPreviewProps {
  imageUrl: string;
  shader: string;
  onClick?: () => void;
  className?: string;
}

export function ShaderPreview({
  imageUrl,
  shader,
  onClick,
  className,
}: ShaderPreviewProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const [loaded, setLoaded] = useState(false);
  const [hovering, setHovering] = useState(false);
  const imageRef = useRef<HTMLImageElement | null>(null);
  const rendererRef = useRef<WebGLRenderer | null>(null);

  const draw = useCallback(() => {
    const canvas = canvasRef.current;
    const img = imageRef.current;
    if (!canvas || !img || !img.complete || img.naturalWidth === 0) return;

    const container = containerRef.current;
    if (!container) return;

    const rect = container.getBoundingClientRect();
    const displayWidth = rect.width;
    const displayHeight = rect.width * (3 / 4);

    const renderer = rendererRef.current;
    if (renderer) {
      renderer.setImage(img);
      renderer.setShader(shader);
      renderer.resize(displayWidth, displayHeight);
      renderer.draw();
      return;
    }

    // Canvas 2D fallback
    const dpr = window.devicePixelRatio || 1;
    canvas.width = displayWidth * dpr;
    canvas.height = displayHeight * dpr;
    canvas.style.width = `${displayWidth}px`;
    canvas.style.height = `${displayHeight}px`;

    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    ctx.scale(dpr, dpr);
    ctx.clearRect(0, 0, displayWidth, displayHeight);
    ctx.fillStyle = "#0a0a0a";
    ctx.fillRect(0, 0, displayWidth, displayHeight);

    configureSmoothing(ctx, shader);

    const imgAspect = img.naturalWidth / img.naturalHeight;
    const canvasAspect = displayWidth / displayHeight;
    let drawW: number;
    let drawH: number;
    let drawX: number;
    let drawY: number;

    if (imgAspect > canvasAspect) {
      drawW = displayWidth;
      drawH = displayWidth / imgAspect;
      drawX = 0;
      drawY = (displayHeight - drawH) / 2;
    } else {
      drawH = displayHeight;
      drawW = displayHeight * imgAspect;
      drawX = (displayWidth - drawW) / 2;
      drawY = 0;
    }

    ctx.drawImage(img, drawX, drawY, drawW, drawH);

    applyShaderOverlay(
      ctx,
      shader,
      drawX,
      drawY,
      drawW,
      drawH,
      img.naturalWidth,
      img.naturalHeight,
    );
  }, [shader]);

  useEffect(() => {
    const img = new Image();
    img.crossOrigin = "anonymous";
    img.onload = () => {
      imageRef.current = img;
      setLoaded(true);
      draw();
    };
    img.src = imageUrl;

    return () => {
      img.onload = null;
    };
  }, [imageUrl, draw]);

  useEffect(() => {
    if (loaded) {
      draw();
    }
  }, [shader, loaded, draw]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const observer = new ResizeObserver(() => {
      if (loaded) {
        draw();
      }
    });

    observer.observe(container);
    return () => observer.disconnect();
  }, [loaded, draw]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (canvas) {
      rendererRef.current = createWebGLRenderer(canvas);
    }
    return () => {
      rendererRef.current?.destroy();
      rendererRef.current = null;
    };
  }, []);

  return (
    <div
      ref={containerRef}
      className={cn("relative", onClick && "cursor-pointer", className)}
      onClick={onClick}
      onMouseEnter={() => onClick && setHovering(true)}
      onMouseLeave={() => setHovering(false)}
    >
      {!loaded && (
        <div
          className="w-full bg-surface-900 rounded-lg"
          style={{ aspectRatio: "4/3" }}
        />
      )}
      <canvas
        ref={canvasRef}
        className={cn("rounded-lg w-full", loaded ? "block" : "hidden")}
        style={{ aspectRatio: "4/3" }}
      />
      {hovering && loaded && (
        <div className="absolute inset-0 flex items-center justify-center bg-black/40 rounded-lg transition-opacity">
          <span className="text-sm font-medium text-white/80">
            Click to enlarge
          </span>
        </div>
      )}
    </div>
  );
}
