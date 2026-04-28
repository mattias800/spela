import { AlertTriangle, X } from "lucide-react";
import { Link } from "react-router-dom";
import { Button } from "@/components/ui";

interface IgdbWarningBannerProps {
  variant: "settings" | "dashboard";
  onDismiss?: () => void;
}

export function IgdbWarningBanner({
  variant,
  onDismiss,
}: IgdbWarningBannerProps) {
  return (
    <div data-comp="IgdbWarningBanner"
      className="rounded-xl bg-warning-500/10 border border-warning-500/30 px-4 py-3 flex items-start gap-3"
      role="alert"
      data-testid="igdb-warning-banner"
    >
      <AlertTriangle className="h-5 w-5 text-warning-500 mt-0.5 flex-shrink-0" />
      <div className="flex-1 min-w-0">
        <p className="text-sm text-warning-400">
          {variant === "settings"
            ? "Game metadata is unavailable. IGDB credentials have not been configured. Fill in your Twitch developer credentials below to enable metadata scraping."
            : "Game metadata is unavailable. IGDB credentials have not been configured."}
        </p>
        <p className="text-xs text-surface-500 mt-1">
          {variant === "settings" ? (
            <a
              href="#igdb-config"
              className="text-brand-400 hover:text-brand-300 underline"
            >
              Jump to IGDB Configuration
            </a>
          ) : (
            <Link
              to="/admin/settings"
              className="text-brand-400 hover:text-brand-300 underline"
            >
              Go to Settings
            </Link>
          )}
        </p>
      </div>
      {variant === "dashboard" && onDismiss && (
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
