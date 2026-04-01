import { Trash2 } from "lucide-react";
import {
  Button,
  Badge,
  Section,
  EmptyState,
  TableRowSkeleton,
} from "@/components/ui";
import { Users } from "lucide-react";
import { formatDate } from "@/lib/format";
import type { DeletedUser } from "@/types/api";

interface DeletedUsersTableProps {
  users: DeletedUser[] | undefined;
  isLoading: boolean;
  onHardDelete: (user: DeletedUser) => void;
}

export function DeletedUsersTable({
  users,
  isLoading,
  onHardDelete,
}: DeletedUsersTableProps) {
  return (
    <Section>
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
                Deleted
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
                    title="No deleted users"
                    description="There are no soft-deleted users."
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
                      <div className="h-8 w-8 rounded-full bg-surface-700 flex items-center justify-center text-xs font-bold text-surface-400">
                        {user.username.charAt(0).toUpperCase()}
                      </div>
                      <span className="text-sm font-medium text-surface-300">
                        {user.username}
                      </span>
                    </div>
                  </td>
                  <td className="px-5 py-3 text-sm text-surface-400">
                    {user.email}
                  </td>
                  <td className="px-5 py-3">
                    <Badge variant="default">{user.role}</Badge>
                  </td>
                  <td className="px-5 py-3 text-sm text-surface-400">
                    {formatDate(user.deletedAt)}
                  </td>
                  <td className="px-5 py-3 text-right">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => onHardDelete(user)}
                    >
                      <Trash2 className="h-4 w-4 text-danger-500 mr-1.5" />
                      Permanently Delete
                    </Button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </Section>
  );
}
