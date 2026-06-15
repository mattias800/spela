import { useState } from "react";
import { Modal, Button, Switch, useToast } from "@/components/ui";
import { useUpdateFederationPolicy } from "@/hooks/use-federation";
import type { FederationPeer } from "@/generated/schemas";

// Only the data classes that are actually wired end-to-end today. The backend
// enumerates more (presence, reviews, achievements) for Phase 4, but surfacing
// toggles that do nothing yet would mislead the admin.
const DATA_CLASSES = [
  {
    key: "stats",
    label: "Stats",
    description: "Aggregate top-lists and total playtime.",
  },
  {
    key: "catalog",
    label: "Catalog",
    description: "Which games this server has — metadata only, no ROM data.",
  },
  { key: "download", label: "Downloads", description: "ROM file transfers." },
] as const;

function parsePolicy(json: string | undefined): Record<string, boolean> {
  if (!json) return {};
  try {
    const parsed = JSON.parse(json);
    return typeof parsed === "object" && parsed !== null ? parsed : {};
  } catch {
    return {};
  }
}

interface PolicyEditorDialogProps {
  peer: FederationPeer;
  onClose: () => void;
}

// Per-friend share/consume editor. Mounted only while a peer is selected, so
// the initial toggle state reads straight from that peer's stored policy.
export function PolicyEditorDialog({ peer, onClose }: PolicyEditorDialogProps) {
  const { toast } = useToast();
  const updatePolicy = useUpdateFederationPolicy();
  // Seed from the full stored policy so Phase 4 classes (presence, reviews,
  // etc. — known to the backend but not surfaced here yet) are preserved on save.
  const [share, setShare] = useState<Record<string, boolean>>(() =>
    parsePolicy(peer.sharePolicy),
  );
  const [consume, setConsume] = useState<Record<string, boolean>>(() =>
    parsePolicy(peer.consumePolicy),
  );

  const handleSave = () => {
    updatePolicy.mutate(
      { fingerprint: peer.fingerprint, sharePolicy: share, consumePolicy: consume },
      {
        onSuccess: () => {
          toast("success", "Sharing policy updated");
          onClose();
        },
        onError: (e) =>
          toast(
            "error",
            e instanceof Error ? e.message : "Could not update policy",
          ),
      },
    );
  };

  return (
    <Modal
      open
      onClose={onClose}
      title={`Sharing with ${peer.name || "this friend"}`}
      size="lg"
    >
      <div className="space-y-5">
        <p className="text-sm text-surface-400">
          Choose which data you{" "}
          <span className="text-surface-200">share</span> with this friend and
          which you <span className="text-surface-200">accept</span> from them.
          Both sides must opt in for data to flow.
        </p>

        <div className="space-y-3">
          <div className="grid grid-cols-[1fr_4rem_4rem] items-center gap-x-4 px-4 text-xs font-semibold uppercase tracking-wider text-surface-500">
            <span>Data</span>
            <span className="text-center">Share</span>
            <span className="text-center">Accept</span>
          </div>
          {DATA_CLASSES.map((dc) => (
            <div
              key={dc.key}
              data-testid={`policy-row-${dc.key}`}
              className="grid grid-cols-[1fr_4rem_4rem] items-center gap-x-4 rounded-xl bg-white/[0.03] border border-white/[0.06] px-4 py-3"
            >
              <div>
                <div className="text-sm font-medium text-surface-200">
                  {dc.label}
                </div>
                <div className="text-xs text-surface-500">{dc.description}</div>
              </div>
              <div className="flex justify-center">
                <Switch
                  checked={!!share[dc.key]}
                  onChange={(v) => setShare((p) => ({ ...p, [dc.key]: v }))}
                  data-testid={`share-${dc.key}`}
                />
              </div>
              <div className="flex justify-center">
                <Switch
                  checked={!!consume[dc.key]}
                  onChange={(v) => setConsume((p) => ({ ...p, [dc.key]: v }))}
                  data-testid={`consume-${dc.key}`}
                />
              </div>
            </div>
          ))}
        </div>

        <div className="flex justify-end gap-3">
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="primary"
            loading={updatePolicy.isPending}
            onClick={handleSave}
            data-testid="save-policy-button"
          >
            Save
          </Button>
        </div>
      </div>
    </Modal>
  );
}
