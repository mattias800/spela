import { cn } from "@/lib/cn";

interface StatCardProps {
  value: string;
  label: string;
  highlight?: boolean;
}

export function StatCard({ value, label, highlight }: StatCardProps) {
  return (
    <div className="bg-surface-800/30 rounded-xl p-4">
      <p
        className={cn(
          "text-lg font-bold",
          highlight ? "text-brand-400" : "text-surface-100",
        )}
      >
        {value}
      </p>
      <p className="text-xs text-surface-500 mt-0.5">{label}</p>
    </div>
  );
}
