import { useState, useEffect } from "react";
import { Button, Modal, Input, Select } from "@/components/ui";
import { useUpdateUser } from "@/hooks/use-admin";
import { useToast } from "@/components/ui";
import type { User } from "@/types/api";

interface EditUserModalProps {
  user: User | null;
  currentUser: User | undefined;
  onClose: () => void;
}

export function EditUserModal({ user, currentUser, onClose }: EditUserModalProps) {
  const updateUser = useUpdateUser();
  const { toast } = useToast();

  const [editEmail, setEditEmail] = useState("");
  const [editPassword, setEditPassword] = useState("");
  const [editRole, setEditRole] = useState<string>("user");
  const [editDisabled, setEditDisabled] = useState(false);

  useEffect(() => {
    if (user) {
      setEditEmail(user.email);
      setEditPassword("");
      setEditRole(user.role);
      setEditDisabled(user.disabled);
    }
  }, [user]);

  function handleSaveUser() {
    if (!user) return;
    const data: { role?: string; email?: string; password?: string; disabled?: boolean } = {};
    if (editEmail !== user.email) data.email = editEmail;
    if (editPassword) data.password = editPassword;
    if (editRole !== user.role) data.role = editRole;
    if (editDisabled !== user.disabled) data.disabled = editDisabled;

    updateUser.mutate(
      { id: user.id, data },
      {
        onSuccess: () => {
          toast("success", "User updated");
          onClose();
        },
        onError: (err) => toast("error", err.message),
      },
    );
  }

  return (
    <Modal
      open={!!user}
      onClose={onClose}
      title="Edit User"
      size="sm"
    >
      <div className="space-y-4">
        <Input
          label="Email"
          type="email"
          value={editEmail}
          onChange={(e) => setEditEmail(e.target.value)}
        />
        <Input
          label="Password"
          type="password"
          placeholder="Leave blank to keep current"
          value={editPassword}
          onChange={(e) => setEditPassword(e.target.value)}
        />
        <Select
          label="Role"
          options={[
            { value: "user", label: "User" },
            { value: "admin", label: "Admin" },
          ]}
          value={editRole}
          onChange={(e) => setEditRole(e.target.value)}
          disabled={user?.role === "owner" || user?.id === currentUser?.id}
        />
        {user?.role !== "owner" && (
          <label className="flex items-center gap-3 cursor-pointer">
            <input
              type="checkbox"
              checked={editDisabled}
              onChange={(e) => setEditDisabled(e.target.checked)}
              className="h-4 w-4 rounded border-surface-600 bg-surface-800 text-brand-500 focus:ring-brand-500"
            />
            <span className="text-sm text-surface-300">Disable account</span>
          </label>
        )}
        <div className="flex justify-end gap-3">
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={handleSaveUser} loading={updateUser.isPending}>
            Save
          </Button>
        </div>
      </div>
    </Modal>
  );
}
