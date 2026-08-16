"use client";

import { ThemedSyntaxHighlighter } from "@/components/shared/themed-syntax-highlighter";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Skeleton } from "@/components/ui/skeleton";
import type { SkillDetailResponse, SkillResourceResponse } from "@/lib/ai/skills/skill-provider";
import { backendApiFetch, backendApiHeaders, backendApiUrl } from "@/lib/backend-api";
import { ArrowLeft } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Panel, PanelGroup, PanelResizeHandle } from "react-resizable-panels";
import { SkillDetailHeader } from "./detail/skill-detail-header";
import { buildDirTree } from "./detail/skill-detail-tree";
import { SkillFileHeader } from "./detail/skill-file-header";
import { SkillFileTreePanel } from "./detail/skill-file-tree-panel";
import { SkillMarkdownRenderer } from "./detail/skill-markdown-renderer";

interface SkillsDetailViewProps {
  skillId: string;
  onBack: () => void;
}

function buildSkillDetailUrl(skillId: string): string {
  return backendApiUrl(`/api/ai/skills/${encodeURIComponent(skillId)}`);
}

function buildSkillResourceUrl(skillId: string, resourcePath: string): string {
  const searchParams = new URLSearchParams({ path: resourcePath });
  return backendApiUrl(`/api/ai/skills/${encodeURIComponent(skillId)}/resource?${searchParams}`);
}

/**
 * Read-only skill viewer. Skills ship inside the server jar, so this page only browses the
 * SKILL.md and its bundle resources — there is no draft, publish, review or delete flow.
 */
export function SkillsDetailView({ skillId, onBack }: SkillsDetailViewProps) {
  const [detail, setDetail] = useState<SkillDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Left panel: null = SKILL.md, string = resource path
  const [selectedFile, setSelectedFile] = useState<string | null>(null);
  const [resourceDetail, setResourceDetail] = useState<SkillResourceResponse | null>(null);
  const [resourceLoadingPath, setResourceLoadingPath] = useState<string | null>(null);
  const [resourceError, setResourceError] = useState<string | null>(null);
  const [renderMode, setRenderMode] = useState<"rendered" | "raw">("rendered");
  const detailRequestIdRef = useRef(0);
  const resourceRequestIdRef = useRef(0);
  const resourceAbortControllerRef = useRef<AbortController | null>(null);

  // Load skill detail
  useEffect(() => {
    const requestId = ++detailRequestIdRef.current;
    const controller = new AbortController();

    setLoading(true);
    setError(null);
    setDetail(null);
    setSelectedFile(null);
    setResourceDetail(null);
    setResourceError(null);
    setResourceLoadingPath(null);
    setRenderMode("rendered");
    resourceAbortControllerRef.current?.abort();
    resourceAbortControllerRef.current = null;

    backendApiFetch(buildSkillDetailUrl(skillId), {
      signal: controller.signal,
      headers: backendApiHeaders(),
    })
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return res.json() as Promise<SkillDetailResponse>;
      })
      .then((data) => {
        if (detailRequestIdRef.current !== requestId) return;
        setDetail(data);
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted || detailRequestIdRef.current !== requestId) return;
        setError(err instanceof Error ? err.message : "Failed to load skill");
        setLoading(false);
      });

    return () => {
      controller.abort();
    };
  }, [skillId]);

  // Load a resource file when a tree node is clicked
  const handleFileClick = useCallback(
    (resourcePath: string) => {
      const requestId = ++resourceRequestIdRef.current;
      resourceAbortControllerRef.current?.abort();
      const controller = new AbortController();
      resourceAbortControllerRef.current = controller;

      setSelectedFile(resourcePath);
      setResourceDetail(null);
      setResourceError(null);
      setResourceLoadingPath(resourcePath);
      setRenderMode("raw");

      backendApiFetch(buildSkillResourceUrl(skillId, resourcePath), {
        signal: controller.signal,
        headers: backendApiHeaders(),
      })
        .then((res) => {
          if (!res.ok) throw new Error(`HTTP ${res.status}`);
          return res.json() as Promise<SkillResourceResponse>;
        })
        .then((data) => {
          if (resourceRequestIdRef.current !== requestId) return;
          setResourceDetail(data);
          setResourceLoadingPath(null);
        })
        .catch((err: unknown) => {
          if (controller.signal.aborted || resourceRequestIdRef.current !== requestId) return;
          setResourceError(err instanceof Error ? err.message : "Failed to load file");
          setResourceLoadingPath(null);
        });
    },
    [skillId]
  );

  // Click SKILL.md → go back to main content
  const handleSkillMdClick = useCallback(() => {
    resourceRequestIdRef.current += 1;
    resourceAbortControllerRef.current?.abort();
    resourceAbortControllerRef.current = null;
    setSelectedFile(null);
    setResourceDetail(null);
    setResourceError(null);
    setResourceLoadingPath(null);
    setRenderMode("rendered");
  }, []);

  // Derived display state
  const displayedResourcePaths = useMemo(
    () => (detail ? [...detail.resourcePaths].sort() : []),
    [detail]
  );
  const isMarkdownFile =
    selectedFile === null || selectedFile.endsWith(".md") || selectedFile.endsWith(".MD");
  const isJsonFile = selectedFile?.endsWith(".json") || selectedFile?.endsWith(".JSON");
  const displayedFilename = selectedFile === null ? "SKILL.md" : selectedFile.split("/").pop()!;
  const currentContent =
    selectedFile === null ? (detail?.content ?? "") : (resourceDetail?.content ?? "");
  const currentSource = selectedFile === null ? (detail?.source ?? null) : (resourceDetail?.source ?? null);
  const dirTree = useMemo(() => buildDirTree(displayedResourcePaths), [displayedResourcePaths]);
  const resourceLoading = selectedFile !== null && resourceLoadingPath === selectedFile;
  const showRenderToggle = isMarkdownFile;

  return (
    <div className="h-full flex flex-col relative">
      {/* Header */}
      <div className="flex-shrink-0 px-4 py-2 border-b flex items-center gap-2">
        <Button variant="ghost" size="icon" onClick={onBack} className="h-7 w-7">
          <ArrowLeft className="h-4 w-4" />
        </Button>
        {loading ? (
          <Skeleton className="h-4 w-32" />
        ) : detail ? (
          <SkillDetailHeader detail={detail} />
        ) : null}
      </div>

      {/* Body */}
      {loading ? (
        <div className="flex-1 px-4 py-4 space-y-3">
          <Skeleton className="h-3 w-full" />
          <Skeleton className="h-3 w-5/6" />
          <Skeleton className="h-3 w-4/6" />
        </div>
      ) : error ? (
        <div className="flex-1 flex items-center justify-center px-4">
          <p className="text-sm text-destructive">{error}</p>
        </div>
      ) : detail ? (
        <PanelGroup direction="horizontal" className="flex-1 overflow-hidden min-h-0">
          {/* ── Left panel — file content ── */}
          <Panel defaultSize={75} minSize={20} className="flex flex-col overflow-hidden">
            <SkillFileHeader
              displayedFilename={displayedFilename}
              currentSource={currentSource}
              renderMode={renderMode}
              showRenderToggle={showRenderToggle}
              onRenderModeChange={setRenderMode}
            />

            <ScrollArea className="flex-1">
              <div className="px-4 py-3">
                {resourceLoading ? (
                  <div className="space-y-2">
                    <Skeleton className="h-3 w-full" />
                    <Skeleton className="h-3 w-5/6" />
                    <Skeleton className="h-3 w-4/6" />
                  </div>
                ) : resourceError ? (
                  <p className="text-sm text-destructive">{resourceError}</p>
                ) : isJsonFile ? (
                  <ThemedSyntaxHighlighter
                    language="json"
                    customStyle={{
                      margin: 0,
                      padding: 0,
                      fontSize: "0.75rem",
                      background: "transparent",
                    }}
                    showLineNumbers={false}
                  >
                    {currentContent}
                  </ThemedSyntaxHighlighter>
                ) : isMarkdownFile && renderMode === "rendered" ? (
                  <SkillMarkdownRenderer raw={currentContent} />
                ) : (
                  <pre className="text-xs font-mono whitespace-pre-wrap break-words leading-relaxed">
                    {currentContent}
                  </pre>
                )}
              </div>
            </ScrollArea>
          </Panel>

          <PanelResizeHandle className="w-0.5 bg-border hover:bg-primary/40 active:bg-primary/60 cursor-col-resize transition-colors" />

          {/* ── Right panel — directory tree ── */}
          <Panel defaultSize={25} minSize={20} className="flex flex-col overflow-hidden">
            <SkillFileTreePanel
              selectedFile={selectedFile}
              dirTree={dirTree}
              onSkillMdClick={handleSkillMdClick}
              onFileClick={handleFileClick}
            />
          </Panel>
        </PanelGroup>
      ) : null}
    </div>
  );
}
