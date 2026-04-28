import { useState } from "react";
import { Check, Search, Loader2, UserPlus, Clock } from "lucide-react";
import { Button, Modal } from "@/components/ui";
import { useSearchUsers, useRecentPartners } from "@/hooks/use-social";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { Pagination } from "@/components/pagination";
import type { UserSearchResult } from "@/types/api";

interface InviteModalProps {
  title: string;
  closeLabel?: string;
  open: boolean;
  onClose: () => void;
  onInvite: (username: string) => void;
  isInviting: boolean;
  invitingUsername?: string;
}

const PAGE_SIZE = 10;

function UserAvatar({ user }: { user: UserSearchResult }) {
  if (user.avatarUrl) {
    return (
      <img
        src={user.avatarUrl}
        alt=""
        className="h-8 w-8 rounded-full object-cover"
      />
    );
  }
  return (
    <div data-comp="UserAvatar" className="h-8 w-8 rounded-full bg-surface-600 flex items-center justify-center text-xs font-medium text-surface-300">
      {user.username[0]?.toUpperCase()}
    </div>
  );
}

export function InviteModal({
  title,
  closeLabel = "Close",
  open,
  onClose,
  onInvite,
  isInviting,
  invitingUsername,
}: InviteModalProps) {
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(1);
  const [invitedUsers, setInvitedUsers] = useState<string[]>([]);
  const debouncedQuery = useDebouncedValue(query, 300);

  const { data: searchResponse, isLoading: isSearching } = useSearchUsers(
    debouncedQuery,
    page,
    PAGE_SIZE,
  );
  const { data: recentPartners, isLoading: isLoadingPartners } =
    useRecentPartners();

  const showRecentPartners =
    !debouncedQuery && recentPartners && recentPartners.length > 0;

  function handleInvite(user: UserSearchResult) {
    onInvite(user.username);
    setInvitedUsers((prev) => [...prev, user.username]);
  }

  function handleClose() {
    setQuery("");
    setPage(1);
    setInvitedUsers([]);
    onClose();
  }

  function handleSearchChange(value: string) {
    setQuery(value);
    setPage(1);
  }

  const users = searchResponse?.data ?? [];

  function renderUserRow(user: UserSearchResult) {
    const isInvited = invitedUsers.includes(user.username);
    return (
      <div
        key={user.id}
        className="flex items-center justify-between px-3 py-2 rounded-lg hover:bg-surface-800 transition-colors"
      >
        <div className="flex items-center gap-2.5">
          <UserAvatar user={user} />
          <span className="text-sm text-surface-200">{user.username}</span>
        </div>
        {isInvited ? (
          <span className="inline-flex items-center gap-1 px-2 py-1 rounded-full text-xs font-medium bg-brand-500/15 text-brand-400">
            <Check className="h-3 w-3" />
            Invited
          </span>
        ) : (
          <Button
            size="sm"
            variant="secondary"
            onClick={() => handleInvite(user)}
            loading={isInviting && invitingUsername === user.username}
          >
            <UserPlus className="h-3.5 w-3.5" />
            Invite
          </Button>
        )}
      </div>
    );
  }

  return (
    <Modal open={open} onClose={handleClose} title={title} size="md">
      <div className="space-y-4">
        {/* Search input */}
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-surface-500 pointer-events-none" />
          <input
            type="text"
            placeholder="Search users..."
            value={query}
            onChange={(e) => handleSearchChange(e.target.value)}
            autoFocus
            autoComplete="off"
            aria-label="Search users"
            className="w-full rounded-lg border border-surface-700 bg-surface-800 py-2.5 pl-10 pr-3 text-sm text-surface-100 placeholder:text-surface-500 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
          />
        </div>

        {/* Invited this session chips */}
        {invitedUsers.length > 0 && (
          <div className="space-y-1.5">
            <p className="text-xs font-medium text-surface-500">
              Invited this session
            </p>
            <div className="flex flex-wrap gap-2">
              {invitedUsers.map((username) => (
                <span
                  key={username}
                  className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium bg-brand-500/15 text-brand-400"
                >
                  <Check className="h-3 w-3" />
                  {username}
                </span>
              ))}
            </div>
          </div>
        )}

        {/* Recent partners section */}
        {showRecentPartners && (
          <div className="space-y-2">
            <div className="flex items-center gap-1.5">
              <Clock className="h-3.5 w-3.5 text-surface-500" />
              <p className="text-xs font-medium text-surface-500">Previous</p>
            </div>
            <div className="space-y-1">
              {recentPartners.map((partner) => renderUserRow(partner))}
            </div>
          </div>
        )}

        {/* Loading state for partners when no query */}
        {!debouncedQuery && isLoadingPartners && (
          <div className="flex items-center gap-2 px-3 py-2 text-sm text-surface-500">
            <Loader2 className="h-4 w-4 animate-spin" />
            Loading recent partners...
          </div>
        )}

        {/* Divider between partners and user list */}
        {showRecentPartners && (
          <div className="border-t border-surface-800" />
        )}

        {/* User list */}
        <div className="space-y-1">
          {isSearching ? (
            <div className="flex items-center gap-2 px-3 py-4 text-sm text-surface-500">
              <Loader2 className="h-4 w-4 animate-spin" />
              Searching...
            </div>
          ) : users.length > 0 ? (
            users.map((user) => renderUserRow(user))
          ) : (
            <div className="px-3 py-4 text-sm text-surface-500 text-center">
              No users found
            </div>
          )}
        </div>

        {/* Pagination */}
        {searchResponse && (
          <Pagination
            total={searchResponse.total}
            pageSize={PAGE_SIZE}
            currentPage={page}
            onPageChange={setPage}
          />
        )}

        {/* Footer */}
        <div className="flex justify-end pt-2">
          <Button variant="secondary" onClick={handleClose}>
            {closeLabel}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
