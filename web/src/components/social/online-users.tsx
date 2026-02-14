import { Link } from "react-router-dom";
import { Users, Gamepad2 } from "lucide-react";
import { Skeleton, EmptyState, Badge } from "@/components/ui";
import { PlayerAvatar } from "@/components/player-avatar";
import { useOnlineUsers } from "@/hooks/use-social";

function OnlineUsersSkeleton() {
  return (
    <div className="space-y-3">
      {Array.from({ length: 3 }, (_, i) => (
        <div key={i} className="flex items-center gap-3 px-3 py-2">
          <Skeleton className="h-8 w-8 rounded-full" />
          <div className="flex-1 space-y-1">
            <Skeleton className="h-4 w-24" />
            <Skeleton className="h-3 w-32" />
          </div>
        </div>
      ))}
    </div>
  );
}

export function OnlineUsers() {
  const { data, isLoading } = useOnlineUsers();

  return (
    <div data-testid="online-users-widget">
      <div className="flex items-center gap-2.5 mb-4">
        <Users className="h-5 w-5 text-brand-400" />
        <h2 className="text-lg font-bold text-surface-100">Online Now</h2>
        {data?.users && data.users.length > 0 && (
          <span className="text-xs font-medium text-brand-400 bg-brand-400/10 px-2 py-0.5 rounded-full">
            {data.users.length}
          </span>
        )}
      </div>

      {isLoading && <OnlineUsersSkeleton />}

      {!isLoading && (!data?.users || data.users.length === 0) && (
        <EmptyState
          icon={Users}
          title="No one online"
          description="Check back later to see who's playing."
          className="py-8"
        />
      )}

      {!isLoading && data?.users && data.users.length > 0 && (
        <div className="space-y-1">
          {data.users.map((user) => (
            <div
              key={user.id}
              className="flex items-center gap-3 rounded-xl px-3 py-2.5 hover:bg-surface-800/50 transition-colors"
              data-testid={`online-user-${user.id}`}
            >
              <div className="relative">
                <PlayerAvatar
                  username={user.username}
                  avatarUrl={user.avatarUrl}
                />
                <span className="absolute -bottom-0.5 -right-0.5 h-3 w-3 rounded-full bg-green-500 border-2 border-surface-900" />
              </div>
              <div className="flex-1 min-w-0">
                <Link
                  to={`/users/${user.id}`}
                  className="text-sm font-medium text-surface-200 truncate hover:text-brand-400 transition-colors"
                >
                  {user.username}
                </Link>
                {user.currentGame ? (
                  <Link
                    to={`/games/${user.currentGame.id}`}
                    className="flex items-center gap-1.5 group"
                  >
                    <Gamepad2 className="h-3 w-3 text-brand-400 flex-shrink-0" />
                    <span className="text-xs text-surface-400 truncate group-hover:text-brand-400 transition-colors">
                      {user.currentGame.title}
                    </span>
                    <Badge
                      variant="brand"
                      className="text-[10px] px-1.5 py-0 flex-shrink-0"
                    >
                      {user.currentGame.consoleName}
                    </Badge>
                  </Link>
                ) : (
                  <p className="text-xs text-surface-500">Idle</p>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
