import { useToast } from "@/components/ui";
import { InviteModal } from "@/components/invite-modal";
import { useInviteToSharedSession } from "@/hooks/use-shared-sessions";

interface SharedSessionInviteModalProps {
  sharedSessionId: string;
  open: boolean;
  onClose: () => void;
}

export function SharedSessionInviteModal({
  sharedSessionId,
  open,
  onClose,
}: SharedSessionInviteModalProps) {
  const inviteUser = useInviteToSharedSession();
  const { toast } = useToast();

  function handleInvite(username: string) {
    inviteUser.mutate(
      { sharedSessionId, username },
      {
        onSuccess: () => {
          toast("success", `Invitation sent to ${username}`);
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

  return (
    <InviteModal
      title="Invite Players"
      closeLabel="Done"
      open={open}
      onClose={onClose}
      onInvite={handleInvite}
      isInviting={inviteUser.isPending}
      invitingUsername={inviteUser.variables?.username}
    />
  );
}
