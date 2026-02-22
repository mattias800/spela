import { useState } from "react";
import { CheckCircle2, AlertTriangle } from "lucide-react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Badge } from "@/components/ui";
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
      <button type="button" onClick={() => setShowInfo(!showInfo)}>
        <Badge variant="warning" data-testid="verification-badge">
          <AlertTriangle className="h-3.5 w-3.5 mr-1" />
          {displayText}
        </Badge>
      </button>

      {showInfo && (
        <div className="absolute top-full left-0 mt-2 z-50 w-64 rounded-lg border border-surface-700 bg-surface-800 p-4 shadow-xl">
          <p className="text-sm text-surface-300 mb-3">
            This ROM does not match any known verified dump.
          </p>

          {isAdmin && (
            <div className="space-y-2">
              <label className="text-xs font-medium text-surface-400">
                Set tag
              </label>
              <select
                className="w-full rounded-md border border-surface-600 bg-surface-700 px-2 py-1.5 text-sm text-surface-200"
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
                  className="flex-1 rounded-md border border-surface-600 bg-surface-700 px-2 py-1.5 text-sm text-surface-200"
                  data-testid="custom-tag-input"
                />
                <button
                  type="button"
                  onClick={() => {
                    if (customTag.trim()) {
                      mutation.mutate(customTag.trim());
                    }
                  }}
                  className="rounded-md bg-brand-500 px-3 py-1.5 text-xs font-medium text-white hover:bg-brand-600"
                  data-testid="save-custom-tag"
                >
                  Save
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
