import { ArrowLeft } from "lucide-react";
import { Button } from "./button";
import { cn } from "@/lib/cn";
import { type ButtonHTMLAttributes } from "react";

interface BackButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, "children"> {
  children?: string;
}

export function BackButton({ children = "Back", className, ...props }: BackButtonProps) {
  return (
    <Button
      variant="ghost"
      size="sm"
      className={cn("gap-2 text-surface-400 hover:text-surface-100", className)}
      {...props}
    >
      <ArrowLeft className="h-4 w-4" />
      {children}
    </Button>
  );
}
