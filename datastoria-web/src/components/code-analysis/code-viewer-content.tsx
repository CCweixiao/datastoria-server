"use client";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Tree, type TreeDataItem } from "@/components/ui/tree";
import { BasePath } from "@/lib/base-path";
import { ChevronDown, ChevronUp, FileCode2, FolderClosed, Search, X } from "lucide-react";
import dynamic from "next/dynamic";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { Panel, PanelGroup, PanelResizeHandle } from "react-resizable-panels";

const ThemedSyntaxHighlighter = dynamic(
  () =>
    import("@/components/shared/themed-syntax-highlighter").then((module) => ({
      default: module.ThemedSyntaxHighlighter,
    })),
  { ssr: false }
);

function languageFor(path: string): string {
  const extension = path.split(".").pop()?.toLowerCase();
  return (
    {
      ts: "typescript",
      tsx: "typescript",
      js: "javascript",
      jsx: "javascript",
      json: "json",
      md: "markdown",
      css: "css",
      html: "html",
      sql: "sql",
      yml: "yaml",
      yaml: "yaml",
      cpp: "cpp",
      cc: "cpp",
      cxx: "cpp",
      h: "cpp",
      hpp: "cpp",
      rs: "rust",
      go: "go",
      py: "python",
      java: "java",
      sh: "bash",
    }[extension ?? ""] ?? "text"
  );
}

function fileTree(paths: string[]): TreeDataItem[] {
  const roots: TreeDataItem[] = [];
  const folders = new Map<string, TreeDataItem>();

  for (const path of paths) {
    const segments = path.split("/").filter(Boolean);
    for (let depth = 0; depth < segments.length - 1; depth++) {
      const folderPath = segments.slice(0, depth + 1).join("/");
      if (folders.has(folderPath)) continue;
      const folder: TreeDataItem = {
        id: folderPath,
        labelContent: segments[depth]!,
        search: segments[depth]!.toLowerCase(),
        type: "folder",
        icon: FolderClosed,
        children: [],
      };
      folders.set(folderPath, folder);
      if (depth === 0) roots.push(folder);
      else folders.get(segments.slice(0, depth).join("/"))?.children?.push(folder);
    }

    const leaf: TreeDataItem = {
      id: path,
      labelContent: segments.at(-1) ?? path,
      search: (segments.at(-1) ?? path).toLowerCase(),
      type: "leaf",
      icon: FileCode2,
      data: { path },
    };
    if (segments.length === 1) roots.push(leaf);
    else folders.get(segments.slice(0, -1).join("/"))?.children?.push(leaf);
  }

  const sort = (nodes: TreeDataItem[]) => {
    nodes.sort((left, right) => {
      if (left.type !== right.type) return left.type === "folder" ? -1 : 1;
      return String(left.id).localeCompare(String(right.id));
    });
    nodes.forEach((node) => node.children && sort(node.children));
  };
  sort(roots);
  return roots;
}

function viewerUrl(options: {
  path: string;
  startLine?: number;
  endLine?: number;
  viewStartLine?: number;
  viewEndLine?: number;
}) {
  const query = new URLSearchParams({ path: options.path });
  if (options.startLine) query.set("startLine", String(options.startLine));
  if (options.endLine) query.set("endLine", String(options.endLine));
  if (options.viewStartLine) query.set("viewStartLine", String(options.viewStartLine));
  if (options.viewEndLine) query.set("viewEndLine", String(options.viewEndLine));
  return BasePath.getURL(`/code-viewer?${query}`);
}

export function CodeViewerContent({
  filePaths,
  path,
  content,
  startLine,
  endLine,
  totalLines,
  highlightedStartLine,
  highlightedEndLine,
  autoScrollToHighlight,
  truncated,
  hasPrevious,
  hasNext,
}: {
  filePaths: string[];
  path: string;
  content: string;
  startLine: number;
  endLine: number;
  totalLines: number;
  highlightedStartLine?: number;
  highlightedEndLine?: number;
  autoScrollToHighlight: boolean;
  truncated: boolean;
  hasPrevious: boolean;
  hasNext: boolean;
}) {
  const router = useRouter();
  const [search, setSearch] = useState("");
  const scrollRef = useRef<HTMLDivElement>(null);
  const tree = useMemo(() => fileTree(filePaths), [filePaths]);
  const windowSize = Math.max(1, endLine - startLine + 1);
  const effectiveHighlightEnd = highlightedEndLine ?? highlightedStartLine;

  useEffect(() => {
    if (!autoScrollToHighlight || highlightedStartLine == null) return;
    let attempts = 0;
    let frame = 0;
    const scroll = () => {
      const target = scrollRef.current?.querySelector<HTMLElement>(
        `[data-code-line="${highlightedStartLine}"]`
      );
      if (!target && attempts++ < 20) {
        frame = requestAnimationFrame(scroll);
        return;
      }
      target?.scrollIntoView({ block: "center", behavior: "smooth" });
    };
    frame = requestAnimationFrame(scroll);
    return () => cancelAnimationFrame(frame);
  }, [autoScrollToHighlight, highlightedStartLine, content]);

  const navigateWindow = (viewStartLine: number, viewEndLine: number) =>
    router.push(
      viewerUrl({
        path,
        startLine: highlightedStartLine,
        endLine: highlightedEndLine,
        viewStartLine,
        viewEndLine,
      })
    );

  return (
    <main className="h-screen w-full overflow-hidden bg-background text-foreground">
      <PanelGroup direction="horizontal" className="h-full w-full min-w-0">
        <Panel defaultSize={24} minSize={16} maxSize={40} className="min-w-0">
          <aside className="flex h-full min-h-0 flex-col border-r">
            <div className="relative flex h-10 items-center border-b">
              <Search className="pointer-events-none absolute left-4 h-4 w-4 text-muted-foreground" />
              <Input
                aria-label="Search repository files"
                placeholder="Search files…"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                className="h-10 rounded-none border-0 pl-10 pr-9 focus-visible:ring-0"
              />
              {search ? (
                <button
                  type="button"
                  aria-label="Clear file search"
                  className="absolute right-4 text-muted-foreground hover:text-foreground"
                  onClick={() => setSearch("")}
                >
                  <X className="h-4 w-4" />
                </button>
              ) : null}
            </div>
            <div className="min-h-0 flex-1 overflow-hidden p-2">
              <Tree
                data={tree}
                className="h-full"
                search={search}
                selectedItemId={path}
                initialSlelectedItemId={path}
                pathSeparator="/"
                rowHeight={30}
                folderIcon={FolderClosed}
                itemIcon={FileCode2}
                showChildCount
                onSelectChange={(item) => {
                  const data = item?.data as { path?: unknown } | undefined;
                  if (
                    item?.type === "leaf" &&
                    typeof data?.path === "string" &&
                    data.path !== path
                  ) {
                    router.push(viewerUrl({ path: data.path }));
                  }
                }}
              />
            </div>
          </aside>
        </Panel>
        <PanelResizeHandle className="w-0.5 cursor-col-resize bg-border hover:bg-primary/50" />
        <Panel defaultSize={76} minSize={40} className="min-w-0">
          <section className="flex h-full min-h-0 flex-col">
            <header className="flex h-10 shrink-0 items-center justify-between border-b px-5">
              <span className="truncate font-mono text-sm text-muted-foreground">{path}</span>
              <span className="shrink-0 text-xs text-muted-foreground">
                {startLine}–{endLine} / {totalLines}
              </span>
            </header>
            <div ref={scrollRef} className="min-h-0 flex-1 overflow-auto">
              {hasPrevious ? (
                <div className="px-5 pt-3">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() =>
                      navigateWindow(Math.max(1, startLine - windowSize), startLine - 1)
                    }
                  >
                    <ChevronUp className="mr-1 h-4 w-4" />
                    Load previous lines
                  </Button>
                </div>
              ) : null}
              <ThemedSyntaxHighlighter
                language={languageFor(path)}
                showLineNumbers
                startingLineNumber={startLine}
                wrapLines
                lineProps={(lineNumber: number) => ({
                  "data-code-line": String(lineNumber),
                  style:
                    highlightedStartLine != null &&
                    lineNumber >= highlightedStartLine &&
                    lineNumber <= (effectiveHighlightEnd ?? highlightedStartLine)
                      ? { display: "block", backgroundColor: "rgba(250, 204, 21, 0.12)" }
                      : { display: "block" },
                })}
                customStyle={{
                  margin: 0,
                  padding: "1rem 1.25rem",
                  minWidth: "100%",
                  fontSize: "0.9rem",
                }}
              >
                {content}
              </ThemedSyntaxHighlighter>
              {hasNext || truncated ? (
                <div className="flex items-center gap-2 px-5 pb-3">
                  {hasNext ? (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() =>
                        navigateWindow(endLine + 1, Math.min(totalLines, endLine + windowSize))
                      }
                    >
                      <ChevronDown className="mr-1 h-4 w-4" />
                      Load next lines
                    </Button>
                  ) : null}
                  {truncated ? (
                    <span className="text-sm text-amber-700 dark:text-amber-300">
                      Large window content was byte-truncated.
                    </span>
                  ) : null}
                </div>
              ) : null}
            </div>
          </section>
        </Panel>
      </PanelGroup>
    </main>
  );
}
