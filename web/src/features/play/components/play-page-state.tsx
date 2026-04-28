import { AlertTriangle } from "lucide-react";
import { Button, Skeleton } from "@/components/ui";

// Full-screen placeholders used by `play-page.tsx` while the page is
// not yet showing the emulator. Each shape used to be an inline
// early-return block in the page body; extracted for readability
// and to let the page body focus on the happy path.

interface PlayPageLoadingProps {
  // intentionally empty — renders a fixed skeleton
}

/** Top-bar + blank stage skeleton while game / consoles are loading. */
export function PlayPageLoading(_props: PlayPageLoadingProps = {}) {
  return (
    <div data-comp="PlayPageLoading" className="flex flex-col h-screen">
      <div className="flex items-center justify-between px-4 py-2 border-b border-surface-800 bg-surface-950/80">
        <div className="flex items-center gap-3">
          <Skeleton className="h-5 w-16" />
          <Skeleton className="h-5 w-40" />
        </div>
        <div className="flex items-center gap-2">
          <Skeleton className="h-8 w-8 rounded" />
          <Skeleton className="h-8 w-8 rounded" />
          <Skeleton className="h-8 w-8 rounded" />
        </div>
      </div>
      <div className="flex-1 bg-surface-950" />
    </div>
  );
}

interface PlayPageNotFoundProps {
  onBack: () => void;
}

export function PlayPageNotFound({ onBack }: PlayPageNotFoundProps) {
  return (
    <div data-comp="PlayPageNotFound" className="text-center py-20">
      <p className="text-surface-400">Game not found</p>
      <Button variant="ghost" onClick={onBack} className="mt-4">
        Go back
      </Button>
    </div>
  );
}

interface PlayPageUnsupportedProps {
  consoleName: string;
  onBack: () => void;
}

export function PlayPageUnsupported({
  consoleName,
  onBack,
}: PlayPageUnsupportedProps) {
  return (
    <div data-comp="PlayPageUnsupported" className="text-center py-20 space-y-4">
      <AlertTriangle className="h-12 w-12 text-warning-500 mx-auto" />
      <h2 className="text-xl font-bold text-surface-100">
        Browser Play Not Available
      </h2>
      <p className="text-surface-400 max-w-md mx-auto">
        {consoleName} is not yet supported for browser play. Use the Spela
        player app to play this game.
      </p>
      <Button variant="secondary" onClick={onBack}>
        Back to Game Details
      </Button>
    </div>
  );
}

interface PlayPageErrorProps {
  error: string | null | undefined;
  onBack: () => void;
  onRetry: () => void;
}

export function PlayPageError({
  error,
  onBack,
  onRetry,
}: PlayPageErrorProps) {
  return (
    <div data-comp="PlayPageError" className="text-center py-20 space-y-4">
      <AlertTriangle className="h-12 w-12 text-danger-500 mx-auto" />
      <h2 className="text-xl font-bold text-surface-100">Emulation Error</h2>
      <p className="text-surface-400 max-w-md mx-auto">
        {error || "The emulator encountered an unexpected error."}
      </p>
      <div className="flex items-center justify-center gap-3 pt-2">
        <Button variant="secondary" onClick={onBack}>
          Back to Game Details
        </Button>
        <Button onClick={onRetry}>Try Again</Button>
      </div>
    </div>
  );
}
