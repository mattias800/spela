export function applyShaderOverlay(
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

export function configureSmoothing(
  ctx: CanvasRenderingContext2D,
  shader: string,
) {
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
    case "scanlines":
    case "crt-simple":
    case "lcd-grid":
      ctx.imageSmoothingEnabled = false;
      break;
    default:
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = "high";
      break;
  }
}
