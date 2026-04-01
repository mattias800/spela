import { type TextareaHTMLAttributes, forwardRef } from "react";
import { cn } from "@/lib/cn";

interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string;
  error?: string;
}

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(
  ({ className, label, error, id, ...props }, ref) => {
    return (
      <div data-comp="Textarea" className="space-y-1.5">
        {label && (
          <label
            htmlFor={id}
            className="block text-sm font-medium text-surface-300"
          >
            {label}
          </label>
        )}
        <textarea
          ref={ref}
          id={id}
          className={cn(
            "w-full rounded-lg bg-surface-900 border px-3.5 py-2.5 text-sm text-surface-100 placeholder:text-surface-500",
            "transition-all duration-200 resize-none",
            "focus:outline-none focus:ring-2 focus:ring-brand-500/40 focus:border-brand-500",
            error
              ? "border-danger-500/50 focus:ring-danger-500/40 focus:border-danger-500"
              : "border-surface-700 hover:border-surface-600",
            className,
          )}
          {...props}
        />
        {error && <p className="text-sm text-danger-500">{error}</p>}
      </div>
    );
  },
);

Textarea.displayName = "Textarea";
