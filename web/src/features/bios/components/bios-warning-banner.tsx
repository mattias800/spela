import { AlertTriangle, X } from "lucide-react";
import { Link } from "react-router-dom";
import { Button } from "@/components/ui";

interface BiosWarningBannerProps {
  message: string;
  isAdmin: boolean;
  onDismiss?: () => void;
  missingFiles?: string[];
}

export function BiosWarningBanner({
  message,
  isAdmin,
  onDismiss,
  missingFiles,
}: BiosWarningBannerProps) {
  return (
    <div data-comp="BiosWarningBanner"
      className="rounded-xl bg-warning-500/10 border border-warning-500/30 px-4 py-3 flex items-start gap-3"
      role="alert"
      data-testid="bios-warning-banner"
    >
      <AlertTriangle className="h-5 w-5 text-warning-500 mt-0.5 flex-shrink-0" />
      <div className="flex-1 min-w-0">
        <p className="text-sm text-warning-400">{message}</p>
        {missingFiles && missingFiles.length > 0 && (
          <p className="text-xs text-surface-400 mt-1 font-mono">
            {missingFiles.join(", ")}
          </p>
        )}
        <p className="text-xs text-surface-500 mt-1">
          {isAdmin ? (
            <Link
              to="/admin/bios"
              className="text-brand-400 hover:text-brand-300 underline"
            >
              Go to BIOS Management
            </Link>
          ) : (
            "Contact your server admin to upload the required BIOS files."
          )}
        </p>
      </div>
      {onDismiss && (
        <Button
          variant="ghost"
          size="sm"
          onClick={onDismiss}
          className="p-1 h-auto flex-shrink-0"
          aria-label="Dismiss"
        >
          <X className="h-4 w-4" />
        </Button>
      )}
    </div>
  );
}
