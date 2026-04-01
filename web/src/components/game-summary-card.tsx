import { CheckCircle2, AlertTriangle, File, HardDrive } from "lucide-react";
import { Badge, Section } from "@/components/ui";
import { RatingDisplay } from "@/components/rating-display";
import { ConsoleBadge } from "@/components/console-badge";
import { formatFileSize } from "@/lib/format";
import { CoverImage } from "@/components/cover-image";

interface GameSummaryCardProps {
  title: string;
  coverUrl?: string;
  consoleId: string;
  consoleName: string;
  rating?: number;
  verificationStatus?: string;
  fileName: string;
  fileSize: number;
  isDuplicate?: boolean;
  actions?: React.ReactNode;
}

export function GameSummaryCard({
  title,
  coverUrl,
  consoleId,
  consoleName,
  rating,
  verificationStatus,
  fileName,
  fileSize,
  isDuplicate,
  actions,
}: GameSummaryCardProps) {
  const displayTitle = title || fileName;

  return (
    <Section data-comp="GameSummaryCard" data-testid="game-summary-card">
      <div className="flex gap-4 p-4">
        {/* Cover art */}
        <CoverImage src={coverUrl} alt={displayTitle} className="w-20 h-28 rounded-lg flex-shrink-0" />

        {/* Info */}
        <div className="flex-1 min-w-0 space-y-2">
          <div className="flex items-start gap-2">
            <h3
              className="text-sm font-semibold text-surface-100 truncate flex-1"
              title={displayTitle}
            >
              {displayTitle}
            </h3>
            {isDuplicate && (
              <Badge variant="warning" data-testid="duplicate-badge">
                Duplicate
              </Badge>
            )}
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <ConsoleBadge code={consoleId} label={consoleName} />

            {verificationStatus === "verified" && (
              <Badge variant="success" data-testid="verification-badge">
                <CheckCircle2 className="h-3 w-3 mr-1" />
                Verified
              </Badge>
            )}
            {verificationStatus === "unverified" && (
              <Badge variant="warning" data-testid="verification-badge">
                <AlertTriangle className="h-3 w-3 mr-1" />
                Unverified
              </Badge>
            )}
            {verificationStatus === "not_applicable" && (
              <Badge variant="default" data-testid="verification-badge">
                N/A
              </Badge>
            )}
          </div>

          {rating != null && rating > 0 && (
            <RatingDisplay value={rating} variant="stars" />
          )}

          <div className="flex items-center gap-3 text-xs text-surface-500">
            <span className="flex items-center gap-1 truncate" title={fileName}>
              <File className="h-3 w-3 flex-shrink-0" />
              <span className="truncate">{fileName}</span>
            </span>
            <span className="flex items-center gap-1 flex-shrink-0">
              <HardDrive className="h-3 w-3" />
              {formatFileSize(fileSize)}
            </span>
          </div>
        </div>
      </div>

      {actions && (
        <div className="px-4 pb-4 flex items-center gap-2">{actions}</div>
      )}
    </Section>
  );
}
