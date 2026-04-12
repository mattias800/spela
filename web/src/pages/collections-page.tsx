import { useState } from "react";
import { Link } from "react-router-dom";
import { FolderOpen, Plus, Globe, Lock, Gamepad2 } from "lucide-react";
import { PageLayout, SectionList } from "@/components/layout";
import {
  Button,
  Input,
  Modal,
  Switch,
  Badge,
  GameCardSkeleton,
  EmptyState,
} from "@/components/ui";
import { useToast } from "@/components/ui";
import {
  useMyCollections,
  usePublicCollections,
  useCreateCollection,
} from "@/hooks/use-collections";
import { Pagination } from "@/components/pagination";
import type { Collection } from "@/types/api";

type Tab = "mine" | "public";

function CollectionCard({ collection }: { collection: Collection }) {
  return (
    <Link
      to={`/collections/${collection.id}`}
      className="group block space-y-3"
    >
      <div className="relative aspect-[3/4] rounded-2xl overflow-hidden bg-surface-900 border border-surface-800/50 transition-all duration-300 group-hover:border-surface-700/50 group-hover:shadow-xl group-hover:shadow-black/30 group-hover:-translate-y-1">
        {collection.coverUrl ? (
          <img
            src={collection.coverUrl}
            alt={collection.name}
            className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
            loading="lazy"
          />
        ) : (
          <div className="h-full w-full flex items-center justify-center bg-gradient-to-br from-surface-800 to-surface-900">
            <FolderOpen className="h-12 w-12 text-surface-700" />
          </div>
        )}

        {/* Hover overlay */}
        <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300" />

        {/* Badges */}
        <div className="absolute top-2.5 right-2.5 flex flex-col gap-1.5">
          <Badge variant={collection.isPublic ? "success" : "default"}>
            {collection.isPublic ? (
              <Globe className="h-3 w-3 mr-1" />
            ) : (
              <Lock className="h-3 w-3 mr-1" />
            )}
            {collection.isPublic ? "Public" : "Private"}
          </Badge>
        </div>

        {/* Game count */}
        <div className="absolute bottom-2.5 left-2.5 opacity-0 group-hover:opacity-100 transition-opacity duration-300">
          <Badge variant="brand">
            <Gamepad2 className="h-3 w-3 mr-1" />
            {collection.gameCount}{" "}
            {collection.gameCount === 1 ? "game" : "games"}
          </Badge>
        </div>
      </div>

      <div className="px-1 space-y-1">
        <h3 className="text-sm font-semibold text-surface-100 truncate group-hover:text-brand-400 transition-colors">
          {collection.name}
        </h3>
        {collection.description && (
          <p className="text-xs text-surface-500 truncate">
            {collection.description}
          </p>
        )}
      </div>
    </Link>
  );
}

export function CollectionsPage() {
  const [activeTab, setActiveTab] = useState<Tab>("mine");
  const [page, setPage] = useState(1);
  const pageSize = 24;

  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newName, setNewName] = useState("");
  const [newDescription, setNewDescription] = useState("");
  const [newIsPublic, setNewIsPublic] = useState(false);

  const { toast } = useToast();
  const createCollection = useCreateCollection();

  const { data: myData, isLoading: myLoading } = useMyCollections(
    activeTab === "mine" ? page : 1,
    pageSize,
  );

  const { data: publicData, isLoading: publicLoading } = usePublicCollections(
    activeTab === "public" ? page : 1,
    pageSize,
  );

  const data = activeTab === "mine" ? myData : publicData;
  const isLoading = activeTab === "mine" ? myLoading : publicLoading;
  const collections = data?.data ?? [];

  function handleTabChange(tab: Tab) {
    setActiveTab(tab);
    setPage(1);
  }

  function handleCreate() {
    if (!newName.trim()) return;
    createCollection.mutate(
      {
        name: newName.trim(),
        description: newDescription.trim() || undefined,
        isPublic: newIsPublic,
      },
      {
        onSuccess: () => {
          toast("success", "Collection created");
          setShowCreateModal(false);
          setNewName("");
          setNewDescription("");
          setNewIsPublic(false);
        },
        onError: () => {
          toast("error", "Failed to create collection");
        },
      },
    );
  }

  return (
    <PageLayout>
      <SectionList>
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-3xl font-bold text-surface-100">Collections</h1>
          <p className="mt-1 text-surface-400">
            Organize your games into custom collections.
          </p>
        </div>
        <Button onClick={() => setShowCreateModal(true)}>
          <Plus className="h-4 w-4" />
          Create Collection
        </Button>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 rounded-lg bg-surface-900/50 p-1 w-fit">
        <button
          onClick={() => handleTabChange("mine")}
          className={`px-4 py-2 text-sm font-medium rounded-md transition-colors ${
            activeTab === "mine"
              ? "bg-surface-800 text-surface-100"
              : "text-surface-400 hover:text-surface-200"
          }`}
        >
          My Collections
        </button>
        <button
          onClick={() => handleTabChange("public")}
          className={`px-4 py-2 text-sm font-medium rounded-md transition-colors ${
            activeTab === "public"
              ? "bg-surface-800 text-surface-100"
              : "text-surface-400 hover:text-surface-200"
          }`}
        >
          Public Collections
        </button>
      </div>

      {/* Content */}
      {isLoading ? (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-5">
          {Array.from({ length: 12 }, (_, i) => (
            <GameCardSkeleton key={i} />
          ))}
        </div>
      ) : collections.length === 0 ? (
        <EmptyState
          icon={FolderOpen}
          title={
            activeTab === "mine"
              ? "No collections yet"
              : "No public collections"
          }
          description={
            activeTab === "mine"
              ? "Create a collection to organize your favorite games."
              : "No users have shared public collections yet."
          }
          action={
            activeTab === "mine" ? (
              <Button onClick={() => setShowCreateModal(true)}>
                <Plus className="h-4 w-4" />
                Create Collection
              </Button>
            ) : undefined
          }
        />
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-5">
          {collections.map((collection) => (
            <CollectionCard key={collection.id} collection={collection} />
          ))}
        </div>
      )}

      {data && (
        <Pagination
          total={data.total}
          pageSize={pageSize}
          currentPage={page}
          onPageChange={setPage}
        />
      )}

      {/* Create Collection Modal */}
      <Modal
        open={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        title="Create Collection"
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            handleCreate();
          }}
          className="space-y-4"
        >
          <Input
            id="collection-name"
            label="Name"
            placeholder="My Favorite RPGs"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            required
          />
          <div className="space-y-1.5">
            <label
              htmlFor="collection-description"
              className="block text-sm font-medium text-surface-300"
            >
              Description
            </label>
            <textarea
              id="collection-description"
              placeholder="A brief description of this collection..."
              value={newDescription}
              onChange={(e) => setNewDescription(e.target.value)}
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
            <Switch checked={newIsPublic} onChange={setNewIsPublic} />
          </div>
          <div className="flex justify-end gap-3 pt-2">
            <Button
              variant="secondary"
              type="button"
              onClick={() => setShowCreateModal(false)}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              loading={createCollection.isPending}
              disabled={!newName.trim()}
            >
              Create
            </Button>
          </div>
        </form>
      </Modal>
    </SectionList>
    </PageLayout>
  );
}
