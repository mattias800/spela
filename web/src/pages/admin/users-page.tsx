import { useState } from "react";
import { Users, Shield, ShieldCheck, Crown, Plus, Trash2, UserX, BarChart3, Gamepad2, Save, Monitor, Smartphone, Laptop } from "lucide-react";
import { Button, Badge, Card, CardContent, Modal, Input, Select, EmptyState, TableRowSkeleton } from "@/components/ui";
import { useAdminUsers, useUpdateUser, useCreateUser, useDeleteUser, useAdminStats } from "@/hooks/use-admin";
import { useAdminUserDevices } from "@/hooks/use-devices";
import { useAuth } from "@/hooks/use-auth";
import { formatDate, formatRelativeTime } from "@/lib/format";
import { useToast } from "@/components/ui";
import type { User } from "@/types/api";

export function AdminUsersPage() {
  const { data: users, isLoading } = useAdminUsers();
  const { data: stats } = useAdminStats();
  const updateUser = useUpdateUser();
  const createUser = useCreateUser();
  const deleteUser = useDeleteUser();
  const { user: currentUser } = useAuth();
  const { toast } = useToast();

  const [editingUser, setEditingUser] = useState<User | null>(null);
  const [editEmail, setEditEmail] = useState("");
  const [editPassword, setEditPassword] = useState("");
  const [editRole, setEditRole] = useState<string>("user");
  const [editDisabled, setEditDisabled] = useState(false);

  const [deleteTarget, setDeleteTarget] = useState<User | null>(null);
  const [devicesUser, setDevicesUser] = useState<User | null>(null);

  const [showCreate, setShowCreate] = useState(false);
  const [newUsername, setNewUsername] = useState("");
  const [newEmail, setNewEmail] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [newRole, setNewRole] = useState<"admin" | "user">("user");

  function openEditModal(user: User) {
    setEditingUser(user);
    setEditEmail(user.email);
    setEditPassword("");
    setEditRole(user.role);
    setEditDisabled(user.disabled);
  }

  function handleSaveUser() {
    if (!editingUser) return;
    const data: { role?: string; email?: string; password?: string; disabled?: boolean } = {};
    if (editEmail !== editingUser.email) data.email = editEmail;
    if (editPassword) data.password = editPassword;
    if (editRole !== editingUser.role) data.role = editRole;
    if (editDisabled !== editingUser.disabled) data.disabled = editDisabled;

    updateUser.mutate(
      { id: editingUser.id, data },
      {
        onSuccess: () => {
          toast("success", "User updated");
          setEditingUser(null);
        },
        onError: (err) => toast("error", err.message),
      },
    );
  }

  function handleDeleteUser() {
    if (!deleteTarget) return;
    deleteUser.mutate(deleteTarget.id, {
      onSuccess: () => {
        toast("success", "User deleted");
        setDeleteTarget(null);
      },
      onError: (err) => toast("error", err.message),
    });
  }

  function handleCreateUser() {
    if (!newUsername || !newEmail || !newPassword) return;
    createUser.mutate(
      { username: newUsername, email: newEmail, password: newPassword, role: newRole },
      {
        onSuccess: () => {
          toast("success", "User created");
          setShowCreate(false);
          setNewUsername("");
          setNewEmail("");
          setNewPassword("");
          setNewRole("user");
        },
        onError: (err) => toast("error", err.message),
      },
    );
  }

  function getRoleBadge(role: string) {
    switch (role) {
      case "owner":
        return (
          <Badge variant="warning">
            <Crown className="h-3 w-3 mr-1" />
            owner
          </Badge>
        );
      case "admin":
        return (
          <Badge variant="brand">
            <ShieldCheck className="h-3 w-3 mr-1" />
            admin
          </Badge>
        );
      default:
        return (
          <Badge variant="default">
            <Shield className="h-3 w-3 mr-1" />
            user
          </Badge>
        );
    }
  }

  const isOwnerOrSelf = (user: User) =>
    user.role === "owner" || user.id === currentUser?.id;

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

      {/* Stats */}
      {stats && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <Card>
            <CardContent className="flex items-center gap-3 py-4">
              <div className="h-10 w-10 rounded-xl bg-brand-500/10 flex items-center justify-center">
                <Users className="h-5 w-5 text-brand-400" />
              </div>
              <div>
                <p className="text-2xl font-bold text-surface-100">{stats.users}</p>
                <p className="text-xs text-surface-500">Users</p>
              </div>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="flex items-center gap-3 py-4">
              <div className="h-10 w-10 rounded-xl bg-brand-500/10 flex items-center justify-center">
                <Gamepad2 className="h-5 w-5 text-brand-400" />
              </div>
              <div>
                <p className="text-2xl font-bold text-surface-100">{stats.games}</p>
                <p className="text-xs text-surface-500">Games</p>
              </div>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="flex items-center gap-3 py-4">
              <div className="h-10 w-10 rounded-xl bg-brand-500/10 flex items-center justify-center">
                <BarChart3 className="h-5 w-5 text-brand-400" />
              </div>
              <div>
                <p className="text-2xl font-bold text-surface-100">{stats.consoles}</p>
                <p className="text-xs text-surface-500">Consoles</p>
              </div>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="flex items-center gap-3 py-4">
              <div className="h-10 w-10 rounded-xl bg-brand-500/10 flex items-center justify-center">
                <Save className="h-5 w-5 text-brand-400" />
              </div>
              <div>
                <p className="text-2xl font-bold text-surface-100">{stats.saves}</p>
                <p className="text-xs text-surface-500">Saves</p>
              </div>
            </CardContent>
          </Card>
        </div>
      )}

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
                  Status
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
                  <TableRowSkeleton key={i} columns={6} />
                ))
              ) : !users || users.length === 0 ? (
                <tr>
                  <td colSpan={6}>
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
                    className={`border-b border-surface-800/50 hover:bg-surface-800/20 transition-colors ${user.id === currentUser?.id ? "bg-surface-800/10" : ""}`}
                  >
                    <td className="px-5 py-3">
                      <div className="flex items-center gap-3">
                        <div className="h-8 w-8 rounded-full bg-gradient-to-br from-brand-500 to-brand-700 flex items-center justify-center text-xs font-bold text-white">
                          {user.username.charAt(0).toUpperCase()}
                        </div>
                        <div>
                          <span className="text-sm font-medium text-surface-100">
                            {user.username}
                          </span>
                          {user.id === currentUser?.id && (
                            <span className="ml-2 text-xs text-surface-500">(you)</span>
                          )}
                        </div>
                      </div>
                    </td>
                    <td className="px-5 py-3 text-sm text-surface-400">
                      {user.email}
                    </td>
                    <td className="px-5 py-3">
                      {getRoleBadge(user.role)}
                    </td>
                    <td className="px-5 py-3">
                      {user.disabled ? (
                        <Badge variant="danger">
                          <UserX className="h-3 w-3 mr-1" />
                          disabled
                        </Badge>
                      ) : (
                        <span className="text-xs text-surface-500">active</span>
                      )}
                    </td>
                    <td className="px-5 py-3 text-sm text-surface-400">
                      {formatDate(user.createdAt)}
                    </td>
                    <td className="px-5 py-3 text-right">
                      <div className="flex items-center justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setDevicesUser(user)}
                        >
                          Devices
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => openEditModal(user)}
                        >
                          Edit
                        </Button>
                        {!isOwnerOrSelf(user) && (
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => setDeleteTarget(user)}
                          >
                            <Trash2 className="h-4 w-4 text-danger-500" />
                          </Button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </Card>

      {/* Edit User Modal */}
      <Modal
        open={!!editingUser}
        onClose={() => setEditingUser(null)}
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
            disabled={editingUser?.role === "owner" || editingUser?.id === currentUser?.id}
          />
          {editingUser?.role !== "owner" && (
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
            <Button variant="secondary" onClick={() => setEditingUser(null)}>
              Cancel
            </Button>
            <Button onClick={handleSaveUser} loading={updateUser.isPending}>
              Save
            </Button>
          </div>
        </div>
      </Modal>

      {/* Delete Confirmation Modal */}
      <Modal
        open={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        title="Delete User"
        size="sm"
      >
        <p className="text-sm text-surface-300 mb-6">
          This will permanently delete <strong className="text-surface-100">{deleteTarget?.username}</strong> and all their data (saves, favorites, play history). This action cannot be undone.
        </p>
        <div className="flex justify-end gap-3">
          <Button variant="secondary" onClick={() => setDeleteTarget(null)}>
            Cancel
          </Button>
          <Button variant="danger" onClick={handleDeleteUser} loading={deleteUser.isPending}>
            Delete User
          </Button>
        </div>
      </Modal>

      {/* Create User Modal */}
      <Modal
        open={showCreate}
        onClose={() => setShowCreate(false)}
        title="Create User"
        size="sm"
      >
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
            <Button variant="secondary" onClick={() => setShowCreate(false)}>
              Cancel
            </Button>
            <Button onClick={handleCreateUser} loading={createUser.isPending}>
              Create
            </Button>
          </div>
        </div>
      </Modal>

      {/* User Devices Modal */}
      {devicesUser && (
        <AdminDevicesModal user={devicesUser} onClose={() => setDevicesUser(null)} />
      )}
    </div>
  );
}

function AdminDevicesModal({ user, onClose }: { user: User; onClose: () => void }) {
  const { data: devices, isLoading } = useAdminUserDevices(user.id);

  function getPlatformIcon(platform: string) {
    switch (platform.toLowerCase()) {
      case "android":
        return Smartphone;
      case "macos":
      case "linux":
      case "windows":
        return Laptop;
      default:
        return Monitor;
    }
  }

  return (
    <Modal
      open
      onClose={onClose}
      title={`${user.username}'s Devices`}
      size="md"
    >
      {isLoading ? (
        <div className="space-y-3">
          <div className="h-14 bg-surface-800/50 rounded-lg animate-pulse" />
          <div className="h-14 bg-surface-800/50 rounded-lg animate-pulse" />
        </div>
      ) : !devices || devices.length === 0 ? (
        <p className="text-sm text-surface-400 text-center py-8">No devices registered.</p>
      ) : (
        <div className="space-y-2">
          {devices.map((device) => {
            const PlatformIcon = getPlatformIcon(device.platform);
            return (
              <div key={device.id} className="flex items-center gap-3 rounded-xl border border-surface-800 bg-surface-900/50 px-4 py-3">
                <div className="h-9 w-9 rounded-lg bg-surface-800 flex items-center justify-center">
                  <PlatformIcon className="h-4 w-4 text-surface-400" />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-surface-200 truncate">{device.name}</p>
                  <div className="flex items-center gap-2">
                    <Badge variant="default">{device.platform}</Badge>
                    <span className="text-xs text-surface-500">Last seen {formatRelativeTime(device.lastSeenAt)}</span>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </Modal>
  );
}
