import { Link } from "react-router-dom";
import { Inbox } from "lucide-react";
import { Badge, ProgressBar, EmptyState } from "@/components/ui";
import { Card } from "@/components/ui/card";
import { ConsoleBadge } from "@/components/console-badge";
import { formatFileSize } from "@/lib/format";
import type { ImportJob } from "@/generated/schemas";

// Human label for each job status. The server drives the state machine:
// pending -> downloading -> ingesting -> scraping -> completed | failed.
function importStatusLabel(status: string): string {
  switch (status) {
    case "pending":
      return "Queued";
    case "downloading":
      return "Downloading";
    case "ingesting":
      return "Adding to library";
    case "scraping":
      return "Fetching metadata";
    case "completed":
      return "Imported";
    case "failed":
      return "Failed";
    default:
      return status;
  }
}

function statusVariant(
  status: string,
): "default" | "brand" | "success" | "danger" {
  if (status === "completed") return "success";
  if (status === "failed") return "danger";
  if (status === "pending") return "default";
  return "brand";
}

// One import job: title + console, a status badge, and the state-appropriate
// detail — a byte progress bar while downloading, the error when failed, or a
// link into the local library once imported.
export function ImportJobRow({ job }: { job: ImportJob }) {
  return (
    <Card data-testid={`import-job-${job.id}`} className="p-3">
      <div className="flex flex-col gap-2">
        <div className="flex items-center justify-between gap-3">
          <div className="flex min-w-0 flex-col gap-1">
            <p className="truncate text-sm font-medium text-surface-100">
              {job.title || job.key}
            </p>
            <ConsoleBadge code={job.console} className="self-start" />
          </div>
          <Badge
            variant={statusVariant(job.status)}
            data-testid="import-job-status"
          >
            {importStatusLabel(job.status)}
          </Badge>
        </div>

        {job.status === "downloading" && job.totalBytes > 0 && (
          <ProgressBar
            value={job.bytesDownloaded}
            max={job.totalBytes}
            size="sm"
            showPercentage
            label={`${formatFileSize(job.bytesDownloaded)} / ${formatFileSize(job.totalBytes)}`}
          />
        )}
        {job.status === "downloading" && job.totalBytes <= 0 && (
          <p className="text-xs text-surface-400">
            {formatFileSize(job.bytesDownloaded)} downloaded
          </p>
        )}

        {job.status === "failed" && job.errorMessage && (
          <p className="text-xs text-danger-500" data-testid="import-job-error">
            {job.errorMessage}
          </p>
        )}

        {job.status === "completed" && job.gameId != null && (
          <Link
            to={`/games/${job.gameId}`}
            data-testid="import-job-open-game"
            className="text-xs font-medium text-brand-400 hover:text-brand-300"
          >
            View in library →
          </Link>
        )}
      </div>
    </Card>
  );
}

// A live list of import jobs (newest first).
export function ImportsQueue({ jobs }: { jobs: ImportJob[] }) {
  if (jobs.length === 0) {
    return (
      <EmptyState
        icon={Inbox}
        title="No imports yet"
        description="Games you import from connected servers will appear here."
      />
    );
  }
  return (
    <div className="flex flex-col gap-2" data-testid="imports-queue">
      {jobs.map((job) => (
        <ImportJobRow key={job.id} job={job} />
      ))}
    </div>
  );
}
