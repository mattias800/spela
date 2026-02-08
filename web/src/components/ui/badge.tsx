import type { HTMLAttributes } from "react";
import { cn } from "@/lib/cn";

type BadgeVariant = "default" | "brand" | "success" | "warning" | "danger";

interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  variant?: BadgeVariant;
}

const variantStyles: Record<BadgeVariant, string> = {
  default: "bg-surface-800 text-surface-300 border-surface-700",
  brand: "bg-brand-500/15 text-brand-400 border-brand-500/30",
  success: "bg-success-500/15 text-success-500 border-success-500/30",
  warning: "bg-warning-500/15 text-warning-500 border-warning-500/30",
  danger: "bg-danger-500/15 text-danger-500 border-danger-500/30",
};

export function Badge({ className, variant = "default", children, ...props }: BadgeProps) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium",
        variantStyles[variant],
        className,
      )}
      {...props}
    >
      {children}
    </span>
  );
}
