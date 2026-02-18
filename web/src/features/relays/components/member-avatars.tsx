import { PlayerAvatar } from "@/components/player-avatar";
import type { RelayMember } from "@/types/api";

interface MemberAvatarsProps {
  members: Pick<RelayMember, "username" | "avatarUrl">[];
  max?: number;
}

export function MemberAvatars({ members, max = 4 }: MemberAvatarsProps) {
  const visible = members.slice(0, max);
  const overflow = members.length - max;

  return (
    <div className="flex -space-x-2">
      {visible.map((member) => (
        <div
          key={member.username}
          className="ring-2 ring-surface-950 rounded-full"
          title={member.username}
        >
          <PlayerAvatar
            username={member.username}
            avatarUrl={member.avatarUrl}
            size="sm"
          />
        </div>
      ))}
      {overflow > 0 && (
        <div className="h-8 w-8 rounded-full bg-surface-800 ring-2 ring-surface-950 flex items-center justify-center text-xs font-medium text-surface-300">
          +{overflow}
        </div>
      )}
    </div>
  );
}
