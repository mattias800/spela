import { useEffect, useState } from "react";
import { Button, Input, Modal } from "@/components/ui";

interface CloneSessionDialogProps {
  open: boolean;
  onClose: () => void;
  /**
   * Display label for what is being cloned — the source session's name.
   * Used to pre-fill the name field as `{sourceName} (Copy)` and shown
   * in the dialog copy so the user knows what they are cloning.
   */
  sourceName: string;
  /** Optional override for the dialog title. */
  title?: string;
  /**
   * Optional hint line shown above the name field — e.g.
   * "Cloning from this save" for US-3 or "Cloning to your library" for
   * US-1. When omitted the dialog shows a minimal default.
   */
  description?: string;
  /** Label on the confirm button. Defaults to "Clone". */
  confirmLabel?: string;
  isPending: boolean;
  onConfirm: (name: string) => void;
}

/**
 * Confirmation dialog for cloning a session. Matches the rename dialog
 * pattern (editable name, Cancel + confirm) — deliberately small because
 * cloning is a secondary action.
 */
export function CloneSessionDialog({
  open,
  onClose,
  sourceName,
  title = "Clone Session",
  description,
  confirmLabel = "Clone",
  isPending,
  onConfirm,
}: CloneSessionDialogProps) {
  const defaultName = `${sourceName} (Copy)`;
  const [name, setName] = useState(defaultName);

  // Reset the name to the derived default every time the dialog opens.
  // The user may open it again for a different save — the previous value
  // must not leak through.
  useEffect(() => {
    if (open) setName(defaultName);
  }, [open, defaultName]);

  function handleSubmit() {
    if (isPending) return;
    const trimmed = name.trim();
    // An empty submission falls through to the backend default
    // (`{source.Name} (Copy)`) — no need to guard client-side.
    onConfirm(trimmed);
  }

  return (
    <Modal open={open} onClose={onClose} title={title} size="sm">
      <div className="space-y-4" data-testid="clone-session-dialog">
        {description && (
          <p className="text-sm text-surface-300">{description}</p>
        )}
        <div>
          <label
            htmlFor="clone-session-name"
            className="block text-sm font-medium text-surface-200 mb-1.5"
          >
            New session name
          </label>
          <Input
            id="clone-session-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") handleSubmit();
              if (e.key === "Escape") onClose();
            }}
            data-testid="clone-session-name-input"
            autoFocus
          />
        </div>
        <div className="flex justify-end gap-3">
          <Button
            variant="secondary"
            onClick={onClose}
            disabled={isPending}
            data-testid="clone-session-cancel"
          >
            Cancel
          </Button>
          <Button
            variant="primary"
            loading={isPending}
            onClick={handleSubmit}
            data-testid="clone-session-confirm"
          >
            {confirmLabel}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
