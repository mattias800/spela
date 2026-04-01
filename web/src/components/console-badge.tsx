import { Badge } from "@/components/ui";
import { getConsoleStyle } from "@/lib/console-metadata";

interface ConsoleBadgeProps {
  /** Platform code for color lookup (e.g., "snes", "nes", "gba").
   *  This is the console's `code` field, or `consoleId` from game responses. */
  code: string;
  /** Optional display label. Defaults to code.toUpperCase(). */
  label?: string;
  className?: string;
}

/**
 * ROLE component — a badge that represents a console platform.
 *
 * Layer 3 in the component hierarchy (Design → Content → Role).
 * Thin wrapper around Badge — maps a platform code to the correct brand
 * color from the authoritative console-metadata registry.
 *
 * All console badges across the web UI must use this, never raw Badge
 * with inline color styling.
 */
export function ConsoleBadge({ code, label, className }: ConsoleBadgeProps) {
  const { color } = getConsoleStyle(code);

  return (
    <Badge
      className={className}
      style={{
        backgroundColor: `${color}40`,
        borderColor: `${color}60`,
        color: "white",
      }}
    >
      {label ?? code.toUpperCase()}
    </Badge>
  );
}
