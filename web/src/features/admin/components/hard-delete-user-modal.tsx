import { Button, Modal } from "@/components/ui";
import { useHardDeleteUser } from "@/hooks/use-admin";
import { useToast } from "@/components/ui";
import type { DeletedUser } from "@/types/api";

interface HardDeleteUserModalProps {
  user: DeletedUser | null;
  onClose: () => void;
}

export function HardDeleteUserModal({
  user,
  onClose,
}: HardDeleteUserModalProps) {
  const hardDeleteUser = useHardDeleteUser();
  const { toast } = useToast();

  function handleHardDelete() {
    if (!user) return;
    hardDeleteUser.mutate(user.id, {
      onSuccess: () => {
        toast("success", "User permanently deleted");
        onClose();
      },
      onError: (err) => toast("error", err.message),
    });
  }

  return (
    <Modal
      open={!!user}
      onClose={onClose}
      title="Permanently Delete User"
      size="sm"
    >
      <p className="text-sm text-surface-300 mb-6">
        This will{" "}
        <strong className="text-danger-400">permanently and irreversibly</strong>{" "}
        delete{" "}
        <strong className="text-surface-100">{user?.username}</strong> and all
        their data (saves, favorites, play history, ratings, collections). This
        action cannot be undone.
      </p>
      <div className="flex justify-end gap-3">
        <Button variant="secondary" onClick={onClose}>
          Cancel
        </Button>
        <Button
          variant="danger"
          onClick={handleHardDelete}
          loading={hardDeleteUser.isPending}
        >
          Permanently Delete
        </Button>
      </div>
    </Modal>
  );
}
