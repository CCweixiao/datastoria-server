"use client";

import { useUiPreferences } from "@/components/shared/ui-preferences-provider";
import { ScrollArea } from "@/components/ui/scroll-area";
import { File } from "lucide-react";
import { memo } from "react";
import type { DirNode } from "./skill-detail-tree";
import { SkillFileTreeNode } from "./skill-file-tree-node";

export const SkillFileTreePanel = memo(function SkillFileTreePanel({
  selectedFile,
  dirTree,
  onSkillMdClick,
  onFileClick,
}: {
  selectedFile: string | null;
  dirTree: DirNode[];
  onSkillMdClick: () => void;
  onFileClick: (path: string) => void;
}) {
  const { t } = useUiPreferences();
  return (
    <>
      <div className="flex h-10 flex-shrink-0 items-center border-b px-3">
        <span className="text-xs font-medium text-muted-foreground">{t("skills.files")}</span>
      </div>

      <ScrollArea className="flex-1">
        <div className="px-2 py-2">
          <button
            className={`flex items-center gap-1 w-full text-left py-0.5 rounded px-1 transition-colors ${
              selectedFile === null ? "bg-accent text-accent-foreground" : "hover:bg-accent/40"
            }`}
            onClick={onSkillMdClick}
          >
            <File className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
            <span className="text-xs font-medium">SKILL.md</span>
          </button>

          {dirTree.length === 0 ? (
            <p className="text-xs text-muted-foreground px-1 mt-2">
              {t("skills.noAdditionalFiles")}
            </p>
          ) : (
            dirTree.map((node) => (
              <SkillFileTreeNode
                key={node.path}
                node={node}
                depth={0}
                selectedPath={selectedFile}
                onFileClick={onFileClick}
              />
            ))
          )}
        </div>
      </ScrollArea>
    </>
  );
});
