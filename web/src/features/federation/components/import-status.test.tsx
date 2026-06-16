import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect } from "vitest";
import { ImportJobRow, ImportsQueue } from "./import-status";
import type { ImportJob } from "@/generated/schemas";

function makeJob(overrides: Partial<ImportJob> = {}): ImportJob {
  return {
    id: 1,
    status: "pending",
    key: "igdb:42",
    title: "Chrono Trigger",
    console: "SNES",
    bytesDownloaded: 0,
    totalBytes: 0,
    errorMessage: "",
    gameId: null,
    requestedByUserId: 7,
    createdAt: "2026-06-16T00:00:00Z",
    updatedAt: "2026-06-16T00:00:00Z",
    startedAt: null,
    completedAt: null,
    ...overrides,
  };
}

function renderRow(job: ImportJob) {
  return render(
    <MemoryRouter>
      <ImportJobRow job={job} />
    </MemoryRouter>,
  );
}

describe("ImportJobRow", () => {
  it("shows a Queued status for a pending job", () => {
    renderRow(makeJob({ status: "pending" }));
    expect(screen.getByTestId("import-job-status")).toHaveTextContent("Queued");
  });

  it("shows a byte progress bar while downloading with a known size", () => {
    renderRow(
      makeJob({ status: "downloading", bytesDownloaded: 512, totalBytes: 1024 }),
    );
    expect(screen.getByTestId("import-job-status")).toHaveTextContent(
      "Downloading",
    );
    // ProgressBar renders a progressbar role with the byte values.
    const bar = screen.getByRole("progressbar");
    expect(bar).toHaveAttribute("aria-valuenow", "512");
    expect(bar).toHaveAttribute("aria-valuemax", "1024");
  });

  it("surfaces the error message for a failed job", () => {
    renderRow(
      makeJob({ status: "failed", errorMessage: "no connected server" }),
    );
    expect(screen.getByTestId("import-job-status")).toHaveTextContent("Failed");
    expect(screen.getByTestId("import-job-error")).toHaveTextContent(
      "no connected server",
    );
  });

  it("links into the library once imported", () => {
    renderRow(makeJob({ status: "completed", gameId: 99 }));
    expect(screen.getByTestId("import-job-status")).toHaveTextContent(
      "Imported",
    );
    const link = screen.getByTestId("import-job-open-game");
    expect(link).toHaveAttribute("href", "/games/99");
  });
});

describe("ImportsQueue", () => {
  it("renders an empty state with no jobs", () => {
    render(
      <MemoryRouter>
        <ImportsQueue jobs={[]} />
      </MemoryRouter>,
    );
    expect(screen.getByText("No imports yet")).toBeInTheDocument();
  });

  it("renders a row per job", () => {
    render(
      <MemoryRouter>
        <ImportsQueue
          jobs={[
            makeJob({ id: 1, title: "Game One" }),
            makeJob({ id: 2, title: "Game Two" }),
          ]}
        />
      </MemoryRouter>,
    );
    expect(screen.getByTestId("import-job-1")).toHaveTextContent("Game One");
    expect(screen.getByTestId("import-job-2")).toHaveTextContent("Game Two");
  });
});
