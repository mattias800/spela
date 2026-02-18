import { useState } from "react";
import { Button, Modal, Input, Select } from "@/components/ui";
import { useCreateUser } from "@/hooks/use-admin";
import { useToast } from "@/components/ui";

interface CreateUserModalProps {
  open: boolean;
  onClose: () => void;
}

export function CreateUserModal({ open, onClose }: CreateUserModalProps) {
  const createUser = useCreateUser();
  const { toast } = useToast();

  const [newUsername, setNewUsername] = useState("");
  const [newEmail, setNewEmail] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [newRole, setNewRole] = useState<"admin" | "user">("user");

  function handleCreateUser() {
    if (!newUsername || !newEmail || !newPassword) return;
    createUser.mutate(
      {
        username: newUsername,
        email: newEmail,
        password: newPassword,
        role: newRole,
      },
      {
        onSuccess: () => {
          toast("success", "User created");
          onClose();
          setNewUsername("");
          setNewEmail("");
          setNewPassword("");
          setNewRole("user");
        },
        onError: (err) => toast("error", err.message),
      },
    );
  }

  return (
    <Modal open={open} onClose={onClose} title="Create User" size="sm">
      <div className="space-y-4">
        <Input
          label="Username"
          placeholder="Username (min 3 characters)"
          value={newUsername}
          onChange={(e) => setNewUsername(e.target.value)}
        />
        <Input
          label="Email"
          type="email"
          placeholder="user@example.com"
          value={newEmail}
          onChange={(e) => setNewEmail(e.target.value)}
        />
        <Input
          label="Password"
          type="password"
          placeholder="Min 8 characters"
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
        />
        <Select
          label="Role"
          options={[
            { value: "user", label: "User" },
            { value: "admin", label: "Admin" },
          ]}
          value={newRole}
          onChange={(e) => setNewRole(e.target.value as "admin" | "user")}
        />
        <div className="flex justify-end gap-3">
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={handleCreateUser} loading={createUser.isPending}>
            Create
          </Button>
        </div>
      </div>
    </Modal>
  );
}
