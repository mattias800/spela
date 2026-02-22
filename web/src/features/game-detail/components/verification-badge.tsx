import { useState, useRef, useEffect, useCallback } from "react";
import { CheckCircle2, AlertTriangle } from "lucide-react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Badge, Button } from "@/components/ui";
import { api } from "@/lib/api-client";
import type { Game } from "@/types/api";

const PREDEFINED_TAGS = [
  "Translation",
  "ROM Hack",
  "Homebrew",
  "Trainer",
  "Prototype",
  "Bad Dump",
  "Overdump",
  "Patched",
] as const;

interface VerificationBadgeProps {
  game: Game;
  isAdmin: boolean;
}

export function VerificationBadge({ game, isAdmin }: VerificationBadgeProps) {
  const [showInfo, setShowInfo] = useState(false);
  const [customTag, setCustomTag] = useState("");
  const queryClient = useQueryClient();
  const dropdownRef = useRef<HTMLDivElement>(null);
  const selectRef = useRef<HTMLSelectElement>(null);

  const close = useCallback(() => setShowInfo(false), []);

  // Close on Escape key
  useEffect(() => {
    if (!showInfo) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === "Escape") close();
    };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [showInfo, close]);

  // Close on click outside
  useEffect(() => {
    if (!showInfo) return;
    function handleClickOutside(e: MouseEvent) {
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(e.target as Node)
      ) {
        close();
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [showInfo, close]);

  // Auto-focus first interactive element when opening
  useEffect(() => {
    if (showInfo && selectRef.current) {
      selectRef.current.focus();
    }
  }, [showInfo]);

  const mutation = useMutation({
    mutationFn: (tag: string) =>
      api.put<void>(`/admin/games/${game.id}/verification-tag`, { tag }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["game", game.id] });
      queryClient.invalidateQueries({ queryKey: ["games"] });
      setShowInfo(false);
      setCustomTag("");
    },
  });

  if (!game.verificationStatus) {
    return null;
  }

  if (game.verificationStatus === "verified") {
    return (
      <Badge variant="success" data-testid="verification-badge">
        <CheckCircle2 className="h-3.5 w-3.5 mr-1" />
        Verified
      </Badge>
    );
  }

  const displayText = game.verificationTag || "Unverified";

  return (
    <div className="relative">
      <button
        type="button"
        onClick={() => setShowInfo(!showInfo)}
        className="cursor-pointer rounded-full transition-opacity hover:opacity-80"
      >
        <Badge variant="warning" data-testid="verification-badge">
          <AlertTriangle className="h-3.5 w-3.5 mr-1" />
          {displayText}
        </Badge>
      </button>

      {showInfo && (
        <div
          ref={dropdownRef}
          role="dialog"
          aria-modal="true"
          className="absolute top-full left-0 mt-2 z-50 w-80 rounded-lg border border-surface-700 bg-surface-800 p-4 shadow-xl"
        >
          <p className="text-sm text-surface-300 mb-3">
            This ROM does not match any known verified dump.
          </p>

          {isAdmin && (
            <div className="space-y-2">
              <label className="text-xs font-medium text-surface-400">
                Set tag
              </label>
              <select
                ref={selectRef}
                className="w-full appearance-none rounded-lg border border-surface-600 bg-surface-700 bg-[url('data:image/svg+xml;charset=utf-8,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20width%3D%2216%22%20height%3D%2216%22%20viewBox%3D%220%200%2024%2024%22%20fill%3D%22none%22%20stroke%3D%22%239ca3af%22%20stroke-width%3D%222%22%20stroke-linecap%3D%22round%22%20stroke-linejoin%3D%22round%22%3E%3Cpath%20d%3D%22m6%209%206%206%206-6%22%2F%3E%3C%2Fsvg%3E')] bg-[length:16px_16px] bg-[right_0.75rem_center] bg-no-repeat px-3 pr-9 py-1.5 text-sm text-surface-200"
                value=""
                onChange={(e) => {
                  const value = e.target.value;
                  if (value === "__custom__") {
                    return;
                  }
                  mutation.mutate(value);
                }}
                data-testid="tag-select"
              >
                <option value="" disabled>
                  Select a tag...
                </option>
                {PREDEFINED_TAGS.map((tag) => (
                  <option key={tag} value={tag}>
                    {tag}
                  </option>
                ))}
                <option value="__custom__">Custom...</option>
              </select>

              <div className="flex gap-2">
                <input
                  type="text"
                  placeholder="Custom tag"
                  value={customTag}
                  onChange={(e) => setCustomTag(e.target.value)}
                  className="flex-1 rounded-lg border border-surface-600 bg-surface-700 px-3 py-1.5 text-sm text-surface-200"
                  data-testid="custom-tag-input"
                />
                <Button
                  size="sm"
                  onClick={() => {
                    if (customTag.trim()) {
                      mutation.mutate(customTag.trim());
                    }
                  }}
                  loading={mutation.isPending}
                  data-testid="save-custom-tag"
                >
                  Save
                </Button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
