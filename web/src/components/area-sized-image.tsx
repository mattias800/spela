import { useState, useCallback } from "react";

interface AreaSizedImageProps {
  src: string;
  alt: string;
  targetArea?: number;
  maxHeight?: number;
  maxWidth?: number;
  minHeight?: number;
  className?: string;
}

/**
 * Renders an image at a consistent visual area size.
 *
 * Instead of a fixed height or width, this component targets a consistent
 * visual area (width × height in px²). Wide images become shorter, tall
 * images become narrower — but they all occupy roughly the same visual space.
 *
 * This solves the problem where logos of different aspect ratios look
 * inconsistently sized when constrained by height alone.
 */
export function AreaSizedImage({
  src,
  alt,
  targetArea = 12000,
  maxHeight = 160,
  maxWidth = 400,
  minHeight = 48,
  className = "",
}: AreaSizedImageProps) {
  const [dimensions, setDimensions] = useState<{
    width: number;
    height: number;
  } | null>(null);

  const onLoad = useCallback(
    (e: React.SyntheticEvent<HTMLImageElement>) => {
      const img = e.currentTarget;
      const { naturalWidth, naturalHeight } = img;
      if (naturalWidth > 0 && naturalHeight > 0) {
        const aspectRatio = naturalWidth / naturalHeight;
        const h = Math.sqrt(targetArea / aspectRatio);
        const w = Math.sqrt(targetArea * aspectRatio);
        setDimensions({
          width: Math.min(w, maxWidth),
          height: Math.max(Math.min(h, maxHeight), minHeight),
        });
      }
    },
    [targetArea, maxHeight, maxWidth, minHeight],
  );

  return (
    <img
      src={src}
      alt={alt}
      onLoad={onLoad}
      className={className}
      style={
        dimensions
          ? { maxHeight: dimensions.height, maxWidth: dimensions.width, objectFit: "contain" }
          : { maxHeight, maxWidth: maxWidth, objectFit: "contain" }
      }
    />
  );
}
