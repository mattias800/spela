interface GameScreenshotsProps {
  screenshotUrls: string[];
  gameTitle: string;
}

export function GameScreenshots({
  screenshotUrls,
  gameTitle,
}: GameScreenshotsProps) {
  if (!screenshotUrls || screenshotUrls.length === 0) return null;

  return (
    <section>
      <h2 className="text-xl font-bold text-surface-100 mb-4">
        {screenshotUrls.length === 1 ? "Screenshot" : "Screenshots"}
      </h2>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 max-w-4xl">
        {screenshotUrls.map((url, i) => (
          <div
            key={i}
            className="rounded-xl overflow-hidden border border-surface-800 bg-surface-900"
          >
            <img
              src={url}
              alt={`${gameTitle} screenshot ${i + 1}`}
              className="w-full aspect-video object-cover hover:scale-105 transition-transform duration-500"
              loading="lazy"
            />
          </div>
        ))}
      </div>
    </section>
  );
}
