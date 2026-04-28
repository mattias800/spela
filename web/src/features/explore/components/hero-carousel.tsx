import { useState, useEffect, useCallback, useRef } from "react";
import { Link } from "react-router-dom";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { RatingDisplay } from "@/components/rating-display";
import { Skeleton } from "@/components/ui";
import { AreaSizedImage } from "@/components/area-sized-image";
import { ConsoleBadge } from "@/components/console-badge";
import { cn } from "@/lib/cn";
import type { FeaturedGame } from "@/types/api";

interface HeroCarouselProps {
  games: FeaturedGame[] | undefined;
  isLoading: boolean;
}

function HeroCarouselSkeleton() {
  return (
    <div data-comp="HeroCarouselSkeleton"
      className="relative w-full overflow-hidden rounded-2xl"
      data-testid="hero-carousel-skeleton"
    >
      <Skeleton className="w-full rounded-2xl" style={{ aspectRatio: "21/9" }} />
    </div>
  );
}

function NavigationDots({
  count,
  activeIndex,
  onSelect,
}: {
  count: number;
  activeIndex: number;
  onSelect: (index: number) => void;
}) {
  return (
    <div data-comp="NavigationDots"
      className="absolute bottom-4 left-1/2 -translate-x-1/2 flex items-center gap-2 z-10"
      role="tablist"
      aria-label="Carousel navigation"
    >
      {Array.from({ length: count }, (_, i) => (
        <button
          key={i}
          role="tab"
          aria-selected={i === activeIndex}
          aria-label={`Go to slide ${i + 1}`}
          onClick={() => onSelect(i)}
          className={cn(
            "h-2 rounded-full transition-all duration-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
            i === activeIndex
              ? "w-6 bg-white"
              : "w-2 bg-white/40 hover:bg-white/60",
          )}
        />
      ))}
    </div>
  );
}

function HeroSlide({
  game,
  isActive,
}: {
  game: FeaturedGame;
  isActive: boolean;
}) {
  const [imageError, setImageError] = useState(false);

  return (
    <div data-comp="HeroSlide"
      className={cn(
        "absolute inset-0 transition-opacity duration-700 ease-in-out",
        isActive ? "opacity-100 z-[1]" : "opacity-0 z-0",
      )}
      aria-hidden={!isActive}
      data-testid={`hero-slide-${game.gameId}`}
    >
      {/* Hero background image */}
      {!imageError ? (
        <img
          src={game.heroUrl}
          alt={`Hero art for ${game.title}`}
          className="absolute inset-0 w-full h-full object-cover"
          onError={() => setImageError(true)}
          loading={isActive ? "eager" : "lazy"}
        />
      ) : (
        <div className="absolute inset-0 bg-gradient-to-br from-surface-800 to-surface-950" />
      )}

      {/* Gradient overlays for readability */}
      <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/30 to-transparent" />
      <div className="absolute inset-0 bg-gradient-to-r from-black/40 via-transparent to-black/40" />

      {/* Content overlay — logo+badges centered left, button bottom-right */}
      <div className="absolute inset-0 flex flex-col p-6 sm:p-8 lg:p-10">
        {/* Logo + badges vertically centered */}
        <div className="flex-1 flex items-center">
          <div className="flex flex-col items-center gap-3">
            {game.logoUrl ? (
              <AreaSizedImage
                src={game.logoUrl}
                alt={game.title}
                targetArea={28000}
                maxHeight={160}
                maxWidth={400}
                minHeight={60}
                className="drop-shadow-lg"
              />
            ) : (
              <h2
                className="text-3xl sm:text-4xl lg:text-5xl font-bold text-white drop-shadow-lg"
                data-testid={`hero-title-text-${game.gameId}`}
              >
                {game.title}
              </h2>
            )}

            <div className="flex flex-wrap items-center justify-center gap-2">
              {game.consoleAbbreviation && (
                <ConsoleBadge code={game.consoleAbbreviation} />
              )}
              {game.igdbCriticsRating > 0 && (
                <RatingDisplay value={game.igdbCriticsRating} />
              )}
            </div>
          </div>
        </div>

        {/* Button pinned to bottom-right */}
        <div className="flex justify-end">
          <Link
            to={`/games/${game.gameId}`}
            className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-brand-600 text-white font-medium text-sm hover:bg-brand-500 transition-colors shadow-lg shadow-brand-600/20 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 focus-visible:ring-offset-surface-950"
            tabIndex={isActive ? 0 : -1}
            data-testid={`hero-view-game-${game.gameId}`}
          >
            View Game
          </Link>
        </div>
      </div>
    </div>
  );
}

export function HeroCarousel({ games, isLoading }: HeroCarouselProps) {
  const [activeIndex, setActiveIndex] = useState(0);
  const [isPaused, setIsPaused] = useState(false);
  const [prefersReducedMotion, setPrefersReducedMotion] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  // Check for prefers-reduced-motion
  useEffect(() => {
    const mq = window.matchMedia("(prefers-reduced-motion: reduce)");
    setPrefersReducedMotion(mq.matches);
    const handler = (e: MediaQueryListEvent) =>
      setPrefersReducedMotion(e.matches);
    mq.addEventListener("change", handler);
    return () => mq.removeEventListener("change", handler);
  }, []);

  const slideCount = games?.length ?? 0;

  const goToNext = useCallback(() => {
    if (slideCount <= 1) return;
    setActiveIndex((prev) => (prev + 1) % slideCount);
  }, [slideCount]);

  const goToPrev = useCallback(() => {
    if (slideCount <= 1) return;
    setActiveIndex((prev) => (prev - 1 + slideCount) % slideCount);
  }, [slideCount]);

  const goToSlide = useCallback((index: number) => {
    setActiveIndex(index);
  }, []);

  // Auto-rotation
  useEffect(() => {
    if (
      prefersReducedMotion ||
      isPaused ||
      slideCount <= 1
    ) {
      return;
    }

    const timer = setInterval(goToNext, 8000);
    return () => clearInterval(timer);
  }, [prefersReducedMotion, isPaused, slideCount, goToNext]);

  // Keyboard navigation
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const handler = (e: KeyboardEvent) => {
      if (e.key === "ArrowLeft") {
        e.preventDefault();
        goToPrev();
      } else if (e.key === "ArrowRight") {
        e.preventDefault();
        goToNext();
      }
    };

    container.addEventListener("keydown", handler);
    return () => container.removeEventListener("keydown", handler);
  }, [goToNext, goToPrev]);

  if (isLoading) {
    return <HeroCarouselSkeleton />;
  }

  if (!games || games.length === 0) {
    return null;
  }

  return (
    <div data-comp="HeroCarousel"
      ref={containerRef}
      className="relative w-full overflow-hidden rounded-2xl group aspect-[21/9]"
      onMouseEnter={() => setIsPaused(true)}
      onMouseLeave={() => setIsPaused(false)}
      tabIndex={0}
      role="region"
      aria-label="Featured games carousel"
      aria-roledescription="carousel"
      data-testid="hero-carousel"
    >
      {/* Slides */}
      {games.map((game, index) => (
        <HeroSlide
          key={game.gameId}
          game={game}
          isActive={index === activeIndex}
        />
      ))}

      {/* Arrow buttons */}
      {slideCount > 1 && (
        <>
          <button
            onClick={goToPrev}
            className="absolute left-3 top-1/2 -translate-y-1/2 z-10 p-2 rounded-full bg-black/40 text-white/70 hover:text-white hover:bg-black/60 opacity-0 group-hover:opacity-100 transition-all duration-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:opacity-100"
            aria-label="Previous slide"
          >
            <ChevronLeft className="h-5 w-5" />
          </button>
          <button
            onClick={goToNext}
            className="absolute right-3 top-1/2 -translate-y-1/2 z-10 p-2 rounded-full bg-black/40 text-white/70 hover:text-white hover:bg-black/60 opacity-0 group-hover:opacity-100 transition-all duration-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:opacity-100"
            aria-label="Next slide"
          >
            <ChevronRight className="h-5 w-5" />
          </button>
        </>
      )}

      {/* Navigation dots */}
      {slideCount > 1 && (
        <NavigationDots
          count={slideCount}
          activeIndex={activeIndex}
          onSelect={goToSlide}
        />
      )}
    </div>
  );
}
