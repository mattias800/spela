import { Play, LogOut, Trash2, Repeat, Copy } from "lucide-react";
import { ActionsMenu, Button, Badge } from "@/components/ui";
import { MemberAvatars } from "@/features/shared-sessions/components/member-avatars";
import { sharedSessionStatusVariant } from "@/features/shared-sessions/components/shared-session-status";
import { formatRelativeTime } from "@/lib/format";
import type { SharedSessionDetail } from "@/types/api";

interface SharedSessionHeroProps {
  sharedSession: SharedSessionDetail;
  isOwner: boolean;
  canPlayInBrowser: boolean;
  onPlay: () => void;
  onLeave: () => void;
  onDelete: () => void;
  /**
   * Opens the "Clone to my library" dialog. Disabled (menu item
   * absent) when the shared session has no backing personal session
   * yet — cloning copies save bytes from a real session, and there
   * are none before anyone's played.
   */
  onClone: () => void;
  isCloning: boolean;
}

export function SharedSessionHero({
  sharedSession,
  isOwner,
  canPlayInBrowser,
  onPlay,
  onLeave,
  onDelete,
  onClone,
  isCloning,
}: SharedSessionHeroProps) {
  // The backing session is what actually holds the save bytes; until
  // someone plays the shared session at least once, there is nothing
  // to clone. We leave the menu visible but omit the clone item in
  // that edge case rather than showing it disabled — a disabled item
  // provides no affordance about what would unblock it.
  const canClone = !!sharedSession.sessionId;
  return (
    <div data-comp="SharedSessionHero" className="flex flex-col items-center gap-6 md:flex-row md:items-start md:gap-8">
      {/* Cover art */}
      <div className="w-48 flex-shrink-0 md:w-64">
        <div className="aspect-[3/4] rounded-2xl overflow-hidden bg-surface-900 border border-surface-800 shadow-2xl">
          {sharedSession.gameCoverUrl ? (
            <img
              src={sharedSession.gameCoverUrl}
              alt={sharedSession.gameTitle}
              className="h-full w-full object-cover"
            />
          ) : (
            <div className="h-full w-full flex items-center justify-center bg-gradient-to-br from-surface-800 to-surface-900">
              <Repeat className="h-16 w-16 text-surface-700" />
            </div>
          )}
        </div>
      </div>

      {/* Info */}
      <div className="w-full min-w-0 flex-1 space-y-5 pt-2">
        <div className="space-y-4">
          <div>
            <h1 className="text-2xl font-bold text-surface-100 md:text-3xl">
              {sharedSession.name}
            </h1>
            <div className="flex items-center gap-3 mt-2">
              <Badge variant="brand">{sharedSession.consoleName}</Badge>
              <Badge
                variant={sharedSessionStatusVariant[sharedSession.status]}
                className="capitalize"
              >
                {sharedSession.status}
              </Badge>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <Button
              variant="primary"
              size="sm"
              onClick={onPlay}
              disabled={!canPlayInBrowser || sharedSession.status === "completed"}
              title={
                !canPlayInBrowser
                  ? `${sharedSession.consoleName} is not supported for browser play`
                  : sharedSession.status === "completed"
                    ? "This shared session is completed"
                    : "Play in Browser"
              }
              data-testid="shared-session-play-btn"
            >
              <Play className="h-5 w-5" />
              Play
            </Button>
            <div data-testid="shared-session-hero-actions">
              <ActionsMenu
                size="sm"
                items={[
                  ...(canClone
                    ? [
                        {
                          label: "Clone to my library",
                          icon: <Copy className="h-4 w-4" />,
                          onClick: onClone,
                          loading: isCloning,
                        },
                      ]
                    : []),
                  ...(isOwner
                    ? [
                        {
                          label: "Delete shared session",
                          icon: <Trash2 className="h-4 w-4" />,
                          onClick: onDelete,
                          variant: "danger" as const,
                        },
                      ]
                    : [
                        {
                          label: "Leave shared session",
                          icon: <LogOut className="h-4 w-4" />,
                          onClick: onLeave,
                          variant: "danger" as const,
                        },
                      ]),
                ]}
              />
            </div>
          </div>
        </div>

        {/* Meta info */}
        <div className="flex flex-wrap items-center gap-6 text-sm text-surface-400">
          <div className="flex items-center gap-2">
            <span className="text-surface-500">Game:</span>
            <span className="text-surface-200">{sharedSession.gameTitle}</span>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-surface-500">Created by:</span>
            <span className="text-surface-200">{sharedSession.ownerUsername}</span>
          </div>
          <div>
            <span className="text-surface-500">Last active:</span>{" "}
            {formatRelativeTime(sharedSession.updatedAt)}
          </div>
        </div>

        {/* Member avatars */}
        <MemberAvatars members={sharedSession.members} max={6} />
      </div>
    </div>
  );
}
