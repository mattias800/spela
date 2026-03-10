import { useRef, useState, useEffect, useCallback } from "react";
import { Link } from "react-router-dom";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { Skeleton } from "@/components/ui";
import type { ArtworkItem } from "@/types/api";

interface ArtworkShowcaseProps {
  artworks: ArtworkItem[] | undefined;
  isLoading: boolean;
}

function ArtworkShowcaseSkeleton() {
  return (
    <section data-testid="artwork-showcase-skeleton">
      <Skeleton className="w-48 h-7 mb-5" />
      <div className="flex gap-5 overflow-hidden">
        {Array.from({ length: 4 }, (_, i) => (
          <div key={i} className="w-80 sm:w-96 flex-shrink-0">
            <Skeleton className="w-full rounded-xl" style={{ aspectRatio: "16/9" }} />
          </div>
        ))}
      </div>
    </section>
  );
}

export function ArtworkShowcase({ artworks, isLoading }: ArtworkShowcaseProps) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(false);

  const updateScrollState = useCallback(() => {
    const el = scrollRef.current;
    if (!el) return;
    setCanScrollLeft(el.scrollLeft > 0);
    setCanScrollRight(el.scrollLeft + el.clientWidth < el.scrollWidth - 1);
  }, []);

  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;

    updateScrollState();
    el.addEventListener("scroll", updateScrollState, { passive: true });
    window.addEventListener("resize", updateScrollState);
    return () => {
      el.removeEventListener("scroll", updateScrollState);
      window.removeEventListener("resize", updateScrollState);
    };
  }, [updateScrollState, artworks]);

  const scroll = useCallback((direction: "left" | "right") => {
    const el = scrollRef.current;
    if (!el) return;
    const scrollAmount = el.clientWidth * 0.7;
    el.scrollBy({
      left: direction === "left" ? -scrollAmount : scrollAmount,
      behavior: "smooth",
    });
  }, []);

  if (isLoading) {
    return <ArtworkShowcaseSkeleton />;
  }

  if (!artworks || artworks.length === 0) {
    return null;
  }

  // Show up to 10 items
  const displayArtworks = artworks.slice(0, 10);

  return (
    <section data-testid="artwork-showcase" className="group/artwork">
      <div className="flex items-center justify-between mb-5">
        <h2 className="text-xl font-bold text-surface-100">
          Artwork Showcase
        </h2>
        <div className="flex items-center gap-3 text-sm text-surface-400">
          <Link
            to="/explore/gallery"
            className="hover:text-brand-400 transition-colors"
            data-testid="browse-screenshots-link"
          >
            Browse Screenshots
          </Link>
          <span className="text-surface-700">|</span>
          <Link
            to="/explore/covers"
            className="hover:text-brand-400 transition-colors"
            data-testid="browse-covers-link"
          >
            Browse Cover Art
          </Link>
        </div>
      </div>

      <div className="relative">
        {/* Scroll arrows */}
        {canScrollLeft && (
          <button
            onClick={() => scroll("left")}
            className="absolute -left-2 top-1/2 -translate-y-1/2 z-10 p-2 rounded-full bg-surface-900/90 text-surface-300 hover:text-surface-100 hover:bg-surface-800 opacity-0 group-hover/artwork:opacity-100 group-focus-within/artwork:opacity-100 transition-all duration-300 shadow-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:opacity-100"
            aria-label="Scroll artwork left"
          >
            <ChevronLeft className="h-5 w-5" />
          </button>
        )}
        {canScrollRight && (
          <button
            onClick={() => scroll("right")}
            className="absolute -right-2 top-1/2 -translate-y-1/2 z-10 p-2 rounded-full bg-surface-900/90 text-surface-300 hover:text-surface-100 hover:bg-surface-800 opacity-0 group-hover/artwork:opacity-100 group-focus-within/artwork:opacity-100 transition-all duration-300 shadow-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:opacity-100"
            aria-label="Scroll artwork right"
          >
            <ChevronRight className="h-5 w-5" />
          </button>
        )}

        <div
          ref={scrollRef}
          className="flex gap-5 overflow-x-auto scrollbar-hide pb-2"
          style={{ scrollbarWidth: "none", msOverflowStyle: "none" }}
          role="list"
          aria-label="Artwork showcase"
        >
          {displayArtworks.map((artwork, i) => (
            <Link
              key={`${artwork.gameId}-${i}`}
              to={`/games/${artwork.gameId}`}
              className="w-80 sm:w-96 flex-shrink-0 group/card"
              role="listitem"
              data-testid="artwork-card"
            >
              <div className="relative rounded-xl overflow-hidden">
                <div style={{ aspectRatio: "16/9" }}>
                  <img
                    src={artwork.url}
                    alt={`Artwork for ${artwork.gameTitle}`}
                    className="w-full h-full object-cover transition-transform duration-300 group-hover/card:scale-[1.03]"
                    loading="lazy"
                  />
                </div>
                {/* Overlay with game info */}
                <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-transparent pointer-events-none" />
                <div className="absolute bottom-0 left-0 right-0 p-4">
                  <p className="text-sm font-semibold text-white truncate">
                    {artwork.gameTitle}
                  </p>
                  <p className="text-xs text-white/60 mt-0.5">
                    {artwork.consoleName}
                  </p>
                </div>
              </div>
            </Link>
          ))}
        </div>
      </div>
    </section>
  );
}
