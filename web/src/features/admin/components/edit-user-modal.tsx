import { useState } from "react";
import { Button, Modal, Input, Select, Switch } from "@/components/ui";
import { useUpdateUser } from "@/hooks/use-admin";
import { useToast } from "@/components/ui";
import type { User } from "@/types/api";

interface EditUserModalProps {
  user: User | null;
  currentUser: User | null | undefined;
  onClose: () => void;
}

export function EditUserModal({
  user,
  currentUser,
  onClose,
}: EditUserModalProps) {
  return (
    <Modal
      open={!!user}
      onClose={onClose}
      title={user ? `Edit ${user.username}` : "Edit User"}
      size="sm"
    >
      {user && (
        <EditUserForm
          key={user.id}
          user={user}
          currentUser={currentUser}
          onClose={onClose}
        />
      )}
    </Modal>
  );
}

function EditUserForm({
  user,
  currentUser,
  onClose,
}: {
  user: User;
  currentUser: User | null | undefined;
  onClose: () => void;
}) {
  const updateUser = useUpdateUser();
  const { toast } = useToast();

  const [editPassword, setEditPassword] = useState("");
  const [editRole, setEditRole] = useState<string>(user.role);
  const [editDisabled, setEditDisabled] = useState(user.disabled);
  const [editCanImportGames, setEditCanImportGames] = useState(
    user.canImportGames,
  );

  function handleSaveUser() {
    const data: {
      role?: string;
      password?: string;
      disabled?: boolean;
      canImportGames?: boolean;
    } = {};
    if (editPassword) data.password = editPassword;
    if (editRole !== user.role) data.role = editRole;
    if (editDisabled !== user.disabled) data.disabled = editDisabled;
    if (editCanImportGames !== user.canImportGames)
      data.canImportGames = editCanImportGames;

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
    <div data-comp="EditUserForm" className="space-y-4">
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
        disabled={user.role === "owner" || user.id === currentUser?.id}
      />
      {user.role !== "owner" && (
        <div className="flex items-center justify-between gap-3">
          <span className="text-sm text-surface-300">Disable account</span>
          <Switch
            checked={editDisabled}
            onChange={setEditDisabled}
            data-testid="disable-account-toggle"
          />
        </div>
      )}
      {/* Admins/owners can always import; this grants the capability to a
          plain user, so it only applies while the role is "user". */}
      {editRole === "user" && (
        <div className="flex items-center justify-between gap-3">
          <span className="text-sm text-surface-300">
            Can import games from connected servers
          </span>
          <Switch
            checked={editCanImportGames}
            onChange={setEditCanImportGames}
            data-testid="can-import-games-toggle"
          />
        </div>
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
  );
}
