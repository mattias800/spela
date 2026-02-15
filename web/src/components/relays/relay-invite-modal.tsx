import { useState } from "react";
import { UserPlus, Check } from "lucide-react";
import { Button, Modal, Input } from "@/components/ui";
import { useToast } from "@/components/ui";
import { useInviteToRelay } from "@/hooks/use-relays";

interface RelayInviteModalProps {
  relayId: string;
  open: boolean;
  onClose: () => void;
}

export function RelayInviteModal({
  relayId,
  open,
  onClose,
}: RelayInviteModalProps) {
  const [username, setUsername] = useState("");
  const [invitedUsers, setInvitedUsers] = useState<string[]>([]);
  const inviteUser = useInviteToRelay();
  const { toast } = useToast();

  function handleInvite() {
    const trimmed = username.trim();
    if (!trimmed) return;

    inviteUser.mutate(
      { relayId, username: trimmed },
      {
        onSuccess: () => {
          toast("success", `Invitation sent to ${trimmed}`);
          setInvitedUsers((prev) => [...prev, trimmed]);
          setUsername("");
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
    setUsername("");
    setInvitedUsers([]);
    onClose();
  }

  return (
    <Modal open={open} onClose={handleClose} title="Invite Players">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          handleInvite();
        }}
        className="space-y-4"
      >
        <Input
          id="invite-username"
          label="Username"
          placeholder="Enter username to invite"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          autoFocus
        />

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
          You can invite multiple players before closing this dialog.
        </p>

        <div className="flex justify-end gap-3">
          <Button variant="secondary" type="button" onClick={handleClose}>
            Done
          </Button>
          <Button
            type="submit"
            loading={inviteUser.isPending}
            disabled={!username.trim()}
          >
            <UserPlus className="h-4 w-4" />
            Send Invite
          </Button>
        </div>
      </form>
    </Modal>
  );
}
