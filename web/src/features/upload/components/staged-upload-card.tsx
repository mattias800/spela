import { Check, X, Loader2, AlertCircle } from "lucide-react";
import { Button } from "@/components/ui";
import { GameSummaryCard } from "@/components/game-summary-card";
import { ConsoleSelector } from "./console-selector";
import type { StagedUpload } from "@/types/api";

interface StagedUploadCardProps {
  upload: StagedUpload;
  onAccept: (id: string) => void;
  onReject: (id: string) => void;
  onSetConsole: (id: string, consoleId: string) => void;
  isAccepting: boolean;
  isRejecting: boolean;
  isSettingConsole: boolean;
}

export function StagedUploadCard({
  upload,
  onAccept,
  onReject,
  onSetConsole,
  isAccepting,
  isRejecting,
  isSettingConsole,
}: StagedUploadCardProps) {
  if (upload.status === "pending_console") {
    return (
      <div
        className="rounded-2xl bg-surface-900/50 border border-warning-500/30 overflow-hidden p-4 space-y-3"
        data-testid="staged-upload-card"
      >
        <div className="flex items-center gap-2 text-sm">
          <AlertCircle className="h-4 w-4 text-warning-500" />
          <span className="text-surface-200 font-medium truncate">
            {upload.fileName}
          </span>
        </div>
        <p className="text-xs text-surface-400">
          This file extension matches multiple consoles. Please select the
          correct console.
        </p>
        <ConsoleSelector
          upload={upload}
          onSelect={(consoleId) => onSetConsole(upload.id, consoleId)}
          isPending={isSettingConsole}
        />
      </div>
    );
  }

  if (upload.status === "pending_scrape") {
    return (
      <div
        className="rounded-2xl bg-surface-900/50 border border-surface-800/50 overflow-hidden p-4"
        data-testid="staged-upload-card"
      >
        <div className="flex items-center gap-2 text-sm">
          <Loader2 className="h-4 w-4 text-brand-400 animate-spin" />
          <span className="text-surface-300">
            Waiting for scrape: {upload.fileName}
          </span>
        </div>
      </div>
    );
  }

  return (
    <GameSummaryCard
      title={upload.title || upload.canonicalName || upload.fileName}
      coverUrl={upload.coverUrl || undefined}
      consoleId={upload.consoleId ?? ""}
      consoleName={upload.consoleName ?? ""}
      rating={upload.igdbCriticsRating ?? 0}
      verificationStatus={upload.verificationStatus || undefined}
      fileName={upload.fileName}
      fileSize={upload.fileSize}
      isDuplicate={upload.status === "duplicate"}
      actions={
        <>
          <Button
            size="sm"
            variant="primary"
            onClick={() => onAccept(upload.id)}
            loading={isAccepting}
            disabled={isRejecting}
            icon={<Check className="h-4 w-4" />}
            data-testid="accept-button"
          >
            Accept
          </Button>
          <Button
            size="sm"
            variant="danger"
            onClick={() => onReject(upload.id)}
            loading={isRejecting}
            disabled={isAccepting}
            icon={<X className="h-4 w-4" />}
            data-testid="reject-button"
          >
            Reject
          </Button>
        </>
      }
    />
  );
}
