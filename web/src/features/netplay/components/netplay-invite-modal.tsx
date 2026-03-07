import { useState, useRef, useEffect } from "react";
import { UserPlus, Check, Search, Loader2 } from "lucide-react";
import { Button, Modal } from "@/components/ui";
import { useToast } from "@/components/ui";
import { useSendNetplayInvite } from "@/hooks/use-netplay";
import { useSearchUsers } from "@/hooks/use-social";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import type { UserSearchResult } from "@/types/api";

interface NetplayInviteModalProps {
  sessionId: string;
  open: boolean;
  onClose: () => void;
}

export function NetplayInviteModal({
  sessionId,
  open,
  onClose,
}: NetplayInviteModalProps) {
  const [query, setQuery] = useState("");
  const [selectedUser, setSelectedUser] = useState<UserSearchResult | null>(
    null,
  );
  const [invitedUsers, setInvitedUsers] = useState<string[]>([]);
  const [showDropdown, setShowDropdown] = useState(false);
  const sendInvite = useSendNetplayInvite();
  const { toast } = useToast();
  const debouncedQuery = useDebouncedValue(query, 300);
  const { data: searchResults, isLoading: isSearching } =
    useSearchUsers(debouncedQuery);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(e.target as Node)
      ) {
        setShowDropdown(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  function handleSelectUser(user: UserSearchResult) {
    setSelectedUser(user);
    setQuery(user.username);
    setShowDropdown(false);
  }

  function handleInvite() {
    const username = selectedUser?.username ?? query.trim();
    if (!username) return;

    sendInvite.mutate(
      { sessionId, username },
      {
        onSuccess: () => {
          toast("success", `Invitation sent to ${username}`);
          setInvitedUsers((prev) => [...prev, username]);
          setQuery("");
          setSelectedUser(null);
          inputRef.current?.focus();
        },
        onError: (error) => {
          toast(
            "error",
            error instanceof Error
              ? error.message
              : "Failed to send invitation",
          );
        },
      },
    );
  }

  function handleClose() {
    setQuery("");
    setSelectedUser(null);
    setInvitedUsers([]);
    setShowDropdown(false);
    onClose();
  }

  const filteredResults = (searchResults ?? []).filter(
    (u) => !invitedUsers.includes(u.username),
  );

  return (
    <Modal open={open} onClose={handleClose} title="Invite Player">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          handleInvite();
        }}
        className="space-y-4"
      >
        <div className="relative" ref={dropdownRef}>
          <label
            htmlFor="netplay-invite-username"
            className="block text-sm font-medium text-surface-300 mb-1.5"
          >
            Username
          </label>
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-surface-500 pointer-events-none" />
            <input
              ref={inputRef}
              id="netplay-invite-username"
              type="text"
              placeholder="Search for a user..."
              value={query}
              onChange={(e) => {
                setQuery(e.target.value);
                setSelectedUser(null);
                setShowDropdown(e.target.value.length >= 2);
              }}
              onFocus={() => {
                if (query.length >= 2) setShowDropdown(true);
              }}
              autoFocus
              autoComplete="off"
              className="w-full rounded-lg border border-surface-700 bg-surface-800 py-2.5 pl-10 pr-3 text-sm text-surface-100 placeholder:text-surface-500 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
            />
          </div>
          {showDropdown && query.length >= 2 && (
            <div className="absolute z-50 mt-1 w-full rounded-lg border border-surface-700 bg-surface-800 shadow-lg max-h-48 overflow-y-auto">
              {isSearching ? (
                <div className="flex items-center gap-2 px-3 py-2 text-sm text-surface-500">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  Searching...
                </div>
              ) : filteredResults.length > 0 ? (
                filteredResults.map((user) => (
                  <button
                    key={user.id}
                    type="button"
                    className="flex w-full items-center gap-2.5 px-3 py-2 text-left text-sm hover:bg-surface-700 transition-colors"
                    onClick={() => handleSelectUser(user)}
                  >
                    {user.avatarUrl ? (
                      <img
                        src={user.avatarUrl}
                        alt=""
                        className="h-6 w-6 rounded-full object-cover"
                      />
                    ) : (
                      <div className="h-6 w-6 rounded-full bg-surface-600 flex items-center justify-center text-xs font-medium text-surface-300">
                        {user.username[0]?.toUpperCase()}
                      </div>
                    )}
                    <span className="text-surface-200">{user.username}</span>
                  </button>
                ))
              ) : (
                <div className="px-3 py-2 text-sm text-surface-500">
                  No users found
                </div>
              )}
            </div>
          )}
        </div>

        {invitedUsers.length > 0 && (
          <div className="space-y-1.5">
            <p className="text-xs font-medium text-surface-500">
              Invited this session
            </p>
            <div className="flex flex-wrap gap-2">
              {invitedUsers.map((user) => (
                <span
                  key={user}
                  className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium bg-brand-500/15 text-brand-400"
                >
                  <Check className="h-3 w-3" />
                  {user}
                </span>
              ))}
            </div>
          </div>
        )}

        <p className="text-xs text-surface-500">
          Search for users by name and invite them to this netplay session.
        </p>

        <div className="flex justify-end gap-3">
          <Button variant="secondary" type="button" onClick={handleClose}>
            Close
          </Button>
          <Button
            type="submit"
            loading={sendInvite.isPending}
            disabled={!query.trim()}
          >
            <UserPlus className="h-4 w-4" />
            Send Invite
          </Button>
        </div>
      </form>
    </Modal>
  );
}
