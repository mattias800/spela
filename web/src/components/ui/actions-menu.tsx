import { type ReactNode } from "react";
import { Ellipsis } from "lucide-react";
import { cn } from "@/lib/cn";
import { Button } from "./button";
import { DropdownMenu } from "./dropdown-menu";

interface ActionsMenuItem {
  label: string;
  icon?: ReactNode;
  onClick: () => void;
  variant?: "default" | "danger";
  disabled?: boolean;
  loading?: boolean;
}

interface ActionsMenuProps {
  items: ActionsMenuItem[];
  size?: "sm" | "md" | "lg";
  align?: "left" | "right";
}

export function ActionsMenu({
  items,
  size = "sm",
  align = "right",
}: ActionsMenuProps) {
  return (
    <DropdownMenu
      align={align}
      className="w-56"
      trigger={
        <Button variant="secondary" size={size} aria-label="Actions" data-testid="actions-menu-btn">
          <Ellipsis className="h-5 w-5" />
        </Button>
      }
    >
      {items.map((item, index) => (
        <button
          key={index}
          role="menuitem"
          onClick={item.onClick}
          disabled={item.disabled || item.loading}
          className={cn(
            "w-full flex items-center gap-2 px-3 py-2 text-sm text-left transition-colors cursor-pointer",
            "disabled:opacity-50 disabled:pointer-events-none",
            item.variant === "danger"
              ? "text-danger-500 hover:bg-danger-500/10"
              : "text-surface-200 hover:bg-surface-800 hover:text-surface-100",
          )}
        >
          {item.loading ? (
            <span className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
          ) : (
            item.icon
          )}
          {item.label}
        </button>
      ))}
    </DropdownMenu>
  );
}
