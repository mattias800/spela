import { useEffect } from "react";
import { ShaderPreview } from "./shader-preview";

interface ShaderPreviewModalProps {
  open: boolean;
  onClose: () => void;
  imageUrl: string;
  shader: string;
}

export function ShaderPreviewModal({
  open,
  onClose,
  imageUrl,
  shader,
}: ShaderPreviewModalProps) {
  useEffect(() => {
    if (!open) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [open, onClose]);

  useEffect(() => {
    if (open) {
      document.body.style.overflow = "hidden";
    } else {
      document.body.style.overflow = "";
    }
    return () => {
      document.body.style.overflow = "";
    };
  }, [open]);

  if (!open) return null;

  return (
    <div data-comp="ShaderPreviewModal"
      className="fixed inset-0 z-50 flex flex-col items-center justify-center"
      onClick={onClose}
    >
      <div className="absolute inset-0 bg-black/90" />
      <div className="relative w-full max-w-4xl px-4">
        <ShaderPreview imageUrl={imageUrl} shader={shader} className="w-full" />
      </div>
      <p className="relative mt-4 text-sm text-surface-500">
        Click anywhere to close
      </p>
    </div>
  );
}
