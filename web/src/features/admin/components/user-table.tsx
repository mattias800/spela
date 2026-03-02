import {
  Shield,
  ShieldCheck,
  Crown,
  Trash2,
  UserX,
  Clock,
  AlertTriangle,
} from "lucide-react";
import {
  Button,
  Badge,
  Card,
  EmptyState,
  TableRowSkeleton,
} from "@/components/ui";
import { Users } from "lucide-react";
import { formatDate } from "@/lib/format";
import { cn } from "@/lib/cn";
import type { User } from "@/types/api";
import { useUserRateLimit, useResetRateLimit } from "@/hooks/use-admin";

interface UserTableProps {
  users: User[] | undefined;
  currentUser: User | null | undefined;
  isLoading: boolean;
  onEdit: (user: User) => void;
  onDelete: (user: User) => void;
  onViewDevices: (user: User) => void;
  onApprove: (user: User) => void;
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

function RateLimitBadge({ userId }: { userId: string }) {
  const { data } = useUserRateLimit(userId);
  const resetMutation = useResetRateLimit();

  if (!data || (!data.isLockedOut && data.failedCount === 0)) {
    return null;
  }

  if (data.isLockedOut) {
    return (
      <div className="flex items-center gap-1">
        <Badge variant="danger">
          <AlertTriangle className="h-3 w-3 mr-1" />
          locked out
        </Badge>
        <Button
          variant="ghost"
          size="sm"
          onClick={() => resetMutation.mutate(userId)}
          disabled={resetMutation.isPending}
        >
          Reset
        </Button>
      </div>
    );
  }

  return (
    <Badge variant="warning">
      {data.failedCount} failed login{data.failedCount !== 1 ? "s" : ""}
    </Badge>
  );
}

export function UserTable({
  users,
  currentUser,
  isLoading,
  onEdit,
  onDelete,
  onViewDevices,
  onApprove,
}: UserTableProps) {
  const isOwnerOrSelf = (user: User) =>
    user.role === "owner" || user.id === currentUser?.id;

  return (
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
                  className={cn(
                    "border-b border-surface-800/50 hover:bg-surface-800/20 transition-colors",
                    user.id === currentUser?.id && "bg-surface-800/10",
                  )}
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
                          <span className="ml-2 text-xs text-surface-500">
                            (you)
                          </span>
                        )}
                      </div>
                    </div>
                  </td>
                  <td className="px-5 py-3 text-sm text-surface-400">
                    {user.email}
                  </td>
                  <td className="px-5 py-3">{getRoleBadge(user.role)}</td>
                  <td className="px-5 py-3">
                    <div className="flex flex-col gap-1">
                      {user.pendingApproval ? (
                        <Badge variant="warning">
                          <Clock className="h-3 w-3 mr-1" />
                          pending
                        </Badge>
                      ) : user.disabled ? (
                        <Badge variant="danger">
                          <UserX className="h-3 w-3 mr-1" />
                          disabled
                        </Badge>
                      ) : (
                        <span className="text-xs text-surface-500">active</span>
                      )}
                      <RateLimitBadge userId={user.id} />
                    </div>
                  </td>
                  <td className="px-5 py-3 text-sm text-surface-400">
                    {formatDate(user.createdAt)}
                  </td>
                  <td className="px-5 py-3 text-right">
                    <div className="flex items-center justify-end gap-1">
                      {user.pendingApproval && (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => onApprove(user)}
                        >
                          Approve
                        </Button>
                      )}
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => onViewDevices(user)}
                      >
                        Devices
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => onEdit(user)}
                      >
                        Edit
                      </Button>
                      {!isOwnerOrSelf(user) && (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => onDelete(user)}
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
  );
}
