import { Button, Modal } from "@/components/ui";
import type { Device } from "@/types/api";

interface DeleteDeviceModalProps {
  device: Device | null;
  onClose: () => void;
  onConfirm: () => void;
  isDeleting: boolean;
}

export function DeleteDeviceModal({
  device,
  onClose,
  onConfirm,
  isDeleting,
}: DeleteDeviceModalProps) {
  return (
    <Modal open={!!device} onClose={onClose} title="Delete Device" size="sm">
      <p className="text-sm text-surface-300 mb-6">
        This will remove{" "}
        <strong className="text-surface-100">{device?.name}</strong> and all its
        shader overrides. This action cannot be undone.
      </p>
      <div className="flex justify-end gap-3">
        <Button variant="secondary" onClick={onClose}>
          Cancel
        </Button>
        <Button variant="danger" onClick={onConfirm} loading={isDeleting}>
          Delete Device
        </Button>
      </div>
    </Modal>
  );
}
