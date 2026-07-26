"use client";

import { CodeViewerContent } from "@/components/code-analysis/code-viewer-content";
import { backendApiFetch, backendApiUrl } from "@/lib/backend-api";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";

type FileView = {
  path: string;
  content: string;
  startLine: number;
  endLine: number;
  totalLines: number;
  hasPrevious: boolean;
  hasNext: boolean;
  truncated: boolean;
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

  if (error) return <ViewerMessage title="Unable to load file" description={error} />;
  if (!view) return <ViewerMessage title="Loading source file" description={path} />;

  return (
    <CodeViewerContent
      filePaths={files}
      path={view.path}
      content={view.content}
      startLine={view.startLine}
      endLine={view.endLine}
      totalLines={view.totalLines}
      highlightedStartLine={highlightedStart}
      highlightedEndLine={highlightedEnd}
      autoScrollToHighlight={
        highlightedStart != null &&
        params.get("viewStartLine") == null &&
        params.get("viewEndLine") == null
      }
      truncated={view.truncated}
      hasPrevious={view.hasPrevious}
      hasNext={view.hasNext}
    />
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
