import { AlertTriangle } from "lucide-react";
import { Modal, Button } from "@/components/ui";

export type CoreMismatchChoice = "try-loading" | "sram-only" | "fresh";

interface CoreMismatchModalProps {
  open: boolean;
  onChoice: (choice: CoreMismatchChoice) => void;
}

export function CoreMismatchModal({ open, onChoice }: CoreMismatchModalProps) {
  return (
    <Modal
      open={open}
      onClose={() => onChoice("sram-only")}
      title="Save State Compatibility Warning"
    >
      <div className="space-y-4">
        <div className="flex items-start gap-3">
          <AlertTriangle className="h-6 w-6 text-warning-500 flex-shrink-0 mt-0.5" />
          <p className="text-sm text-surface-300">
            Your last save state was created with a different emulator
            configuration. It may not load correctly on this platform.
          </p>
        </div>

        <div className="pt-2">
          <div className="space-y-1">
            <Button
              variant="primary"
              className="w-full justify-center"
              onClick={() => onChoice("sram-only")}
            >
              Start with Game Save Only
            </Button>
            <p className="text-xs text-surface-500 text-center px-4">
              Loads your in-game progress (battery save) but skips the save state.
              The game starts from the title screen.
            </p>
          </div>
          <div className="mt-4 space-y-2">
            <Button
              variant="secondary"
              className="w-full justify-center"
              onClick={() => onChoice("try-loading")}
            >
              Try Loading Anyway
            </Button>
            <Button
              variant="ghost"
              className="w-full justify-center"
              onClick={() => onChoice("fresh")}
            >
              Start Fresh
            </Button>
          </div>
        </div>
      </div>
    </Modal>
  );
}
