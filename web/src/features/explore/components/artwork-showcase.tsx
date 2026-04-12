import { Link } from "react-router-dom";
import { Skeleton } from "@/components/ui";
import { ScrollShelf } from "@/components/scroll-shelf";
import type { ArtworkItem } from "@/types/api";

interface ArtworkShowcaseProps {
  artworks: ArtworkItem[] | undefined;
  isLoading: boolean;
}

function ArtworkSkeletonContent() {
  return (
    <div className="flex gap-5 overflow-hidden">
      {Array.from({ length: 4 }, (_, i) => (
        <div key={i} className="w-80 sm:w-96 flex-shrink-0">
          <Skeleton className="w-full rounded-xl" style={{ aspectRatio: "16/9" }} />
        </div>
      ))}
    </div>
  );
}

export function ArtworkShowcase({ artworks, isLoading }: ArtworkShowcaseProps) {
  const displayArtworks = artworks?.slice(0, 10);

  return (
    <ScrollShelf
      title="Artwork Showcase"
      testId="artwork-showcase"
      isLoading={isLoading}
      isEmpty={!displayArtworks || displayArtworks.length === 0}
      loadingSkeleton={<ArtworkSkeletonContent />}
      headerRight={
        <div className="flex items-center gap-3 text-sm text-surface-400">
          <Link to="/explore/gallery" className="hover:text-brand-400 transition-colors" data-testid="browse-screenshots-link">
            Browse Screenshots
          </Link>
          <span className="text-surface-700">|</span>
          <Link to="/explore/covers" className="hover:text-brand-400 transition-colors" data-testid="browse-covers-link">
            Browse Cover Art
          </Link>
        </div>
      }
    >
      {displayArtworks?.map((artwork, i) => (
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
            <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-transparent pointer-events-none" />
            <div className="absolute bottom-0 left-0 right-0 p-4">
              <p className="text-sm font-semibold text-white truncate">{artwork.gameTitle}</p>
              <p className="text-xs text-white/60 mt-0.5">{artwork.consoleName}</p>
            </div>
          </div>
        </Link>
      ))}
    </ScrollShelf>
  );
}
