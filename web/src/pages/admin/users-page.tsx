import { useState } from "react";
import { Plus } from "lucide-react";
import { Button } from "@/components/ui";
import { useAdminUsers, useAdminStats, useUpdateUser } from "@/hooks/use-admin";
import { useAuth } from "@/hooks/use-auth";
import { UserStatsGrid } from "@/features/admin/components/user-stats-grid";
import { UserTable } from "@/features/admin/components/user-table";
import { EditUserModal } from "@/features/admin/components/edit-user-modal";
import { CreateUserModal } from "@/features/admin/components/create-user-modal";
import { DeleteUserModal } from "@/features/admin/components/delete-user-modal";
import { AdminDevicesModal } from "@/features/admin/components/admin-devices-modal";
import type { User } from "@/types/api";

export function AdminUsersPage() {
  const { data: users, isLoading } = useAdminUsers();
  const { data: stats } = useAdminStats();
  const { user: currentUser } = useAuth();
  const { mutate: updateUser } = useUpdateUser();

  const [editingUser, setEditingUser] = useState<User | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<User | null>(null);
  const [devicesUser, setDevicesUser] = useState<User | null>(null);
  const [showCreate, setShowCreate] = useState(false);

  function handleApprove(user: User) {
    updateUser({ id: user.id, data: { pendingApproval: false } });
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-surface-100">
            User Management
          </h1>
          <p className="mt-1 text-surface-400">
            Manage user accounts and permissions.
          </p>
        </div>
        <Button onClick={() => setShowCreate(true)}>
          <Plus className="h-4 w-4 mr-1.5" />
          Create User
        </Button>
      </div>

      {stats && <UserStatsGrid stats={stats} />}

      <UserTable
        users={users}
        currentUser={currentUser}
        isLoading={isLoading}
        onEdit={setEditingUser}
        onDelete={setDeleteTarget}
        onViewDevices={setDevicesUser}
        onApprove={handleApprove}
      />

      <EditUserModal
        user={editingUser}
        currentUser={currentUser}
        onClose={() => setEditingUser(null)}
      />
      <DeleteUserModal
        user={deleteTarget}
        onClose={() => setDeleteTarget(null)}
      />
      <CreateUserModal open={showCreate} onClose={() => setShowCreate(false)} />

      {devicesUser && (
        <AdminDevicesModal
          user={devicesUser}
          onClose={() => setDevicesUser(null)}
        />
      )}
    </div>
  );
}
