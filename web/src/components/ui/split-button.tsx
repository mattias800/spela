import { type ReactNode, type ButtonHTMLAttributes } from "react";
import { Ellipsis } from "lucide-react";
import { cn } from "@/lib/cn";
import { Button } from "./button";
import { DropdownMenu } from "./dropdown-menu";

interface SplitButtonMenuItem {
  label: string;
  icon?: ReactNode;
  onClick: () => void;
}

interface SplitButtonProps
  extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, "children"> {
  variant?: "primary" | "secondary";
  size?: "sm" | "md" | "lg";
  loading?: boolean;
  menuItems: SplitButtonMenuItem[];
  children: ReactNode;
}

export function SplitButton({
  className,
  variant = "primary",
  size = "sm",
  disabled,
  loading,
  menuItems,
  children,
  onClick,
  ...props
}: SplitButtonProps) {
  // Fall back to plain Button when there are no menu items
  if (menuItems.length === 0) {
    return (
      <Button
        variant={variant}
        size={size}
        disabled={disabled}
        loading={loading}
        onClick={onClick}
        className={className}
        {...props}
      >
        {children}
      </Button>
    );
  }

  return (
    <div data-comp="SplitButton" className={cn("inline-flex", className)}>
      <Button
        variant={variant}
        size={size}
        disabled={disabled}
        loading={loading}
        onClick={onClick}
        className="rounded-r-none"
        {...props}
      >
        {children}
      </Button>
      <DropdownMenu
        align="right"
        className="w-48"
        trigger={
          <Button
            variant={variant}
            size={size}
            disabled={disabled}
            className="rounded-l-none border-l border-l-white/20 px-2"
            aria-label="More options"
            data-testid="split-menu-btn"
          >
            <Ellipsis className="h-5 w-5" />
          </Button>
        }
      >
        {menuItems.map((item, index) => (
          <button
            key={index}
            role="menuitem"
            onClick={item.onClick}
            className="w-full flex items-center gap-2 px-3 py-2 text-sm text-left text-surface-200 hover:bg-surface-800 hover:text-surface-100 transition-colors cursor-pointer"
          >
            {item.icon}
            {item.label}
          </button>
        ))}
      </DropdownMenu>
    </div>
  );
}
