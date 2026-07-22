import type { ReactNode } from "react";
import { AlertTriangle } from "lucide-react";

interface BannerProps {
  children: ReactNode;
  className?: string;
  "data-testid"?: string;
}

export function Banner({
  children,
  className = "",
  "data-testid": dataTestId,
}: BannerProps) {
  return (
    <div
      className={`flex items-start gap-3 rounded-lg border border-warning-500/30 bg-warning-500/10 p-3 ${className}`}
      data-testid={dataTestId}
    >
      <AlertTriangle className="h-5 w-5 flex-shrink-0 text-warning-500 mt-0.5" />
      <p className="text-sm text-secondary">{children}</p>
    </div>
  );
}
