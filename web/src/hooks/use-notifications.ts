import { useWebSocketEvent } from "@/hooks/use-websocket";
import { useToast } from "@/components/ui";

interface SharedSessionInvitePayload {
  sharedSessionName: string;
  inviterUsername: string;
  gameTitle: string;
}

interface NetplaySessionPayload {
  hostUsername: string;
  gameTitle: string;
}

/**
 * Global WebSocket notification hook — shows toast notifications
 * for shared session invites and netplay session events.
 * Should be mounted once in AppLayout.
 */
export function useNotifications() {
  const { toast } = useToast();

  useWebSocketEvent("shared_session_invite_sent", (payload: SharedSessionInvitePayload) => {
    toast(
      "info",
      `${payload.inviterUsername} invited you to shared session "${payload.sharedSessionName}"`,
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
