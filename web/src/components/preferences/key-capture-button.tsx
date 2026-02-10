import { useState, useEffect, useRef } from "react";
import { formatKeyName } from "@/lib/key-mapping-constants";

interface KeyCaptureButtonProps {
  value: string;
  onChange: (key: string) => void;
}

export function KeyCaptureButton({ value, onChange }: KeyCaptureButtonProps) {
  const [listening, setListening] = useState(false);
  const buttonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!listening) return;

    function handleKeyDown(e: KeyboardEvent) {
      e.preventDefault();
      e.stopPropagation();
      if (e.key === "Escape") {
        setListening(false);
        return;
      }
      onChange(e.key.toLowerCase());
      setListening(false);
    }

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [listening, onChange]);

  return (
    <button
      ref={buttonRef}
      type="button"
      onClick={() => setListening(true)}
      className={`
        inline-flex items-center justify-center min-w-[3.5rem] px-2.5 py-1.5 rounded-lg text-sm font-mono
        transition-all duration-150
        ${listening
          ? "bg-brand-500/20 border-brand-400 text-brand-300 ring-2 ring-brand-500/40 animate-pulse"
          : "bg-surface-800 border-surface-700 text-surface-200 hover:bg-surface-700 hover:border-surface-600"
        }
        border cursor-pointer
      `}
    >
      {listening ? "..." : formatKeyName(value)}
    </button>
  );
}
