import { AlertTriangle } from "lucide-react";
import { Button, Section } from "@/components/ui";

interface FederationErrorBlockProps {
  title: string;
  error: unknown;
  onRetry: () => void;
}

// Shown in place of a federation table when its query fails, so an API error
// reads as an error (with a retry) rather than an empty "no data" state.
export function FederationErrorBlock({
  title,
  error,
  onRetry,
}: FederationErrorBlockProps) {
  return (
    <Section>
      <div
        data-testid="federation-error"
        className="flex flex-col items-center gap-3 px-6 py-10 text-center"
      >
        <AlertTriangle className="h-10 w-10 text-danger-500" />
        <div>
          <p className="text-sm font-semibold text-surface-100">{title}</p>
          <p className="mt-1 text-sm text-surface-400">
            {error instanceof Error
              ? error.message
              : "An unknown error occurred."}
          </p>
        </div>
        <Button variant="secondary" size="sm" onClick={onRetry}>
          Try again
        </Button>
      </div>
    </Section>
  );
}
