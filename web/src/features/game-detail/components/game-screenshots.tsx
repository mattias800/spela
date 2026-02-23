import { useState } from "react";
import { ScreenshotLightbox } from "./screenshot-lightbox";

interface GameScreenshotsProps {
  screenshotUrls: string[];
  gameTitle: string;
}

export function GameScreenshots({
  screenshotUrls,
  gameTitle,
}: GameScreenshotsProps) {
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null);

  if (!screenshotUrls || screenshotUrls.length === 0) return null;

  return (
    <section>
      <h2 className="text-xl font-bold text-surface-100 mb-4">
        {screenshotUrls.length === 1 ? "Screenshot" : "Screenshots"}
      </h2>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 max-w-4xl">
        {screenshotUrls.map((url, i) => (
          <div
            key={url}
            role="button"
            tabIndex={0}
            className="rounded-xl overflow-hidden border border-surface-800 bg-surface-900 cursor-pointer focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:outline-none"
            onClick={() => setLightboxIndex(i)}
            onKeyDown={(e) => {
              if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                setLightboxIndex(i);
              }
            }}
          >
            <img
              src={url}
              alt={`${gameTitle} screenshot ${i + 1}`}
              className="w-full object-contain hover:scale-105 focus-visible:scale-105 transition-transform duration-500"
              loading="lazy"
            />
          </div>
        ))}
      </div>
      <ScreenshotLightbox
        open={lightboxIndex !== null}
        onClose={() => setLightboxIndex(null)}
        screenshotUrls={screenshotUrls}
        currentIndex={lightboxIndex ?? 0}
        onIndexChange={setLightboxIndex}
        gameTitle={gameTitle}
      />
    </section>
  );
}
