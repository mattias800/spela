import { Button, Modal } from "@/components/ui";

interface ConfirmDeleteModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  message: string;
  onConfirm: () => void;
  isPending: boolean;
  actionLabel?: string;
}

export function ConfirmDeleteModal({
  open,
  onClose,
  title,
  message,
  onConfirm,
  isPending,
  actionLabel = "Delete",
}: ConfirmDeleteModalProps) {
  return (
    <Modal open={open} onClose={onClose} title={title} size="sm" data-comp="ConfirmDeleteModal">
      <p className="text-sm text-surface-300 mb-6">{message}</p>
      <div className="flex justify-end gap-3">
        <Button variant="secondary" onClick={onClose}>
          Cancel
        </Button>
        <Button variant="danger" loading={isPending} onClick={onConfirm}>
          {actionLabel}
        </Button>
      </div>
    </Modal>
  );
}
