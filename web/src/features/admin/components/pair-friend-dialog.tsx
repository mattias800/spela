import { useState } from "react";
import { Copy, Check } from "lucide-react";
import {
  Modal,
  Button,
  Input,
  Textarea,
  StateTabNav,
  StateTabItem,
  useToast,
} from "@/components/ui";
import {
  useIssueFederationInvite,
  useAcceptFederationInvite,
} from "@/hooks/use-federation";

interface PairFriendDialogProps {
  open: boolean;
  onClose: () => void;
}

type Tab = "accept" | "invite";

// Modal for forming a federation link with a connected server. Two directions:
// accept an invite the other admin gave you, or generate one to hand to them.
// Pairing is mutual and invite-gated; either side can initiate.
export function PairFriendDialog({ open, onClose }: PairFriendDialogProps) {
  const { toast } = useToast();
  const [tab, setTab] = useState<Tab>("accept");

  // Accept-invite form.
  const [invite, setInvite] = useState("");
  const [name, setName] = useState("");
  const acceptInvite = useAcceptFederationInvite();

  // Issue-invite result.
  const issueInvite = useIssueFederationInvite();
  const [generated, setGenerated] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const close = () => {
    onClose();
    // Reset after the close animation so fields don't flicker mid-fade.
    setTimeout(() => {
      setTab("accept");
      setInvite("");
      setName("");
      setGenerated(null);
      setCopied(false);
    }, 200);
  };

  const handleAccept = () => {
    acceptInvite.mutate(
      { invite: invite.trim(), name: name.trim() },
      {
        onSuccess: () => {
          toast("success", "Connected server added");
          close();
        },
        onError: (e) =>
          toast("error", e instanceof Error ? e.message : "Pairing failed"),
      },
    );
  };

  const handleGenerate = () => {
    issueInvite.mutate(undefined, {
      onSuccess: (data) => {
        if (data?.invite) {
          setGenerated(data.invite);
        } else {
          toast("error", "Server returned an empty invite");
        }
      },
      onError: (e) =>
        toast(
          "error",
          e instanceof Error ? e.message : "Could not generate an invite",
        ),
    });
  };

  const handleCopy = () => {
    if (!generated) return;
    navigator.clipboard.writeText(generated).then(() => {
      setCopied(true);
      toast("success", "Invite copied to clipboard");
      setTimeout(() => setCopied(false), 2000);
    });
  };

  return (
    <Modal open={open} onClose={close} title="Add a connected server" size="lg">
      <div className="space-y-5">
        <StateTabNav>
          <StateTabItem active={tab === "accept"} onClick={() => setTab("accept")}>
            Accept an invite
          </StateTabItem>
          <StateTabItem active={tab === "invite"} onClick={() => setTab("invite")}>
            Generate an invite
          </StateTabItem>
        </StateTabNav>

        {tab === "accept" ? (
          <div className="space-y-4" data-testid="accept-invite-panel">
            <p className="text-sm text-surface-400">
              Paste the invite another server's admin sent you. We'll verify it,
              connect to their server, and add it as a connected server.
            </p>
            <Textarea
              label="Invite"
              rows={4}
              value={invite}
              onChange={(e) => setInvite(e.target.value)}
              placeholder="Paste the invite string the other admin sent you"
              className="font-mono text-xs"
              data-testid="accept-invite-input"
            />
            <Input
              label="Name for this server"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Alice's Server"
              data-testid="accept-invite-name"
            />
            <div className="flex justify-end">
              <Button
                variant="primary"
                loading={acceptInvite.isPending}
                disabled={!invite.trim() || !name.trim()}
                onClick={handleAccept}
                data-testid="accept-invite-submit"
              >
                Pair
              </Button>
            </div>
          </div>
        ) : (
          <div className="space-y-4" data-testid="invite-friend-panel">
            <p className="text-sm text-surface-400">
              Generate a one-time invite and send it to the other server's admin
              out-of-band. They paste it under “Accept an invite” to connect.
            </p>
            {generated ? (
              <>
                <Textarea
                  label="Invite (expires in 30 minutes)"
                  readOnly
                  rows={4}
                  value={generated}
                  className="font-mono text-xs"
                  data-testid="generated-invite"
                />
                <div className="flex justify-end gap-3">
                  <Button
                    variant="secondary"
                    onClick={handleCopy}
                    data-testid="copy-invite-button"
                  >
                    {copied ? (
                      <Check className="h-4 w-4" />
                    ) : (
                      <Copy className="h-4 w-4" />
                    )}
                    {copied ? "Copied!" : "Copy"}
                  </Button>
                </div>
              </>
            ) : (
              <div className="flex justify-end">
                <Button
                  variant="primary"
                  loading={issueInvite.isPending}
                  onClick={handleGenerate}
                  data-testid="generate-invite-button"
                >
                  Generate invite
                </Button>
              </div>
            )}
          </div>
        )}
      </div>
    </Modal>
  );
}
