import { type ReactNode, useState, useRef, useEffect } from "react";
import { cn } from "@/lib/cn";

interface DropdownMenuProps {
  trigger: ReactNode;
  children: ReactNode;
  align?: "left" | "right";
  className?: string;
}

export function DropdownMenu({
  trigger,
  children,
  align = "right",
  className,
}: DropdownMenuProps) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    function handleClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [open]);

  return (
    <div className="relative" ref={ref} data-comp="DropdownMenu">
      <div onClick={() => setOpen((prev) => !prev)}>{trigger}</div>
      {open && (
        <div
          role="menu"
          className={cn(
            "absolute top-full mt-2 z-40 rounded-xl bg-surface-900 border border-surface-800 shadow-2xl py-1",
            align === "right" ? "right-0" : "left-0",
            className,
          )}
          onClick={(e) => {
            const target = e.target as HTMLElement;
            const button = target.closest("button");
            if (button && !button.disabled) setOpen(false);
          }}
        >
          {children}
        </div>
      )}
    </div>
  );
}
