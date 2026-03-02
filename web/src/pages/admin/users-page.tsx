import { useState } from "react";
import { Plus } from "lucide-react";
import { Button, StateTabNav, StateTabItem } from "@/components/ui";
import {
  useAdminUsers,
  useAdminStats,
  useUpdateUser,
  useDeletedUsers,
} from "@/hooks/use-admin";
import { useAuth } from "@/hooks/use-auth";
import { UserStatsGrid } from "@/features/admin/components/user-stats-grid";
import { UserTable } from "@/features/admin/components/user-table";
import { DeletedUsersTable } from "@/features/admin/components/deleted-users-table";
import { EditUserModal } from "@/features/admin/components/edit-user-modal";
import { CreateUserModal } from "@/features/admin/components/create-user-modal";
import { DeleteUserModal } from "@/features/admin/components/delete-user-modal";
import { HardDeleteUserModal } from "@/features/admin/components/hard-delete-user-modal";
import { AdminDevicesModal } from "@/features/admin/components/admin-devices-modal";
import type { User, DeletedUser } from "@/types/api";

type Tab = "active" | "deleted";

export function AdminUsersPage() {
  const { data: users, isLoading } = useAdminUsers();
  const { data: stats } = useAdminStats();
  const { data: deletedUsers, isLoading: isLoadingDeleted } = useDeletedUsers();
  const { user: currentUser } = useAuth();
  const { mutate: updateUser } = useUpdateUser();

  const [tab, setTab] = useState<Tab>("active");
  const [editingUser, setEditingUser] = useState<User | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<User | null>(null);
  const [hardDeleteTarget, setHardDeleteTarget] = useState<DeletedUser | null>(
    null,
  );
  const [devicesUser, setDevicesUser] = useState<User | null>(null);
  const [showCreate, setShowCreate] = useState(false);

  function handleApprove(user: User) {
    updateUser({ id: user.id, data: { pendingApproval: false } });
  }

  const deletedCount = deletedUsers?.length ?? 0;

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
        {tab === "active" && (
          <Button onClick={() => setShowCreate(true)}>
            <Plus className="h-4 w-4 mr-1.5" />
            Create User
          </Button>
        )}
      </div>

      {stats && <UserStatsGrid stats={stats} />}

      <StateTabNav>
        <StateTabItem active={tab === "active"} onClick={() => setTab("active")}>
          Active Users
        </StateTabItem>
        <StateTabItem
          active={tab === "deleted"}
          onClick={() => setTab("deleted")}
        >
          Deleted Users
          {deletedCount > 0 && (
            <span className="ml-1.5 rounded-full bg-surface-700 px-2 py-0.5 text-xs text-surface-300">
              {deletedCount}
            </span>
          )}
        </StateTabItem>
      </StateTabNav>

      {tab === "active" ? (
        <UserTable
          users={users}
          currentUser={currentUser}
          isLoading={isLoading}
          onEdit={setEditingUser}
          onDelete={setDeleteTarget}
          onViewDevices={setDevicesUser}
          onApprove={handleApprove}
        />
      ) : (
        <DeletedUsersTable
          users={deletedUsers}
          isLoading={isLoadingDeleted}
          onHardDelete={setHardDeleteTarget}
        />
      )}

      <EditUserModal
        user={editingUser}
        currentUser={currentUser}
        onClose={() => setEditingUser(null)}
      />
      <DeleteUserModal
        user={deleteTarget}
        onClose={() => setDeleteTarget(null)}
      />
      <HardDeleteUserModal
        user={hardDeleteTarget}
        onClose={() => setHardDeleteTarget(null)}
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
