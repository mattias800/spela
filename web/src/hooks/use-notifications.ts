import { useWebSocketEvent } from "@/hooks/use-websocket";
import { useToast } from "@/components/ui";

interface RelayInvitePayload {
  relayName: string;
  inviterUsername: string;
  gameTitle: string;
}

interface NetplaySessionPayload {
  hostUsername: string;
  gameTitle: string;
}

/**
 * Global WebSocket notification hook — shows toast notifications
 * for relay invites and netplay session events.
 * Should be mounted once in AppLayout.
 */
export function useNotifications() {
  const { toast } = useToast();

  useWebSocketEvent("relay_invite_sent", (payload: RelayInvitePayload) => {
    toast(
      "info",
      `${payload.inviterUsername} invited you to relay "${payload.relayName}"`,
    );
  });

  useWebSocketEvent(
    "netplay_session_created",
    (payload: NetplaySessionPayload) => {
      toast(
        "info",
        `${payload.hostUsername} is hosting a netplay session for ${payload.gameTitle}`,
      );
    },
  );

  useWebSocketEvent(
    "netplay_player_joined",
    (payload: NetplaySessionPayload) => {
      toast("info", `A player joined the netplay session for ${payload.gameTitle}`);
    },
  );
}
