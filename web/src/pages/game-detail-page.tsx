import { useParams, useNavigate } from "react-router-dom";
import {
  ArrowLeft,
  Heart,
  Calendar,
  Users,
  Building2,
  Star,
  HardDrive,
  Trash2,
  Download,
} from "lucide-react";
import { Button, Badge, Card, CardContent, GameDetailSkeleton, Modal } from "@/components/ui";
import { useGame, useGameSaves, useToggleFavorite, useDeleteSave } from "@/hooks/use-games";
import { formatFileSize, formatRelativeTime } from "@/lib/format";
import { cn } from "@/lib/cn";
import { useState } from "react";

export function GameDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: game, isLoading } = useGame(id ?? "");
  const { data: saves } = useGameSaves(id ?? "");
  const toggleFavorite = useToggleFavorite();
  const deleteSave = useDeleteSave();
  const [deleteTarget, setDeleteTarget] = useState<number | null>(null);

  const isFavorite = game?.isFavorite ?? false;

  if (isLoading) {
    return (
      <div className="max-w-5xl">
        <GameDetailSkeleton />
      </div>
    );
  }

  if (!game) {
    return (
      <div className="text-center py-20">
        <p className="text-surface-400">Game not found</p>
        <Button variant="ghost" onClick={() => navigate(-1)} className="mt-4">
          Go back
        </Button>
      </div>
    );
  }

  const consoleName = game.consoleName ?? "";

  return (
    <div className="max-w-5xl space-y-8">
      {/* Back button */}
      <button
        onClick={() => navigate(-1)}
        className="flex items-center gap-2 text-sm text-surface-400 hover:text-surface-100 transition-colors"
      >
        <ArrowLeft className="h-4 w-4" />
        Back
      </button>

      {/* Hero section */}
      <div className="flex gap-8">
        {/* Cover art */}
        <div className="w-64 flex-shrink-0">
          <div className="aspect-[3/4] rounded-2xl overflow-hidden bg-surface-900 border border-surface-800 shadow-2xl">
            {game.coverUrl ? (
              <img
                src={game.coverUrl}
                alt={game.title}
                className="h-full w-full object-cover"
              />
            ) : (
              <div className="h-full w-full flex items-center justify-center bg-gradient-to-br from-surface-800 to-surface-900">
                <span className="text-5xl font-bold text-surface-700">
                  {game.title.charAt(0)}
                </span>
              </div>
            )}
          </div>
        </div>

        {/* Info */}
        <div className="flex-1 space-y-5 pt-2">
          <div>
            <div className="flex items-start justify-between gap-4">
              <h1 className="text-3xl font-bold text-surface-100">
                {game.title}
              </h1>
              <Button
                variant={isFavorite ? "danger" : "secondary"}
                size="sm"
                onClick={() =>
                  toggleFavorite.mutate({
                    gameId: game.id,
                    isFavorite,
                  })
                }
              >
                <Heart
                  className={cn(
                    "h-4 w-4",
                    isFavorite && "fill-current",
                  )}
                />
                {isFavorite ? "Unfavorite" : "Favorite"}
              </Button>
            </div>
            {consoleName && (
              <Badge variant="brand" className="mt-2">{consoleName}</Badge>
            )}
          </div>

          {/* Metadata grid */}
          <div className="grid grid-cols-2 gap-3">
            {game.developer && (
              <MetaItem icon={Building2} label="Developer" value={game.developer} />
            )}
            {game.publisher && (
              <MetaItem icon={Building2} label="Publisher" value={game.publisher} />
            )}
            {game.releaseDate && (
              <MetaItem icon={Calendar} label="Released" value={game.releaseDate} />
            )}
            {game.genre && (
              <MetaItem icon={Star} label="Genre" value={game.genre} />
            )}
            {game.players && (
              <MetaItem icon={Users} label="Players" value={`${game.players}`} />
            )}
            <MetaItem icon={HardDrive} label="Size" value={formatFileSize(game.fileSize)} />
            {game.rating !== undefined && game.rating > 0 && (
              <MetaItem icon={Star} label="Rating" value={`${game.rating}/10`} />
            )}
          </div>

          {/* Description */}
          {game.description && (
            <p className="text-sm text-surface-300 leading-relaxed">
              {game.description}
            </p>
          )}
        </div>
      </div>

      {/* Screenshots */}
      {game.screenshotUrls && game.screenshotUrls.length > 0 && (
        <section>
          <h2 className="text-xl font-bold text-surface-100 mb-4">
            {game.screenshotUrls.length === 1 ? "Screenshot" : "Screenshots"}
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 max-w-4xl">
            {game.screenshotUrls.map((url, i) => (
              <div key={i} className="rounded-xl overflow-hidden border border-surface-800 bg-surface-900">
                <img
                  src={url}
                  alt={`${game.title} screenshot ${i + 1}`}
                  className="w-full aspect-video object-cover hover:scale-105 transition-transform duration-500"
                  loading="lazy"
                />
              </div>
            ))}
          </div>
        </section>
      )}

      {/* Save States */}
      <section>
        <h2 className="text-xl font-bold text-surface-100 mb-4">Save States</h2>
        {!saves || saves.length === 0 ? (
          <Card>
            <CardContent className="py-8 text-center">
              <p className="text-surface-400">No save states yet. Play the game in the Spela player to create saves.</p>
            </CardContent>
          </Card>
        ) : (
          <div className="space-y-2">
            {saves.map((save) => (
              <Card key={save.id} hover>
                <CardContent className="flex items-center gap-4 py-3">
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-surface-100">
                      {save.name}
                      {save.isAuto && (
                        <Badge variant="brand" className="ml-2">Auto</Badge>
                      )}
                    </p>
                    <p className="text-xs text-surface-500">
                      {formatRelativeTime(save.createdAt)} &middot; {formatFileSize(save.fileSize)}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <a
                      href={`/api/games/${game.id}/saves/${save.id}`}
                      download
                      className="inline-flex items-center justify-center p-2 rounded-lg text-surface-300 hover:text-surface-100 hover:bg-surface-800/50 transition-colors"
                    >
                      <Download className="h-4 w-4" />
                    </a>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => setDeleteTarget(save.id)}
                    >
                      <Trash2 className="h-4 w-4 text-danger-500" />
                    </Button>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        )}
      </section>

      {/* Delete confirm modal */}
      <Modal
        open={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        title="Delete Save State"
        size="sm"
      >
        <p className="text-sm text-surface-300 mb-6">
          Are you sure you want to delete this save state? This action cannot be undone.
        </p>
        <div className="flex justify-end gap-3">
          <Button variant="secondary" onClick={() => setDeleteTarget(null)}>
            Cancel
          </Button>
          <Button
            variant="danger"
            loading={deleteSave.isPending}
            onClick={() => {
              if (deleteTarget !== null) {
                deleteSave.mutate(
                  { gameId: game.id, saveId: deleteTarget },
                  { onSuccess: () => setDeleteTarget(null) },
                );
              }
            }}
          >
            Delete
          </Button>
        </div>
      </Modal>
    </div>
  );
}

function MetaItem({
  icon: Icon,
  label,
  value,
}: {
  icon: typeof Calendar;
  label: string;
  value: string;
}) {
  return (
    <div className="flex items-center gap-2.5 text-sm">
      <Icon className="h-4 w-4 text-surface-500 flex-shrink-0" />
      <div>
        <span className="text-surface-500">{label}:</span>{" "}
        <span className="text-surface-200">{value}</span>
      </div>
    </div>
  );
}
