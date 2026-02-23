import type { LucideIcon } from "lucide-react";

interface MetaItemProps {
  icon: LucideIcon;
  label: string;
  value: string;
}

export function MetaItem({ icon: Icon, label, value }: MetaItemProps) {
  return (
    <div className="flex items-start gap-2.5 text-sm">
      <Icon className="h-4 w-4 text-surface-500 flex-shrink-0 mt-0.5" />
      <div className="flex flex-col">
        <span className="text-surface-500">{label}</span>
        <span className="text-surface-200">{value}</span>
      </div>
    </div>
  );
}
