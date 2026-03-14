import { Link } from "react-router-dom";
import { Layers, Puzzle, HardDrive } from "lucide-react";
import { Badge } from "@/components/ui";
import { formatFileSize } from "@/lib/format";
import type { GameVariant } from "@/types/api";

interface GameVariantsSectionProps {
  variants: GameVariant[];
}

function hasHackTag(variant: GameVariant): boolean {
  if (!variant.tags) return false;
  return variant.tags
    .split(",")
    .map((t) => t.trim().toLowerCase())
    .includes("hack");
}

function VariantRow({ variant }: { variant: GameVariant }) {
  return (
    <Link
      to={`/games/${variant.id}`}
      className="flex items-center gap-3 px-4 py-3 rounded-xl bg-surface-800/30 hover:bg-surface-800/50 transition-colors group focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
      data-testid={`variant-${variant.id}`}
    >
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-surface-200 truncate group-hover:text-brand-400 transition-colors">
          {variant.title}
        </p>
        <div className="flex flex-wrap items-center gap-2 mt-1">
          {variant.region && (
            <Badge variant="default">{variant.region}</Badge>
          )}
          {variant.revision && (
            <Badge variant="default">{variant.revision}</Badge>
          )}
          {variant.tags &&
            variant.tags
              .split(",")
              .map((t) => t.trim())
              .filter((t) => t.toLowerCase() !== "hack" && t !== "")
              .map((tag) => (
                <Badge key={tag} variant="default">
                  {tag}
                </Badge>
              ))}
        </div>
      </div>
      <span className="flex items-center gap-1 text-xs text-surface-500 whitespace-nowrap">
        <HardDrive className="h-3 w-3" />
        {formatFileSize(variant.fileSize)}
      </span>
    </Link>
  );
}

export function GameVariantsSection({ variants }: GameVariantsSectionProps) {
  if (!variants || variants.length === 0) return null;

  const versionVariants = variants.filter((v) => !hasHackTag(v));
  const hackVariants = variants.filter((v) => hasHackTag(v));

  // If both lists are empty (shouldn't happen, but guard)
  if (versionVariants.length === 0 && hackVariants.length === 0) return null;

  return (
    <section data-testid="game-variants-section">
      {versionVariants.length > 0 && (
        <div data-testid="versions-subsection">
          <div className="flex items-center gap-2.5 mb-3">
            <Layers className="h-5 w-5 text-brand-400" />
            <h2 className="text-lg font-semibold text-surface-100">Versions</h2>
            <span className="text-sm text-surface-500">
              ({versionVariants.length})
            </span>
          </div>
          <div className="space-y-2">
            {versionVariants.map((variant) => (
              <VariantRow key={variant.id} variant={variant} />
            ))}
          </div>
        </div>
      )}

      {hackVariants.length > 0 && (
        <div
          className={versionVariants.length > 0 ? "mt-6" : undefined}
          data-testid="rom-hacks-subsection"
        >
          <div className="flex items-center gap-2.5 mb-3">
            <Puzzle className="h-5 w-5 text-brand-400" />
            <h2 className="text-lg font-semibold text-surface-100">
              ROM Hacks
            </h2>
            <span className="text-sm text-surface-500">
              ({hackVariants.length})
            </span>
          </div>
          <div className="space-y-2">
            {hackVariants.map((variant) => (
              <VariantRow key={variant.id} variant={variant} />
            ))}
          </div>
        </div>
      )}
    </section>
  );
}
