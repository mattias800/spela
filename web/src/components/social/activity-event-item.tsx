import { Link } from "react-router-dom";
import { Play, Heart, Star, Share2, Clock } from "lucide-react";
import { Badge } from "@/components/ui";
import { PlayerAvatar } from "@/components/player-avatar";
import { formatRelativeTime } from "@/lib/format";
import type { ActivityEvent } from "@/types/api";

function eventIcon(eventType: ActivityEvent["eventType"]) {
  switch (eventType) {
    case "started_playing":
      return <Play className="h-3.5 w-3.5 text-brand-400" />;
    case "favorited_game":
      return <Heart className="h-3.5 w-3.5 text-danger-500" />;
    case "rated_game":
      return <Star className="h-3.5 w-3.5 text-amber-400" />;
    case "shared_save":
      return <Share2 className="h-3.5 w-3.5 text-green-400" />;
    case "queued_play_later":
      return <Clock className="h-3.5 w-3.5 text-brand-400" />;
  }
}

function eventDescription(event: ActivityEvent): React.ReactNode {
  const gameLink = (
    <Link
      to={`/games/${event.gameId}`}
      className="font-medium text-surface-200 hover:text-brand-400 transition-colors"
    >
      {event.gameTitle}
    </Link>
  );

  switch (event.eventType) {
    case "started_playing":
      return <>started playing {gameLink}</>;
    case "favorited_game":
      return <>favorited {gameLink}</>;
    case "rated_game": {
      const rating = (event.metadata?.rating as number) ?? 0;
      return (
        <>
          rated {gameLink}{" "}
          <Badge variant="brand" className="text-[10px] px-1.5 py-0">
            {rating}/5
          </Badge>
        </>
      );
    }
    case "shared_save":
      return <>shared a save for {gameLink}</>;
    case "queued_play_later":
      return <>added {gameLink} to their Play Later queue</>;
  }
}

interface ActivityEventItemProps {
  event: ActivityEvent;
  compact?: boolean;
}

export function ActivityEventItem({ event, compact }: ActivityEventItemProps) {
  return (
    <div
      className="flex items-start gap-3 rounded-xl px-3 py-3 hover:bg-surface-800/50 transition-colors"
      data-testid={`activity-event-${event.id}`}
    >
      <div className="relative flex-shrink-0 mt-0.5">
        <PlayerAvatar
          username={event.username}
          avatarUrl={event.userAvatarUrl}
        />
        <span className="absolute -bottom-1 -right-1 h-5 w-5 rounded-full bg-surface-900 flex items-center justify-center">
          {eventIcon(event.eventType)}
        </span>
      </div>

      <div className="flex-1 min-w-0">
        <p className="text-sm text-surface-400">
          <Link to={`/users/${event.userId}`} className="font-medium text-surface-200 hover:text-brand-400 transition-colors">
            {event.username}
          </Link>{" "}
          {eventDescription(event)}
        </p>
        <div className="flex items-center gap-2 mt-1">
          <span className="text-xs text-surface-500">
            {formatRelativeTime(event.createdAt)}
          </span>
          {event.gameConsoleName && (
            <Badge variant="brand" className="text-[10px] px-1.5 py-0">
              {event.gameConsoleName}
            </Badge>
          )}
        </div>
      </div>

      {!compact && event.gameCoverUrl && (
        <Link
          to={`/games/${event.gameId}`}
          className="flex-shrink-0"
        >
          <img
            src={event.gameCoverUrl}
            alt={event.gameTitle}
            className="h-12 w-9 rounded-lg object-cover"
          />
        </Link>
      )}
    </div>
  );
}
