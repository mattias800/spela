import { useRef, useEffect, useState, useCallback } from "react";

interface ShaderPreviewProps {
  imageUrl: string;
  shader: string;
  onClick?: () => void;
  className?: string;
}

const SOURCE_WIDTH = 256;
const SOURCE_HEIGHT = 224;

function applyShaderOverlay(
  ctx: CanvasRenderingContext2D,
  shader: string,
  x: number,
  y: number,
  w: number,
  h: number,
  imgW: number,
  imgH: number,
) {
  switch (shader) {
    case "scanlines": {
      const scaleY = h / imgH;
      ctx.strokeStyle = `rgba(0, 0, 0, 0.25)`;
      ctx.lineWidth = scaleY * 0.35;
      for (let row = 0; row < imgH; row++) {
        const lineY = y + row * scaleY + scaleY * 0.75;
        ctx.beginPath();
        ctx.moveTo(x, lineY);
        ctx.lineTo(x + w, lineY);
        ctx.stroke();
      }
      break;
    }
    case "crt-simple": {
      const scaleY = h / imgH;
      ctx.strokeStyle = `rgba(0, 0, 0, 0.35)`;
      ctx.lineWidth = scaleY * 0.4;
      for (let row = 0; row < imgH; row++) {
        const lineY = y + row * scaleY + scaleY * 0.6;
        ctx.beginPath();
        ctx.moveTo(x, lineY);
        ctx.lineTo(x + w, lineY);
        ctx.stroke();
      }
      const cx = x + w / 2;
      const cy = y + h / 2;
      const radius = Math.max(w, h) / 1.5;
      const gradient = ctx.createRadialGradient(cx, cy, 0, cx, cy, radius);
      gradient.addColorStop(0, "rgba(0, 0, 0, 0)");
      gradient.addColorStop(1, "rgba(0, 0, 0, 0.4)");
      ctx.fillStyle = gradient;
      ctx.fillRect(x, y, w, h);
      break;
    }
    case "lcd-grid": {
      const scaleX = w / imgW;
      const scaleY = h / imgH;
      const lineWidth = Math.max(1, Math.min(scaleX, scaleY) * 0.15);
      ctx.strokeStyle = `rgba(0, 0, 0, 0.2)`;
      ctx.lineWidth = lineWidth;
      for (let row = 0; row <= imgH; row++) {
        const lineY = y + row * scaleY;
        ctx.beginPath();
        ctx.moveTo(x, lineY);
        ctx.lineTo(x + w, lineY);
        ctx.stroke();
      }
      for (let col = 0; col <= imgW; col++) {
        const lineX = x + col * scaleX;
        ctx.beginPath();
        ctx.moveTo(lineX, y);
        ctx.lineTo(lineX, y + h);
        ctx.stroke();
      }
      break;
    }
  }
}

function configureSmoothing(ctx: CanvasRenderingContext2D, shader: string) {
  switch (shader) {
    case "none":
      ctx.imageSmoothingEnabled = false;
      break;
    case "bilinear":
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = "high";
      break;
    case "sharp-bilinear":
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = "medium";
      break;
    default:
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = "high";
      break;
  }
}

export function ShaderPreview({ imageUrl, shader, onClick, className }: ShaderPreviewProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const [loaded, setLoaded] = useState(false);
  const [hovering, setHovering] = useState(false);
  const imageRef = useRef<HTMLImageElement | null>(null);

  const draw = useCallback(() => {
    const canvas = canvasRef.current;
    const img = imageRef.current;
    if (!canvas || !img || !img.complete || img.naturalWidth === 0) return;

    const container = containerRef.current;
    if (!container) return;

    const rect = container.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;
    const displayWidth = rect.width;
    const displayHeight = rect.width * (3 / 4);

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

    applyShaderOverlay(ctx, shader, drawX, drawY, drawW, drawH, SOURCE_WIDTH, SOURCE_HEIGHT);
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

  return (
    <div
      ref={containerRef}
      className={`relative cursor-pointer ${className ?? ""}`}
      onClick={onClick}
      onMouseEnter={() => setHovering(true)}
      onMouseLeave={() => setHovering(false)}
    >
      {!loaded && (
        <div className="w-full bg-surface-900 rounded-lg" style={{ aspectRatio: "4/3" }} />
      )}
      <canvas
        ref={canvasRef}
        className={`rounded-lg w-full ${loaded ? "block" : "hidden"}`}
        style={{ aspectRatio: "4/3" }}
      />
      {hovering && loaded && (
        <div className="absolute inset-0 flex items-center justify-center bg-black/40 rounded-lg transition-opacity">
          <span className="text-sm font-medium text-white/80">Click to enlarge</span>
        </div>
      )}
    </div>
  );
}
