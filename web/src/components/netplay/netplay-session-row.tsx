import { useState } from "react";
import { Link } from "react-router-dom";
import { Copy, Check, Wifi } from "lucide-react";
import { Badge, Button } from "@/components/ui";
import {
  netplayStatusVariant,
  netplayStatusLabel,
  endReasonLabel,
} from "@/components/netplay/netplay-status";
import { formatRelativeTime } from "@/lib/format";
import type { NetplaySession } from "@/types/api";

interface NetplaySessionRowProps {
  session: NetplaySession;
}

export function NetplaySessionRow({ session }: NetplaySessionRowProps) {
  const [copied, setCopied] = useState(false);

  function handleCopy(e: React.MouseEvent) {
    e.preventDefault();
    e.stopPropagation();
    navigator.clipboard.writeText(session.inviteCode).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  }

  return (
    <Link
      to={`/netplay/${session.id}`}
      className="flex items-center gap-4 px-4 py-4 rounded-xl bg-surface-900/50 hover:bg-surface-800/50 transition-colors"
      data-testid={`netplay-session-row-${session.id}`}
    >
      {/* Cover art thumbnail */}
      <div className="h-16 w-12 flex-shrink-0 rounded-lg overflow-hidden bg-surface-800">
        {session.gameCoverUrl ? (
          <img
            src={session.gameCoverUrl}
            alt={session.gameTitle}
            className="h-full w-full object-cover"
            loading="lazy"
          />
        ) : (
          <div className="h-full w-full flex items-center justify-center">
            <Wifi className="h-5 w-5 text-surface-600" />
          </div>
        )}
      </div>

      {/* Session info */}
      <div className="flex-1 min-w-0 space-y-1">
        <div className="flex items-center gap-2">
          <p className="text-sm font-medium text-surface-100 truncate">
            {session.gameTitle}
          </p>
          <Badge variant="brand" className="flex-shrink-0">
            {session.consoleName}
          </Badge>
          <Badge
            variant={netplayStatusVariant[session.status]}
            className="flex-shrink-0"
          >
            {netplayStatusLabel[session.status]}
          </Badge>
          {session.status === "ended" && session.endReason && (
            <Badge variant="default" className="flex-shrink-0">
              {endReasonLabel[session.endReason]}
            </Badge>
          )}
        </div>
        <div className="flex items-center gap-2 text-xs text-surface-500">
          <span>{session.hostUsername}</span>
          {session.clientUsername && (
            <>
              <span>vs</span>
              <span>{session.clientUsername}</span>
            </>
          )}
          <span>&middot;</span>
          <span>{formatRelativeTime(session.createdAt)}</span>
        </div>
      </div>

      {/* Invite code for waiting sessions */}
      {session.status === "waiting" && (
        <div className="flex items-center gap-2 flex-shrink-0">
          <span className="font-mono text-sm tracking-wider text-surface-300">
            {session.inviteCode}
          </span>
          <Button
            variant="ghost"
            size="sm"
            onClick={handleCopy}
            title="Copy invite code"
          >
            {copied ? (
              <Check className="h-3.5 w-3.5" />
            ) : (
              <Copy className="h-3.5 w-3.5" />
            )}
          </Button>
        </div>
      )}
    </Link>
  );
}
