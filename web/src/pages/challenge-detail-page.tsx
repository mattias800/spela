import { useState } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import { Flag, Download, Trash2, Users, Clock, Play } from "lucide-react";
import {
  Button,
  Badge,
  Skeleton,
  Modal,
} from "@/components/ui";
import { PageLayout, SectionList } from "@/components/layout";
import { useToast } from "@/components/ui";
import { PlayerAvatar } from "@/components/player-avatar";
import { ChallengeDifficultyBadge } from "@/features/challenges/components/challenge-difficulty-badge";
import { ChallengeStatusBadge } from "@/features/challenges/components/challenge-status-badge";
import { ChallengeTypeIcon } from "@/features/challenges/components/challenge-type-icon";
import { ChallengeLeaderboard } from "@/features/challenges/components/challenge-leaderboard";
import { useChallenge, useDeleteChallenge } from "@/hooks/use-challenges";
import { useAuth } from "@/hooks/use-auth";
import {
  formatRelativeTime,
  formatFileSize,
} from "@/lib/format";

function DetailSkeleton() {
  return (
    <div data-comp="DetailSkeleton" className="max-w-5xl space-y-8">
      <Skeleton className="h-8 w-24" />
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        <div className="lg:col-span-2">
          <Skeleton className="aspect-[16/10] rounded-2xl" />
        </div>
        <div className="space-y-4">
          <Skeleton className="h-8 w-48" />
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-3/4" />
          <Skeleton className="h-10 w-full rounded-lg" />
        </div>
      </div>
    </div>
  );
}

export function ChallengeDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: challenge, isLoading } = useChallenge(id ?? "");
  const deleteChallenge = useDeleteChallenge();
  const { user } = useAuth();
  const { toast } = useToast();
  const [showDeleteModal, setShowDeleteModal] = useState(false);

  const isCreator = challenge?.creatorId === user?.id;
  const isAdmin = user?.role === "admin" || user?.role === "owner";
  const canDelete = isCreator || isAdmin;

  if (isLoading) {
    return <DetailSkeleton />;
  }

  if (!challenge) {
    return (
      <div className="text-center py-20">
        <p className="text-surface-400">Challenge not found</p>
        <Button variant="ghost" onClick={() => navigate(-1)} className="mt-4">
          Go back
        </Button>
      </div>
    );
  }

  function handleDelete() {
    if (!challenge) return;
    deleteChallenge.mutate(challenge.id, {
      onSuccess: () => {
        toast("success", "Challenge deleted");
        navigate("/challenges");
      },
      onError: () => {
        toast("error", "Failed to delete challenge");
      },
    });
  }

  return (
    <PageLayout backButtonVariant="standard">
      <SectionList className="max-w-5xl">

      {/* Hero section */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Screenshot */}
        <div className="lg:col-span-2">
          <div className="relative aspect-[16/10] rounded-2xl overflow-hidden bg-surface-900 border border-surface-800/50">
            {challenge.screenshotUrl ? (
              <img
                src={challenge.screenshotUrl}
                alt={challenge.name}
                className="h-full w-full object-cover"
              />
            ) : (
              <div className="h-full w-full flex items-center justify-center bg-gradient-to-br from-surface-800 to-surface-900">
                <Flag className="h-16 w-16 text-surface-700" />
              </div>
            )}
          </div>
        </div>

        {/* Info panel */}
        <div className="space-y-5">
          <div>
            <div className="flex items-center gap-2 mb-2">
              <ChallengeStatusBadge status={challenge.status} />
              <ChallengeDifficultyBadge difficulty={challenge.difficulty} />
              <ChallengeTypeIcon type={challenge.type} showLabel />
            </div>
            <h1 className="text-2xl font-bold text-surface-100">
              {challenge.name}
            </h1>
          </div>

          {challenge.description && (
            <p className="text-sm text-surface-400">{challenge.description}</p>
          )}

          {/* Game info */}
          <Link
            to={`/games/${challenge.gameId}`}
            className="flex items-center gap-3 rounded-xl px-3 py-2.5 bg-surface-900/50 hover:bg-surface-900/80 transition-colors"
          >
            {challenge.gameCoverUrl && (
              <img
                src={challenge.gameCoverUrl}
                alt={challenge.gameTitle}
                className="h-10 w-8 rounded-lg object-cover flex-shrink-0"
              />
            )}
            <div className="min-w-0">
              <p className="text-sm font-medium text-surface-200 truncate">
                {challenge.gameTitle}
              </p>
              <Badge variant="brand" className="text-[10px] px-1.5 py-0">
                {challenge.consoleName}
              </Badge>
            </div>
          </Link>

          {/* Creator */}
          <div className="flex items-center gap-3">
            <PlayerAvatar
              username={challenge.creatorUsername}
              avatarUrl={challenge.creatorAvatar}
            />
            <div>
              <p className="text-sm font-medium text-surface-200">
                {challenge.creatorUsername}
              </p>
              <p className="text-xs text-surface-500">
                Created {formatRelativeTime(challenge.createdAt)}
              </p>
            </div>
          </div>

          {/* Stats */}
          <div className="grid grid-cols-2 gap-3" role="group" aria-label="Challenge statistics">
            <div className="rounded-xl bg-surface-900/50 px-3 py-2.5 text-center">
              <div className="flex items-center justify-center gap-1.5 text-surface-400 mb-1">
                <Users className="h-3.5 w-3.5" />
                <span className="text-xs">Attempts</span>
              </div>
              <p className="text-lg font-bold text-surface-100">
                {challenge.attemptCount}
              </p>
            </div>
            <div className="rounded-xl bg-surface-900/50 px-3 py-2.5 text-center">
              <div className="flex items-center justify-center gap-1.5 text-surface-400 mb-1">
                <Clock className="h-3.5 w-3.5" />
                <span className="text-xs">Completions</span>
              </div>
              <p className="text-lg font-bold text-surface-100">
                {challenge.completionCount}
              </p>
            </div>
          </div>

          {/* Actions */}
          <div className="flex flex-col gap-2">
            {challenge.status === "active" && (
              <Button className="w-full">
                <Play className="h-4 w-4" />
                Attempt Challenge
              </Button>
            )}

            <a
              href={`/api/challenges/${challenge.id}/save/download`}
              download
              className="w-full"
            >
              <Button variant="secondary" className="w-full">
                <Download className="h-4 w-4" />
                Download Save State
                <span className="text-xs text-surface-500 ml-auto">
                  {formatFileSize(challenge.saveFileSize)}
                </span>
              </Button>
            </a>

            {canDelete && (
              <Button
                variant="ghost"
                className="w-full text-danger-500 hover:text-danger-400"
                onClick={() => setShowDeleteModal(true)}
                aria-label={`Delete challenge: ${challenge.name}`}
              >
                <Trash2 className="h-4 w-4" />
                Delete Challenge
              </Button>
            )}
          </div>

          {challenge.expiresAt && (
            <p className="text-xs text-surface-500">
              Expires {formatRelativeTime(challenge.expiresAt)}
            </p>
          )}
        </div>
      </div>

      {/* Leaderboard */}
      <ChallengeLeaderboard challengeId={challenge.id} />

      {/* Delete confirm modal */}
      <Modal
        open={showDeleteModal}
        onClose={() => setShowDeleteModal(false)}
        title="Delete Challenge"
        size="sm"
      >
        <p className="text-sm text-surface-300 mb-6">
          Are you sure you want to delete this challenge? All attempts and
          leaderboard entries will be permanently removed.
        </p>
        <div className="flex justify-end gap-3">
          <Button
            variant="secondary"
            onClick={() => setShowDeleteModal(false)}
          >
            Cancel
          </Button>
          <Button
            variant="danger"
            loading={deleteChallenge.isPending}
            onClick={handleDelete}
          >
            Delete
          </Button>
        </div>
      </Modal>
    </SectionList>
    </PageLayout>
  );
}

export default ChallengeDetailPage;
