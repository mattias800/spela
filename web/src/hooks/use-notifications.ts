import { useWebSocketEvent } from "@/hooks/use-websocket";
import { useToast } from "@/components/ui";
import { useAuth } from "@/hooks/use-auth";

interface SharedSessionInvitePayload {
  sharedSessionName: string;
  inviterUsername: string;
  gameTitle: string;
}

interface NetplaySessionPayload {
  hostUsername: string;
  gameTitle: string;
}

interface NetplayInvitePayload {
  inviteeId: string;
  inviterUsername: string;
  gameTitle: string;
}

/**
 * Global WebSocket notification hook — shows toast notifications
 * for shared session invites and netplay session events.
 * Should be mounted once in AppLayout.
 */
export function useNotifications() {
  const { toast } = useToast();
  const { user } = useAuth();

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

  useWebSocketEvent(
    "netplay_invite_sent",
    (payload: NetplayInvitePayload) => {
      if (payload.inviteeId !== user?.id) return;
      toast(
        "info",
        `${payload.inviterUsername} invited you to play ${payload.gameTitle}`,
      );
    },
  );
}
