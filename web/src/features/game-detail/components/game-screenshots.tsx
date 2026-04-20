import { useState, useCallback, useRef } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { Section } from "@/components/ui";
import { ScreenshotLightbox } from "./screenshot-lightbox";

interface GameScreenshotsProps {
  screenshotUrls: string[] | null;
  gameTitle: string;
}

export function GameScreenshots({
  screenshotUrls,
  gameTitle,
}: GameScreenshotsProps) {
  const [currentIndex, setCurrentIndex] = useState(0);
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null);
  const pointerStartX = useRef<number | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  const total = screenshotUrls?.length ?? 0;

  const goNext = useCallback(() => {
    setCurrentIndex((i) => Math.min(i, total - 2) + 1);
  }, [total]);

  const goPrev = useCallback(() => {
    setCurrentIndex((i) => Math.max(i, 1) - 1);
  }, []);

  if (!screenshotUrls || total === 0) return null;

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "ArrowLeft") {
      e.preventDefault();
      goPrev();
    } else if (e.key === "ArrowRight") {
      e.preventDefault();
      goNext();
    }
  };

  const handlePointerDown = (e: React.PointerEvent) => {
    pointerStartX.current = e.clientX;
  };

  const handlePointerUp = (e: React.PointerEvent) => {
    if (pointerStartX.current === null) return;
    const delta = e.clientX - pointerStartX.current;
    pointerStartX.current = null;
    if (delta > 50 && currentIndex > 0) {
      goPrev();
    } else if (delta < -50 && currentIndex < total - 1) {
      goNext();
    }
  };

  return (
    <Section className="p-6">
      <h2 className="text-lg font-semibold text-surface-100 mb-4">
        {total === 1 ? "Screenshot" : "Screenshots"}
      </h2>
      <div
        ref={containerRef}
        className="relative overflow-hidden rounded-xl focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:outline-none"
        tabIndex={0}
        onKeyDown={handleKeyDown}
        onPointerDown={handlePointerDown}
        onPointerUp={handlePointerUp}
        role="region"
        aria-label={`${gameTitle} screenshots carousel`}
        aria-roledescription="carousel"
      >
        {/* Slides */}
        <div
          className="flex transition-transform duration-300 ease-in-out"
          style={{ transform: `translateX(-${currentIndex * 100}%)` }}
        >
          {screenshotUrls.map((url, i) => (
            <div
              key={url}
              className="w-full flex-shrink-0 flex items-center justify-center cursor-pointer"
              role="button"
              tabIndex={-1}
              onClick={() => setLightboxIndex(i)}
              aria-label={`Screenshot ${i + 1} of ${total}`}
            >
              <img
                src={url}
                alt={`${gameTitle} screenshot ${i + 1}`}
                className="max-w-full max-h-[28rem] object-contain"
                loading="lazy"
                draggable={false}
              />
            </div>
          ))}
        </div>

        {/* Previous button */}
        {currentIndex > 0 && (
          <button
            onClick={goPrev}
            className="absolute left-2 top-1/2 -translate-y-1/2 p-2 rounded-full bg-black/60 text-surface-300 hover:text-surface-100 hover:bg-black/80 focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:outline-none transition-colors"
            aria-label="Previous screenshot"
          >
            <ChevronLeft className="w-6 h-6" />
          </button>
        )}

        {/* Next button */}
        {currentIndex < total - 1 && (
          <button
            onClick={goNext}
            className="absolute right-2 top-1/2 -translate-y-1/2 p-2 rounded-full bg-black/60 text-surface-300 hover:text-surface-100 hover:bg-black/80 focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:outline-none transition-colors"
            aria-label="Next screenshot"
          >
            <ChevronRight className="w-6 h-6" />
          </button>
        )}
      </div>

      {/* Counter */}
      {total > 1 && (
        <p className="mt-2 text-center text-sm text-surface-400">
          {currentIndex + 1} / {total}
        </p>
      )}

      <ScreenshotLightbox
        open={lightboxIndex !== null}
        onClose={() => setLightboxIndex(null)}
        screenshotUrls={screenshotUrls}
        currentIndex={lightboxIndex ?? 0}
        onIndexChange={setLightboxIndex}
        gameTitle={gameTitle}
      />
    </Section>
  );
}
