import { useState } from "react";
import { Link } from "react-router-dom";
import { Layers, Puzzle, HardDrive, ChevronRight } from "lucide-react";
import { Badge } from "@/components/ui";
import { cn } from "@/lib/cn";
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
      className="flex items-center gap-3 px-3 py-2 rounded-lg bg-surface-800/20 hover:bg-surface-800/40 transition-colors group focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
      data-testid={`variant-${variant.id}`}
    >
      <div className="flex-1 min-w-0">
        <p className="text-sm text-surface-300 truncate group-hover:text-brand-400 transition-colors">
          {variant.title}
        </p>
        <div className="flex flex-wrap items-center gap-1.5 mt-0.5">
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

function CollapsibleVariantList({
  icon: Icon,
  label,
  variants,
  testId,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  variants: GameVariant[];
  testId: string;
}) {
  const [expanded, setExpanded] = useState(variants.length <= 2);

  return (
    <div data-testid={testId}>
      <button
        onClick={() => setExpanded(!expanded)}
        className="flex items-center gap-2 w-full text-left group/toggle focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 rounded-md px-1 py-0.5 -mx-1"
        aria-expanded={expanded}
      >
        <Icon className="h-4 w-4 text-surface-400" />
        <span className="text-sm font-medium text-surface-400 group-hover/toggle:text-surface-300 transition-colors">
          {label}
        </span>
        <span className="text-xs text-surface-500">({variants.length})</span>
        <ChevronRight
          className={cn(
            "h-3.5 w-3.5 text-surface-500 transition-transform duration-200 ml-auto",
            expanded && "rotate-90",
          )}
        />
      </button>
      {expanded && (
        <div className="space-y-1.5 mt-2">
          {variants.map((variant) => (
            <VariantRow key={variant.id} variant={variant} />
          ))}
        </div>
      )}
    </div>
  );
}

export function GameVariantsSection({ variants }: GameVariantsSectionProps) {
  if (!variants || variants.length === 0) return null;

  const versionVariants = variants.filter((v) => !hasHackTag(v));
  const hackVariants = variants.filter((v) => hasHackTag(v));

  if (versionVariants.length === 0 && hackVariants.length === 0) return null;

  return (
    <section
      className="space-y-4"
      data-testid="game-variants-section"
    >
      {versionVariants.length > 0 && (
        <CollapsibleVariantList
          icon={Layers}
          label="Versions"
          variants={versionVariants}
          testId="versions-subsection"
        />
      )}

      {hackVariants.length > 0 && (
        <CollapsibleVariantList
          icon={Puzzle}
          label="ROM Hacks"
          variants={hackVariants}
          testId="rom-hacks-subsection"
        />
      )}
    </section>
  );
}
