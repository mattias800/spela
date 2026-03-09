import { type ReactNode } from "react";
import { Link } from "react-router-dom";
import { cn } from "@/lib/cn";
import { PlayerAvatar } from "@/components/player-avatar";

interface LeaderboardRowProps {
  isCurrentUser: boolean;
  username: string;
  avatarUrl?: string;
  rank: ReactNode;
  children: ReactNode;
  href?: string;
  usernameClassName?: string;
  "data-testid"?: string;
}

export function LeaderboardRow({
  isCurrentUser,
  username,
  avatarUrl,
  rank,
  children,
  href,
  usernameClassName,
  "data-testid": testId,
}: LeaderboardRowProps) {
  return (
    <div
      data-testid={testId}
      className={cn(
        "flex items-center gap-3 rounded-xl px-3 py-2.5",
        isCurrentUser
          ? "bg-brand-500/5 border-l-2 border-brand-400"
          : "bg-surface-800/30",
      )}
    >
      {rank}
      <PlayerAvatar username={username} avatarUrl={avatarUrl} />
      {href ? (
        <Link
          to={href}
          className={cn(
            "text-sm font-medium truncate hover:underline",
            usernameClassName ?? "flex-shrink-0 w-28",
            isCurrentUser ? "text-brand-400" : "text-surface-200",
          )}
        >
          {username}
        </Link>
      ) : (
        <span
          className={cn(
            "text-sm font-medium truncate",
            usernameClassName ?? "flex-shrink-0 w-28",
            isCurrentUser ? "text-brand-400" : "text-surface-200",
          )}
        >
          {username}
        </span>
      )}
      {children}
    </div>
  );
}
