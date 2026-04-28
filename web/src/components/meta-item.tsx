import { Link } from "react-router-dom";
import type { LucideIcon } from "lucide-react";

interface MetaItemProps {
  icon: LucideIcon;
  label: string;
  value: string;
  href?: string;
}

export function MetaItem({ icon: Icon, label, value, href }: MetaItemProps) {
  return (
    <div data-comp="MetaItem" className="flex items-start gap-2.5 text-sm">
      <Icon className="h-4 w-4 text-surface-500 flex-shrink-0 mt-0.5" />
      <div className="flex flex-col">
        <span className="text-surface-500">{label}</span>
        {href ? (
          <Link
            to={href}
            className="text-brand-300 hover:text-brand-400 underline decoration-brand-300/30 hover:decoration-brand-400/60 transition-colors"
          >
            {value}
          </Link>
        ) : (
          <span className="text-surface-200">{value}</span>
        )}
      </div>
    </div>
  );
}
