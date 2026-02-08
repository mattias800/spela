import { useState } from "react";
import { Users, Shield, ShieldCheck } from "lucide-react";
import { Button, Badge, Card, Modal, Select, EmptyState, TableRowSkeleton } from "@/components/ui";
import { useAdminUsers, useUpdateUser } from "@/hooks/use-admin";
import { formatDate } from "@/lib/format";
import { useToast } from "@/components/ui";

export function AdminUsersPage() {
  const { data: users, isLoading } = useAdminUsers();
  const updateUser = useUpdateUser();
  const { toast } = useToast();
  const [editingUser, setEditingUser] = useState<string | null>(null);
  const [editRole, setEditRole] = useState<"admin" | "user">("user");

  function handleSaveRole() {
    if (!editingUser) return;
    updateUser.mutate(
      { id: editingUser, data: { role: editRole } },
      {
        onSuccess: () => {
          toast("success", "User role updated");
          setEditingUser(null);
        },
        onError: (err) => toast("error", err.message),
      },
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold text-surface-100">User Management</h1>
        <p className="mt-1 text-surface-400">
          Manage user accounts and permissions.
        </p>
      </div>

      <Card>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-surface-800">
                <th className="text-left px-5 py-3 text-xs font-semibold uppercase tracking-wider text-surface-400">
                  User
                </th>
                <th className="text-left px-5 py-3 text-xs font-semibold uppercase tracking-wider text-surface-400">
                  Email
                </th>
                <th className="text-left px-5 py-3 text-xs font-semibold uppercase tracking-wider text-surface-400">
                  Role
                </th>
                <th className="text-left px-5 py-3 text-xs font-semibold uppercase tracking-wider text-surface-400">
                  Joined
                </th>
                <th className="px-5 py-3" />
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                Array.from({ length: 3 }, (_, i) => (
                  <TableRowSkeleton key={i} columns={5} />
                ))
              ) : !users || users.length === 0 ? (
                <tr>
                  <td colSpan={5}>
                    <EmptyState
                      icon={Users}
                      title="No users"
                      description="No users have been created yet."
                    />
                  </td>
                </tr>
              ) : (
                users.map((user) => (
                  <tr
                    key={user.id}
                    className="border-b border-surface-800/50 hover:bg-surface-800/20 transition-colors"
                  >
                    <td className="px-5 py-3">
                      <div className="flex items-center gap-3">
                        <div className="h-8 w-8 rounded-full bg-gradient-to-br from-brand-500 to-brand-700 flex items-center justify-center text-xs font-bold text-white">
                          {user.username.charAt(0).toUpperCase()}
                        </div>
                        <span className="text-sm font-medium text-surface-100">
                          {user.username}
                        </span>
                      </div>
                    </td>
                    <td className="px-5 py-3 text-sm text-surface-400">
                      {user.email}
                    </td>
                    <td className="px-5 py-3">
                      <Badge variant={user.role === "admin" ? "brand" : "default"}>
                        {user.role === "admin" ? (
                          <ShieldCheck className="h-3 w-3 mr-1" />
                        ) : (
                          <Shield className="h-3 w-3 mr-1" />
                        )}
                        {user.role}
                      </Badge>
                    </td>
                    <td className="px-5 py-3 text-sm text-surface-400">
                      {formatDate(user.createdAt)}
                    </td>
                    <td className="px-5 py-3 text-right">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => {
                          setEditingUser(user.id);
                          setEditRole(user.role);
                        }}
                      >
                        Edit
                      </Button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </Card>

      <Modal
        open={!!editingUser}
        onClose={() => setEditingUser(null)}
        title="Edit User Role"
        size="sm"
      >
        <div className="space-y-4">
          <Select
            label="Role"
            options={[
              { value: "user", label: "User" },
              { value: "admin", label: "Admin" },
            ]}
            value={editRole}
            onChange={(e) => setEditRole(e.target.value as "admin" | "user")}
          />
          <div className="flex justify-end gap-3">
            <Button variant="secondary" onClick={() => setEditingUser(null)}>
              Cancel
            </Button>
            <Button onClick={handleSaveRole} loading={updateUser.isPending}>
              Save
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
