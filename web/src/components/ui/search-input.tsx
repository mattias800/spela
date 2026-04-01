import { type InputHTMLAttributes, forwardRef } from "react";
import { Search } from "lucide-react";
import { cn } from "@/lib/cn";

export const SearchInput = forwardRef<
  HTMLInputElement,
  InputHTMLAttributes<HTMLInputElement>
>(({ className, ...props }, ref) => {
  return (
    <div data-comp="SearchInput" className="relative">
      <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-surface-500 pointer-events-none" />
      <input
        ref={ref}
        type="search"
        className={cn(
          "w-full rounded-xl bg-surface-900 border border-surface-700 pl-10 pr-4 py-2.5 text-sm text-surface-100 placeholder:text-surface-500",
          "transition-all duration-200",
          "focus:outline-none focus:ring-2 focus:ring-brand-500/40 focus:border-brand-500",
          "hover:border-surface-600",
          className,
        )}
        {...props}
      />
    </div>
  );
});

SearchInput.displayName = "SearchInput";
