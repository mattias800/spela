import { Section, Switch, useToast } from "@/components/ui";
import { useServerSettings, useUpdateSettings } from "@/hooks/use-admin";

const RELAY_KEY = "federation_relay_enabled";

// Server-wide opt-in for relaying ROM downloads between a connected server and
// one of ITS connected servers. Default off — enabling it makes this server pass
// ROM bytes through on behalf of others, so it must be a deliberate admin choice.
export function FederationRelayToggle() {
  const { toast } = useToast();
  const { data: settings } = useServerSettings();
  const updateSettings = useUpdateSettings();
  const enabled = settings?.[RELAY_KEY] === "true";

  const handleToggle = (next: boolean) => {
    updateSettings.mutate(
      { [RELAY_KEY]: String(next) },
      {
        onSuccess: () =>
          toast("success", next ? "ROM relay enabled" : "ROM relay disabled"),
        onError: (e) =>
          toast(
            "error",
            e instanceof Error ? e.message : "Could not update setting",
          ),
      },
    );
  };

  return (
    <Section>
      <div className="flex items-start justify-between gap-6 p-5">
        <div className="space-y-1">
          <div className="text-sm font-medium text-surface-200">
            Relay ROM downloads between connected servers
          </div>
          <p className="max-w-2xl text-sm text-surface-400">
            When enabled, your server forwards ROM transfers between a connected
            server and one of <em>its</em> connected servers (hop-bounded) — the
            bytes pass through your server as a bridge. Leave this off unless you
            intend to act as a relay in the mesh. Per-server download policy still
            applies.
          </p>
        </div>
        <Switch
          checked={enabled}
          disabled={updateSettings.isPending}
          onChange={handleToggle}
          data-testid="relay-toggle"
        />
      </div>
    </Section>
  );
}
