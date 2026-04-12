import { Archive, HardDrive, Sparkles } from "lucide-react";
import {
  Section,
  Button,
  Skeleton,
  EmptyState,
} from "@/components/ui";
import { PageLayout, SectionList } from "@/components/layout";
import { useToast } from "@/components/ui";
import { useStorage, useCompactSaves } from "@/hooks/use-storage";
import { formatFileSize } from "@/lib/format";
import { StorageBar } from "@/features/storage/components/storage-bar";
import { ConsoleBreakdownTable } from "@/features/storage/components/console-breakdown-table";

function StorageSkeleton() {
  return (
    <div className="space-y-6">
      <Section>
        <div className="px-5 pt-5 pb-2">
          <Skeleton className="h-6 w-40" />
        </div>
        <div className="px-5 pb-5">
          <div className="space-y-3">
            <Skeleton className="h-4 w-64" />
            <Skeleton className="h-3 w-full rounded-full" />
          </div>
        </div>
      </Section>
      <Section>
        <div className="px-5 pt-5 pb-2">
          <Skeleton className="h-6 w-48" />
        </div>
        <div className="px-5 pb-5">
          <div className="space-y-3">
            {Array.from({ length: 4 }, (_, i) => (
              <Skeleton key={i} className="h-10 w-full" />
            ))}
          </div>
        </div>
      </Section>
    </div>
  );
}

export function StoragePage() {
  const { data: storage, isLoading, error } = useStorage();
  const compactSaves = useCompactSaves();
  const { toast } = useToast();

  function handleCompact() {
    compactSaves.mutate(undefined, {
      onSuccess: (result) => {
        if (result.deletedCount === 0) {
          toast("info", "No saves to compact. Storage is already optimized.");
        } else {
          toast(
            "success",
            `Compacted ${result.deletedCount} save${result.deletedCount !== 1 ? "s" : ""}, freed ${formatFileSize(result.freedBytes)}.`,
          );
        }
      },
      onError: (err) => {
        toast("error", err.message);
      },
    });
  }

  if (isLoading) {
    return (
      <PageLayout title="Storage" subtitle="Manage your save state storage.">
        <SectionList>
          <StorageSkeleton />
        </SectionList>
      </PageLayout>
    );
  }

  if (error || !storage) {
    return (
      <PageLayout title="Storage" subtitle="Manage your save state storage.">
        <SectionList>
          <EmptyState
            icon={HardDrive}
            title="Unable to load storage info"
            description="Something went wrong while fetching your storage data. Please try again later."
          />
        </SectionList>
      </PageLayout>
    );
  }

  const totalSaves = storage.byConsole.reduce(
    (acc, c) => acc + c.saveCount,
    0,
  );
  const hasSaves = totalSaves > 0;

  return (
    <PageLayout title="Storage" subtitle="Manage your save state storage.">
      <SectionList>
      {/* Storage usage card */}
      <Section>
        <div className="px-5 pt-5 pb-2">
          <div className="flex items-center gap-2.5">
            <HardDrive className="h-5 w-5 text-brand-400" />
            <h2 className="text-lg font-semibold text-surface-100">
              Storage Usage
            </h2>
          </div>
        </div>
        <div className="px-5 pb-5">
          <StorageBar
            usedBytes={storage.usedBytes}
            quotaBytes={storage.quotaBytes}
          />
        </div>
      </Section>

      {/* Console breakdown card */}
      <Section>
        <div className="px-5 pt-5 pb-2">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <Archive className="h-5 w-5 text-brand-400" />
              <h2 className="text-lg font-semibold text-surface-100">
                By Console
              </h2>
            </div>
            {hasSaves && (
              <Button
                variant="secondary"
                size="sm"
                icon={<Sparkles className="h-4 w-4" />}
                loading={compactSaves.isPending}
                onClick={handleCompact}
              >
                Compact All Saves
              </Button>
            )}
          </div>
        </div>
        <div className="px-5 pb-5">
          {hasSaves ? (
            <ConsoleBreakdownTable consoles={storage.byConsole} />
          ) : (
            <EmptyState
              icon={Archive}
              title="No saves yet"
              description="Play some games and your save state storage usage will appear here."
              className="py-10"
            />
          )}
        </div>
      </Section>
    </SectionList>
    </PageLayout>
  );
}
