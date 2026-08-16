"use client";

import { ChevronRight, File, Folder } from "lucide-react";
import { memo, useState } from "react";
import type { DirNode } from "./skill-detail-tree";

export const SkillFileTreeNode = memo(function SkillFileTreeNode({
  node,
  depth = 0,
  selectedPath,
  onFileClick,
}: {
  node: DirNode;
  depth?: number;
  selectedPath: string | null;
  onFileClick: (path: string) => void;
}) {
  const [expanded, setExpanded] = useState(true);

  if (node.isDir) {
    return (
      <div>
        <button
          className="flex min-w-0 flex-1 items-center gap-1 rounded px-1 py-0.5 text-left hover:bg-accent/40 w-full"
          style={{ paddingLeft: `${depth * 14 + 4}px` }}
          onClick={() => setExpanded((value) => !value)}
        >
          <ChevronRight
            className={`h-3.5 w-3.5 text-muted-foreground shrink-0 transition-transform ${expanded ? "rotate-90" : ""}`}
          />
          <Folder className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
          <span className="text-xs truncate">{node.name}</span>
        </button>
        {expanded
          ? node.children.map((child) => (
              <SkillFileTreeNode
                key={child.path}
                node={child}
                depth={depth + 1}
                selectedPath={selectedPath}
                onFileClick={onFileClick}
              />
            ))
          : null}
      </div>
    );
  }

  const isSelected = selectedPath === node.path;

  return (
    <button
      className={`flex items-center gap-1.5 w-full text-left py-0.5 rounded px-1 transition-colors ${
        isSelected ? "bg-accent text-accent-foreground" : "hover:bg-accent/40"
      }`}
      style={{ paddingLeft: `${depth * 14 + 20}px` }}
      onClick={() => onFileClick(node.path)}
    >
      <File className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
      <span className="text-xs truncate flex-1 min-w-0">{node.name}</span>
    </button>
  );
});
