import { Users } from "lucide-react";
import {
  NetplayPlayerSlot,
  NetplayEmptySlot,
} from "@/features/netplay/components/netplay-player-slot";
import type { NetplaySession } from "@/types/api";

interface NetplayPlayerListProps {
  session: NetplaySession;
}

export function NetplayPlayerList({ session }: NetplayPlayerListProps) {
  return (
    <section>
      <div className="flex items-center gap-2.5 mb-4">
        <Users className="h-5 w-5 text-brand-400" />
        <h2 className="text-xl font-bold text-surface-100">Players</h2>
        <span className="text-sm text-surface-500">
          ({session.clientId ? 2 : 1}/2)
        </span>
      </div>

      <div className="space-y-2">
        <NetplayPlayerSlot
          username={session.hostUsername}
          avatarUrl={session.hostAvatarUrl}
          isHost
          label="Player 1"
        />

        {session.clientId && session.clientUsername ? (
          <NetplayPlayerSlot
            username={session.clientUsername}
            avatarUrl={session.clientAvatarUrl}
            isHost={false}
            label="Player 2"
          />
        ) : (
          <NetplayEmptySlot />
        )}
      </div>
    </section>
  );
}
