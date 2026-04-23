import { Loader2 } from "lucide-react";
import { Section } from "@/components/ui";
import { useGroupingProgress } from "@/hooks/use-grouping-progress";
import { ProgressBar } from "./progress-bar";

// Small transient status card shown at the top of the admin scan
// page while a background grouping pass is running. Self-hides when
// `grouping.active` is false, so it can be safely mounted as a
// permanent child of the scan-page layout.
export function GroupingStatus() {
  const grouping = useGroupingProgress();

  if (!grouping.active) return null;

  return (
    <Section>
      <div className="px-5 py-4 space-y-3">
        <div className="flex items-center gap-2 text-sm">
          <Loader2 className="h-4 w-4 animate-spin text-brand-400" />
          <span className="text-surface-200">{grouping.message}</span>
        </div>
        {grouping.total > 0 && (
          <>
            <ProgressBar value={grouping.current} max={grouping.total} />
            <p className="text-xs text-surface-400">
              {grouping.current.toLocaleString()} / {grouping.total.toLocaleString()}
            </p>
          </>
        )}
      </div>
    </Section>
  );
}
