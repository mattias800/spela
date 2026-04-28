import { Clock, Calendar } from "lucide-react";
import { usePlayStats } from "@/hooks/use-play-stats";
import { formatPlayTime, formatRelativeTime } from "@/lib/format";

interface PlayInfoProps {
  gameId: string | number;
  className?: string;
}

export function PlayInfo({ gameId, className }: PlayInfoProps) {
  const { data: stats } = usePlayStats();

  const entry = stats?.find((s) => String(s.gameId) === String(gameId));
  if (!entry) return null;

  return (
    <span data-comp="PlayInfo"
      className={`inline-flex items-center gap-3 text-xs text-surface-500 ${className ?? ""}`}
    >
      {entry.playTime > 0 && (
        <span className="inline-flex items-center gap-1">
          <Clock className="h-3 w-3" />
          {formatPlayTime(entry.playTime)}
        </span>
      )}
      {entry.lastPlayedAt && (
        <span className="inline-flex items-center gap-1">
          <Calendar className="h-3 w-3" />
          {formatRelativeTime(entry.lastPlayedAt)}
        </span>
      )}
    </span>
  );
}
