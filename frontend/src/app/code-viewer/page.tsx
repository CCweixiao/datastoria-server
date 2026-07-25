"use client";

import { backendApiFetch, backendApiUrl } from "@/lib/backend-api";
import { BasePath } from "@/lib/base-path";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useMemo, useState } from "react";

type FileView = {
  path: string;
  content: string;
  startLine: number;
  endLine: number;
  totalLines: number;
  hasPrevious: boolean;
  hasNext: boolean;
};

const WINDOW_LINES = 200;

function positive(value: string | null): number | undefined {
  if (!value) return undefined;
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined;
}

function CodeViewer() {
  const params = useSearchParams();
  const path = params.get("path") ?? "";
  const highlightedStart = positive(params.get("startLine"));
  const highlightedEnd = positive(params.get("endLine")) ?? highlightedStart;
  const requestedStart = positive(params.get("viewStartLine")) ?? highlightedStart ?? 1;
  const [view, setView] = useState<FileView>();
  const [files, setFiles] = useState<string[]>([]);
  const [filter, setFilter] = useState("");
  const [error, setError] = useState<string>();

  useEffect(() => {
    backendApiFetch(backendApiUrl("/api/code/files"))
      .then(async (response) => {
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return (await response.json()) as { paths: string[] };
      })
      .then((result) => setFiles(result.paths))
      .catch(() => {
        // Reading the selected file remains useful if the optional tree cannot be loaded.
      });
  }, []);

  useEffect(() => {
    if (!path) {
      setError("This viewer needs a repo-relative path in the query string.");
      return;
    }
    const query = new URLSearchParams({
      path,
      startLine: String(Math.max(1, requestedStart - Math.floor(WINDOW_LINES / 2))),
      endLine: String(Math.max(WINDOW_LINES, requestedStart + Math.floor(WINDOW_LINES / 2))),
    });
    backendApiFetch(backendApiUrl(`/api/code/file?${query}`))
      .then(async (response) => {
        if (!response.ok) throw new Error((await response.text()) || `HTTP ${response.status}`);
        return (await response.json()) as FileView;
      })
      .then(setView)
      .catch((reason: unknown) =>
        setError(reason instanceof Error ? reason.message : "Unable to load file")
      );
  }, [path, requestedStart]);

  const lines = useMemo(() => view?.content.split("\n") ?? [], [view]);
  const visibleFiles = useMemo(() => {
    const needle = filter.trim().toLowerCase();
    return (needle ? files.filter((file) => file.toLowerCase().includes(needle)) : files).slice(
      0,
      500
    );
  }, [files, filter]);
  if (error) return <ViewerMessage title="Unable to load file" description={error} />;
  if (!view) return <ViewerMessage title="Loading source file" description={path} />;

  return (
    <main className="min-h-screen bg-background p-4 text-foreground">
      <header className="mx-auto mb-3 max-w-[1500px] rounded-lg border bg-card px-4 py-3">
        <h1 className="truncate font-mono text-sm font-semibold">{view.path}</h1>
        <p className="text-xs text-muted-foreground">
          Lines {view.startLine}–{view.endLine} of {view.totalLines}
        </p>
      </header>
      <div className="mx-auto grid max-w-[1500px] gap-3 lg:grid-cols-[300px_minmax(0,1fr)]">
        <aside className="max-h-[calc(100vh-7rem)] overflow-auto rounded-lg border bg-card p-3">
          <input
            className="mb-2 w-full rounded border bg-background px-2 py-1.5 text-xs"
            placeholder="Filter repository files"
            value={filter}
            onChange={(event) => setFilter(event.target.value)}
          />
          <nav className="space-y-0.5">
            {visibleFiles.map((file) => (
              <a
                className={`block truncate rounded px-2 py-1 font-mono text-xs hover:bg-muted ${
                  file === view.path ? "bg-muted font-semibold" : ""
                }`}
                href={BasePath.getURL(`/code-viewer?path=${encodeURIComponent(file)}`)}
                key={file}
                title={file}
              >
                {file}
              </a>
            ))}
          </nav>
        </aside>
        <pre className="overflow-x-auto rounded-lg border bg-card py-3 text-xs leading-5">
        {lines.map((line, index) => {
          const number = view.startLine + index;
          const highlighted =
            highlightedStart != null &&
            highlightedEnd != null &&
            number >= highlightedStart &&
            number <= highlightedEnd;
          return (
            <div
              key={number}
              className={highlighted ? "bg-yellow-500/15" : "hover:bg-muted/40"}
            >
              <span className="mr-4 inline-block w-14 select-none text-right text-muted-foreground">
                {number}
              </span>
              <code>{line || " "}</code>
            </div>
          );
        })}
        </pre>
      </div>
    </main>
  );
}

function ViewerMessage({ title, description }: { title: string; description: string }) {
  return (
    <main className="flex min-h-screen items-center justify-center bg-background p-6">
      <section className="w-full max-w-xl rounded-lg border bg-card p-8">
        <h1 className="text-xl font-semibold">{title}</h1>
        <p className="mt-2 text-sm text-muted-foreground">{description}</p>
      </section>
    </main>
  );
}

export default function CodeViewerPage() {
  return (
    <Suspense fallback={<ViewerMessage title="Loading source file" description="" />}>
      <CodeViewer />
    </Suspense>
  );
}
