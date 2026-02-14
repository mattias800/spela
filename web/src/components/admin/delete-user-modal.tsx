import { Button, Modal } from "@/components/ui";
import { useDeleteUser } from "@/hooks/use-admin";
import { useToast } from "@/components/ui";
import type { User } from "@/types/api";

interface DeleteUserModalProps {
  user: User | null;
  onClose: () => void;
}

export function DeleteUserModal({ user, onClose }: DeleteUserModalProps) {
  const deleteUser = useDeleteUser();
  const { toast } = useToast();

  function handleDeleteUser() {
    if (!user) return;
    deleteUser.mutate(user.id, {
      onSuccess: () => {
        toast("success", "User deleted");
        onClose();
      },
      onError: (err) => toast("error", err.message),
    });
  }

  return (
    <Modal open={!!user} onClose={onClose} title="Delete User" size="sm">
      <p className="text-sm text-surface-300 mb-6">
        This will permanently delete{" "}
        <strong className="text-surface-100">{user?.username}</strong> and all
        their data (saves, favorites, play history). This action cannot be
        undone.
      </p>
      <div className="flex justify-end gap-3">
        <Button variant="secondary" onClick={onClose}>
          Cancel
        </Button>
        <Button
          variant="danger"
          onClick={handleDeleteUser}
          loading={deleteUser.isPending}
        >
          Delete User
        </Button>
      </div>
    </Modal>
  );
}
