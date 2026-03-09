import { useToast } from "@/components/ui";
import { InviteModal } from "@/components/invite-modal";
import { useSendNetplayInvite } from "@/hooks/use-netplay";

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
  const sendInvite = useSendNetplayInvite();
  const { toast } = useToast();

  function handleInvite(username: string) {
    sendInvite.mutate(
      { sessionId, username },
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
      title="Invite Player"
      closeLabel="Close"
      open={open}
      onClose={onClose}
      onInvite={handleInvite}
      isInviting={sendInvite.isPending}
      invitingUsername={sendInvite.variables?.username}
    />
  );
}
