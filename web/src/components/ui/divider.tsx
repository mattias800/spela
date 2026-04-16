import { cn } from "@/lib/cn";

interface DividerProps {
  className?: string;
}

export function Divider({ className }: DividerProps) {
  return (
    <div
      data-comp="Divider"
      className={cn("border-t border-surface-800/50", className)}
    />
  );
}
