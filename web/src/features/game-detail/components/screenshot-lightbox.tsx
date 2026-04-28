import { useEffect, useCallback, useRef } from "react";
import { ChevronLeft, ChevronRight, X } from "lucide-react";

interface ScreenshotLightboxProps {
  open: boolean;
  onClose: () => void;
  screenshotUrls: string[];
  currentIndex: number;
  onIndexChange: (index: number) => void;
  gameTitle: string;
}

export function ScreenshotLightbox({
  open,
  onClose,
  screenshotUrls,
  currentIndex,
  onIndexChange,
  gameTitle,
}: ScreenshotLightboxProps) {
  const dialogRef = useRef<HTMLDivElement>(null);

  const goNext = useCallback(() => {
    if (currentIndex < screenshotUrls.length - 1) {
      onIndexChange(currentIndex + 1);
    }
  }, [currentIndex, screenshotUrls.length, onIndexChange]);

  const goPrev = useCallback(() => {
    if (currentIndex > 0) {
      onIndexChange(currentIndex - 1);
    }
  }, [currentIndex, onIndexChange]);

  useEffect(() => {
    if (!open) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
      if (e.key === "ArrowLeft") goPrev();
      if (e.key === "ArrowRight") goNext();
    };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [open, onClose, goPrev, goNext]);

  useEffect(() => {
    if (open) {
      document.body.style.overflow = "hidden";
      dialogRef.current?.focus();
    } else {
      document.body.style.overflow = "";
    }
    return () => {
      document.body.style.overflow = "";
    };
  }, [open]);

  if (!open) return null;

  return (
    <div data-comp="ScreenshotLightbox"
      ref={dialogRef}
      role="dialog"
      aria-modal="true"
      aria-label={`${gameTitle} screenshots`}
      tabIndex={-1}
      className="fixed inset-0 z-50 flex flex-col items-center justify-center animate-in fade-in duration-200 outline-none"
      onClick={onClose}
    >
      <div className="absolute inset-0 bg-black/90 backdrop-blur-sm" />

      {/* Counter */}
      <div className="relative z-10 mb-4 text-sm text-surface-400">
        {currentIndex + 1} / {screenshotUrls.length}
      </div>

      {/* Image + navigation */}
      <div
        className="relative z-10 flex items-center gap-4 max-w-[90vw] max-h-[80vh]"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Left arrow */}
        <button
          onClick={goPrev}
          disabled={currentIndex === 0}
          className="shrink-0 p-2 rounded-full text-surface-400 hover:text-surface-100 hover:bg-surface-800/50 focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:outline-none disabled:opacity-30 disabled:cursor-default transition-colors"
          aria-label="Previous screenshot"
        >
          <ChevronLeft className="w-8 h-8" />
        </button>

        {/* Image */}
        <img
          src={screenshotUrls[currentIndex]}
          alt={`${gameTitle} screenshot ${currentIndex + 1}`}
          className="max-w-full max-h-[80vh] object-contain rounded-lg"
        />

        {/* Right arrow */}
        <button
          onClick={goNext}
          disabled={currentIndex === screenshotUrls.length - 1}
          className="shrink-0 p-2 rounded-full text-surface-400 hover:text-surface-100 hover:bg-surface-800/50 focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:outline-none disabled:opacity-30 disabled:cursor-default transition-colors"
          aria-label="Next screenshot"
        >
          <ChevronRight className="w-8 h-8" />
        </button>
      </div>

      {/* Close button */}
      <button
        className="absolute top-4 right-4 z-10 p-2 rounded-lg text-surface-400 hover:text-surface-100 hover:bg-surface-800 focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:outline-none transition-colors"
        onClick={(e) => {
          e.stopPropagation();
          onClose();
        }}
        aria-label="Close lightbox"
      >
        <X className="w-6 h-6" />
      </button>

      {/* Dismiss hint */}
      <p className="relative z-10 mt-4 text-sm text-surface-500">
        Click anywhere to close
      </p>
    </div>
  );
}
