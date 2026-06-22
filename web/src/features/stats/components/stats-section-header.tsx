import type { LucideIcon } from "lucide-react";
import { SegmentedControl } from "@/components/ui";

export type StatsScope = "this_server" | "across";

// Section heading (icon + title) with the "This server | Across servers" scope
// toggle pushed to the right. Shared by the federated-capable stats sections.
export function StatsSectionHeader({
  icon: Icon,
  title,
  scope,
  onScopeChange,
  testId,
}: {
  icon: LucideIcon;
  title: string;
  scope: StatsScope;
  onScopeChange: (scope: StatsScope) => void;
  testId?: string;
}) {
  return (
    <div className="mb-5 flex items-center gap-2.5">
      <Icon className="h-5 w-5 text-brand-400" />
      <h2 className="text-xl font-bold text-surface-100">{title}</h2>
      <div className="ml-auto">
        <SegmentedControl<StatsScope>
          ariaLabel={`${title} scope`}
          testId={testId}
          value={scope}
          onChange={onScopeChange}
          options={[
            { value: "this_server", label: "This server" },
            { value: "across", label: "Across servers" },
          ]}
        />
      </div>
    </div>
  );
}
