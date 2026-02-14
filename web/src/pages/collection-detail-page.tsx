import { useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import {
  Globe,
  Lock,
  Pencil,
  Trash2,
  Gamepad2,
} from "lucide-react";
import {
  Button,
  BackButton,
  Input,
  Modal,
  Switch,
  Badge,
  Skeleton,
  EmptyState,
  SearchInput,
} from "@/components/ui";
import { useToast } from "@/components/ui";
import { GameCard } from "@/components/game-card";
import { GameGrid } from "@/components/game-grid";
import { useToggleFavorite } from "@/hooks/use-games";
import { useTogglePlayLater } from "@/hooks/use-play-later";
import {
  useCollection,
  useUpdateCollection,
  useDeleteCollection,
  useRemoveGameFromCollection,
} from "@/hooks/use-collections";
import { useAuth } from "@/hooks/use-auth";

function CollectionDetailSkeleton() {
  return (
    <div className="space-y-6">
      <Skeleton className="h-5 w-16" />
      <div className="space-y-3">
        <Skeleton className="h-9 w-72" />
        <Skeleton className="h-5 w-96" />
        <div className="flex gap-2">
          <Skeleton className="h-6 w-16 rounded-full" />
          <Skeleton className="h-6 w-24 rounded-full" />
        </div>
      </div>
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-5">
        {Array.from({ length: 6 }, (_, i) => (
          <div key={i}>
            <Skeleton className="aspect-[3/4] rounded-2xl" />
            <div className="mt-3 px-1 space-y-1">
              <Skeleton className="h-4 w-24" />
              <Skeleton className="h-3 w-16" />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

export function CollectionDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const { toast } = useToast();
  const { toggle: handleToggleFavorite } = useToggleFavorite();
  const { toggle: handleTogglePlayLater } = useTogglePlayLater();

  const { data: collection, isLoading } = useCollection(id ?? "");
  const updateCollection = useUpdateCollection();
  const deleteCollection = useDeleteCollection();
  const removeGame = useRemoveGameFromCollection();

  const [search, setSearch] = useState("");
  const [showEditModal, setShowEditModal] = useState(false);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [editName, setEditName] = useState("");
  const [editDescription, setEditDescription] = useState("");
  const [editIsPublic, setEditIsPublic] = useState(false);

  const isOwner = collection?.userId === user?.id;

  function openEditModal() {
    if (!collection) return;
    setEditName(collection.name);
    setEditDescription(collection.description ?? "");
    setEditIsPublic(collection.isPublic);
    setShowEditModal(true);
  }

  function handleUpdate() {
    if (!collection || !editName.trim()) return;
    updateCollection.mutate(
      {
        id: collection.id,
        name: editName.trim(),
        description: editDescription.trim() || undefined,
        isPublic: editIsPublic,
      },
      {
        onSuccess: () => {
          toast("success", "Collection updated");
          setShowEditModal(false);
        },
        onError: () => {
          toast("error", "Failed to update collection");
        },
      },
    );
  }

  function handleDelete() {
    if (!collection) return;
    deleteCollection.mutate(collection.id, {
      onSuccess: () => {
        toast("success", "Collection deleted");
        navigate("/collections");
      },
      onError: () => {
        toast("error", "Failed to delete collection");
      },
    });
  }

  function handleRemoveGame(gameId: string) {
    if (!collection) return;
    removeGame.mutate(
      { collectionId: collection.id, gameId },
      {
        onSuccess: () => {
          toast("success", "Game removed from collection");
        },
        onError: () => {
          toast("error", "Failed to remove game");
        },
      },
    );
  }

  if (isLoading) {
    return (
      <div className="max-w-5xl">
        <CollectionDetailSkeleton />
      </div>
    );
  }

  if (!collection) {
    return (
      <div className="text-center py-20">
        <p className="text-surface-400">Collection not found</p>
        <Button variant="ghost" onClick={() => navigate(-1)} className="mt-4">
          Go back
        </Button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <BackButton onClick={() => navigate("/collections")}>Collections</BackButton>

      {/* Header */}
      <div className="flex items-start justify-between">
        <div className="space-y-2">
          <h1 className="text-3xl font-bold text-surface-100">
            {collection.name}
          </h1>
          {collection.description && (
            <p className="text-surface-400 max-w-2xl">
              {collection.description}
            </p>
          )}
          <div className="flex items-center gap-3">
            <Badge variant={collection.isPublic ? "success" : "default"}>
              {collection.isPublic ? (
                <Globe className="h-3 w-3 mr-1" />
              ) : (
                <Lock className="h-3 w-3 mr-1" />
              )}
              {collection.isPublic ? "Public" : "Private"}
            </Badge>
            <span className="text-sm text-surface-500">
              by {collection.username}
            </span>
            <span className="text-sm text-surface-500">
              {collection.gameCount} {collection.gameCount === 1 ? "game" : "games"}
            </span>
          </div>
        </div>

        {isOwner && (
          <div className="flex items-center gap-2">
            <Button variant="secondary" size="sm" onClick={openEditModal}>
              <Pencil className="h-4 w-4" />
              Edit
            </Button>
            <Button
              variant="danger"
              size="sm"
              onClick={() => setShowDeleteModal(true)}
            >
              <Trash2 className="h-4 w-4" />
              Delete
            </Button>
          </div>
        )}
      </div>

      {/* Games */}
      {collection.games.length === 0 ? (
        <EmptyState
          icon={Gamepad2}
          title="No games in this collection"
          description={
            isOwner
              ? "Add games from any game detail page."
              : "This collection has no games yet."
          }
        />
      ) : (
        <>
          {collection.games.length > 5 && (
            <SearchInput
              placeholder="Search games in this collection..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="max-w-sm"
            />
          )}
          {(() => {
            const filtered = search.trim()
              ? collection.games.filter((g) =>
                  g.title.toLowerCase().includes(search.trim().toLowerCase()),
                )
              : collection.games;

            return filtered.length === 0 ? (
              <EmptyState
                icon={Gamepad2}
                title="No matching games"
                description={`No games matching "${search.trim()}" in this collection.`}
              />
            ) : (
              <GameGrid>
                {filtered.map((game) => (
                  <div key={game.id} className="relative group/card">
                    <GameCard game={game} onToggleFavorite={handleToggleFavorite} onTogglePlayLater={handleTogglePlayLater} />
                    {isOwner && (
                      <button
                        onClick={(e) => {
                          e.preventDefault();
                          e.stopPropagation();
                          handleRemoveGame(game.id);
                        }}
                        className="absolute top-2.5 left-2.5 p-1.5 rounded-full bg-black/60 text-surface-300 hover:text-danger-500 hover:bg-black/80 opacity-0 group-hover/card:opacity-100 transition-all z-20"
                        title="Remove from collection"
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </button>
                    )}
                  </div>
                ))}
              </GameGrid>
            );
          })()}
        </>
      )}

      {/* Edit Modal */}
      <Modal
        open={showEditModal}
        onClose={() => setShowEditModal(false)}
        title="Edit Collection"
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            handleUpdate();
          }}
          className="space-y-4"
        >
          <Input
            id="edit-collection-name"
            label="Name"
            value={editName}
            onChange={(e) => setEditName(e.target.value)}
            required
          />
          <div className="space-y-1.5">
            <label
              htmlFor="edit-collection-description"
              className="block text-sm font-medium text-surface-300"
            >
              Description
            </label>
            <textarea
              id="edit-collection-description"
              value={editDescription}
              onChange={(e) => setEditDescription(e.target.value)}
              rows={3}
              className="w-full rounded-lg bg-surface-900 border border-surface-700 hover:border-surface-600 px-3.5 py-2.5 text-sm text-surface-100 placeholder:text-surface-500 transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-brand-500/40 focus:border-brand-500 resize-none"
            />
          </div>
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-surface-300">Public</p>
              <p className="text-xs text-surface-500">
                Allow other users to view this collection
              </p>
            </div>
            <Switch checked={editIsPublic} onChange={setEditIsPublic} />
          </div>
          <div className="flex justify-end gap-3 pt-2">
            <Button
              variant="secondary"
              type="button"
              onClick={() => setShowEditModal(false)}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              loading={updateCollection.isPending}
              disabled={!editName.trim()}
            >
              Save
            </Button>
          </div>
        </form>
      </Modal>

      {/* Delete Confirmation Modal */}
      <Modal
        open={showDeleteModal}
        onClose={() => setShowDeleteModal(false)}
        title="Delete Collection"
        size="sm"
      >
        <div className="space-y-4">
          <p className="text-sm text-surface-300">
            Are you sure you want to delete{" "}
            <span className="font-semibold text-surface-100">
              {collection.name}
            </span>
            ? This action cannot be undone.
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
              onClick={handleDelete}
              loading={deleteCollection.isPending}
            >
              <Trash2 className="h-4 w-4" />
              Delete
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
