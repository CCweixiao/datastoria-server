import { Button } from "@/components/ui/button";
import type { AppUIMessage, PendingActionData, ToolPart } from "@/lib/ai/ai-types";
import { memo, useState } from "react";
import { useChatAction } from "../chat-action-context";
import { CollapsiblePart } from "./collapsible-part";
import { ToolProgressIndicator } from "./tool-progress-indicator";

export const MessageToolGeneral = memo(function MessageToolGeneral({
  toolName,
  part,
  isRunning = true,
  pendingAction,
}: {
  toolName: string;
  part: AppUIMessage["parts"][0];
  isRunning?: boolean;
  pendingAction?: PendingActionData;
}) {
  const toolPart = part as ToolPart;
  const state = toolPart.state;
  const { onApproval } = useChatAction();
  const [isResolving, setIsResolving] = useState(false);
  const [isResolved, setIsResolved] = useState(false);
  const [resolutionError, setResolutionError] = useState<string | null>(null);

  // Extract toolCallId from the part - try multiple possible locations
  const toolCallId =
    (toolPart as { toolCallId?: string }).toolCallId ||
    (toolPart as { id?: string }).id ||
    (toolPart as unknown as { toolCall?: { toolCallId?: string } }).toolCall?.toolCallId ||
    "";

  return (
    <CollapsiblePart toolName={toolName} state={state} isRunning={isRunning}>
      {toolPart.input != null && (
        <div className="mt-1 max-h-[300px] overflow-auto text-[10px] text-muted-foreground">
          <div className="mb-0.5">input:</div>
          <pre className="bg-muted/30 rounded p-2 overflow-x-auto shadow-sm leading-tight border border-muted/20">
            {JSON.stringify(toolPart.input, null, 2)}
          </pre>
        </div>
      )}

      <ToolProgressIndicator toolCallId={toolCallId} />

      {pendingAction?.actionType === "approval" &&
        state === "approval-requested" &&
        !isResolved && (
          <div className="mt-2 flex items-center gap-2">
            <Button
              size="sm"
              disabled={isResolving}
              onClick={async () => {
                setIsResolving(true);
                setResolutionError(null);
                try {
                  await onApproval({
                    runId: pendingAction.runId,
                    actionId: pendingAction.actionId,
                    approved: true,
                  });
                  setIsResolved(true);
                } catch (error) {
                  setResolutionError(
                    error instanceof Error ? error.message : "Failed to approve action."
                  );
                  setIsResolving(false);
                }
              }}
            >
              Approve
            </Button>
            <Button
              size="sm"
              variant="outline"
              disabled={isResolving}
              onClick={async () => {
                setIsResolving(true);
                setResolutionError(null);
                try {
                  await onApproval({
                    runId: pendingAction.runId,
                    actionId: pendingAction.actionId,
                    approved: false,
                  });
                  setIsResolved(true);
                } catch (error) {
                  setResolutionError(
                    error instanceof Error ? error.message : "Failed to deny action."
                  );
                  setIsResolving(false);
                }
              }}
            >
              Deny
            </Button>
            {resolutionError && <span className="text-xs text-destructive">{resolutionError}</span>}
          </div>
        )}

      {toolPart.output != null && (
        <div className="mt-1 max-h-[300px] overflow-auto text-[10px] text-muted-foreground">
          <div className="mb-0.5">output:</div>
          <pre className="bg-muted/30 rounded p-2 overflow-x-auto shadow-sm leading-tight border border-muted/20">
            {JSON.stringify(toolPart.output, null, 2)}
          </pre>
        </div>
      )}
    </CollapsiblePart>
  );
});
