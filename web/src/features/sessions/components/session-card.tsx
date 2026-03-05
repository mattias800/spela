import { useState } from "react";
import { Link } from "react-router-dom";
import { Play, Pencil, Trash2, Clock, Users, Zap } from "lucide-react";
import { Button, ConfirmDeleteModal } from "@/components/ui";
import { formatPlayTime, formatRelativeTime } from "@/lib/format";
import type { GameSession } from "@/types/api";

interface SessionCardProps {
  session: GameSession;
  currentUsername?: string;
  onContinue: (session: GameSession) => void;
  onRename: (session: GameSession, name: string) => void;
  onDelete: (session: GameSession) => void;
  isDeleting: boolean;
}

export function SessionCard({
  session,
  currentUsername,
  onContinue,
  onRename,
  onDelete,
  isDeleting,
}: SessionCardProps) {
  const [isEditing, setIsEditing] = useState(false);
  const [editName, setEditName] = useState(session.name);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);

  function handleSubmitRename() {
    const trimmed = editName.trim();
    if (trimmed && trimmed !== session.name) {
      onRename(session, trimmed);
    }
    setIsEditing(false);
  }

  return (
    <>
      <div
        className="flex items-center gap-4 px-4 py-3 rounded-xl bg-surface-800/30 hover:bg-surface-800/50 transition-colors group"
        data-testid={`session-card-${session.id}`}
      >
        {/* Thumbnail */}
        <div className="w-12 h-12 rounded-lg bg-surface-700/50 flex-shrink-0 overflow-hidden flex items-center justify-center">
          {session.screenshotUrl ? (
            <img
              src={`${session.screenshotUrl}${session.screenshotUrl.includes("?") ? "&" : "?"}token=${localStorage?.getItem("accessToken") ?? ""}`}
              alt={session.name}
              className="w-full h-full object-cover"
            />
          ) : (
            <Play className="h-5 w-5 text-surface-500" />
          )}
        </div>

        {/* Info */}
        <div className="flex-1 min-w-0">
          {isEditing ? (
            <input
              type="text"
              value={editName}
              onChange={(e) => setEditName(e.target.value)}
              onBlur={handleSubmitRename}
              onKeyDown={(e) => {
                if (e.key === "Enter") handleSubmitRename();
                if (e.key === "Escape") {
                  setEditName(session.name);
                  setIsEditing(false);
                }
              }}
              className="bg-surface-700 text-surface-100 text-sm font-medium rounded px-2 py-0.5 w-full outline-none ring-1 ring-brand-500"
              data-testid="session-name-input"
              autoFocus
            />
          ) : (
            <Link
              to={`/sessions/${session.id}`}
              className="text-sm font-medium text-surface-200 truncate hover:text-surface-100 transition-colors"
            >
              {session.name}
            </Link>
          )}
          <div className="flex items-center gap-2 text-xs text-surface-500 mt-0.5">
            {session.lastPlayedAt && (
              <>
                <span className="flex items-center gap-0.5">
                  <Clock className="h-3 w-3" />
                  {formatRelativeTime(session.lastPlayedAt)}
                </span>
                <span>&middot;</span>
              </>
            )}
            <span>{formatPlayTime(session.totalPlayTime)}</span>
            {session.cheatsEnabled && (
              <>
                <span>&middot;</span>
                <span className="flex items-center gap-0.5 text-amber-400">
                  <Zap className="h-3 w-3" />
                  Cheats
                </span>
              </>
            )}
            {session.memberCount > 1 && (
              <>
                <span>&middot;</span>
                <span className="flex items-center gap-0.5">
                  <Users className="h-3 w-3" />
                  {session.memberCount}
                </span>
              </>
            )}
            {session.lastPlayedByUsername &&
              currentUsername &&
              session.lastPlayedByUsername !== currentUsername && (
                <>
                  <span>&middot;</span>
                  <span data-testid="last-played-by">
                    Last played by {session.lastPlayedByUsername}
                  </span>
                </>
              )}
          </div>
        </div>

        {/* Member avatars */}
        {(session.memberAvatars ?? []).length > 0 && (() => {
          const avatars = session.memberAvatars!;
          return (
            <div className="flex -space-x-2" data-testid="member-avatars">
              {avatars.slice(0, 3).map((url, i) => (
                <img
                  key={i}
                  src={url}
                  alt=""
                  className="w-6 h-6 rounded-full border-2 border-surface-900"
                />
              ))}
              {avatars.length > 3 && (
                <span className="w-6 h-6 rounded-full border-2 border-surface-900 bg-surface-700 flex items-center justify-center text-[10px] text-surface-400">
                  +{avatars.length - 3}
                </span>
              )}
            </div>
          );
        })()}

        {/* Actions */}
        <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setEditName(session.name);
              setIsEditing(true);
            }}
            aria-label="Rename session"
          >
            <Pencil className="h-3.5 w-3.5" />
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setShowDeleteConfirm(true)}
            aria-label="Delete session"
          >
            <Trash2 className="h-3.5 w-3.5 text-red-400" />
          </Button>
        </div>

        <Button
          variant="primary"
          size="sm"
          onClick={() => onContinue(session)}
        >
          <Play className="h-4 w-4 mr-1" />
          Play
        </Button>
      </div>

      <ConfirmDeleteModal
        open={showDeleteConfirm}
        onClose={() => setShowDeleteConfirm(false)}
        title="Delete Session"
        message={`Delete "${session.name}"? All save data for this session will be permanently removed.`}
        onConfirm={() => {
          onDelete(session);
          setShowDeleteConfirm(false);
        }}
        isPending={isDeleting}
      />
    </>
  );
}
