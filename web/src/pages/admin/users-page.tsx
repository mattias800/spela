import { useState } from "react";
import { Plus } from "lucide-react";
import { Button } from "@/components/ui";
import { useAdminUsers, useAdminStats } from "@/hooks/use-admin";
import { useAuth } from "@/hooks/use-auth";
import { UserStatsGrid } from "@/components/admin/user-stats-grid";
import { UserTable } from "@/components/admin/user-table";
import { EditUserModal } from "@/components/admin/edit-user-modal";
import { CreateUserModal } from "@/components/admin/create-user-modal";
import { DeleteUserModal } from "@/components/admin/delete-user-modal";
import { AdminDevicesModal } from "@/components/admin/admin-devices-modal";
import type { User } from "@/types/api";

export function AdminUsersPage() {
  const { data: users, isLoading } = useAdminUsers();
  const { data: stats } = useAdminStats();
  const { user: currentUser } = useAuth();

  const [editingUser, setEditingUser] = useState<User | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<User | null>(null);
  const [devicesUser, setDevicesUser] = useState<User | null>(null);
  const [showCreate, setShowCreate] = useState(false);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-surface-100">User Management</h1>
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
      />

      <EditUserModal user={editingUser} currentUser={currentUser} onClose={() => setEditingUser(null)} />
      <DeleteUserModal user={deleteTarget} onClose={() => setDeleteTarget(null)} />
      <CreateUserModal open={showCreate} onClose={() => setShowCreate(false)} />

      {devicesUser && (
        <AdminDevicesModal user={devicesUser} onClose={() => setDevicesUser(null)} />
      )}
    </div>
  );
}
